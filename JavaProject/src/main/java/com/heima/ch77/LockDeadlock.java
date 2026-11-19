/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-05
 * 课名：锁与死锁
 * ------------------------------------------------------------
 * 知识点：
 *   - **InnoDB 的锁粒度**：**行锁**（Record Lock，锁索引记录）、**间隙锁**（Gap Lock，锁索引区间，防幻读）、**临键锁**（Next-Key = 行锁 + 间隙锁）、**意向锁**（表级，表示"我要锁某些行"）——**锁的是索引，不是行本身**（没走索引会锁全表）。
 *   - **共享锁 vs 排他锁**：**S 锁可共存、X 锁互斥**；**`SELECT ... FOR UPDATE` 加 X 锁**、**`LOCK IN SHARE MODE` 加 S 锁**——**普通 SELECT 走 MVCC 不加锁**（77-04）。
 *   - **死锁的四个必要条件**：**互斥、占有且等待、不可抢占、循环等待**——**数据库的死锁检测就是"等待图找环"**，**发现环后回滚代价小的事务（通常 undo 量小）**。
 *   - **避免死锁的工程手段**：**固定加锁顺序**（按主键排序更新）、**缩短事务**（锁持有时间）、**降低隔离级别**（RC 减少间隙锁）、**唯一索引让冲突变等待**——**没有银弹，只能减少概率**。
 *   - 与 32 章的关系：32 章讲 Java 的 `synchronized`/死锁检测，**数据库锁是"跨进程、跨机器"的同一问题**——**等待图、超时、回滚**是通用解法。
 *   - 易错点：不按顺序更新多行（死锁高发）；长事务持有行锁（阻塞全表更新）；`SELECT ... FOR UPDATE` 不带索引（锁全表）；把死锁当"偶发故障"不治理。
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

public class LockDeadlock {
    /** 简化锁管理器：资源 -> 持有事务；等待图：事务 -> 它等待的事务 */
    static class LockManager {
        final Map<String, String> holder = new LinkedHashMap<>();
        final Map<String, Set<String>> waits = new LinkedHashMap<>();
        final List<String> events = new ArrayList<>();

        boolean tryLock(String trx, String resource) {
            String owner = holder.get(resource);
            if (owner == null || owner.equals(trx)) {
                holder.put(resource, trx);
                waits.computeIfAbsent(trx, k -> new LinkedHashSet<>());
                events.add(trx + " 获得 " + resource);
                return true;
            }
            waits.computeIfAbsent(trx, k -> new LinkedHashSet<>()).add(owner);
            events.add(trx + " 等待 " + resource + "（持有者 " + owner + "）");
            return false;
        }

        void releaseAll(String trx) {
            holder.entrySet().removeIf(e -> e.getValue().equals(trx));
            waits.remove(trx);
            for (Set<String> set : waits.values()) {
                set.remove(trx);
            }
            events.add(trx + " 释放全部锁");
        }

        /** 等待图找环：DFS 三色标记 */
        String findCycle() {
            Map<String, Integer> color = new LinkedHashMap<>();
            for (String t : waits.keySet()) {
                color.put(t, 0);
            }
            for (String t : waits.keySet()) {
                String cycle = dfs(t, color, new ArrayList<>());
                if (cycle != null) {
                    return cycle;
                }
            }
            return null;
        }

        String dfs(String node, Map<String, Integer> color, List<String> path) {
            Integer c = color.get(node);
            if (c != null && c == 1) {
                return String.join(" -> ", path) + " -> " + node;
            }
            if (c != null && c == 2) {
                return null;
            }
            color.put(node, 1);
            path.add(node);
            for (String next : waits.getOrDefault(node, Set.of())) {
                String found = dfs(next, color, path);
                if (found != null) {
                    return found;
                }
            }
            path.remove(path.size() - 1);
            color.put(node, 2);
            return null;
        }
    }

    public static void main(String[] args) {
        LockManager lm = new LockManager();

        System.out.println("【场景：T1 锁 A 再等 B，T2 锁 B 再等 A】");
        lm.tryLock("T1", "row-A");
        lm.tryLock("T2", "row-B");
        lm.tryLock("T1", "row-B");
        lm.tryLock("T2", "row-A");

        lm.events.forEach(e -> System.out.println("  " + e));

        System.out.println();
        String cycle = lm.findCycle();
        System.out.println("  死锁检测：" + (cycle == null ? "无环" : "发现环 " + cycle));

        System.out.println();
        System.out.println("【选 victim 回滚（这里回滚事务号较大的 T2）】");
        lm.releaseAll("T2");
        boolean ok = lm.tryLock("T1", "row-B");
        System.out.println("  T2 回滚后，T1 重新申请 row-B：" + (ok ? "成功" : "仍失败"));
        System.out.println("  当前持有：" + lm.holder);

        System.out.println();
        System.out.println("【避免死锁的四条工程规则】");
        String[][] rules = {
                {"固定顺序", "多行更新按主键升序", "消除循环等待"},
                {"短事务", "锁内不做 RPC/大计算", "减少持有时间"},
                {"走索引", "FOR UPDATE 必须命中索引", "避免锁全表"},
                {"降隔离", "RC 减少间隙锁", "降低冲突面"},
        };
        for (String[] r : rules) {
            System.out.printf("  %-8s %-26s %s%n", r[0], r[1], r[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 构造三个事务的等待环，用等待图检测并输出环路径，验证 victim 回滚后恢复。
 * 2. 模拟"不按顺序更新"与"按主键排序更新"两种写法，对比死锁概率。
 * 3. 思考题：为什么"没走索引的 FOR UPDATE 会锁全表"？从"行锁锁的是索引记录"分析。
 * ============================================================
 */
