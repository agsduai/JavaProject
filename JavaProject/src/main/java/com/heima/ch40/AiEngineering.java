/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：40-10
 * 课名：AI 应用的工程化：成本、降级与安全
 * ------------------------------------------------------------
 * 知识点：
 *   - **AI 功能的可靠性预算与普通服务不同**：模型服务本身会慢、会错、会限流。工程化的目标是**把"模型的不确定性"封装在可控边界内**：超时、重试、降级、熔断一个都不能少——第 29 章微服务的所有容错手段在这里全部适用，只是对象从"下游服务"变成了"模型服务"。
 *   - **成本控制三板斧**：① **缓存**：相同输入（规范化后）命中缓存直接返回，客服场景命中率可观；② **分级模型**：简单任务用便宜的小模型（分类、抽取），复杂任务才用贵的大模型，路由策略是固定规则；③ **上下文瘦身**：压缩历史、RAG 只带相关片段、系统提示精简。监控每千次会话成本是 AI 功能的基础报表。
 *   - **降级阶梯**：大模型超时 → 换小模型 → 关键词规则兜底 → 静态 FAQ → 人工。**每一级都要真的实现**，而不是写了 try-catch 就当降级。AI 功能上线前，"模型全挂了系统还能不能用"要演练。
 *   - **内容安全双向把关**：输入侧过滤（敏感词、提示注入检测，40-03）；输出侧校验（违禁内容、是否泄露系统提示、JSON 结构合法性）。模型生成内容**必须经过审核管道才能到达用户**，尤其在 C 端。
 *   - **可观测性的特殊字段**：除常规 QPS/耗时/错误率，AI 功能要记录：每请求 token 数（输入/输出分开）、模型版本、Prompt 版本、检索命中数、用户反馈（点赞/点踩）。**没有反馈闭环的 AI 功能无法迭代**。
 *   - **评测（eval）驱动迭代**：建测试集（真实问题 + 期望要点），每次改 Prompt/换模型/调检索参数都跑评测集，输出通过率对比。人工抽检 + 自动要点匹配（关键词/LLM-as-judge）结合。**没有评测集的 AI 功能等于裸奔**。
 *   - **合规与数据边界**：用户对话数据喂给第三方模型服务前明确法律依据；日志脱敏（手机号/身份证）；模型服务的 DPA/数据处理协议；内部敏感系统不要把全部数据放进向量库（按密级切分）。
 *   - 易错点：超时没设或太短（长生成被掐）；重试放大了限流（429 时无脑重试）；把模型输出直接渲染 HTML（XSS，第 36 章的知识在这里同样生效）；成本只有总量没有归因（不知道哪个 Prompt 最烧钱）；评测集与真实流量分布脱节（评测满分线上翻车）。
 * ============================================================
 */

package com.heima.ch40;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AiEngineering {
    record ModelTier(String name, double costPer1k, int maxTokens, String fit) {
    }

    static final ModelTier LARGE = new ModelTier("large", 0.04, 4096, "复杂推理/长文本");
    static final ModelTier SMALL = new ModelTier("small", 0.004, 2048, "分类/抽取/简短回复");

    static void tieredRouting() {
        System.out.println("【分级模型路由：按任务复杂度选模型】");
        String[][] tasks = {
                {"意图分类", "SMALL", "60% 流量，成本可忽略"},
                {"信息抽取", "SMALL", "结构化输出，低温"},
                {"客服长答", "LARGE", "复杂但低频"},
                {"文案生成", "LARGE", "质量敏感"},
        };
        for (String[] t : tasks) {
            System.out.printf("  %-8s -> %-5s  %s%n", t[0], t[1], t[2]);
        }
        long dailyLarge = 2_000, dailySmall = 30_000;
        double costLarge = dailyLarge * 1200 / 1000 * LARGE.costPer1k();
        double costSmall = dailySmall * 400 / 1000 * SMALL.costPer1k();
        System.out.printf("  估算：大模型 %d 次/日 ≈ %.0f 元；小模型 %d 次/日 ≈ %.0f 元%n",
                dailyLarge, costLarge, dailySmall, costSmall);
        System.out.println("  规则路由先行，效果不够再上模型路由（用小模型判断该不该用大模型）");
        System.out.println();
    }

    static void degradeLadder() {
        System.out.println("【降级阶梯：模型全挂了系统还能用】");
        String[][] ladder = {
                {"L1 主模型", "正常路径，流式输出"},
                {"L2 备用模型", "主模型超时/5xx，切备用供应商"},
                {"L3 规则引擎", "关键词匹配 FAQ，回答固定话术"},
                {"L4 静态兜底", "暂时无法智能解答，已转人工"},
        };
        for (String[] l : ladder) {
            System.out.printf("  %-12s %s%n", l[0], l[1]);
        }
        System.out.println("  触发器：连续 5 次超时 -> 熔断 30s 直落 L2；供应商全面故障 -> L3");
        System.out.println("  演练：季度关掉全部模型跑一遍，确认 L3/L4 真的能用");
        System.out.println();
    }

    static void cacheLayer() {
        System.out.println("【请求缓存：规范化的输入做 key】");
        Map<String, String> cache = new LinkedHashMap<>();
        String q1 = "退货运费谁出", q2 = "退货运费谁出？", q3 = "质量问题退货运费谁承担";
        cache.put(normalize(q1), "运费由商家承担（质量问题）");
        String hit = cache.get(normalize(q2));
        String miss = cache.get(normalize(q3));
        System.out.println("  输入1：" + q1 + " -> 未命中，调模型，写入缓存");
        System.out.println("  输入2：" + q2 + " -> 规范化后命中：" + (hit != null ? hit : "MISS"));
        System.out.println("  输入3：" + q3 + " -> 规范化后：" + (miss != null ? "HIT" : "MISS（语义不同不硬凑）"));
        System.out.println("  规范化：去标点/统一空格；语义级缓存用向量相似度但阈值要高（0.95+）防误答");
        System.out.println();
    }

    static String normalize(String s) {
        return s.replace("？", "").replace("?", "").trim();
    }

    static void observability() {
        System.out.println("【AI 专属可观测性字段】");
        List<String> fields = List.of(
                "tokens_in / tokens_out（每请求，成本归因的原子数据）",
                "model_version + prompt_version（劣化归因的坐标系）",
                "retrieval_hits（RAG 命中块数，检索质量信号）",
                "tool_calls（工具调用次数，循环异常的信号）",
                "user_feedback（赞/踩 + 原因，最宝贵的真实评测数据）");
        for (String f : fields) {
            System.out.println("  - " + f);
        }
        System.out.println("  汇总报表：每千会话成本、首 token 延迟 P99、降级率、点踩率");
    }

    public static void main(String[] args) {
        tieredRouting();
        degradeLadder();
        cacheLayer();
        observability();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整降级阶梯：模拟主模型超时/429/500 三种故障，验证逐级回落与熔断恢复，输出每级的触发日志。
 * 2. 给你的 AI 功能建评测集（20 条），实现"跑评测 -> 输出通过率 -> 对比两个 Prompt 版本"的脚本，把评测接入 CI。
 * 3. 思考题：为什么语义缓存阈值要设得很高（0.95+）？算一笔账：误命中导致的错误回答 vs 省下的模型成本，哪个代价更大？
 * ============================================================
 */
