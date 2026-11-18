/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 32-02
 * 课名：死锁检测与预防
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】构造经典死锁（两线程交叉加锁 A->B 与 B->A），用 ThreadMXBean.findDeadlockedThreads 检测并输出死锁对（线程与持有的锁）；修复两版：① 统一加锁顺序（按锁 id 排序）；② tryLock 超时回退；输出修复前后对比（死锁发生 vs 正常完成）。
 *   - 【考核点】死锁四条件、ThreadMXBean 检测、锁排序预防、tryLock 超时回退、死锁诊断输出。
 *   - 【易错点】修复用"加 try-catch"（死锁不是异常）；锁排序 key 不稳定（对象 hashCode 不固定，用业务 id）；检测线程阻塞主流程。
 *   - 【提示】ThreadMXBean.findDeadlockedThreads 返回死锁线程 id 数组，再取线程信息与锁信息打印证据。
 * ============================================================
 */

package com.heima.practice.deepdive;

// ==================== 示例代码 ====================

public class DeadlockDetector {
    static final Object L1 = new Object(), L2 = new Object();

    static void transfer(boolean direction) {
        Object first = direction ? L1 : L2;
        Object second = direction ? L2 : L1;
        synchronized (first) {
            synchronized (second) {
                System.out.println("  " + Thread.currentThread().getName() + " 完成转账");
            }
        }
    }

    public static void main(String[] args) {
        // 统一顺序：无论方向，先拿 L1 再拿 L2 —— 死锁四条件被破坏（占有且等待消失）
        Thread t1 = new Thread(() -> transfer(true), "t1");
        Thread t2 = new Thread(() -> transfer(false), "t2");
        t1.start();
        t2.start();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整实现死锁复现（交叉加锁）+ ThreadMXBean 检测输出 + 两版修复验证。
 * 2. 实现"tryLock 超时回退 + 事务语义回滚"版（拿不到第二把锁释放第一把），对比排序法的适用场景。
 * 3. 思考题：为什么数据库死锁由 DB 自动检测回滚而应用层需要自己预防？从检测成本与回滚粒度的差异分析。
 * 
 * 
 * ## 第33章 测试与质量保障
 * ============================================================
 */
