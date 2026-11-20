# CICD与发布工程（com.heima.ch41）

`package com.heima.ch41`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 41-01 | Git 协作模型与分支策略 | [GitBranchModel.java](GitBranchModel.java) |
| 41-02 | 提交规范与变更追溯 | [CommitConvention.java](CommitConvention.java) |
| 41-03 | 流水线的阶段设计 | [PipelineStages.java](PipelineStages.java) |
| 41-04 | 制品管理与版本策略 | [ArtifactRegistry.java](ArtifactRegistry.java) |
| 41-05 | 环境晋升与配置管理 | [EnvironmentPromotion.java](EnvironmentPromotion.java) |
| 41-06 | 发布策略：滚动与蓝绿 | [RollingBlueGreen.java](RollingBlueGreen.java) |
| 41-07 | 发布策略：金丝雀与灰度 | [CanaryRelease.java](CanaryRelease.java) |
| 41-08 | 特性开关与暗发布 | [FeatureFlags.java](FeatureFlags.java) |
| 41-09 | 数据库变更与发布协同 | [DbMigration.java](DbMigration.java) |
| 41-10 | 发布自动化与审计 | [ReleaseAudit.java](ReleaseAudit.java) |
| 41-11 | 综合案例：一条完整的发布流水线 | [PipelineCase.java](PipelineCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：一条完整的发布流水线

**场景**

为团队设计一条从提交到上线的流水线：分支策略、提交规范、构建与测试、制品版本、环境晋升、灰度发布、数据库变更协同、发布审计。每一段都要能回答「出问题怎么回滚」。

**任务**
1. 设计分支模型：main/develop/feature/release/hotfix 各自职责与合并方向，写清 hotfix 如何同时回到 main 与 develop。
2. 设计流水线阶段：编译 → 单测 → 静态检查 → 打制品 → 部署测试环境 → 自动化回归 → 人工卡点 → 灰度 → 全量，标出每段的失败处理。
3. 设计制品版本与晋升：版本号规则、制品仓库不可变、同一制品逐级晋升而不重新构建，说明为什么不能每环境重打一次包。
4. 设计灰度与回滚：按 1% → 10% → 50% → 100% 放量，每步的观测指标与判定阈值，以及回滚需要多久、依赖哪些开关。

**验收清单**
- [ ] 分支图能说清任一提交最终如何到达 main，不出现双向合并死循环。
- [ ] 流水线任一阶段失败都不会污染下游环境，制品不会被覆盖。
- [ ] 灰度每一步都有量化判定标准（错误率、P99、业务指标），不是「看着还行」。
- [ ] 回滚方案经过演练，给出实际耗时与数据兼容说明。

> 同一道题也写在 [PipelineCase.java](PipelineCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第41章-CICD与发布工程.md](../../../../../../../黑马Java课程笔记/第41章-CICD与发布工程.md) 的「本章综合练习」一节。
