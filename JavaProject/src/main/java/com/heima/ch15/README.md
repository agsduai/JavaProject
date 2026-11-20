# 集合框架（com.heima.ch15）

`package com.heima.ch15`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 15-01 | 为什么需要集合 | [WhyCollection.java](WhyCollection.java) |
| 15-02 | Collection的常用方法 | [CollectionMethods.java](CollectionMethods.java) |
| 15-03 | List接口与ArrayList | [ListArrayList.java](ListArrayList.java) |
| 15-04 | List的遍历与并发修改异常 | [ListTraversal.java](ListTraversal.java) |
| 15-05 | LinkedList与链表结构 | [LinkedListDemo.java](LinkedListDemo.java) |
| 15-06 | 泛型类与泛型方法 | [GenericClassMethod.java](GenericClassMethod.java) |
| 15-07 | 泛型通配符与上下限 | [GenericWildcard.java](GenericWildcard.java) |
| 15-08 | Set接口与HashSet去重原理 | [HashSetDedup.java](HashSetDedup.java) |
| 15-09 | LinkedHashSet与TreeSet | [LinkedHashSetTreeSet.java](LinkedHashSetTreeSet.java) |
| 15-10 | Map接口与HashMap | [MapHashMap.java](MapHashMap.java) |
| 15-11 | Map的遍历与嵌套集合 | [MapTraversal.java](MapTraversal.java) |
| 15-12 | TreeMap与比较器 | [TreeMapComparator.java](TreeMapComparator.java) |
| 15-13 | Collections工具类与不可变集合 | [CollectionsUtil.java](CollectionsUtil.java) |
| 15-14 | Stream流入门 | [StreamIntro.java](StreamIntro.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：学生成绩多维分析

**场景**

给定一批学生的姓名、班级、三科成绩，用集合完成：按班级分组、按总分排名、去重同名学生、用 Stream 做一次统计，最后输出一份分析报告。

**任务**
1. 用 List<Student> 存数据，用 Map<String, List<Student>> 按班级分组，遍历输出每组人数与平均总分。
2. 用 TreeSet 或排序把学生按总分降序排列，总分相同时按姓名升序，说明比较器怎么写才稳定。
3. 用 HashSet 演示去重：同名同班视为重复，重写 equals 与 hashCode，并验证重写前后去重效果差异。
4. 用 Stream 完成一次链式统计：过滤总分及格 → 按班级分组 → 求每组最高分学生 → 收集成 Map 输出。

**验收清单**
- [ ] 分组结果人数之和等于学生总数，没有遗漏也没有重复计入。
- [ ] 排名结果在总分相同的情况下顺序稳定可复现。
- [ ] 重写 equals/hashCode 后 HashSet 能正确去重，注释说明两者必须同时重写。
- [ ] Stream 链式写法没有副作用，中间操作与终端操作职责清晰。

> 同一道题也写在 [StreamIntro.java](StreamIntro.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第15章-集合框架.md](../../../../../../../黑马Java课程笔记/第15章-集合框架.md) 的「本章综合练习」一节。
