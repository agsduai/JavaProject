# 面向对象（com.heima.ch08）

`package com.heima.ch08`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 8-01 | 什么是面向对象？（面向过程 vs 面向对象思想） | [WhatIsOop.java](WhatIsOop.java) |
| 8-02 | 类和对象 | [ClassAndObject.java](ClassAndObject.java) |
| 8-03 | 面向对象中的小细节 | [OopDetails.java](OopDetails.java) |
| 8-04 | 面向对象中数据安全的问题 | [DataSecurity.java](DataSecurity.java) |
| 8-05 | 数据安全的练习：学生 | [StudentSecurity.java](StudentSecurity.java) |
| 8-06 | 就近原则和this关键字 | [ThisKeyword.java](ThisKeyword.java) |
| 8-07 | 构造方法 | [Constructor.java](Constructor.java) |
| 8-08 | 构造方法的注意事项 | [ConstructorNotes.java](ConstructorNotes.java) |
| 8-09 | 面向对象小结 | [OopSummary.java](OopSummary.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：图书借阅系统的领域对象

**场景**

为社区图书角做一个最小领域模型：Book 与 Reader 两个类，封装字段、提供构造方法、用 this 区分同名变量，并在借书时做数据安全校验。这一题只做对象，不做界面。

**任务**
1. 定义 Book 类：私有字段 isbn、title、price、borrowed，提供无参构造、全参构造、getter/setter，price 为负时 set 直接拒绝并保持原值。
2. 定义 Reader 类：字段 name、maxBooks、borrowedCount，提供 borrow(Book) 与 giveBack(Book) 方法，超过上限或书已被借出时给出提示且不改变状态。
3. 在构造方法里用 this 区分同名字段，并说明「就近原则」在哪个位置会出错。
4. 在 main 中构造 2 个读者、3 本书，跑一遍借书 → 重复借 → 超限 → 归还 → 再借的完整流程。

**验收清单**
- [ ] 所有字段 private，外部只能通过方法改状态。
- [ ] 非法 price 赋值后原值不变，控制台有明确拒绝提示。
- [ ] 同一本书不能被同一读者重复借出，borrowed 状态与实际一致。
- [ ] toString() 输出包含书名与借阅状态，便于打印验证。

> 同一道题也写在 [OopSummary.java](OopSummary.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第08章-面向对象.md](../../../../../../../黑马Java课程笔记/第08章-面向对象.md) 的「本章综合练习」一节。
