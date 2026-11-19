/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-10
 * 课名：弱网与移动端优化
 * ------------------------------------------------------------
 * 知识点：
 *   - **移动端 IM 的网络特征**：**频繁切换网络**（WiFi/4G/5G）、**信号抖动**（延迟与丢包）、**系统杀后台**（连接被冻结）、**省电策略**（长连接被系统限制）——**"连接稳定"在移动端是奢侈品**，客户端必须处理"随时断开"。
 *   - **弱网优化的五个手段**：**断线重连**（指数退避 + 抖动，40-02）、**增量同步**（重连后按 seq 拉，65-05）、**消息合并**（多条离线合并拉取）、**本地缓存**（已读消息本地存，弱网时先展示）、**发送队列**（离线时消息进队列，恢复后按序发送）。
 *   - **发送队列的状态机**：`PENDING（本地待发）-> SENDING（已发出待 ACK）-> SENT（已 ACK）-> FAILED（重试超限）`——**客户端必须有"发送中/失败/重发"的 UI 状态**（用户知道消息到底发出去没有）。
 *   - **重连的防雪崩**：**十万客户端同时重连会打爆网关**——服务端要**限流新连接**（网关容量保护），客户端要**随机退避**（不是所有客户端 1 秒后同时重连）；**连接数恢复要有梯度**。
 *   - **与 47-02/50 章的对应**：长连接是"资源"，重连是"资源重建"——**重连风暴与重试风暴同构**（44-08 的退避与抖动）。
 *   - 易错点：断线后不补偿（消息丢失）；重连立即拉全量历史（流量爆炸）；发送失败无 UI 提示（用户以为发出去了）；重连无退避（重启网关即全站雪崩）。
 * ============================================================
 */

package com.heima.ch65;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WeakNetwork {
    enum SendState {
        PENDING, SENDING, SENT, FAILED
    }

    record Outgoing(String clientMsgId, String text, SendState state, int attempts) {
        Outgoing move(SendState next, int attempts) {
            return new Outgoing(clientMsgId, text, next, attempts);
        }
    }

    /** 发送队列推进：成功则 SENT，失败重试，超限 FAILED */
    static void drain(List<Outgoing> queue, boolean networkUp, int maxAttempts) {
        for (int i = 0; i < queue.size(); i++) {
            Outgoing o = queue.get(i);
            if (o.state() == SendState.SENT || o.state() == SendState.FAILED) {
                continue;
            }
            if (!networkUp) {
                queue.set(i, o.move(SendState.PENDING, o.attempts()));
                continue;
            }
            if (o.attempts() >= maxAttempts) {
                queue.set(i, o.move(SendState.FAILED, o.attempts()));
                continue;
            }
            boolean ack = o.attempts() < 1;             // 简化：第一次成功
            if (ack) {
                queue.set(i, o.move(SendState.SENT, o.attempts() + 1));
            } else {
                queue.set(i, o.move(SendState.SENDING, o.attempts() + 1));
            }
        }
    }

    static void printQueue(List<Outgoing> queue) {
        queue.forEach(o -> System.out.printf("  %-10s %-8s attempts=%d %s%n",
                o.clientMsgId(), o.state(), o.attempts(), o.text()));
    }

    static void reconnectStorm() {
        System.out.println("【重连风暴的两种策略】");
        long base = 1_000;
        System.out.println("  无退避：10 万客户端全部 1s 后重连 -> 网关瞬时 10 万连接");
        List<Long> delays = new ArrayList<>();
        for (int attempt = 1; attempt <= 5; attempt++) {
            delays.add(reconnectDelayJitter(attempt, base, attempt * 137L));
        }
        System.out.println("  指数 + 抖动（示意）：" + delays + " ms");
        System.out.println("  效果：重连在时间上摊开，网关可梯度恢复");
    }

    /** 简化版重连延迟（避免移位溢出） */
    static long reconnectDelayJitter(int attempt, long baseMs, long jitterMs) {
        long exp = baseMs * (long) Math.pow(2, Math.min(attempt - 1, 5));
        return exp + jitterMs;
    }

    public static void main(String[] args) {
        System.out.println("【离线时发送：进队列 PENDING】");
        List<Outgoing> queue = new ArrayList<>();
        queue.add(new Outgoing("c-1", "消息A", SendState.PENDING, 0));
        queue.add(new Outgoing("c-2", "消息B", SendState.PENDING, 0));
        drain(queue, false, 3);
        printQueue(queue);

        System.out.println();
        System.out.println("【网络恢复：按序发送】");
        drain(queue, true, 3);
        printQueue(queue);

        System.out.println();
        System.out.println("【失败与重试】");
        List<Outgoing> retryQueue = new ArrayList<>();
        retryQueue.add(new Outgoing("c-3", "消息C", SendState.PENDING, 0));
        for (int round = 0; round < 4; round++) {
            drain(retryQueue, true, 3);
        }
        printQueue(retryQueue);

        System.out.println();
        System.out.println("【重连补偿：只拉增量】");
        System.out.println("  重连后请求 sync(lastSeq=87) -> 只补 88..N（65-05），不拉全量");
        System.out.println();
        reconnectStorm();
        System.out.println();
        System.out.println("  移动端铁律：随时会断 -> 队列 + 增量 + 退避 + UI 状态");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现发送队列：PENDING/SENDING/SENT/FAILED 四态 + 网络开关 + 重试上限，用 3 条消息验证"离线排队 -> 恢复发送 -> 失败提示"。
 * 2. 实现重连退避与网关限流：模拟 1 万客户端重连，对比"无退避"与"指数+抖动"下的瞬时连接峰值。
 * 3. 思考题：为什么"重连后不能拉全量历史"？从流量成本与拉取耗时两个角度分析，并给出增量同步的条件。
 * ============================================================
 */
