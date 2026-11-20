# 支付与资金系统设计（com.heima.ch51）

`package com.heima.ch51`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 51-01 | 支付系统全景 | [PaymentOverview.java](PaymentOverview.java) |
| 51-02 | 渠道对接与签名验签 | [ChannelIntegration.java](ChannelIntegration.java) |
| 51-03 | 支付状态机与幂等 | [PaymentStateMachine.java](PaymentStateMachine.java) |
| 51-04 | 异步回调与通知可靠 | [CallbackReliability.java](CallbackReliability.java) |
| 51-05 | 对账系统 | [ReconciliationSystem.java](ReconciliationSystem.java) |
| 51-06 | 退款链路 | [RefundComplexity.java](RefundComplexity.java) |
| 51-07 | 资金安全与风控 | [FundSafety.java](FundSafety.java) |
| 51-08 | 清结算与复式记账 | [ClearingLedger.java](ClearingLedger.java) |
| 51-09 | 分布式事务在支付 | [PaymentTransactions.java](PaymentTransactions.java) |
| 51-10 | 支付高可用与降级 | [PaymentHa.java](PaymentHa.java) |
| 51-11 | 综合案例：一笔支付的端到端时序 | [PaymentCase.java](PaymentCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：一笔支付的端到端时序

**场景**

设计支付链路：渠道对接与签名验签、支付状态机与幂等、异步回调可靠、对账与差错处理、退款链路、清结算与复式记账。核心要求是「钱不能错」。

**任务**
1. 实现渠道签名验签：按参数排序拼接 + 密钥做 HMAC，验证篡改任一参数后验签失败；再实现回调验签。
2. 设计支付状态机：待支付 → 支付中 → 成功/失败/关闭，画出合法流转，实现幂等的「支付中」重复请求返回同一结果。
3. 设计回调可靠性：回调重试（指数退避）、验签、幂等处理、超时后主动查单补偿，说明为什么不能只依赖回调。
4. 实现对账：下载渠道账单与本地流水逐笔比对，分类出「本地有渠道无」「渠道有本地无」「金额不一致」，并给出差错处理流程。

**验收清单**
- [ ] 状态机拒绝非法流转（如成功后再支付），所有流转有日志。
- [ ] 同一订单重复支付请求 10 次只产生一笔扣款。
- [ ] 回调丢失时主动查单能在分钟级补齐状态。
- [ ] 对账结果三类差异都能被识别并输出明细，复式记账借贷平衡。

> 同一道题也写在 [PaymentCase.java](PaymentCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第51章-支付与资金系统设计.md](../../../../../../../黑马Java课程笔记/第51章-支付与资金系统设计.md) 的「本章综合练习」一节。
