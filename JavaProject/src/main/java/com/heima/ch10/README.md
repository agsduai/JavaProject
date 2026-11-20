# 面向对象进阶（com.heima.ch10）

`package com.heima.ch10`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 10-01 | static修饰成员变量 | [StaticVariable.java](StaticVariable.java) |
| 10-02 | static静态变量的内存图 | [StaticMemory.java](StaticMemory.java) |
| 10-03 | static修饰成员方法 | [StaticMethod.java](StaticMethod.java) |
| 10-04 | static关键字的注意事项 | [StaticNotes.java](StaticNotes.java) |
| 10-05 | final关键字 | [FinalKeyword.java](FinalKeyword.java) |
| 10-06 | 枚举 | [EnumDemo.java](EnumDemo.java) |
| 10-07 | 枚举的注意事项 | [EnumNotes.java](EnumNotes.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：静态工具类与订单状态枚举

**场景**

把工具方法收进静态工具类并禁止实例化，再用枚举表达订单状态与合法流转，最后用 final 定义一组业务常量。这一题训练「什么时候该 static、什么时候该 enum」。

**任务**
1. 定义 OrderStatus 枚举（CREATED、PAID、SHIPPED、FINISHED、CANCELED），带中文描述字段与 getter。
2. 实现静态方法 canTransit(OrderStatus from, OrderStatus to)，用 switch 表达合法流转，非法流转返回 false。
3. 定义私有构造方法的工具类 OrderUtil，提供静态方法 formatOrderNo(long id) 把 id 补零到 10 位，并说明为什么工具类不该被 new。
4. 用 static final 定义最大订单金额、默认超时时长等常量，在 main 中演示常量被误改时的编译错误。

**验收清单**
- [ ] 枚举遍历 values() 能打印全部状态及描述。
- [ ] CREATED → PAID → SHIPPED → FINISHED 全部返回 true，PAID → CREATED、FINISHED → PAID 返回 false。
- [ ] 工具类实例化被阻止（构造方法 private），调用方全部走类名.方法。
- [ ] 常量命名全大写加下划线，代码中没有裸露的数字阈值。

> 同一道题也写在 [EnumNotes.java](EnumNotes.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第10章-面向对象进阶.md](../../../../../../../黑马Java课程笔记/第10章-面向对象进阶.md) 的「本章综合练习」一节。
