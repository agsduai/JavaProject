/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-11
 * 课名：综合案例：单聊 + 群聊闭环
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个完整可运行的 IM 核心**：**连接**（注册 + 心跳 + 在线判定 65-02）-> **协议**（长度头帧 + 类型 65-03）-> **上行**（clientMsgId 幂等 + seq 分配 65-04/05）-> **路由**（单聊直达 / 群聊扇出 65-06）-> **投递**（在线推送 + ACK；离线暂存 65-07）-> **同步**（seq 增量 + 已读位点 65-05）-> **存储**（会话分片 + 会话列表 65-08）。
 *   - **闭环验收的四个断言**：**不丢**（每条消息最终 DELIVERED 或进离线待补）、**不重**（clientMsgId 与 msgId 双去重）、**有序**（会话内 seq 单调）、**可同步**（任意端按 seq 拉到一致结果）。
 *   - **与全书的关系**：连接层用 60-06 的 Netty 模型、协议用 18-01/60-07 的帧设计、投递用 28/62 的语义、分片用 39-05、心跳用 29-01、在线状态用 Redis TTL（25 章）——**IM 是"分布式系统工程"的综合演练**。
 *   - 易错点：只做单聊不验证群聊扇出（写放大）；没有 seq（多端同步无从谈起）；ACK 缺失（丢消息不可知）；离线消息与会话列表不一致（未读数错）。
 * ============================================================
 */

package com.heima.ch65;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ImCase {
    record Msg(String msgId, String clientMsgId, long seq, String convId,
               String from, String text, int acked) {
    }

    static final Map<String, List<Msg>> convStore = new LinkedHashMap<>();   // 会话流水
    static final Map<String, List<String>> inbox = new LinkedHashMap<>();     // 用户收件箱（引用）
    static final Map<String, List<String>> userConvs = new LinkedHashMap<>();
    static final Map<String, Long> readSeq = new LinkedHashMap<>();
    static final Map<String, Boolean> online = new LinkedHashMap<>();
    static long seqCounter = 0;

    static void register(String userId) {
        online.put(userId, true);
        System.out.println("  " + userId + " 上线");
    }

    static void send(String clientMsgId, String from, String convId,
                     List<String> members, String text) {
        List<Msg> conv = convStore.computeIfAbsent(convId, k -> new ArrayList<>());
        for (Msg m : conv) {
            if (m.clientMsgId().equals(clientMsgId)) {
                System.out.println("  上行重复 clientMsgId=" + clientMsgId + " -> 忽略");
                return;
            }
        }
        seqCounter++;
        Msg msg = new Msg("m-" + seqCounter, clientMsgId, conv.size() + 1L, convId, from, text, 0);
        conv.add(msg);
        for (String member : members) {
            userConvs.computeIfAbsent(member, k -> new ArrayList<>());
            if (!userConvs.get(member).contains(convId)) {
                userConvs.get(member).add(convId);
            }
            if (!member.equals(from)) {
                inbox.computeIfAbsent(member, k -> new ArrayList<>())
                        .add(msg.msgId() + "@" + convId);
            }
            boolean on = Boolean.TRUE.equals(online.get(member));
            System.out.printf("    -> %s %s（seq=%d）%n", member,
                    on ? "实时推送" : "离线暂存", msg.seq());
        }
        System.out.println("  发送成功：" + msg.msgId() + " 会话内 seq=" + msg.seq());
    }

    /** 同步：按会话拉增量 */
    static void sync(String userId, String convId, long lastSeq) {
        List<Msg> conv = convStore.getOrDefault(convId, List.of());
        List<String> delta = new ArrayList<>();
        for (Msg m : conv) {
            if (m.seq() > lastSeq) {
                delta.add(m.seq() + ":" + m.text());
            }
        }
        System.out.printf("  %s 同步 %s（after seq=%d）-> %s%n", userId, convId, lastSeq, delta);
        readSeq.put(userId + "@" + convId, (long) conv.size());
    }

    static long unread(String userId, String convId) {
        long total = convStore.getOrDefault(convId, List.of()).size();
        long read = readSeq.getOrDefault(userId + "@" + convId, 0L);
        return Math.max(0, total - read);
    }

    public static void main(String[] args) {
        register("u-1");
        register("u-2");
        online.put("u-3", false);

        System.out.println();
        System.out.println("【单聊】");
        send("c-msg-1", "u-1", "c-1", List.of("u-1", "u-2"), "你好");
        send("c-msg-1", "u-1", "c-1", List.of("u-1", "u-2"), "你好（重复）");

        System.out.println();
        System.out.println("【群聊（3 人，其中 u-3 离线）】");
        send("c-msg-2", "u-1", "g-1", List.of("u-1", "u-2", "u-3"), "开会了");

        System.out.println();
        System.out.println("【同步与未读】");
        sync("u-2", "c-1", 0);
        sync("u-2", "g-1", 0);
        System.out.printf("  u-3 未读：c-1=%d g-1=%d（上线后按 seq 拉取）%n",
                unread("u-3", "c-1"), unread("u-3", "g-1"));

        System.out.println();
        System.out.println("【闭环断言】");
        Map<String, String> asserts = new LinkedHashMap<>();
        asserts.put("不丢", "每条消息在会话流水中，离线用户上线可拉");
        asserts.put("不重", "clientMsgId + msgId 双去重");
        asserts.put("有序", "会话内 seq 单调递增");
        asserts.put("可同步", "任意端按 lastSeq 拉增量");
        asserts.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现 IM 闭环（连接/协议/上行/路由/投递/同步/存储），用 2 单聊 + 1 群聊验证四个断言。
 * 2. 加入"已读回执"与"多端同步"：u-2 手机读完，平板未读数归零，验证 readSeq 取最大值。
 * 3. 思考题：为什么 IM 的"闭环断言"里没有"恰好一次"？从"至少一次 + 双去重"的工程取舍分析（65-04 的呼应）。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 设计一个 IM 系统：长连接接入与心跳、消息序号与去重、离线消息与漫游、群聊扩散写与读、已读回执、多端同步。核心指标是「消息不丢、不乱、不重」。
 * 【任务】
 * 1. 实现消息序号：会话内单调递增 seq，客户端按 seq 排序与去重，处理乱序到达。
 * 2. 设计离线消息：在线推送 + 离线落库，上线后按 seq 拉取增量，说明如何避免拉取期间的新消息丢失。
 * 3. 对比群聊两种模式：写扩散（每人一份收件箱）与读扩散（共享一份消息表），给出万人群的选择与理由。
 * 4. 实现多端同步：同一账号多设备在线，已读状态同步、消息撤回同步，说明设备维度与用户维度的位点管理。
 * 【验收清单】
 * [ ] 客户端收到的消息按 seq 严格有序，重复消息被去重。
 * [ ] 断线重连后能补齐断连期间的全部消息，不丢不重。
 * [ ] 群聊方案选型有量化依据（成员数阈值、写放大倍数）。
 * [ ] 多端已读/撤回状态一致，任一端的操作其他端可见。
 * ============================================================
 */
