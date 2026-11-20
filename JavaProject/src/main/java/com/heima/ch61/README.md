# 分布式任务调度与工作流引擎（com.heima.ch61）

`package com.heima.ch61`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 61-01 | 调度系统全景：从 cron 到调度平台 | [SchedulerOverview.java](SchedulerOverview.java) |
| 61-02 | 调度中心架构：注册、发现与故障转移 | [SchedulerCenter.java](SchedulerCenter.java) |
| 61-03 | 任务分片与并行执行 | [ShardingJob.java](ShardingJob.java) |
| 61-04 | 任务依赖与 DAG 编排 | [DagOrchestration.java](DagOrchestration.java) |
| 61-05 | 调度幂等与重复执行防护 | [IdempotentJob.java](IdempotentJob.java) |
| 61-06 | 失败重试与补偿 | [RetryAndCompensation.java](RetryAndCompensation.java) |
| 61-07 | 工作流引擎：状态机与审批流 | [WorkflowEngine.java](WorkflowEngine.java) |
| 61-08 | 长流程的持久化与恢复 | [WorkflowPersistence.java](WorkflowPersistence.java) |
| 61-09 | 调度可观测与积压治理 | [SchedulerObservability.java](SchedulerObservability.java) |
| 61-10 | 调度与消息队列、批处理的边界 | [MechanismBoundary.java](MechanismBoundary.java) |
| 61-11 | 综合案例：订单超时关闭调度平台 | [CloseOrderJob.java](CloseOrderJob.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：订单超时关闭的调度方案

**场景**

实现「下单 30 分钟未支付自动关闭」：定时扫描、延迟队列、时间轮三种方案各写一版，处理重复触发与漏触发、分片调度、任务幂等、失败重试、可观测。最后给出选型结论。

**任务**
1. 用定时扫描实现：每 10 秒扫一次超时订单表，分批处理（每批 500），说明为什么不能一次扫全表。
2. 用延迟队列实现：订单创建时投递延迟消息，到点消费关闭，处理消息丢失（补偿扫描）与重复消费（幂等）。
3. 用时间轮实现：按秒级槽位挂载任务，说明时间轮的复杂度优势与内存占用代价。
4. 实现分片与幂等：多实例按订单号取模分片避免重复扫描，关闭动作加状态机校验（已支付的不关闭）。

**验收清单**
- [ ] 三种方案都能在 30 分钟 ±1 分钟内关闭订单，不漏单。
- [ ] 重复触发同一订单只产生一次关闭动作，日志可见幂等拦截。
- [ ] 多实例部署时任务不重复执行，实例宕机后任务被其他实例接管。
- [ ] 给出选型结论并说明在什么数据量下应该切换方案。

> 同一道题也写在 [CloseOrderJob.java](CloseOrderJob.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第61章-分布式任务调度与工作流引擎.md](../../../../../../../黑马Java课程笔记/第61章-分布式任务调度与工作流引擎.md) 的「本章综合练习」一节。
