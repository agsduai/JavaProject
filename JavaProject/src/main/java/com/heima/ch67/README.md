# 地理空间与位置服务（com.heima.ch67）

`package com.heima.ch67`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 67-01 | LBS 全景：坐标、距离与索引 | [LbsOverview.java](LbsOverview.java) |
| 67-02 | 坐标系与坐标转换 | [CoordinateSystem.java](CoordinateSystem.java) |
| 67-03 | 距离计算：Haversine 与近似 | [DistanceCalc.java](DistanceCalc.java) |
| 67-04 | GeoHash：把二维降成一维 | [GeoHash.java](GeoHash.java) |
| 67-05 | 空间索引：R 树与网格 | [SpatialIndex.java](SpatialIndex.java) |
| 67-06 | 附近的人：查询与分页 | [NearByQuery.java](NearByQuery.java) |
| 67-07 | 电子围栏：点在多边形内 | [GeoFence.java](GeoFence.java) |
| 67-08 | 路径规划基础：图与最短路 | [PathPlanning.java](PathPlanning.java) |
| 67-09 | 位置数据模型与存储 | [LocationStorage.java](LocationStorage.java) |
| 67-10 | 位置数据的隐私与合规 | [LocationPrivacy.java](LocationPrivacy.java) |
| 67-11 | 综合案例：外卖配送范围服务 | [DeliveryRangeCase.java](DeliveryRangeCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：附近的门店

**场景**

实现「查找附近 3 公里门店」：经纬度与坐标系转换、Geohash 与网格索引、距离计算、配送范围（多边形）判断、海量点位检索优化。用纯 JDK 手写核心算法。

**任务**
1. 实现距离计算：用 Haversine 公式算球面距离，对比直接算平面欧氏距离的误差，说明什么范围下可以近似。
2. 实现 Geohash：把经纬度编码成字符串前缀，用前缀匹配快速筛候选，说明精度位数与网格大小的对应关系。
3. 实现配送范围判断：判断点是否在多边形内（射线法），处理边界点与自相交多边形。
4. 优化检索：先用网格粗筛再用精确距离精筛，对比全量遍历与网格索引的耗时差异。

**验收清单**
- [ ] 距离计算结果与在线地图工具对比误差在可接受范围（<1%）。
- [ ] Geohash 前缀筛选不漏掉边界附近的点（需查相邻 8 格）。
- [ ] 多边形判断对边界点、凹多边形的结果正确。
- [ ] 网格索引后查询耗时下降一个数量级以上，且结果与全量遍历完全一致。

> 同一道题也写在 [DeliveryRangeCase.java](DeliveryRangeCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第67章-地理空间与位置服务.md](../../../../../../../黑马Java课程笔记/第67章-地理空间与位置服务.md) 的「本章综合练习」一节。
