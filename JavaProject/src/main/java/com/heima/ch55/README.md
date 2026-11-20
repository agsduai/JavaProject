# 多租户SaaS架构（com.heima.ch55）

`package com.heima.ch55`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 55-01 | SaaS 与多租户总览 | [SaaSOverview.java](SaaSOverview.java) |
| 55-02 | 租户隔离三方案 | [TenantIsolation.java](TenantIsolation.java) |
| 55-03 | 租户上下文与路由 | [TenantContext.java](TenantContext.java) |
| 55-04 | 数据隔离的安全边界 | [TenantDataSafety.java](TenantDataSafety.java) |
| 55-05 | 租户配额与计费 | [TenantQuotaBilling.java](TenantQuotaBilling.java) |
| 55-06 | 租户配置分层 | [TenantConfig.java](TenantConfig.java) |
| 55-07 | 租户生命周期 | [TenantLifecycle.java](TenantLifecycle.java) |
| 55-08 | 噪声邻居与性能隔离 | [NoisyNeighborDemo.java](NoisyNeighborDemo.java) |
| 55-09 | 多租户下的可观测 | [TenantObservability.java](TenantObservability.java) |
| 55-10 | SaaS 安全与合规 | [SaaSCompliance.java](SaaSCompliance.java) |
| 55-11 | 综合案例：多租户 CMS 落地 | [TenantCmsCase.java](TenantCmsCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：多租户 CMS 落地

**场景**

做一个多租户内容管理系统：租户隔离方案选型、租户上下文与路由、数据隔离安全边界、配额计费、配置分层、噪声邻居隔离。核心是「一个租户绝不能看到另一个租户的数据」。

**任务**
1. 对比三种隔离方案（独立库、共享库独立 schema、共享表加 tenant_id），给出本项目选型与理由。
2. 实现租户上下文：请求头解析租户 → ThreadLocal 传递 → 数据访问层自动加租户条件，并处理异步线程中上下文丢失。
3. 实现越权防护：所有查询强制带 tenant_id，写一个测试用例证明伪造租户 id 无法读到他人数据。
4. 实现配额与隔离：按租户限制请求速率与存储量，单租户超限不影响其他租户（噪声邻居）。

**验收清单**
- [ ] 任何查询都不可能出现跨租户数据，越权测试用例全部被拦截。
- [ ] 异步任务中租户上下文正确传递（或明确拒绝执行）。
- [ ] 配额超限时只影响该租户，其他租户响应时间无明显变化。
- [ ] 配置分层生效：平台级默认配置 + 租户级覆盖，改租户配置不影响其他租户。

> 同一道题也写在 [TenantCmsCase.java](TenantCmsCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第55章-多租户SaaS架构.md](../../../../../../../黑马Java课程笔记/第55章-多租户SaaS架构.md) 的「本章综合练习」一节。
