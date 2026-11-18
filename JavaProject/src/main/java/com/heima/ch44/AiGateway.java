/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：44-08
 * 课名：AI 网关与多模型路由
 * ------------------------------------------------------------
 * 知识点：
 *   - **多模型是常态，统一入口是刚需**：业务已经同时面对云端大模型、云端小模型、本地模型（44-05）——每个调用方各自管鉴权/重试/限流是灾难。**AI 网关是所有模型调用的唯一入口**：统一协议、统一鉴权、统一可观测。
 *   - **网关的核心职责**：**协议转换**（OpenAI 风格 -> 各家私有 API）、**密钥管理**（业务侧只见网关 token，真实 Key 在网关）、**限流与配额**（按业务方分配预算）、**路由与降级**（44-10 分级路由的执行层）、**成本归因**（每请求记账到业务方）。
 *   - **路由策略的三层**：**规则路由**（意图/长度/成本标签 -> 指定模型，确定性高）；**模型路由**（用小模型判断该任务需要大模型吗）；**降级路由**（主模型失败/超时 -> 备用 -> 本地兜底）。40-10 的降级阶梯在网关层统一实现，业务方零感知。
 *   - **语义缓存是网关的增值项**：相同/语义相近请求直接返回缓存（40-10 的缓存层上移到网关，全局共享）。**缓存命中率的监控与失效策略**（知识库更新后相关缓存失效）是缓存正确性的关键。
 *   - **可观测性下沉到网关**：tokens、模型版本、首 token 延迟、缓存命中率全部网关记录（42 章指标体系），**成本按业务方/意图归因出报表**——这是 AI 功能成本治理的执行点。
 *   - **AI 网关的失败语义**：网关自己是单点——部署要高可用；超时预算要留给上游（网关超时 + 上游超时叠加会让用户等到天荒地老）。
 *   - 易错点：业务方直连各家 API（密钥散落、无归因）；网关超时没做预算分配（叠加超时）；语义缓存阈值过松（错误答案被缓存，40-10 的教训）；降级路由把"重试打同一个过载模型"（应切换到备用池）。
 * ============================================================
 */

package com.heima.ch44;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AiGateway {
    record ModelRoute(String name, String tier, double costPer1k, int p99Ms, String fit) {
    }

    static final List<ModelRoute> POOL = List.of(
            new ModelRoute("cloud-large", "large", 0.04, 2200, "复杂推理/长文"),
            new ModelRoute("cloud-small", "small", 0.004, 600, "分类/抽取"),
            new ModelRoute("local-7b-int4", "local", 0.0002, 900, "高频粗活/内网合规"));

    static void routeDecision() {
        System.out.println("【路由决策：规则先行，降级兜底】");
        record Call(String intent, String route, String note) {
        }
        List<Call> calls = List.of(
                new Call("意图分类", "cloud-small", "规则路由：分类类目 -> small"),
                new Call("工单摘要", "local-7b", "合规路由：内网数据不出域"),
                new Call("复杂诊断", "cloud-large", "能力路由：需要推理"),
                new Call("复杂诊断(超时)", "cloud-large -> cloud-small", "降级路由：主超时切备用"),
                new Call("任意(网关故障)", "本地兜底", "终极降级：local 恒在线"));
        for (Call c : calls) {
            System.out.printf("  %-16s -> %-22s %s%n", c.intent(), c.route(), c.note());
        }
        System.out.println();
    }

    static void costAttribution() {
        System.out.println("【成本归因：网关的记账本】");
        record Ledger(String biz, String model, long tokens, double cost) {
        }
        List<Ledger> ledger = List.of(
                new Ledger("客服-意图分类", "small", 1_800_000, 7.2),
                new Ledger("客服-复杂问答", "large", 120_000, 4.8),
                new Ledger("工单-风格改写", "local", 900_000, 0.2));
        double total = 0;
        for (Ledger l : ledger) {
            total += l.cost();
            System.out.printf("  %-14s %-6s %8d tok  %.1f 元%n", l.biz(), l.model(), l.tokens(), l.cost());
        }
        System.out.printf("  日合计 %.1f 元 -> 按业务方出周报，谁烧钱一目了然%n", total);
        System.out.println();
    }

    static void timeoutBudget() {
        System.out.println("【超时预算：网关与上游不许叠加】");
        int userBudget = 3000;
        int gatewayOverhead = 100;
        int upstream = userBudget - gatewayOverhead;
        System.out.printf("  用户预算 %dms = 网关自身 %dms + 上游调用 %dms%n",
                userBudget, gatewayOverhead, upstream);
        System.out.println("  降级链路预算：large 2000ms 超时 -> 切 small（剩余 900ms）");
        System.out.println("  反例：网关 3s + 上游 3s -> 用户等 6s 预算翻倍");
        System.out.println();
    }

    static void gatewayChecklist() {
        System.out.println("【网关上线清单】");
        List<String> items = List.of(
                "统一 OpenAI 兼容协议，业务方只认网关 token",
                "规则/模型/降级三层路由配置化（改路由不发版）",
                "语义缓存（阈值 0.95+，知识库更新可定向失效）",
                "全量记账：tokens/模型/业务方/首 token 延迟",
                "网关自身高可用 + 超时预算分配",
                "密钥只在网关，轮换不影响业务方");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        routeDecision();
        costAttribution();
        timeoutBudget();
        gatewayChecklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现迷你 AI 网关（模拟）：协议转发 + 规则路由 + 超时降级 + 成本记账，用 20 个模拟请求验证路由与降级日志。
 * 2. 设计你团队的模型池与路由表：至少 3 个模型、4 类意图的规则、2 级降级，并给出超时预算分配。
 * 3. 思考题：为什么语义缓存放在网关层比放在业务层好？从缓存共享面、失效广播、成本归因三个角度分析。
 * ============================================================
 */
