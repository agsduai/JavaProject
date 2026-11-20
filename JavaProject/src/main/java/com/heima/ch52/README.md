# 毕业总纲系统设计与技术决策（com.heima.ch52）

`package com.heima.ch52`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 52-01 | 需求分析与容量估算 | [RequirementCapacity.java](RequirementCapacity.java) |
| 52-02 | 架构决策记录 ADR | [ArchitectureDecision.java](ArchitectureDecision.java) |
| 52-03 | 技术选型决策树：全书选型回顾 | [TechSelectionTree.java](TechSelectionTree.java) |
| 52-04 | 系统设计题方法论 | [SystemDesignMethod.java](SystemDesignMethod.java) |
| 52-05 | 非功能需求与权衡 | [NonFunctionalTradeoff.java](NonFunctionalTradeoff.java) |
| 52-06 | 全书知识地图 | [KnowledgeMap.java](KnowledgeMap.java) |
| 52-07 | 毕业能力清单与自测 | [GraduationChecklist.java](GraduationChecklist.java) |
| 52-08 | 综合毕业案例①：电商平台总架构 | [PlatformArchitecture.java](PlatformArchitecture.java) |
| 52-09 | 综合毕业案例②：演进路线与里程碑 | [EvolutionRoadmap.java](EvolutionRoadmap.java) |
| 52-10 | 综合毕业案例③：故障演练与毕业答辩 | [GradDefense.java](GradDefense.java) |
| 52-11 | 毕业寄语：持续学习的工程人生 | [ClosingWords.java](ClosingWords.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：毕业答辩：系统设计与技术决策

**场景**

为一个「日均 100 万单的电商平台」做完整系统设计并答辩：容量估算、架构分层、技术选型与 ADR、非功能需求权衡、演进路线、故障演练。这一题考的是「能不能把全书串成一个能自洽的方案」。

**任务**
1. 容量估算：从 DAU 推算 QPS、存储量、带宽、缓存与数据库规格，写出每一步假设与计算过程。
2. 画总架构图：接入层、应用层、中间件、存储层、数据平台，标出每个组件的职责与关键指标。
3. 写 3 份 ADR：数据库选型、缓存方案、消息中间件选型，每份包含背景、候选方案、决策、后果与回滚方式。
4. 设计演进路线：从单体到拆分的三个阶段，每阶段的触发条件（数据量、团队规模、故障频率）与验证指标。
5. 设计一次故障演练：模拟数据库主库宕机，写出检测、切换、降级、恢复的完整动作与预期耗时。

**验收清单**
- [ ] 容量估算可复算，关键数字（QPS、存储）与实际业务量级匹配。
- [ ] 架构图中不存在单点，每个组件都说明了故障时的降级路径。
- [ ] ADR 里能看出「为什么不选另一个」，而不是只有结论。
- [ ] 演进路线的每个阶段都有明确的触发条件，不是「以后再说」。
- [ ] 故障演练有量化目标（RTO、RPO），并说明数据一致性如何保证。

> 同一道题也写在 [ClosingWords.java](ClosingWords.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第52章-毕业总纲系统设计与技术决策.md](../../../../../../../黑马Java课程笔记/第52章-毕业总纲系统设计与技术决策.md) 的「本章综合练习」一节。
