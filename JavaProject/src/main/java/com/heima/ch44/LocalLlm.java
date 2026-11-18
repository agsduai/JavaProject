/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：44-05
 * 课名：本地模型部署与量化
 * ------------------------------------------------------------
 * 知识点：
 *   - **为什么要本地部署**：数据不出域（合规硬约束）、网络延迟（内网高频调用）、成本（海量低价值任务自建更便宜）、可用性（内网断网也能跑）。**云端 API 与本地模型不是二选一，是路由问题（44-08）**。
 *   - **本地部署的主流形态**：Ollama（最易上手，一键拉模型）、vLLM（生产级高吞吐，PagedAttention）、llama.cpp（CPU/边缘设备）。Java 对接 OpenAI 兼容接口——**Ollama/vLLM 都暴露 OpenAI 风格 API，40-02 的对接代码原样复用**。
 *   - **模型大小的现实感**：7B 模型（FP16 权重约 14GB）单卡 24G 可跑；70B 需要 4 卡或量化。**参数量 × 精度 = 显存需求**，选型的第一问是"你有什么卡"。
 *   - **量化（quantization）用精度换显存**：FP16 -> INT8（体积减半，质量损失极小）-> INT4（再减半，损失可感知）。主流格式 GGUF（llama.cpp/Ollama 用）与 AWQ/GPTQ（vLLM 用）。**经验：4-bit 量化的小模型在抽取/分类任务上常可接受，创作类任务质量下降明显**——量化后必须跑评测（44-10）。
 *   - **吞吐与并发的权衡**：vLLM 的 continuous batching 让多请求共享算力，吞吐远高于逐条；Ollama 默认并发低（适合个人）。**"本地模型慢"多数是部署方式问题而不是模型问题**。
 *   - **本地小模型的能力边界**：7B~14B 量化模型适合：分类/抽取/改写/简单摘要；不适合：复杂推理、长上下文精细理解、前沿知识。**"小模型干粗活、大模型干细活"的路由策略**（44-08）是本地部署的最佳实践。
 *   - 易错点：显存预算只算权重不算 KV Cache（上下文一长 OOM）；量化后不跑评测直接上（质量塌了不知道）；用 CPU 跑大模型等十分钟（边缘场景才用 CPU）；本地模型不带幂等重试（和云端一样的工程要求）。
 * ============================================================
 */

package com.heima.ch44;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class LocalLlm {
    record ModelOption(String name, int params, String quant, double vramGb, String fit) {
        double weightGb() {
            return params * (quant.contains("INT4") ? 0.5 : quant.contains("INT8") ? 1.0 : 2.0);
        }
    }

    static void vramBudget() {
        System.out.println("【显存预算：权重 + KV Cache】");
        List<ModelOption> opts = List.of(
                new ModelOption("Qwen2.5-7B", 7, "FP16", 0, "单卡24G 可跑"),
                new ModelOption("Qwen2.5-7B", 7, "INT8", 0, "约 7G 权重"),
                new ModelOption("Qwen2.5-7B", 7, "INT4", 0, "约 3.5G 权重，长上下文余量大"),
                new ModelOption("Qwen2.5-72B", 72, "INT4", 0, "约 36G+，需多卡"));
        for (ModelOption m : opts) {
            double w = m.weightGb();
            double kv = 2.0;   // 8K 上下文的 KV Cache 估算（示意）
            System.out.printf("  %-14s %-4s 权重≈%5.1fGB + KV≈%.0fGB -> 建议 %.0fGB 显存%n",
                    m.name(), m.quant(), w, kv, w + kv + 1);
        }
        System.out.println("  规则：上下文越长 KV Cache 越大；预算只算权重必翻车");
        System.out.println();
    }

    static void openaiCompat() {
        System.out.println("【Ollama 的 OpenAI 兼容接口：40-02 代码原样复用】");
        String req = "{\"model\":\"qwen2.5:7b-instruct\",\"temperature\":0.2,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"把这段文本抽成 JSON...\"}]}";
        String resp = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"{\\\"sku\\\":\\\"A100\\\",\\\"stock\\\":12}\"}}]}";
        System.out.println("  请求（指向 http://localhost:11434/v1/chat/completions）：");
        System.out.println("    " + req);
        System.out.println("  响应：" + resp);
        System.out.println("  换模型的改动 = 换 base_url 与 model 字段 —— 路由层（44-08）的基础");
        System.out.println();
    }

    static void quantTradeoff() {
        System.out.println("【量化的取舍：必须用评测验证】");
        record Task(String name, String fp16Score, String int4Score, String verdict) {
        }
        List<Task> tasks = List.of(
                new Task("意图分类（5类）", "94.2%", "93.8%", "可接受 -> 用 INT4"),
                new Task("字段抽取（订单）", "91.5%", "90.1%", "可接受，JSON 校验兜底"),
                new Task("营销文案生成", "8.2/10", "6.9/10", "明显下降 -> 创作任务用 FP16/云端"),
                new Task("复杂多步推理", "71%", "58%", "大幅下降 -> 路由到云端大模型"));
        for (Task t : tasks) {
            System.out.printf("  %-14s FP16=%-8s INT4=%-8s %s%n",
                    t.name(), t.fp16Score(), t.int4Score(), t.verdict());
        }
        System.out.println("  量化不是免费的：不同任务的损失差异巨大，评测说话");
        System.out.println();
    }

    static void servingChoice() {
        System.out.println("【部署形态选择】");
        List<String> rows = List.of(
                "个人/POC：Ollama 一键起，OpenAI 兼容，并发低",
                "生产高吞吐：vLLM（continuous batching + PagedAttention），吞吐数倍",
                "边缘/无 GPU：llama.cpp CPU 推理，慢但可用",
                "托管折中：云厂商一键部署（兼顾合规与运维省心）");
        for (String r : rows) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        vramBudget();
        openaiCompat();
        quantTradeoff();
        servingChoice();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 Ollama 部署一个 7B 模型，用 40-02 的客户端代码对接，跑通"文本抽取"任务并记录延迟与吞吐。
 * 2. 对比 FP16 与 INT4 在"字段抽取/文案生成"两个任务上的质量（各 20 条样本），量化差异并写出路由建议。
 * 3. 思考题：为什么"本地模型不带幂等重试"是错误？本地部署改变了什么、没改变什么？
 * ============================================================
 */
