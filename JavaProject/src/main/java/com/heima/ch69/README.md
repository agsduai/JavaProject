# 广告系统与实时竞价（com.heima.ch69）

`package com.heima.ch69`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 69-01 | 广告系统全景：从展示到结算 | [AdSystemOverview.java](AdSystemOverview.java) |
| 69-02 | 广告检索：定向与倒排 | [AdTargeting.java](AdTargeting.java) |
| 69-03 | 竞价机制：GFP、GSP 与 VCG | [AuctionMechanism.java](AuctionMechanism.java) |
| 69-04 | eCPM 与排序 | [EcpmRanking.java](EcpmRanking.java) |
| 69-05 | 预算控制与 pacing | [BudgetPacing.java](BudgetPacing.java) |
| 69-06 | 频控与去重 | [FrequencyCap.java](FrequencyCap.java) |
| 69-07 | 归因与转化追踪 | [Attribution.java](Attribution.java) |
| 69-08 | 反作弊：点击欺诈识别 | [ClickFraud.java](ClickFraud.java) |
| 69-09 | 广告计费与对账 | [AdBilling.java](AdBilling.java) |
| 69-10 | 实时竞价链路（RTB） | [RtbPipeline.java](RtbPipeline.java) |
| 69-11 | 综合案例：信息流广告投放 | [FeedAdCase.java](FeedAdCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：实时竞价广告链路

**场景**

设计 RTB 广告系统：竞价请求与出价、eCPM 排序、预算控制与 pacing、频次控制、点击率预估、计费与反作弊。核心约束是「100ms 内返回出价」。

**任务**
1. 实现 eCPM 排序：CPM、CPC、CPA 三种计价方式统一折算成 eCPM 排序，写出换算公式。
2. 实现预算 pacing：日预算均速消耗，按小时配额 + 实时修正，避免上午花完全天预算。
3. 实现频次控制：单用户单广告每天最多 N 次曝光，用 Redis 计数或本地布隆过滤器，说明两者精度差异。
4. 设计反作弊：识别异常点击（高频、同 IP、无停留），给出规则与阈值，并说明误伤如何申诉。

**验收清单**
- [ ] 三种计价方式能放在同一维度排序，排序结果符合 eCPM 高低。
- [ ] 预算消耗曲线平滑，日终不超支也不大量剩余。
- [ ] 频次控制在阈值处生效，跨实例计数一致。
- [ ] 作弊流量能被规则拦截，正常流量误杀率可接受并可控。

> 同一道题也写在 [FeedAdCase.java](FeedAdCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第69章-广告系统与实时竞价.md](../../../../../../../黑马Java课程笔记/第69章-广告系统与实时竞价.md) 的「本章综合练习」一节。
