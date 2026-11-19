/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-10
 * 课名：存储治理：生命周期与成本
 * ------------------------------------------------------------
 * 知识点：
 *   - **存储治理的三件事**：**清理**（删除无用文件：未完成分片、软删文件的物理对象、过期导出包）、**分层**（热/温/冷存储降级 46-03）、**配额**（用户/租户容量限制，55-05）。
 *   - **文件的生命周期状态机**：`UPLOADING`（分片上传中）-> `READY`（可用）-> `DELETED`（逻辑删除，保留物理对象 N 天便于恢复）-> `PURGED`（物理删除）——**与 68-10 的删除权衔接**（用户要求删除要能真删）。
 *   - **垃圾来源清单**：未完成的分片（73-02 的过期清理）、上传成功但业务未引用的孤儿文件（业务回滚）、软删未物理删的文件、过期导出包、日志/备份残留——**治理就是逐个堵住这些来源**。
 *   - **成本优化的杠杆**：**去重**（73-03）、**压缩**（73-09）、**分层**（冷数据转低频/归档）、**配额**（防滥用）、**出网走 CDN**（73-04）——**按收益排序做**。
 *   - 与 46-03 的关系：对象存储桶策略 + 生命周期规则是"基础设施层的治理"，本章是"业务层的治理"——**两层都要有**。
 *   - 易错点：软删永久保留（存储只增）；孤儿文件无巡查（业务回滚后文件还在）；无配额（一个用户传满盘）；物理删除跳过审计（68-09）。
 * ============================================================
 */

package com.heima.ch73;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class StorageGovernance {
    enum State {
        UPLOADING, READY, DELETED, PURGED
    }

    record FileRecord(String fileId, String ownerId, long sizeBytes, State state,
                      long createdAtMs, Long deletedAtMs, boolean referenced) {
        FileRecord move(State next, Long deletedAt) {
            return new FileRecord(fileId, ownerId, sizeBytes, next, createdAtMs,
                    deletedAt, referenced);
        }
    }

    static final List<FileRecord> records = new ArrayList<>();

    static void seed() {
        long now = 1_000_000_000L;
        records.add(new FileRecord("f-1", "u-1", 5_000_000, State.READY, now, null, true));
        records.add(new FileRecord("f-2", "u-1", 8_000_000, State.READY, now, null, false));
        records.add(new FileRecord("f-3", "u-2", 2_000_000, State.UPLOADING, now - 25L * 3_600_000,
                null, false));
        records.add(new FileRecord("f-4", "u-2", 12_000_000, State.DELETED, now - 40L * 86_400_000,
                now - 40L * 86_400_000, false));
        records.add(new FileRecord("f-5", "u-3", 900_000_000, State.READY, now, null, true));
    }

    /** 治理动作：按规则批量处理 */
    static void govern(long nowMs) {
        System.out.println("【治理动作】");
        long retentionDeleted = 30L * 86_400_000;
        long uploadTtl = 24L * 3_600_000;
        int purged = 0;
        long freed = 0;
        for (int i = 0; i < records.size(); i++) {
            FileRecord r = records.get(i);
            if (r.state() == State.UPLOADING && nowMs - r.createdAtMs() > uploadTtl) {
                records.set(i, r.move(State.PURGED, nowMs));
                freed += r.sizeBytes();
                System.out.printf("  清理未完成上传：%s（%d 字节）%n", r.fileId(), r.sizeBytes());
            } else if (r.state() == State.DELETED && r.deletedAtMs() != null
                    && nowMs - r.deletedAtMs() > retentionDeleted) {
                records.set(i, r.move(State.PURGED, nowMs));
                freed += r.sizeBytes();
                purged++;
                System.out.printf("  软删超期物理删除：%s（%d 字节）%n", r.fileId(), r.sizeBytes());
            }
        }
        System.out.printf("  共释放 %.1f MB（物理删除 %d 个）%n", freed / 1024.0 / 1024, purged);
        System.out.println();
    }

    /** 孤儿文件：READY 但无业务引用 */
    static void orphanReport() {
        System.out.println("【孤儿文件巡查（READY 但未被引用）】");
        for (FileRecord r : records) {
            if (r.state() == State.READY && !r.referenced()) {
                System.out.printf("  %s（%d 字节，owner=%s）-> 候选清理（需业务确认）%n",
                        r.fileId(), r.sizeBytes(), r.ownerId());
            }
        }
        System.out.println();
    }

    static void quotaReport() {
        System.out.println("【配额统计】");
        Map<String, Long> usage = new LinkedHashMap<>();
        for (FileRecord r : records) {
            if (r.state() != State.PURGED) {
                usage.merge(r.ownerId(), r.sizeBytes(), Long::sum);
            }
        }
        long quota = 1_000_000_000L;
        usage.forEach((owner, used) -> System.out.printf(
                "  %-4s 已用 %.1f MB / 配额 %.0f MB -> %s%n",
                owner, used / 1024.0 / 1024, quota / 1024.0 / 1024,
                used > quota ? "超限（禁止上传）" : "正常"));
        System.out.println();
    }

    static void costLevers() {
        System.out.println("【成本优化杠杆（按收益排序）】");
        Map<String, String> levers = new LinkedHashMap<>();
        levers.put("去重", "同内容只存一份（73-03）");
        levers.put("分层", "30 天未访问转低频/归档（46-03）");
        levers.put("CDN", "出网走 CDN（73-04）");
        levers.put("配额", "防单租户滥用（55-05）");
        levers.put("清理", "未完成分片/孤儿文件（本章）");
        levers.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }

    public static void main(String[] args) {
        seed();
        long now = 1_000_000_000L;
        govern(now);
        orphanReport();
        quotaReport();
        costLevers();
        System.out.println();
        System.out.println("  治理闭环：巡查 -> 候选清单 -> 业务确认 -> 删除 -> 审计（68-09）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现存储治理任务：清理过期分片 + 软删超期物理删除 + 孤儿巡查 + 配额统计，用 5 条记录验证。
 * 2. 为你的业务列出"存储垃圾来源清单"（≥5 类），各给一个堵住的办法。
 * 3. 思考题：为什么"软删要保留 N 天再物理删除"？从"误删恢复"与"用户删除权（68-10）"的平衡分析。
 * ============================================================
 */
