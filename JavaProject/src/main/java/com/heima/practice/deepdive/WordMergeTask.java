/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 32-01
 * 课名：ConcurrentHashMap 并发聚合
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现多线程词频统计：8 线程各处理 1 段文本，用 ConcurrentHashMap.merge 聚合词频（线程安全无锁竞争争论点）；对比 HashMap+synchronized 全局锁与 LongAdder 计数；输出正确性验证（与单线程结果一致）与耗时对比。
 *   - 【考核点】ConcurrentHashMap.merge 原子聚合、LongAdder 高频计数、正确性验证方法（并发 vs 串行等价）。
 *   - 【易错点】get 后 put 的复合操作（丢失更新，用 merge/compute）；size() 精度误解（弱一致）；key null 值异常。
 *   - 【提示】正确性验证的黄金方法：并发结果 == 单线程串行结果（词频全量比对）。
 * ============================================================
 */

package com.heima.practice.deepdive;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// ==================== 示例代码 ====================

public class WordMergeTask {
    static final Map<String, Integer> count = new ConcurrentHashMap<>();

    static void hit(String key) {
        count.merge(key, 1, Integer::sum);      // 原子读改写
    }

    public static void main(String[] args) throws Exception {
        Thread[] ts = new Thread[8];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    hit("k");
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) {
            t.join();
        }
        System.out.printf("  并发 merge 后 k=%d（期望 8000）%n", count.get("k"));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整词频统计（8 线程 merge + 串行等价验证），输出正确性与耗时对比。
 * 2. 把高频计数换 LongAdder（只加不改读），对比 sum 精度与吞吐（1000 万次自增实验）。
 * 3. 思考题：为什么 ConcurrentHashMap 的 size() 是弱一致的？从分段计数与并发写的关系分析正确使用方式。
 * ============================================================
 */
