# 微服务入门（com.heima.ch29）

`package com.heima.ch29`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 29-01 | 从单体到微服务：拆与不拆 | [MonolithToMicro.java](MonolithToMicro.java) |
| 29-02 | 服务拆分原则与边界 | [ServiceSplit.java](ServiceSplit.java) |
| 29-03 | 注册中心与服务发现 | [RegistryDiscovery.java](RegistryDiscovery.java) |
| 29-04 | 远程调用：RPC 与 HTTP | [RemoteCall.java](RemoteCall.java) |
| 29-05 | 负载均衡与重试策略 | [LoadBalanceRetry.java](LoadBalanceRetry.java) |
| 29-06 | 网关：路由、鉴权、限流 | [ApiGateway.java](ApiGateway.java) |
| 29-07 | 配置中心与动态刷新 | [ConfigCenter.java](ConfigCenter.java) |
| 29-08 | 熔断、降级与服务隔离 | [CircuitBreaker.java](CircuitBreaker.java) |
| 29-09 | 分布式事务：从 2PC 到最终一致 | [DistributedTx.java](DistributedTx.java) |
| 29-10 | 链路追踪与日志聚合 | [TraceLogging.java](TraceLogging.java) |
| 29-11 | 综合案例：微服务化的下单链路 | [MicroserviceOrderCase.java](MicroserviceOrderCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：微服务化的下单链路

**场景**

把单体下单拆成订单、库存、用户三个服务：注册与发现、远程调用带超时、负载均衡与重试、网关鉴权限流、熔断降级、最终一致，最后用链路追踪串起一次下单。

**任务**
1. 用内存 Map 模拟注册中心：服务启动注册、定时心跳、超时剔除，客户端按服务名拿到实例列表。
2. 实现远程调用：设置连接与读取超时，失败重试最多 2 次且只对幂等接口重试，说明为什么不幂等接口不能重试。
3. 实现网关：路由转发、token 鉴权、令牌桶限流（每秒 20 个），超限返回 429 而不是 500。
4. 实现熔断器三态（CLOSED/OPEN/HALF_OPEN）：下游连续失败 5 次打开、30 秒后放一个探测请求、成功则恢复；熔断期间走降级返回兜底数据。

**验收清单**
- [ ] 停掉一个服务实例后客户端能在心跳超时内把它剔除，请求不再打过去。
- [ ] 下游超时场景下调用方在配置时间内返回，不出现线程堆积。
- [ ] 限流触发时被拒绝的请求数与配置速率一致，返回 429。
- [ ] 熔断打开后请求快速失败走降级，恢复后自动切回正常调用，日志能看清三态迁移。

> 同一道题也写在 [MicroserviceOrderCase.java](MicroserviceOrderCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第29章-微服务入门.md](../../../../../../../黑马Java课程笔记/第29章-微服务入门.md) 的「本章综合练习」一节。
