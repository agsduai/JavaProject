/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：48-01
 * 课名：推理与训练的分界
 * ------------------------------------------------------------
 * 知识点：
 *   - **训练与推理是两种完全不同的负载**：训练（几天离线，追求吞吐，梯度计算，需要全量数据）vs 推理（7×24 在线，追求延迟与并发，只做前向计算）。**Java 工程师日常面对的是推理服务**——它是“一个特殊的常驻服务”，可以套用全书的服务工程方法论（容量、降级、观测），但多了 GPU 与生成过程的特殊性。
 *   - **自回归生成的两阶段**：**prefill（预填充）**——把全部输入 prompt 一次性算完（并行，算力密集）；**decode（解码）**——逐 token 生成，每步只算一个 token（访存密集，受显存带宽限制）。**两阶段的资源画像完全不同**：prefill 吃算力，decode 吃带宽——这是推理优化的主线（48-03 的 batching 复杂性来源）。
 *   - **延迟的两个指标**：**TTFT**（首 token 时间，主要由 prefill 决定，用户感知“多快开始回答”）与 **TPOT/ITL**（每 token 时间，用户感知“打字机速度”）。**SLO 要分开承诺**：TTFT < 800ms、TPOT < 50ms 是典型目标。
 *   - **推理服务与传统服务的差异点**：GPU 是稀缺且单价的（容量规划按卡算）；请求处理时间与输出长度成正比（不可预知，排队模型复杂）；批处理能大幅提升吞吐但延迟随之波动（48-03）。
 *   - **Java 的位置**：推理引擎本体（vLLM/TensorRT-LLM）是 Python/C++，但**服务化层全是 Java 的领地**：网关路由（44-08）、排队与限流、会话管理、可观测、成本归因——48 章的服务端工程都在 Java 侧。
 *   - **按输出长度规划容量**：平均输出 300 token 的请求处理时间 ≈ 300 × TPOT + TTFT——**输出长度分布是容量计算的输入**，必须在真实流量中测量而不是拍脑袋。
 *   - 易错点：把推理请求当普通 HTTP 请求做容量（忽略输出长度方差导致长尾爆炸）；TTFT 与总延迟混为一谈（SLO 无法落地）；忽略 prefill/decode 的资源差异（批配置盲目）；认为加机器线性扩吞吐（GPU 利用率与 batching 才是第一杠杆）。
 * ============================================================
 */

package com.heima.ch48;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class InferenceBasics {
    record Phase(String name, String work, String bottleneck, String metric) {
    }

    static void twoPhases() {
        System.out.println("【自回归生成：prefill 与 decode】");
        List<Phase> phases = List.of(
                new Phase("prefill", "一次算完整个输入 prompt（并行矩阵乘）", "GPU 算力", "决定 TTFT"),
                new Phase("decode", "每步只算 1 个新 token（循环 N 次）", "显存带宽（每步读全部权重/KV）", "决定 TPOT"));
        for (Phase p : phases) {
            System.out.printf("  %-7s %-32s 瓶颈=%-12s %s%n",
                    p.name(), p.work(), p.bottleneck(), p.metric());
        }
        System.out.println("  直觉：输入 2000 token 是“一口气”，输出 300 token 是“300 小步”");
        System.out.println();
    }

    static void latencyMath() {
        System.out.println("【单请求延迟估算（容量规划的原子公式）】");
        double ttft = 350;          // prefill 时间，随输入长度增长
        double tpot = 30;           // 每-token 时间
        int[] outputs = {100, 300, 1000};
        for (int out : outputs) {
            double total = ttft + out * tpot;
            System.out.printf("  输出 %4d token -> 总延迟 %5.0fms（TTFT %.0f + %d×%.0f）%n",
                    out, total, ttft, out, tpot);
        }
        System.out.println("  关键：输出长度方差决定延迟方差 —— 长尾来自“话痨回答”");
        System.out.println("  容量输入：真实流量的输出长度分布（P50/P95），不是平均值");
        System.out.println();
    }

    static void sloSplit() {
        System.out.println("【SLO 拆分：TTFT 与 TPOT 分开承诺】");
        String[][] rows = {
                {"TTFT", "< 800ms（P95）", "用户感知“开始响应”的速度", "受 prefill 与排队影响"},
                {"TPOT", "< 50ms（P95）", "打字机流畅度", "受 decode 带宽与批干扰影响"},
                {"总时长", "按输出长度分桶承诺", "短答/长答分开看", "混看会被长请求污染"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-7s %-20s %-18s %s%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println("  对照 42-05：推理服务的 SLI 天然多一个维度（输出长度）");
        System.out.println();
    }

    static void serviceLayers() {
        System.out.println("【推理服务的分层：Java 在哪一层】");
        List<String> layers = List.of(
                "GPU/内核层：CUDA、显存、NVLink（平台层）",
                "推理引擎层：vLLM/TensorRT-LLM（continuous batching、KV 管理）",
                "服务层：网关路由、排队限流、会话、降级 —— Java 领地",
                "业务层：Prompt 组装、RAG、工具（40/44 章）");
        for (String l : layers) {
            System.out.println("  " + l);
        }
        System.out.println("  分界清晰：引擎调参是平台组的事，服务工程是 Java 的事");
    }

    public static void main(String[] args) {
        twoPhases();
        latencyMath();
        sloSplit();
        serviceLayers();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用真实流量（或模拟）统计输出长度分布（P50/P95/P99），按本章公式估算三种分位的请求延迟。
 * 2. 为你的推理服务写 SLO：TTFT/TPOT/总时长分桶承诺，并说明每项的测量方法。
 * 3. 思考题：为什么 decode 阶段是“访存密集”而不是“算力密集”？从每步要读全部权重与 KV 的角度推导 batching 能省什么。
 * ============================================================
 */
