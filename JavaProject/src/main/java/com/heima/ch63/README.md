# 数据仓库维度建模（com.heima.ch63）

`package com.heima.ch63`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 63-01 | 维度建模总览：从业务过程到星型模型 | [DimensionalOverview.java](DimensionalOverview.java) |
| 63-02 | 四步建模法 | [FourStepModeling.java](FourStepModeling.java) |
| 63-03 | 维度表设计：代理键与退化维度 | [DimensionDesign.java](DimensionDesign.java) |
| 63-04 | 缓慢变化维 SCD | [ScdDemo.java](ScdDemo.java) |
| 63-05 | 拉链表与历史追溯 | [ZipperTable.java](ZipperTable.java) |
| 63-06 | 事实表三种类型 | [FactTableTypes.java](FactTableTypes.java) |
| 63-07 | 星型与雪花的权衡 | [StarVsSnowflake.java](StarVsSnowflake.java) |
| 63-08 | 一致性维度与总线矩阵 | [ConformedDimension.java](ConformedDimension.java) |
| 63-09 | 指标口径与原子指标 | [MetricDefinition.java](MetricDefinition.java) |
| 63-10 | 建模评审与反模式 | [ModelingReview.java](ModelingReview.java) |
| 63-11 | 综合案例：电商订单主题建模 | [OrderModelingCase.java](OrderModelingCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：电商数仓维度建模

**场景**

给电商数仓建模：星型模型 vs 雪花模型选型、事实表粒度选择、维度退化与缓慢变化维、拉链表、一致性维度、指标口径统一。要能回答「退款算不算 GMV」。

**任务**
1. 设计交易域模型：订单事实表（粒度=订单明细行）+ 用户/商品/时间/门店维度，说明为什么粒度不能选「订单」而选「明细行」。
2. 实现缓慢变化维：用户等级变更保留历史（拉链表：start_date/end_date/is_current），验证按历史时间点能查到当时的等级。
3. 处理维度退化：把订单号这类「维度」直接放进事实表，说明什么情况下该退化。
4. 统一指标口径：写出 GMV、成交额、退款率三个指标的精确定义与 SQL，说明下单未支付、已退款、跨天退款分别怎么算。

**验收清单**
- [ ] 事实表粒度明确，任何指标都能从该粒度上聚合得到。
- [ ] 拉链表能还原任意历史时点的维度状态，且不产生重复行。
- [ ] 指标口径写成可执行 SQL，两个不同人写出来结果一致。
- [ ] 能解释「同一指标两个部门数字不同」的常见原因（口径、时区、是否含退款）。

> 同一道题也写在 [OrderModelingCase.java](OrderModelingCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第63章-数据仓库维度建模.md](../../../../../../../黑马Java课程笔记/第63章-数据仓库维度建模.md) 的「本章综合练习」一节。
