/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：第15-36章综合卷 卷一
 * 课名：业务核心综合（集合 + IO + 多线程 + 网络）
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现「订单批处理管道」：① 读取订单 CSV 文件（含脏数据行）；② 多线程分片解析与校验（脏数据收集进错误清单，不中断）；③ Stream 聚合统计（按城市/状态）；④ 结果写回结果文件；⑤ 通过 TCP 上报处理进度（本地 HttpServer 或 Socket 接收端模拟）。验收：脏行不丢单不阻塞、并发校验正确（与串行一致）、进度可观测。
 *   - 【考核点】IO 读与解析容错、多线程分片与聚合正确性、Stream 统计、网络上报、管道编排。
 *   - 【易错点】解析异常中断全批（应收集错误继续）；并发聚合结果与串行不一致（无同步聚合）；进度上报阻塞主流程（应异步）。
 *   - 【提示】分片策略：按行号取模分给 N 个线程，各线程独立校验后合并——正确性用"并行结果 == 串行结果"验证。
 * ============================================================
 */

package com.heima.practice.midexam;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class MidBusinessExam {
    record Order(String id, long amountCents) {
    }

    static List<Order> parse(List<String> lines, List<String> errors) {
        List<Order> ok = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String[] p = lines.get(i).split(",");
            try {
                ok.add(new Order(p[0], Long.parseLong(p[1])));
            } catch (Exception e) {
                errors.add("第" + (i + 1) + "行脏数据：" + lines.get(i));
            }
        }
        return ok;
    }

    public static void main(String[] args) {
        List<String> errors = new ArrayList<>();
        List<Order> orders = parse(List.of("9001,6900", "bad,line", "9002,3900"), errors);
        System.out.println("  有效订单：" + orders.size() + "  脏行：" + errors);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整实现五段管道（IO->并发解析->统计->写回->进度上报），10 万行模拟数据跑通全链。
 * 2. 实现"并行结果 == 串行结果"的等价性验证（统计快照比对），并故意制造一次不等价定位原因。
 * 3. 输出设计说明：分片策略、容错策略、上报频率三项的决策理由（52-04 取舍模板）。
 * ============================================================
 */
