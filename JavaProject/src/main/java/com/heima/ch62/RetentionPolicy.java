/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-08
 * 课名：消息保留、压缩与清理策略
 * ------------------------------------------------------------
 * 知识点：
 *   - **两种清理策略**：`cleanup.policy=delete`（按时间/大小删段，日志类）与 `cleanup.policy=compact`（按 key 保留最新值，状态类）——**可组合**（`delete,compact`：既按时间删又保留最新 key）。
 *   - **compact 的语义**：同一个 key 的旧值被清理，**只留最新一条**；**墓碑消息（tombstone，value=null）**表示"该 key 被删除"，保留一段时间后清除——**消费端重放 compact Topic 得到的是"最终状态快照"**（49-06 事件溯源的快照思想）。
 *   - **compact 的触发与整理**：后台清理线程周期性扫描段 -> 生成新的整理段 -> 旧段延迟删除；**compact 不保证即时**（可能同时看到新旧值，消费端要"后写覆盖"语义）。
 *   - **保留策略的参数配合**：`retention.ms`/`retention.bytes` + `log.segment.bytes`（段太大 -> 过期删不掉）+ `log.retention.check.interval.ms`（检查频率）——**三者共同决定"数据实际能留多久"**。
 *   - **场景对应**：日志/埋点（delete，7 天）、订单事件（delete + 长保留，可重放对账）、配置/画像最新值（compact）、CDC 表同步（compact，下游拿到每个主键的最新镜像 43-09）。
 *   - 易错点：高频 key 用 compact 但 key 基数极大（整理开销与存储都高）；tombstone 配置不当（删掉的 key 又"复活"）；delete 策略下以为"消费过就删"（删除与消费无关）；段大小与保留期不匹配（过期数据迟迟不删）。
 * ============================================================
 */

package com.heima.ch62;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RetentionPolicy {
    record Record(long offset, long timestamp, String key, String value) {
        boolean isTombstone() {
            return value == null;
        }
    }

    /** delete 策略：按时间删除（保留最近 N 个时间单位） */
    static List<Record> applyDelete(List<Record> log, long now, long retentionMs) {
        List<Record> kept = new ArrayList<>();
        for (Record r : log) {
            if (now - r.timestamp() <= retentionMs) {
                kept.add(r);
            }
        }
        System.out.printf("  delete：总 %d 条 -> 保留 %d 条（窗口 %dms）%n",
                log.size(), kept.size(), retentionMs);
        return kept;
    }

    /** compact 策略：每个 key 只留最新一条；tombstone 保留（视配置）后清除 */
    static List<Record> applyCompact(List<Record> log, boolean keepTombstone) {
        Map<String, Record> latest = new LinkedHashMap<>();
        for (Record r : log) {
            latest.put(r.key(), r);                    // 后写覆盖
        }
        List<Record> out = new ArrayList<>();
        latest.forEach((k, r) -> {
            if (r.isTombstone() && !keepTombstone) {
                return;                                // 已删除的 key 彻底移除
            }
            out.add(r);
        });
        System.out.printf("  compact：总 %d 条（%d 个 key）-> 保留 %d 条%n",
                log.size(), latest.size(), out.size());
        return out;
    }

    public static void main(String[] args) {
        long now = 1_000_000;
        List<Record> log = new ArrayList<>();
        log.add(new Record(0, now - 900_000, "user:1", "{\"level\":\"NEW\"}"));
        log.add(new Record(1, now - 800_000, "user:2", "{\"level\":\"NEW\"}"));
        log.add(new Record(2, now - 500_000, "user:1", "{\"level\":\"VIP\"}"));
        log.add(new Record(3, now - 100_000, "user:2", null));      // 删除 user:2
        log.add(new Record(4, now - 50_000, "user:3", "{\"level\":\"NEW\"}"));

        System.out.println("【原始日志】");
        log.forEach(r -> System.out.printf("  offset=%d key=%-8s value=%s%n",
                r.offset(), r.key(), r.value()));
        System.out.println();

        applyDelete(log, now, 600_000);
        System.out.println();
        applyCompact(log, false);
        System.out.println("  对比：delete 保留时间窗口，compact 保留最新状态（消费端得到快照）");
        System.out.println();
        System.out.println("【策略选型】");
        Map<String, String> choice = new LinkedHashMap<>();
        choice.put("日志/埋点", "delete（按时间），7~30 天");
        choice.put("订单事件", "delete + 长保留（可重放对账）");
        choice.put("配置/画像", "compact（每 key 最新值）");
        choice.put("CDC 表同步", "compact（每主键最新镜像，43-09）");
        choice.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现两种清理策略（delete/compact）并组合（`delete,compact`），用 30 条含 tombstone 的记录验证输出。
 * 2. 分析"段大小 1GB + 保留 7 天"在"日增 100GB"场景下的实际磁盘占用，给出参数调整建议。
 * 3. 思考题：为什么 CDC 用 compact 而不是 delete？从"下游需要每个主键的最新镜像"与"重放时不重复应用旧值"分析。
 * ============================================================
 */
