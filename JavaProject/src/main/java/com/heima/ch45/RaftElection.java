/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-03
 * 课名：Raft 领导者选举
 * ------------------------------------------------------------
 * 知识点：
 *   - **Raft 把共识拆成三个可理解的问题**：**选主**（谁当 leader）、**日志复制**（leader 怎么让全员对操作顺序达成一致）、**安全**（新 leader 不能丢已提交的数据）。每个问题独立可测——这是 Raft 相比 Paxos 的最大工程价值。
 *   - **三种角色与任期（term）**：Follower / Candidate / Leader。逻辑时钟是**任期号**（单调递增），一轮选举 = 一个任期。**任期的单调性是全局顺序的基石**：旧任期的消息一律拒绝——它替代了不可信的物理时钟（45-07 的伏笔）。
 *   - **选举流程**：Follower 超时没听到 Leader 心跳 -> 自增任期、转 Candidate、给自己投票、向所有节点拉票 -> **获过半选票**则当选，立刻广播心跳确立权威 -> 未过半则等待随机超时后新一轮。**随机化选举超时**（150~300ms 抖动）避免两个节点同时发起导致选票分裂。
 *   - **投票的安全约束**：每个节点**一个任期只投一票**（先到先得）；**只投给日志不落后于自己的候选者**（比较最后一条日志的 term+index）——这两条保证"当选者拥有全部已提交日志"。
 *   - **脑裂不可能产生两个主**：过半票只有一个（3 节点需 2 票），两个 Candidate 都拿不到 2 票时谁也不当选，等下一轮。**45-01 的朴素方案缺的正是这个全局约束**。
 *   - **选举风暴与心跳**：Leader 每隔心跳间隔（如 100ms）广播心跳抑制选举；网络抖动导致的频繁换届要监控（换届率是集群健康信号，42 章方法论）。
 *   - 易错点：选举超时不随机（两节点同刻拉票死循环）；任期号用了物理时间（时钟回拨毁掉顺序，39-07/45-07 同款问题）；少数派分区里不断重试选举刷任期号（应静默等待恢复）；投完票还接受旧 term 的消息（安全漏洞）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// ==================== 示例代码 ====================

public class RaftElection {
    enum Role {
        FOLLOWER, CANDIDATE, LEADER
    }

    /** 单节点状态：任期、投票、角色 */
    static class RaftNode {
        final String name;
        Role role = Role.FOLLOWER;
        int term = 0;
        Integer votedFor = null;       // 本任期投给谁（null 未投）
        int logLastTerm = 0;           // 本地最后一条日志的任期（安全比较用）

        RaftNode(String name, int logLastTerm) {
            this.name = name;
            this.logLastTerm = logLastTerm;
        }

        boolean canVoteFor(int candidateLogTerm) {
            // 只投给“日志不落后于我”的候选者（简化：只比 term）
            return votedFor == null && candidateLogTerm >= logLastTerm;
        }
    }

    static void electionRound() {
        System.out.println("【一轮选举：3 节点，n2 先超时】");
        List<RaftNode> cluster = List.of(
                new RaftNode("n1", 3), new RaftNode("n2", 4), new RaftNode("n3", 4));
        RaftNode n2 = cluster.get(1);
        n2.role = Role.CANDIDATE;
        n2.term = 5;
        n2.votedFor = 2;                       // 自己投自己（节点编号示意）
        Map<Integer, Integer> votes = new LinkedHashMap<>();
        votes.put(2, 1);
        System.out.println("  n2 超时 -> term=5 转 Candidate，投自己");
        for (RaftNode n : cluster) {
            if (n != n2) {
                boolean grant = n.canVoteFor(n2.logLastTerm);
                if (grant) {
                    n.term = 5;
                    n.votedFor = 2;
                }
                votes.put(cluster.indexOf(n), grant ? 1 : 0);
                System.out.printf("  n2 -> %s 拉票(term=5, logTerm=%d) -> %s%n",
                        n.name, n2.logLastTerm, grant ? "同意（votedFor=2）" : "拒绝（日志落后/已投）");
            }
        }
        int total = votes.values().stream().mapToInt(Integer::intValue).sum();
        boolean won = total >= 2;
        System.out.printf("  得票 %d/2 -> %s%n", total, won ? "n2 当选 LEADER（term=5），广播心跳" : "选举失败，随机超时后重选");
        System.out.println();
    }

    static void splitVote() {
        System.out.println("【选票分裂与随机超时的化解】");
        System.out.println("  n1、n3 同刻发起选举（term=6），各拿 1 票（自己 + 互投失败）");
        System.out.println("  双方都未过半 -> 各自等待随机超时（150~300ms 抖动）");
        int n1Delay = 180, n3Delay = 260;
        System.out.printf("  n1 先超时（%dms）-> term=7 重新拉票 -> 获得 2 票当选%n", n1Delay);
        System.out.printf("  n3 晚 %dms 收到 n1 心跳 -> 退回 Follower%n", n3Delay - n1Delay);
        System.out.println("  随机化让“同时发起”大概率自然化解——分布式去重的经典手法");
        System.out.println();
    }

    static void partitionBehavior() {
        System.out.println("【分区行为：旧主与新主】");
        System.out.println("  集群 {n1(旧leader,term=4), n2, n3}，n1 与 n2/n3 分区");
        List<String> lines = List.of(
                "n2/n3 侧：选举出新 leader（term=5），继续接收写入",
                "n1 侧：仍在 term=4 自认为是 leader，接收客户端写",
                "客户端写 n1：leader 只复制给自己，拿不到过半 -> 不提交（安全）",
                "n1 收到 n2 心跳（term=5 > 4）-> 立刻退位为 Follower，未提交数据回滚",
                "关键：过半机制保证任何时刻最多一个有效 leader");
        for (String s : lines) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void healthSignals() {
        System.out.println("【选举健康度监控（42 章方法论）】");
        List<String> signals = List.of(
                "换届频率：稳定集群应接近 0（>1 次/小时需排查）",
                "选举耗时：正常 < 500ms，P99 过高说明网络抖动",
                "拒绝票原因分布：日志落后（数据问题）vs 已投他人（时序问题）",
                "任期号跳变：突增说明反复选举");
        for (String s : signals) {
            System.out.println("  - " + s);
        }
    }

    public static void main(String[] args) {
        electionRound();
        splitVote();
        partitionBehavior();
        healthSignals();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三节点 Raft 选举模拟器（线程 + 消息队列）：含随机超时、投票约束、心跳抑制，统计 1000 轮中的选举耗时分布。
 * 2. 构造"两个节点同时发起选举"的场景，验证随机超时如何化解，并统计平均化解轮数。
 * 3. 思考题：为什么"只投给日志不落后的候选者"是安全的？构造一个违反该约束导致已提交数据丢失的场景。
 * ============================================================
 */
