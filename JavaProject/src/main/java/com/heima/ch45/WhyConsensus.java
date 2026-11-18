/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-01
 * 课名：为什么需要共识
 * ------------------------------------------------------------
 * 知识点：
 *   - **复制的根本分歧问题是"谁说了算"**：三副本集群收到写请求时，如果两个副本各自认为自己是主，同一个键就会被写两次不同的值。**共识（consensus）就是让多个节点对"一个值"达成唯一决定**——它是复制、选主、分布式锁、配置中心的共同底座。
 *   - **故障模型决定协议难度**：崩溃故障（节点停了就不再回来作恶）与拜占庭故障（节点可能撒谎）是两个世界。**数据中心内部只需要处理崩溃故障**——Raft/Paxos 的适用域；区块链处理拜占庭。选协议先问"我要防什么样的坏节点"。
 *   - **分布式系统的敌人是异步网络**：消息可以丢失、延迟、乱序，节点时钟不可信，你无法区分"节点挂了"和"网络很慢"。**共识协议的全部复杂性都来自这个不确定性**——单机程序没有这个问题。
 *   - **安全性与活性的取舍**：安全性（决不返回两个不同的值）是底线，任何网络分区下都不能违反；活性（最终能做出决定）在分区时可能暂停。**Raft 选择"分区时少数派停服，多数派继续"**——宁可不服务，不给错误答案。
 *   - **共识在 Java 系统里的身影**：ZooKeeper/etcd（配置与协调）、Redis Cluster/ES master 选举（41/38 章的选主）、Kafka Controller、K8s 的 etcd 底座——**你已经天天在用共识，只是被封装了**。本章把它拆开看。
 *   - **不要自己实现共识**："看起来简单的协议"（两阶段提交+超时切换）在分区下几乎必然产生脑裂。**正确姿势是用经过验证的库**（etcd/raft 库、ZooKeeper），自己写只存在于学习场景。
 *   - 易错点：把"过半数确认"理解成"过半机器在线就行"（是过半的**投票**，不是机器活着）；在少数派分区里继续写（脑裂）；用共享存储当共识（存储挂了就没有共识）；以为 Raft 保证"所有请求都成功"（少数派请求会失败，客户端要重试到新主）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WhyConsensus {
    /** 没有共识协议的"主备切换"：分区下的脑裂现场 */
    static void splitBrainDemo() {
        System.out.println("【朴素主备切换的脑裂：两台都认为自己是主】");
        record Node(String name, String role, String reason) {
        }
        // 网络分区：master 与 slave-1 一组，slave-2 单独一组
        List<Node> partition = List.of(
                new Node("master", "主", "心跳超时前仍是主"),
                new Node("slave-1", "主？", "探测到 master 失联，抢主"),
                new Node("slave-2", "主？", "同样探测到失联，也抢主"));
        for (Node n : partition) {
            System.out.printf("  %-8s %s（%s）%n", n.name(), n.role(), n.reason());
        }
        System.out.println("  结果：slave-1 与 slave-2 各自成为主，客户端写到两个不同的值");
        System.out.println("  根因：每个节点只依赖自己的观察（失联≠死亡），没有全局决定机制");
        System.out.println("  解法：用共识协议选主——只有获得过半投票的节点才能当主");
        System.out.println();
    }

    static void majorityRule() {
        System.out.println("【多数派投票：3 节点集群的分区表现】");
        record Scenario(String partition, String majoritySide, String outcome) {
        }
        List<Scenario> cases = List.of(
                new Scenario("A={n1,n2} | B={n3}", "A 侧 2/3", "A 侧继续选主并服务；B 侧 n3 拿不到过半，停服"),
                new Scenario("A={n1} | B={n2,n3}", "B 侧 2/3", "B 侧服务；n1 停服"),
                new Scenario("三边各 1 台", "无过半", "全部停服——宁可不服务，不给错误答案"));
        for (Scenario c : cases) {
            System.out.printf("  分区 %-16s 多数派=%s%n    %s%n", c.partition(), c.majoritySide(), c.outcome());
        }
        System.out.println("  安全性优先于可用性：这是共识协议与“脑裂备份”方案的本质区别");
        System.out.println();
    }

    static void consensusUses() {
        System.out.println("【Java 系统里的共识：你天天在用】");
        Map<String, String> uses = new LinkedHashMap<>();
        uses.put("ZooKeeper/etcd", "配置中心、选主协调（29 章）、K8s 元数据");
        uses.put("Redis Cluster", "slot 归属的主节点仲裁");
        uses.put("ES master 选举", "38-10 的 quorum 选举");
        uses.put("Kafka Controller", "分区 leader 的全局决定");
        uses.put("分布式锁", "39 章本地消息表的协调补充（45-08 细讲）");
        for (Map.Entry<String, String> e : uses.entrySet()) {
            System.out.printf("  %-14s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  共同点：都需要“多个节点对一个值达成唯一决定”");
        System.out.println();
    }

    static void failureModels() {
        System.out.println("【故障模型：决定协议选型】");
        String[][] rows = {
                {"崩溃故障", "节点宕机后不再恢复作恶", "Raft/Paxos/ZAB（数据中心标准）"},
                {"拜占庭故障", "节点可能返回任意错误结果", "PBFT 类（区块链、航天），开销大 1~2 个数量级"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-24s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  内网服务按崩溃故障设计即可；公网多方系统才考虑拜占庭");
    }

    public static void main(String[] args) {
        splitBrainDemo();
        majorityRule();
        consensusUses();
        failureModels();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用两台（或两进程）模拟"朴素主备切换"，制造网络分区（不互相 ping），复现双主写冲突，记录数据分叉证据。
 * 2. 把三节点集群的六种分区组合全部推演一遍（本章只列了三种），标注每侧的服务状态与原因。
 * 3. 思考题：为什么"共享存储当共识"不可靠？从存储自身的可用性与的一致性要求推导。
 * ============================================================
 */
