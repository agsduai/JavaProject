# 企业权限中台与SSO（com.heima.ch74）

`package com.heima.ch74`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 74-01 | 认证与授权全景 | [AuthOverview.java](AuthOverview.java) |
| 74-02 | SSO 与 OIDC 流程 | [SsoOidc.java](SsoOidc.java) |
| 74-03 | 组织架构与用户体系 | [OrgStructure.java](OrgStructure.java) |
| 74-04 | RBAC：用户-角色-权限模型 | [RbacModel.java](RbacModel.java) |
| 74-05 | 权限模型进阶：ABAC 与数据权限 | [AbacAndDataScope.java](AbacAndDataScope.java) |
| 74-06 | 权限树与菜单渲染 | [PermissionTree.java](PermissionTree.java) |
| 74-07 | 权限缓存与失效 | [PermissionCache.java](PermissionCache.java) |
| 74-08 | 越权防护与权限测试 | [AuthzTesting.java](AuthzTesting.java) |
| 74-09 | 多租户与权限隔离 | [MultiTenantAuthz.java](MultiTenantAuthz.java) |
| 74-10 | 权限变更审计 | [AuthzAudit.java](AuthzAudit.java) |
| 74-11 | 综合案例：统一权限中台 | [AuthPlatformCase.java](AuthPlatformCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：企业权限中台

**场景**

设计企业权限中台：RBAC 与 ABAC 结合、SSO 单点登录（OIDC/SAML 思路）、组织架构与数据权限、会话与令牌、权限变更实时生效、审计。要能回答「张三能不能看这个订单」。

**任务**
1. 设计权限模型：用户-角色-权限 + 资源属性（部门、区域），用 RBAC 管功能权限、ABAC 管数据权限，给出判断流程。
2. 实现 SSO 登录：授权码流程、令牌校验、单点登出，说明为什么不能让各系统各自维护密码。
3. 实现数据权限：按部门树/区域过滤数据，验证「同角色不同部门看到的数据不同」。
4. 实现权限实时生效：权限变更后旧令牌立即失效（令牌版本号或短 TTL + 刷新），验证不重启不重新登录即生效。

**验收清单**
- [ ] 「谁能看什么」的判断可复现，规则集中在一处而非散落各处。
- [ ] SSO 登录一次可访问全部接入系统，登出后全部失效。
- [ ] 数据权限按组织架构正确过滤，越权查询返回空而非报错泄露。
- [ ] 权限回收后旧会话在秒级失效。

> 同一道题也写在 [AuthPlatformCase.java](AuthPlatformCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第74章-企业权限中台与SSO.md](../../../../../../../黑马Java课程笔记/第74章-企业权限中台与SSO.md) 的「本章综合练习」一节。
