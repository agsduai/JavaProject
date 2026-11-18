/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-05
 * 课名：ZooKeeper 与 ZAB
 * ------------------------------------------------------------
 * 知识点：
 *   - **ZooKeeper 的定位是"协调服务"而不是"数据库"**：它提供分层命名空间（znode 树）、 watches（变更通知）、临时节点（会话绑定的存活性信号）、顺序节点（全局递增编号）——**用这四样积木拼出选主、分布式锁、配置订阅、队列**。存数据是次要能力（单 znode 默认 1MB 上限）。
 *   - **ZAB 协议 = Raft 的近亲**：同样是过半投票选主 + 日志复制（ZAB 叫"崩溃恢复与消息广播"两阶段）。理解了 Raft（45-03/04）就理解了 ZK 的可用性边界：**3~5 节点、写走 leader、过半确认**。
 *   - **临时节点是"存活性"的原语**：客户端 session 断开（心跳超时）时其临时节点自动删除——**选主的经典实现**：所有候选者创建 `/master` 临时节点，创建成功者为主，其余 watch 该节点，节点消失即触发重新竞选。**它比心跳探测可靠**：session 失效由 ZK 集群裁决（共识），不是客户端自己的观察（45-01 脑裂的反例）。
 *   - **顺序节点是"公平锁与队列"的原语**：`/lock/lock-0000012` 全局递增，拿最小编号者持锁、其余 watch 前一个编号——避免惊群，保证公平。**这是分布式锁的标准姿势**（45-08 的 Redis 方案对比）。
 *   - **Watch 是一次性触发**：收到通知后要重新注册（或用 Curator 缓存封装），**watch 通知不保证送达内容**，只保证"有变化"——读取最新值必须重查。
 *   - **ZK 的运维红线**：写吞吐低（全集群过半确认，每秒几千写已是极限）；session 超时调太短（GC 停顿就掉 session -> 临时节点消失 -> 选主抖动，29 章 Nacos 为什么最终不用 ZK 做注册中心的原因之一）；**不要把 ZK 当消息队列或大数据存储用**。
 *   - 易错点：用非临时节点做选主（进程挂了节点还在，双主）；watch 只注册一次（丢后续变更）；把大数据量塞 znode（写放大拖垮集群）；session 超时与业务 GC 停顿不匹配（频繁重连风暴）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ZookeeperConcepts {
    record Znode(String path, String data, boolean ephemeral, int seq) {
    }

    static void leaderElection() {
        System.out.println("【选主：临时节点 + watch】");
        List<Znode> znodes = List.of(
                new Znode("/election/n-0000011", "hostA", true, 11),
                new Znode("/election/n-0000012", "hostB", true, 12));
        Znode master = znodes.stream()
                .min((a, b) -> Integer.compare(a.seq(), b.seq())).orElse(null);
        System.out.println("  三个候选者都创建顺序临时节点，编号最小者为主：");
        System.out.printf("  主 = %s（%s）%n", master.path(), master.data());
        System.out.println("  hostB watch /election/n-0000011");
        System.out.println("  hostA session 断开 -> 临时节点自动删除 -> hostB 收到通知 ->");
        System.out.println("       重查编号 -> 自己最小 -> 当选");
        System.out.println("  存活性由 ZK 集群（共识）裁决，而非节点自我观察 —— 无脑裂");
        System.out.println();
    }

    static void fairLock() {
        System.out.println("【公平锁：顺序节点编号排队】");
        List<String> holders = List.of("/lock/lock-0000012(A)", "/lock/lock-0000013(B)",
                "/lock/lock-0000014(C)");
        String acquired = holders.get(0);
        System.out.println("  排队状态：" + holders);
        System.out.printf("  A（编号最小）持锁；B watch 0012，C watch 0013%n");
        System.out.println("  A 释放（删节点）-> B 收到通知 -> 自己成为最小 -> 持锁");
        System.out.println("  对比单 watch /lock：锁释放时所有等待者同时醒来抢（惊群）");
        System.out.println("  对照 45-08 Redis 锁：ZK 锁自带公平性与存活性，代价是每次操作走共识");
        System.out.println();
    }

    static void watchSemantics() {
        System.out.println("【Watch：一次性触发，必须重注册】");
        List<String> flow = List.of(
                "1) hostB getData(/config, watch=true) 得到值 v5",
                "2) /config 变为 v6 -> hostB 收到通知（不含新值！）",
                "3) hostB 重新 getData + 重新注册 watch -> 拿到 v6",
                "4) 若在“收到通知”与“重查”之间又变了 v7 -> 下次通知保证不丢变化");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println("  生产实践：用 Curator NodeCache/TreeCache 封装，避免裸 watch 的时序坑");
        System.out.println();
    }

    static void opsRedlines() {
        System.out.println("【ZK 运维红线】");
        List<String> rules = List.of(
                "znode 数据 ≤ 1MB（默认），大配置拆分或走配置中心",
                "session 超时 > 最大 GC 停顿（否则掉线风暴 -> 选主抖动）",
                "写 QPS 有限（过半确认）：高频写场景用本地缓存 + watch 推送",
                "不把 ZK 当注册中心全量心跳（29 章：注册中心对写吞吐的要求超出 ZK 舒适区）",
                "集群 3 或 5 台（quorum），生产禁止单机");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        leaderElection();
        fairLock();
        watchSemantics();
        opsRedlines();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用临时节点 + 顺序节点实现一个公平分布式锁模拟器（内存版），验证"编号最小者持锁、释放后依次传递"，并构造惊群对照。
 * 2. 解释 GC 停顿 8 秒、session 超时 5 秒的集群会发生什么连锁反应，给出参数修正方案。
 * 3. 思考题：为什么 29 章的注册中心最终用"心跳 + 内存表"而不是 ZK？从写吞吐与会话模型对比注册中心的负载形态。
 * ============================================================
 */
