/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：40-05
 * 课名：多轮对话与上下文管理
 * ------------------------------------------------------------
 * 知识点：
 *   - **多轮对话的内存结构就是 messages 数组**：每轮把新增的 user 消息与 assistant 回复 append 进去，下次请求整体发送。**历史长度 = token 成本线性增长 + 终将超窗口**，上下文管理是长对话系统的核心工程问题。
 *   - **三种裁剪策略**：① **滑动窗口**（保留最近 N 轮，最简单，丢早期信息）；② **摘要压缩**（早期对话让模型总结成一段塞进 system 或开头，细节丢失要点保留）；③ **混合**（系统提示 + 长期摘要 + 最近 K 轮原文）。客服/助手类产品几乎都用混合。
 *   - **什么必须永远保留**：用户的核心属性（姓名、偏好、当前业务单号）在对话中一旦出现就要持续在上下文里——通常做成**槽位（slot）**单独维护，不依赖对话历史。槽位是"结构化记忆"，比"祈祷模型记住第 3 轮说过的话"可靠。
 *   - **截断的边界感**：裁剪按轮次不按字符——半截对话（user 有 assistant 无）会破坏模型的对话结构感；系统提示永远不裁；工具调用轮（40-06）的配对消息要么全保要么全删。
 *   - **会话存储**：按 sessionId 存 Redis（TTL 对齐会话时长），序列化 messages + 槽位。**上下文不是越全越好**——不相关的长历史会稀释注意力（模型更容易"被带偏"），摘要压缩反而提升回答质量。
 *   - **上下文更新的一致性**： assistant 回复失败/中断时历史如何记（记 user + 记错误占位，不能让下一轮结构残缺）；并发同 session 消息要串行处理。
 *   - 易错点：历史无限增长直到 400 报错；截断恰好截掉"当前业务单号"导致答非所问（槽位化可避免）；把工具调用的中间消息残留在历史里（下次请求 400 invalid messages）；摘要由低质模型生成导致关键信息丢失（摘要 Prompt 也要测试）。
 * ============================================================
 */

package com.heima.ch40;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ChatMemory {
    record Msg(String role, String content) {
    }

    static class Session {
        final List<Msg> system = new ArrayList<>();
        final Map<String, String> slots = new LinkedHashMap<>();     // 结构化记忆
        final Deque<Msg> recent = new ArrayDeque<>();                // 最近 K 轮
        String summary = "";                                         // 长期摘要
        int maxRecent = 4;

        List<Msg> buildContext() {
            List<Msg> ctx = new ArrayList<>(system);
            if (!summary.isEmpty()) {
                ctx.add(new Msg("system", "历史摘要：" + summary));
            }
            ctx.addAll(recent);
            return ctx;
        }

        void addUser(String text) {
            recent.add(new Msg("user", text));
            while (recent.size() > maxRecent) {
                Msg oldest = recent.pollFirst();
                summary = compact(summary, oldest.content());
            }
        }

        void addAssistant(String text) {
            recent.add(new Msg("assistant", text));
            while (recent.size() > maxRecent) {
                summary = compact(summary, recent.pollFirst().content());
            }
        }

        String compact(String old, String text) {
            return (old.isEmpty() ? "" : old + " ") + text.substring(0, Math.min(12, text.length())) + "…";
        }
    }

    static void contextGrowth() {
        System.out.println("【上下文随轮次线性增长：必须管理】");
        int[] turns = {5, 20, 50};
        for (int t : turns) {
            double tokens = t * 120;      // 每轮约 120 token
            System.out.printf("  %2d 轮对话 ≈ %6.0f token（无管理时成本与长度同步涨）%n", t, tokens);
        }
        System.out.println("  裁剪策略：滑动窗口保最近轮 + 摘要保早期信息 + 槽位保关键事实");
        System.out.println();
    }

    static void sessionDemo() {
        System.out.println("【一个会话的上下文构建过程】");
        Session s = new Session();
        s.system.add(new Msg("system", "你是客服助手。用户资料以【用户资料】为准。"));
        s.slots.put("用户", "小王");
        s.slots.put("当前订单", "2026111701");
        s.addUser("我的订单 2026111701 到哪了？");
        s.addAssistant("您的订单已发货，预计明天送达。");
        s.addUser("换货的话地址怎么填？");
        s.addAssistant("换货地址请填您下单时的收货地址。");
        s.addUser("那退货运费谁出？");
        s.addAssistant("质量问题退货运费我们承担。");
        s.addUser("好的，那什么时候到账？");
        System.out.println("  槽位（永远注入 system）：" + s.slots);
        System.out.println("  摘要（早期轮压缩）：" + (s.summary.isEmpty() ? "（空）" : s.summary));
        List<Msg> ctx = s.buildContext();
        System.out.println("  最终发送的 messages：");
        for (Msg m : ctx) {
            System.out.printf("    [%s] %s%n", m.role(), m.content());
        }
        System.out.println();
    }

    static void truncationRules() {
        System.out.println("【裁剪的边界规则】");
        List<String> rules = List.of(
                "系统提示永不裁剪",
                "按完整轮次裁（user+assistant 成对），不留半轮",
                "工具调用消息与其响应必须同进同出（40-06）",
                "槽位信息脱离历史单独注入，防止被裁掉",
                "摘要压缩后抽查关键事实是否还在（订单号/金额）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        contextGrowth();
        sessionDemo();
        truncationRules();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"混合策略"的完整会话管理（槽位 + 摘要 + 滑动窗口），模拟 30 轮对话，打印每轮实际发送的 messages 总 token 数曲线。
 * 2. 制造"截断丢关键信息"事故：把"订单号在第 2 轮"的对话裁剪到只留最近 2 轮，观察第 8 轮"我的订单到哪了"如何答非所问，再用槽位修复。
 * 3. 思考题：为什么摘要压缩可能反而提升回答质量？从"注意力稀释"的角度分析超长历史的负面影响。
 * ============================================================
 */
