/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-08
 * 课名：数据倾斜与作业调优
 * ------------------------------------------------------------
 * 知识点：
 *   - **数据倾斜是 Spark 性能问题的第一大杀手**：某个 key（爆款商品/大卖家/默认值 null）占数据量的 30% 以上——**Shuffle 后某个 Task 处理量是其他 Task 的几十倍**，整个 Stage 等这一个 Task（长尾）。
 *   - **识别方法**：Spark UI 看 Task 的 Shuffle Read Size 分布（最大/中位数比值 > 3 即倾斜）；`groupBy(key).count().orderBy(desc)` 看 top key 占比；**AQE 的动态倾斜处理会自动发现**（59-07）。
 *   - **五种解法**（按成本排序）：① **过滤/预处理**（null 或异常 key 提前剔除，最常见）；② **提高并行度**（对轻度倾斜有效，治标）；③ **两阶段聚合（加盐打散）**（39-06/43-08 同款：key 加随机后缀局部聚合再全局聚合）；④ **大 key 单独处理**（拆成独立作业/广播小表思路）；⑤ **Broadcast Join**（如果倾斜发生在 join 且一侧可广播）。
 *   - **倾斜的根因常在业务**：默认值（`user_id = null` 或 `-1` 的兜底数据全挤一个 key）、热点商品、测试数据混入——**治理数据比调参更治本**。
 *   - **调优的优先级**：消除倾斜 > 减少 Shuffle > 调整并行度 > 调内存参数——**先看 Spark UI 的 Task 分布，再决定改什么**（不看数据就调参是赌博）。
 *   - 易错点：只加并行度（倾斜 Task 还是那个 key，更碎但更慢）；加盐后忘记二次聚合（结果错）；不处理 null key（全挤到 default 分区）；调大 executor 内存解决倾斜（只是让单 Task 晚点 OOM）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// ==================== 示例代码 ====================

public class SkewDemo {
    record Row(String key, long amount) {
    }

    /** 评估倾斜：输出各分区数据量、最大/中位比 */
    static void diagnose(List<Row> rows, int partitions) {
        Map<Integer, Integer> perPartition = new LinkedHashMap<>();
        for (Row r : rows) {
            int p = Math.abs(r.key().hashCode()) % partitions;
            perPartition.merge(p, 1, Integer::sum);
        }
        List<Integer> sizes = new ArrayList<>(perPartition.values());
        sizes.sort(Integer::compare);
        int max = sizes.get(sizes.size() - 1);
        int median = sizes.get(sizes.size() / 2);
        System.out.printf("  分区数=%d 各分区行数=%s%n", partitions, perPartition);
        System.out.printf("  最大/中位 = %d/%d = %.1f 倍 -> %s%n", max, median,
                (double) max / median, (double) max / median > 3 ? "倾斜！" : "正常");
    }

    /** 两阶段聚合：加盐 -> 局部聚合 -> 去盐 -> 全局聚合 */
    static Map<String, Long> saltedAggregate(List<Row> rows, int saltBuckets) {
        Random rnd = new Random(7);
        Map<String, Long> local = new LinkedHashMap<>();
        for (Row row : rows) {
            String saltedKey = row.key() + "#" + rnd.nextInt(saltBuckets);
            local.merge(saltedKey, row.amount(), Long::sum);
        }
        Map<String, Long> global = new LinkedHashMap<>();
        local.forEach((saltedKey, sum) -> {
            String originalKey = saltedKey.substring(0, saltedKey.lastIndexOf('#'));
            global.merge(originalKey, sum, Long::sum);
        });
        return global;
    }

    public static void main(String[] args) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            rows.add(new Row("hot-sku", 100));            // 80% 集中在一个 key
        }
        for (int i = 0; i < 2500; i++) {
            rows.add(new Row("sku-" + i, 100));
        }
        System.out.println("【倾斜诊断】");
        diagnose(rows, 8);
        System.out.println();
        System.out.println("【两阶段聚合（加盐 16 桶）】");
        Map<String, Long> result = saltedAggregate(rows, 16);
        System.out.println("  hot-sku 聚合结果：" + result.get("hot-sku") + "（正确性保持）");
        System.out.println("  局部聚合桶数：" + 16 + " -> 单桶负载降到 1/16");
        System.out.println();
        System.out.println("  治本：查清 hot-sku 为什么这么大（爆款/测试数据/默认值）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现倾斜诊断器（分区数据量分布 + 最大/中位比 + top key 占比），用 3 组模拟数据（正常/轻度/严重）验证。
 * 2. 实现完整两阶段聚合，与单阶段结果对拍（正确性），并对比两种方式的"最大分区负载"。
 * 3. 思考题：为什么"只提高并行度"不能解决倾斜？从"同一 key 必然落同一分区"的哈希规则推导。
 * ============================================================
 */
