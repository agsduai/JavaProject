# 实时计算与流处理（com.heima.ch43）

`package com.heima.ch43`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 43-01 | 流与批的分界 | [StreamVsBatch.java](StreamVsBatch.java) |
| 43-02 | 时间语义：事件时间与处理时间 | [TimeSemantics.java](TimeSemantics.java) |
| 43-03 | 窗口：滚动、滑动与会话 | [WindowsDemo.java](WindowsDemo.java) |
| 43-04 | 水位线与乱序处理 | [WatermarkDemo.java](WatermarkDemo.java) |
| 43-05 | 状态管理与容错 | [StateCheckpoint.java](StateCheckpoint.java) |
| 43-06 | Exactly-Once 语义 | [ExactlyOnce.java](ExactlyOnce.java) |
| 43-07 | 流式 Join 与维度关联 | [StreamJoin.java](StreamJoin.java) |
| 43-08 | 反压与性能调优 | [Backpressure.java](Backpressure.java) |
| 43-09 | CDC 与实时数仓 | [CdcWarehouse.java](CdcWarehouse.java) |
| 43-10 | Flink 与 Spark Streaming 概念对比 | [FlinkVsSpark.java](FlinkVsSpark.java) |
| 43-11 | 综合案例：实时大屏（订单成交监控） | [RealtimeDashboard.java](RealtimeDashboard.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：实时大屏：订单成交监控

**场景**

做一个订单成交实时大屏：事件时间与水位线处理乱序、三种窗口统计、状态容错与恢复、Exactly-Once、流式 Join 补维度，最后处理反压。

**任务**
1. 用事件时间 + 水位线处理乱序数据：给定一批乱序事件，说明水位线推进规则与迟到数据的两种处理（丢弃或旁路输出）。
2. 实现三种窗口：滚动窗口算每分钟成交额、滑动窗口算 5 分钟移动平均、会话窗口按用户行为间隔切分，对比三者结果。
3. 实现状态与容错：定期 checkpoint 保存窗口状态，模拟进程重启后从 checkpoint 恢复，验证统计不重不漏。
4. 实现流式 Join：订单流 join 用户维度表，用本地缓存 + 过期刷新，说明维表变更后多久生效以及为什么不用全量 join。

**验收清单**
- [ ] 乱序数据在允许延迟内被正确计入窗口，超出延迟的数据进入旁路并可查。
- [ ] 三种窗口结果与离线批处理核对一致（允许水位线范围内的差异）。
- [ ] 重启恢复后累计值连续，不出现重复累加或数据丢失。
- [ ] 维表更新后新数据能取到新维度值，旧数据不被改写。

> 同一道题也写在 [RealtimeDashboard.java](RealtimeDashboard.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第43章-实时计算与流处理.md](../../../../../../../黑马Java课程笔记/第43章-实时计算与流处理.md) 的「本章综合练习」一节。
