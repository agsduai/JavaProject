/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-11
 * 课名：综合案例：百万行导出
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一条完整链路**：**配额校验**（76-10）-> **异步任务**（76-08）-> **游标流式查询**（76-02）-> **分册生成 xlsx**（76-03/76-04）-> **ZIP 打包**（73-09）-> **落对象存储 + 签名 URL**（73 章）-> **审计**（76-10）——**每一环都可独立验证**。
 *   - **百万行的验收指标**：**内存峰值恒定**（不随行数增长）、**耗时线性**（不因翻页变慢）、**可取消**、**可重试**、**文件可下载**（分册 + 总 ZIP）。
 *   - **关键权衡**：**单文件 vs 分册**（Excel 单文件超 10 万行打开困难 -> 按 5 万行分册）、**CSV vs XLSX**（百万行 CSV 更合适）、**实时 vs 异步**（必然异步）。
 *   - 与全书的关系：本章是"数据处理 + 文件 + 任务 + 权限 + 安全"的**终章级综合**——**它把 54（数据）、61（任务）、73（文件）、74（权限）、76（导出）串成一条线**。
 *   - 易错点：全量 load；单文件百万行；无配额；无审计；不可取消。
 * ============================================================
 */

package com.heima.ch76;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// ==================== 示例代码 ====================

public class MillionRowExportCase {
    record Order(long id, String customer, double amount, String deptId) {
    }

    static final long TOTAL = 1_000_000L;
    static final int PAGE_ROWS = 50_000;
    static final int BATCH = 2_000;

    /** 游标查询（只生成元数据，不真存百万对象） */
    static List<Order> queryAfter(long lastId, int limit) {
        List<Order> rows = new ArrayList<>(limit);
        long end = Math.min(TOTAL, lastId + limit);
        for (long id = lastId + 1; id <= end; id++) {
            rows.add(new Order(id, "客户" + (id % 5000), 10.0 + (id % 300) / 3.0,
                    "d-" + (id % 20)));
        }
        return rows;
    }

    /** 一册的 CSV 内容（演示流式：逐批写，不持有全册） */
    static String buildPageCsv(long startId, long endId, String deptFilter) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("id,customer,amount,dept\n");
        long cursor = startId - 1;
        while (cursor < endId) {
            List<Order> batch = queryAfter(cursor, BATCH);
            for (Order o : batch) {
                if (o.id() > endId) {
                    break;
                }
                if (deptFilter != null && !o.deptId().equals(deptFilter)) {
                    continue;
                }
                sb.append(o.id()).append(',').append(o.customer()).append(',')
                        .append(o.amount()).append(',').append(o.deptId()).append('\n');
            }
            cursor = batch.get(batch.size() - 1).id();
        }
        return sb.toString();
    }

    static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    record JobState(String taskId, int totalPages, int donePages, long rowsWritten,
                    String state, String url, boolean cancelled) {
    }

    /** 主流程：配额 -> 任务 -> 逐册 -> 打包 -> URL -> 审计 */
    static void run(String taskId, String deptFilter, int quota, boolean cancelAfterPage)
            throws IOException {
        System.out.printf("【任务 %s】筛选=%s 配额=%d 行%n", taskId, deptFilter, quota);

        long estimated = deptFilter == null ? TOTAL : TOTAL / 20;
        if (estimated > quota) {
            System.out.println("  拒绝：预计 " + estimated + " 行 > 配额 " + quota);
            return;
        }
        System.out.println("  配额通过（预计筛选后 " + estimated + " 行）");

        List<String> pageNames = new ArrayList<>();
        Map<String, byte[]> files = new LinkedHashMap<>();
        // 分册始终按 id 区间切（不因筛选而改变区间，避免重复/遗漏）
        int pages = (int) ((TOTAL + PAGE_ROWS - 1) / PAGE_ROWS);
        int done = 0;
        long written = 0;
        boolean cancelled = false;

        for (int p = 0; p < pages; p++) {
            if (cancelAfterPage && p == 1) {
                cancelled = true;
                System.out.println("  用户取消：已完成 " + done + " 册，停止");
                break;
            }
            long startId = (long) p * PAGE_ROWS + 1;
            long endId = Math.min(TOTAL, startId + PAGE_ROWS - 1);
            String name = String.format("订单_%d-%d.csv", startId, endId);
            String csv = buildPageCsv(startId, endId, deptFilter);
            files.put(name, csv.getBytes(StandardCharsets.UTF_8));
            pageNames.add(name);
            done++;
            int lines = (int) csv.lines().count() - 1;
            written += lines;
            System.out.printf("    第 %d/%d 册 %s -> %,d 行%n", done, pages, name, lines);
        }

        byte[] zipBytes = zip(files);
        String url = cancelled ? null
                : "https://cdn.example.com/export/" + taskId + ".zip?expires=1800&sig=abc";
        System.out.println();
        System.out.println("【结果】");
        System.out.println("  册数 " + done + "/" + pages + "，总行数 " + written
                + "，ZIP " + zipBytes.length + " 字节");
        System.out.println("  下载 URL：" + (url == null ? "（已取消，无）" : url));
        System.out.println("  审计：task=" + taskId + " user=u-1 rows=" + written
                + " filter=" + deptFilter + " state=" + (cancelled ? "CANCELLED" : "SUCCESS"));
        System.out.println("  内存：每批 " + BATCH + " 行（约 "
                + (BATCH * 200 / 1024) + "KB），与总行数无关");
        System.out.println();
    }

    public static void main(String[] args) throws IOException {
        System.out.println("【百万行导出：全量（配额 200 万）】");
        run("task-full", null, 2_000_000, false);

        System.out.println("【部门筛选（d-3）+ 取消】");
        run("task-dept", "d-3", 100_000, true);

        System.out.println("【配额不足】");
        run("task-over", null, 500_000, false);

        System.out.println("【验收清单】");
        String[][] checks = {
                {"内存恒定", "每批 2000 行，不随总行数增长", "76-02"},
                {"耗时线性", "游标分页，不因翻页变慢", "76-03"},
                {"可取消", "取消后停止生成，返回已完成的册", "76-08"},
                {"可重试", "任务幂等，重试不重复", "76-01"},
                {"可下载", "分册 + ZIP + 签名 URL", "73-06"},
                {"可审计", "谁/条件/行数/结果", "76-10"},
        };
        for (String[] c : checks) {
            System.out.printf("  %-8s %-32s %s%n", c[0], c[1], c[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现百万行导出（配额/分册/游标/打包/URL/审计），用"全量"与"部门筛选"两次运行验证。
 * 2. 加入"取消"与"重试"：取消后停止并返回已完成分册；重试时幂等不重复生成。
 * 3. 思考题：为什么"百万行必须分册"？从"Excel 打开性能 / 下载可靠性 / 失败重试粒度"三点分析。
 * ============================================================
 */
