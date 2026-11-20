# 开放平台与API治理（com.heima.ch64）

`package com.heima.ch64`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 64-01 | 开放平台全景：从内部 API 到生态 | [OpenPlatformOverview.java](OpenPlatformOverview.java) |
| 64-02 | OAuth2 四种授权模式 | [OAuth2Flow.java](OAuth2Flow.java) |
| 64-03 | API Key、签名与防重放 | [SignatureDemo.java](SignatureDemo.java) |
| 64-04 | 配额、限流与计费 | [QuotaAndBilling.java](QuotaAndBilling.java) |
| 64-05 | API 版本演进与兼容策略 | [ApiVersioning.java](ApiVersioning.java) |
| 64-06 | 契约优先与 OpenAPI | [ContractFirst.java](ContractFirst.java) |
| 64-07 | SDK 设计与开发者体验 | [SdkDesign.java](SdkDesign.java) |
| 64-08 | API 网关治理：鉴权、审计、灰度 | [OpenGateway.java](OpenGateway.java) |
| 64-09 | 错误码与文档体系 | [ErrorCodeSystem.java](ErrorCodeSystem.java) |
| 64-10 | API 安全：越权、注入与滥用 | [OpenApiSecurity.java](OpenApiSecurity.java) |
| 64-11 | 综合案例：支付开放平台 | [PaymentOpenPlatform.java](PaymentOpenPlatform.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：开放平台的设计与治理

**场景**

做一个开放平台：应用注册与 AppKey/Secret、签名与防重放、限流配额、权限 Scope、API 版本演进、错误码规范、开发者文档与沙箱。核心是「第三方不可信」。

**任务**
1. 实现签名机制：参数排序 + 时间戳 + nonce + Secret 做 HMAC，服务端校验签名与时间窗口（如 5 分钟），拒绝重放。
2. 实现分级限流：按应用、按接口、按 IP 三个维度限流，超限返回 429 并带 Retry-After。
3. 设计权限模型：Scope 授权（读订单/写订单/退款），应用只能调用已授权接口，越权返回 403。
4. 设计版本演进：URL 版本（/v1/、/v2/）与字段兼容策略（只加不删、字段废弃走公告期），说明为什么不能直接改老接口。

**验收清单**
- [ ] 篡改任一参数或复用旧 nonce 的请求被拒绝，错误信息明确。
- [ ] 限流在阈值处精确生效，不同应用之间互不影响。
- [ ] 未授权 Scope 的调用被拦截，且不会泄露接口是否存在。
- [ ] 新旧版本可并行运行，老版本在公告期内仍可用。

> 同一道题也写在 [PaymentOpenPlatform.java](PaymentOpenPlatform.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第64章-开放平台与API治理.md](../../../../../../../黑马Java课程笔记/第64章-开放平台与API治理.md) 的「本章综合练习」一节。
