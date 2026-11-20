# 数据库内核与SQL调优（com.heima.ch77）

`package com.heima.ch77`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 77-01 | 为什么 Java 工程师要懂数据库内核 | [DbKernelOverview.java](DbKernelOverview.java) |
| 77-02 | B+ 树索引与页分裂 | [BPlusTreeIndex.java](BPlusTreeIndex.java) |
| 77-03 | 缓冲池与 LRU | [BufferPoolLru.java](BufferPoolLru.java) |
| 77-04 | 事务与 MVCC | [MvccReadView.java](MvccReadView.java) |
| 77-05 | 锁与死锁 | [LockDeadlock.java](LockDeadlock.java) |
| 77-06 | redo log 与崩溃恢复 | [WALRecovery.java](WALRecovery.java) |
| 77-07 | 执行计划与优化器 | [QueryOptimizer.java](QueryOptimizer.java) |
| 77-08 | 慢 SQL 诊断与改写 | [SlowSqlRewrite.java](SlowSqlRewrite.java) |
| 77-09 | 索引设计方法论 | [IndexDesign.java](IndexDesign.java) |
| 77-10 | 分库分表后的 SQL 约束 | [ShardedSql.java](ShardedSql.java) |
| 77-11 | 综合案例：慢查询治理平台 | [SlowQueryPlatform.java](SlowQueryPlatform.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：一条慢 SQL 的完整排查

**场景**

给一条跑了 8 秒的 SQL 做全链路调优：执行计划解读、索引失效原因、回表与覆盖索引、join 顺序与驱动表、深分页优化、锁等待排查、参数与统计信息。要给出「为什么慢、怎么改、改完多快」。

**任务**
1. 读执行计划：指出 type、key、rows、Extra 各字段含义，判断是全表扫描、索引扫描还是回表过多。
2. 定位索引失效：列出常见失效场景（函数包裹列、隐式类型转换、前导通配 like、or 混用），逐条验证。
3. 优化深分页：limit 1000000, 20 改为基于游标（where id > lastId limit 20）或延迟关联，对比耗时。
4. 排查锁等待：用当前锁信息找出阻塞源，说明长事务如何导致锁堆积，给出拆分事务的方案。

**验收清单**
- [ ] 能说清这条 SQL 慢在哪一步（扫描行数、回表次数、排序、临时表）。
- [ ] 优化后执行计划命中预期索引，扫描行数下降数量级。
- [ ] 深分页优化后耗时从秒级降到毫秒级，结果集一致。
- [ ] 锁等待问题定位到具体事务与 SQL，有可执行的拆分方案。

> 同一道题也写在 [SlowQueryPlatform.java](SlowQueryPlatform.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第77章-数据库内核与SQL调优.md](../../../../../../../黑马Java课程笔记/第77章-数据库内核与SQL调优.md) 的「本章综合练习」一节。
