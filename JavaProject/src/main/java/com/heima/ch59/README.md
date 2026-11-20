# 大数据离线计算与Spark原理（com.heima.ch59）

`package com.heima.ch59`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 59-01 | 离线计算的定位与 Spark 全景 | [SparkOverview.java](SparkOverview.java) |
| 59-02 | RDD：弹性分布式数据集 | [RddDemo.java](RddDemo.java) |
| 59-03 | 惰性求值与 DAG 调度 | [DagDemo.java](DagDemo.java) |
| 59-04 | 分区与并行度 | [PartitionDemo.java](PartitionDemo.java) |
| 59-05 | Shuffle 原理与代价 | [ShuffleCost.java](ShuffleCost.java) |
| 59-06 | 宽窄依赖与容错 | [LineageDemo.java](LineageDemo.java) |
| 59-07 | Spark SQL 与 Catalyst 优化器 | [CatalystDemo.java](CatalystDemo.java) |
| 59-08 | 数据倾斜与作业调优 | [SkewDemo.java](SkewDemo.java) |
| 59-09 | 资源模型与推测执行 | [ResourceModel.java](ResourceModel.java) |
| 59-10 | Spark 与湖仓集成 | [SparkLakeDemo.java](SparkLakeDemo.java) |
| 59-11 | 综合案例：订单数据离线加工链 | [OfflinePipelineCase.java](OfflinePipelineCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：订单数据离线加工链

**场景**

用 Spark 思想处理订单离线数据：RDD 分区与并行度、惰性求值与 DAG、Shuffle 代价、宽窄依赖与容错、SQL 优化、数据倾斜调优。用纯 JDK 模拟核心机制。

**任务**
1. 实现一个迷你 RDD：分区、map/filter（窄依赖）、groupByKey（宽依赖触发 Shuffle），打印每个 stage 的划分过程。
2. 对比 map+filter 链式与 groupByKey 的耗时，说明 Shuffle 为什么是性能瓶颈（磁盘 IO + 网络传输 + 排序）。
3. 处理数据倾斜：某 key 数据量占 80%，用加盐打散 + 二次聚合解决，对比优化前后的耗时。
4. 设计容错：某个分区计算失败时如何用血缘（lineage）重算，说明 checkpoint 与 lineage 的取舍。

**验收清单**
- [ ] 能画出 DAG 并指出 Shuffle 边界，stage 划分正确。
- [ ] 加盐方案后各分区数据量趋于均衡（偏差小于 20%），总耗时明显下降。
- [ ] 分区失败后能通过血缘重算恢复，结果与一次成功一致。
- [ ] 惰性求值可验证：没有 action 时 transformation 不执行（打印日志证明）。

> 同一道题也写在 [OfflinePipelineCase.java](OfflinePipelineCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第59章-大数据离线计算与Spark原理.md](../../../../../../../黑马Java课程笔记/第59章-大数据离线计算与Spark原理.md) 的「本章综合练习」一节。
