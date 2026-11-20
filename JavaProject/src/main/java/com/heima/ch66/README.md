# JavaAgent与字节码增强（com.heima.ch66）

`package com.heima.ch66`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 66-01 | Agent 全景：premain 与 agentmain | [AgentOverview.java](AgentOverview.java) |
| 66-02 | 类加载与 Instrumentation | [InstrumentationDemo.java](InstrumentationDemo.java) |
| 66-03 | 字节码基础：Class 文件结构 | [ClassFileStructure.java](ClassFileStructure.java) |
| 66-04 | ASM：读取与修改字节码 | [AsmDemo.java](AsmDemo.java) |
| 66-05 | ByteBuddy：更友好的增强 | [ByteBuddyDemo.java](ByteBuddyDemo.java) |
| 66-06 | 无侵入埋点：方法耗时与链路 | [TracingAgent.java](TracingAgent.java) |
| 66-07 | Agent 的边界：不能做什么 | [AgentLimits.java](AgentLimits.java) |
| 66-08 | 热修复与动态诊断 | [HotFixDemo.java](HotFixDemo.java) |
| 66-09 | Agent 的性能与稳定性 | [AgentPerformance.java](AgentPerformance.java) |
| 66-10 | Agent 安全与治理 | [AgentSecurity.java](AgentSecurity.java) |
| 66-11 | 综合案例：自研 APM Agent | [ApmAgentCase.java](ApmAgentCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：无侵入的链路埋点 Agent

**场景**

写一个 Java Agent 做无侵入埋点：premain/agentmain、字节码增强、方法耗时统计、异常捕获、动态 attach 热更新、性能开销评估。要求业务代码零改动。

**任务**
1. 实现 premain 启动挂载：通过 -javaagent 参数加载，打印加载成功的类数量。
2. 实现字节码增强：在目标方法前后插入计时逻辑，统计调用次数、耗时分布（P50/P99），不改业务源码。
3. 实现 agentmain 动态挂载：对运行中的进程 attach，替换增强逻辑并验证立即生效。
4. 评估开销：对比开启/关闭 Agent 时的吞吐与延迟，给出「可接受的开销上限」判断。

**验收清单**
- [ ] 业务代码完全未修改，埋点数据正确输出。
- [ ] 增强后的类能正常加载运行，不出现 VerifyError。
- [ ] 动态 attach 后新逻辑生效，无需重启进程。
- [ ] 开销有量化数据（延迟增加百分比），并说明哪些方法不适合增强。

> 同一道题也写在 [ApmAgentCase.java](ApmAgentCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第66章-JavaAgent与字节码增强.md](../../../../../../../黑马Java课程笔记/第66章-JavaAgent与字节码增强.md) 的「本章综合练习」一节。
