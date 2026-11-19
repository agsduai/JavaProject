/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-04
 * 课名：日志存储：段文件与索引
 * ------------------------------------------------------------
 * 知识点：
 *   - **分区日志被切成多个 Segment 文件**：`00000000000000000100.log`（数据）、`.index`（偏移量索引）、`.timeindex`（时间戳索引）——**单文件过大（1GB 默认）就滚动新段**；**文件名是段的起始 offset**。
 *   - **索引的作用**：`.index` 是**稀疏索引**（每写 N 条记录一条索引项，默认 4KB 间隔），**用二分查到"小于目标 offset 的最近索引项"，再顺序扫描**——不是 B+ 树，是"稀疏索引 + 顺序扫"，因为磁盘顺序读很快。
 *   - **查找一条消息的路径**：按 offset 定位段（文件名二分）-> 查 `.index` 定位接近位置 -> 从该位置顺序扫到目标 offset ——**这就是 Kafka 读"任意位置"都能很快的原因**。
 *   - **清理（retention）按段进行**：`retention.ms`/`retention.bytes` 到期后**删除整个段文件**（不是逐条删）——所以"删除延迟粒度 = 一个段的时间跨度"；`log.segment.bytes` 与 `retention` 要配合（段太大导致过期后迟迟不删）。
 *   - **压缩（compaction）是另一种清理**：按 key 保留最后一条（`cleanup.policy=compact`），适合"状态快照类"Topic（如配置、用户画像最新值）——**与按时间删除互斥/可组合**。
 *   - 易错点：段文件过大导致 retention 回收不及时（磁盘涨）；用 offset 索引查找时间范围（应该用 `.timeindex`）；compact 与 delete 混用没想清楚语义；磁盘只按总量告警不看"段数/索引大小"。
 * ============================================================
 */

package com.heima.ch62;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class LogStorage {
    record Record(long offset, long timestamp, String key, String value) {
    }

    record Segment(long baseOffset, long baseTimestamp, List<Record> records) {
        long lastOffset() {
            return records.isEmpty() ? baseOffset - 1 : records.get(records.size() - 1).offset();
        }

        long sizeHint() {
            return records.size();                     // 简化：条数代替字节数
        }
    }

    /** 稀疏索引：每 interval 条记录一个索引项 */
    static Map<Long, Integer> sparseIndex(Segment seg, int interval) {
        Map<Long, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < seg.records().size(); i += interval) {
            index.put(seg.records().get(i).offset(), i);
        }
        return index;
    }

    /** 按 offset 查找：选段 -> 索引定位 -> 顺序扫 */
    static String find(List<Segment> segments, long target, int interval) {
        Segment chosen = null;
        for (Segment s : segments) {
            if (s.baseOffset() <= target && target <= s.lastOffset()) {
                chosen = s;
                break;
            }
        }
        if (chosen == null) {
            return "offset " + target + " 不在任何段（已被 retention 删除）";
        }
        Map<Long, Integer> index = sparseIndex(chosen, interval);
        int start = 0;
        for (Map.Entry<Long, Integer> e : index.entrySet()) {
            if (e.getKey() <= target) {
                start = e.getValue();
            } else {
                break;
            }
        }
        int scanned = 0;
        for (int i = start; i < chosen.records().size(); i++) {
            scanned++;
            if (chosen.records().get(i).offset() == target) {
                return "命中 " + chosen.records().get(i).value()
                        + "（段 " + chosen.baseOffset() + "，从索引位置扫了 " + scanned + " 条）";
            }
        }
        return "未找到";
    }

    public static void main(String[] args) {
        List<Segment> segments = new ArrayList<>();
        long offset = 0;
        for (int segIdx = 0; segIdx < 3; segIdx++) {
            List<Record> records = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                records.add(new Record(offset, 1000 + offset, "k" + (offset % 4), "v" + offset));
                offset++;
            }
            segments.add(new Segment(segIdx * 10L, 1000 + segIdx * 10L, records));
        }
        System.out.println("【段文件布局】");
        segments.forEach(s -> System.out.printf("  段 base=%d..%d（%d 条）%n",
                s.baseOffset(), s.lastOffset(), s.records().size()));

        System.out.println();
        System.out.println("【稀疏索引示例（间隔 4）】");
        System.out.println("  段 0 索引：" + sparseIndex(segments.get(0), 4));

        System.out.println();
        System.out.println("【按 offset 查找】");
        System.out.println("  " + find(segments, 3, 4));
        System.out.println("  " + find(segments, 22, 4));
        System.out.println("  " + find(segments, 99, 4));
        System.out.println();
        System.out.println("  retention 删除整个段（不是逐条），offset 25 被删后新消息从 25 继续");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现段与索引模拟器：1000 条消息切成 5 段，验证"按 offset 查找只扫 1 段 + 索引后的少数几条"。
 * 2. 实现 compact 策略：按 key 保留最新值，用 20 条同 key 的记录验证结果（对比 delete 策略）。
 * 3. 思考题：为什么 Kafka 用稀疏索引而不是 B+ 树？从"顺序追加 + 顺序扫描"的访问模式与写入成本分析。
 * ============================================================
 */
