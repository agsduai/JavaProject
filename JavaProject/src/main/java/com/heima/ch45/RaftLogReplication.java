/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-04
 * 课名：Raft 日志复制与安全
 * ------------------------------------------------------------
 * 知识点：
 *   - **一切操作都先变成日志**：客户端请求到达 Leader -> 追加为日志条目（term+index）-> 并行复制给 Follower -> **过半节点落盘** -> Leader 提交并应用 -> 通知客户端成功 -> Follower 跟随提交。**日志就是全集群的唯一操作顺序**，状态机按序回放日志得到一致状态。
 *   - **提交规则是安全的核心**：只有**当前任期**的条目被过半复制才能直接提交；提交当前条目会**间接提交**之前所有已复制条目（Raft 安全性论文的经典补丁——旧任期条目不能靠计数直接提交，否则已提交数据可能被覆盖）。
 *   - **日志匹配性质**：两节点日志在相同 index 上 term 相同，则该位置之前的所有日志完全相同（归纳保证）。AppendEntries 自带一致性检查：Follower 缺失/冲突时 Leader 回退 nextIndex 重发，**冲突的 Follower 条目被 Leader 覆盖**。
 *   - **新 Leader 的安全保证**：当选者日志不落后于任何过半节点（45-03 投票约束）=> **已提交的条目必然在新 Leader 日志里**——这就是"提交过的数据永不丢"的推导链。
 *   - **读请求的处理**：写走日志，读也要保证线性一致时不能只读本地——**ReadIndex**（leader 确认自己在当前 term 内仍是 leader：与多数派交换一次心跳）或 **Lease Read**（租约期内信任本地，45-08）。**"读不走日志所以没有一致性问题"是常见误解**。
 *   - **性能工程**：日志条目合并（batch）、流水线化（不等上一条确认就发下一条）、follower 读（ReadIndex 下放）、快照压缩日志（快照点之前的日志可丢弃）。**etcd 的实践：写 QPS 上千就很可观**——共识不便宜，能不用就不用（缓存、无状态计算不进共识）。
 *   - 易错点：commit 后才回复客户端（应过半落盘即可回复，apply 异步）；忽略"旧任期提交规则补丁"（自己实现时最易犯的安全 bug）；Follower 冲突日志直接追加不回退（日志分叉）；把大 value 塞进日志（建议日志只存命令引用，数据走外部存储）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RaftLogReplication {
    record Entry(int term, int index, String cmd, boolean committed) {
    }

    /** 三节点的日志状态 */
    static Map<String, List<Entry>> logs = new LinkedHashMap<>();

    static void replicate() {
        System.out.println("【一条写请求的复制与提交】");
        logs.put("leader", new ArrayList<>(List.of(new Entry(5, 1, "SET x=1", false))));
        logs.put("f1", new ArrayList<>());
        logs.put("f2", new ArrayList<>());
        Entry e = logs.get("leader").get(0);
        System.out.println("  客户端 -> leader：SET x=1 -> 追加日志 " + e.term() + "/" + e.index());
        logs.get("f1").add(new Entry(5, 1, "SET x=1", false));
        System.out.println("  f1 复制成功（1/2）");
        logs.get("f2").add(new Entry(5, 1, "SET x=1", false));
        System.out.println("  f2 复制成功（2/2 过半）-> 提交！");
        for (Map.Entry<String, List<Entry>> node : logs.entrySet()) {
            List<Entry> list = node.getValue();
            list.set(0, new Entry(e.term(), e.index(), e.cmd(), true));
            System.out.printf("  %s 日志：5/1 SET x=1 committed=%b%n", node.getKey(), list.get(0).committed());
        }
        System.out.println("  回复客户端成功；Follower 随后收到 commitIndex 同步");
        System.out.println();
    }

    static void commitRulePatch() {
        System.out.println("【提交规则补丁：为什么旧任期条目不能直接提交】");
        System.out.println("  场景：leader(旧term=4) 复制条目 idx=2 到 f1 后宕机");
        System.out.println("  f1 有 idx=2，但只有 1/3 -> 未提交；新 leader(term=5) 追加 idx=2 新值");
        System.out.println("  若旧条目可以按“已复制到 1 台”提交 -> 与新 leader 的 idx=2 冲突");
        List<String> rules = List.of(
                "规则1：只直接提交当前任期的条目（过半复制时）",
                "规则2：提交当前条目时，其之前的所有条目一并提交（间接提交）",
                "规则3：新 leader 必须先提交一条自己任期的 no-op，才能安全读");
        for (String r : rules) {
            System.out.println("  " + r);
        }
        System.out.println("  这是自实现 Raft 最常犯的安全错误（etcd/consul 论文都强调）");
        System.out.println();
    }

    static void conflictFix() {
        System.out.println("【日志冲突修复：一致性检查与回退】");
        List<Entry> leaderLog = List.of(
                new Entry(5, 1, "SET x=1", true),
                new Entry(5, 2, "SET x=2", false));
        List<Entry> staleLog = new ArrayList<>(List.of(
                new Entry(4, 1, "SET x=1", true),
                new Entry(3, 2, "SET y=9", false)));    // 分区期间旧 leader 写入的孤儿日志
        System.out.println("  leader 日志： " + leaderLog);
        System.out.println("  f2 旧日志：  " + staleLog + "（term=3 的 idx=2 是孤儿条目）");
        System.out.println("  AppendEntries(idx=2) 一致性检查失败（term 5≠3）");
        System.out.println("  leader 递减 nextIndex 重试 -> 发现 f2 的 idx=1 匹配");
        staleLog.remove(1);
        staleLog.add(new Entry(5, 2, "SET x=2", false));
        System.out.println("  覆盖 f2 的冲突条目 -> " + staleLog);
        System.out.println("  结论：以 leader 当前任期日志为准，分区侧孤儿数据被安全覆盖");
        System.out.println();
    }

    static void readPaths() {
        System.out.println("【读的三条路径与代价】");
        String[][] rows = {
                {"日志读", "读操作也写日志过半提交", "线性一致最强，写入翻倍，极少用"},
                {"ReadIndex", "leader 与多数派交换心跳确认权威后本地读", "线性一致，+1 RTT，etcd 默认串行读"},
                {"Lease Read", "租约期内信任本地时钟读", "0 RTT，依赖时钟有界偏移（45-08）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  “读不走日志就没问题”是误解：读也要确认 leader 权威");
    }

    public static void main(String[] args) {
        replicate();
        commitRulePatch();
        conflictFix();
        readPaths();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展 45-03 的模拟器实现日志复制：三节点 + leader 挂掉 + 重新选举，验证"已提交条目在新 leader 日志中保留、未提交孤儿条目被覆盖"。
 * 2. 实现 AppendEntries 一致性检查与 nextIndex 回退逻辑，用本章冲突场景跑通修复过程。
 * 3. 思考题：为什么读路径也要确认 leader 权威？构造一个"陈旧 leader 直接读本地返回旧值"的时序并说明后果。
 * ============================================================
 */
