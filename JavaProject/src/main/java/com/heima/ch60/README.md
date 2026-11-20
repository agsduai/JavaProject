# 开源框架源码精读（com.heima.ch60）

`package com.heima.ch60`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 60-01 | 为什么读源码：方法与路线 | [ReadSourceMethod.java](ReadSourceMethod.java) |
| 60-02 | Spring IOC 容器：BeanDefinition 与实例化 | [IocContainerDemo.java](IocContainerDemo.java) |
| 60-03 | Spring Bean 生命周期与循环依赖 | [BeanLifecycle.java](BeanLifecycle.java) |
| 60-04 | Spring AOP：代理创建与拦截链 | [AopProxyChain.java](AopProxyChain.java) |
| 60-05 | Spring 事务：代理与传播行为 | [TransactionDemo.java](TransactionDemo.java) |
| 60-06 | Netty：主从 Reactor 线程模型 | [NettyReactorDemo.java](NettyReactorDemo.java) |
| 60-07 | Netty：ByteBuf 与零拷贝 | [ByteBufDemo.java](ByteBufDemo.java) |
| 60-08 | Tomcat：连接器与容器 | [TomcatDemo.java](TomcatDemo.java) |
| 60-09 | MyBatis：SqlSession 与执行器 | [MybatisDemo.java](MybatisDemo.java) |
| 60-10 | 日志框架：SLF4J 桥接与异步 | [LoggingDemo.java](LoggingDemo.java) |
| 60-11 | 综合案例：一次 HTTP 请求的框架穿越 | [FrameworkJourney.java](FrameworkJourney.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：一次 HTTP 请求的框架穿越

**场景**

跟着一个 HTTP 请求穿过框架：Tomcat 连接器接收 → Spring IOC 找到 Bean → AOP 拦截 → 事务代理 → MyBatis 执行 SQL → 日志框架输出。每一步都要说出「框架在这里做了什么、用了什么设计模式」。

**任务**
1. 梳理 Tomcat 的连接器与容器结构：Acceptor、Poller、Worker 各自职责，说明为什么用主从 Reactor。
2. 梳理 Spring IOC 的启动流程：BeanDefinition 加载 → BeanFactoryPostProcessor → 实例化 → 属性填充 → 初始化 → 后置处理，指出循环依赖在哪个环节被解决。
3. 梳理 AOP 代理创建与拦截链：什么时候用 JDK 动态代理、什么时候用 CGLIB，多个切面的执行顺序如何确定。
4. 梳理 MyBatis 的 SqlSession 与执行器：一级缓存、二级缓存、插件拦截点（Executor/StatementHandler），说明为什么分页插件能生效。

**验收清单**
- [ ] 每个环节都能指出对应的核心类名与方法，不是泛泛而谈。
- [ ] 能画出 Bean 生命周期图并标注扩展点（BeanPostProcessor 等）。
- [ ] 能解释「同类内部方法调用 AOP 不生效」的原因，并给出解决方案。
- [ ] 能说明 Netty 的 ByteBuf 与零拷贝在什么场景下真正省了内存拷贝。

> 同一道题也写在 [FrameworkJourney.java](FrameworkJourney.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第60章-开源框架源码精读.md](../../../../../../../黑马Java课程笔记/第60章-开源框架源码精读.md) 的「本章综合练习」一节。
