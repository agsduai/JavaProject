# 测试与质量保障（com.heima.ch33）

`package com.heima.ch33`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 33-01 | 单元测试基础：从手写 main 到测试框架 | [UnitTestBasics.java](UnitTestBasics.java) |
| 33-02 | 测试替身：Stub、Mock、Spy、Fake | [TestDoubles.java](TestDoubles.java) |
| 33-03 | 测试用例设计：等价类、边界值与参数化 | [TestDesignCases.java](TestDesignCases.java) |
| 33-04 | 覆盖率：行覆盖、分支覆盖与未覆盖分支分析 | [CoverageMetrics.java](CoverageMetrics.java) |
| 33-05 | 打桩与交互验证：手写一个 MiniMockito | [MockStubbing.java](MockStubbing.java) |
| 33-06 | 测试分层：单元、集成与端到端 | [TestPyramid.java](TestPyramid.java) |
| 33-07 | 测试数据构建：ObjectMother、Builder 与夹具清理 | [TestDataBuilder.java](TestDataBuilder.java) |
| 33-08 | 异常契约、参数校验与并发测试 | [ExceptionContract.java](ExceptionContract.java) |
| 33-09 | 静态检查：规范、坏味道与自定义规则引擎 | [StaticAnalysis.java](StaticAnalysis.java) |
| 33-10 | CI 流水线与质量门禁 | [CiPipeline.java](CiPipeline.java) |
| 33-11 | 综合案例：为订单模块搭建质量保障体系 | [QualitySystemCase.java](QualitySystemCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：为订单模块搭建质量保障体系

**场景**

给订单模块补一套质量保障：手写测试运行器跑单测、用测试替身隔离依赖、设计等价类与边界用例、统计覆盖率、静态检查圈复杂度、接进 CI 门禁。

**任务**
1. 写一个基于反射的 MiniRunner：识别 @Test 注解方法、独立执行、统计通过/失败/跳过，失败时打印异常栈。
2. 用动态代理手写 Stub 与 Mock：为库存服务造一个固定返回的 Stub，为通知服务造一个能验证调用次数的 Mock。
3. 为「计算订单应付金额」设计用例：等价类（正常、边界、异常）+ 边界值（0 件、最大件数、折扣临界），并参数化跑一遍。
4. 写静态检查脚本：统计每个方法的圈复杂度与重复代码块，超过阈值的给出告警，并把它作为提交前门禁。

**验收清单**
- [ ] MiniRunner 能正确区分通过/失败/跳过，失败用例不中断后续执行。
- [ ] Mock 能断言「通知方法被调用了 1 次」，次数不符时测试失败。
- [ ] 用例覆盖了金额为 0、数量为 0、折扣恰好等于阈值等边界，全部通过。
- [ ] 静态检查能指出圈复杂度超标的方法，门禁脚本在超标时返回非 0 退出码。

> 同一道题也写在 [QualitySystemCase.java](QualitySystemCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第33章-测试与质量保障.md](../../../../../../../黑马Java课程笔记/第33章-测试与质量保障.md) 的「本章综合练习」一节。
