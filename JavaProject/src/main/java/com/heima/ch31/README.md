# 设计模式与重构（com.heima.ch31）

`package com.heima.ch31`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 31-01 | 设计模式概览与六大原则 | [DesignPatternIntro.java](DesignPatternIntro.java) |
| 31-02 | 单例模式：五种写法与两种破坏 | [SingletonPattern.java](SingletonPattern.java) |
| 31-03 | 工厂模式：简单工厂、工厂方法、抽象工厂 | [FactoryPattern.java](FactoryPattern.java) |
| 31-04 | 建造者模式：链式调用与不可变对象 | [BuilderPattern.java](BuilderPattern.java) |
| 31-05 | 代理模式与装饰器模式 | [ProxyDecorator.java](ProxyDecorator.java) |
| 31-06 | 适配器模式与外观模式 | [AdapterFacade.java](AdapterFacade.java) |
| 31-07 | 策略模式与状态模式 | [StrategyState.java](StrategyState.java) |
| 31-08 | 观察者模式与责任链模式 | [ObserverChain.java](ObserverChain.java) |
| 31-09 | 模板方法与回调 | [TemplateCallback.java](TemplateCallback.java) |
| 31-10 | 代码坏味道与重构手法 | [RefactorSmells.java](RefactorSmells.java) |
| 31-11 | 综合案例：优惠计算引擎重构 | [PromotionEngineCase.java](PromotionEngineCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：优惠计算引擎重构

**场景**

给定一段 300 行 if-else 的优惠计算代码，按设计模式重构：策略消除分支、工厂/注册表管理策略、责任链处理叠加规则、建造者构造不可变结果，最后用单例管理全局配置。

**任务**
1. 先读原代码并列出坏味道清单（重复、长方法、魔法数字、难扩展），说明每种坏味道对应哪种重构手法。
2. 把每种优惠（满减、折扣、券、积分抵扣）抽成策略实现类，用 Map 注册表按类型取策略，新增优惠只加类不改主流程。
3. 用责任链把「券 → 满减 → 折扣 → 积分」串起来，每环可决定是否继续，并处理互斥规则。
4. 用建造者模式构造不可变的计算结果对象（原价、优惠明细、实付），并用单例（双重检查或枚举）管理优惠配置。

**验收清单**
- [ ] 重构后主流程代码行数显著下降，且新增一种优惠不需要改任何已有类。
- [ ] 所有原有测试用例在重构后全部通过（行为不变）。
- [ ] 责任链在互斥场景下能正确终止，优惠明细可追溯每一环减了多少。
- [ ] 单例在多线程下只创建一个实例（用并发测试验证）。

> 同一道题也写在 [PromotionEngineCase.java](PromotionEngineCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第31章-设计模式与重构.md](../../../../../../../黑马Java课程笔记/第31章-设计模式与重构.md) 的「本章综合练习」一节。
