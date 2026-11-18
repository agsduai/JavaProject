/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-10
 * 课名：WebSocket 与长连接
 * ------------------------------------------------------------
 * 知识点：
 *   - **短轮询 / 长轮询 / SSE / WebSocket 的区别**：短轮询按固定间隔问（延迟高、空请求多）；长轮询挂起直到有数据（省空请求，但仍是一次次重建）；SSE 是服务端单向推流（HTTP 响应不结束，`text/event-stream`）；WebSocket 是**全双工**，双方随时能发。
 *   - **握手借用了 HTTP**：客户端发一个带 `Upgrade: websocket`、`Connection: Upgrade`、`Sec-WebSocket-Key: <16字节随机 Base64>` 的 GET 请求。服务端把 key 拼上固定 GUID `258EAFA5-E914-47DA-95CA-C5AB0DC85B11`，算 SHA-1 再 Base64，作为 `Sec-WebSocket-Accept` 返回，状态码 `101 Switching Protocols`。之后这条 TCP 连接就不再走 HTTP 了。
 *   - **`Sec-WebSocket-Key` 不是安全机制**：它只是防止缓存代理把「普通 HTTP 响应」误当成握手成功。真正的认证靠 Cookie/Token 在握手请求里带过去。
 *   - **帧格式**：2 字节起 —— `FIN`(1 bit) + `RSV`(3 bit) + `opcode`(4 bit)，然后是 `MASK`(1 bit) + `payload len`(7 bit，126 表示后跟 2 字节长度，127 表示后跟 8 字节)。客户端发往服务端的帧**必须掩码**，服务端发往客户端的**必须不掩码**，违反就断开。
 *   - **opcode**：`0x0` 续帧、`0x1` 文本、`0x2` 二进制、`0x8` 关闭、`0x9` ping、`0xA` pong。控制帧不能分片，载荷不超过 125 字节。
 *   - **分片（fragmentation）**：大消息拆成「首帧 FIN=0 + 若干续帧 + 末帧 FIN=1」。用于边生成边发，不必先算总长度。
 *   - **心跳必须自己做**：WebSocket 没有内置保活。中间代理通常在 60 秒空闲后断开连接，所以客户端要定时发 ping（或应用层心跳），服务端回 pong。只靠 TCP keepalive 不够，因为它默认 2 小时才探测。
 *   - **重连要退避 + 补偿**：断线后立刻重连会造成惊群。用指数退避 + 随机抖动（1s、2s、4s…上限 30s）。重连后要重新同步状态（拉一次全量或按最后一条消息 ID 增量拉）。
 *   - **鉴权与安全**：浏览器 WebSocket API 不能自定义头，所以 token 一般放在 URL 参数或首个消息里 —— 放 URL 会进日志，优先用「握手时用 Cookie」或「连接后第一帧发 token 并设短超时」。必须校验 `Origin`，否则任意站点都能用用户 Cookie 建立连接（CSWSH）。
 *   - **水平扩展**：连接分散在多台机器上，A 机器上的用户要给 B 机器上的用户发消息，需要**会话路由**（Redis 记录 uid→节点）或**广播总线**（Redis Pub/Sub、Kafka）。
 *   - 易错点：以为 WebSocket 能自动重连（不能，要自己写）；用 `setInterval` 发心跳但页面隐藏时被节流导致误判断线；忘记处理 `onclose` 的 code（1006 是异常断开，不能用于业务判断）；在消息里直接 `JSON.parse` 不校验（大消息会卡主线程）。
 * ============================================================
 */

package com.heima.ch35;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WebSocketDemo {
    static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** 按 RFC 6455 计算 Sec-WebSocket-Accept */
    static String acceptKey(String clientKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest((clientKey + GUID).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    enum Opcode {
        CONTINUATION(0x0), TEXT(0x1), BINARY(0x2), CLOSE(0x8), PING(0x9), PONG(0xA);

        final int code;

        Opcode(int code) {
            this.code = code;
        }

        static Opcode of(int code) {
            for (Opcode o : values()) {
                if (o.code == code) {
                    return o;
                }
            }
            throw new IllegalArgumentException("未知 opcode: " + code);
        }
    }

    /** 一个 WebSocket 帧，含掩码处理 */
    static class Frame {
        final boolean fin;
        final Opcode opcode;
        final boolean masked;
        final byte[] payload;

        Frame(boolean fin, Opcode opcode, boolean masked, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.masked = masked;
            this.payload = payload;
        }

        static Frame text(String s, boolean clientToServer) {
            return new Frame(true, Opcode.TEXT, clientToServer, s.getBytes(StandardCharsets.UTF_8));
        }

        /** 编码成线上字节：这是抓包时真正看到的东西 */
        byte[] encode(long maskKey) {
            byte[] out = new byte[14 + payload.length];
            int p = 0;
            out[p++] = (byte) ((fin ? 0x80 : 0) | opcode.code);
            int maskBit = masked ? 0x80 : 0;
            if (payload.length < 126) {
                out[p++] = (byte) (maskBit | payload.length);
            } else if (payload.length < 65536) {
                out[p++] = (byte) (maskBit | 126);
                out[p++] = (byte) (payload.length >>> 8);
                out[p++] = (byte) payload.length;
            } else {
                out[p++] = (byte) (maskBit | 127);
                for (int i = 7; i >= 0; i--) {
                    out[p++] = (byte) (payload.length >>> (8 * i));
                }
            }
            if (masked) {
                byte[] key = new byte[4];
                for (int i = 0; i < 4; i++) {
                    key[i] = (byte) (maskKey >>> (8 * (3 - i)));
                }
                System.arraycopy(key, 0, out, p, 4);
                p += 4;
                for (int i = 0; i < payload.length; i++) {
                    out[p++] = (byte) (payload[i] ^ key[i % 4]);
                }
            } else {
                System.arraycopy(payload, 0, out, p, payload.length);
                p += payload.length;
            }
            byte[] trimmed = new byte[p];
            System.arraycopy(out, 0, trimmed, 0, p);
            return trimmed;
        }

        String describe(long maskKey) {
            byte[] wire = encode(maskKey);
            String head = String.format("FIN=%d opcode=%s mask=%s len=%d",
                    fin ? 1 : 0, opcode, masked ? 1 : 0, payload.length);
            return String.format("%-42s 线上 %2d B  %s", head, wire.length, hexPreview(wire, 12));
        }
    }

    static String hexPreview(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, b.length); i++) {
            sb.append(String.format("%02x ", b[i]));
        }
        return sb.toString().trim() + (b.length > n ? " ..." : "");
    }

    static void handshake() {
        System.out.println("【握手：借用 HTTP，算一个 SHA-1 就升级】");
        String clientKey = Base64.getEncoder().encodeToString(
                "0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
        System.out.println("  client 生成 16 字节随机数，Base64 后作为 Sec-WebSocket-Key：");
        System.out.println("    Sec-WebSocket-Key: " + clientKey);

        String request = "GET /ws/chat HTTP/1.1\r\n"
                + "Host: shop.example.com\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + clientKey + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Origin: https://shop.example.com\r\n"
                + "\r\n";
        System.out.println("  请求：");
        for (String line : request.split("\r\n")) {
            if (!line.isEmpty()) {
                System.out.println("    | " + line);
            }
        }

        String accept = acceptKey(clientKey);
        System.out.println("  server 计算：SHA-1(key + GUID) 再 Base64");
        System.out.println("    GUID = " + GUID);
        System.out.println("    Sec-WebSocket-Accept: " + accept);
        System.out.println("  响应：HTTP/1.1 101 Switching Protocols + 上面这个头");
        System.out.println("  之后这条 TCP 连接不再走 HTTP，改按帧收发");
        System.out.println("  注意：这个 key 只是防缓存代理误判，不是认证；认证靠 Cookie/Token");
        System.out.println();
    }

    static void frameFormat() {
        System.out.println("【帧格式与掩码规则】");
        long mask = 0x37fa213dL;
        List<Frame> frames = new ArrayList<>();
        frames.add(Frame.text("hello", true));
        frames.add(Frame.text("hello", false));
        frames.add(new Frame(true, Opcode.PING, true, new byte[0]));
        frames.add(new Frame(true, Opcode.CLOSE, true, new byte[]{0x03, (byte) 0xE8}));

        byte[] big = new byte[300];
        frames.add(new Frame(true, Opcode.BINARY, true, big));

        for (Frame f : frames) {
            System.out.println("  " + f.describe(mask));
        }
        System.out.println("  规则：client->server 必须 mask=1，server->client 必须 mask=0");
        System.out.println("  长度 126 表示后面 2 字节是长度；127 表示后面 8 字节是长度");
        System.out.println("  控制帧（CLOSE/PING/PONG）不能分片，载荷 <= 125 字节");
        System.out.println();
    }

    static void fragmentation() {
        System.out.println("【分片：大消息拆成多帧】");
        String[] chunks = {"第一部分-", "第二部分-", "第三部分"};
        List<Frame> frames = new ArrayList<>();
        for (int i = 0; i < chunks.length; i++) {
            boolean last = i == chunks.length - 1;
            Opcode op = i == 0 ? Opcode.TEXT : Opcode.CONTINUATION;
            frames.add(new Frame(last, op, true, chunks[i].getBytes(StandardCharsets.UTF_8)));
        }
        for (Frame f : frames) {
            System.out.println("  " + f.describe(0x11223344L));
        }
        System.out.println("  首帧 opcode=TEXT，中间与末帧 opcode=CONTINUATION，只有末帧 FIN=1");
        System.out.println("  接收方按 FIN 判断消息边界，不能把每个 DATA 帧当成独立消息");
        System.out.println();
    }

    static void heartbeat() {
        System.out.println("【心跳：没有内置保活，必须自己做】");
        int idleTimeoutSec = 60;
        int pingIntervalSec = 25;
        System.out.printf("  代理/负载均衡空闲超时 %d 秒，心跳间隔设为 %d 秒（留余量）%n",
                idleTimeoutSec, pingIntervalSec);
        for (int t = 0; t <= 120; t += pingIntervalSec) {
            System.out.printf("  t=%3ds  发 PING -> 收 PONG，连接保活%n", t);
        }
        System.out.println("  只靠 TCP keepalive 不行：Linux 默认 7200 秒才探测");
        System.out.println("  心跳还要做「超时判定」：连续 2 次没收到 PONG 就主动 close 并重连");
        System.out.println();
    }

    static void reconnectBackoff() {
        System.out.println("【断线重连：指数退避 + 抖动】");
        int base = 1;
        int cap = 30;
        long seed = 20260101L;
        for (int attempt = 1; attempt <= 8; attempt++) {
            int exp = Math.min(cap, base * (1 << Math.min(attempt - 1, 5)));
            seed = seed * 1103515245L + 12345L;
            int jitter = Math.floorMod(seed >> 32, Math.max(exp / 2, 1));
            int wait = Math.min(cap, exp + jitter);
            System.out.printf("  第 %d 次重连：等待 %2d 秒（指数 %d + 抖动 %d）%n", attempt, wait, exp, jitter);
        }
        System.out.println("  没有抖动 -> 十万客户端在同一毫秒重连，服务端直接被自己人打挂");
        System.out.println("  重连成功后要补偿状态：按最后一条消息 ID 拉增量，或直接拉一次全量");
        System.out.println();
    }

    static void authAndOrigin() {
        System.out.println("【鉴权与 Origin 校验】");
        System.out.println("  浏览器 WebSocket API 不能自定义请求头，所以 token 只能：");
        System.out.println("    1) 放 URL 参数  ?token=xxx   -> 会进访问日志、Referer，不推荐");
        System.out.println("    2) 靠握手时自动带的 Cookie   -> 推荐，但要防 CSRF");
        System.out.println("    3) 连接建立后第一帧发 token，5 秒内没收到就断开 -> 常用折中");
        System.out.println();
        System.out.println("  必须校验 Origin：否则任意站点都能用用户 Cookie 建立连接（CSWSH）");
        String[] allowed = {"https://shop.example.com", "https://admin.example.com"};
        String[] incoming = {"https://shop.example.com", "https://evil.example.net"};
        for (String origin : incoming) {
            boolean ok = List.of(allowed).contains(origin);
            System.out.printf("  Origin=%-30s -> %s%n", origin, ok ? "允许" : "拒绝（403）");
        }
        System.out.println();
    }

    static void scaleOut() {
        System.out.println("【多机部署：消息怎么跨节点送达】");
        Map<String, String> sessionRoute = new LinkedHashMap<>();
        sessionRoute.put("user-1001", "node-A");
        sessionRoute.put("user-1002", "node-B");
        System.out.println("  连接分散在多台机器，必须先知道「谁在哪台」：");
        sessionRoute.forEach((u, n) -> System.out.printf("    %s -> %s%n", u, n));
        System.out.println("  方案一：会话路由表（Redis 存 uid -> nodeId），定向投递");
        System.out.println("  方案二：广播总线（Redis Pub/Sub、Kafka），各节点订阅后只投本地在线用户");
        System.out.println("  方案三：网关层按 uid 哈希做一致性路由，同一用户固定落到同一节点");
        System.out.println("  群聊消息走方案二；单聊走方案一，找不到路由再退回方案二");
        System.out.println();
    }

    public static void main(String[] args) {
        handshake();
        frameFormat();
        fragmentation();
        heartbeat();
        reconnectBackoff();
        authAndOrigin();
        scaleOut();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 `Frame.decode(byte[] wire)`：解析 FIN、opcode、长度（含 126/127 扩展长度）、掩码并还原 payload，与 `encode` 互逆。
 * 2. 写一个 `WebSocketServer` 状态机：`handshaking` → `open` → `closing` → `closed`，收到 CLOSE 帧时回一个 CLOSE 并停止收帧，超时未收到对端 CLOSE 则强制断开。
 * 3. 实现带抖动的心跳超时判定：连续 N 次未收到 PONG 触发重连，并打印每次的 RTT 统计（最小/最大/平均）。
 * 4. 思考题：为什么客户端帧必须掩码？如果允许不掩码，会给中间代理带来什么风险？
 * ============================================================
 */
