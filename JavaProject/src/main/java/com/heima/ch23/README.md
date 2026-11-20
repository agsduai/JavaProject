# MyBatis（com.heima.ch23）

`package com.heima.ch23`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 23-01 | 为什么需要MyBatis | [WhyMyBatis.java](WhyMyBatis.java) |
| 23-02 | 快速入门：SqlSessionFactory与SqlSession | [MyBatisQuickStart.java](MyBatisQuickStart.java) |
| 23-03 | Mapper接口与映射文件 | [MapperProxyDemo.java](MapperProxyDemo.java) |
| 23-04 | 参数传递与占位符 | [ParameterBinding.java](ParameterBinding.java) |
| 23-05 | 结果映射resultMap | [ResultMapDemo.java](ResultMapDemo.java) |
| 23-06 | 动态SQL | [DynamicSqlBuilder.java](DynamicSqlBuilder.java) |
| 23-07 | 注解开发 | [AnnotationMapperDemo.java](AnnotationMapperDemo.java) |
| 23-08 | 关联查询（一对一与一对多） | [AssociationDemo.java](AssociationDemo.java) |
| 23-09 | 缓存机制 | [CacheDemo.java](CacheDemo.java) |
| 23-10 | 综合案例：学生管理持久层 | [StudentMapperCase.java](StudentMapperCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：学生管理持久层（MyBatis）

**场景**

把第 21 章的 JDBC 版本改写成 MyBatis：Mapper 接口 + XML 映射、动态 SQL 多条件查询、resultMap 处理下划线转驼峰、一对一关联查班级，最后验证一级缓存与二级缓存。

**任务**
1. 搭建 SqlSessionFactory（从 mybatis-config.xml 读取），实现 Mapper 接口与 XML 的对应关系，完成增删改查。
2. 写动态 SQL 的 findStudents：姓名模糊、年龄区间、班级可选，用 if + where + foreach 组合，参数为空时不影响其他条件。
3. 用 resultMap 映射 student 表到 Student 对象（含关联的 Clazz 对象），说明 resultType 与 resultMap 的适用差别。
4. 验证缓存：同一 SqlSession 内两次查同一 id 只发一条 SQL（一级）；跨 SqlSession 开启二级缓存后再验证一次，并说明什么情况下缓存会失效。

**验收清单**
- [ ] 所有 Mapper 方法都有对应 SQL，参数传递正确（单参、多参 @Param、对象参数三种写法各出现一次）。
- [ ] 动态 SQL 在任意条件组合下都不出现多余 AND 或 WHERE。
- [ ] 关联查询结果中班级字段完整，不是 null。
- [ ] 控制台日志能看到一级缓存命中（第二次无 SQL），更新操作后缓存被清空。

> 同一道题也写在 [StudentMapperCase.java](StudentMapperCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第23章-MyBatis.md](../../../../../../../黑马Java课程笔记/第23章-MyBatis.md) 的「本章综合练习」一节。
