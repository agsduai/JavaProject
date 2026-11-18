/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：40-08
 * 课名：RAG：检索增强生成
 * ------------------------------------------------------------
 * 知识点：
 *   - **RAG 解决模型的两个先天缺陷**：不知道私有知识（产品文档、内部制度）与不知道新知识（训练截止后）。做法是**检索相关片段塞进上下文，让模型"开卷考试"**——回答基于你给的资料而不是参数记忆，幻觉率大幅下降。
 *   - **完整流水线五步**：① **切块（chunking）**：文档按语义段切 300~800 token，带 10~20% 重叠防切断语义；② **嵌入**：每块算向量入库；③ **检索**：混合检索取 top K 块；④ **拼 Prompt**：资料块 + 来源编号 + 问题 + "仅基于资料回答"；⑤ **生成 + 引用校验**：要求输出带 [来源N]，程序校验引用真实存在。
 *   - **切块策略决定检索上限**：太碎（100 token）→ 单块信息不足，答案不完整；太粗（2000 token）→ 一块里塞了太多主题，向量语义被稀释。**按标题/章节结构切优于纯按字数切**，表格与代码块不可拦腰截断。
 *   - **引用与溯源是 RAG 的质量护栏**：模型输出"根据资料3，退货运费由平台承担"，用户可点开资料 3 验证。**没有引用的 RAG 只是幻觉换了件衣服**；程序要校验引用编号真实、且引用的块确实包含被声称的内容（抽查关键词）。
 *   - **检索不到时的诚实路径**：相似度最高分低于阈值 → 诚实回答"资料中没有找到"，转人工。**宁可说不知道，不可编造**——这是产品决策也是 Prompt 明文规则。
 *   - **多文档冲突**：2024 版政策说 A、2025 版说 B，检索都可能命中。治理手段：文档带版本元数据，Prompt 注入"以最新版本为准"，冲突样本进测试集。
 *   - 易错点：把整本文档塞上下文当 RAG（贵、慢、窗口超限、注意力稀释）；切块无重叠导致关键句子被切断；检索结果与问题毫不相关还硬塞给模型（问什么答什么，模型被迫编）；RAG 知识库更新无流程（文档改了向量库没更新，答案一直是旧的）。
 * ============================================================
 */

package com.heima.ch40;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RagPipeline {
    record Chunk(String source, String text, double[] vector) {
    }

    static List<Chunk> chunks = new ArrayList<>();

    static void chunking() {
        System.out.println("【切块：按结构切、带重叠、块内语义完整】");
        String doc = "退货政策：\n  1.7天内无理由退货，运费买家承担。\n  2.质量问题退货，运费商家承担。\n  3.生鲜类不支持无理由退货。";
        String[] parts = doc.split("\n");
        for (int i = 0; i < parts.length; i++) {
            String text = i > 0 && i < parts.length - 1
                    ? parts[i] + "（重叠：" + nextHead(parts[i + 1]) + "）"
                    : parts[i];
            double[] vec = {i == 2 ? 0.95 : 0.2, i == 1 ? 0.95 : 0.3, 0.1};
            chunks.add(new Chunk("退货政策#" + (i + 1), text, vec));
            System.out.printf("  块%d [%s] %s%n", i + 1, "退货政策#" + (i + 1), text);
        }
        System.out.println("  300~800 token/块，10~20% 重叠；表格与代码块不可拦腰截断");
        System.out.println();
    }

    static String nextHead(String s) {
        return s.length() <= 8 ? s : s.substring(0, 8) + "…";
    }

    static List<Chunk> retrieve(double[] query, int topK, double minScore) {
        record Scored(Chunk c, double s) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Chunk c : chunks) {
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < c.vector().length; i++) {
                dot += c.vector()[i] * query[i];
                na += c.vector()[i] * c.vector()[i];
                nb += query[i] * query[i];
            }
            scored.add(new Scored(c, dot / (Math.sqrt(na) * Math.sqrt(nb))));
        }
        scored.sort((a, b) -> Double.compare(b.s(), a.s()));
        List<Chunk> out = new ArrayList<>();
        for (Scored s : scored) {
            if (s.s() < minScore) {
                break;
            }
            out.add(s.c());
        }
        return out.size() > topK ? out.subList(0, topK) : out;
    }

    static void fullPipeline() {
        System.out.println("【完整 RAG：检索 -> 拼 Prompt -> 生成带引用 -> 校验】");
        double[] query = {0.9, 0.3, 0.1};    // “质量问题退货运费谁出”
        List<Chunk> top = retrieve(query, 2, 0.3);
        StringBuilder material = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            material.append("[资料").append(i + 1).append("] ").append(top.get(i).text()).append("\n");
        }
        System.out.println("  检索到 " + top.size() + " 块：");
        System.out.println(material);
        String systemPrompt = "仅基于以下资料回答，回答格式：结论 + [资料N]。资料不足时回答“未在资料中找到”。";
        System.out.println("  system: " + systemPrompt);
        // 模拟生成（规则版模型）：命中“质量问题退货，运费商家承担”
        String answer = null;
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).text().contains("质量问题")) {
                answer = "质量问题退货运费由商家承担。" + "[资料" + (i + 1) + "]";
            }
        }
        if (answer == null) {
            answer = "未在资料中找到";
        }
        System.out.println("  生成回答：" + answer);
        boolean refValid = answer.contains("[资料") && top.size() >= 1;
        System.out.println("  引用校验：" + (refValid ? "引用编号真实存在 -> PASS" : "FAIL"));
        System.out.println();
    }

    static void noAnswerPath() {
        System.out.println("【检索为空：诚实路径】");
        double[] irrelevant = {0.05, 0.05, 0.9};   // “公司年会什么时候开”
        List<Chunk> top = retrieve(irrelevant, 2, 0.6);
        System.out.println("  最高相似度低于阈值 0.6，检索到 " + top.size() + " 块");
        System.out.println("  回答：“未在资料中找到，已为您转人工。”");
        System.out.println("  原则：宁可转人工，不可编造 —— 写进 Prompt 也写进产品流程");
    }

    public static void main(String[] args) {
        chunking();
        fullPipeline();
        noAnswerPath();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为一份真实业务文档实现完整 RAG（切块 + 向量 + 检索 + 引用校验），准备 10 个问题验证回答质量，其中 2 个是知识库外的问题验证"诚实路径"。
 * 2. 对比三种切块粒度（200/500/1500 token）对检索质量的影响：用同一组问题测命中率，记录结论。
 * 3. 思考题：为什么"引用校验"要做成程序逻辑而不是提示词约束？从模型输出不可保证性的角度设计三层防线。
 * ============================================================
 */
