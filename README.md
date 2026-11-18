# 黑马 2026 版 AI+Java 课程学习资料

> 课程来源：B站 [BV1TJxCzSEEZ](https://www.bilibili.com/video/BV1TJxCzSEEZ)（黑马程序员，阿玮老师）

## 目录

- [JavaProject](JavaProject/README.md) —— **可运行 Java 工程**（遵循 Java 命名规范）
  - 第 2~56 章共 579 课，一课一个 `.java` 文件，放在 `com.heima.ch02` ~ `com.heima.ch56` 包下；
  - 每课文件头：课程来源、课号、课名、知识点注释；
  - 正文：完整示例代码；
  - 文件末尾：课后练习题注释块（无参考答案）；
  - 另含 107 道分章毕业实操题 + 9 套综合卷，共 116 个实操题文件（`com.heima.practice.*` 包，覆盖全部 52 章）。

> 官方视频到第 14 章结束，`ch15` ~ `ch56`（集合框架、IO流与File、多线程、网络编程、反射与注解、MySQL基础、JDBC、Maven与项目构建、MyBatis、Spring Boot 入门、Redis 与缓存、Spring Boot 进阶、项目实战、消息队列与异步、微服务入门、JVM 与性能调优、设计模式与重构、并发编程进阶、测试与质量保障、数据结构与算法进阶、计算机网络与 HTTP 协议、安全编码与漏洞防护、Docker与容器化部署、Elasticsearch与全文搜索、海量数据与分库分表、Java AI 应用开发入门、CI/CD与发布工程、可观测性平台、实时计算与流处理、AI 进阶 Agent 与本地模型、分布式一致性与共识协议、湖仓一体与数据平台、云原生进阶 Mesh 与 Serverless、推理工程与模型服务、领域驱动设计与架构模式、响应式编程与虚拟线程、支付与资金系统设计、毕业总纲系统设计与技术决策、Spring Cloud Alibaba 生态实战、推荐系统入门、多租户 SaaS 架构、稳定性工程与容量管理）为按同一格式补充的扩展章节，用于衔接后续框架课程。

- [黑马Java课程笔记](黑马Java课程笔记/README.md) —— Markdown 分章笔记与实操题文档
  - 按章节整理的笔记版，适合阅读与检索；
  - 与 JavaProject 内容对应。

## 命名规范

| 成员 | 规则 | 示例 |
| --- | --- | --- |
| 包名 | 全小写、点分隔、域名反写 | `com.heima.ch02`、`com.heima.practice.basic` |
| 类名 | 英文大驼峰 | `Literal`、`BmiCalculator`、`LibraryManagementSystem` |
| 方法名 | 小驼峰 | `main`、`calculateBmi` |
| 变量名 | 小驼峰 | `studentScore`、`orderId` |
| 常量名 | 全大写 + 下划线 | `MAX_AGE_VALUE` |

## 快速开始

请用 IntelliJ IDEA 打开：

```
D:\Javalianxi\JavaProject\pom.xml
```

以 Maven 工程导入，等待依赖索引完成，把 Project SDK 设为 **JDK 21**，然后打开任意课文件，点击 `main` 方法左侧绿色三角运行。

命令行方式：

```bash
cd D:/Javalianxi/JavaProject
javac -encoding UTF-8 -d out $(find src/main/java -name '*.java')
java -cp out com.heima.ch02.Literal
```

本工程使用 JDK 21，695 个文件全部编译校验通过（`javac --release 21 -Xlint:all`，0 error），其中 579 课 + 116 个实操题；第 53~56 章课程 44 课冒烟 44/44 通过，全部新增实操文件冒烟亦 100% 通过。
