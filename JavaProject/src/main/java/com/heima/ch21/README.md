# JDBC（com.heima.ch21）

`package com.heima.ch21`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 21-01 | JDBC概述与快速入门 | [JdbcQuickStart.java](JdbcQuickStart.java) |
| 21-02 | Statement执行DDL与DML | [JdbcStatementDml.java](JdbcStatementDml.java) |
| 21-03 | ResultSet遍历与结果封装 | [JdbcResultSetMap.java](JdbcResultSetMap.java) |
| 21-04 | SQL注入与PreparedStatement | [JdbcSqlInjection.java](JdbcSqlInjection.java) |
| 21-05 | PreparedStatement增删改与批量操作 | [JdbcPreparedBatch.java](JdbcPreparedBatch.java) |
| 21-06 | JDBC事务控制 | [JdbcTransaction.java](JdbcTransaction.java) |
| 21-07 | JDBC工具类封装 | [JdbcUtilDemo.java](JdbcUtilDemo.java) |
| 21-08 | DAO模式与学生管理综合案例 | [JdbcDaoCase.java](JdbcDaoCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：学生管理持久层（JDBC + DAO）

**场景**

用纯 JDBC 写一个学生管理持久层：封装工具类拿连接、用 PreparedStatement 防注入、用 ResultSet 映射实体、用事务保证批量导入要么全成功要么全失败，最后按 DAO 模式收口。

**任务**
1. 写 JdbcUtil：静态代码块加载驱动，提供 getConnection / close 方法，连接参数从 Properties 读取而不是硬编码。
2. 实现 StudentDao 的增删改查：insert、deleteById、update、findById、findAll，全部用 PreparedStatement 并正确设置参数。
3. 写一个演示 SQL 注入的对比：分别用 Statement 拼接与 PreparedStatement 参数化执行 ' OR '1'='1，打印两种结果并解释差异。
4. 实现批量导入：addBatch 插入 100 条，中途人为制造主键冲突，验证事务回滚后一条都没进库。

**验收清单**
- [ ] 所有数据库资源都在 finally 或 try-with-resources 中关闭，连续调用 1000 次不出现连接泄漏。
- [ ] 注入演示中 Statement 版本返回全部数据、PreparedStatement 版本返回空，结论清晰。
- [ ] 批量导入失败后表内记录数与导入前一致。
- [ ] DAO 层不出现任何 SQL 字符串拼接用户输入。

> 同一道题也写在 [JdbcDaoCase.java](JdbcDaoCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第21章-JDBC.md](../../../../../../../黑马Java课程笔记/第21章-JDBC.md) 的「本章综合练习」一节。
