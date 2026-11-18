/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-08
 * 课名：HTTP/2 多路复用与帧
 * ------------------------------------------------------------
 * 知识点：
 *   - HTTP/2 把报文改成**二进制帧**：每个帧有 9 字节头（长度 24 位、类型 8 位、标志 8 位、流 ID 31 位），载荷是帧内容。文本协议变成二进制，解析更快也更严格。
 *   - **一条 TCP 连接承载所有请求**，每个请求是一条**流（stream）**，用奇数 ID 由客户端发起、偶数 ID 由服务端发起。帧可以交错发送，接收端按流 ID 归位 —— 这就是**多路复用**。
 *   - **解决的是应用层队头阻塞**：HTTP/1.1 里请求 B 必须等 A 的响应写完；HTTP/2 里 A 和 B 的帧可以交替上路。但**传输层队头阻塞没解决**，因为底层还是一条 TCP 字节流 —— 这是 HTTP/3 换 QUIC 的动机。
 *   - **HPACK 头部压缩**：静态表（61 个常见头，如 `:method: GET`）+ 动态表（本次连接里出现过的头）+ 哈夫曼编码。第二个请求的头部可能只有几个字节。
 *   - **伪头部**：`:method`、`:path`、`:scheme`、`:authority` 替代请求行。它们必须出现在所有普通头之前。
 *   - **流状态机**：`idle` → `open` → `half-closed (local/remote)` → `closed`。还有 `reserved` 用于服务端推送。乱序发帧、在 closed 流上发帧都是协议错误，要回 `RST_STREAM` 或 `GOAWAY`。
 *   - **流量控制是两级的**：连接级窗口 + 流级窗口，默认 65535 字节，靠 `WINDOW_UPDATE` 扩大。接收方可以只给某条流小窗口，实现「慢请求别占满带宽」。
 *   - **优先级**：HTTP/2 原本有复杂的依赖树（`PRIORITY` 帧），实际效果不好，RFC 9113 已废弃，改用 `Priority` 头 + `PRIORITY_UPDATE` 帧的简化方案。
 *   - **服务端推送（Server Push）已被弃用**：浏览器无法精确知道客户端是否已有缓存，推了常常是浪费。Chrome 已移除。
 *   - **`h2` 与 `h2c`**：`h2` 是 TLS 上的（ALPN 协商），`h2c` 是明文升级（`Upgrade: h2c`），生产基本只用 `h2`。
 *   - **常见误区**：以为 HTTP/2 一定更快（高丢包链路上单连接反而更糟，因为一个包丢了所有流都等）；以为多路复用等于无限并发（`SETTINGS_MAX_CONCURRENT_STREAMS` 有限制）；以为 HTTP/2 不需要域名分片（确实不需要，分片反而有害，因为要建多条连接）。
 *   - 易错点：在 HTTP/2 里继续用 `Connection: keep-alive`（这是 HTTP/1.1 的头，HTTP/2 禁止连接相关头）；忘记 `:authority` 导致虚拟主机路由失败；用 HTTP/2 明文端口跑 `h2`（必须 TLS + ALPN）。
 * ============================================================
 */

package com.heima.ch35;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class Http2Mux {
    enum FrameType {
        HEADERS, DATA, SETTINGS, WINDOW_UPDATE, RST_STREAM, PING, GOAWAY
    }

    /** 一个 HTTP/2 帧：9 字节头里的关键字段 */
    record Frame(FrameType type, int streamId, int flags, String payload) {
        int wireSize() {
            return 9 + payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        String flagText() {
            return switch (type) {
                case HEADERS -> (flags & 1) != 0 ? "END_STREAM" : (flags & 4) != 0 ? "END_HEADERS" : "-";
                case DATA -> (flags & 1) != 0 ? "END_STREAM" : "-";
                default -> "-";
            };
        }
    }

    /** 极简 HPACK：静态表 + 动态表 + 「已存在就只发索引」 */
    static class Hpack {
        static final List<String> STATIC = List.of(
                ":authority", ":method", ":method: GET", ":method: POST",
                ":path", ":path: /", ":scheme", ":scheme: https",
                ":status", ":status: 200", ":status: 404",
                "accept-encoding: gzip, deflate", "user-agent", "cookie");
        final Map<String, Integer> dynamic = new LinkedHashMap<>();
        int nextIndex = 62;

        int encode(String header) {
            int idx = STATIC.indexOf(header);
            if (idx >= 0) {
                return idx + 1;
            }
            Integer d = dynamic.get(header);
            if (d != null) {
                return d;
            }
            dynamic.put(header, nextIndex++);
            return -1;      // -1 表示要发字面量
        }

        int encodedSize(String header) {
            return encode(header) > 0 ? 1 : 1 + header.length();
        }
    }

    static void http11VsH2() {
        System.out.println("【HTTP/1.1：三条连接，各自排队】");
        String[][] h1 = {
                {"连接1", "GET /a.css  -> 等 120ms -> 响应"},
                {"连接2", "GET /b.js   -> 等 90ms  -> 响应"},
                {"连接3", "GET /c.png  -> 等 200ms -> 响应"},
        };
        for (String[] r : h1) {
            System.out.printf("  %s  %s%n", r[0], r[1]);
        }
        System.out.println("  总耗时约 200ms，但要占 3 条 TCP 连接（3 次握手 + 3 次慢启动）");
        System.out.println();

        System.out.println("【HTTP/2：一条连接，帧交错】");
        List<Frame> wire = new ArrayList<>();
        wire.add(new Frame(FrameType.HEADERS, 1, 4, ":method: GET /a.css"));
        wire.add(new Frame(FrameType.HEADERS, 3, 4, ":method: GET /b.js"));
        wire.add(new Frame(FrameType.HEADERS, 5, 4, ":method: GET /c.png"));
        wire.add(new Frame(FrameType.DATA, 1, 0, "a.css-part1"));
        wire.add(new Frame(FrameType.DATA, 3, 0, "b.js-part1"));
        wire.add(new Frame(FrameType.DATA, 5, 1, "c.png-all"));
        wire.add(new Frame(FrameType.DATA, 1, 1, "a.css-part2"));
        wire.add(new Frame(FrameType.DATA, 3, 1, "b.js-part2"));
        for (Frame f : wire) {
            System.out.printf("  %-12s stream=%-2d flags=%-10s %s%n",
                    f.type(), f.streamId(), f.flagText(), f.payload());
        }
        System.out.println("  同一个 TCP 连接，c.png 先完成，不必等 a.css 发完");
        System.out.println("  总耗时约等于最慢那一条，且只付 1 次握手");
        System.out.println();
    }

    static void hpack() {
        System.out.println("【HPACK：第二个请求的头几乎不用传】");
        Hpack hpack = new Hpack();
        String[][] requests = {
                {"GET", "/index.html", "gzip, deflate"},
                {"GET", "/app.js", "gzip, deflate"},
                {"GET", "/logo.png", "gzip, deflate"},
        };
        int total = 0;
        for (String[] r : requests) {
            String[] headers = {
                    ":method: " + r[0],
                    ":path: " + r[1],
                    "accept-encoding: " + r[2],
                    "user-agent: demo/1.0",
            };
            StringBuilder detail = new StringBuilder();
            int size = 0;
            for (String h : headers) {
                int idx = hpack.encode(h);
                int s = idx > 0 ? 1 : 1 + h.length();
                size += s;
                detail.append(idx > 0 ? ("#" + idx + " ") : ("LIT(" + h + ") "));
            }
            total += size;
            System.out.printf("  %-12s 头部编码 %2d B：%s%n", r[1], size, detail.toString().trim());
        }
        System.out.printf("  合计 %d B；若按 HTTP/1.1 明文传，三个请求的头部约 %d B%n",
                total, 3 * 80);
        System.out.println("  真实 HPACK 还会对字面量做哈夫曼编码，常见头 1 字节就能表示");
        System.out.println();
    }

    static void streamStates() {
        System.out.println("【流状态机：谁先 END_STREAM，谁先半关闭】");
        Map<String, String> states = new LinkedHashMap<>();
        states.put("idle", "尚未使用，或收到 HEADERS 之前的初始态");
        states.put("open", "双方都可以发帧");
        states.put("half-closed (local)", "本端发了 END_STREAM，只能收不能发");
        states.put("half-closed (remote)", "对端发了 END_STREAM，只能发不能收");
        states.put("closed", "流结束，再发帧就是协议错误");
        states.put("reserved (remote)", "服务端推送预留（已弃用）");
        states.forEach((k, v) -> System.out.printf("  %-22s %s%n", k, v));
        System.out.println();
        System.out.println("  客户端 GET 流程：HEADERS(END_HEADERS) -> open");
        System.out.println("                  -> DATA(END_STREAM)        -> half-closed(local)");
        System.out.println("                  -> 收 HEADERS + DATA(END_STREAM) -> closed");
        System.out.println();
    }

    static void flowControl() {
        System.out.println("【两级流量控制：连接窗口 + 流窗口】");
        int connWindow = 65535;
        Map<Integer, Integer> streamWindow = new HashMap<>();
        streamWindow.put(1, 65535);
        streamWindow.put(3, 16384);      // 给慢请求一个小窗口
        System.out.printf("  连接窗口 %d B；流 1 窗口 %d B；流 3 窗口 %d B%n",
                connWindow, streamWindow.get(1), streamWindow.get(3));

        int[] dataByStream3 = {8000, 8000, 8000};
        for (int i = 0; i < dataByStream3.length; i++) {
            int want = dataByStream3[i];
            int allowed = Math.min(want, Math.min(connWindow, streamWindow.get(3)));
            connWindow -= allowed;
            streamWindow.put(3, streamWindow.get(3) - allowed);
            System.out.printf("  流 3 第 %d 块想发 %d B，实际允许 %d B；连接窗口剩 %d，流窗口剩 %d%n",
                    i + 1, want, allowed, connWindow, streamWindow.get(3));
            if (allowed < want) {
                System.out.println("    窗口不足，必须等接收方发 WINDOW_UPDATE");
            }
        }
        System.out.println("  流窗口可以按流单独调小：慢接口不会把整条连接的带宽吃光");
        System.out.println();
    }

    static void limitsAndTraps() {
        System.out.println("【上限与常见坑】");
        System.out.println("  SETTINGS_MAX_CONCURRENT_STREAMS：同时活跃的流数上限，超了要排队");
        System.out.println("  SETTINGS_MAX_FRAME_SIZE：单帧最大 16384 B，大响应必须切成多个 DATA 帧");
        System.out.println("  禁止连接相关头：Connection、Keep-Alive、Transfer-Encoding、Upgrade");
        System.out.println("  伪头部必须在普通头之前，且每个请求必须有 :method :scheme :path");
        System.out.println("  明文 h2c 需要 Upgrade 协商，生产环境基本不用；浏览器只认 TLS + ALPN 的 h2");
        System.out.println("  单连接 = 单 TCP 流：丢一个包，所有流一起等 -> 这正是 HTTP/3 要解决的问题");
        System.out.println();
    }

    public static void main(String[] args) {
        http11VsH2();
        hpack();
        streamStates();
        flowControl();
        limitsAndTraps();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Frame` 加 `wireSize()` 的完整实现，并统计上面 8 个帧在线上总共占多少字节（含 9 字节帧头）。
 * 2. 实现 `Hpack.decode(List<Integer> indices)`，把编码后的索引序列还原成头部列表，验证第二个请求能复用动态表。
 * 3. 写一个流状态机 `Stream`：`onHeaders`、`onData`、`onEndStream`、`onRst`，非法迁移时抛异常，覆盖上面列出的五种状态。
 * 4. 思考题：为什么 HTTP/2 在丢包率高的移动网络上可能比 HTTP/1.1 还慢？HTTP/3 用 QUIC 后这个问题如何缓解？
 * ============================================================
 */
