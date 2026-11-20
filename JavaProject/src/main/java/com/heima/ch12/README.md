# 常见API（com.heima.ch12）

`package com.heima.ch12`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 12-01 | API和API帮助文档 | [ApiDoc.java](ApiDoc.java) |
| 12-02 | 字符串的概述 | [StringOverview.java](StringOverview.java) |
| 12-03 | 字符串的比较、遍历和两个小练习 | [StringCompare.java](StringCompare.java) |
| 12-04 | 字符串的截取、替换和其他常见方法 | [StringSubstring.java](StringSubstring.java) |
| 12-05 | StringBuilder | [StringBuilderDemo.java](StringBuilderDemo.java) |
| 12-06 | 字符串的四个算法小练习 | [StringAlgorithmPractice.java](StringAlgorithmPractice.java) |
| 12-07 | 算法练习-字符串计算（难） | [StringCalculation.java](StringCalculation.java) |
| 12-08 | 字符串的作业 | [StringHomework.java](StringHomework.java) |
| 12-09 | ArrayList集合 | [ArrayListDemo.java](ArrayListDemo.java) |
| 12-10 | ArrayList练习 | [ArrayListPractice.java](ArrayListPractice.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：通讯录与文本清洗

**场景**

做一个控制台通讯录：用 ArrayList 存联系人，支持增删改查与按姓名排序；再做一轮文本处理，统计敏感词出现次数并把手机号中间四位打码。String 与 ArrayList 的常用 API 要全部覆盖。

**任务**
1. 定义 Contact 类（name、phone、group），用 ArrayList<Contact> 实现增删改查，删除时按下标与按对象两种方式各写一遍。
2. 实现按姓名排序（String 的 compareTo），并说明为什么不直接用 == 比较字符串。
3. 实现敏感词统计：给定一段文本与词表，用 indexOf 循环计数，输出每个词的出现次数。
4. 实现手机号打码：11 位手机号保留前 3 后 4，中间用 **** 替换，用 StringBuilder 拼接；长度非 11 位时原样返回。

**验收清单**
- [ ] 增删改查四个操作各有输出验证，删除不存在的元素有提示且不报错。
- [ ] 排序后姓名按字典序升序，中文姓名也能稳定排序。
- [ ] 敏感词计数与人工核对一致，重叠出现的情况有说明。
- [ ] 打码方法对空串、短串、超长串都不抛异常。

> 同一道题也写在 [ArrayListPractice.java](ArrayListPractice.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第12章-常见API.md](../../../../../../../黑马Java课程笔记/第12章-常见API.md) 的「本章综合练习」一节。
