# SpringBoot入门（com.heima.ch24）

`package com.heima.ch24`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 24-01 | 为什么需要Spring Boot | [WhySpringBoot.java](WhySpringBoot.java) |
| 24-02 | 第一个Spring Boot工程 | [FirstBootApp.java](FirstBootApp.java) |
| 24-03 | 自动配置原理 | [AutoConfigPrinciple.java](AutoConfigPrinciple.java) |
| 24-04 | 配置文件与Profile | [ConfigAndProfile.java](ConfigAndProfile.java) |
| 24-05 | IOC容器与依赖注入 | [IocContainer.java](IocContainer.java) |
| 24-06 | RESTful接口开发 | [RestApiDemo.java](RestApiDemo.java) |
| 24-07 | 参数校验与统一响应 | [ValidationDemo.java](ValidationDemo.java) |
| 24-08 | 全局异常处理与拦截器 | [ExceptionAndInterceptor.java](ExceptionAndInterceptor.java) |
| 24-09 | 综合案例：整合MyBatis的学生服务 | [StudentServiceCase.java](StudentServiceCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：学生服务 REST 接口

**场景**

用 Spring Boot 搭一个学生服务：分层 Controller/Service/Mapper、统一响应体、参数校验、全局异常处理、Profile 区分环境，最后用 curl 跑通全套接口。

**任务**
1. 搭建工程并实现 REST 接口：GET 列表（分页）、GET 详情、POST 新增、PUT 修改、DELETE 删除，URL 与 HTTP 方法语义正确。
2. 定义统一响应体 Result<T>（code、message、data），用 @RestControllerAdvice 把参数校验异常、业务异常、未知异常统一转换成对应 code。
3. 用 @Valid + JSR-303 注解校验入参（姓名非空、年龄范围、邮箱格式），验证非法入参返回 400 且提示具体字段。
4. 配置 application-dev.yml 与 application-prod.yml，用 --spring.profiles.active 切换，验证不同环境的数据库配置生效。

**验收清单**
- [ ] 五个接口全部用 curl 或 Postman 跑通，返回结构与统一响应体一致。
- [ ] 参数非法时返回 400 且 message 明确指出哪个字段不合法，不返回堆栈。
- [ ] 未知异常返回 500 且日志里有完整堆栈，响应体不泄露内部信息。
- [ ] 切换 Profile 后连接的数据源随之变化，控制台启动日志能看到激活的 profile。

> 同一道题也写在 [StudentServiceCase.java](StudentServiceCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第24章-SpringBoot入门.md](../../../../../../../黑马Java课程笔记/第24章-SpringBoot入门.md) 的「本章综合练习」一节。
