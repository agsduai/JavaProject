# 推理工程与模型服务（com.heima.ch48）

`package com.heima.ch48`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 48-01 | 推理与训练的分界 | [InferenceBasics.java](InferenceBasics.java) |
| 48-02 | KV Cache 原理 | [KvCache.java](KvCache.java) |
| 48-03 | Continuous Batching | [ContinuousBatching.java](ContinuousBatching.java) |
| 48-04 | PagedAttention 与显存管理 | [PagedAttention.java](PagedAttention.java) |
| 48-05 | 推理加速技术全景 | [InferenceAcceleration.java](InferenceAcceleration.java) |
| 48-06 | 容量规划与 QPS | [ServingCapacity.java](ServingCapacity.java) |
| 48-07 | 流式服务与连接管理 | [ServingStreaming.java](ServingStreaming.java) |
| 48-08 | 高可用与多副本路由 | [ServingHA.java](ServingHA.java) |
| 48-09 | 成本工程 | [InferenceCost.java](InferenceCost.java) |
| 48-10 | 推理可观测 | [InferenceObservability.java](InferenceObservability.java) |
| 48-11 | 综合案例：推理集群的容量与降级演练 | [InferenceCase.java](InferenceCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：推理集群的容量与降级演练

**场景**

为一个模型推理服务做容量规划：估算 QPS 与显存、KV Cache 与批处理、流式输出与连接管理、多副本路由、成本核算，最后做一次过载降级演练。

**任务**
1. 容量估算：给定模型参数量与显存、单请求输入输出长度，算出单卡能承载的并发与 QPS，写出估算公式与假设。
2. 说明 KV Cache 随序列长度增长的关系，以及 Continuous Batching 为什么比静态批处理吞吐更高。
3. 设计流式服务：SSE 分块返回、首 token 延迟与总耗时分别统计、连接超时与客户端断开的清理。
4. 设计过载降级：排队上限、超时快速失败、降级到小模型或缓存答案，并演练请求量翻 5 倍时的表现。

**验收清单**
- [ ] 容量估算结果有公式、有假设、有误差说明，不是拍脑袋的数字。
- [ ] 能解释「为什么长请求会拖慢整批」，并给出长度分桶的缓解方案。
- [ ] 流式接口首 token 延迟明显低于总耗时，客户端断开后服务端资源被释放。
- [ ] 过载演练中服务不崩溃，被拒请求返回明确错误而非超时挂死。

> 同一道题也写在 [InferenceCase.java](InferenceCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第48章-推理工程与模型服务.md](../../../../../../../黑马Java课程笔记/第48章-推理工程与模型服务.md) 的「本章综合练习」一节。
