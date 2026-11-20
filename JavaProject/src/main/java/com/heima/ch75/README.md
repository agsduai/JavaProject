# GraphQL与BFF（com.heima.ch75）

`package com.heima.ch75`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 75-01 | 为什么需要 GraphQL | [WhyGraphQL.java](WhyGraphQL.java) |
| 75-02 | Schema 与类型系统 | [SchemaDesign.java](SchemaDesign.java) |
| 75-03 | 查询解析 | [QueryParsing.java](QueryParsing.java) |
| 75-04 | 执行引擎与 Resolver | [ExecutionEngine.java](ExecutionEngine.java) |
| 75-05 | N+1 与 DataLoader | [DataLoaderDemo.java](DataLoaderDemo.java) |
| 75-06 | Mutation 与订阅 | [MutationDemo.java](MutationDemo.java) |
| 75-07 | 错误处理与部分成功 | [ErrorHandling.java](ErrorHandling.java) |
| 75-08 | 深度与复杂度限制 | [QueryComplexity.java](QueryComplexity.java) |
| 75-09 | 安全：内省、批量与注入 | [GraphQLSecurity.java](GraphQLSecurity.java) |
| 75-10 | BFF 与联邦架构 | [BffFederation.java](BffFederation.java) |
| 75-11 | 综合案例：商品 GraphQL 网关 | [ProductGraphqlCase.java](ProductGraphqlCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：GraphQL BFF 层

**场景**

为移动端做 GraphQL BFF：schema 设计、按需取字段解决 over-fetching、DataLoader 解决 N+1、分页与游标、错误处理与部分成功、查询复杂度限制与防滥用。

**任务**
1. 设计 schema：把后端多个 REST 接口聚合成一张图，定义类型、查询、变更，说明为什么移动端适合 GraphQL。
2. 实现 DataLoader：批量合并同一请求内的多次单条查询，验证 N+1 被消除（数据库调用次数下降）。
3. 实现游标分页：用 cursor（如 id 或时间戳）而非 offset 分页，说明深分页时 offset 的性能问题。
4. 实现防护：查询深度限制、复杂度评分、超时中断，防止恶意深嵌套查询打爆后端。

**验收清单**
- [ ] 客户端只取需要的字段，响应体明显小于固定 REST 响应。
- [ ] N+1 被消除，一次列表查询的数据库调用次数可数且很少。
- [ ] 分页翻到深页时性能不衰减，结果不重不漏。
- [ ] 超深嵌套或高复杂度查询被拒绝，错误信息明确。

> 同一道题也写在 [ProductGraphqlCase.java](ProductGraphqlCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第75章-GraphQL与BFF.md](../../../../../../../黑马Java课程笔记/第75章-GraphQL与BFF.md) 的「本章综合练习」一节。
