# 数据安全与隐私工程（com.heima.ch68）

`package com.heima.ch68`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 68-01 | 隐私工程全景：从合规到技术 | [PrivacyOverview.java](PrivacyOverview.java) |
| 68-02 | 数据分类分级 | [DataClassification.java](DataClassification.java) |
| 68-03 | 静态脱敏与动态脱敏 | [DataMasking.java](DataMasking.java) |
| 68-04 | 加密与密钥管理 | [EncryptionDemo.java](EncryptionDemo.java) |
| 68-05 | 数据权限：行列级控制 | [DataPermission.java](DataPermission.java) |
| 68-06 | 数据出境与本地化 | [CrossBorderData.java](CrossBorderData.java) |
| 68-07 | 隐私计算入门：差分隐私 | [DifferentialPrivacy.java](DifferentialPrivacy.java) |
| 68-08 | 隐私计算入门：联邦学习概念 | [FederatedLearning.java](FederatedLearning.java) |
| 68-09 | 审计与可追溯 | [AuditTrail.java](AuditTrail.java) |
| 68-10 | 数据删除与遗忘权 | [RightToBeForgotten.java](RightToBeForgotten.java) |
| 68-11 | 综合案例：用户数据全生命周期治理 | [DataGovernanceCase.java](DataGovernanceCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：用户数据合规改造

**场景**

对现有系统做数据合规改造：敏感字段识别与分级、脱敏与加密存储、密钥管理、访问审计、数据导出管控、用户注销与数据删除、日志脱敏。要能回答「数据泄露时影响面多大」。

**任务**
1. 识别敏感字段并分级（手机号/身份证/银行卡/地址），给出每级的存储与展示要求。
2. 实现脱敏：展示层掩码（138****1234）、导出层脱敏、日志层自动脱敏（正则替换），验证日志里查不到明文。
3. 实现加密存储：敏感字段加密入库、查询用索引列（如手机号哈希）检索，说明为什么不能直接对密文做 like 查询。
4. 实现用户注销：级联删除或匿名化、保留必要的交易凭证（法定留存），说明「删除」与「匿名化」的合规差异。

**验收清单**
- [ ] 全链路日志、接口响应、导出文件中均无明文敏感数据。
- [ ] 加密字段可通过哈希索引精确查询，模糊查询有替代方案说明。
- [ ] 注销后用户不可登录、个人信息不可查，但交易凭证按法规保留。
- [ ] 访问敏感数据有审计记录（谁、何时、查了谁的什么字段）。

> 同一道题也写在 [DataGovernanceCase.java](DataGovernanceCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第68章-数据安全与隐私工程.md](../../../../../../../黑马Java课程笔记/第68章-数据安全与隐私工程.md) 的「本章综合练习」一节。
