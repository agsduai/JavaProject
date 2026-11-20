# Maven与项目构建（com.heima.ch22）

`package com.heima.ch22`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 22-01 | 为什么需要 Maven | [MavenCoordinate.java](MavenCoordinate.java) |
| 22-02 | 安装与仓库配置 | [SettingsReader.java](SettingsReader.java) |
| 22-03 | 标准目录结构与pom.xml | [StandardLayout.java](StandardLayout.java) |
| 22-04 | 坐标依赖与依赖范围 | [DependencyScope.java](DependencyScope.java) |
| 22-05 | 依赖冲突与调解 | [DependencyMediation.java](DependencyMediation.java) |
| 22-06 | 生命周期与常用命令 | [LifecycleRunner.java](LifecycleRunner.java) |
| 22-07 | 聚合与继承（多模块） | [MultiModule.java](MultiModule.java) |
| 22-08 | 综合案例：打一个可执行jar | [ExecutableJar.java](ExecutableJar.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：多模块工程与可执行 jar

**场景**

把单模块工程改造成多模块 Maven 工程：common 放工具、dao 依赖 common、web 依赖 dao，最后打一个能 java -jar 直接跑的可执行包，并解决一次依赖冲突。

**任务**
1. 搭建 parent + 三个子模块的聚合结构，父 pom 用 dependencyManagement 统一版本，子模块只声明依赖不写版本号。
2. 在 dao 模块引入两个间接依赖不同版本的同一个库，制造依赖冲突，用 mvn dependency:tree 定位并用 exclusions 或 dependencyManagement 解决。
3. 配置 maven-shade-plugin 或 assembly 打可执行 jar，写清 Main-Class，验证 java -jar 能启动并读到 resources 下的配置。
4. 说明 compile、provided、runtime、test 四种依赖范围的区别，并各举一个真实使用场景。

**验收清单**
- [ ] mvn clean package 一次成功，三个模块产物齐全。
- [ ] dependency:tree 输出中冲突库只剩一个版本，运行时不出现 NoSuchMethodError。
- [ ] 可执行 jar 在干净目录下 java -jar 能跑起来，不依赖外部 classpath。
- [ ] 父 pom 中没有重复声明版本，子模块 pom 里看不到具体版本号。

> 同一道题也写在 [ExecutableJar.java](ExecutableJar.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第22章-Maven与项目构建.md](../../../../../../../黑马Java课程笔记/第22章-Maven与项目构建.md) 的「本章综合练习」一节。
