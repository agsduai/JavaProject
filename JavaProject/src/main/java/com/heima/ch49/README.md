# 领域驱动设计与架构模式（com.heima.ch49）

`package com.heima.ch49`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 49-01 | 领域驱动设计总览 | [DddOverview.java](DddOverview.java) |
| 49-02 | 战术设计：聚合、实体与值对象 | [DddTactical.java](DddTactical.java) |
| 49-03 | 聚合设计与领域事件实战 | [DomainEventFlow.java](DomainEventFlow.java) |
| 49-04 | 六边形架构与端口适配器 | [HexagonalArch.java](HexagonalArch.java) |
| 49-05 | CQRS 读写分离建模 | [CqrsPattern.java](CqrsPattern.java) |
| 49-06 | 事件溯源与投影 | [EventSourcing.java](EventSourcing.java) |
| 49-07 | 上下文集成与防腐层 | [AntiCorruptionLayer.java](AntiCorruptionLayer.java) |
| 49-08 | DDD 与微服务、单体落地 | [DddMicroservices.java](DddMicroservices.java) |
| 49-09 | 领域建模实战：营销与优惠 | [MarketingModeling.java](MarketingModeling.java) |
| 49-10 | 遗留系统改造与演进式架构 | [LegacyRefactor.java](LegacyRefactor.java) |
| 49-11 | 综合案例：订单域建模全流程 | [OrderDomainCase.java](OrderDomainCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：订单域建模全流程

**场景**

用 DDD 给订单域建模：划分子域与限界上下文、识别聚合与值对象、定义领域事件、用六边形架构隔离外部依赖、CQRS 分离读写，最后处理与库存上下文的集成。

**任务**
1. 划分子域：订单、支付、库存、履约，标出核心域/支撑域/通用域，给出限界上下文的边界与职责。
2. 设计订单聚合：聚合根、实体、值对象（金额、地址、订单号），说明聚合内的一致性边界与「一次事务只改一个聚合」的原因。
3. 定义领域事件（OrderCreated、OrderPaid、OrderCanceled）并实现发布订阅，用事件驱动库存扣减，处理重复消费。
4. 用六边形架构落地：领域层不依赖框架，端口定义在领域侧、适配器在基础设施侧，用测试证明替换适配器不影响领域逻辑。

**验收清单**
- [ ] 聚合边界清晰，跨聚合只通过 id 引用而不持有对象。
- [ ] 领域事件能被正确发布与消费，重复投递不导致库存重复扣减。
- [ ] 领域层代码不 import 任何框架或数据库包，纯 Java 可单测。
- [ ] 替换一个适配器实现（如内存仓储换数据库仓储）后领域测试仍全绿。

> 同一道题也写在 [OrderDomainCase.java](OrderDomainCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第49章-领域驱动设计与架构模式.md](../../../../../../../黑马Java课程笔记/第49章-领域驱动设计与架构模式.md) 的「本章综合练习」一节。
