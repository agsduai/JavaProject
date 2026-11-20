# 响应式编程与虚拟线程（com.heima.ch50）

`package com.heima.ch50`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 50-01 | 阻塞与非阻塞的并发模型 | [BlockingVsNonBlocking.java](BlockingVsNonBlocking.java) |
| 50-02 | Reactor 核心：Mono/Flux 与背压 | [ReactorCore.java](ReactorCore.java) |
| 50-03 | WebFlux 的分层与陷阱 | [WebFluxPatterns.java](WebFluxPatterns.java) |
| 50-04 | 虚拟线程原理：载体线程与挂载 | [VirtualThreadInternals.java](VirtualThreadInternals.java) |
| 50-05 | 虚拟线程实战与并发编排 | [VirtualThreadPractice.java](VirtualThreadPractice.java) |
| 50-06 | 虚拟线程的坑 | [VtPitfalls.java](VtPitfalls.java) |
| 50-07 | 响应式 vs 虚拟线程选型 | [ReactiveVsVt.java](ReactiveVsVt.java) |
| 50-08 | CompletableFuture 与两者整合 | [CfIntegration.java](CfIntegration.java) |
| 50-09 | 高并发 IO 实测对比 | [IoBenchmark.java](IoBenchmark.java) |
| 50-10 | 迁移路线：从线程池到虚拟线程 | [MigrationPath.java](MigrationPath.java) |
| 50-11 | 综合案例：聚合网关 | [AggregationGatewayCase.java](AggregationGatewayCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：聚合网关：响应式与虚拟线程

**场景**

写一个聚合网关：并行调用 5 个下游服务、设置超时与降级、处理背压。分别用 CompletableFuture、Reactor、虚拟线程三种方式实现，最后对比吞吐与代码可读性并给出选型建议。

**任务**
1. 用 CompletableFuture 实现并行聚合：allOf 等待全部、anyOf 取最快、单个超时不影响整体，写出超时降级逻辑。
2. 用 Reactor 的 Mono/Flux 实现同样功能：zip 合并、timeout 兜底、背压控制（限制并发请求数）。
3. 用虚拟线程实现同样功能：每个下游一个虚拟线程，阻塞写法不变，用 StructuredTaskScope 或 join 汇总。
4. 压测三种实现：相同并发下的吞吐、P99、线程数占用，列表对比并给出「什么场景选哪种」的结论。

**验收清单**
- [ ] 三种实现在同一组下游延迟下返回结果一致。
- [ ] 单个下游超时时整体在配置时间内返回兜底数据，不拖垮其他分支。
- [ ] 压测数据完整（吞吐、P99、平台线程数），结论有数据支撑。
- [ ] 能说清虚拟线程的适用边界（阻塞 IO 友好、CPU 密集无益、ThreadLocal 慎用）。

> 同一道题也写在 [AggregationGatewayCase.java](AggregationGatewayCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第50章-响应式编程与虚拟线程.md](../../../../../../../黑马Java课程笔记/第50章-响应式编程与虚拟线程.md) 的「本章综合练习」一节。
