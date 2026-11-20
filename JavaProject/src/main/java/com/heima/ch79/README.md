# 研发效能与内部开发者平台（com.heima.ch79）

`package com.heima.ch79`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 79-01 | 研发效能全景 | [DevOpsMetrics.java](DevOpsMetrics.java) |
| 79-02 | 脚手架与模板工程 | [Scaffolding.java](Scaffolding.java) |
| 79-03 | 构建缓存与增量构建 | [BuildCache.java](BuildCache.java) |
| 79-04 | Monorepo 与依赖图 | [MonorepoGraph.java](MonorepoGraph.java) |
| 79-05 | 代码扫描与质量门禁 | [CodeScanGate.java](CodeScanGate.java) |
| 79-06 | 环境即服务 | [EnvironmentService.java](EnvironmentService.java) |
| 79-07 | 流水线编排与复用 | [PipelineTemplate.java](PipelineTemplate.java) |
| 79-08 | 制品与版本管理 | [ArtifactPromotion.java](ArtifactPromotion.java) |
| 79-09 | 开发者门户与 Golden Path | [DeveloperPortal.java](DeveloperPortal.java) |
| 79-10 | 效能度量与反模式 | [MetricAntiPatterns.java](MetricAntiPatterns.java) |
| 79-11 | 综合案例：内部开发者平台 | [IdpCase.java](IdpCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：内部开发者平台

**场景**

搭一个 IDP：服务模板与脚手架、自助创建仓库与流水线、环境即代码、服务目录与归属、黄金路径指标（DORA）、开发者体验度量。核心是「新服务从 0 到上线 1 小时」。

**任务**
1. 做服务脚手架：一个命令生成项目骨架（目录、依赖、Dockerfile、CI 配置、健康检查），新服务开箱可跑。
2. 实现自助流水线：创建仓库后自动注入 CI 配置，提交即触发构建与部署到开发环境。
3. 建服务目录：登记每个服务的负责人、依赖、SLO、文档链接，能查出「谁负责这个服务」。
4. 度量 DORA 指标：部署频率、变更前置时间、变更失败率、恢复时间，给出采集方式与改进建议。

**验收清单**
- [ ] 从脚手架到开发环境可访问，全流程在一个小时内完成。
- [ ] 流水线无需人工配置，新仓库自动具备构建部署能力。
- [ ] 服务目录信息准确，任一告警能定位到负责团队。
- [ ] 四项 DORA 指标有数据、有趋势、有对应的改进动作。

> 同一道题也写在 [IdpCase.java](IdpCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第79章-研发效能与内部开发者平台.md](../../../../../../../黑马Java课程笔记/第79章-研发效能与内部开发者平台.md) 的「本章综合练习」一节。
