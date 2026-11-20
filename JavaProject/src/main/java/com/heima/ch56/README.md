# 稳定性工程与容量管理（com.heima.ch56）

`package com.heima.ch56`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 56-01 | 稳定性体系总览 | [StabilityOverview.java](StabilityOverview.java) |
| 56-02 | 全链路压测 | [ShadowRoutingDemo.java](ShadowRoutingDemo.java) |
| 56-03 | 容量画像与水位管理 | [WatermarkDemo2.java](WatermarkDemo2.java) |
| 56-04 | 预案平台与自动开关 | [ContingencyPlatform.java](ContingencyPlatform.java) |
| 56-05 | 大促保障作战手册 | [PromotionWarRoom.java](PromotionWarRoom.java) |
| 56-06 | 故障演练常态化 | [DrillRoutines.java](DrillRoutines.java) |
| 56-07 | 风险治理：变更管理与风险清单 | [RiskRegisterDemo.java](RiskRegisterDemo.java) |
| 56-08 | 依赖治理与弱依赖降级清单 | [DependencyGovernance.java](DependencyGovernance.java) |
| 56-09 | 容量与成本的年度规划 | [AnnualCapacityPlan.java](AnnualCapacityPlan.java) |
| 56-10 | 稳定性度量与文化建设 | [StabilityMetrics.java](StabilityMetrics.java) |
| 56-11 | 综合案例：一次大促的稳定性闭环 | [StabilityCase.java](StabilityCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：一次大促的稳定性闭环

**场景**

为双十一大促做稳定性保障：容量画像与水位、全链路压测、预案与开关、依赖治理与降级清单、故障演练、变更管控。最后产出一份可执行的作战手册。

**任务**
1. 做容量画像：每个核心接口的 QPS 峰值、单机承载、所需实例数、水位阈值（如 CPU 60% 告警），算出扩容余量。
2. 设计全链路压测：影子库/影子表、流量染色、压测数据隔离，说明如何避免压测污染生产数据。
3. 设计预案与开关：限流、降级（关闭非核心功能）、熔断、切流量，每个预案写清触发条件、操作人、预期影响。
4. 列依赖治理清单：强依赖与弱依赖分类，弱依赖全部配降级兜底，验证关掉弱依赖后主链路仍可用。

**验收清单**
- [ ] 容量画像有计算公式与实测数据支撑，水位阈值可触发告警。
- [ ] 压测流量不写入生产真实表，压测后可一键清理。
- [ ] 每个预案都演练过，操作耗时记录在案（分钟级）。
- [ ] 关掉全部弱依赖后下单主链路仍能完成，只是少了非核心展示。

> 同一道题也写在 [StabilityCase.java](StabilityCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第56章-稳定性工程与容量管理.md](../../../../../../../黑马Java课程笔记/第56章-稳定性工程与容量管理.md) 的「本章综合练习」一节。
