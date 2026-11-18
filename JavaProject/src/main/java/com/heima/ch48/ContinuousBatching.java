/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：48-03
 * 课名：Continuous Batching
 * ------------------------------------------------------------
 * 知识点：
 *   - **为什么 batching 是吞吐的第一杠杆**：decode 是访存密集——单请求每步读全部权重只产出 1 个 token，GPU 利用率极低；**把多个请求的 decode 步骤拼在一起**，一次读权重产出 N 个 token——**权重读取成本被 N 摊薄**，吞吐近似线性提升（直到算力饱和）。
 *   - **static batching 的浪费**：传统批把一组请求凑满一起算、一起结束——**短请求等长请求**（批内最长的决定整批时间），GPU 大量空转。**continuous batching**：每步迭代动态进出——完成的请求立刻退出、新请求立刻加入，GPU 永远满负荷。
 *   - **吞吐与延迟的交换曲线**：批越大吞吐越高、TPOT 越差（每步算更多请求）。**批次上限是 SLO 旋钮**：TPOT < 50ms 的约束反解出最大并发批（受显存与算力双重限制）。压测画出“并发-吞吐-TPOT”曲线是容量规划的核心实验。
 *   - **prefill 与 decode 的干扰**：一个长 prefill（2 万 token 输入）会把同批所有 decode 卡住（TTFT/ITL 尖刺）——**prefill 与 decode 分离调度**（分批/分实例）是 vLLM 新版与专用推理集群的方向。
 *   - **Java 侧的配合**：网关的排队深度与引擎的并发上限要匹配——**在引擎前排队（网关）而不是让引擎内部无限排队**（44-08 超时预算 + 42 章限流的推理版）。
 *   - 易错点：用 static batching 思维估吞吐（实际 continuous 提升数倍）；批上限拍脑袋（压测曲线说话）；长 prefill 与 decode 混跑（ITL 尖刺归因错误）；网关不限流直接打引擎（队列积压在引擎内，TTFT 失控）。
 * ============================================================
 */

package com.heima.ch48;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ContinuousBatching {
    static void staticWaste() {
        System.out.println("【static batching 的空转：批内最长决定一切】");
        record Req(String id, int outTokens) {
        }
        List<Req> batch = List.of(
                new Req("r1", 40), new Req("r2", 380), new Req("r3", 120), new Req("r4", 50));
        int maxOut = batch.stream().mapToInt(Req::outTokens).max().orElse(0);
        int wasted = 0;
        for (Req r : batch) {
            wasted += maxOut - r.outTokens();
            System.out.printf("  %s 输出 %3d token -> 空转 %3d 步%n", r.id(), r.outTokens(),
                    maxOut - r.outTokens());
        }
        System.out.printf("  批结束于最长者 %d token；累计空转 %d 步（GPU 白跑）%n", maxOut, wasted);
        System.out.println("  continuous batching：r1 完成即退出，槽位立刻给新请求");
        System.out.println();
    }

    static void throughputCurve() {
        System.out.println("【并发-吞吐-TPOT 的压测曲线（示意）】");
        record Point(int concurrency, double tokPerSec, double tpotMs) {
        }
        List<Point> points = List.of(
                new Point(1, 45, 22), new Point(8, 320, 25), new Point(16, 580, 28),
                new Point(32, 820, 39), new Point(64, 880, 73), new Point(128, 870, 148));
        for (Point p : points) {
            String flag = p.tpotMs() > 50 ? "  <- 超出 TPOT SLO" : "";
            System.out.printf("  并发%4d 吞吐%6.0f tok/s  TPOT=%3.0fms%s%n",
                    p.concurrency(), p.tokPerSec(), p.tpotMs(), flag);
        }
        System.out.println("  最优工作点：吞吐增长放缓且 TPOT 触线的位置（本例 ≈ 32~48 并发）");
        System.out.println("  旋钮：max_num_seqs（引擎批上限）+ 网关排队深度");
        System.out.println();
    }

    static void prefillInterference() {
        System.out.println("【长 prefill 干扰 decode 的尖刺】");
        List<String> events = List.of(
                "10:00:01 批内 32 个请求正常 decode，TPOT 稳定 30ms",
                "10:00:03 新请求 A 进入：prompt 20000 token -> prefill 占用整批 1.8s",
                "10:00:03~05 同批 decode 全部停顿 -> ITL 尖刺 1800ms（用户感知卡顿）",
                "对策：prefill 分批上限（chunked prefill）/ 专用 prefill 实例");
        for (String e : events) {
            System.out.println("  " + e);
        }
        System.out.println("  归因提示：ITL 尖刺与“长输入请求到达”时间对齐 -> prefill 干扰");
        System.out.println();
    }

    static void gatewayCoop() {
        System.out.println("【网关与引擎的排队分工】");
        List<String> rules = List.of(
                "引擎只暴露安全并发上限（压测确定，如 32）",
                "网关在引擎前排队：FIFO + 按优先级（VIP 请求插队）",
                "排队超时 > 网关超时预算（44-08）：等不到就降级（切小模型/本地）",
                "排队深度暴露为指标（42 章）：depth 持续增长 = 容量不足信号");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  反例：网关不限流，引擎内部排队 -> TTFT 无界增长，SLO 全灭");
    }

    public static void main(String[] args) {
        staticWaste();
        throughputCurve();
        prefillInterference();
        gatewayCoop();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 压测你的推理服务（或用模拟数据），画出“并发-吞吐-TPOT”曲线，标出满足 TPOT<50ms 的最大并发与对应吞吐。
 * 2. 模拟 static 与 continuous batching：给定一批不同输出长度的请求，对比总完成时间与 GPU 空转步数。
 * 3. 思考题：为什么“排队放在网关而不是引擎”能保住 TTFT？从排队策略灵活性（优先级/降级）与引擎批上限的关系分析。
 * ============================================================
 */
