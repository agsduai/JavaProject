# 缓存架构与一致性设计（com.heima.ch81）

`package com.heima.ch81`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 81-01 | 缓存的收益与代价 | [CacheCostBenefit.java](CacheCostBenefit.java) |
| 81-02 | 缓存读写模式：Cache-Aside 与 Write-Behind | [CachePatterns.java](CachePatterns.java) |
| 81-03 | 穿透、击穿、雪崩 | [CacheProblems.java](CacheProblems.java) |
| 81-04 | 空值缓存与布隆过滤器 | [BloomFilter.java](BloomFilter.java) |
| 81-05 | 缓存一致性：双写与失效 | [CacheConsistency.java](CacheConsistency.java) |
| 81-06 | 延迟双删与 binlog 订阅 | [DelayedDoubleDelete.java](DelayedDoubleDelete.java) |
| 81-07 | 热点 key 与本地缓存 | [HotKeyLocalCache.java](HotKeyLocalCache.java) |
| 81-08 | 缓存预热与降级 | [CacheWarmupDegrade.java](CacheWarmupDegrade.java) |
| 81-09 | 多级缓存与 TTL 抖动 | [MultiLevelCache.java](MultiLevelCache.java) |
| 81-10 | 缓存监控与命中率治理 | [CacheMetrics.java](CacheMetrics.java) |
| 81-11 | 综合案例：商品详情页缓存改造 | [ProductDetailCache.java](ProductDetailCache.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：商品详情页的缓存改造

**场景**

商品详情页 QPS 2 万，DB 单机只能扛 3000，现在 70% 的请求在打库，P99 已经 800ms。要求：加一层缓存把 P99 压到 50ms 以内，同时保证「改价后 1 秒内可见」，还要防止大促零点整批 key 同时过期把库打穿。请给出完整的缓存方案与验证数据。

**任务**
1. 算收益：按当前 QPS 与命中率目标（90%/95%/98%）估算回源 QPS 与平均延迟，说明为什么 90% 不够。
2. 选模式：商品详情是读多写少、允许秒级旧值，选 Cache-Aside 并说明写路径为什么是「先写库再删缓存」。
3. 防三大故障：为空结果加 60 秒空值缓存、给热点 key 加互斥锁或逻辑过期、给 TTL 加 0~5 分钟随机抖动。
4. 做验证：模拟 2 万 QPS 跑一遍，输出命中率、回源 QPS、P50/P99，以及「改价后缓存何时失效」的时间线。
5. 补降级：缓存集群整体不可用时，用本地缓存 + 限流把流量挡在 DB 之外，给出降级开关的触发条件。

**验收清单**
- [ ] 命中率 ≥ 95%，回源 QPS 从 2 万降到 1000 以内，有计算过程不是拍脑袋。
- [ ] 写路径是「先写库、再删缓存」，并能说清并发下为什么不能「更新缓存」。
- [ ] 穿透/击穿/雪崩三种故障各有一条具体对策，且能在代码里找到对应实现。
- [ ] 改价后 1 秒内可见，有一致性时间线（写库 → 删缓存 → 下次读回填）佐证。
- [ ] 有降级开关与触发阈值，缓存全挂时 DB 不被打穿。

> 同一道题也写在 [ProductDetailCache.java](ProductDetailCache.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第81章-缓存架构与一致性设计.md](../../../../../../../黑马Java课程笔记/第81章-缓存架构与一致性设计.md) 的「本章综合练习」一节。

