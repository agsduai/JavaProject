/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 35-02
 * 课名：TCP 状态机模拟
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现 TCP 连接状态机（CLOSED/LISTEN/SYN_SENT/SYN_RCVD/ESTABLISHED/FIN_WAIT_1/TIME_WAIT/CLOSE_WAIT/LAST_ACK）：枚举 + 合法迁移表 + 非法迁移拒绝；模拟"三次握手建立 -> 数据 -> 主动关闭四次挥手 -> TIME_WAIT 等待 2MSL"全生命周期；输出每个事件的迁移日志；演示 TIME_WAIT 过多的问题与 SO_REUSEADDR。
 *   - 【考核点】TCP 状态机全迁移表、握手挥手对应状态、TIME_WAIT 存在意义（旧报文消散）、非法迁移防御。
 *   - 【易错点】TIME_WAIT 在主动关闭方（不是被动方）；跳过迁移（SYN_SENT 直接 ESTABLISHED）；把 CLOSE_WAIT 当服务端问题（其实是应用未 close）。
 *   - 【提示】迁移表用 Map<当前态+事件, 目标态>；日志输出便于对照 netstat 真实输出。
 * ============================================================
 */

package com.heima.practice.deepdive;

import java.util.Map;

// ==================== 示例代码 ====================

public class TcpStateMachine2 {
    record Transition(String from, String event, String to) {
    }

    static final Map<String, String> TABLE = Map.of(
            "CLOSED+connect", "SYN_SENT",
            "SYN_SENT+synack", "ESTABLISHED",
            "ESTABLISHED+close", "FIN_WAIT_1",
            "FIN_WAIT_1+ackfin", "TIME_WAIT");

    public static void main(String[] args) {
        String state = "CLOSED";
        for (String event : new String[]{"connect", "synack", "close", "ackfin"}) {
            String next = TABLE.get(state + "+" + event);
            if (next == null) {
                System.out.printf("  %s + %s -> 非法迁移，拒绝%n", state, event);
                continue;
            }
            System.out.printf("  %s --%s--> %s%n", state, event, next);
            state = next;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整状态机（全迁移表 + 四次挥手 + 非法迁移拒绝），输出主动/被动关闭两条完整路径。
 * 2. 模拟"TIME_WAIT 堆积"：1 万条短连接统计状态分布，解释 SO_REUSEADDR 与连接池的治理。
 * 3. 思考题：为什么 TIME_WAIT 要等 2MSL？从"旧报文消散"与"确保对端收到最终 ACK"两个目的推导。
 * 
 * 
 * ## 第36章 安全编码与漏洞防护
 * ============================================================
 */
