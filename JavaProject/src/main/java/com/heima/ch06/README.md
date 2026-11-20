# 原理篇（com.heima.ch06）

`package com.heima.ch06`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 6-01 | Java的运行机制 | [JavaRuntimeMechanism.java](JavaRuntimeMechanism.java) |
| 6-02 | 内存和内存地址 | [MemoryAndAddress.java](MemoryAndAddress.java) |
| 6-03 | 变量和方法的内存分配 | [VariableMethodMemory.java](VariableMethodMemory.java) |
| 6-04 | 数组的内存分配 | [ArrayMemory.java](ArrayMemory.java) |
| 6-05 | 数组在方法中传递 | [ArrayPassInMethod.java](ArrayPassInMethod.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：内存图推演实验报告

**场景**

不写业务功能，只做实验：用一组刻意设计的小程序，把栈、堆、方法区的分配过程画出来并写成报告。目标是能凭内存图解释「为什么改了这个变量，那个变量也变了」。

**任务**
1. 写两段代码：一段让两个引用指向同一个数组并修改元素，另一段让第二个引用指向副本，分别画出内存图并解释输出差异。
2. 写方法 swap(int a, int b) 与 swap(int[] arr)，调用后打印结果，说明为什么前者换不了、后者能换。
3. 在方法内把形参重新赋值成新数组，调用后打印原数组，解释引用传递传的到底是什么。
4. 把三组实验的输出与内存图整理成一份对照表（现象 → 内存原因 → 结论）。

**验收清单**
- [ ] 三组实验的输出与内存图一一对应，图上标出栈帧、堆对象、引用箭头。
- [ ] 能准确说出「Java 只有值传递，对象传的是引用的值」这句话在实验中的体现。
- [ ] 报告里每个结论都能被某段可运行代码直接验证。
- [ ] 代码里没有未使用变量，命名能体现是实验用例（如 sharedRef、copyRef）。

> 同一道题也写在 [ArrayPassInMethod.java](ArrayPassInMethod.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第06章-原理篇.md](../../../../../../../黑马Java课程笔记/第06章-原理篇.md) 的「本章综合练习」一节。
