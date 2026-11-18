/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-07
 * 课名：分布式 ID：雪花算法
 * ------------------------------------------------------------
 * 知识点：
 *   - **分库分表后自增主键必然冲突**：两台机器各发 1、2、3，合并就是重复。业务需要**全局唯一且趋势递增**的 ID——递增保证 B+ 树顺序插入（随机主键会导致页分裂与缓存抖动），唯一保证路由与合并正确。
 *   - **雪花算法（Snowflake）的三段结构（64 位）**：1 位符号恒 0 + 41 位毫秒时间戳（约可用 69 年）+ 10 位机器 ID（5 位数据中心 + 5 位机器，或全给机器=1024 台）+ 12 位序列号（同一毫秒内最多 4096 个）。**每毫秒每机器可发 4096 个 ID，每秒约 400 万**。
 *   - **机器 ID 的分配是运维问题**：写死在配置里会人工重复；常用方案：ZooKeeper 顺序节点、数据库注册表、K8s StatefulSet 序号。**同一毫秒同一机器 ID 才可能撞**，机器 ID 冲突 = 灾难，必须中心化分配。
 *   - **时钟回拨是雪花算法的头号故障**：NTP 校时把机器时间拨回去，时间戳变小，会发出与过去重复的 ID。对策：① 回拨 < 5ms 忙等追平；② 回拨大则报错拒发（拒绝服务优于数据重复）；③ 用"上次时间戳"维护单调性，`timestamp = max(now, lastTs)`。
 *   - **序列号用尽**：单机单毫秒超过 4096 个请求，序列溢出——标准做法是**自旋等待下一毫秒**（把时钟推进到 lastTs+1）。高并发场景这是正常分支，不是异常。
 *   - **趋势递增不等于严格递增**：多机器并发时，后发出的 ID 可能小于另一台刚发出的 ID（时间戳精度是毫秒）。排序场景要么容忍（按 create_time 排），要么牺牲并发换严格递增（号段模式）。
 *   - **号段模式（Leaf）作为对照**：数据库里存当前最大值，应用一次取一段（如 1000 个）缓存在内存里用完再取。实现简单、ID 短，但取号段时重启会"跳号"，且强依赖数据库可用性。双 buffer 预取消除取号延迟。
 *   - 易错点：机器 ID 重复（两台机器 ID 相同同时发号）；忽略时钟回拨（NTP 一定会来）；把雪花 ID 当严格排序键（跨机器不严格）；41 位时间戳的起始纪元乱设导致 69 年寿命缩水。
 * ============================================================
 */

package com.heima.ch39;

// ==================== 示例代码 ====================

public class DistributedId {
    /** 雪花算法可运行实现：标准位分配 1+41+10+12 */
    static class Snowflake {
        private static final long EPOCH = 1735689600000L;  // 2025-01-01 起始纪元
        private static final long WORKER_BITS = 10, SEQ_BITS = 12;
        private static final long MAX_WORKER = (1L << WORKER_BITS) - 1;
        private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;

        private final long workerId;
        private long lastTs = -1;
        private long seq = 0;

        Snowflake(long workerId) {
            if (workerId < 0 || workerId > MAX_WORKER) {
                throw new IllegalArgumentException("workerId 超范围: " + workerId);
            }
            this.workerId = workerId;
        }

        synchronized long nextId() {
            long now = System.currentTimeMillis();
            if (now < lastTs) {                       // 时钟回拨
                long rollback = lastTs - now;
                if (rollback <= 5) {                  // 小回拨：忙等追平
                    while (System.currentTimeMillis() <= lastTs) {
                        Thread.onSpinWait();
                    }
                    now = System.currentTimeMillis();
                } else {                              // 大回拨：拒发，宁停勿重
                    throw new IllegalStateException("时钟回拨 " + rollback + "ms，拒绝发号");
                }
            }
            if (now == lastTs) {
                seq = (seq + 1) & MAX_SEQ;            // 同毫秒序列递增
                if (seq == 0) {                       // 序列用尽：等到下一毫秒
                    while (System.currentTimeMillis() <= lastTs) {
                        Thread.onSpinWait();
                    }
                    now = System.currentTimeMillis();
                }
            } else {
                seq = 0;
            }
            lastTs = now;
            return ((now - EPOCH) << (WORKER_BITS + SEQ_BITS))
                    | (workerId << SEQ_BITS)
                    | seq;
        }

        static String dissect(long id) {
            long ts = (id >> (WORKER_BITS + SEQ_BITS)) + EPOCH;
            long worker = (id >> SEQ_BITS) & MAX_WORKER;
            long seq = id & MAX_SEQ;
            return String.format("时间戳=%d worker=%d seq=%d", ts, worker, seq);
        }
    }

    static void basicIssue() {
        Snowflake sf = new Snowflake(7);
        System.out.println("【同一毫秒连续发号：worker=7】");
        long first = sf.nextId();
        long second = sf.nextId();
        System.out.println("  id1=" + first);
        System.out.println("  id2=" + second);
        System.out.println("  id1 拆解：" + Snowflake.dissect(first));
        System.out.println("  趋势递增：id2 > id1 -> " + (second > first));
        System.out.println();
    }

    static void throughput() {
        System.out.println("【发号吞吐：单机每秒理论上限】");
        long perMs = 4096;
        System.out.printf("  12 位序列 -> 每毫秒 %d 个 -> 每秒约 %d 万个%n",
                perMs, perMs * 1000 / 10000);
        Snowflake sf = new Snowflake(1);
        long t0 = System.nanoTime();
        long[] ids = new long[100_000];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = sf.nextId();
        }
        long ms = Math.max(1, (System.nanoTime() - t0) / 1_000_000);
        System.out.printf("  实测 %d 个耗时 %dms（含跨毫秒等待）%n", ids.length, ms);
        System.out.println();
    }

    static void twoWorkers() {
        System.out.println("【两台机器并发发号：唯一但全局不严格递增】");
        Snowflake w1 = new Snowflake(101), w2 = new Snowflake(202);
        long a = w1.nextId(), b = w2.nextId(), c = w1.nextId();
        System.out.printf("  w1:%d  w2:%d  w1:%d%n", a, b, c);
        System.out.println("  同毫秒时 w1 的第二个 ID 也可能小于 w2 的第一个（毫秒粒度内无全局序）");
        System.out.println("  排序需求：按 create_time + id 双键，或接受「近似有序」");
        System.out.println();
    }

    static void epochNote() {
        System.out.println("【纪元与寿命：41 位时间戳用 69 年】");
        long epoch = 1735689600000L;
        long maxTs = (1L << 41) - 1;
        System.out.printf("  纪元 %d + 2^41ms ≈ 纪元 + %.0f 年%n", epoch, maxTs / 3.15e10);
        System.out.println("  纪元取「项目上线日」而不是 1970，寿命从今天起算 69 年");
    }

    public static void main(String[] args) {
        basicIssue();
        throughput();
        twoWorkers();
        epochNote();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现双 worker 并发发号（各发 1 万个），用 Set 验证无重复，并统计"全局逆序对"数量，体会"趋势递增≠严格递增"。
 * 2. 模拟时钟回拨：改造示例，允许注入时间源，触发 3ms 与 50ms 两种回拨，验证忙等与拒绝两种分支。
 * 3. 实现号段模式（双 buffer）：数据库表模拟号段，对比雪花与号段的 ID 长度、有序性、依赖项。
 * 4. 思考题：为什么"机器 ID 中心化分配"比"配置文件写死"更安全？给出两种自动分配方案与各自的失败模式。
 * ============================================================
 */
