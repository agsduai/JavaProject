# 消息队列与异步（com.heima.ch28）

`package com.heima.ch28`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 28-01 | 为什么需要消息队列 | [QueueIntro.java](QueueIntro.java) |
| 28-02 | 两种消息模型：点对点与发布订阅 | [MessageModel.java](MessageModel.java) |
| 28-03 | 可靠投递：发送确认、持久化与重试 | [ReliableSend.java](ReliableSend.java) |
| 28-04 | 消费确认、重试与死信队列 | [AckAndRetry.java](AckAndRetry.java) |
| 28-05 | 消费幂等：重复消息怎么办 | [ConsumeIdempotent.java](ConsumeIdempotent.java) |
| 28-06 | 顺序消息与延迟消息 | [OrderAndDelayMessage.java](OrderAndDelayMessage.java) |
| 28-07 | 消息积压的排查与处理 | [BacklogDiagnosis.java](BacklogDiagnosis.java) |
| 28-08 | 延迟队列与定时任务的选型 | [DelayQueueDesign.java](DelayQueueDesign.java) |
| 28-09 | 事务消息与本地消息表 | [TransactionMessage.java](TransactionMessage.java) |
| 28-10 | 消息中间件选型与常见坑 | [MqSelection.java](MqSelection.java) |
| 28-11 | 综合案例：订单异步履约 | [OrderAsyncCase.java](OrderAsyncCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：订单异步履约链路

**场景**

下单成功后不再同步做加积分、发短信、通知仓库，改成发消息：生产者可靠投递、消费者幂等消费、失败重试与死信、延迟消息做超时关单，最后处理一次消息积压。

**任务**
1. 用内存队列模拟 Broker，实现生产者发送确认与失败重试（最多 3 次，退避递增），消息持久化到文件后重启不丢。
2. 实现消费者：手动确认、处理成功才 ack、失败重试超过 3 次进入死信队列，死信可查询与人工重放。
3. 实现幂等消费：每条消息带业务唯一键，消费前查已处理表，重复消息直接 ack 不重复执行。
4. 实现延迟消息：下单 30 分钟未支付自动关单，用延迟队列或定时扫描实现，并说明两者在精度与成本上的取舍。

**验收清单**
- [ ] 消费者故意抛异常时消息进入重试，3 次后进死信，业务数据只被成功处理一次。
- [ ] 同一消息重复投递 5 次，积分只加一次。
- [ ] 延迟关单在设定时间点附近触发，已支付的订单不会被误关。
- [ ] 模拟积压（生产者 1000 条、消费者 1 条/秒）后能用扩容消费者把积压消化掉，并给出耗时估算。

> 同一道题也写在 [OrderAsyncCase.java](OrderAsyncCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第28章-消息队列与异步.md](../../../../../../../黑马Java课程笔记/第28章-消息队列与异步.md) 的「本章综合练习」一节。
