# Java21现代语言特性（com.heima.ch57）

`package com.heima.ch57`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 57-01 | 从 Java 8 到 Java 21 的演进地图 | [JavaEvolution.java](JavaEvolution.java) |
| 57-02 | record：值语义与不可变设计 | [RecordDemo.java](RecordDemo.java) |
| 57-03 | sealed：受限继承与穷举保障 | [SealedDemo.java](SealedDemo.java) |
| 57-04 | 模式匹配：instanceof 与 switch | [PatternMatching.java](PatternMatching.java) |
| 57-05 | 记录模式与嵌套解构 | [RecordPatternDemo.java](RecordPatternDemo.java) |
| 57-06 | 文本块与字符串处理 | [TextBlockDemo.java](TextBlockDemo.java) |
| 57-07 | var 与局部变量类型推断 | [VarDemo.java](VarDemo.java) |
| 57-08 | Sequenced Collections 与集合增强 | [SequencedDemo.java](SequencedDemo.java) |
| 57-09 | Stream 与 Optional 的现代用法 | [StreamModern.java](StreamModern.java) |
| 57-10 | 现代 Java 的迁移策略与兼容性 | [MigrationPlan.java](MigrationPlan.java) |
| 57-11 | 综合案例：用 Java 21 重构订单模块 | [ModernRefactorCase.java](ModernRefactorCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：用 Java 21 重构订单模块

**场景**

把一段 Java 8 风格的订单代码用 Java 21 特性重构：record 表达值对象、sealed 约束状态、switch 模式匹配替代 if 链、文本块写 SQL、var 简化局部变量、Sequenced Collections 统一首尾操作。

**任务**
1. 把 Order、Money、Address 改成 record，说明 record 的不可变语义与适用边界（什么时候不该用 record）。
2. 用 sealed interface 定义订单状态，配合 switch 模式匹配穷举所有状态，验证漏写分支时编译报错。
3. 用记录模式解构嵌套 record（如 Order(Money amount, Address addr)），在 switch 中直接取字段。
4. 用文本块写多行 SQL 与 JSON，用 var 简化局部变量，用 SequencedCollection 的 getFirst/getLast 替代下标计算。

**验收清单**
- [ ] 重构后行为与重构前完全一致（原有测试全通过）。
- [ ] 新增一个状态时，编译器能指出所有需要修改的 switch 位置。
- [ ] record 组件不可变，任何「修改」都返回新对象。
- [ ] 代码中不再出现 get(0)、get(size()-1) 这类下标写法。

> 同一道题也写在 [ModernRefactorCase.java](ModernRefactorCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第57章-Java21现代语言特性.md](../../../../../../../黑马Java课程笔记/第57章-Java21现代语言特性.md) 的「本章综合练习」一节。
