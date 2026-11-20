# AI进阶Agent与本地模型（com.heima.ch44）

`package com.heima.ch44`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 44-01 | 从工具调用到 Agent | [AgentIntro.java](AgentIntro.java) |
| 44-02 | ReAct 循环与多步规划 | [ReActLoop.java](ReActLoop.java) |
| 44-03 | Agent 的记忆体系 | [AgentMemory.java](AgentMemory.java) |
| 44-04 | 多智能体协作 | [MultiAgent.java](MultiAgent.java) |
| 44-05 | 本地模型部署与量化 | [LocalLlm.java](LocalLlm.java) |
| 44-06 | 模型微调与 LoRA | [FineTuning.java](FineTuning.java) |
| 44-07 | MCP 与工具生态 | [McpEcosystem.java](McpEcosystem.java) |
| 44-08 | AI 网关与多模型路由 | [AiGateway.java](AiGateway.java) |
| 44-09 | Agent 的安全与权限 | [AgentSecurity.java](AgentSecurity.java) |
| 44-10 | 评测驱动迭代 | [EvalDriven.java](EvalDriven.java) |
| 44-11 | 综合案例：运维 Agent（日志诊断） | [OpsAgentCase.java](OpsAgentCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：运维 Agent：日志诊断

**场景**

做一个能自主排查问题的运维 Agent：ReAct 循环规划步骤、调用工具读日志与指标、记忆分层保存结论、权限校验限制危险操作、评测集验证效果。

**任务**
1. 实现 ReAct 循环：思考 → 选工具 → 执行 → 观察 → 再思考，设置最大步数与终止条件，打印完整推理轨迹。
2. 实现工具集：查日志（按关键词与时间范围）、查指标（CPU/内存/错误率）、查最近变更记录，每个工具有参数校验与超时。
3. 实现记忆分层：短期记忆存当前会话步骤、长期记忆存历史故障结论，检索时按相似度注入，验证第二次遇到同类问题更快。
4. 实现安全护栏：只读工具直接放行，写操作（重启、扩容）必须二次确认，并对工具调用做白名单校验。

**验收清单**
- [ ] 给定一个「CPU 飙高」的模拟故障，Agent 能在 5 步内定位到可疑进程并给出结论。
- [ ] 推理轨迹完整可审计，每一步都能看到用了什么工具、拿到了什么证据。
- [ ] 尝试调用白名单外的工具或写操作时被拦截并记录。
- [ ] 评测集 10 个案例中至少 8 个定位正确，错误案例能说明原因。

> 同一道题也写在 [OpsAgentCase.java](OpsAgentCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第44章-AI进阶Agent与本地模型.md](../../../../../../../黑马Java课程笔记/第44章-AI进阶Agent与本地模型.md) 的「本章综合练习」一节。
