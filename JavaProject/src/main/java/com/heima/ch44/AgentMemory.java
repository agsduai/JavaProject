/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：44-03
 * 课名：Agent 的记忆体系
 * ------------------------------------------------------------
 * 知识点：
 *   - **Agent 的记忆分三层，各管不同时效**：**工作记忆**（当前循环的上下文：Thought/Action/ Observation，随任务存亡）、**会话记忆**（跨轮次对话历史，40-05 的会话管理）、**长期记忆**（跨会话沉淀的用户偏好、历史结论，存向量库/DB）。**三层混用是 Agent 记忆混乱的根源**。
 *   - **工作记忆的裁剪**：长任务的 Observation 累积最凶——策略：完成步骤的 Observation 压缩成一行摘要（"已查库存=12"），只保留最近 2 轮原文。**裁剪不丢关键结论**（库存数字要留，查询过程可丢）。
 *   - **长期记忆的写入要"事件驱动 + 主动沉淀"**：任务结束时用小模型总结"本次学到了什么"（用户偏好 A 品牌下单快、该项目部署前要清缓存），**有价值的才写入**——什么都存等于没有记忆（检索噪声淹没有效信息）。
 *   - **记忆的检索与注入**：长期记忆按当前任务语义检索 top K（40-07 向量检索），以"已知信息"块注入 system。**检索质量决定记忆价值**：向量库的切块与 40-08 RAG 同一套方法论。
 *   - **记忆的时效与失效**：偏好会变（用户换了手机品牌）、事实会过期（项目下线了）——记忆条目带时间戳与 TTL，**定期清理与置信度衰减**。永不失效的记忆最终变成误导。
 *   - **跨 Agent 共享记忆**：多智能体（44-04）协作时共享一块"黑板"（blackboard）：中间结论、当前状态——避免每个 Agent 重复查询。**黑板要分角色可写**，否则互相覆盖。
 *   - 易错点：三层记忆不分（会话历史当长期记忆用，越滚越大）；记忆全量保存（检索噪声）；无 TTL（过期偏好误导决策）；跨线程/Agent 记忆不共享（重复劳动与结论打架）。
 * ============================================================
 */

package com.heima.ch44;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AgentMemory {
    record Memory(String content, String kind, long ttlDays, double score) {
    }

    /** 三层记忆：各自的生命周期 */
    static Map<String, List<Memory>> layers = new LinkedHashMap<>();

    static void threeLayers() {
        System.out.println("【三层记忆的生命周期】");
        layers.put("工作记忆", List.of(
                new Memory("已查库存=12", "本轮结论", 0, 1.0),
                new Memory("补货量建议 198", "本轮结论", 0, 1.0)));
        layers.put("会话记忆", List.of(
                new Memory("用户要求“只补爆款”", "本会话约束", 1, 0.9),
                new Memory("上一轮补货方案被否（超预算）", "本会话历史", 1, 0.8)));
        layers.put("长期记忆", List.of(
                new Memory("用户偏好：预算敏感，先报总价", "偏好", 180, 0.7),
                new Memory("项目 A 已下线", "事实", 30, 0.5)));
        for (Map.Entry<String, List<Memory>> e : layers.entrySet()) {
            System.out.println("  " + e.getKey() + "：");
            for (Memory m : e.getValue()) {
                System.out.printf("    [%s ttl=%dd 置信%.1f] %s%n",
                        m.kind(), m.ttlDays(), m.score(), m.content());
            }
        }
        System.out.println("  工作记忆随任务消亡；会话记忆随会话；长期记忆带 TTL 与置信度");
        System.out.println();
    }

    static void contextTrim() {
        System.out.println("【工作记忆裁剪：只留结论，丢弃过程】");
        List<String> fullObs = List.of(
                "查询返回 JSON：{\"sku\":\"A100\",\"stock\":12,\"daily_sales\":30,\"warehouses\":3...}",
                "calc(30*7-12) = 198，计算过程...", "check_budget(4950) 返回 {...}");
        List<String> trimmed = List.of(
                "库存=12，日均销量=30", "建议补货 198 件", "预算通过（4950/5000）");
        System.out.printf("  原始 Observation 合计约 %d token，裁剪后约 %d token%n",
                fullObs.stream().mapToInt(String::length).sum() * 2,
                trimmed.stream().mapToInt(String::length).sum() * 2);
        System.out.println("  原则：数字与结论保留，JSON 过程丢弃（需要时重查）");
        System.out.println();
    }

    static void retrieval() {
        System.out.println("【长期记忆检索注入：按任务相关性取 top K】");
        String task = "为 A100 制定补货方案";
        record Candidate(String content, double rel, boolean expired) {
        }
        List<Candidate> cands = List.of(
                new Candidate("用户偏好：预算敏感，先报总价", 0.82, false),
                new Candidate("爆款补货需走审批流", 0.78, false),
                new Candidate("去年双11补货记录", 0.41, false),
                new Candidate("项目 A 部署规范（已下线）", 0.33, true));
        List<String> injected = new ArrayList<>();
        for (Candidate c : cands) {
            if (c.rel() > 0.6 && !c.expired()) {
                injected.add(c.content());
            }
        }
        System.out.println("  任务：" + task);
        System.out.println("  注入 system 的已知信息：");
        for (String s : injected) {
            System.out.println("    - " + s);
        }
        System.out.println("  过期记忆被 TTL 拦截：项目 A 已下线，注入只会误导");
        System.out.println();
    }

    public static void main(String[] args) {
        threeLayers();
        contextTrim();
        retrieval();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三层记忆的最小框架（工作/会话/长期），完成"任务结束 -> 总结沉淀 -> 下次任务检索注入"闭环，验证记忆价值。
 * 2. 给长期记忆加 TTL 与置信度衰减（每过 30 天 score×0.9，低于 0.3 清理），用模拟时间轴验证失效行为。
 * 3. 思考题：为什么"什么都存的记忆等于没有记忆"？从检索噪声与上下文成本两个角度量化记忆的负价值。
 * ============================================================
 */
