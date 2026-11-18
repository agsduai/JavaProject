/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 29-02
 * 课名：熔断器三态
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现熔断器（CLOSED/OPEN/HALF_OPEN）：滑动窗口统计失败率（最近 20 次）、失败率超阈值 -> OPEN（拒绝请求，快速失败）、冷却时间到 -> HALF_OPEN（放行探测请求）、探测成功 -> CLOSED / 失败 -> OPEN；模拟下游"正常->故障->恢复"全周期，输出每请求的三态与决策日志。
 *   - 【考核点】三态迁移条件、滑动窗口统计、快速失败保护上游、半开探测机制、与重试的区别（熔断不重试只拒绝）。
 *   - 【易错点】半开放行全部流量（应限探测数）；窗口用总累计（历史失败拖住熔断器）；OPEN 期间仍打下游（保护失效）。
 *   - 【提示】半开态维护探测计数：前 N 次真实放行，其余快速失败；成功 M 次才回 CLOSED。
 * ============================================================
 */

package com.heima.practice.middleware;

// ==================== 示例代码 ====================

public class CircuitBreakerTask2 {
    enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    static State state = State.CLOSED;
    static int fails = 0, probeOk = 0;

    static boolean call(boolean downstreamOk) {
        switch (state) {
            case OPEN -> {
                System.out.println("  熔断中 -> 快速失败");
                return false;
            }
            case HALF_OPEN -> {
                if (downstreamOk) {
                    probeOk++;
                    if (probeOk >= 2) {
                        state = State.CLOSED;
                        System.out.println("  探测成功 x2 -> 回 CLOSED");
                    }
                    return true;
                }
                state = State.OPEN;
                System.out.println("  探测失败 -> 回 OPEN");
                return false;
            }
            default -> {
                if (downstreamOk) {
                    fails = 0;
                    return true;
                }
                fails++;
                if (fails >= 3) {
                    state = State.OPEN;
                    System.out.println("  连续 3 失败 -> OPEN");
                }
                return false;
            }
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            call(false);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整熔断器（滑动窗口 + 三态 + 半开探测），模拟"正常->故障->恢复"全周期输出决策日志。
 * 2. 实现"按接口隔离熔断"（同一服务不同接口独立熔断器），对比全局熔断的误伤面。
 * 3. 思考题：为什么熔断器"快速失败"比"等待超时"更保护系统？从线程占用与故障放大的角度分析 29 章熔断与超时的组合。
 * ============================================================
 */
