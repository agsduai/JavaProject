# 阶段项目实战（com.heima.ch13）

`package com.heima.ch13`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 13-01 | 项目介绍和主页面搭建 | [ProjectMainPage.java](ProjectMainPage.java) |
| 13-02 | 用户类和注册的业务 | [UserRegister.java](UserRegister.java) |
| 13-03 | 验证码和登录业务的完整实现 | [LoginAndCaptcha.java](LoginAndCaptcha.java) |
| 13-04 | 准备工作（编写游戏角色的继承结构） | [GameRoleStructure.java](GameRoleStructure.java) |
| 13-05 | 准备工作（创建敌我双方的角色） | [CreateCharacters.java](CreateCharacters.java) |
| 13-06 | 战斗前（增强属性，选择敌人，打印血条） | [BeforeBattle.java](BeforeBattle.java) |
| 13-07 | 战斗中（我方回合） | [HeroTurn.java](HeroTurn.java) |
| 13-08 | 战斗中（敌方回合） | [EnemyTurn.java](EnemyTurn.java) |
| 13-09 | 战斗结算（恢复血量，增加属性，是否继续） | [BattleSettlement.java](BattleSettlement.java) |
| 13-10 | 游戏最终结算 | [FinalSettlement.java](FinalSettlement.java) |
| 13-11 | 阶段项目的作业 | [ProjectHomework.java](ProjectHomework.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：控制台图书管理系统（分层收口）

**场景**

把前几章零散练习收成一个可运行的小系统：登录注册 → 主菜单 → 图书增删改查 → 借还书 → 退出。要求分层（界面层 / 业务层 / 数据层），为后面框架课程打底。

**任务**
1. 实现用户注册与登录：用户名唯一、密码长度校验、验证码（4 位随机字符）比对，登录失败 3 次锁定。
2. 设计 Book 与 Reader 的实体与数据层（用 ArrayList 充当库），业务层只暴露接口方法，界面层不直接碰集合。
3. 实现主菜单循环：switch 分发到图书管理、借还书、统计三个子菜单，输入 0 退出并打印总结。
4. 实现借还书业务：校验库存与借阅上限，归还时更新库存与借阅数，全程不出现空指针。

**验收清单**
- [ ] 三层职责清晰：界面层只做输入输出，业务层只做规则，数据层只做存取。
- [ ] 菜单任意层级输入非法字符都不会崩溃，能重新提示。
- [ ] 借书 → 超限借书 → 归还 → 再借的流程状态完全正确。
- [ ] 退出时打印本次会话的操作次数统计。

> 同一道题也写在 [ProjectHomework.java](ProjectHomework.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第13章-阶段项目实战.md](../../../../../../../黑马Java课程笔记/第13章-阶段项目实战.md) 的「本章综合练习」一节。
