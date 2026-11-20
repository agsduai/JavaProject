# 项目实战（com.heima.ch27）

`package com.heima.ch27`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 27-01 | 接口设计：URL、方法、状态码 | [RestApiDesign.java](RestApiDesign.java) |
| 27-02 | 分层架构：Controller、Service、Repository | [LayeredArchitecture.java](LayeredArchitecture.java) |
| 27-03 | DTO、VO 与参数校验 | [DtoValidation.java](DtoValidation.java) |
| 27-04 | 统一异常处理与错误码 | [GlobalExceptionHandler.java](GlobalExceptionHandler.java) |
| 27-05 | 分页查询：参数、排序与总数 | [PageQuery.java](PageQuery.java) |
| 27-06 | 事务与并发扣库存 | [StockConcurrency.java](StockConcurrency.java) |
| 27-07 | 幂等与防重复提交 | [IdempotentSubmit.java](IdempotentSubmit.java) |
| 27-08 | 日志、监控与链路追踪 | [Observability.java](Observability.java) |
| 27-09 | 打包与部署 | [Deployment.java](Deployment.java) |
| 27-10 | 接口文档与联调 | [ApiContract.java](ApiContract.java) |
| 27-11 | 综合案例：下单全流程 | [OrderFlowCase.java](OrderFlowCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：下单全流程接口

**场景**

把前几章的能力拼成一条下单链路：接口设计 → 分层 → DTO/VO 转换 → 参数校验 → 事务扣库存 → 幂等防重 → 统一异常 → 日志追踪 → 打包部署。这是第一个「完整业务闭环」的综合题。

**任务**
1. 设计下单接口：URL、方法、状态码、请求体、响应体各写清楚，说明为什么下单用 POST 而不是 GET、为什么创建成功返回 201。
2. 实现 Service 层下单事务：校验商品与库存 → 扣库存（用 update ... where stock >= n 防超卖）→ 写订单与明细 → 清购物车，任一步失败整体回滚。
3. 实现幂等：客户端传 requestId，服务端用唯一索引或 Redis setnx 保证同一 requestId 只成功一次，重复请求返回首次结果。
4. 用 MDC 给每次请求生成 traceId 并打进日志，串联 Controller 与 Service 的日志；最后打成 jar 用 java -jar 启动验证。

**验收清单**
- [ ] 并发 100 次抢 10 件库存，成功订单恰好 10 笔，库存不为负。
- [ ] 同一 requestId 重复提交 10 次只产生 1 笔订单。
- [ ] 库存不足、商品下架、参数非法三种失败场景都返回明确错误码且数据无脏写。
- [ ] 同一请求的日志能靠 traceId 串起来，jar 包在干净环境可启动。

> 同一道题也写在 [OrderFlowCase.java](OrderFlowCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第27章-项目实战.md](../../../../../../../黑马Java课程笔记/第27章-项目实战.md) 的「本章综合练习」一节。
