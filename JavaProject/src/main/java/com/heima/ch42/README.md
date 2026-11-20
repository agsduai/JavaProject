# 可观测性平台（com.heima.ch42）

`package com.heima.ch42`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 42-01 | 为什么需要可观测性 | [WhyObservability.java](WhyObservability.java) |
| 42-02 | 结构化日志与采集管道 | [StructuredLogging.java](StructuredLogging.java) |
| 42-03 | 日志存储与查询 | [LogStoreQuery.java](LogStoreQuery.java) |
| 42-04 | 指标模型与时间序列 | [MetricsModel.java](MetricsModel.java) |
| 42-05 | 黄金指标与 SLO | [SloGoldenSignals.java](SloGoldenSignals.java) |
| 42-06 | 告警设计与降噪 | [AlertDesign.java](AlertDesign.java) |
| 42-07 | 分布式链路追踪 | [TraceSpans.java](TraceSpans.java) |
| 42-08 | 采样与链路成本 | [TraceSampling.java](TraceSampling.java) |
| 42-09 | 持续剖析与资源画像 | [ContinuousProfiling.java](ContinuousProfiling.java) |
| 42-10 | 仪表盘与事故响应 | [DashboardIncident.java](DashboardIncident.java) |
| 42-11 | 综合案例：一次线上事故的可观测复盘 | [IncidentCase.java](IncidentCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：一次线上事故的可观测复盘

**场景**

给定一次「下单接口成功率跌到 80%」的事故，用日志、指标、链路三条腿定位：从告警到定位到恢复，最后补上监控盲区与 SLO。

**任务**
1. 设计结构化日志：字段规范（时间、级别、traceId、userId、耗时、错误码），把散乱日志改造成可检索格式。
2. 定义黄金指标与 SLO：请求量、错误率、延迟、饱和度各一条，写出 SLI 计算方式与 SLO 目标，并配置告警阈值。
3. 用链路追踪还原一次失败请求：从网关到订单服务到库存服务，找出耗时最长与报错的那一跳。
4. 写事故复盘报告：时间线、根因、影响面、处置动作、改进项（含负责人与期限），并指出哪一项监控缺失导致发现变慢。

**验收清单**
- [ ] 日志能按 traceId 检索出一次请求的全部环节，字段完整无缺。
- [ ] SLO 计算可复现，告警阈值能触发且不产生大量误报。
- [ ] 链路图能指出具体是哪一跳超时或报错，并对应到代码位置。
- [ ] 复盘报告里改进项可验证，不是「加强监控」这类空话。

> 同一道题也写在 [IncidentCase.java](IncidentCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第42章-可观测性平台.md](../../../../../../../黑马Java课程笔记/第42章-可观测性平台.md) 的「本章综合练习」一节。
