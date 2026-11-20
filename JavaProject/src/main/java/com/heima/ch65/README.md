# IM即时通讯系统设计（com.heima.ch65）

`package com.heima.ch65`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 65-01 | IM 系统全景：连接、消息、会话 | [ImOverview.java](ImOverview.java) |
| 65-02 | 长连接网关：连接管理与心跳 | [ConnectionGateway.java](ConnectionGateway.java) |
| 65-03 | 协议设计：消息帧与版本兼容 | [FrameProtocol.java](FrameProtocol.java) |
| 65-04 | 消息可靠投递：ACK、重传与去重 | [ReliableDelivery.java](ReliableDelivery.java) |
| 65-05 | 会话与消息同步：多端一致性 | [ConversationSync.java](ConversationSync.java) |
| 65-06 | 群聊扇出：写扩散与读扩散 | [GroupFanout.java](GroupFanout.java) |
| 65-07 | 离线消息与推送 | [OfflineMessages.java](OfflineMessages.java) |
| 65-08 | 消息存储与漫游 | [MessageStorage.java](MessageStorage.java) |
| 65-09 | 在线状态与 Presence | [PresenceService.java](PresenceService.java) |
| 65-10 | 弱网与移动端优化 | [WeakNetwork.java](WeakNetwork.java) |
| 65-11 | 综合案例：单聊 + 群聊闭环 | [ImCase.java](ImCase.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：IM 消息不丢不乱

**场景**

设计一个 IM 系统：长连接接入与心跳、消息序号与去重、离线消息与漫游、群聊扩散写与读、已读回执、多端同步。核心指标是「消息不丢、不乱、不重」。

**任务**
1. 实现消息序号：会话内单调递增 seq，客户端按 seq 排序与去重，处理乱序到达。
2. 设计离线消息：在线推送 + 离线落库，上线后按 seq 拉取增量，说明如何避免拉取期间的新消息丢失。
3. 对比群聊两种模式：写扩散（每人一份收件箱）与读扩散（共享一份消息表），给出万人群的选择与理由。
4. 实现多端同步：同一账号多设备在线，已读状态同步、消息撤回同步，说明设备维度与用户维度的位点管理。

**验收清单**
- [ ] 客户端收到的消息按 seq 严格有序，重复消息被去重。
- [ ] 断线重连后能补齐断连期间的全部消息，不丢不重。
- [ ] 群聊方案选型有量化依据（成员数阈值、写放大倍数）。
- [ ] 多端已读/撤回状态一致，任一端的操作其他端可见。

> 同一道题也写在 [ImCase.java](ImCase.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第65章-IM即时通讯系统设计.md](../../../../../../../黑马Java课程笔记/第65章-IM即时通讯系统设计.md) 的「本章综合练习」一节。
