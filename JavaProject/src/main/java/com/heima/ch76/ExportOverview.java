/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-01
 * 课名：报表导出全景
 * ------------------------------------------------------------
 * 知识点：
 *   - **报表导出的四条链路**：**数据查询**（分页/流式 76-02）-> **格式生成**（CSV/Excel/PDF 76-04~76-06）-> **文件落地**（本地/对象存储 73 章）-> **通知下载**（下载中心 76-09）——**任何一环出错都会让用户拿到半个文件**。
 *   - **同步 vs 异步的分界线**：**小报表（< 5 万行、< 10 秒）同步直出**；**大报表（百万行、分钟级）走异步任务**（76-08）——**按"用户能等多久"与"资源占用"选**。
 *   - **导出的三个隐形要求**：**数据一致性**（导出的是某一时刻的快照，不是边导边变）、**可重入**（失败可重试，不产生重复文件）、**可审计**（谁在何时导了什么，76-10）。
 *   - 与 73 章的关系：导出文件就是"大文件"，**分片/断点/签名 URL 的机制全部适用**——本章聚焦"怎么把数据变成文件"，73 章聚焦"文件怎么存怎么发"。
 *   - 易错点：百万行全量load进内存（OOM）；导出过程数据在变（金额对不上）；无进度（用户以为卡死）；重复点击生成多份（76-08 幂等）。
 * ============================================================
 */

package com.heima.ch76;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ExportOverview {
    enum Mode { SYNC, ASYNC }

    record ExportRequest(String reportType, int estimatedRows, String format) {
    }

    record Decision(Mode mode, String reason) {
    }

    /** 决策：按行数 + 格式耗时估算 */
    static Decision decide(ExportRequest req) {
        long estimatedMs = switch (req.format()) {
            case "CSV" -> req.estimatedRows() / 50_000L;
            case "XLSX" -> req.estimatedRows() / 5_000L;
            case "PDF" -> req.estimatedRows() / 500L;
            default -> throw new IllegalArgumentException("不支持格式 " + req.format());
        };
        boolean tooBig = req.estimatedRows() > 50_000 || estimatedMs > 10_000;
        if (tooBig) {
            return new Decision(Mode.ASYNC,
                    "预计 " + req.estimatedRows() + " 行 / " + estimatedMs + "ms，超过同步阈值");
        }
        return new Decision(Mode.SYNC,
                "预计 " + req.estimatedRows() + " 行 / " + estimatedMs + "ms，同步直出");
    }

    static void pipeline() {
        System.out.println("【导出四段链路】");
        String[][] stages = {
                {"1. 数据查询", "分页或游标流式读取", "不能全量 load 进内存（76-02）"},
                {"2. 格式生成", "CSV/Excel/PDF 逐行写", "边查边写，内存恒定"},
                {"3. 文件落地", "本地临时文件 -> 对象存储", "73 章：分片/校验/生命周期"},
                {"4. 通知下载", "生成下载链接/站内信", "76-09 下载中心 + 签名 URL"},
        };
        for (String[] s : stages) {
            System.out.printf("  %-12s %-22s %s%n", s[0], s[1], s[2]);
        }
        System.out.println();
    }

    static void consistency() {
        System.out.println("【数据一致性：快照 vs 边导边变】");
        System.out.println("  反例：第 1 页查到 100 条，导出第 2 页时数据被删了 10 条");
        System.out.println("        -> 总行数对不上，用户投诉「报表不准」");
        System.out.println("  正解一：按主键游标（id > lastId），避免 offset 漂移");
        System.out.println("  正解二：固定快照（导出开始时记录 maxId / 时间点）");
        System.out.println("  正解三：读从库 + 一致性快照读（RC/RR 隔离级别）");
        System.out.println();
    }

    static void idempotent() {
        System.out.println("【可重入：同一请求只生成一份】");
        Map<String, String> jobByKey = new LinkedHashMap<>();
        String key = "user-1:order-report:2026-11-18";
        String first = jobByKey.computeIfAbsent(key, k -> "job-1001");
        String second = jobByKey.computeIfAbsent(key, k -> "job-1002");
        System.out.println("  第一次点击 -> " + first);
        System.out.println("  重复点击   -> " + second + "（复用同一任务，不重复生成）");
        System.out.println("  幂等键 = 用户 + 报表类型 + 参数摘要");
        System.out.println();
    }

    public static void main(String[] args) {
        pipeline();
        consistency();
        idempotent();

        System.out.println("【同步/异步决策】");
        List<ExportRequest> reqs = new ArrayList<>(List.of(
                new ExportRequest("订单明细", 1_000, "XLSX"),
                new ExportRequest("订单明细", 200_000, "XLSX"),
                new ExportRequest("对账单", 5_000_000, "CSV"),
                new ExportRequest("发票", 2_000, "PDF")));
        for (ExportRequest r : reqs) {
            Decision d = decide(r);
            System.out.printf("  %-6s %8d 行 %-5s -> %-5s（%s）%n",
                    r.reportType(), r.estimatedRows(), r.format(), d.mode(), d.reason());
        }

        System.out.println();
        System.out.println("【验收】内存恒定、快照一致、任务幂等、可审计（76-10）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的业务设计"同步/异步"分界线，给出 3 类报表的阈值与理由。
 * 2. 演示"offset 分页导出"的漂移问题：导出中删除数据导致总数对不上，改用主键游标修正。
 * 3. 思考题：为什么"导出的是快照"很重要？从"财务报表对账"场景分析数据一致性的业务影响。
 * ============================================================
 */
