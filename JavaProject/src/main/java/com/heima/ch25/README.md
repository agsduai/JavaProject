# Redis与缓存（com.heima.ch25）

`package com.heima.ch25`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 25-01 | 为什么需要Redis | [WhyRedis.java](WhyRedis.java) |
| 25-02 | 五种基本数据类型 | [FiveDataTypes.java](FiveDataTypes.java) |
| 25-03 | key的设计与过期策略 | [KeyDesignAndExpire.java](KeyDesignAndExpire.java) |
| 25-04 | 持久化：RDB与AOF | [PersistenceDemo.java](PersistenceDemo.java) |
| 25-05 | 事务、Lua与管道 | [TransactionAndLua.java](TransactionAndLua.java) |
| 25-06 | 主从复制、哨兵与集群 | [ClusterTopology.java](ClusterTopology.java) |
| 25-07 | Spring Data Redis | [SpringDataRedisDemo.java](SpringDataRedisDemo.java) |
| 25-08 | 缓存的读写模式 | [CachePatterns.java](CachePatterns.java) |
| 25-09 | 缓存穿透、击穿、雪崩 | [CacheProblems.java](CacheProblems.java) |
| 25-10 | 分布式锁 | [DistributedLock.java](DistributedLock.java) |
| 25-11 | 综合案例：商品详情缓存 | [ItemCacheCase.java](ItemCacheCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：商品详情缓存体系

**场景**

给商品详情接口加缓存：Cache-Aside 读写、TTL 与随机抖动、穿透与击穿防护、分布式锁防重建，最后验证缓存与数据库的一致性。

**任务**
1. 实现 Cache-Aside：先查缓存，未命中查库回填并设置 TTL（加 0~300 秒随机抖动），更新时先更新库再删缓存。
2. 复现缓存穿透：查一个不存在的 id，用空值缓存或布隆过滤器（手写简版位数组）拦截，对比加防护前后的查库次数。
3. 复现缓存击穿：热点 key 过期瞬间 100 个线程并发查库，用互斥锁（SETNX + 过期时间）只放一个线程重建，其余等待。
4. 说明缓存雪崩的三种应对（TTL 打散、多级缓存、限流降级），并在代码里至少实现 TTL 打散。

**验收清单**
- [ ] 命中缓存时控制台不打印查库日志，未命中时打印一次并回填。
- [ ] 穿透防护生效后，查不存在的 id 不再打到数据库。
- [ ] 击穿场景下并发 100 次只有 1 次查库，其余命中缓存或等待后命中。
- [ ] 更新数据后缓存被删除，下一次查询拿到新值，不会长期脏读。

> 同一道题也写在 [ItemCacheCase.java](ItemCacheCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第25章-Redis与缓存.md](../../../../../../../黑马Java课程笔记/第25章-Redis与缓存.md) 的「本章综合练习」一节。
