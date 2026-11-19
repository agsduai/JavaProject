/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-11
 * 课名：综合案例：文件中台
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个可运行的文件中台**：**上传**（分片 + 秒传 73-02/03）-> **校验**（类型/大小/文件头 73-05）-> **存储**（对象存储 + 元数据 73-01/04）-> **访问**（签名 URL 73-06）-> **衍生物**（图片按需生成 73-07）-> **下载**（Range 73-08）-> **导出**（流式 zip 73-09）-> **治理**（生命周期 73-10）。
 *   - **中台的三个对外能力**：**上传服务**（统一入口，业务方不关心存储）、**下载服务**（签名 URL + 权限校验）、**文件 API**（元数据查询/删除/配额）。
 *   - **验收清单**：大文件可分片续传、重复内容秒传、非法文件被拦截、私有文件必须签名、图片按需生成、Range 下载可用、垃圾能被清理、配额生效。
 *   - **与全书的关系**：文件中台把 37（容器存储）、36（上传安全）、46（对象存储成本）、55（租户配额）、68（隐私删除）串成一个业务组件——**它是"基础设施能力的业务封装"**。
 *   - 易错点：上传成功即认为可用（要等合并 + 扫描完成）；签名 URL TTL 太长；衍生物同步生成（阻塞响应）；无治理（存储只增）。
 * ============================================================
 */

package com.heima.ch73;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FileHubCase {
    enum State {
        UPLOADING, SCANNING, READY, DELETED
    }

    record FileMeta(String fileId, String name, long size, String hash, String tenantId,
                    String ownerId, State state) {
        FileMeta move(State next) {
            return new FileMeta(fileId, name, size, hash, tenantId, ownerId, next);
        }
    }

    static final Map<String, FileMeta> metaStore = new LinkedHashMap<>();
    static final Map<String, String> hashIndex = new LinkedHashMap<>();   // 秒传索引
    static final Map<String, Long> quotaUsed = new LinkedHashMap<>();

    static String upload(String fileId, String name, long size, String hash,
                         String tenantId, String ownerId, long quotaMb) {
        // 1) 配额
        long used = quotaUsed.getOrDefault(tenantId, 0L);
        if (used + size > quotaMb * 1024 * 1024) {
            return "拒绝：租户配额超限";
        }
        // 2) 秒传
        String existing = hashIndex.get(hash);
        String note;
        if (existing != null) {
            note = "秒传（复用 " + existing + "）";
        } else {
            hashIndex.put(hash, fileId);
            note = "真实上传";
        }
        // 3) 入库（先 SCANNING，扫描通过才 READY）
        metaStore.put(fileId, new FileMeta(fileId, name, size, hash, tenantId, ownerId,
                State.SCANNING));
        quotaUsed.merge(tenantId, size, Long::sum);
        return note + " -> 状态 SCANNING（等待扫描）";
    }

    static void scanDone(String fileId, boolean clean) {
        FileMeta m = metaStore.get(fileId);
        if (m == null) {
            return;
        }
        metaStore.put(fileId, m.move(clean ? State.READY : State.DELETED));
        System.out.printf("  扫描结果 %s -> %s%n", fileId, clean ? "READY" : "DELETED（命中风险）");
    }

    /** 下载授权：租户 + 归属 + 状态 */
    static String authorizeDownload(String fileId, String tenantId, String userId) {
        FileMeta m = metaStore.get(fileId);
        if (m == null || m.state() != State.READY) {
            return "拒绝：文件不存在或未就绪";
        }
        if (!m.tenantId().equals(tenantId)) {
            return "拒绝：跨租户（55-04）";
        }
        if (!m.ownerId().equals(userId)) {
            return "拒绝：非归属人（越权 36-05）";
        }
        return "允许：签发临时 URL（TTL 300s）";
    }

    static void acceptance() {
        System.out.println();
        System.out.println("【验收清单】");
        List<String> gates = List.of(
                "分片上传 + 断点续传（73-02）",
                "重复内容秒传（73-03）",
                "类型/文件头校验（73-05）",
                "私有文件签名 URL（73-06）",
                "图片按需生成（73-07）",
                "Range 下载（73-08）",
                "流式导出（73-09）",
                "生命周期治理（73-10）");
        gates.forEach(g -> System.out.println("  [ ] " + g));
    }

    public static void main(String[] args) {
        System.out.println("【上传】");
        System.out.println("  " + upload("f-1", "合同.pdf", 2_000_000, "h-pdf-1",
                "t-100", "u-1", 100));
        System.out.println("  " + upload("f-2", "合同副本.pdf", 2_000_000, "h-pdf-1",
                "t-100", "u-2", 100));
        System.out.println("  " + upload("f-3", "巨款.zip", 200L * 1024 * 1024, "h-zip-1",
                "t-100", "u-1", 100));

        System.out.println();
        System.out.println("【扫描】");
        scanDone("f-1", true);
        scanDone("f-2", true);

        System.out.println();
        System.out.println("【下载授权】");
        System.out.println("  u-1 下载 f-1：" + authorizeDownload("f-1", "t-100", "u-1"));
        System.out.println("  u-2 下载 f-1：" + authorizeDownload("f-1", "t-100", "u-2"));
        System.out.println("  t-200 下载 f-1：" + authorizeDownload("f-1", "t-200", "u-1"));
        System.out.println("  u-1 下载未扫描完的 f-3："
                + authorizeDownload("f-3", "t-100", "u-1"));

        System.out.println();
        System.out.println("【配额】");
        quotaUsed.forEach((tenant, used) -> System.out.printf("  %-6s 已用 %.1f MB%n",
                tenant, used / 1024.0 / 1024));

        acceptance();
        System.out.println();
        System.out.println("  一句话：文件中台 = 上传/校验/存储/授权/衍生/下载/导出/治理 的统一封装");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现文件中台（上传/秒传/扫描/授权/配额），用 3 次上传（含秒传与超配额）验证。
 * 2. 加入"图片按需生成 + Range 下载"：验证同一文件既支持缩略图又支持分段下载。
 * 3. 思考题：为什么"上传成功 ≠ 可用"？从"分片合并"与"内容扫描"两个异步阶段分析。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 做一个文件服务：分片上传与断点续传、秒传（内容哈希）、大文件合并、直传对象存储与预签名、防盗链、图片处理、生命周期与冷热分层。要能回答「10GB 文件怎么传」。
 * 【任务】
 * 1. 实现分片上传：文件切片、并发上传、服务端记录分片状态，中断后从已上传分片续传。
 * 2. 实现秒传：先算文件 SHA-256，命中已有文件直接返回引用，说明哈希碰撞与权限隔离的注意点。
 * 3. 实现预签名直传：服务端只发临时凭证，客户端直传对象存储，说明为什么这样能减轻服务端带宽压力。
 * 4. 实现访问控制：私有文件用带过期时间的签名 URL、公开文件走 CDN + 防盗链（Referer/时间戳签名）。
 * 【验收清单】
 * [ ] 上传中断后重连续传，只补传缺失分片。
 * [ ] 相同内容重复上传秒传命中，不同用户之间不串文件。
 * [ ] 直传链路服务端不中转文件流，带宽占用可验证下降。
 * [ ] 私有文件未签名不可访问，签名过期后自动失效。
 * ============================================================
 */
