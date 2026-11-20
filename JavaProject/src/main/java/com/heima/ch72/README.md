# GraalVM与原生镜像（com.heima.ch72）

`package com.heima.ch72`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 72-01 | 为什么需要原生镜像 | [WhyNativeImage.java](WhyNativeImage.java) |
| 72-02 | AOT 与封闭世界假设 | [ClosedWorld.java](ClosedWorld.java) |
| 72-03 | 反射、资源与代理配置 | [AotConfiguration.java](AotConfiguration.java) |
| 72-04 | 类初始化时机 | [ClassInitialization.java](ClassInitialization.java) |
| 72-05 | Spring Boot 3 AOT 与 native | [SpringAotDemo.java](SpringAotDemo.java) |
| 72-06 | 原生镜像的调试与排障 | [NativeDebugging.java](NativeDebugging.java) |
| 72-07 | 性能特征对比 | [PerformanceCompare.java](PerformanceCompare.java) |
| 72-08 | 与容器/Serverless 结合 | [NativeContainer.java](NativeContainer.java) |
| 72-09 | 构建流水线 | [NativePipeline.java](NativePipeline.java) |
| 72-10 | 迁移评估与决策 | [MigrationDecision.java](MigrationDecision.java) |
| 72-11 | 综合案例：把一个服务改成 native | [NativeMigrationCase.java](NativeMigrationCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：原生镜像迁移评估

**场景**

把订单服务迁移到 GraalVM 原生镜像：构建配置、反射与资源注册、启动时间与内存对比、构建耗时与镜像体积权衡、动态代理与序列化兼容、生产适用性结论。

**任务**
1. 完成一次原生镜像构建：配置 native-image 参数、注册反射类与资源文件，跑通启动与一个接口。
2. 解决兼容问题：处理反射、动态代理、序列化框架的注册，记录每个报错与修复方式。
3. 对比数据：JVM 模式 vs 原生镜像的启动时间、常驻内存、峰值内存、镜像体积、首次请求延迟。
4. 给出适用性结论：什么服务适合原生镜像（短生命周期/弹性伸缩），什么服务不适合（大量反射/JIT 预热收益大）。

**验收清单**
- [ ] 原生镜像能正常启动并完成核心接口调用。
- [ ] 反射/资源相关的运行时报错全部解决，有配置清单。
- [ ] 对比数据完整，启动时间与内存改善有量化结论。
- [ ] 结论指出构建耗时与调试成本的代价，不是一边倒推荐。

> 同一道题也写在 [NativeMigrationCase.java](NativeMigrationCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第72章-GraalVM与原生镜像.md](../../../../../../../黑马Java课程笔记/第72章-GraalVM与原生镜像.md) 的「本章综合练习」一节。
