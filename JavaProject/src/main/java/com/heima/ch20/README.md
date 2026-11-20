# MySQL基础（com.heima.ch20）

`package com.heima.ch20`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 20-01 | 数据库基础与MySQL安装使用 | [MySqlBasics.java](MySqlBasics.java) |
| 20-02 | 建表与数据类型 | [TypeMapping.java](TypeMapping.java) |
| 20-03 | 数据的增删改 | [CrudSqlTemplate.java](CrudSqlTemplate.java) |
| 20-04 | 基础查询与条件过滤 | [ConditionQueryBuilder.java](ConditionQueryBuilder.java) |
| 20-05 | 排序、分页与聚合函数 | [PageCalculator.java](PageCalculator.java) |
| 20-06 | 多表关联查询 | [JoinResultModel.java](JoinResultModel.java) |
| 20-07 | 约束与事务 | [TransactionSimulator.java](TransactionSimulator.java) |
| 20-08 | 索引与视图 | [IndexRuleChecker.java](IndexRuleChecker.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：电商库表设计与查询实战

**场景**

给一个小型电商设计数据库：用户、商品、订单、订单明细四张表，写清约束与索引，然后完成 8 条真实业务查询，包括多表关联、分组聚合、排序分页与事务。

**任务**
1. 写出四张表的建表语句，包含主键、外键、非空、唯一、默认值与检查约束，并说明每张表为什么这样选数据类型。
2. 为订单表设计索引：按用户查订单、按时间范围查订单、按状态统计，各写一条索引并说明最左前缀的影响。
3. 写 4 条查询：查某用户的订单列表（含商品名与金额）、统计每月成交额、查销售额前 10 的商品、查从未下单的用户。
4. 用事务演示下单：插入订单 → 扣减库存 → 插入明细，中途人为制造失败，验证回滚后数据一致。

**验收清单**
- [ ] 四张表能成功创建，外键约束生效（插入不存在的商品会失败）。
- [ ] 每条查询结果与手工核对一致，聚合查询没有重复计数。
- [ ] 用 EXPLAIN 能看到索引被命中，未命中时能说出原因。
- [ ] 事务回滚后库存与订单数据都回到初始状态。

> 同一道题也写在 [IndexRuleChecker.java](IndexRuleChecker.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第20章-MySQL基础.md](../../../../../../../黑马Java课程笔记/第20章-MySQL基础.md) 的「本章综合练习」一节。
