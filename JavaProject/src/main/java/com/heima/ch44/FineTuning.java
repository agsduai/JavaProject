/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：44-06
 * 课名：模型微调与 LoRA
 * ------------------------------------------------------------
 * 知识点：
 *   - **什么时候需要微调，什么时候不需要**：提示词能解决的不微调（few-shot/结构化输出，40-03）；知识更新用 RAG（40-08）不用微调；**微调的正当场景**：稳定的输出风格/格式（难以用提示描述）、垂直领域术语理解、小模型替代大模型（蒸馏，省成本）、指令遵循风格统一。
 *   - **微调 ≠ 灌知识**：把文档塞进训练数据让模型"记住"，效果差且昂贵还容易过拟合——**知识该用 RAG（可更新、可溯源），能力该用微调**（怎么回答、什么格式、什么风格）。
 *   - **LoRA 的核心思想**：冻结原模型权重，只训练注入的低秩小矩阵（rank 8~64），**可训练参数只有全量的 0.1%~1%**。收益：单卡可训、训练快、多个 LoRA 适配器可热切换（一个基座 + 多个业务 LoRA）。
 *   - **训练数据是成败的关键**：几百到几千条高质量样本（问答对/输入输出对）胜过十万条垃圾数据。**数据治理**：格式统一、去重、清洗噪声、留出验证集。数据质量差是微调失败的 80% 原因。
 *   - **过拟合与灾难性遗忘**：训练太多轮模型只会背训练样本（泛化失效）、或忘掉原本能力（通用问答变差）。**对策**：早停（验证集分数拐点）、保留通用数据混合训练、LoRA 的低 rank 本身有正则效应。
 *   - **评估与上线**：微调模型与基座模型在**同一评测集**上对比（44-10），通过才发布；LoRA 适配器可灰度切换——**微调也要走"评测 -> 灰度 -> 全量"的发布流程**（41 章思想复用）。
 *   - 易错点：用微调解决知识时效问题（应该 RAG）；训练数据与真实分布脱节（评测集满分、线上翻车）；不设验证集盲训（过拟合不可见）；微调后不做基座对比直接替换线上模型。
 * ============================================================
 */

package com.heima.ch44;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FineTuning {
    record Sample(String input, String output, String source) {
    }

    static void whenToFinetune() {
        System.out.println("【决策：RAG / 提示词 / 微调 的边界】");
        String[][] rows = {
                {"产品知识问答", "RAG", "知识需更新、需溯源"},
                {"固定格式抽取", "提示词 + JSON 模式", "格式可描述，无需训练"},
                {"报告生成风格统一", "微调（LoRA）", "风格难以用提示词描述"},
                {"术语理解（医疗/法律）", "微调 + RAG 混合", "术语能力训练，知识检索"},
                {"小模型替代大模型", "微调（蒸馏）", "用大模型生成数据教小模型，省成本"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-16s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  口诀：知识用 RAG，能力用微调，格式用提示词");
        System.out.println();
    }

    static void loraIntuition() {
        System.out.println("【LoRA：冻结大矩阵，只训小增量】");
        long baseParams = 7_000_000_000L;
        long loraParams = 20_000_000;
        System.out.printf("  基座 %.0fB 参数冻结 + LoRA %.0fM 参数训练（占比 %.3f%%）%n",
                baseParams / 1e9, loraParams / 1e6, loraParams * 100.0 / baseParams);
        System.out.println("  收益：单卡 24G 可训、训练小时级、适配器按业务热切换");
        System.out.println("  一个基座 + 多个 LoRA：order-lora（工单风格）、support-lora（客服语气）");
        System.out.println();
    }

    static void dataQuality() {
        System.out.println("【训练数据治理：质量的 80/20】");
        record Issue(String problem, String effect, String fix) {
        }
        List<Issue> issues = List.of(
                new Issue("格式不统一", "模型输出格式漂移", "统一 schema 校验后入库"),
                new Issue("重复样本", "过拟合到重复项", "去重（语义+精确）"),
                new Issue("分布偏差", "评测满分、线上翻车", "按线上真实分布采样"),
                new Issue("无验证集", "过拟合不可见", "留 10~20% 验证，早停"));
        for (Issue i : issues) {
            System.out.printf("  %-10s %-16s %s%n", i.problem(), i.effect(), i.fix());
        }
        System.out.println("  规模经验：500~5000 条高质量样本足够多数风格/格式任务");
        System.out.println();
    }

    static void evalGate() {
        System.out.println("【发布闸门：微调模型 vs 基座对比】");
        record Eval(String task, String base, String finetuned, String verdict) {
        }
        List<Eval> evals = List.of(
                new Eval("目标：工单风格一致性", "61%", "88%", "PASS：目标达成"),
                new Eval("回归：通用问答", "92%", "90%", "PASS：遗忘可接受（<3%）"),
                new Eval("回归：JSON 抽取", "90%", "84%", "FAIL：验证集没覆盖，回去补数据"));
        for (Eval e : evals) {
            System.out.printf("  %-18s 基座=%-6s 微调=%-6s %s%n",
                    e.task(), e.base(), e.finetuned(), e.verdict());
        }
        System.out.println("  未通过不发布：微调必须与基座同评测集对比，双门槛（目标+回归）");
    }

    public static void main(String[] args) {
        whenToFinetune();
        loraIntuition();
        dataQuality();
        evalGate();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用你的业务数据构造 200 条微调样本（含 schema 校验与去重），写出数据治理清单与验证集划分方案。
 * 2. 在 Ollama 上体验一个已微调的社区模型 vs 基座模型，对比同一评测集（10 条）上的表现差异。
 * 3. 思考题：为什么"知识用 RAG，能力用微调"是工程上的黄金划分？从更新成本、溯源、规模效应三个角度分析。
 * ============================================================
 */
