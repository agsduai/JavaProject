/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-10
 * 课名：Spark 与湖仓集成
 * ------------------------------------------------------------
 * 知识点：
 *   - **Spark 是湖表的"批处理主力写入方与读取方"**：`INSERT INTO`/`MERGE INTO` 写 Iceberg（46-05 快照提交），`INSERT OVERWRITE` 分区重写；**读时走 Iceberg 的元数据裁剪**（分区 + 文件统计 + 46-02 Row Group）。
 *   - **写入模式的正确选择**：**追加**（`INSERT INTO`，日志/事件类）、**分区覆盖**（`INSERT OVERWRITE PARTITION`，T+1 重算某天——幂等重跑的正确姿势 59-06）、**MERGE**（按主键 upsert，维表/CDC 合并——39-11 双写思想的湖端实现）。**避免"全表覆盖"**（重写全部数据，成本爆炸）。
 *   - **小文件治理是 Spark 写湖的必修课**：`coalesce`/`AQE` 控制输出文件数（59-04）；**写后异步 compaction**（46-06）；**目标文件 128MB~1GB**（46-02）。
 *   - **与 46 章的分层衔接**：ODS（CDC 流入）-> DWD（Spark 清洗 + 质量校验 46-08）-> DWS（预聚合）-> ADS（物化视图 46-07）——**Spark 作业是层与层之间的搬运工**。
 *   - **Spark + Iceberg 的优化点**：分区裁剪与列裁剪自动生效；**`MERGE INTO` 的写放大**（需读匹配文件 + 重写）；**分桶表**（bucketed）+ `bucket join` 可免 Shuffle（59-05）。
 *   - 易错点：全表覆盖写（每次重写 TB 级）；MERGE 无主键（笛卡尔匹配）；小文件不治理（读时分区数爆炸 59-04）；写湖不做幂等（重跑数据翻倍）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class SparkLakeDemo {
    record Row(long orderId, String dt, long amount, String status) {
    }

    /** 三种写入模式的语义对比 */
    static Map<String, List<Row>> applyWrite(String mode, List<Row> existing, List<Row> incoming) {
        Map<String, List<Row>> result = new LinkedHashMap<>();
        switch (mode) {
            case "append" -> {
                List<Row> all = new ArrayList<>(existing);
                all.addAll(incoming);
                result.put("append", all);
            }
            case "overwrite-partition" -> {
                String targetDt = incoming.get(0).dt();
                List<Row> kept = existing.stream().filter(r -> !r.dt().equals(targetDt)).toList();
                List<Row> merged = new ArrayList<>(kept);
                merged.addAll(incoming);
                result.put("overwrite-partition", merged);
            }
            default -> {                       // merge by primary key
                Map<Long, Row> map = new LinkedHashMap<>();
                for (Row r : existing) {
                    map.put(r.orderId(), r);
                }
                for (Row r : incoming) {
                    map.put(r.orderId(), r);
                }
                result.put("merge", new ArrayList<>(map.values()));
            }
        }
        return result;
    }

    /** 输出文件数评估：目标文件大小 */
    static int outputFiles(long outputBytes, long targetBytes) {
        return (int) Math.max(1, (outputBytes + targetBytes - 1) / targetBytes);
    }

    public static void main(String[] args) {
        List<Row> existing = new ArrayList<>(List.of(
                new Row(1, "2026-11-17", 6900, "PAID"),
                new Row(2, "2026-11-17", 3900, "PAID"),
                new Row(3, "2026-11-18", 12900, "PAID")));
        List<Row> incoming = List.of(
                new Row(1, "2026-11-17", 6900, "REFUNDED"),
                new Row(4, "2026-11-18", 500, "PAID"));

        System.out.println("【append：重复行风险】" + applyWrite("append", existing, incoming).get("append"));
        System.out.println("【merge：按主键覆盖】" + applyWrite("merge", existing, incoming).get("merge"));
        System.out.println();

        long outputBytes = 200L * 1024 * 1024 * 1024;    // 200GB
        System.out.println("【输出文件数】200GB / 目标 512MB -> "
                + outputFiles(outputBytes, 512L << 20) + " 个文件");
        System.out.println("  小于 128MB 的文件视为碎文件 -> 触发 compaction（46-06）");
        System.out.println();
        System.out.println("  幂等重跑：分区覆盖或 MERGE，绝不用全表覆盖");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三种写入模式的模拟（append/分区覆盖/MERGE），构造"重跑两次"验证哪种模式幂等。
 * 2. 为 500GB 日增数据设计 Spark 写湖方案（分区策略、目标文件大小、compaction 频率）。
 * 3. 思考题：为什么"分区覆盖"是 T+1 重算的最佳模式？从"只影响目标分区"与"其他分区数据不重写"两个角度分析。
 * ============================================================
 */
