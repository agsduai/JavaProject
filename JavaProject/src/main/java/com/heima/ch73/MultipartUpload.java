/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-02
 * 课名：分片上传与断点续传
 * ------------------------------------------------------------
 * 知识点：
 *   - **分片上传解决两个问题**：**大文件超时**（一次传 2GB 必然失败）与**网络中断**（重传整个文件）——**切 5~10MB/shards 并发上传，失败只重传失败分片**。
 *   - **分片上传的完整流程**：**初始化**（服务端返回 uploadId + 分片大小）-> **逐片上传**（带 uploadId + 片序号 + 片 hash）-> **合并**（所有分片到齐后校验总 hash 并合并）-> **清理**（临时分片过期删除）。
 *   - **断点续传**：客户端记录"已上传分片列表"，恢复时**先查询服务端已收到的分片**（服务端要记录每片状态），跳过已传的——**进度条与续传依赖这份清单**。
 *   - **并发与顺序**：分片可**并发上传**（提升吞吐），但**合并要按序号顺序**；**最后一片写完触发合并**，或由客户端显式调"完成"接口（推荐后者，因为并发下无法判断"它是最后一片"）。
 *   - **失败与清理**：**分片过期**（24 小时未合并则清理临时片，防垃圾堆积）；**合并失败**（hash 不符）要能定位到哪个分片——**每片单独存 hash**。
 *   - 易错点：分片大小固定不可调（弱网应更小）；不记录分片状态（无法续传）；合并时按到达顺序拼接（数据错乱）；无过期清理（存储被垃圾占满）。
 * ============================================================
 */

package com.heima.ch73;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MultipartUpload {
    record Part(int index, String hash, byte[] data) {
    }

    record Session(String uploadId, long totalSize, int partSize, int partCount,
                   Map<Integer, String> receivedParts, long createdAtMs) {
        boolean complete() {
            return receivedParts.size() == partCount;
        }

        List<Integer> missing() {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < partCount; i++) {
                if (!receivedParts.containsKey(i)) {
                    out.add(i);
                }
            }
            return out;
        }
    }

    static final Map<String, Session> sessions = new LinkedHashMap<>();

    static Session init(String uploadId, long totalSize, int partSize) {
        int count = (int) ((totalSize + partSize - 1) / partSize);
        Session s = new Session(uploadId, totalSize, partSize, count,
                new LinkedHashMap<>(), 1_000_000L);
        sessions.put(uploadId, s);
        System.out.printf("  初始化 %s：总大小=%d 分片=%d 片数=%d%n",
                uploadId, totalSize, partSize, count);
        return s;
    }

    static void uploadPart(String uploadId, int index, String hash, int bytes) {
        Session s = sessions.get(uploadId);
        if (s == null) {
            System.out.println("  会话不存在：" + uploadId);
            return;
        }
        s.receivedParts().put(index, hash);
        System.out.printf("  收到分片 %d（%d 字节，hash=%s）-> 已收 %d/%d%n",
                index, bytes, hash, s.receivedParts().size(), s.partCount());
    }

    /** 续传：查询已收分片，只补缺失 */
    static void resume(String uploadId) {
        Session s = sessions.get(uploadId);
        System.out.println("  断点续传查询：" + uploadId);
        System.out.println("    已收分片：" + s.receivedParts().keySet());
        System.out.println("    缺失分片：" + s.missing() + " -> 只补这些");
    }

    /** 合并：校验完整性与总 hash */
    static String complete(String uploadId, String expectedHash) {
        Session s = sessions.get(uploadId);
        if (s == null) {
            return "会话不存在";
        }
        if (!s.complete()) {
            return "分片未齐，缺 " + s.missing();
        }
        // 模拟合并后的总 hash（用分片 hash 拼接的简化摘要）
        String merged = String.join("", s.receivedParts().values());
        if (expectedHash != null && !expectedHash.equals(merged)) {
            return "合并失败：总 hash 不符（分片损坏）";
        }
        return "合并成功：" + s.partCount() + " 片，共 " + s.totalSize() + " 字节";
    }

    static void cleanup(long nowMs, long ttlMs) {
        System.out.println("【过期清理】");
        sessions.forEach((id, s) -> {
            if (nowMs - s.createdAtMs() > ttlMs && !s.complete()) {
                System.out.printf("  清理 %s（未完成且超过 %d 小时）%n", id, ttlMs / 3_600_000);
            }
        });
        System.out.println();
    }

    public static void main(String[] args) {
        // 模拟 25MB 文件，5MB 一片
        init("up-1", 25L * 1024 * 1024, 5 * 1024 * 1024);
        uploadPart("up-1", 0, "h0", 5 * 1024 * 1024);
        uploadPart("up-1", 1, "h1", 5 * 1024 * 1024);
        System.out.println("  网络中断...");
        resume("up-1");

        uploadPart("up-1", 2, "h2", 5 * 1024 * 1024);
        uploadPart("up-1", 3, "h3", 5 * 1024 * 1024);
        uploadPart("up-1", 4, "h4", 5 * 1024 * 1024);
        System.out.println();
        System.out.println("【合并】" + complete("up-1", null));
        System.out.println("【hash 校验失败场景】" + complete("up-1", "wrong-hash"));

        System.out.println();
        init("up-2", 10L * 1024 * 1024, 5 * 1024 * 1024);
        uploadPart("up-2", 0, "x0", 5 * 1024 * 1024);
        cleanup(1_000_000L + 25L * 3_600_000, 24L * 3_600_000);
        System.out.println("  要点：分片可并发、续传靠服务端记录、合并要校验、临时片要过期");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现分片上传：初始化/上传/续传查询/合并/过期清理，用"中断后恢复"场景验证只补缺失分片。
 * 2. 实现分片 hash 校验：注入一个损坏分片，验证合并时能定位到具体分片。
 * 3. 思考题：为什么"合并要由客户端显式调用完成接口"而不是"最后一片自动触发"？从"并发上传无法判断最后一片"分析。
 * ============================================================
 */
