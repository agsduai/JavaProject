/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-03
 * 课名：大数据量分页导出
 * ------------------------------------------------------------
 * 知识点：
 *   - **"分页导出"有两种含义**：**按页导出多个文件**（每页一个 Excel，适合"用户要分批看"）与**内部按页取数**（导出仍是单文件，76-02 的游标）——**先分清需求**，别把后者做成前者。
 *   - **按页多文件的设计**：**页大小固定**（如每册 5 万行）、**文件名带页码**（`订单_1-50000.xlsx`）、**打包 ZIP**（73-09）——**单文件不超 2 万行是 Excel 的舒适区**（76-04 的流式写入也有内存收益）。
 *   - **并发导出**：**多批并行查 + 顺序写**（保证行序）或**并行写各自分片再合并**——**并行度受 DB 连接池与下游限流约束**（44-08），**不是越高越好**。
 *   - **进度与取消**：**记录已处理行数**（76-08 的任务状态）、**支持取消**（用户关页面/主动取消，任务可中断 61-03）——**长任务必须可观测可中断**。
 *   - 与 39/61 章的关系：与"批量任务的分片并行"同构——**分片键、断点续传、幂等**三件套（61-08 的 DAG 编排）。
 *   - 易错点：并发度 = 线程数（连接池打爆）；分页按 offset（越后越慢）；不让取消（用户等到崩溃）；单 Excel 百万行（打开即卡死）。
 * ============================================================
 */

package com.heima.ch76;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// ==================== 示例代码 ====================

public class PagedExport {
    record Chunk(int pageNo, long startId, long endId, int rows) {
    }

    static final long TOTAL = 1_000_000L;
    static final int ROWS_PER_PAGE = 50_000;

    /** 分片：按 id 区间切（避免 offset 漂移） */
    static List<Chunk> plan() {
        List<Chunk> chunks = new ArrayList<>();
        long start = 1;
        int page = 1;
        while (start <= TOTAL) {
            long end = Math.min(TOTAL, start + ROWS_PER_PAGE - 1);
            chunks.add(new Chunk(page++, start, end, (int) (end - start + 1)));
            start = end + 1;
        }
        return chunks;
    }

    static void sequentialPlan() {
        System.out.println("【分页方案】");
        List<Chunk> chunks = plan();
        System.out.printf("  总行数 %,d / 每页 %,d -> %d 个文件%n",
                TOTAL, ROWS_PER_PAGE, chunks.size());
        chunks.forEach(c -> System.out.printf("    第 %d 页：id %d~%d（%d 行）-> 订单_%d-%d.xlsx%n",
                c.pageNo(), c.startId(), c.endId(), c.rows(), c.startId(), c.endId()));
        System.out.println("  最后打包：订单导出_20261118.zip（73-09）");
        System.out.println();
    }

    /** 并发导出 + 进度 + 取消 */
    static void concurrent(boolean cancelAfterMs) throws Exception {
        System.out.println("【并发导出（4 线程）+ 进度 + 取消】");
        List<Chunk> chunks = plan();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        AtomicInteger done = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        AtomicBoolean stop = new AtomicBoolean(false);
        Map<Integer, Long> pageCost = new LinkedHashMap<>();

        long t0 = System.currentTimeMillis();
        List<Future<String>> futures = new ArrayList<>();
        for (Chunk c : chunks) {
            futures.add(pool.submit(() -> {
                if (stop.get()) {
                    cancelled.incrementAndGet();
                    return "第 " + c.pageNo() + " 页：已取消";
                }
                long start = System.currentTimeMillis();
                // 模拟生成：每 1 万行约 40ms
                long work = Math.max(20, c.rows() / 250L);
                try {
                    Thread.sleep(work);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "第 " + c.pageNo() + " 页：中断";
                }
                long cost = System.currentTimeMillis() - start;
                pageCost.put(c.pageNo(), cost);
                int n = done.incrementAndGet();
                System.out.printf("  完成 %d/%d：第 %d 页（%d 行，%d ms）%n",
                        n, chunks.size(), c.pageNo(), c.rows(), cost);
                return "第 " + c.pageNo() + " 页：完成";
            }));
        }

        if (cancelAfterMs) {
            Thread.sleep(60);
            stop.set(true);
            System.out.println("  >>> 用户取消（正在执行的批次会跑完，未开始的跳过）");
        }

        int ok = 0;
        for (Future<String> f : futures) {
            String s = f.get();
            if (s.endsWith("完成")) {
                ok++;
            }
        }
        pool.shutdown();
        long total = System.currentTimeMillis() - t0;
        System.out.printf("  汇总：完成 %d 页，取消/跳过 %d 页，耗时 %d ms%n",
                ok, cancelled.get(), total);
        System.out.println("  注意：并发度受连接池限制（4 线程 = 4 连接），不是越高越好");
        System.out.println();
    }

    public static void main(String[] args) throws Exception {
        sequentialPlan();
        concurrent(false);
        concurrent(true);
        System.out.println("【要点】id 区间分片（不重不漏）/ 并发受连接池约束 / 进度可观测 / 可取消");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 100 万行的分页导出计划（id 区间 + 文件名），验证"不重不漏"。
 * 2. 实现并发导出：4 线程 + 进度统计 + 取消开关，观察"执行中批次跑完、未开始跳过"的语义。
 * 3. 思考题：为什么"并发度 = 线程数"是危险假设？从"DB 连接池/下游限流"分析（44-08）。
 * ============================================================
 */
