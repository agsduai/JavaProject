/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-03
 * 课名：秒传与内容去重
 * ------------------------------------------------------------
 * 知识点：
 *   - **秒传（Instant Upload）的原理：内容寻址**——客户端先算文件 hash 发给服务端，**服务端已存在同 hash 的文件就直接返回成功**（不用传字节），文件元数据新建一条记录指向已有的存储对象——**同一份内容只存一份**。
 *   - **hash 的选择与代价**：**MD5**（快、碰撞风险理论存在但不是攻击目标）、**SHA-256**（更安全、慢一些）——**大文件算 hash 本身要几秒**（客户端算，不占服务端带宽）；**可以先算"文件头 + 大小 + 尾部"的轻量指纹做预判**，命中再算全量 hash。
 *   - **秒传的安全风险**：**hash 泄露**（攻击者知道你有哪些文件）、**恶意秒传**（上传一个 hash 让别人以为你有某个敏感文件）——对策：**秒传按"用户可见范围"校验**（不能跨租户复用）、**敏感文件不做秒传**（55-04/68-05）。
 *   - **与存储分层的关系**：秒传让"逻辑文件"与"物理对象"变成 **N:1**（多个 fileId 指向同一 storageKey）——**删除要引用计数**（最后一个引用删掉才删物理文件，像 60-07 的 ByteBuf 引用计数）。
 *   - 易错点：跨租户秒传（数据泄露）；删除物理文件时其他引用失效；hash 算法变了但没版本（新旧不兼容）；只凭 hash 就信任内容（客户端可伪造）。
 *   - **与 46-03 的关系**：对象存储本身不提供秒传，**是应用层用 hash 索引实现的**——"查 hash 是否存在"这一步就是秒传的全部魔法。
 * ============================================================
 */

package com.heima.ch73;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class InstantUpload {
    /** 物理对象：hash -> 存储键 + 引用计数 */
    record Blob(String hash, String storageKey, long sizeBytes, int refCount) {
        Blob retain() {
            return new Blob(hash, storageKey, sizeBytes, refCount + 1);
        }

        Blob release() {
            return new Blob(hash, storageKey, sizeBytes, refCount - 1);
        }
    }

    static final Map<String, Blob> blobs = new LinkedHashMap<>();
    /** 逻辑文件：fileId -> hash（多对一） */
    static final Map<String, String> files = new LinkedHashMap<>();

    /** 秒传检查：hash 已存在且在允许范围内 */
    static String checkInstant(String hash, String tenantId, boolean allowCrossTenant) {
        Blob b = blobs.get(hash);
        if (b == null) {
            return "MISS：需要真实上传";
        }
        if (!allowCrossTenant) {
            // 简化：假设同租户才允许复用（真实实现按 blob 的 tenant 标记判断）
            System.out.println("  命中 hash，但需校验租户范围（本示例允许同租户）");
        }
        return "HIT：秒传成功（复用 storageKey=" + b.storageKey()
                + "，大小 " + b.sizeBytes() + "）";
    }

    /** 真实上传：创建 blob + 逻辑文件引用 */
    static String upload(String fileId, String hash, long size, String tenantId) {
        Blob existing = blobs.get(hash);
        if (existing != null) {
            blobs.put(hash, existing.retain());
            files.put(fileId, hash);
            return "秒传（引用计数 -> " + blobs.get(hash).refCount() + "）";
        }
        String storageKey = "blob/" + hash.substring(0, 2) + "/" + hash;
        blobs.put(hash, new Blob(hash, storageKey, size, 1));
        files.put(fileId, hash);
        return "真实上传（新建 blob " + storageKey + "）";
    }

    /** 删除：引用计数归零才删物理对象 */
    static String delete(String fileId) {
        String hash = files.remove(fileId);
        if (hash == null) {
            return "文件不存在";
        }
        Blob b = blobs.get(hash);
        Blob released = b.release();
        if (released.refCount() <= 0) {
            blobs.remove(hash);
            return "删除逻辑文件 + 物理对象（引用归零）";
        }
        blobs.put(hash, released);
        return "删除逻辑文件（物理对象保留，剩余引用 " + released.refCount() + "）";
    }

    public static void main(String[] args) {
        System.out.println("【首次上传】");
        System.out.println("  " + upload("f-1", "abc123", 1024 * 1024, "t-100"));
        System.out.println("  blobs=" + blobs.keySet() + " files=" + files);

        System.out.println();
        System.out.println("【秒传检查】");
        System.out.println("  " + checkInstant("abc123", "t-100", true));
        System.out.println("  " + checkInstant("not-exist", "t-100", true));

        System.out.println();
        System.out.println("【同一内容第二个文件】");
        System.out.println("  " + upload("f-2", "abc123", 1024 * 1024, "t-100"));
        System.out.println("  blobs=" + blobs + " files=" + files);
        System.out.println("  两个逻辑文件指向同一物理对象（N:1）");

        System.out.println();
        System.out.println("【删除一个逻辑文件】");
        System.out.println("  " + delete("f-1"));
        System.out.println("  blobs=" + blobs + " files=" + files);

        System.out.println();
        System.out.println("【删除最后一个引用】");
        System.out.println("  " + delete("f-2"));
        System.out.println("  blobs=" + blobs + "（物理对象已删除）");

        System.out.println();
        System.out.println("【安全边界】");
        System.out.println("  跨租户秒传 = 数据泄露（55-04 双键校验）");
        System.out.println("  敏感文件（身份证照片）不做秒传（68-02 L4 数据）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现秒传与引用计数：两个文件复用同一 blob、逐个删除验证"最后引用才删物理对象"。
 * 2. 实现"轻量指纹预判"：先用（大小 + 头尾 hash）判断可能命中，再算全量 hash 确认，量化节省的时间。
 * 3. 思考题：为什么"跨租户秒传"是数据泄露？构造"用 hash 探测其他租户有无某文件"的攻击路径。
 * ============================================================
 */
