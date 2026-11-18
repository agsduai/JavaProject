/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-04
 * 课名：UDP 与 QUIC
 * ------------------------------------------------------------
 * 知识点：
 *   - UDP 只做四件事：**端口复用**、**校验和**、**长度**、**无连接发送**。没有握手、没有序号、没有重传、没有窗口、没有拥塞控制。头部固定 8 B，TCP 是 20 B 起。
 *   - **UDP 不保证任何东西**：可能丢、可能重复、可能乱序、可能比先发的后到。应用层要用就得自己补：序号、确认、重传、去重、分片重组。
 *   - **UDP 的价值在「不加延迟」**：DNS 一次查询一问一答，用 TCP 要先握手 1 个 RTT；游戏、语音、直播宁可丢一帧也不要等重传；QUIC 就是拿 UDP 当「裸 IP 通道」，在用户态自己实现可靠传输。
 *   - **TCP 的队头阻塞有两层**：传输层是「前一个段丢了，后面到达的段也不能交付给应用」；HTTP/1.1 应用层是「前一个响应没写完，后面的响应不能开始」。HTTP/2 解决了第二层（多路复用），但没解决第一层 —— 因为 TCP 只有一条有序字节流。
 *   - **QUIC 的核心设计**：在 UDP 之上实现**独立的流（stream）**，每条流有自己的序号和重组缓冲。流 A 丢包只阻塞流 A，流 B 照常交付 —— 这是真正消除传输层队头阻塞。
 *   - **QUIC 把 TLS 1.3 内置**：握手和密钥协商合并，1-RTT 建立安全连接，恢复会话时可以 **0-RTT** 直接带数据。TCP+TLS 是「先三次握手，再 TLS 握手」，至少 2 个 RTT。
 *   - **连接迁移**：TCP 用四元组标识连接，手机从 Wi-Fi 切到 4G，IP 一变连接就断。QUIC 用**连接 ID**，只要 ID 不变，换 IP/端口也能继续，切网不掉线。
 *   - **QUIC 的代价**：UDP 在很多企业网络和中间设备里被限速或直接封；用户态实现比内核 TCP 更吃 CPU；调试工具链不如 TCP 成熟。
 *   - **什么时候仍用 TCP**：内网服务间调用、需要内核零拷贝和成熟拥塞控制的场景、对中间设备兼容性要求高的场景。UDP 不是「更快的 TCP」，是「更自由的传输层」。
 *   - 易错点：以为 UDP 一定比 TCP 快（小包一问一答确实快，大流量下没有拥塞控制的 UDP 会把网络打崩）；以为 HTTP/3 就是「HTTP over UDP」（是 HTTP over QUIC，QUIC over UDP）；以为 QUIC 不用重传（它自己实现重传，只是按流隔离）。
 * ============================================================
 */

package com.heima.ch35;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// ==================== 示例代码 ====================

public class UdpQuic {
    /** 一个 UDP 数据报：带序号只是为了演示，真实 UDP 头部里没有序号 */
    record Datagram(int seq, String streamId, String payload) {
    }

    /** 模拟不可靠链路：随机丢弃、随机乱序、偶尔重复 */
    static class LossyLink {
        final Random rnd;
        final double lossRate;

        LossyLink(long seed, double lossRate) {
            this.rnd = new Random(seed);
            this.lossRate = lossRate;
        }

        List<Datagram> transmit(List<Datagram> in) {
            List<Datagram> out = new ArrayList<>();
            for (Datagram d : in) {
                double r = rnd.nextDouble();
                if (r < lossRate) {
                    continue;                       // 丢了
                }
                out.add(d);
                if (r > 0.97) {
                    out.add(d);                     // 极小概率重复
                }
            }
            out.sort(Comparator.comparingInt(d -> rnd.nextInt(100)));  // 打乱顺序
            return out;
        }
    }

    static void unreliableNature() {
        List<Datagram> sent = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            sent.add(new Datagram(i, "dns", "q" + i));
        }
        LossyLink link = new LossyLink(7, 0.25);
        List<Datagram> got = link.transmit(sent);

        System.out.println("【UDP：发出 12 个，链路丢 25%】");
        System.out.print("  收到顺序：");
        for (Datagram d : got) {
            System.out.print(d.seq() + " ");
        }
        System.out.println();
        List<Integer> seen = new ArrayList<>();
        int dup = 0;
        for (Datagram d : got) {
            if (seen.contains(d.seq())) {
                dup++;
            } else {
                seen.add(d.seq());
            }
        }
        System.out.printf("  实际到达 %d 个（含重复 %d 个），去重后 %d 个，丢失 %d 个%n",
                got.size(), dup, seen.size(), sent.size() - seen.size());
        System.out.println("  UDP 自己不管这些：丢包、重复、乱序全交给应用层");
        System.out.println();
    }

    static void tcpHeadOfLine() {
        System.out.println("【TCP 单流：一个段丢了，后面的都得等】");
        Map<Integer, String> arrived = new HashMap<>();
        arrived.put(1, "A1");
        arrived.put(3, "A3");
        arrived.put(4, "A4");
        int expect = 0;
        System.out.println("  段 0 丢失，段 1/3/4 已到达接收缓冲");
        for (int seq : new int[]{0, 1, 2, 3, 4}) {
            if (!arrived.containsKey(seq)) {
                System.out.printf("  等待段 %d ... 后面已到的 %s 全部不能交付%n", seq,
                        new java.util.TreeSet<>(arrived.keySet()));
                break;
            }
            expect = seq + 1;
        }
        System.out.printf("  应用层只能读到段 0 之前的数据，交付位置=%d%n", expect);
        System.out.println("  这就是传输层队头阻塞：一条有序字节流，缺口堵住所有后续数据");
        System.out.println();
    }

    static void quicPerStream() {
        System.out.println("【QUIC：按流独立重组，A 流丢包不影响 B 流】");
        List<Datagram> wire = List.of(
                new Datagram(0, "A", "a0"),
                new Datagram(0, "B", "b0"),
                new Datagram(1, "A", "a1"),
                new Datagram(1, "B", "b1"),
                new Datagram(2, "B", "b2"),
                new Datagram(3, "B", "b3"));
        Map<String, Map<Integer, String>> buffers = new HashMap<>();
        for (Datagram d : wire) {
            if (d.streamId().equals("A") && d.seq() == 2) {
                System.out.println("  A 流第 2 个包在链路上丢了");
                continue;
            }
            buffers.computeIfAbsent(d.streamId(), k -> new HashMap<>()).put(d.seq(), d.payload());
        }
        for (String sid : new String[]{"A", "B"}) {
            Map<Integer, String> buf = buffers.get(sid);
            StringBuilder sb = new StringBuilder();
            int next = 0;
            while (buf.containsKey(next)) {
                sb.append(buf.get(next)).append(' ');
                next++;
            }
            System.out.printf("  %s 流可交付：%s（已连续到序号 %d）%n", sid, sb.toString().trim(), next - 1);
        }
        System.out.println("  B 流完全不受影响，A 流只等自己那一个包 —— 队头阻塞被限制在单条流内");
        System.out.println();
    }

    static void handshakeRounds() {
        System.out.println("【建立安全连接要几个 RTT】");
        System.out.println("  TCP + TLS 1.3：TCP 三次握手 1 RTT + TLS 握手 1 RTT = 2 RTT 才能发第一个请求");
        System.out.println("  QUIC（首次）：传输参数 + TLS 1.3 合并 = 1 RTT");
        System.out.println("  QUIC（会话恢复）：0-RTT，第一个包就能带应用数据");
        System.out.println("  跨洲链路 RTT 200 ms 时，2 RTT 与 1 RTT 差 200 ms，首屏感知很明显");
        System.out.println();
    }

    static void connectionMigration() {
        System.out.println("【连接迁移：手机从 Wi-Fi 切到 4G】");
        System.out.println("  TCP：连接 = 源IP:源端口 + 目的IP:目的端口，IP 一变四元组就变了，连接直接断");
        System.out.println("  QUIC：连接 = 连接 ID，包头上带 ID，换了 IP/端口照样认得出是同一条连接");
        System.out.println("  所以 QUIC 能做到切网不重连、视频通话不断流");
        System.out.println();
    }

    static void whenStillTcp() {
        System.out.println("【什么时候还是该用 TCP】");
        System.out.println("  1) 内网 RPC：延迟本来就低，内核 TCP + 零拷贝更省 CPU");
        System.out.println("  2) 中间设备严格的环境：不少企业防火墙对 UDP 限速甚至封禁 443/UDP");
        System.out.println("  3) 需要成熟拥塞控制与生态工具：抓包、调优、监控都更成熟");
        System.out.println("  UDP 不是「更快的 TCP」：没有拥塞控制的大流量 UDP 会把整条链路打崩");
        System.out.println();
    }

    public static void main(String[] args) {
        unreliableNature();
        tcpHeadOfLine();
        quicPerStream();
        handshakeRounds();
        connectionMigration();
        whenStillTcp();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `LossyLink` 加一个 `reorderWindow` 参数，模拟「只在最近 k 个包内乱序」的链路，观察去重逻辑是否需要调整。
 * 2. 实现一个极简的「可靠 UDP」：发送端保存未确认包并定时重传，接收端回 ACK 并去重，在 `lossRate=0.3` 下把 20 个包全部送达。
 * 3. 在 `quicPerStream` 里加入流优先级：B 流是关键流，A 流是低优先级，丢包时优先重传 B 流，打印重传顺序。
 * 4. 思考题：QUIC 的 0-RTT 有什么安全风险？为什么服务端要对 0-RTT 数据做重放防护？
 * ============================================================
 */
