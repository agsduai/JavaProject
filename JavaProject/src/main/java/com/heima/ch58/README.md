# 规则引擎与实时风控（com.heima.ch58）

`package com.heima.ch58`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 58-01 | 规则引擎全景：从 if-else 到规则平台 | [RuleEngineOverview.java](RuleEngineOverview.java) |
| 58-02 | 规则模型：条件、动作与优先级 | [RuleModel.java](RuleModel.java) |
| 58-03 | 规则表达式与求值器 | [ExpressionEvaluator.java](ExpressionEvaluator.java) |
| 58-04 | 决策表与规则矩阵 | [DecisionTable.java](DecisionTable.java) |
| 58-05 | 规则链与冲突消解 | [RuleChain.java](RuleChain.java) |
| 58-06 | 实时特征与规则计算 | [RealtimeFeatures.java](RealtimeFeatures.java) |
| 58-07 | 名单管理与名单同步 | [ListManagement.java](ListManagement.java) |
| 58-08 | 规则热更新与灰度验证 | [RuleHotUpdate.java](RuleHotUpdate.java) |
| 58-09 | 规则效果评估与误杀治理 | [RuleEvaluation.java](RuleEvaluation.java) |
| 58-10 | 风控决策全链路 | [RiskDecisionChain.java](RiskDecisionChain.java) |
| 58-11 | 综合案例：支付风控规则引擎 | [PaymentRiskEngine.java](PaymentRiskEngine.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：支付风控规则引擎

**场景**

做一个支付风控引擎：规则模型（条件+动作+优先级）、表达式求值、决策表、规则链与冲突消解、名单管理、热更新与灰度、效果评估与误杀治理。

**任务**
1. 定义规则模型：条件（金额、频次、设备、地域）、动作（通过、拒绝、人工审核）、优先级、生效时间，用对象而非硬编码 if 表达。
2. 实现表达式求值器：支持比较、逻辑组合、括号，把规则配置字符串解析成可执行判断，并防止非法表达式导致崩溃。
3. 实现规则链与冲突消解：多条规则命中时按优先级与动作严重度决策（拒绝 > 审核 > 通过），输出命中的全部规则。
4. 实现名单管理：黑名单直接拒绝、白名单跳过规则，支持热更新（改配置不重启）与灰度（只对 10% 流量生效）。

**验收清单**
- [ ] 新增一条规则只需改配置，不需要改代码、不需要重启。
- [ ] 冲突场景下决策结果符合预设优先级，且能追溯命中了哪几条规则。
- [ ] 黑名单命中直接拒绝，白名单用户不触发任何规则。
- [ ] 灰度期间只有目标比例流量走新规则，可随时回滚。

> 同一道题也写在 [PaymentRiskEngine.java](PaymentRiskEngine.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第58章-规则引擎与实时风控.md](../../../../../../../黑马Java课程笔记/第58章-规则引擎与实时风控.md) 的「本章综合练习」一节。
