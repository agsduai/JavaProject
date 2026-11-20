# 低代码与元数据驱动平台（com.heima.ch71）

`package com.heima.ch71`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 71-01 | 低代码全景：从表单到流程 | [LowCodeOverview.java](LowCodeOverview.java) |
| 71-02 | 元数据建模：实体、字段、关系 | [MetadataModeling.java](MetadataModeling.java) |
| 71-03 | 动态表单渲染 | [DynamicForm.java](DynamicForm.java) |
| 71-04 | 元数据驱动的权限 | [MetadataPermission.java](MetadataPermission.java) |
| 71-05 | 规则引擎与低代码联动 | [RuleIntegration.java](RuleIntegration.java) |
| 71-06 | 代码生成 vs 解释执行 | [CodegenVsInterpretation.java](CodegenVsInterpretation.java) |
| 71-07 | 版本与发布管理 | [VersionManagement.java](VersionManagement.java) |
| 71-08 | 多租户与扩展 | [TenantExtension.java](TenantExtension.java) |
| 71-09 | 性能与安全边界 | [LowCodeBoundary.java](LowCodeBoundary.java) |
| 71-10 | API 与集成 | [Integration.java](Integration.java) |
| 71-11 | 综合案例：审批低代码平台 | [ApprovalPlatformCase.java](ApprovalPlatformCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：低代码表单平台

**场景**

做一个元数据驱动的表单平台：表单 schema 定义、动态渲染、字段联动与校验规则、元数据版本与迁移、权限到字段级、运行时解释执行。核心是「改配置不改代码」。

**任务**
1. 设计表单 schema：字段类型、必填、默认值、选项、联动规则（A 为空则 B 隐藏），用 JSON 描述而非硬编码。
2. 实现动态渲染与校验：根据 schema 生成控件、执行校验规则、联动时重算可见性，非法 schema 要有兜底。
3. 实现元数据版本：schema 变更后老数据仍可正确展示（字段改名/删除的兼容），给出迁移脚本思路。
4. 实现字段级权限：同一表单不同角色看到不同字段（隐藏/只读/可编辑），验证越权提交被拒绝。

**验收清单**
- [ ] 新增一个字段或一条联动规则只改 schema，服务不重启即生效。
- [ ] 非法 schema 不会导致页面白屏，有明确错误提示。
- [ ] schema 升级后历史数据打开正常，不出现字段错位。
- [ ] 无权限字段在接口层也被过滤，不只是前端隐藏。

> 同一道题也写在 [ApprovalPlatformCase.java](ApprovalPlatformCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第71章-低代码与元数据驱动平台.md](../../../../../../../黑马Java课程笔记/第71章-低代码与元数据驱动平台.md) 的「本章综合练习」一节。
