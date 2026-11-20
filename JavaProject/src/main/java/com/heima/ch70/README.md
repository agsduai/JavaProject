# 国际化与本地化工程（com.heima.ch70）

`package com.heima.ch70`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 70-01 | i18n 全景：语言、地区与格式 | [I18nOverview.java](I18nOverview.java) |
| 70-02 | 资源束与消息格式化 | [ResourceBundleDemo.java](ResourceBundleDemo.java) |
| 70-03 | 复数规则与性别 | [PluralRulesDemo.java](PluralRulesDemo.java) |
| 70-04 | 时区与时间处理 | [TimeZoneDemo.java](TimeZoneDemo.java) |
| 70-05 | 货币与数字格式 | [CurrencyDemo.java](CurrencyDemo.java) |
| 70-06 | 排序与比较（Collator） | [CollationDemo.java](CollationDemo.java) |
| 70-07 | 翻译工作流与质量 | [TranslationWorkflow.java](TranslationWorkflow.java) |
| 70-08 | 前端与多端一致性 | [MultiPlatformI18n.java](MultiPlatformI18n.java) |
| 70-09 | 测试：伪本地化与回归 | [I18nTesting.java](I18nTesting.java) |
| 70-10 | 多语言下的搜索与匹配 | [MultilingualSearch.java](MultilingualSearch.java) |
| 70-11 | 综合案例：出海电商多语言改造 | [GlobalEcommerceCase.java](GlobalEcommerceCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：一次全球化上线

**场景**

把一个电商系统做成全球化：多语言与文案管理、时区与货币、本地化格式（日期/数字/地址）、多区域部署与数据合规、翻译回退链、RTL 布局。要求「同一份代码服务所有地区」。

**任务**
1. 实现多语言：资源文件按 locale 组织、运行时切换语言、缺失文案的回退链（zh-HK → zh → en），验证不出现 key 裸露。
2. 处理时区：所有存储用 UTC、展示按用户时区转换、跨时区下单与统计口径，说明「今天」在不同时区不是同一段时间。
3. 处理货币与格式：金额用最小单位整数存储、按 locale 格式化、汇率换算与舍入规则。
4. 设计多区域部署：数据驻留（欧盟数据不出境）、就近接入、跨区一致性策略，说明哪些数据可以跨区、哪些不能。

**验收清单**
- [ ] 切换到任一语言后界面无未翻译的 key，回退链生效。
- [ ] 同一时刻在不同时区展示的「今天订单数」解释得清，不出现跨天错账。
- [ ] 金额计算无浮点误差，格式化符合当地习惯（如 1.234,56）。
- [ ] 数据驻留要求被满足，跨区调用的数据范围有明确清单。

> 同一道题也写在 [GlobalEcommerceCase.java](GlobalEcommerceCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第70章-国际化与本地化工程.md](../../../../../../../黑马Java课程笔记/第70章-国际化与本地化工程.md) 的「本章综合练习」一节。
