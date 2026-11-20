# 语法篇（com.heima.ch02）

`package com.heima.ch02`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 2-01 | 字面量 | [Literal.java](Literal.java) |
| 2-02 | 变量 | [Variable.java](Variable.java) |
| 2-03 | 变量练习-街头霸王 | [StreetFighter.java](StreetFighter.java) |
| 2-04 | 变量的注意事项 | [VariableNotes.java](VariableNotes.java) |
| 2-05 | 计算机的存储规则 | [MemoryStorageRule.java](MemoryStorageRule.java) |
| 2-06 | 数据类型 | [DataType.java](DataType.java) |
| 2-07 | 数据类型练习-计算BMI | [BmiCalculator.java](BmiCalculator.java) |
| 2-08 | 标识符 | [Identifier.java](Identifier.java) |
| 2-09 | 键盘录入 | [KeyboardInput.java](KeyboardInput.java) |
| 2-10 | 键盘录入练习-两数之和 | [TwoNumberSum.java](TwoNumberSum.java) |
| 2-11 | 键盘录入练习-BMI | [BmiInput.java](BmiInput.java) |
| 2-12 | 算数运算符 | [ArithmeticOperator.java](ArithmeticOperator.java) |
| 2-13 | 运算符练习-数值拆分 | [NumberSplit.java](NumberSplit.java) |
| 2-14 | 运算符练习-时间转换 | [TimeConverter.java](TimeConverter.java) |
| 2-15 | 类型转换 | [TypeConversion.java](TypeConversion.java) |
| 2-16 | 字符运算 | [CharOperation.java](CharOperation.java) |
| 2-17 | 字符串拼接操作 | [StringConcat.java](StringConcat.java) |
| 2-18 | 自增自减运算符 | [IncrementDecrement.java](IncrementDecrement.java) |
| 2-19 | 赋值运算符 | [AssignmentOperator.java](AssignmentOperator.java) |
| 2-20 | 关系运算符 | [RelationalOperator.java](RelationalOperator.java) |
| 2-21 | 逻辑运算符 | [LogicalOperator.java](LogicalOperator.java) |
| 2-22 | 短路逻辑运算符 | [ShortCircuitOperator.java](ShortCircuitOperator.java) |
| 2-23 | 三元运算符 | [TernaryOperator.java](TernaryOperator.java) |
| 2-24 | 运算符的优先级 | [OperatorPrecedence.java](OperatorPrecedence.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：便利店收银台小票

**场景**

社区便利店要上一个控制台收银小工具：店员依次录入商品单价、数量、会员等级，程序算出小计、折扣、实付，并打印一张对齐的小票。全程只用本课学的变量、数据类型、键盘录入、运算符和格式化输出。

**任务**
1. 用 Scanner 录入单价（double）、数量（int）、会员等级（char，用 next().charAt(0) 读取，避免残留换行），并说明为什么不能先 nextInt() 再 nextLine()。
2. 按 A 级 8 折、B 级 9 折、C 级无折扣计算小计、优惠金额、实付金额，全部用 printf 保留两位小数输出。
3. 增加「满 100 减 15」：折扣后金额满 100 再减 15，输出最终实付与累计优惠，用三元运算符写判断。
4. 用变量存一位整数金额，分别取出个位、十位、百位打印，验证数值拆分与整除取余的配合。

**验收清单**
- [ ] 编译零错误，运行不抛异常，输入 0 元或 0 件时金额显示 0.00 而不是报错。
- [ ] 小票四列（项目、数量、单价、金额）右对齐，小数统一两位。
- [ ] A/B/C 三档折扣各跑一遍，优惠金额与实付金额相加等于小计。
- [ ] 代码中不出现魔法数字，折扣率与满减阈值都定义成具名常量。

> 同一道题也写在 [OperatorPrecedence.java](OperatorPrecedence.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第02章-语法篇.md](../../../../../../../黑马Java课程笔记/第02章-语法篇.md) 的「本章综合练习」一节。
