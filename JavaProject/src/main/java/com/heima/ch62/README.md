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
