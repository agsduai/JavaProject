# 判断与循环（com.heima.ch03）

`package com.heima.ch03`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 3-01 | if的第一种格式 | [IfBasic.java](IfBasic.java) |
| 3-02 | 练习：游戏血条计算 | [HealthBarCalc.java](HealthBarCalc.java) |
| 3-03 | if语句的细节 | [IfDetails.java](IfDetails.java) |
| 3-04 | if的第二种格式和练习（if-else） | [IfElse.java](IfElse.java) |
| 3-05 | if的第三种格式和多个经典案例 | [IfElseIf.java](IfElseIf.java) |
| 3-06 | switch语句 | [SwitchStatement.java](SwitchStatement.java) |
| 3-07 | switch的其他知识点 | [SwitchDetails.java](SwitchDetails.java) |
| 3-08 | for循环 | [ForLoop.java](ForLoop.java) |
| 3-09 | for的4道基本练习 | [ForBasicPractice.java](ForBasicPractice.java) |
| 3-10 | for的3道算法题 | [ForAlgorithmPractice.java](ForAlgorithmPractice.java) |
| 3-11 | while循环 | [WhileLoop.java](WhileLoop.java) |
| 3-12 | while的3道算法题 | [WhileAlgorithmPractice.java](WhileAlgorithmPractice.java) |
| 3-13 | dowhile循环 | [DoWhileLoop.java](DoWhileLoop.java) |
| 3-14 | 无限循环 | [InfiniteLoop.java](InfiniteLoop.java) |
| 3-15 | break关键字和综合练习 | [BreakKeyword.java](BreakKeyword.java) |
| 3-16 | continue关键字和综合练习 | [ContinueKeyword.java](ContinueKeyword.java) |
| 3-17 | 练习：猜数字小游戏 | [GuessNumberGame.java](GuessNumberGame.java) |
| 3-18 | 循环嵌套 | [NestedLoop.java](NestedLoop.java) |
| 3-19 | 练习：打印多个复杂图形 | [PrintShapes.java](PrintShapes.java) |
| 3-20 | 练习：打印九九乘法表 | [MultiplicationTable.java](MultiplicationTable.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：健身房打卡与训练统计

**场景**

健身房教练要一个控制台小程序：记录会员一周的每日训练时长，判断是否达标，统计达标天数，并在最后让教练猜一个当日推荐课程编号。分支与循环是本工具的全部骨架。

**任务**
1. 用 for 循环录入 7 天的训练时长（分钟），超过 60 分钟记一次达标，最后输出达标天数与平均时长。
2. 用 if-else if 多分支按平均时长划分等级（<30 待提升、30~59 合格、>=60 优秀），并打印对应建议。
3. 用 switch 把课程编号 1~5 映射成课程名，编号非法时走 default 提示「无此课程」，不允许用 if 链替代。
4. 实现猜课程编号小游戏：Random 生成 1~5，最多猜 3 次，猜中 break 并提示用了第几次，猜错提示偏大偏小，次数用完输出正确答案。

**验收清单**
- [ ] 7 天数据里全达标、全不达标、混合三种情况结果都正确，平均时长保留一位小数。
- [ ] 猜数字连续猜 3 次后自动结束，不会无限循环。
- [ ] 循环嵌套打印一个 5 层直角三角形与九九乘法表，格式对齐。
- [ ] 分支覆盖测试通过：每个 switch 分支与每个 if 分支都至少被执行一次。

> 同一道题也写在 [MultiplicationTable.java](MultiplicationTable.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第03章-判断与循环.md](../../../../../../../黑马Java课程笔记/第03章-判断与循环.md) 的「本章综合练习」一节。
