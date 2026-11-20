# SpringCloudAlibaba生态实战（com.heima.ch53）

`package com.heima.ch53`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 53-01 | 阿里生态全景与选位 | [AlibabaLandscape.java](AlibabaLandscape.java) |
| 53-02 | Nacos 注册中心实战 | [NacosRegistry.java](NacosRegistry.java) |
| 53-03 | Nacos 配置中心与灰度 | [NacosConfig.java](NacosConfig.java) |
| 53-04 | Dubbo RPC 原理与调用 | [DubboRpc.java](DubboRpc.java) |
| 53-05 | Sentinel 限流熔断实战 | [SentinelGuard.java](SentinelGuard.java) |
| 53-06 | Seata 分布式事务 | [SeataAtDemo.java](SeataAtDemo.java) |
| 53-07 | Gateway 与鉴权链路 | [GatewayChainDemo.java](GatewayChainDemo.java) |
| 53-08 | 链路追踪 SkyWalking | [AgentInstrumentDemo.java](AgentInstrumentDemo.java) |
| 53-09 | 生态整合：一个订单服务的阿里栈落地 | [AliStackIntegration.java](AliStackIntegration.java) |
| 53-10 | Spring Cloud vs Spring Cloud Alibaba 对照 | [ScVsSca.java](ScVsSca.java) |
| 53-11 | 综合案例：阿里栈微服务上线检查单 | [AliLaunchCase.java](AliLaunchCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：阿里栈微服务上线检查单

**场景**

用 Spring Cloud Alibaba 落地一个订单服务：Nacos 注册与配置、Dubbo 调用、Sentinel 限流熔断、Seata 分布式事务、Gateway 鉴权、SkyWalking 追踪，最后产出一份上线检查单。

**任务**
1. 接入 Nacos：服务注册与发现、配置中心读取与动态刷新、命名空间隔离环境，验证改配置不重启即生效。
2. 用 Dubbo 实现订单调库存：接口与实现分离、超时与重试配置、泛化调用排查问题，说明与 HTTP 调用的取舍。
3. 用 Sentinel 配置流控与熔断规则：QPS 阈值、慢调用比例、异常比例，验证触发后的降级返回。
4. 用 Seata 处理跨服务事务：下单扣库存与写订单在一个全局事务内，模拟库存服务异常验证整体回滚。

**验收清单**
- [ ] 服务在 Nacos 控制台可见，配置修改后客户端在秒级生效。
- [ ] Dubbo 调用超时后按配置重试，非幂等接口不重试。
- [ ] Sentinel 规则触发后返回兜底响应，控制台能看到拒绝计数。
- [ ] Seata 全局事务回滚后两边数据一致，无悬挂与空回滚。
- [ ] 上线检查单覆盖注册、配置、限流、事务、追踪、日志、监控、回滚八项且逐项可勾选。

> 同一道题也写在 [AliLaunchCase.java](AliLaunchCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第53章-SpringCloudAlibaba生态实战.md](../../../../../../../黑马Java课程笔记/第53章-SpringCloudAlibaba生态实战.md) 的「本章综合练习」一节。
