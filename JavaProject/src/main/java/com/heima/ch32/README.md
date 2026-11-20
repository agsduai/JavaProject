# 并发编程进阶（com.heima.ch32）

`package com.heima.ch32`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 32-01 | 线程池核心参数与执行流程 | [ThreadPoolBasics.java](ThreadPoolBasics.java) |
| 32-02 | 四种拒绝策略与线程池监控 | [RejectPolicy.java](RejectPolicy.java) |
| 32-03 | CompletableFuture 异步编排 | [CompletableFutureDemo.java](CompletableFutureDemo.java) |
| 32-04 | AQS 与 ReentrantLock | [AqsReentrantLock.java](AqsReentrantLock.java) |
| 32-05 | 读写锁与 StampedLock | [ReadWriteLockDemo.java](ReadWriteLockDemo.java) |
| 32-06 | 并发容器：ConcurrentHashMap 与 CopyOnWrite | [ConcurrentContainers.java](ConcurrentContainers.java) |
| 32-07 | 并发工具类：CountDownLatch、CyclicBarrier、Semaphore、Phaser | [SyncTools.java](SyncTools.java) |
| 32-08 | 原子类、CAS 与 LongAdder | [AtomicCas.java](AtomicCas.java) |
| 32-09 | 线程协作：wait/notify、BlockingQueue 与 ThreadLocal | [ThreadCooperation.java](ThreadCooperation.java) |
| 32-10 | 并发问题诊断：死锁、线程转储与性能瓶颈 | [ConcurrencyDiagnose.java](ConcurrencyDiagnose.java) |
| 32-11 | 综合案例：高并发秒杀链路 | [SeckillCase.java](SeckillCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：高并发秒杀链路

**场景**

实现一条秒杀链路：线程池参数配置与拒绝策略、令牌桶限流、CAS 预扣库存、ConcurrentHashMap 存结果、CountDownLatch 汇总、LongAdder 计数，最后做一次死锁检测。

**任务**
1. 配置线程池（核心/最大/队列/拒绝策略），用自定义拒绝策略打印被拒任务数，压测后根据监控调整参数并说明依据。
2. 实现令牌桶限流：固定速率放行，超过速率的请求直接返回「排队中」，统计放行与被拒数量。
3. 用 AtomicInteger 或 CAS 实现库存预扣，1000 线程抢 100 件，保证不超卖不超扣，再用 LongAdder 统计成功数。
4. 用 CompletableFuture 并行调用三个依赖服务（用户、商品、风控）并设置超时，任一超时走降级；最后用 ThreadMXBean 检测一次人为构造的死锁。

**验收清单**
- [ ] 1000 并发下成功数恰好 100，库存最终为 0 且不为负。
- [ ] 限流生效，被拒请求数 + 放行请求数 = 总请求数。
- [ ] 线程池队列满时拒绝策略生效，控制台有明确拒绝日志，程序不抛 RejectedExecutionException 到调用方。
- [ ] 死锁检测能打印出互相等待的两个线程与持有的锁。

> 同一道题也写在 [SeckillCase.java](SeckillCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第32章-并发编程进阶.md](../../../../../../../黑马Java课程笔记/第32章-并发编程进阶.md) 的「本章综合练习」一节。
