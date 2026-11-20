# 分布式一致性与共识协议（com.heima.ch45）

`package com.heima.ch45`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 45-01 | 为什么需要共识 | [WhyConsensus.java](WhyConsensus.java) |
| 45-02 | CAP 与一致性谱系 | [CapSpectrum.java](CapSpectrum.java) |
| 45-03 | Raft 领导者选举 | [RaftElection.java](RaftElection.java) |
| 45-04 | Raft 日志复制与安全 | [RaftLogReplication.java](RaftLogReplication.java) |
| 45-05 | ZooKeeper 与 ZAB | [ZookeeperConcepts.java](ZookeeperConcepts.java) |
| 45-06 | etcd 与线性一致读 | [EtcdLinearRead.java](EtcdLinearRead.java) |
| 45-07 | 分布式时钟与顺序 | [DistributedClocks.java](DistributedClocks.java) |
| 45-08 | 租约与分布式锁 | [LeaseFencing.java](LeaseFencing.java) |
| 45-09 | Quorum 读写与仲裁 | [QuorumReplication.java](QuorumReplication.java) |
| 45-10 | 共识的工程代价 | [ConsensusCost.java](ConsensusCost.java) |
| 45-11 | 综合案例：配置中心的高可用设计 | [ConfigCenterCase.java](ConfigCenterCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：配置中心的高可用设计

**场景**

设计一个基于共识的配置中心：Raft 选主、日志复制、线性一致读、租约与分布式锁、Quorum 读写。要能回答「3 节点挂 1 个还能写吗」「网络分区时谁说了算」。

**任务**
1. 实现 Raft 选主：任期、投票、心跳超时随机化，模拟 3 节点中 leader 宕机后重新选主的过程并打印日志。
2. 实现日志复制：leader 接收写请求 → 追加本地日志 → 复制到 follower → 多数确认后提交 → 应用到状态机。
3. 实现线性一致读：读请求也走 leader 并确认自己仍是 leader（用租约或读索引），说明为什么 follower 直接读可能读到旧值。
4. 分析两种故障场景：3 节点挂 1 个（能否写）、5 节点分成 3/2 两区（哪个区能写），给出可用性结论。

**验收清单**
- [ ] 选主过程日志完整，同一任期不会选出两个 leader。
- [ ] 多数节点确认前客户端拿不到成功响应，提交后所有节点最终一致。
- [ ] 分区场景下的结论符合 Quorum 规则，能解释「少数派为什么必须拒绝写入」。
- [ ] 网络恢复后日志自动追平，状态机数据一致。

> 同一道题也写在 [ConfigCenterCase.java](ConfigCenterCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第45章-分布式一致性与共识协议.md](../../../../../../../黑马Java课程笔记/第45章-分布式一致性与共识协议.md) 的「本章综合练习」一节。
