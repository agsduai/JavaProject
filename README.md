# 黑马 2026 版 AI+Java 课程学习资料

> 课程来源：B站 [BV1TJxCzSEEZ](https://www.bilibili.com/video/BV1TJxCzSEEZ)（黑马程序员，阿玮老师）

## 目录

- [JavaProject](JavaProject/README.md) —— **可运行 Java 工程**（遵循 Java 命名规范）
  - 第 2~80 章共 843 课，一课一个 `.java` 文件，放在 `com.heima.ch02` ~ `com.heima.ch80` 包下；
  - 每课文件头：课程来源、课号、课名、知识点注释；
  - 正文：完整示例代码；
  - 文件末尾：课后练习题注释块（无参考答案）；
  - 另含 107 道分章毕业实操题 + 9 套综合卷，共 116 个实操题文件（`com.heima.practice.*` 包，覆盖全部 52 章）。

> 官方视频到第 14 章结束，`ch15` ~ `ch80`（集合框架、IO流与File、多线程、网络编程、反射与注解、MySQL基础、JDBC、Maven与项目构建、MyBatis、Spring Boot 入门、Redis 与缓存、Spring Boot 进阶、项目实战、消息队列与异步、微服务入门、JVM 与性能调优、设计模式与重构、并发编程进阶、测试与质量保障、数据结构与算法进阶、计算机网络与 HTTP 协议、安全编码与漏洞防护、Docker与容器化部署、Elasticsearch与全文搜索、海量数据与分库分表、Java AI 应用开发入门、CI/CD与发布工程、可观测性平台、实时计算与流处理、AI 进阶 Agent 与本地模型、分布式一致性与共识协议、湖仓一体与数据平台、云原生进阶 Mesh 与 Serverless、推理工程与模型服务、领域驱动设计与架构模式、响应式编程与虚拟线程、支付与资金系统设计、毕业总纲系统设计与技术决策、Spring Cloud Alibaba 生态实战、推荐系统入门、多租户 SaaS 架构、稳定性工程与容量管理、Java 21 现代语言特性、规则引擎与实时风控、大数据离线计算与 Spark 原理、开源框架源码精读、分布式任务调度与工作流引擎、Kafka 存储与消费原理、数据仓库维度建模、开放平台与 API 治理、IM 即时通讯系统设计、Java Agent 与字节码增强、地理空间与位置服务、数据安全与隐私工程、广告系统与实时竞价、国际化与本地化工程、低代码与元数据驱动平台、GraalVM 与原生镜像、文件服务与对象存储工程、企业权限中台与 SSO、GraphQL 与 BFF、报表导出与文档生成、数据库内核与 SQL 调优、事件驱动架构与事务消息、研发效能与内部开发者平台、性能压测与 Profiling）为按同一格式补充的扩展章节，用于衔接后续框架课程。

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

本工程使用 JDK 21，959 个文件全部编译校验通过（`javac --release 21 -Xlint:all`，0 error），其中 843 课 + 116 个实操题；第 77~80 章 44 课冒烟 44/44 通过，此前各批次课程与实操冒烟亦全部通过。

## 目录与版本控制约定

仓库只跟踪「源」，不跟踪「产物」：

| 纳入版本控制 | 说明 |
| --- | --- |
| `JavaProject/src/main/java/**/*.java` | 全部课程源码 |
| `JavaProject/pom.xml`、`JavaProject/README.md`、`JavaProject/.idea/*.xml` | 工程与 IDE 共享配置 |
| `_build/*.py`、`_build/_chNN_*.md`、`_build/ch13_deps.txt`、`_build/compile_report.txt` | 生成脚本、章节源稿、编译报告 |
| `黑马Java课程笔记/*.md` | 分章笔记 |
| `demo/.gitkeep`、`_build/demo/.gitkeep` | 仅占位，保证 IO 示例的 `demo/` 目录存在 |

被忽略（`.gitignore`）：`*.class`、`target/`、`_build/out/`、`_build/out_pkg/`、`demo/` 下的运行期文件、`_build/compile_out.txt` 等临时日志。

> 说明：第 16/18 章的 IO 示例以相对路径 `demo/` 读写文件，`FileOutputStream` 不会自动创建父目录，所以 `demo/` 目录本身用 `.gitkeep` 占位保留，目录内文件全部由示例运行时生成、不入库。

