# SpringBoot进阶（com.heima.ch26）

`package com.heima.ch26`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 26-01 | AOP：把横切逻辑抽出来 | [AopIntro.java](AopIntro.java) |
| 26-02 | AOP 核心概念与五种通知 | [AdviceTypes.java](AdviceTypes.java) |
| 26-03 | AOP 实战：统一日志与接口耗时 | [LogAspectDemo.java](LogAspectDemo.java) |
| 26-04 | AOP 实战：自定义注解做权限校验 | [PermissionAspect.java](PermissionAspect.java) |
| 26-05 | MyBatis-Plus：消灭样板 CRUD | [MyBatisPlusIntro.java](MyBatisPlusIntro.java) |
| 26-06 | 条件构造器与分页 | [WrapperAndPage.java](WrapperAndPage.java) |
| 26-07 | 文件上传 | [FileUploadDemo.java](FileUploadDemo.java) |
| 26-08 | JWT：无状态令牌 | [JwtDemo.java](JwtDemo.java) |
| 26-09 | 登录鉴权闭环 | [LoginFlow.java](LoginFlow.java) |
| 26-10 | 拦截器与鉴权上下文 | [AuthInterceptor.java](AuthInterceptor.java) |
| 26-11 | 综合案例：用户模块 | [UserModuleCase.java](UserModuleCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：用户模块（AOP + JWT 鉴权）

**场景**

实现完整的用户模块：注册登录、JWT 签发与校验、拦截器解析令牌写入上下文、AOP 记录接口耗时与操作日志、自定义注解做权限校验，最后用 MyBatis-Plus 简化 CRUD。

**任务**
1. 用 MyBatis-Plus 的 BaseMapper 完成用户 CRUD，再用条件构造器写一个按用户名模糊 + 状态筛选 + 分页的查询。
2. 实现登录签发 JWT（含 userId、过期时间、签名），写拦截器校验 token 并放入 ThreadLocal 上下文，未登录访问返回 401。
3. 写 @RequireRole 注解 + AOP 切面，在方法执行前校验当前用户角色，无权限抛业务异常并返回 403。
4. 写一个 @LogCost 注解 + AOP，记录每个接口的耗时与方法名，超过 500ms 打印告警。

**验收清单**
- [ ] 登录后携带 token 访问受保护接口成功，不带或带过期 token 返回 401。
- [ ] 普通用户访问管理员接口返回 403，管理员访问成功。
- [ ] AOP 日志能打印方法名与耗时，切面不侵入业务代码。
- [ ] 分页查询返回总数与当前页数据，参数非法时返回明确错误。

> 同一道题也写在 [UserModuleCase.java](UserModuleCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第26章-SpringBoot进阶.md](../../../../../../../黑马Java课程笔记/第26章-SpringBoot进阶.md) 的「本章综合练习」一节。
