# 事件驱动架构与事务消息（com.heima.ch78）

`package com.heima.ch78`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 78-01 | 从 RPC 到事件驱动 | [EventDrivenOverview.java](EventDrivenOverview.java) |
| 78-02 | 事务消息与本地消息表 | [TransactionalOutbox.java](TransactionalOutbox.java) |
| 78-03 | 幂等消费与去重 | [IdempotentConsumer.java](IdempotentConsumer.java) |
| 78-04 | 顺序消息与分区键 | [OrderedMessaging.java](OrderedMessaging.java) |
| 78-05 | 事件溯源与 CQRS | [EventSourcingCqrs.java](EventSourcingCqrs.java) |
| 78-06 | Saga 与补偿事务 | [SagaOrchestration.java](SagaOrchestration.java) |
| 78-07 | 事件 Schema 演进与契约 | [EventSchemaEvolution.java](EventSchemaEvolution.java) |
| 78-08 | 死信队列与积压治理 | [DlqBacklog.java](DlqBacklog.java) |
| 78-09 | 事件驱动的可观测性 | [EventObservability.java](EventObservability.java) |
| 78-10 | 事件治理与事件目录 | [EventGovernance.java](EventGovernance.java) |
| 78-11 | 综合案例：订单履约事件流 | [OrderFulfillmentCase.java](OrderFulfillmentCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：订单事件的可靠投递

**场景**

用事件驱动重构订单履约：本地消息表/事务消息保证「业务成功则事件必发」、消费幂等、顺序消费、死信与重试、最终一致对账、事件溯源。要能回答「消息丢了怎么办」。

**任务**
1. 实现本地消息表：业务写库与写消息表在同一本地事务，再由投递器异步发送，验证不出现「订单成功但事件没发」。
2. 实现消费幂等：用业务唯一键或去重表，重复消费 3 次结果不变。
3. 实现顺序消费：同一订单的事件按序处理（按订单号路由到同一分区/队列），说明乱序会导致什么业务错误。
4. 实现死信与对账：重试 N 次失败进入死信队列并告警，定时对账补偿不一致的订单。

**验收清单**
- [ ] 模拟投递器在发送前崩溃，重启后事件仍被补发，业务与事件最终一致。
- [ ] 重复消费不产生重复扣减/重复发货。
- [ ] 同一订单的事件处理顺序正确，状态不会倒退。
- [ ] 死信可查可重放，对账能发现并修复不一致数据。

> 同一道题也写在 [OrderFulfillmentCase.java](OrderFulfillmentCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第78章-事件驱动架构与事务消息.md](../../../../../../../黑马Java课程笔记/第78章-事件驱动架构与事务消息.md) 的「本章综合练习」一节。
