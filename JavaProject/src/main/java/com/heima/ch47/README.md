# 云原生进阶Mesh与Serverless（com.heima.ch47）

`package com.heima.ch47`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 47-01 | 从 K8s 到 Service Mesh | [WhyServiceMesh.java](WhyServiceMesh.java) |
| 47-02 | Sidecar 与数据面 | [SidecarDataplane.java](SidecarDataplane.java) |
| 47-03 | 流量治理与故障注入 | [MeshTrafficPolicy.java](MeshTrafficPolicy.java) |
| 47-04 | mTLS 与零信任 | [MeshMtls.java](MeshMtls.java) |
| 47-05 | Mesh 的可观测性 | [MeshObservability.java](MeshObservability.java) |
| 47-06 | Sidecarless 与 Mesh 的代价 | [MeshCost.java](MeshCost.java) |
| 47-07 | Serverless 函数 | [ServerlessFn.java](ServerlessFn.java) |
| 47-08 | 弹性伸缩与成本 | [ElasticCost.java](ElasticCost.java) |
| 47-09 | 多集群与容灾 | [MultiCluster.java](MultiCluster.java) |
| 47-10 | 混沌工程 | [ChaosEngineering.java](ChaosEngineering.java) |
| 47-11 | 综合案例：Mesh 化的订单服务 | [MeshCase.java](MeshCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：Mesh 化的订单服务

**场景**

把订单服务接入 Service Mesh：sidecar 接管流量、流量治理与故障注入、mTLS 零信任、Mesh 可观测、Serverless 弹性伸缩，最后评估 Mesh 的代价。

**任务**
1. 画出 sidecar 数据面与控制面结构，说明业务容器与 sidecar 的职责边界，以及为什么业务代码不需要改。
2. 设计流量治理：超时、重试、熔断、按版本权重分流（v1 90% / v2 10%），并注入 5% 延迟故障验证熔断生效。
3. 设计 mTLS：证书签发、轮换、双向校验，说明为什么零信任下「内网即安全」不再成立。
4. 评估代价：sidecar 带来的额外延迟与资源开销、运维复杂度，给出「什么规模适合上 Mesh」的判断标准。

**验收清单**
- [ ] 流量治理规则生效：改权重后流量比例符合配置，故障注入后熔断被触发。
- [ ] mTLS 开启后无证书的调用被拒绝，有证书的调用成功。
- [ ] Mesh 指标能按服务、版本、响应码维度统计，能看到每次调用的耗时分布。
- [ ] 代价评估有量化数据（延迟增加多少毫秒、每 Pod 多占多少内存），并给出适用与不适用的场景。

> 同一道题也写在 [MeshCase.java](MeshCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第47章-云原生进阶Mesh与Serverless.md](../../../../../../../黑马Java课程笔记/第47章-云原生进阶Mesh与Serverless.md) 的「本章综合练习」一节。
