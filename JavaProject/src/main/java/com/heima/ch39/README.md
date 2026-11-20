# 海量数据与分库分表（com.heima.ch39）

`package com.heima.ch39`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 39-01 | 数据量大了会发生什么 | [BigTableProblems.java](BigTableProblems.java) |
| 39-02 | 索引优化与执行计划 | [SlowQueryOptimize.java](SlowQueryOptimize.java) |
| 39-03 | 深分页优化 | [DeepPaginationFix.java](DeepPaginationFix.java) |
| 39-04 | 读写分离与主从延迟 | [ReadWriteSplit.java](ReadWriteSplit.java) |
| 39-05 | 分库分表的拆分策略 | [ShardingStrategy.java](ShardingStrategy.java) |
| 39-06 | 分片路由与扩容 | [ShardRouting.java](ShardRouting.java) |
| 39-07 | 分布式 ID：雪花算法 | [DistributedId.java](DistributedId.java) |
| 39-08 | 跨分片查询与聚合 | [CrossShardQuery.java](CrossShardQuery.java) |
| 39-09 | 跨库事务与一致性 | [CrossDbConsistency.java](CrossDbConsistency.java) |
| 39-10 | 冷热分离与数据归档 | [HotColdArchive.java](HotColdArchive.java) |
| 39-11 | 综合案例：订单系统从单表到分库分表 | [OrderShardingCase.java](OrderShardingCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：订单系统从单表到分库分表

**场景**

订单表从 100 万涨到 10 亿：先做索引与深分页优化，再做读写分离，最后按用户 id 分片。要解决分片路由、分布式 ID、跨分片查询与聚合、跨库事务、冷热分离。

**任务**
1. 用 EXPLAIN 分析一条慢查询，补索引或改写 SQL，把全表扫描变成索引扫描，给出前后耗时对比。
2. 优化深分页：limit 1000000, 20 改成基于上次最大 id 的游标查询，说明为什么 offset 越大越慢。
3. 实现分片路由：按 userId 取模分 4 片，写一个路由方法把 SQL 定位到具体分片，并说明为什么不用订单 id 做分片键。
4. 实现雪花算法生成全局唯一 id，再用「先查用户维度分片 + 异步写宽表」解决按商家维度查询的问题，说明跨分片聚合的两种方案。

**验收清单**
- [ ] 慢查询优化后 type 从 ALL 变为 ref 或 range，扫描行数下降一个量级以上。
- [ ] 游标分页结果与 offset 分页在相同数据下一致，但耗时基本恒定。
- [ ] 分片路由在 4 个分片上数据分布均匀（偏差不超过 10%）。
- [ ] 雪花 id 在多线程下无重复、趋势递增，能说明时钟回拨的处理方式。

> 同一道题也写在 [OrderShardingCase.java](OrderShardingCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第39章-海量数据与分库分表.md](../../../../../../../黑马Java课程笔记/第39章-海量数据与分库分表.md) 的「本章综合练习」一节。
