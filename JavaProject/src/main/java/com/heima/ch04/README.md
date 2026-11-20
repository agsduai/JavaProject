# 数组（com.heima.ch04）

`package com.heima.ch04`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 4-01 | 什么是数组？ | [WhatIsArray.java](WhatIsArray.java) |
| 4-02 | 数组的学习内容 | [ArrayLearningContent.java](ArrayLearningContent.java) |
| 4-03 | 数组的静态初始化 | [ArrayStaticInit.java](ArrayStaticInit.java) |
| 4-04 | 数组中元素的访问 | [ArrayElementAccess.java](ArrayElementAccess.java) |
| 4-05 | 数组的遍历 | [ArrayTraversal.java](ArrayTraversal.java) |
| 4-06 | 数组的动态初始化 | [ArrayDynamicInit.java](ArrayDynamicInit.java) |
| 4-07 | 数组的常见问题 | [ArrayCommonProblems.java](ArrayCommonProblems.java) |
| 4-08 | 练习1：查找数据 | [SearchData.java](SearchData.java) |
| 4-09 | 练习2：数组中的最大值 | [ArrayMax.java](ArrayMax.java) |
| 4-10 | 练习3：打乱数组中的数据 | [ShuffleArray.java](ShuffleArray.java) |
| 4-11 | 练习4：去除重复元素 | [RemoveDuplicates.java](RemoveDuplicates.java) |
| 4-12 | 练习5：快慢指针 | [FastSlowPointer.java](FastSlowPointer.java) |
| 4-13 | 数组的作业 | [ArrayHomework.java](ArrayHomework.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：班级成绩单分析

**场景**

班主任拿到一个班的 Java 成绩，要做一次完整分析：找出最高分与最低分、算平均分、查某个分数是否存在、去掉重复分数、把成绩打乱后重新排序。数组的初始化、遍历、最值与双指针要在这一题里全部用上。

**任务**
1. 用静态初始化定义成绩数组，遍历输出全部成绩并求和、求平均值。
2. 手写循环求最高分与最低分及其下标，不允许调用任何排序或工具方法。
3. 实现查找：录入一个分数，返回它第一次出现的下标，找不到返回 -1，并说明为什么返回 -1 而不是 0。
4. 用双指针原地去除重复分数（数组已排序），返回去重后的有效长度，并打印前 length 个元素验证。
5. 用 Random 交换法打乱数组，再用冒泡排序恢复升序，对比打乱前后内容。

**验收清单**
- [ ] 数组长度为 0 与长度为 1 两种边界输入都不抛异常。
- [ ] 去重后有效长度正确，且原数组前 length 个元素无重复、顺序保持升序。
- [ ] 打乱后元素集合与打乱前完全一致（个数与取值都不变）。
- [ ] 所有循环边界都使用 arr.length，代码中不出现写死的数组长度。

> 同一道题也写在 [ArrayHomework.java](ArrayHomework.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第04章-数组.md](../../../../../../../黑马Java课程笔记/第04章-数组.md) 的「本章综合练习」一节。
