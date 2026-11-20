# 反射与注解（com.heima.ch19）

`package com.heima.ch19`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 19-01 | 反射入门与获取Class对象 | [ReflectBasicDemo.java](ReflectBasicDemo.java) |
| 19-02 | 获取构造方法并创建对象 | [ReflectConstructor.java](ReflectConstructor.java) |
| 19-03 | 获取成员变量并赋值 | [ReflectField.java](ReflectField.java) |
| 19-04 | 获取成员方法并调用 | [ReflectMethod.java](ReflectMethod.java) |
| 19-05 | 反射综合案例 | [ReflectCase.java](ReflectCase.java) |
| 19-06 | 注解的定义与使用 | [AnnotationDefine.java](AnnotationDefine.java) |
| 19-07 | 反射+注解综合案例 | [AnnotationValidate.java](AnnotationValidate.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：迷你依赖注入容器与校验框架

**场景**

用反射 + 注解手写两个小框架：一个能扫描类、读取注解、创建对象并注入字段的迷你容器；一个能读取字段上的校验注解并在运行时拦截非法值的校验器。

**任务**
1. 自定义 @Component 与 @Inject 注解，用反射扫描指定包下的类，实例化带 @Component 的类并按类型注入带 @Inject 的字段。
2. 自定义 @NotNull、@MaxLength 注解，写一个 validate(Object) 方法，反射读取字段值与注解参数，收集全部错误信息后一次性返回。
3. 通过反射调用私有方法、修改私有字段，并说明 setAccessible(true) 的作用与风险。
4. 在 main 中定义两个互相依赖的类，验证注入成功；再故意注入一个不存在的类型，观察并处理异常。

**验收清单**
- [ ] 容器能创建对象并把依赖注入进去，注入后调用方法输出正确结果。
- [ ] 校验器一次能返回多条错误，而不是遇到第一条就中断。
- [ ] 非法值被拦截，合法值全部通过，注解参数（如 max）真实生效。
- [ ] 注入失败时抛出有明确信息的异常，不出现 NullPointerException。

> 同一道题也写在 [AnnotationValidate.java](AnnotationValidate.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第19章-反射与注解.md](../../../../../../../黑马Java课程笔记/第19章-反射与注解.md) 的「本章综合练习」一节。
