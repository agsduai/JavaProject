# JVM与性能调优（com.heima.ch30）

`package com.heima.ch30`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 30-01 | JVM 运行时内存结构 | [JvmMemoryLayout.java](JvmMemoryLayout.java) |
| 30-02 | 对象创建与内存布局 | [ObjectLayout.java](ObjectLayout.java) |
| 30-03 | 垃圾回收基础：可达性分析与分代 | [GcBasics.java](GcBasics.java) |
| 30-04 | 垃圾收集器：从 Serial 到 ZGC | [GcCollectors.java](GcCollectors.java) |
| 30-05 | 类加载机制与双亲委派 | [ClassLoading.java](ClassLoading.java) |
| 30-06 | 常用排查工具：jps / jstat / jstack / jmap | [DiagnosticTools.java](DiagnosticTools.java) |
| 30-07 | 内存泄漏排查：从现象到 GC Root 引用链 | [MemoryLeakHunt.java](MemoryLeakHunt.java) |
| 30-08 | CPU 飙高与线程状态分析 | [CpuHotspot.java](CpuHotspot.java) |
| 30-09 | GC 日志分析与参数调优 | [GcLogAnalysis.java](GcLogAnalysis.java) |
| 30-10 | 性能压测与瓶颈定位 | [PerfTuning.java](PerfTuning.java) |
| 30-11 | 线上问题排查综合实战 | [OnlineTroubleshootCase.java](OnlineTroubleshootCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：线上问题排查实战

**场景**

给定一段「接口变慢 + 内存持续上涨」的模拟程序，用 JVM 自带工具完成排查：看内存结构、抓线程栈、分析 GC 日志、定位泄漏对象的引用链，最后给出调优参数与验证结论。

**任务**
1. 用 jps 找到进程、jstat 观察 GC 频率与各代占用，判断是「回收不及时」还是「对象一直被引用」。
2. 用 jstack 抓线程栈，找出处于 BLOCKED 或 RUNNABLE 且吃 CPU 的线程，定位到具体代码行。
3. 用 jmap 导出堆快照并统计对象数量，找出数量异常增长的类，说明如何从 GC Root 反推引用链。
4. 给出调优方案：堆大小、新生代比例、收集器选择、可疑代码修改，并用同样的压测脚本验证前后指标变化。

**验收清单**
- [ ] 能从 GC 日志读出 Young GC 次数、Full GC 次数与每次停顿时间。
- [ ] 定位到的 CPU 热点代码与人为植入的问题代码一致。
- [ ] 堆快照中能指出具体是哪个集合在无限增长，并说明为什么它不会被回收。
- [ ] 调优后 Full GC 次数明显下降或消除，接口 P99 有可量化的改善。

> 同一道题也写在 [OnlineTroubleshootCase.java](OnlineTroubleshootCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第30章-JVM与性能调优.md](../../../../../../../黑马Java课程笔记/第30章-JVM与性能调优.md) 的「本章综合练习」一节。
