# 数据结构与算法进阶（com.heima.ch34）

`package com.heima.ch34`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 34-01 | 复杂度分析与均摊分析 | [ComplexityAnalysis.java](ComplexityAnalysis.java) |
| 34-02 | 动态数组与扩容策略 | [DynamicArray.java](DynamicArray.java) |
| 34-03 | 链表：反转、中点、环检测与合并 | [LinkedListDemo.java](LinkedListDemo.java) |
| 34-04 | 栈、队列与单调栈/单调队列 | [StackQueue.java](StackQueue.java) |
| 34-05 | 哈希表原理与冲突处理 | [HashTableDemo.java](HashTableDemo.java) |
| 34-06 | 二叉搜索树、平衡与四种遍历 | [BinarySearchTree.java](BinarySearchTree.java) |
| 34-07 | 堆与优先队列 | [HeapDemo.java](HeapDemo.java) |
| 34-08 | 图：邻接表、BFS/DFS 与拓扑排序 | [GraphDemo.java](GraphDemo.java) |
| 34-09 | 排序算法对比与稳定性 | [SortingCompare.java](SortingCompare.java) |
| 34-10 | 二分查找及其变体 | [BinarySearchVariants.java](BinarySearchVariants.java) |
| 34-11 | 综合案例：LRU 缓存与实时排行榜 | [LruRankingCase.java](LruRankingCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：LRU 缓存与实时排行榜

**场景**

手写两个数据结构：一个是基于哈希表 + 双向链表的 LRU 缓存，一个是基于小顶堆的实时排行榜，最后用二分查找在有序结果中定位名次。复杂度分析要写进注释。

**任务**
1. 手写 LRUCache：get/put 均为 O(1)，容量满时淘汰最久未使用项，用双向链表 + HashMap 实现，不用 LinkedHashMap。
2. 实现排行榜：支持 addOrUpdate(score)、topK(k)，用容量为 k 的小顶堆，说明为什么不是大顶堆。
3. 在有序分数数组中用二分查找定位某个分数的排名，写出左边界与右边界两个变体并说明差异。
4. 为三个结构各写复杂度注释（时间、空间），并用 10 万次操作粗略测量实际耗时验证量级。

**验收清单**
- [ ] LRU 在容量 3 时按访问顺序正确淘汰，get 会刷新使用顺序。
- [ ] topK 结果与全量排序后取前 k 完全一致，10 万条数据下耗时明显低于全排序。
- [ ] 二分查找的左右边界在含重复元素的数组上都正确。
- [ ] 复杂度注释与实际测量量级吻合，能解释偏差原因。

> 同一道题也写在 [LruRankingCase.java](LruRankingCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第34章-数据结构与算法进阶.md](../../../../../../../黑马Java课程笔记/第34章-数据结构与算法进阶.md) 的「本章综合练习」一节。
