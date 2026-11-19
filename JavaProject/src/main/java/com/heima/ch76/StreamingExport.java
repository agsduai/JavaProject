/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-02
 * 课名：流式导出与内存控制
 * ------------------------------------------------------------
 * 知识点：
 *   - **流式导出的核心：边查边写，内存恒定**——**游标查询**（`id > lastId ORDER BY id LIMIT n`）逐批取数，**写出后立即丢弃**，内存里只有"一批"（如 1000 行）。
 *   - **三种分页方式对比**：**offset/limit**（深分页越翻越慢，且有漂移 76-01）、**主键游标**（`WHERE id > ? ORDER BY id LIMIT n`，稳定高效）、**keyset/复合游标**（多列排序时用 `(create_time, id)` 元组）——**导出首选游标**。
 *   - **写出端也要流式**：`BufferedWriter`/`OutputStream` 直接写文件，**不要先拼成 `List<String>` 再写**（那就是全量内存）——**查询流与写入流"背压"对齐**（快慢不匹配时用有界队列 43-04）。
 *   - **JVM 视角**：**导出是"长事务 + 大对象"的高危场景**——控制批大小（1000~5000）、避免 `String` 拼接（用 `StringBuilder` 复用）、必要时 `-Xmx` 之外用堆外/临时文件。
 *   - 与 54/46 章的关系：与"批量特征加载""列裁剪"同源——**"一次只处理一小片"是所有大数据处理的第一原则**。
 *   - 易错点：`findAll()` 全量查（OOM 第一杀手）；offset 深分页（越翻越慢）；每行一次 DB 查询（N+1 的导出版）；不关流（文件句柄泄露）。
 * ============================================================
 */

package com.heima.ch76;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class StreamingExport {
    record Order(long id, String customer, double amount, String createdAt) {
    }

    /** 模拟 20 万行订单数据（仅元信息，不真存） */
    static final int TOTAL_ROWS = 200_000;

    static Order fakeOrder(long id) {
        return new Order(id, "客户" + (id % 1000), 10.0 + (id % 500) / 10.0,
                "2026-11-" + String.format("%02d", (id % 28) + 1));
    }

    /** 模拟 DB：游标分页（id > lastId） */
    static List<Order> queryAfter(long lastId, int limit) {
        List<Order> out = new ArrayList<>(limit);
        long start = lastId + 1;
        long end = Math.min(TOTAL_ROWS, start + limit - 1);
        for (long id = start; id <= end; id++) {
            out.add(fakeOrder(id));
        }
        return out;
    }

    /** 流式导出：游标 + 边写边丢 */
    static String exportStreaming(int batchSize, int maxRows) throws IOException {
        StringWriter sink = new StringWriter();
        BufferedWriter writer = new BufferedWriter(sink);
        writer.write("id,customer,amount,createdAt\n");

        long lastId = 0;
        int written = 0;
        int batches = 0;
        int peakRowsInMemory = 0;
        while (written < maxRows) {
            int limit = Math.min(batchSize, maxRows - written);
            List<Order> batch = queryAfter(lastId, limit);
            if (batch.isEmpty()) {
                break;
            }
            for (Order o : batch) {
                writer.write(o.id() + "," + o.customer() + ","
                        + o.amount() + "," + o.createdAt() + "\n");
            }
            lastId = batch.get(batch.size() - 1).id();
            written += batch.size();
            batches++;
            peakRowsInMemory = Math.max(peakRowsInMemory, batch.size());
            // batch 出作用域即可被回收：内存里永远只有一批
        }
        writer.flush();
        System.out.printf("  流式：写入 %d 行，%d 批，内存峰值 %d 行，输出 %d 字节%n",
                written, batches, peakRowsInMemory, sink.toString().length());
        return sink.toString();
    }

    /** 反例：全量 load 再写（仅演示内存占用，不真跑 20 万） */
    static void naiveMemory() {
        System.out.println("【反例：全量 load】");
        System.out.println("  List<Order> all = orderMapper.findAll();  // 20 万行对象");
        System.out.println("  每行约 200 字节 -> 约 40MB，还要 + 字符串拼接的临时对象");
        System.out.println("  若 100 万行 / 字段更多 -> 数百 MB，直接 OOM");
        System.out.println("  正解：游标分批，内存 = 一批（1000 行 ≈ 200KB）");
        System.out.println();
    }

    static void cursorVsOffset() {
        System.out.println("【游标 vs offset】");
        String[][] rows = {
                {"第 1 页", "LIMIT 1000 OFFSET 0", "id > 0 LIMIT 1000"},
                {"第 100 页", "LIMIT 1000 OFFSET 99000（扫描 10 万行）", "id > 99000 LIMIT 1000"},
                {"第 1000 页", "LIMIT 1000 OFFSET 999000（扫描 100 万行）", "id > 999000 LIMIT 1000"},
                {"数据漂移", "导出中插入/删除会重合或漏行", "按 id 单调前进，不重不漏"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s offset=%-34s 游标=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) throws IOException {
        cursorVsOffset();
        naiveMemory();
        System.out.println("【流式导出 20 万行】");
        long t0 = System.nanoTime();
        exportStreaming(1000, TOTAL_ROWS);
        System.out.printf("  耗时 %d ms%n", (System.nanoTime() - t0) / 1_000_000);
        System.out.println();
        System.out.println("  关键：查询流 -> 写入流，批间不累积；批大小 1000~5000 是常见甜点");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现游标分页导出 10 万行，统计"内存峰值行数"与总耗时，对比全量 load。
 * 2. 把单列游标扩展为复合游标 `(createdAt, id)`，支持"按时间排序 + 分页稳定"。
 * 3. 思考题：为什么"扫描 100 万行 offset 很慢"？从 B+ 树"定位 vs 遍历"分析（54 章索引）。
 * ============================================================
 */
