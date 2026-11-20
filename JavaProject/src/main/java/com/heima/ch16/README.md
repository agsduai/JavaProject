# IO流与File（com.heima.ch16）

`package com.heima.ch16`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 16-01 | File类的创建与判断 | [FileCreateJudge.java](FileCreateJudge.java) |
| 16-02 | File的遍历与删除 | [FileTraversalDelete.java](FileTraversalDelete.java) |
| 16-03 | 字节流读取与写出 | [ByteStream.java](ByteStream.java) |
| 16-04 | 字符流读取与写出 | [CharStream.java](CharStream.java) |
| 16-05 | 缓冲流 | [BufferedStream.java](BufferedStream.java) |
| 16-06 | 转换流与编码 | [ConversionStream.java](ConversionStream.java) |
| 16-07 | 对象流与序列化 | [ObjectStream.java](ObjectStream.java) |
| 16-08 | Properties配置文件 | [PropertiesConfig.java](PropertiesConfig.java) |
| 16-09 | NIO与Files工具类 | [NioFiles.java](NioFiles.java) |
| 16-10 | IO综合案例：文件内容统计与批量重命名 | [IoCase.java](IoCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：日志文件统计与批量重命名

**场景**

给一个目录下的日志文件做一次体检：统计行数、按级别（INFO/WARN/ERROR）分类计数、找出出现最多的错误信息，再把文件名批量加上日期前缀。File、缓冲流、Properties 与 Files 都要用上。

**任务**
1. 用 File 遍历目录，过滤出 .log 文件，输出文件名、大小、最后修改时间，目录不存在时给出提示而不是抛异常。
2. 用 BufferedReader 逐行读取，按级别计数，统计总行数与 ERROR 占比，结果用 printf 对齐输出。
3. 把出现次数最多的前 3 条 ERROR 信息存入 Map 并排序输出，说明为什么用 LinkedHashMap 与 TreeMap 结果不同。
4. 从 config.properties 读取日期前缀配置，用 Files.move 批量重命名文件，重名时自动加序号避免覆盖。

**验收清单**
- [ ] 空目录、单文件、含子目录三种情况都能正常处理。
- [ ] 行数统计与 wc -l 结果一致，级别计数之和等于总行数。
- [ ] 批量重命名后原文件名可在日志中追溯，不会静默覆盖已有文件。
- [ ] 所有流都用 try-with-resources 关闭，没有资源泄漏。

> 同一道题也写在 [IoCase.java](IoCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第16章-IO流与File.md](../../../../../../../黑马Java课程笔记/第16章-IO流与File.md) 的「本章综合练习」一节。
