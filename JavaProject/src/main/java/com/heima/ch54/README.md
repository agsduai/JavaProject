# 推荐系统入门（com.heima.ch54）

`package com.heima.ch54`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 54-01 | 推荐系统全景 | [RecsysOverview.java](RecsysOverview.java) |
| 54-02 | 召回：多路召回与融合 | [RecallFusion.java](RecallFusion.java) |
| 54-03 | 协同过滤与相似度 | [ItemCFDemo.java](ItemCFDemo.java) |
| 54-04 | 特征工程与 Embedding 特征 | [FeatureDemo.java](FeatureDemo.java) |
| 54-05 | 粗排与精排模型 | [TwoTowerDemo.java](TwoTowerDemo.java) |
| 54-06 | 重排：多样性、打散与业务规则 | [RerankDemo.java](RerankDemo.java) |
| 54-07 | 曝光过滤与去重 | [BloomFilterDemo.java](BloomFilterDemo.java) |
| 54-08 | A/B 实验与效果评估 | [AbSplitDemo.java](AbSplitDemo.java) |
| 54-09 | 实时特征与在线服务 | [RealtimeFeatureDemo.java](RealtimeFeatureDemo.java) |
| 54-10 | 冷启动问题 | [ColdStartDemo.java](ColdStartDemo.java) |
| 54-11 | 综合案例：商品推荐流水线 | [ProductRecCase.java](ProductRecCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：商品推荐流水线

**场景**

搭一条推荐流水线：多路召回、协同过滤、特征与 Embedding、粗排精排、重排打散、曝光去重、A/B 实验评估、冷启动兜底。用纯 JDK 手写核心算法，不依赖推荐框架。

**任务**
1. 实现多路召回：热门、同品类、协同过滤三路各取 100，按规则融合去重后进入排序池，说明每路的优缺点。
2. 手写 ItemCF：用共现矩阵算物品相似度，给用户推荐相似物品，处理热门物品的权重惩罚。
3. 实现重排：多样性打散（同类目最多连续 2 个）、已曝光过滤、业务加权，对比重排前后列表的类目分布。
4. 设计 A/B 实验：分流规则、样本量估算、核心指标（CTR、转化率）与显著性判断，说明为什么不能看一天数据就下结论。

**验收清单**
- [ ] 三路召回融合后无重复 item，数量符合预期。
- [ ] ItemCF 推荐结果与人工核对合理（相似度高的物品被优先推荐）。
- [ ] 重排后列表类目分布更分散，连续同类目不超过 2 个。
- [ ] A/B 实验有分流可复现、指标计算正确、显著性结论有依据。

> 同一道题也写在 [ProductRecCase.java](ProductRecCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第54章-推荐系统入门.md](../../../../../../../黑马Java课程笔记/第54章-推荐系统入门.md) 的「本章综合练习」一节。
