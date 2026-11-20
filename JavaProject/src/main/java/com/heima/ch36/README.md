# 安全编码与漏洞防护（com.heima.ch36）

`package com.heima.ch36`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 36-01 | 安全编码原则与威胁建模 | [ThreatModel.java](ThreatModel.java) |
| 36-02 | SQL 注入与参数化查询 | [SqlInjectionDemo.java](SqlInjectionDemo.java) |
| 36-03 | XSS 与输出编码 | [XssEncoding.java](XssEncoding.java) |
| 36-04 | CSRF 与 SameSite | [CsrfDefense.java](CsrfDefense.java) |
| 36-05 | 越权访问与 IDOR | [IdorDemo.java](IdorDemo.java) |
| 36-06 | 密码存储与加盐哈希 | [PasswordStorage.java](PasswordStorage.java) |
| 36-07 | JWT 与 Session 安全 | [JwtSessionDemo.java](JwtSessionDemo.java) |
| 36-08 | 不安全反序列化与 SSRF | [DeserializeSsrf.java](DeserializeSsrf.java) |
| 36-09 | 敏感信息泄露与日志脱敏 | [SensitiveDataMasking.java](SensitiveDataMasking.java) |
| 36-10 | 依赖供应链与安全响应头 | [SupplyChainHeaders.java](SupplyChainHeaders.java) |
| 36-11 | 综合案例：一次接口安全评审 | [SecurityReviewCase.java](SecurityReviewCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：一次接口安全评审

**场景**

对一段存在多处漏洞的订单查询接口做安全评审：SQL 注入、XSS、越权（IDOR）、密码存储、JWT 伪造、反序列化、敏感信息泄露，逐条复现漏洞再给出修复方案。

**任务**
1. 复现 SQL 注入：用拼接写法构造 ' OR '1'='1，再用参数化查询修复，对比两次返回结果。
2. 复现越权：用户 A 拿用户 B 的订单 id 直接查询成功，修复为「查询必须带当前用户 id 条件」，并写测试证明修复生效。
3. 复现 XSS：把用户输入的 <script> 原样输出到页面，修复为按上下文做 HTML 编码，并说明为什么不能只过滤 <script>。
4. 用 PBKDF2 加盐存储密码，对比明文、MD5、加盐慢哈希三种方案的抗破解能力；再演示 JWT 不校验签名时被篡改的效果。

**验收清单**
- [ ] 每个漏洞都有「复现 → 危害说明 → 修复 → 验证」四段，缺一不可。
- [ ] 修复后的接口对注入 payload 返回空结果而不是全部数据。
- [ ] 越权修复后跨用户查询返回 403，同用户查询正常。
- [ ] 日志与响应体中不出现身份证、手机号明文，密码字段永远不回传。

> 同一道题也写在 [SecurityReviewCase.java](SecurityReviewCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第36章-安全编码与漏洞防护.md](../../../../../../../黑马Java课程笔记/第36章-安全编码与漏洞防护.md) 的「本章综合练习」一节。
