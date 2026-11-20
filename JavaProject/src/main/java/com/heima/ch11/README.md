# 面向对象高级（com.heima.ch11）

`package com.heima.ch11`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 11-01 | 什么是继承？ | [InheritanceIntro.java](InheritanceIntro.java) |
| 11-02 | 如何设计一个复杂的继承结构 | [InheritanceStructure.java](InheritanceStructure.java) |
| 11-03 | 继承的特点 | [InheritanceFeatures.java](InheritanceFeatures.java) |
| 11-04 | 继承中成员变量的特点 | [InheritedVariable.java](InheritedVariable.java) |
| 11-05 | 继承中成员方法的特点 | [InheritedMethod.java](InheritedMethod.java) |
| 11-06 | 继承中构造方法的特点 | [InheritedConstructor.java](InheritedConstructor.java) |
| 11-07 | 带有继承结构的标准javabean类 | [JavaBeanInheritance.java](JavaBeanInheritance.java) |
| 11-08 | 继承的作业 | [InheritanceHomework.java](InheritanceHomework.java) |
| 11-09 | 子类到底能继承父类的哪些内容？ | [InheritedContent.java](InheritedContent.java) |
| 11-10 | Java中的权限修饰符 | [AccessModifier.java](AccessModifier.java) |
| 11-11 | 什么是多态？ | [PolymorphismIntro.java](PolymorphismIntro.java) |
| 11-12 | 多态调用成员的特点和弊端以及解决方案 | [PolymorphismMembers.java](PolymorphismMembers.java) |
| 11-13 | 多态的综合练习 | [PolymorphismPractice.java](PolymorphismPractice.java) |
| 11-14 | 抽象类和抽象方法 | [AbstractClass.java](AbstractClass.java) |
| 11-15 | 接口和接口中的成员？ | [InterfaceMembers.java](InterfaceMembers.java) |
| 11-16 | 接口和抽象类的综合案例 | [InterfaceAbstractCase.java](InterfaceAbstractCase.java) |
| 11-17 | 接口在JDK8和9的新特性 | [InterfaceNewFeatures.java](InterfaceNewFeatures.java) |
| 11-18 | 内部类 | [InnerClassIntro.java](InnerClassIntro.java) |
| 11-19 | 成员内部类 | [MemberInnerClass.java](MemberInnerClass.java) |
| 11-20 | 静态内部类和匿名内部类 | [StaticAnonymousInnerClass.java](StaticAnonymousInnerClass.java) |
| 11-21 | 匿名内部类 | [AnonymousInnerClass.java](AnonymousInnerClass.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：支付渠道体系

**场景**

设计一套支付渠道：抽象类定义公共流程，接口定义可插拔的支付能力，子类实现微信、支付宝、银行卡三种渠道，再用多态统一调用、用内部类做回调。

**任务**
1. 定义抽象类 AbstractPayChannel，含 final 的 pay(long amount) 模板方法（先校验再扣款再打印回执），以及抽象方法 doPay(long amount)。
2. 定义接口 Refundable 含 refund(long amount)，让微信与银行卡实现、支付宝不实现，用 instanceof 判断并处理不可退款的情况。
3. 用父类引用指向三种子类，放进数组遍历调用 pay，验证多态调用的是子类重写的方法而不是父类。
4. 用匿名内部类实现 PayCallback 接口作为支付完成回调，在 pay 结束后触发并打印结果。

**验收清单**
- [ ] 三种渠道共用同一套模板流程，子类只重写 doPay，没有重复代码。
- [ ] 多态数组遍历时输出各渠道自己的名称，证明动态绑定生效。
- [ ] 对不支持的渠道调用退款时给出友好提示，不抛 ClassCastException。
- [ ] 匿名内部类能访问并打印外部方法的局部变量。

> 同一道题也写在 [AnonymousInnerClass.java](AnonymousInnerClass.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第11章-面向对象高级.md](../../../../../../../黑马Java课程笔记/第11章-面向对象高级.md) 的「本章综合练习」一节。
