# 计算机网络与HTTP协议（com.heima.ch35）

`package com.heima.ch35`

| 编号 | 课名 | 文件 |
| --- | --- | --- |
| 35-01 | 网络分层与封装解封装 | [NetworkLayers.java](NetworkLayers.java) |
| 35-02 | TCP 三次握手与四次挥手 | [TcpHandshake.java](TcpHandshake.java) |
| 35-03 | TCP 可靠传输与拥塞控制 | [TcpReliable.java](TcpReliable.java) |
| 35-04 | UDP 与 QUIC | [UdpQuic.java](UdpQuic.java) |
| 35-05 | HTTP/1.1 报文格式与状态码 | [HttpMessage.java](HttpMessage.java) |
| 35-06 | HTTP 缓存：强缓存与协商缓存 | [HttpCache.java](HttpCache.java) |
| 35-07 | HTTPS 与 TLS 握手 | [TlsHandshake.java](TlsHandshake.java) |
| 35-08 | HTTP/2 多路复用与帧 | [Http2Mux.java](Http2Mux.java) |
| 35-09 | DNS 解析与 CDN 调度 | [DnsCdn.java](DnsCdn.java) |
| 35-10 | WebSocket 与长连接 | [WebSocketDemo.java](WebSocketDemo.java) |
| 35-11 | 综合案例：一次请求的完整链路 | [RequestJourney.java](RequestJourney.java) |

> 练习题在每课文件末尾的注释块中，无参考答案。

## 本章综合练习

### 综合题：一次请求的完整链路

**场景**

跟踪一个 HTTPS 请求从输入域名到拿到响应的全过程：DNS 解析、TCP 握手、TLS 协商、HTTP 报文构造、缓存判断、HTTP/2 多路复用，每一段都用手写模拟或真实请求验证。

**任务**
1. 手写 DNS 解析流程模拟：本地缓存 → hosts → 递归查询，打印每一跳的耗时与结果，说明 TTL 的作用。
2. 用状态机模拟 TCP 三次握手与四次挥手，打印每次状态迁移与报文标志位，说明为什么是三次不是两次。
3. 手拼一个 HTTP/1.1 请求报文（请求行、头、空行、体），用 Socket 发给本机 HttpServer，解析响应状态行与响应头。
4. 验证缓存：同一个 URL 连续请求两次，观察 200 与 304 的差异，说明强缓存与协商缓存的判定顺序。

**验收清单**
- [ ] DNS 模拟中命中缓存与未命中两条路径都能打印完整过程。
- [ ] TCP 状态机迁移序列正确（SYN_SENT → ESTABLISHED 等），标志位标注无误。
- [ ] 手拼报文能被服务端正确解析并返回 200，响应头能读出 Content-Length 与 Content-Type。
- [ ] 第二次请求返回 304 且响应体为空，能说出是哪个头触发了协商缓存。

> 同一道题也写在 [RequestJourney.java](RequestJourney.java) 文件末尾的注释块中，无参考答案；完整笔记见 [第35章-计算机网络与HTTP协议.md](../../../../../../../黑马Java课程笔记/第35章-计算机网络与HTTP协议.md) 的「本章综合练习」一节。
