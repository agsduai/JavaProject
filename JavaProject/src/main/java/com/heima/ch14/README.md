# 常用类API（com.heima.ch14）

`package com.heima.ch14`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 14-01 | Math类和两道算法题 | [MathClass.java](MathClass.java) |
| 14-02 | System类和Object类 | [SystemObjectClass.java](SystemObjectClass.java) |
| 14-03 | 包装类 | [WrapperClass.java](WrapperClass.java) |
| 14-04 | BigInteger和BigDecimal | [BigIntegerDecimal.java](BigIntegerDecimal.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：金融分期计算器

**场景**

做一个小型分期计算器：本金、期数、年利率由用户输入，输出每期应还、总利息、总还款；同时用 Math 做取整与幂运算、用包装类处理空值、用 BigInteger 演示阶乘溢出。金额一律用 BigDecimal。

**任务**
1. 用 BigDecimal 实现等额本息每期还款额计算，结果保留两位小数并指定 HALF_UP 舍入，说明为什么不能用 double。
2. 用 Math 实现四舍五入到分、向上取整到元、求最大最小值，对比 Math.round 与 BigDecimal.setScale 的差异。
3. 用 Integer 包装类演示自动装箱拆箱，并写出一个 null 拆箱导致 NullPointerException 的例子及修复方式。
4. 用 BigInteger 计算 50! 并与 long 版本对比，说明 long 在哪个阶乘开始溢出。

**验收清单**
- [ ] 分期结果与手工计算误差不超过 0.01 元，每期金额之和等于总还款额。
- [ ] 本金为 0、期数为 0、利率为 0 三种边界都有明确处理。
- [ ] null 拆箱的异常能被复现，并在修复后不再出现。
- [ ] 所有金额变量类型统一为 BigDecimal，代码中不出现 double 参与金额运算。

> 同一道题也写在 [BigIntegerDecimal.java](BigIntegerDecimal.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第14章-常用类API.md](../../../../../../../黑马Java课程笔记/第14章-常用类API.md) 的「本章综合练习」一节。
