# 营销活动与促销引擎设计（com.heima.ch83）

`package com.heima.ch83`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 83-01 | 营销系统全景与价格链路 | [PriceChain.java](PriceChain.java) |
| 83-02 | 四类优惠的算法与取整 | [DiscountRounding.java](DiscountRounding.java) |
| 83-03 | 叠加互斥与最优组合 | [CouponCombination.java](CouponCombination.java) |
| 83-04 | 价格分摊与退款分摊 | [PriceAllocation.java](PriceAllocation.java) |
| 83-05 | 券状态机与并发核销 | [CouponStateMachine.java](CouponStateMachine.java) |
| 83-06 | 库存扣减与防超卖 | [StockDeduction.java](StockDeduction.java) |
| 83-07 | 限购与防刷 | [PurchaseLimit.java](PurchaseLimit.java) |
| 83-08 | 活动时间窗口与时区 | [TimeWindow.java](TimeWindow.java) |
| 83-09 | 预算控制与超发防护 | [BudgetControl.java](BudgetControl.java) |
| 83-10 | 营销归因与增量 ROI | [MarketingAttribution.java](MarketingAttribution.java) |
| 83-11 | 综合案例：大促券全链路 | [PromotionCase.java](PromotionCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：双十一前的一次全场券活动落地

**场景**

双十一当天要发一批「满 300 减 50」全场券，总预算 2500 万元，可用库存 10 万件，预计 5 万 QPS 的领券峰值，日活 800 万。运营的目标是「把预算花完、把库存卖完、不要超发、不要被黄牛刷走」，财务的目标是「每一分优惠都能对账、能分摊到行、能按行退」。请设计这套促销引擎并给出验证数据。

**任务**
1. 定链路：画出从原价到应付的价格计算阶段，写清每一步的输入输出，并说明折扣与满减互换顺序会差多少钱。
2. 算优惠：实现满减、阶梯、折扣（带上限）、立减四类算法，说明按行取整与整单取整的差额，并给出固定规则。
3. 配叠加：设计券的互斥组与叠加规则，用穷举求出最优组合，并给出一个「按面额贪心会算贵」的反例。
4. 控库存与预算：用 CAS 扣减防超卖，用预扣预算防超发，给出限购（用户 + 设备）与幂等键方案。
5. 验效果：算出表面 ROI 与增量 ROI 的差距，说明为什么「券都核销了」不等于「活动赚钱了」。

**验收清单**
- [ ] 价格链路阶段固定且可追溯，顺序差异有具体金额对比，不是「大概差不多」。
- [ ] 四类优惠的取整规则统一且写死，分摊后各行优惠之和严格等于优惠总额。
- [ ] 券组合有穷举最优解，且指出了贪心算法的失效场景与差额。
- [ ] 库存与预算都有并发扣减方案，超卖/超发能被复现也能被修好，限购覆盖用户与设备两个维度。
- [ ] 效果评估区分表面 ROI 与增量 ROI，结论带增量口径，不是只看核销率。

> 同一道题也写在 [PromotionCase.java](PromotionCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第83章-营销活动与促销引擎设计.md](../../../../../../../黑马Java课程笔记/第83章-营销活动与促销引擎设计.md) 的「本章综合练习」一节。

