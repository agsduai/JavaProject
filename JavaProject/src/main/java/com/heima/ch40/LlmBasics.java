/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：40-01
 * 课名：大模型与 Java 开发者的基本概念
 * ------------------------------------------------------------
 * 知识点：
 *   - **大模型（LLM）的本质是"下一个词的概率预测器"**：给它一段文字，它预测最可能接下去的词元并逐个吐出。它能写代码、写文案、答问题，都是这个机制的涌现结果。理解这一点，就理解了两个关键推论：**输出不可保证 100% 正确（幻觉）**，以及 **Prompt 就是它的全部输入上下文**。
 *   - **Token 是计价与限制的单位**：模型不按字处理，按 token 处理。中文约 1 字 ≈ 1~2 token，英文约 1 词 ≈ 1.3 token。**上下文窗口**（如 128K token）是一次请求能携带的全部内容上限——系统提示 + 历史 + 你塞的知识 + 输出全算在内。窗口超限不会自动报错给你优化，通常直接截断或报错。
 *   - **采样参数控制"发挥"**：temperature（0=最确定、越低越稳定，1+=发散有创意）、top_p（候选词的累积概率截断）、max_tokens（输出上限）。**结构化抽取、分类任务用低温；文案创作用中高温**——"随机"与"稳定"是产品属性，不是随机数种子问题。
 *   - **模型不是数据库**：模型记不住你上次说了什么（除非你把历史重新发给它），训练截止日期之后发生的事它不知道（除非检索增强）。**"AI 记住了我"其实是你的代码把历史拼进下一次请求**——这是 40-05 的主题。
 *   - **幻觉（hallucination）是机制性缺陷不是 bug**：概率预测器会对"不知道的东西"编一个"看起来最像答案"的输出。工程对策不是"要求它别编"，而是**给它可验证的材料**（RAG，40-08）+ 要求引用来源 + 关键结论走程序校验。
 *   - **Java 开发者的位置**：LLM 生态以 Python 为主，但生产系统里模型通常只是一个 HTTP 服务。Java 的优势恰好在"模型之外"：业务逻辑、数据管道、工具调用执行、权限与审计。**Java 工程师做的是"把模型嵌进可靠系统"**。
 *   - 易错点：把模型当数据库问实时数据（训练截止后的都不知道）；temperature 调高还指望输出可复现（采样就是随机的）；忽略上下文窗口（历史越滚越长直到超限报错）；把模型输出的 JSON 直接反序列化进生产代码（必须校验，模型会输出残缺 JSON）。
 * ============================================================
 */

package com.heima.ch40;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class LlmBasics {
    /** 极简 token 估算：中文按 1.5 字/token，英文按 4 字符/token */
    static double estimateTokens(String text) {
        double cjk = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        double other = text.length() - cjk;
        return Math.ceil(cjk / 1.5 + other / 4.0);
    }

    static void tokenMath() {
        System.out.println("【token 估算与上下文窗口】");
        String sys = "你是电商平台的客服助手，只能基于提供的资料回答，不要编造。";
        String history = "用户：我的订单什么时候发货？\n助手：您的订单预计明天发货。";
        String doc = "产品说明书：本款手机壳采用液态硅胶材质，支持无线充电，厚度 1.2mm。";
        double sysT = estimateTokens(sys), hisT = estimateTokens(history), docT = estimateTokens(doc);
        System.out.printf("  系统提示  %-30s ≈ %3.0f token%n", "（约 30 字）", sysT);
        System.out.printf("  对话历史  %-30s ≈ %3.0f token%n", "（两轮）", hisT);
        System.out.printf("  检索文档  %-30s ≈ %3.0f token%n", "（产品说明）", docT);
        System.out.printf("  合计 ≈ %.0f token（窗口 128K 时占比 %.3f%%）%n",
                sysT + hisT + docT, (sysT + hisT + docT) / 128_000 * 100);
        System.out.println("  结论：单轮对话窗口绰绰有余；长文档 RAG 必须先切块再检索");
        System.out.println();
    }

    static void temperatureDemo() {
        System.out.println("【temperature：同一输入，不同采样温度】");
        record Candidate(String word, double weight) {
        }
        List<List<Candidate>> samples = List.of(
                List.of(new Candidate("1", 0.8), new Candidate("2", 0.15), new Candidate("3", 0.05)),
                List.of(new Candidate("1", 0.45), new Candidate("2", 0.3), new Candidate("3", 0.25)));
        String[] labels = {"temperature=0.2（低温：分布尖锐）", "temperature=1.0（高温：分布平坦）"};
        for (int i = 0; i < 2; i++) {
            System.out.println("  " + labels[i]);
            double total = samples.get(i).stream().mapToDouble(Candidate::weight).sum();
            for (Candidate c : samples.get(i)) {
                System.out.printf("    下一个词 %s  概率 %.2f%%%n", c.word(), c.weight() / total * 100);
            }
        }
        System.out.println("  抽取/分类 -> 低温保证稳定；营销文案 -> 中高温换取多样性");
        System.out.println();
    }

    static void statelessModel() {
        System.out.println("【模型无状态：所谓“记忆”是你拼出来的】");
        Map<Integer, String> turns = new LinkedHashMap<>();
        turns.put(1, "user: 我叫小王");
        turns.put(2, "assistant: 好的小王，有什么可以帮你？");
        turns.put(3, "user: 我叫什么？");
        System.out.println("  第 3 轮请求实际发送的内容：");
        System.out.println("    [system] 你是助手…");
        System.out.println("    " + turns.get(1));
        System.out.println("    " + turns.get(2));
        System.out.println("    " + turns.get(3));
        System.out.println("  模型“记得小王”只因第 1 轮还在上下文里；轮次多了要裁剪（40-05）");
        System.out.println();
    }

    static void hallucination() {
        System.out.println("【幻觉的工程对策】");
        List<String> measures = List.of(
                "给材料：把可验证的文档塞进上下文（RAG），要求“仅基于资料回答”",
                "要引用：让输出带 [来源: 文档N]，程序校验引用是否真实存在",
                "程序校验：价格、库存、日期等事实字段用代码比对数据库，不信模型",
                "允许说不知道：Prompt 明确“资料不足时回答不知道”，比编造强一万倍");
        for (String m : measures) {
            System.out.println("  - " + m);
        }
    }

    public static void main(String[] args) {
        tokenMath();
        temperatureDemo();
        statelessModel();
        hallucination();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用示例的 estimateTokens 估算你自己系统提示 + 10 轮历史的 token 数，判断在你的目标模型窗口内的占比。
 * 2. 实现"低温/高温"的加权随机采样器：给定候选词与权重，按 temperature 缩放后采样 100 次统计分布，验证温度对分布的影响。
 * 3. 思考题：为什么"要求模型别编造"不可靠，而"给它材料 + 程序校验"可靠？从概率预测的机制推导工程对策的边界。
 * ============================================================
 */
