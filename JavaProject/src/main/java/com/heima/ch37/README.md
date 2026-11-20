# Docker与容器化部署（com.heima.ch37）

`package com.heima.ch37`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 37-01 | 容器与虚拟机的区别 | [DockerVsVm.java](DockerVsVm.java) |
| 37-02 | 镜像与分层存储 | [ImageLayers.java](ImageLayers.java) |
| 37-03 | Dockerfile 与构建过程 | [DockerfileDemo.java](DockerfileDemo.java) |
| 37-04 | 容器的生命周期 | [ContainerLifecycle.java](ContainerLifecycle.java) |
| 37-05 | 网络模式与端口映射 | [DockerNetwork.java](DockerNetwork.java) |
| 37-06 | 数据卷与持久化 | [DockerVolume.java](DockerVolume.java) |
| 37-07 | Docker Compose 与多容器编排 | [ComposeDemo.java](ComposeDemo.java) |
| 37-08 | Java 应用的容器化实践 | [JavaContainerize.java](JavaContainerize.java) |
| 37-09 | 镜像瘦身与镜像安全 | [ImageSlim.java](ImageSlim.java) |
| 37-10 | Kubernetes 核心概念入门 | [K8sIntro.java](K8sIntro.java) |
| 37-11 | 综合案例：一次完整的容器化发布 | [ContainerizeCase.java](ContainerizeCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：一次完整的容器化发布

**场景**

把一个 Java 服务容器化并完成发布：写 Dockerfile、分层缓存优化、多阶段构建瘦身、Compose 编排应用与依赖、端口映射与数据卷、滚动更新与回滚。没有 Docker 环境时用命令序列与状态推演验证。

**任务**
1. 写 Dockerfile：基础镜像选择、依赖层与代码层分离以利用缓存、非 root 用户运行、暴露端口、健康检查。
2. 用多阶段构建把镜像从完整 JDK 瘦身到 JRE 或分层镜像，对比前后体积并说明每一层删掉了什么。
3. 写 docker-compose.yml 编排应用 + 数据库，配置端口映射、数据卷持久化、依赖启动顺序与健康检查。
4. 设计滚动更新方案：新旧版本共存、健康检查通过才切流量、失败自动回滚，写出每一步命令与预期结果。

**验收清单**
- [ ] Dockerfile 改动代码后重建只重跑代码层，依赖层命中缓存。
- [ ] 镜像体积相比单阶段构建下降明显，且运行时不使用 root。
- [ ] Compose 一条命令拉起全部服务，数据卷重建容器后数据不丢。
- [ ] 滚动更新过程中服务不中断（连续请求无 5xx），回滚能在分钟级完成。

> 同一道题也写在 [ContainerizeCase.java](ContainerizeCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第37章-Docker与容器化部署.md](../../../../../../../黑马Java课程笔记/第37章-Docker与容器化部署.md) 的「本章综合练习」一节。
