# JavaAI应用开发入门（com.heima.ch40）

`package com.heima.ch40`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 40-01 | 大模型与 Java 开发者的基本概念 | [LlmBasics.java](LlmBasics.java) |
| 40-02 | 调用大模型 API：请求、响应与错误 | [LlmApiCall.java](LlmApiCall.java) |
| 40-03 | Prompt 工程：让模型稳定地干活 | [PromptEngineering.java](PromptEngineering.java) |
| 40-04 | 流式输出与 SSE 协议 | [SseStreaming.java](SseStreaming.java) |
| 40-05 | 多轮对话与上下文管理 | [ChatMemory.java](ChatMemory.java) |
| 40-06 | Function Calling：让模型调用你的代码 | [FunctionCalling.java](FunctionCalling.java) |
| 40-07 | Embedding 与向量检索 | [EmbeddingSearch.java](EmbeddingSearch.java) |
| 40-08 | RAG：检索增强生成 | [RagPipeline.java](RagPipeline.java) |
| 40-09 | LangChain4j 与 Spring AI 的分层思想 | [AiFrameworkDemo.java](AiFrameworkDemo.java) |
| 40-10 | AI 应用的工程化：成本、降级与安全 | [AiEngineering.java](AiEngineering.java) |
| 40-11 | 综合案例：智能客服全链路 | [SmartSupportCase.java](SmartSupportCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：智能客服全链路

**场景**

做一个智能客服：调用大模型 API、设计稳定的 Prompt、流式输出、多轮上下文管理、Function Calling 查订单、Embedding + 向量检索做知识库问答，最后处理成本与降级。

**任务**
1. 封装模型调用：请求体构造、超时与重试、错误码分类处理（限流、超长、内容审核），用 Mock 响应跑通全流程。
2. 设计 Prompt：系统角色 + 约束 + few-shot 示例，用同一批问题对比「无约束」与「有约束」的输出稳定性，记录差异。
3. 实现多轮对话：滑动窗口 + 摘要压缩管理上下文，超长时先摘要旧对话，验证第 10 轮仍能记住第 1 轮的关键信息。
4. 实现 RAG：把知识文档切块、算 Embedding（用简化向量或哈希模拟）、按余弦相似度取 top3 拼进 Prompt；再实现 Function Calling 查订单状态。

**验收清单**
- [ ] 模型返回异常时应用不崩溃，能降级到「转人工」提示。
- [ ] Prompt 加约束后，越界回答（编造政策、承诺赔付）明显减少。
- [ ] 第 10 轮对话仍能正确引用第 1 轮的用户信息。
- [ ] RAG 检索到的片段与问题相关，且回答中能追溯到引用来源。

> 同一道题也写在 [SmartSupportCase.java](SmartSupportCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第40章-JavaAI应用开发入门.md](../../../../../../../黑马Java课程笔记/第40章-JavaAI应用开发入门.md) 的「本章综合练习」一节。
