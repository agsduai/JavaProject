# Kafka存储与消费原理（com.heima.ch62）

`package com.heima.ch62`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 62-01 | Kafka 全景：日志即消息 | [KafkaOverview.java](KafkaOverview.java) |
| 62-02 | 分区、副本与 Leader 选举 | [PartitionReplica.java](PartitionReplica.java) |
| 62-03 | ISR 与 HW/LEO：可靠性的账本 | [IsrAndWatermark.java](IsrAndWatermark.java) |
| 62-04 | 日志存储：段文件与索引 | [LogStorage.java](LogStorage.java) |
| 62-05 | 生产者：幂等与事务 | [ProducerSemantics.java](ProducerSemantics.java) |
| 62-06 | 消费者组与 Rebalance | [ConsumerGroup.java](ConsumerGroup.java) |
| 62-07 | Offset 管理与精确一次 | [OffsetSemantics.java](OffsetSemantics.java) |
| 62-08 | 消息保留、压缩与清理策略 | [RetentionPolicy.java](RetentionPolicy.java) |
| 62-09 | 性能模型：顺序写、零拷贝、批量 | [PerformanceModel.java](PerformanceModel.java) |
| 62-10 | 运维与积压排查 | [LagDiagnosis.java](LagDiagnosis.java) |
| 62-11 | 综合案例：订单事件流的 Kafka 落地 | [OrderEventStreamCase.java](OrderEventStreamCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：手写一个迷你消息队列

**场景**

用纯 JDK 实现迷你 Kafka：分区与顺序写、分段日志与索引、消费位点与重复消费、消费者组再均衡、ISR 与 acks 语义。目标是搞清「Kafka 为什么快、为什么可能重复」。

**任务**
1. 实现分区与顺序追加写：每个分区一个 append-only 文件，写入只追加不修改，说明顺序写为什么比随机写快。
2. 实现分段与稀疏索引：按大小滚动新段，用 offset 索引快速定位，验证查任意 offset 只需读一个段。
3. 实现消费位点：消费者提交 offset，模拟「处理成功但提交前崩溃」验证会重复消费，说明至少一次语义。
4. 实现 acks 语义：acks=0/1/all 三种配置下，模拟 broker 崩溃验证消息可能丢失的场景。

**验收清单**
- [ ] 同一分区内消息严格有序，跨分区不保证有序（并有说明）。
- [ ] 按 offset 查找能命中正确段与位置，不需要全文件扫描。
- [ ] 能复现重复消费并说明幂等消费的必要性。
- [ ] acks=all 时已确认的消息在单 broker 崩溃后不丢（或说明需要几个副本）。

> 同一道题也写在 [OrderEventStreamCase.java](OrderEventStreamCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第62章-Kafka存储与消费原理.md](../../../../../../../黑马Java课程笔记/第62章-Kafka存储与消费原理.md) 的「本章综合练习」一节。
