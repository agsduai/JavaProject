/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-06
 * 课名：redo log 与崩溃恢复
 * ------------------------------------------------------------
 * 知识点：
 *   - **WAL（Write-Ahead Logging）的核心规则**：**先写日志，再写数据页**——**日志顺序追加（快）**，**数据页随机写（慢）**；**崩溃后用日志重放未刷盘的修改**。
 *   - **redo log 的两阶段**：**事务提交时只需保证 redo 落盘（fsync）**，**数据页可以稍后刷**——**这就是"提交快"的原因**；**checkpoint 表示"此前的日志对应的页都已刷盘"**，恢复只需从 checkpoint 之后重放。
 *   - **undo log 与 redo log 分工**：**redo 保证"已提交的不丢"（ durability）**，**undo 保证"未提交的可回滚"（atomicity）**——**两者配合实现事务的 A/C/D**。
 *   - **两阶段提交（redo + binlog）**：**prepare（redo 落盘）-> 写 binlog -> commit**——**崩溃恢复时比对两者**，**保证主从一致**（这是 MySQL 特有的复杂点）。
 *   - 与 45 章的关系：45 章讲"分布式共识/Raft 日志"，**单机数据库的 WAL 是"日志复制"的起点**——**先有本地日志，才有跨机复制**。
 *   - 易错点：把 redo 当"缓存"（它是持久化日志）；以为"提交了就一定刷了数据页"（只刷了日志）；checkpoint 太频繁（刷盘压力）或太稀疏（恢复慢）；忽略 `innodb_flush_log_at_trx_commit` 的取舍。
 * ============================================================
 */

package com.heima.ch77;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class WALRecovery {
    record LogRecord(long lsn, int trxId, long pageId, String after) {
    }

    static final List<LogRecord> wal = new ArrayList<>();
    static final Map<Long, String> diskPages = new LinkedHashMap<>();
    static final Map<Long, String> bufferPages = new LinkedHashMap<>();
    static final Set<Long> dirtyPages = new LinkedHashSet<>();
    static long lsn;
    static long checkpointLsn;

    static void update(int trxId, long pageId, String value) {
        lsn++;
        wal.add(new LogRecord(lsn, trxId, pageId, value));   // 1) 先写日志
        bufferPages.put(pageId, value);                     // 2) 再改缓冲池
        dirtyPages.add(pageId);
        System.out.printf("  trx%d 更新 page%d -> %s（lsn=%d，日志已落盘，页仍脏）%n",
                trxId, pageId, value, lsn);
    }

    static void checkpoint() {
        for (Long p : dirtyPages) {
            diskPages.put(p, bufferPages.get(p));
        }
        dirtyPages.clear();
        checkpointLsn = lsn;
        System.out.printf("  checkpoint：脏页刷盘完成，checkpointLsn=%d%n", checkpointLsn);
    }

    static void crashAndRecover() {
        System.out.println("  *** 崩溃：缓冲池丢失，磁盘保留已刷页 ***");
        bufferPages.clear();
        dirtyPages.clear();
        System.out.println("  崩溃后磁盘：" + diskPages);
        int replayed = 0;
        for (LogRecord r : wal) {
            if (r.lsn() > checkpointLsn) {
                diskPages.put(r.pageId(), r.after());
                replayed++;
            }
        }
        System.out.printf("  从 checkpointLsn=%d 之后重放 %d 条日志%n", checkpointLsn, replayed);
        System.out.println("  恢复后磁盘：" + diskPages);
    }

    public static void main(String[] args) {
        diskPages.put(1L, "A0");
        diskPages.put(2L, "B0");

        System.out.println("【初始磁盘】" + diskPages);
        System.out.println();
        System.out.println("【事务 100：更新 A、B，然后 checkpoint】");
        update(100, 1, "A1");
        update(100, 2, "B1");
        checkpoint();

        System.out.println();
        System.out.println("【事务 101：更新 A 后提交，但未 checkpoint 就崩溃】");
        update(101, 1, "A2");
        crashAndRecover();

        System.out.println();
        System.out.println("【结论】");
        System.out.println("  1) 提交只需 redo 落盘，数据页可延后刷（提交快）");
        System.out.println("  2) 崩溃恢复从 checkpoint 之后重放，已提交的不丢");
        System.out.println("  3) redo 与 binlog 的两阶段提交保证主从一致");
        System.out.println("  4) checkpoint 越频繁恢复越快，但平时刷盘压力越大");

        System.out.println();
        System.out.println("【redo vs undo】");
        String[][] rows = {
                {"redo", "记录修改后的值", "崩溃恢复，保证已提交不丢"},
                {"undo", "记录修改前的值", "事务回滚 + MVCC 旧版本"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-6s %-16s %s%n", r[0], r[1], r[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 模拟"更新两页后崩溃"与"checkpoint 后崩溃"，验证恢复结果都等于已提交状态。
 * 2. 加入"未提交事务"的日志，恢复时用 undo 回滚，验证原子性。
 * 3. 思考题：为什么"提交快"和"恢复快"是一对矛盾？从 checkpoint 频率与刷盘压力分析。
 * ============================================================
 */
