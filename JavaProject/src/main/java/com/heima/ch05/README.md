# 方法（com.heima.ch05）

`package com.heima.ch05`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 5-01 | 什么是方法？ | [WhatIsMethod.java](WhatIsMethod.java) |
| 5-02 | 三道方法练习 | [MethodPractice.java](MethodPractice.java) |
| 5-03 | 定义方法的小诀窍 | [MethodDesignTips.java](MethodDesignTips.java) |
| 5-04 | 方法的重载和总结 | [MethodOverload.java](MethodOverload.java) |
| 5-05 | 方法的作业 | [MethodHomework.java](MethodHomework.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：个人数据工具箱

**场景**

把前几章散落在 main 里的逻辑收进一个工具类：闰年判断、数组复制、数组转字符串、求平均分、求最值、判断是否包含。这一题的重点不是算法，而是「要不要返回值、要不要形参」的判断与重载设计。

**任务**
1. 实现 isLeapYear(int year)，用 2024、1900、2000、2023 四组数据验证，并说明每组为什么是或不是闰年。
2. 实现 copyArray(int[] arr)，返回内容相同但互不影响的新数组，修改副本后打印原数组证明未被改动。
3. 实现 arrayToString(int[] arr)，输出 [1, 2, 3] 格式；再写一个重载版本 arrayToString(int[] arr, String sep) 支持自定义分隔符。
4. 实现 getAverage(int[] arr) 返回平均分并保留两位小数，空数组时返回 0 而不是抛异常，并说明为什么用 return 提前结束。
5. 给上述每个方法补 Javadoc 文档注释（三斜线开头的块注释），写清功能、@param、@return。

**验收清单**
- [ ] 所有方法都是 static，main 中逐条调用并打印验证结果。
- [ ] copyArray 的副本修改后原数组输出不变。
- [ ] 两个 arrayToString 构成重载（参数列表不同），调用时能按实参自动匹配。
- [ ] 空数组、单元素数组两种边界下都不抛异常。

> 同一道题也写在 [MethodHomework.java](MethodHomework.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第05章-方法.md](../../../../../../../黑马Java课程笔记/第05章-方法.md) 的「本章综合练习」一节。
