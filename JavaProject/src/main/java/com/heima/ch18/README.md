# 网络编程（com.heima.ch18）

`package com.heima.ch18`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 18-01 | 网络编程三要素 | [NetworkElements.java](NetworkElements.java) |
| 18-02 | UDP通信 | [UdpDemo.java](UdpDemo.java) |
| 18-03 | TCP通信：客户端与服务端 | [TcpDemo.java](TcpDemo.java) |
| 18-04 | TCP多客户端与聊天室 | [ChatRoom.java](ChatRoom.java) |
| 18-05 | TCP文件上传 | [FileUpload.java](FileUpload.java) |
| 18-06 | HTTP请求与URL访问 | [HttpUrlDemo.java](HttpUrlDemo.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：局域网聊天室

**场景**

做一个能在本机跑通的多客户端聊天室：服务端广播消息、客户端收发消息、支持文件上传，最后用 HttpClient 访问一个公开接口验证 HTTP 请求格式。UDP 与 TCP 的差异在对比中体现。

**任务**
1. 用 UDP 实现一个回声服务：客户端发一句、服务端回一句，打印双方端口，说明 UDP 为什么不需要建立连接。
2. 用 TCP 实现服务端 + 多客户端聊天室：服务端为每个连接开一个线程，收到消息后广播给其他客户端。
3. 在聊天室基础上加文件上传：客户端发送文件名与字节流，服务端落盘并回执字节数。
4. 用 HttpURLConnection 或 HttpClient 发起一次 GET 请求，打印状态码与响应头，说明 URL 的各个组成部分。

**验收清单**
- [ ] 三个客户端同时在线，任一客户端发消息其余都能收到。
- [ ] 文件上传后服务端文件大小与客户端发送字节数一致。
- [ ] 客户端断开后服务端能感知并移除连接，不残留死线程。
- [ ] HTTP 请求能打印出 2xx 状态码与 Content-Type。

> 同一道题也写在 [HttpUrlDemo.java](HttpUrlDemo.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第18章-网络编程.md](../../../../../../../黑马Java课程笔记/第18章-网络编程.md) 的「本章综合练习」一节。
