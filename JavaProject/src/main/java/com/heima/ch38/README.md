# Elasticsearch与全文搜索（com.heima.ch38）

`package com.heima.ch38`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 38-01 | 为什么不用数据库 LIKE | [WhyNotLike.java](WhyNotLike.java) |
| 38-02 | 倒排索引的实现 | [InvertedIndex.java](InvertedIndex.java) |
| 38-03 | 中文分词与 IK 分析器 | [ChineseTokenizer.java](ChineseTokenizer.java) |
| 38-04 | 索引与映射设计 | [IndexMapping.java](IndexMapping.java) |
| 38-05 | DSL 查询：term、match 与 bool 组合 | [DslQuery.java](DslQuery.java) |
| 38-06 | 相关性评分与 BM25 | [RelevanceScore.java](RelevanceScore.java) |
| 38-07 | 聚合分析 | [AggregationDemo.java](AggregationDemo.java) |
| 38-08 | 分页与深度分页 | [DeepPagination.java](DeepPagination.java) |
| 38-09 | 数据同步：让搜索库跟上数据库 | [DataSync.java](DataSync.java) |
| 38-10 | 集群、分片与副本 | [EsCluster.java](EsCluster.java) |
| 38-11 | 综合案例：商品搜索全链路 | [ProductSearchCase.java](ProductSearchCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：商品搜索全链路

**场景**

实现商品搜索：倒排索引构建、分词、映射设计、DSL 查询组合、相关性打分、聚合统计、深度分页、数据同步。没有 ES 环境时用纯 JDK 手写一个迷你倒排索引，逻辑与 ES 一一对应。

**任务**
1. 手写倒排索引：文档入库时切词建 term → docId 映射，支持多 term 求交集查询，并对比与数据库 LIKE 的性能差异。
2. 设计索引映射：title 用分词、brand 用 keyword、price 用数值、tags 用数组，说明每种类型为什么这样选。
3. 实现 bool 查询组合：must（关键词）+ filter（品牌、价格区间）+ should（标签加权），并解释 filter 为什么不参与打分。
4. 实现聚合与分页：按品牌聚合计数、按价格区间分桶，用 search_after 替代 from+size 解决深度分页，说明为什么深分页会爆。

**验收清单**
- [ ] 倒排索引查询结果与全量扫描结果一致，10 万文档下查询耗时低一个量级。
- [ ] bool 查询在任意条件组合下都不出现漏检或误检。
- [ ] 聚合结果数量之和等于命中总数。
- [ ] 深度分页方案在 10 万条数据下内存稳定，不出现 OOM。

> 同一道题也写在 [ProductSearchCase.java](ProductSearchCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第38章-Elasticsearch与全文搜索.md](../../../../../../../黑马Java课程笔记/第38章-Elasticsearch与全文搜索.md) 的「本章综合练习」一节。
