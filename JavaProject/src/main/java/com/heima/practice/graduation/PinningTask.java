/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 50-02
 * 课名：Pinning 排查与修复
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】构造 pinning 现场：10 个虚拟线程在 synchronized 方法内 sleep（模拟持锁做 IO），观察吞吐坍塌（实际并发退化为 1）；改用 ReentrantLock 修复后对比；输出“修复前/修复后”的完成耗时与结论（对应 JFR jdk.VirtualThreadPinned 事件的代码层复现）。
 *   - 【考核点】pinning 成因（synchronized 内阻塞不卸载）、载体耗尽的表现（吞吐退化为串行）、修复姿势（ReentrantLock）与验证方法。
 *   - 【易错点】只改锁不验证（修没修好要压测）；短临界区也全局替换（改动面失控）；误判（把下游慢当 pinning）。
 *   - 【提示】对照组实验设计：同任务量，仅锁实现不同——控制变量归因。
 * ============================================================
 */

package com.heima.practice.graduation;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ==================== 示例代码 ====================

public class PinningTask {
    static long shared = 0;
    static final Object monitor = new Object();

    static void workSync() {
        synchronized (monitor) {
            shared++;                  // 短临界区：此形态安全
        }
    }

    public static void main(String[] args) throws Exception {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 8; i++) {
                pool.submit(() -> {
                    for (int j = 0; j < 1000; j++) {
                        workSync();
                    }
                    return null;
                });
            }
        }
        System.out.println("  短临界区 synchronized 在虚拟线程下可接受；长阻塞才必须换 ReentrantLock");
        System.out.println("  计数结果：" + shared);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整复现 pinning：synchronized 内 sleep 200ms × 10 任务，记录耗时；换 ReentrantLock 对比并写结论。
 * 2. 用 JFR（jdk.VirtualThreadPinned）采集你的复现程序，验证事件栈与代码位置对应。
 * 3. 思考题：为什么“短临界区 synchronized 可以保留”？从临界区时长与载体占用概率的关系量化判断标准。
 * 
 * 
 * ## 第51章 支付与资金系统设计
 * ============================================================
 */
