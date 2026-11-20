# 湖仓一体与数据平台（com.heima.ch46）

`package com.heima.ch46`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 46-01 | 从数仓到数据湖 | [WarehouseToLake.java](WarehouseToLake.java) |
| 46-02 | 列式存储与 Parquet | [ParquetColumnar.java](ParquetColumnar.java) |
| 46-03 | 对象存储与数据分层 | [ObjectStorage.java](ObjectStorage.java) |
| 46-04 | Hive 与元数据管理 | [HiveMetastore.java](HiveMetastore.java) |
| 46-05 | Iceberg 表格式 | [IcebergTableFormat.java](IcebergTableFormat.java) |
| 46-06 | 批流一体存储 | [LakeStreaming.java](LakeStreaming.java) |
| 46-07 | MPP 与即席查询 | [MppAdhoc.java](MppAdhoc.java) |
| 46-08 | 数据质量与治理 | [DataGovernance.java](DataGovernance.java) |
| 46-09 | 数据集成与同步 | [DataIngestion.java](DataIngestion.java) |
| 46-10 | 数据平台服务化 | [DataPlatformService.java](DataPlatformService.java) |
| 46-11 | 综合案例：订单主题湖仓 | [LakehouseCase.java](LakehouseCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：订单主题湖仓

**场景**

把订单数据从数据库同步到湖仓：分层建模（ODS/DWD/DWS/ADS）、列式存储与分区、Iceberg 表格式的增量读、数据质量校验、批流一体。要说明每一步解决什么问题。

**任务**
1. 设计分层：ODS 原样落地、DWD 清洗去重、DWS 按主题汇总、ADS 面向报表，给出每层的表结构与分区策略。
2. 设计列式存储与分区：按日期分区、按业务字段分桶，说明为什么列存适合分析、分区能剪掉多少扫描量。
3. 实现增量同步与幂等：用 CDC 思路记录变更，按主键 merge（存在则更新、不存在则插入），重复投递结果不变。
4. 设计数据质量校验：空值率、主键唯一、金额非负、订单与明细数量一致，校验失败阻断下游并告警。

**验收清单**
- [ ] 四层表能串起来跑通一条从明细到报表的链路，结果与直接查源库一致。
- [ ] 分区裁剪生效，查某一天只扫描对应分区。
- [ ] 增量同步重复执行 3 次，结果表数据不重复。
- [ ] 质量校验能拦住人为注入的脏数据，并给出具体是哪条规则失败。

> 同一道题也写在 [LakehouseCase.java](LakehouseCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第46章-湖仓一体与数据平台.md](../../../../../../../黑马Java课程笔记/第46章-湖仓一体与数据平台.md) 的「本章综合练习」一节。
