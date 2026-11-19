/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-04
 * 课名：消息可靠投递：ACK、重传与去重
 * ------------------------------------------------------------
 * 知识点：
 *   - **IM 的投递是"至少一次 + 客户端去重"**（28/62 章语义的应用）：服务端把消息写入存储 -> 推送给在线接收方 -> **接收方回 ACK** -> 服务端标记"已送达"；**未收到 ACK 则重传**（指数退避），**客户端按 msgId 去重**——**任何"可靠投递"最终都要落到幂等**。
 *   - **三级 ACK 链路**：**客户端 -> 服务端**（上行 ACK：消息已收到并落库）、**服务端 -> 接收端**（下行 ACK：已推送）、**接收端 -> 服务端**（接收 ACK：已收到）——**每一跳都可能丢，所以每一跳都要 ACK**。
 *   - **重传的边界**：**只重传"未 ACK"的消息**（不能重传已 ACK 的）；**重传有上限**（超时多次 -> 标记失败，转为"发送失败"提示用户）；**重传要按 msgId 幂等**（服务端与客户端都去重）。
 *   - **消息状态机**：`CREATED -> STORED -> PUSHED -> DELIVERED -> READ`——**未读数 = STORED - READ**（65-05 的已读回执）；状态只进不退（51-03 同款）。
 *   - **消息 ID 的生成**：**服务端生成**（雪花 39-07：全局唯一 + 趋势递增，便于排序）；**客户端生成 clientMsgId**（用于上行的幂等与"发送中"状态关联）——**双 ID 体系**是 IM 的标准做法。
 *   - 易错点：无 ACK 直接标已送达（消息丢了不知道）；重传不去重（客户端重复显示）；消息 ID 用时间戳（同毫秒冲突）；状态可回退（已读变未读）。
 * ============================================================
 */

package com.heima.ch65;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ReliableDelivery {
    enum State {
        CREATED, STORED, PUSHED, DELIVERED, READ, FAILED
    }

    record Message(String msgId, String clientMsgId, String from, String to,
                   String text, State state, int retries) {
        Message advance(State next) {
            return new Message(msgId, clientMsgId, from, to, text, next, retries);
        }

        Message retry() {
            return new Message(msgId, clientMsgId, from, to, text, state, retries + 1);
        }
    }

    static final Map<String, Message> store = new LinkedHashMap<>();
    static final Map<String, String> clientIdIndex = new LinkedHashMap<>();   // 上行幂等

    /** 上行：客户端发消息（clientMsgId 幂等） */
    static String send(String clientMsgId, String from, String to, String text, long seq) {
        String existing = clientIdIndex.get(clientMsgId);
        if (existing != null) {
            System.out.println("  上行重复 clientMsgId=" + clientMsgId + " -> 返回已存在的 msgId");
            return existing;
        }
        String msgId = "m-" + seq;
        store.put(msgId, new Message(msgId, clientMsgId, from, to, text, State.STORED, 0));
        clientIdIndex.put(clientMsgId, msgId);
        System.out.println("  上行消息落库：msgId=" + msgId + " 状态 STORED");
        return msgId;
    }

    /** 下行推送：标记 PUSHED，等待接收 ACK */
    static void push(String msgId) {
        Message m = store.get(msgId);
        if (m != null) {
            store.put(msgId, m.advance(State.PUSHED));
            System.out.println("  推送 " + msgId + " -> 等待接收 ACK");
        }
    }

    /** 接收 ACK：幂等（已 DELIVERED 则忽略），未收到则重传 */
    static void onAck(String msgId, boolean ackReceived) {
        Message m = store.get(msgId);
        if (m == null) {
            return;
        }
        if (ackReceived) {
            if (m.state() == State.DELIVERED || m.state() == State.READ) {
                System.out.println("  重复 ACK " + msgId + " -> 忽略（幂等）");
                return;
            }
            store.put(msgId, m.advance(State.DELIVERED));
            System.out.println("  收到 ACK " + msgId + " -> DELIVERED");
            return;
        }
        if (m.retries() >= 3) {
            store.put(msgId, m.advance(State.FAILED));
            System.out.println("  " + msgId + " 重传 3 次仍无 ACK -> FAILED（提示发送失败）");
            return;
        }
        store.put(msgId, m.retry());
        System.out.println("  " + msgId + " 无 ACK -> 第 " + (m.retries() + 1) + " 次重传");
    }

    /** 已读回执：DELIVERED -> READ，只进不退 */
    static void onRead(String msgId) {
        Message m = store.get(msgId);
        if (m == null || m.state() == State.READ) {
            return;
        }
        if (m.state() == State.DELIVERED || m.state() == State.PUSHED
                || m.state() == State.STORED) {
            store.put(msgId, m.advance(State.READ));
            System.out.println("  " + msgId + " 已读回执 -> READ");
        }
    }

    public static void main(String[] args) {
        System.out.println("【上行幂等】");
        send("client-1", "u-1", "u-2", "你好", 1);
        send("client-1", "u-1", "u-2", "你好", 2);

        System.out.println();
        System.out.println("【下行 ACK 与重传】");
        push("m-1");
        onAck("m-1", false);
        onAck("m-1", false);
        onAck("m-1", true);
        onAck("m-1", true);

        System.out.println();
        System.out.println("【失败路径】");
        send("client-2", "u-1", "u-2", "在吗", 3);
        push("m-3");
        for (int i = 0; i < 4; i++) {
            onAck("m-3", false);
        }

        System.out.println();
        System.out.println("【已读回执】");
        onRead("m-1");
        onRead("m-1");

        System.out.println();
        System.out.println("【最终状态】");
        store.values().forEach(m -> System.out.printf("  %-4s %-10s retries=%d%n",
                m.msgId(), m.state(), m.retries()));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整投递状态机：五态流转 + 三级 ACK + 重传上限 + 上行/下行双向去重，用 3 条消息跑通"成功/重传成功/失败"三条路径。
 * 2. 实现"未读数计算"：`STORED - READ`，构造"多端读取"场景验证未读数在各端的表现。
 * 3. 思考题：为什么 IM 采用"至少一次 + 去重"而不是"尝试做到恰好一次"？从网络不可靠与端侧去重成本分析。
 * ============================================================
 */
