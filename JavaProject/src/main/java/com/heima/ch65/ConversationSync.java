/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-05
 * 课名：会话与消息同步：多端一致性
 * ------------------------------------------------------------
 * 知识点：
 *   - **会话同步的核心是"每会话一个递增序号（seq）"**：同一会话内消息 seq 严格递增，**客户端记录"已拉取到 seq=N"**，断线重连后请求 `seq > N` 的增量——**这是"增量同步"的唯一可靠方式**（比时间戳可靠，时钟不可信 45-07）。
 *   - **多端状态的三类同步**：**消息**（每端各自拉增量，互不影响）、**已读位点**（一端读了，其他端要同步"已读到 seq=M"）、**会话列表**（最后一条消息/未读数/置顶/免打扰）。
 *   - **已读位点的实现**：每个（用户 × 会话）记录一个 `readSeq`——**多端上报取最大值**（手机读到 100，平板读到 80 -> readSeq=100）；**已读回执广播**给会话内其他成员（单聊双方、群聊全员或仅发送者）。
 *   - **多端消息的重复与丢失**：**同一账号多端**（手机/平板/PC）各自独立拉取，**服务端按会话分发**；**多端同时在线**时要确保"一端删除会话不影响另一端的数据"（软删除 + 同步操作事件）。
 *   - **与 62-07 Offset 的类比**：IM 的 seq 就是"会话级 offset"——**幂等、单调、可重放**；差异是 IM 要按会话维护（一个用户 N 个会话 = N 个位点）。
 *   - 易错点：用时间戳做增量同步（时钟漂移导致漏拉/重复）；已读位点每端独立（不同步，未读数混乱）；会话列表不同步（一端删了另一端还在）；seq 由客户端生成（多端冲突）。
 * ============================================================
 */

package com.heima.ch65;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ConversationSync {
    /** 会话消息：seq 由服务端单调分配 */
    static final Map<String, List<String>> messages = new LinkedHashMap<>();
    static long[] seqOf = new long[1];

    /** 每用户每会话的已读位点 */
    static final Map<String, Long> readSeq = new LinkedHashMap<>();

    static void append(String convId, String text) {
        messages.computeIfAbsent(convId, k -> new ArrayList<>()).add(text);
        seqOf[0]++;
        System.out.printf("  追加 %s seq=%d：%s%n", convId, seqOf[0], text);
    }

    /** 增量同步：从 lastSeq 之后拉取 */
    static List<String> sync(String convId, long lastSeq) {
        List<String> all = messages.getOrDefault(convId, List.of());
        List<String> delta = new ArrayList<>();
        for (int i = (int) lastSeq; i < all.size(); i++) {
            delta.add((i + 1) + ":" + all.get(i));
        }
        return delta;
    }

    /** 多端上报已读：取最大值（幂等、只进不退） */
    static long reportRead(String userId, String convId, long seq) {
        String key = userId + "@" + convId;
        long current = readSeq.getOrDefault(key, 0L);
        long next = Math.max(current, seq);
        readSeq.put(key, next);
        System.out.printf("  %s 在 %s 上报已读 %d -> readSeq=%d%s%n",
                userId, convId, seq, next, next == current ? "（未推进）" : "");
        return next;
    }

    static long unread(String convId, String userId) {
        long total = messages.getOrDefault(convId, List.of()).size();
        long read = readSeq.getOrDefault(userId + "@" + convId, 0L);
        return Math.max(0, total - read);
    }

    public static void main(String[] args) {
        System.out.println("【消息写入（服务端分配 seq）】");
        append("c-1", "你好");
        append("c-1", "在吗");
        append("c-1", "今天发货吗");

        System.out.println();
        System.out.println("【增量同步：客户端已拉到 seq=1】");
        System.out.println("  delta=" + sync("c-1", 1));

        System.out.println();
        System.out.println("【多端已读同步】");
        reportRead("u-1", "c-1", 2);
        reportRead("u-1", "c-1", 3);
        reportRead("u-1", "c-1", 1);            // 旧位点不回退
        System.out.println("  当前 readSeq=" + readSeq.get("u-1@c-1"));

        System.out.println();
        System.out.println("【未读数】u-1 未读=" + unread("c-1", "u-1")
                + "  u-2 未读=" + unread("c-1", "u-2"));
        System.out.println();
        System.out.println("  关键：seq 单调 + 位点取最大 + 增量按 seq —— 多端天然一致");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整多端同步：3 个设备各自维护 lastSeq，模拟"设备 A 拉取 -> 设备 B 断线 -> B 重连拉增量"，验证不重不漏。
 * 2. 实现"会话列表同步"：最后一条消息/未读数/置顶状态，模拟"一端删除会话"通过操作事件同步到其他端。
 * 3. 思考题：为什么增量同步用 seq 而不是时间戳？从"时钟不同步"与"同毫秒多条消息"两个角度分析（45-07 的呼应）。
 * ============================================================
 */
