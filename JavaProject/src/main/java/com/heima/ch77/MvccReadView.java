/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-04
 * 课名：事务与 MVCC
 * ------------------------------------------------------------
 * 知识点：
 *   - **MVCC 的核心：每行保留多个版本，读不加锁**——**写操作生成新版本（带事务 ID）**，**读操作按"可见性规则"挑一个版本**——**读写互不阻塞**，这是 InnoDB 高并发的关键。
 *   - **两个隐藏列**：**`trx_id`（最后修改该版本的事务）** 与 **`roll_ptr`（指向 undo log 中的旧版本）**——**版本链就是 undo 链**，回滚与一致性读都靠它。
 *   - **Read View 的可见性规则**：**版本 trx_id < 最小活跃事务 -> 可见**；**在活跃集合中 -> 不可见**；**>= 最大事务号 -> 不可见**；**其余可见**——**RC 每条语句新建 Read View，RR 整个事务复用第一个**。
 *   - **RC vs RR 的差别就来自 Read View 的创建时机**：**RC 能读到别人已提交的新数据（不可重复读）**；**RR 始终读事务开始时的快照（可重复读）**——**不是"锁"带来的，是"快照"带来的**。
 *   - 与 32 章的关系：32 章讲 Java 并发（锁/CAS），**MVCC 是"数据库版的无锁读"**——**同样的思想：用版本代替互斥**。
 *   - 易错点：以为 RR 完全无幻读（当前读仍可能幻读，需间隙锁 77-05）；长事务不提交（undo 链膨胀、快照过旧）；用 RC 跑对账（同一事务内数据会变）；把"快照读"与"当前读"混用。
 * ============================================================
 */

package com.heima.ch77;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// ==================== 示例代码 ====================

public class MvccReadView {
    record Version(int trxId, String value) {
    }

    static class Row {
        final List<Version> versions = new ArrayList<>();

        void write(int trxId, String value) {
            versions.add(new Version(trxId, value));
        }

        /** 找最新可见版本 */
        String read(ReadView view) {
            for (int i = versions.size() - 1; i >= 0; i--) {
                Version v = versions.get(i);
                if (view.visible(v.trxId())) {
                    return v.value();
                }
            }
            return null;
        }
    }

    static class ReadView {
        final int upLimit;                       // 创建时下一个事务号
        final Set<Integer> active;               // 创建时活跃事务
        final int minActive;

        ReadView(int upLimit, Set<Integer> active) {
            this.upLimit = upLimit;
            this.active = new LinkedHashSet<>(active);
            int min = upLimit;
            for (int id : active) {
                min = Math.min(min, id);
            }
            this.minActive = min;
        }

        boolean visible(int trxId) {
            if (trxId >= upLimit) {
                return false;                    // 视图创建后才开始的事务
            }
            if (active.contains(trxId)) {
                return false;                    // 创建时还未提交
            }
            return true;
        }
    }

    /** 模拟事务管理器：分配递增事务号 */
    static class TrxManager {
        int next = 1;
        final Set<Integer> active = new LinkedHashSet<>();

        int begin() {
            int id = next++;
            active.add(id);
            return id;
        }

        void commit(int id) {
            active.remove(id);
        }

        ReadView snapshot() {
            return new ReadView(next, active);
        }
    }

    public static void main(String[] args) {
        Row row = new Row();
        TrxManager tm = new TrxManager();

        int t0 = tm.begin();
        row.write(t0, "原始值");
        tm.commit(t0);

        int t1 = tm.begin();                     // T1 开始，做快照（RR）
        ReadView rrView = tm.snapshot();

        int t2 = tm.begin();                     // T2 开始
        row.write(t2, "T2 修改值");
        tm.commit(t2);

        System.out.println("【版本链】");
        for (Version v : row.versions) {
            System.out.printf("  trx=%d value=%s%n", v.trxId(), v.value());
        }

        System.out.println();
        System.out.println("【T1 在 RC 下（每条语句新快照）】");
        ReadView rcView = tm.snapshot();
        System.out.println("  读到：" + row.read(rcView) + "（能看到 T2 已提交）");

        System.out.println();
        System.out.println("【T1 在 RR 下（复用事务开始时的快照）】");
        System.out.println("  读到：" + row.read(rrView) + "（看不到 T2 的提交）");

        System.out.println();
        System.out.println("【可见性判断（RR 视图）】");
        System.out.println("  视图 upLimit=" + rrView.upLimit
                + " active=" + rrView.active + " minActive=" + rrView.minActive);
        for (Version v : row.versions) {
            System.out.printf("  trx=%d -> %s%n", v.trxId(),
                    rrView.visible(v.trxId()) ? "可见" : "不可见");
        }

        System.out.println();
        System.out.println("【结论】RR 的可重复读来自快照，不来自锁；长事务会让 undo 链越来越长");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 构造"两个事务交替写"的版本链，分别用 RC/RR 快照读取，验证结果差异。
 * 2. 实现"当前读"（读最新已提交版本），对比快照读，说明 `SELECT ... FOR UPDATE` 的语义。
 * 3. 思考题：为什么长事务会拖垮数据库？从"undo 链膨胀 + 快照过旧 + purge 受阻"三方面分析。
 * ============================================================
 */
