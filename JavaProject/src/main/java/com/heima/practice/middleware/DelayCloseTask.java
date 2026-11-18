/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 28-02
 * 课名：延迟队列与本地消息表
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现延迟订单关闭：下单 15 分钟未支付自动关单——用时间轮/优先队列（按到期时间排序）模拟延迟投递；支付回调与关单竞态（先关单后回调 -> 触发退款差异单，51 章复用）；本地消息表保证"关单事件"可靠发出；模拟 3 单（按时支付/超时关单/竞态）输出全轨迹。
 *   - 【考核点】延迟投递实现（优先队列/时间轮思想）、关单与支付竞态裁决、本地消息表可靠发出、超时关单的幂等。
 *   - 【易错点】延迟队列重启丢消息（应持久化/用本地消息表驱动）；关单与支付无竞态裁决（双成功）；关单重复执行（未幂等）。
 *   - 【提示】优先队列按 (到期时间, 单号) 排序，消费侧循环取到期项；竞态用状态机裁决（51-03 同款）。
 * ============================================================
 */

package com.heima.practice.middleware;

import java.util.PriorityQueue;
import java.util.Comparator;

// ==================== 示例代码 ====================

public class DelayCloseTask {
    record DelayedTask(long dueMs, String task) implements Comparable<DelayedTask> {
        public int compareTo(DelayedTask o) {
            return Long.compare(dueMs, o.dueMs);
        }
    }

    public static void main(String[] args) {
        PriorityQueue<DelayedTask> q = new PriorityQueue<>(Comparator.naturalOrder());
        q.add(new DelayedTask(3000, "close-order-9001"));
        q.add(new DelayedTask(1000, "send-reminder-9002"));
        long now = 0;
        while (!q.isEmpty() && now <= 3000) {
            if (q.peek().dueMs() <= now) {
                System.out.printf("  t=%dms 执行 %s%n", now, q.poll().task());
            }
            now += 1000;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现延迟关单完整版（优先队列/时间轮 + 本地消息表 + 竞态裁决），三单轨迹输出。
 * 2. 实现"重启恢复"：延迟任务持久化，进程重启后重建队列，验证不丢不重。
 * 3. 思考题：为什么关单用"延迟队列"而不用"定时扫表"？对比两者的时效精度、DB 压力与实现复杂度。
 * 
 * 
 * ## 第29章 微服务入门
 * ============================================================
 */
