# 性能压测与Profiling（com.heima.ch80）

`package com.heima.ch80`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 80-01 | 性能工程方法论 | [PerformanceMethodology.java](PerformanceMethodology.java) |
| 80-02 | 基准测试与 JMH 思想 | [BenchmarkHarness.java](BenchmarkHarness.java) |
| 80-03 | 延迟分布与分位数 | [LatencyDistribution.java](LatencyDistribution.java) |
| 80-04 | 协调遗漏与开放式模型 | [CoordinatedOmission.java](CoordinatedOmission.java) |
| 80-05 | CPU Profiling 与火焰图 | [FlameGraph.java](FlameGraph.java) |
| 80-06 | 内存分析与 GC 日志 | [GcAnalysis.java](GcAnalysis.java) |
| 80-07 | 锁竞争与线程分析 | [LockProfiling.java](LockProfiling.java) |
| 80-08 | 全链路压测 | [ShadowTraffic.java](ShadowTraffic.java) |
| 80-09 | 瓶颈定位与扩容决策 | [CapacityModel.java](CapacityModel.java) |
| 80-10 | 性能回归与门禁 | [PerfRegressionGate.java](PerfRegressionGate.java) |
| 80-11 | 综合案例：一次双十一压测 | [Double11LoadTest.java](Double11LoadTest.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：双十一压测与调优

**场景**

给订单系统做压测与调优：压测模型设计、阶梯加压找拐点、瓶颈定位（CPU/IO/锁/GC/连接池）、JVM 调优、限流保护、容量结论。最后给出「能扛多少 QPS」的量化答案。

**任务**
1. 设计压测模型：按真实流量比例构造读写比（如 9:1）、参数化数据、思考时间，说明为什么不能只压一个接口。
2. 阶梯加压：从 100 QPS 起每 5 分钟加 100，记录吞吐与 P99，找出吞吐不再上升而延迟陡增的拐点。
3. 定位瓶颈：用线程栈、GC 日志、慢查询、连接池等待时间判断是 CPU 打满、GC 停顿、锁竞争还是下游超时。
4. 做调优对比：调整线程池/连接池/GC 参数/缓存命中率，每次只改一项，记录前后数据，形成调优记录表。

**验收清单**
- [ ] 压测模型贴近真实流量，参数不集中在少数热点 key。
- [ ] 能找到明确的性能拐点并给出对应 QPS 数值。
- [ ] 瓶颈定位有证据（火焰图/GC 日志/慢 SQL），不是猜的。
- [ ] 调优记录表显示每项改动的收益，最终给出「单机/集群可承载 QPS」的结论与余量建议。

> 同一道题也写在 [Double11LoadTest.java](Double11LoadTest.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第80章-性能压测与Profiling.md](../../../../../../../黑马Java课程笔记/第80章-性能压测与Profiling.md) 的「本章综合练习」一节。
