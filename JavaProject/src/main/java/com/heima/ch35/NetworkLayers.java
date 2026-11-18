/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-01
 * 课名：网络分层与封装解封装
 * ------------------------------------------------------------
 * 知识点：
 *   - **分层不是学术洁癖，是分工**。应用层只管「我要传什么」，传输层管「丢了怎么办、按什么顺序交」，网络层管「怎么从 A 走到 B」，链路层管「这一跳怎么送出去」。每层只加自己那份头部，谁也不用关心别人的细节。
 *   - **封装（encapsulation）**：数据从上往下走，每层在前面加一个头部。HTTP 报文 → 加 TCP 头（端口、序号）→ 加 IP 头（源/目的 IP、TTL）→ 加以太网头（MAC）→ 变成电信号。接收方反过来一层层剥，叫**解封装**。
 *   - **头部开销是真实成本**：以太网头 14 B + IP 头 20 B + TCP 头 20 B + 帧尾 FCS 4 B = 58 B。传 20 B 的心跳包，74% 的带宽在传头部；传 1460 B 的满包，只有 3.8%。这就是「小包不划算」的算术根源。
 *   - **MTU 与 MSS**：链路层一次能承载的最大帧叫 MTU，以太网通常 1500 B。减去 IP 头 20 B 和 TCP 头 20 B，TCP 单段最多 1460 B，这个值叫 **MSS**。超过 MSS 的数据由 IP 层**分片**，每片都要重复一个 IP 头，接收端还要重组，代价很高。
 *   - **分片很危险**：任何一片丢了，整个报文都要重传；防火墙和 NAT 对分片的处理还经常不一致。所以 TCP 会按 MSS 主动切分，应用层也该避免发超大报文。
 *   - **TTL 是防环的**：IP 头里的 TTL 每经过一个路由器减 1，减到 0 就丢弃并回一个 ICMP Time Exceeded。它保证网络里有环路时包不会永远转下去，`traceroute` 就是靠逐步加大 TTL 探路的。
 *   - **协议号与端口分工不同**：IP 头里的协议号（TCP=6，UDP=17，ICMP=1）决定交给哪个传输层协议；TCP/UDP 头里的端口决定交给哪个进程。IP 定位主机，端口定位进程。
 *   - **一次 HTTP 请求的真实路径**：应用层组报文 → TCP 分段 → IP 打包 → 网卡发帧 → 交换机/路由器逐跳转发 → 目的主机逐层解封装 → 交给 80/443 端口的进程。
 *   - **常见误解**：以为「HTTP 是应用层所以跟 TCP 无关」——HTTP/1.1 的队头阻塞、HTTP/2 的多路复用、HTTP/3 换 QUIC，全都是传输层特性在决定应用层表现。
 *   - 易错点：把 MTU 和 MSS 混为一谈（差 40 B）；以为分片是 TCP 干的（是 IP 干的）；用 `ping -f` 打不分片包来探测 MTU 时忘了 IPv4 头也可能带选项。
 * ============================================================
 */

package com.heima.ch35;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class NetworkLayers {
    /** 一层协议：名字、头部字节数、这一层最关键的字段 */
    static class Layer {
        final String name;
        final int headerBytes;
        final String keyFields;

        Layer(String name, int headerBytes, String keyFields) {
            this.name = name;
            this.headerBytes = headerBytes;
            this.keyFields = keyFields;
        }
    }

    /** 一个封装好的帧：layers 按「从外到内」即线上顺序保存 */
    static class Frame {
        final String payload;
        final List<Layer> layers = new ArrayList<>();
        int ttl = 64;

        Frame(String payload) {
            this.payload = payload;
        }

        /** 加一层头部：新头部总在最前面，所以插到 0 号位 */
        Frame wrap(Layer layer) {
            layers.add(0, layer);
            return this;
        }

        int payloadBytes() {
            return payload.getBytes(StandardCharsets.UTF_8).length;
        }

        int headerBytes() {
            int sum = 0;
            for (Layer l : layers) {
                sum += l.headerBytes;
            }
            return sum;
        }

        int totalBytes() {
            return payloadBytes() + headerBytes();
        }

        String describe() {
            StringBuilder sb = new StringBuilder();
            for (Layer l : layers) {
                sb.append(String.format("  %-9s %3d B   %s%n", l.name, l.headerBytes, l.keyFields));
            }
            String shown = payload.length() > 22 ? payload.substring(0, 22) + "..." : payload;
            sb.append(String.format("  %-9s %3d B   %s%n", "payload", payloadBytes(), shown));
            sb.append(String.format("  合计 %d B，头部 %d B，占 %.1f%%%n",
                    totalBytes(), headerBytes(), headerBytes() * 100.0 / totalBytes()));
            return sb.toString();
        }
    }

    static void encapsulation() {
        Frame frame = new Frame("GET /api/orders?page=2 HTTP/1.1");
        frame.wrap(new Layer("HTTP", 0, "请求行 + 头部 + 空行（这里只算载荷）"))
                .wrap(new Layer("TCP", 20, "源端口/目的端口/序号/确认号/窗口/标志位"))
                .wrap(new Layer("IP", 20, "源 IP/目的 IP/TTL/协议号=6"))
                .wrap(new Layer("Ethernet", 14, "源 MAC/目的 MAC/类型=0x0800"));
        System.out.println("【封装：从外到内就是线上顺序】");
        System.out.print(frame.describe());
        System.out.println();
    }

    static void headerTax() {
        System.out.println("【头部开销：载荷越小越亏】");
        int[] payloads = {20, 100, 500, 1460};
        for (int p : payloads) {
            int wire = p + 58;   // 以太网 14 + IP 20 + TCP 20 + FCS 4
            System.out.printf("  载荷 %4d B -> 线上 %4d B，头部占 %4.1f%%%n",
                    p, wire, (wire - p) * 100.0 / wire);
        }
        System.out.println("  所以 RPC 要批量合并、要压缩、要长连接复用，而不是一个字段发一个请求");
        System.out.println();
    }

    static void fragmentation(int mtu, int ipHeader, int tcpHeader, int payloadBytes) {
        int mss = mtu - ipHeader - tcpHeader;
        int fragments = (payloadBytes + mss - 1) / mss;
        System.out.printf("【IP 分片：MTU=%d，IP 头=%d，TCP 头=%d】%n", mtu, ipHeader, tcpHeader);
        System.out.printf("  MSS = %d - %d - %d = %d B%n", mtu, ipHeader, tcpHeader, mss);
        int total = 0;
        for (int i = 0; i < fragments; i++) {
            int chunk = Math.min(mss, payloadBytes - i * mss);
            int wire = chunk + ipHeader;      // 分片后每片只有 IP 头 + 数据
            total += wire;
            boolean more = i < fragments - 1;
            System.out.printf("  分片 %d：数据 %4d B + IP 头 %2d B = %4d B，MF=%d，片偏移=%d%n",
                    i + 1, chunk, ipHeader, wire, more ? 1 : 0, i * mss / 8);
        }
        System.out.printf("  线上合计 %d B，比原始报文多出 %d B（重复的 IP 头）%n",
                total, (fragments - 1) * ipHeader);
        System.out.println("  任一分片丢失 -> 整个报文重传，所以 TCP 会主动按 MSS 切，避免走到分片这一步");
        System.out.println();
    }

    static void ttlPath(int hops) {
        System.out.printf("【TTL 逐跳递减，共走 %d 跳】%n", hops);
        int ttl = 64;
        for (int i = 1; i <= hops; i++) {
            ttl--;
            if (ttl <= 0) {
                System.out.printf("  第 %2d 跳：TTL 减到 0，路由器丢弃并回 ICMP Time Exceeded%n", i);
                System.out.println();
                return;
            }
            System.out.printf("  第 %2d 跳：TTL=%2d，查路由表转发到下一跳%n", i, ttl);
        }
        System.out.printf("  到达目的主机，TTL 还剩 %d%n", ttl);
        System.out.println();
    }

    public static void main(String[] args) {
        encapsulation();
        headerTax();
        fragmentation(1500, 20, 20, 5000);
        ttlPath(5);
        ttlPath(70);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `encapsulation()` 改成打印 UDP 版本的封装栈（UDP 头 8 B），再算一遍 20 B 载荷的头部占比，与 TCP 版本比较。
 * 2. 写一个方法 `maxPayloadWithoutFragment(int mtu, boolean ipv6)`：IPv6 固定头 40 B、TCP 头 20 B，返回不分片时应用层最多能放多少字节。
 * 3. 用 `ttlPath` 的思路实现一个 `traceroute` 模拟器：给定一个「跳数 → 路由器名」的数组，从 TTL=1 开始逐次探测，打印每一跳的路由器。
 * 4. 思考题：为什么 QUIC 把「连接 ID」放在 UDP 载荷里，而不是像 TCP 那样用四元组标识连接？这与网络分层有什么冲突？
 * ============================================================
 */
