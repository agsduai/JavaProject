/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 30-02
 * 课名：GC 日志解析器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现 GC 日志解析器：输入典型 GC 日志行（自造格式：`[gc] type=MinorPause cause=AllocationFailure young_before=120M young_after=40M duration=15ms`），解析成结构化记录；统计：Minor/Major 频次、平均暂停、晋升速率（young_after 变化推算）；按"暂停时间预算"判定健康（P99 暂停 >50ms 告警）；输出健康报告。
 *   - 【考核点】GC 日志字段语义、暂停时间与吞吐的权衡、晋升速率与老年代压力、日志解析的正则应用。
 *   - 【易错点】只统计次数不看暂停分布；忽略 Full GC cause（System.gc vs 容量不足）；单位换算错误（M/K/B）。
 *   - 【提示】解析器输出结构化列表后，用 42 章分位数方法统计暂停 P50/P99。
 * ============================================================
 */

package com.heima.practice.deepdive;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

public class GcLogParser {
    public static void main(String[] args) {
        Pattern p = Pattern.compile("type=(\\w+).*?duration=(\\d+)ms");
        List<String> logs = List.of(
                "[gc] type=Minor cause=AllocationFailure duration=12ms",
                "[gc] type=Full cause=System.gc duration=180ms",
                "[gc] type=Minor cause=Promotion duration=18ms");
        long total = 0, full = 0;
        for (String line : logs) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                long d = Long.parseLong(m.group(2));
                total += d;
                if (m.group(1).equals("Full")) {
                    full++;
                }
                System.out.printf("  %s 暂停 %dms%n", m.group(1), d);
            }
        }
        System.out.printf("  总暂停 %dms，Full 次数 %d（System.gc 应排查调用方）%n", total, full);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整解析器（结构化 + 频次 + 暂停分位 + 晋升速率），对 50 行模拟日志输出健康报告。
 * 2. 加入"cause 分类统计"（AllocationFailure/System.gc/Promotion），找出 Top 根因。
 * 3. 思考题：为什么 Full GC cause=System.gc 要单独告警？从 RMI/堆外内存触发的隐式调用与容量不足的排查路径差异分析。
 * 
 * 
 * ## 第31章 设计模式与重构
 * ============================================================
 */
