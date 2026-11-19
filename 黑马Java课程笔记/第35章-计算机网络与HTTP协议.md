# 第35章 计算机网络与 HTTP 协议

第34章讨论的是"单个进程内部怎么组织数据"，本章讨论"两个进程之间怎么把数据送过去"。日常写 `httpClient.send()` 只需要一行，但这一行背后：DNS 把域名换成 IP，TCP 三次握手建立连接，TLS 协商出密钥，HTTP 报文被切成帧发出去，中途可能经过 CDN 和负载均衡，对端可能返回 304 让你用缓存。任何一环出问题，表现都是"接口超时"或"偶发 502"，不懂协议就只能靠重启。本章按「分层 → TCP 握手 → 可靠传输 → UDP/QUIC → HTTP 报文 → 缓存 → TLS → HTTP/2 → DNS/CDN → WebSocket → 综合案例」的顺序讲，把每个阶段的状态机、报文字段、抓包能看到的东西都摊开。所有示例仍是纯 JDK：TCP 状态机用枚举真实迁移，滑动窗口与拥塞控制用手写模拟器跑一遍，HTTP 报文自己拼自己解析，TLS 握手流程用状态表还原，`HttpServer` + `HttpClient` 真起服务打请求，不需要装 Wireshark 或 Nginx。

### 35-01 网络分层与封装解封装

**知识点**
- **分层不是学术洁癖，是分工**。应用层只管「我要传什么」，传输层管「丢了怎么办、按什么顺序交」，网络层管「怎么从 A 走到 B」，链路层管「这一跳怎么送出去」。每层只加自己那份头部，谁也不用关心别人的细节。
- **封装（encapsulation）**：数据从上往下走，每层在前面加一个头部。HTTP 报文 → 加 TCP 头（端口、序号）→ 加 IP 头（源/目的 IP、TTL）→ 加以太网头（MAC）→ 变成电信号。接收方反过来一层层剥，叫**解封装**。
- **头部开销是真实成本**：以太网头 14 B + IP 头 20 B + TCP 头 20 B + 帧尾 FCS 4 B = 58 B。传 20 B 的心跳包，74% 的带宽在传头部；传 1460 B 的满包，只有 3.8%。这就是「小包不划算」的算术根源。
- **MTU 与 MSS**：链路层一次能承载的最大帧叫 MTU，以太网通常 1500 B。减去 IP 头 20 B 和 TCP 头 20 B，TCP 单段最多 1460 B，这个值叫 **MSS**。超过 MSS 的数据由 IP 层**分片**，每片都要重复一个 IP 头，接收端还要重组，代价很高。
- **分片很危险**：任何一片丢了，整个报文都要重传；防火墙和 NAT 对分片的处理还经常不一致。所以 TCP 会按 MSS 主动切分，应用层也该避免发超大报文。
- **TTL 是防环的**：IP 头里的 TTL 每经过一个路由器减 1，减到 0 就丢弃并回一个 ICMP Time Exceeded。它保证网络里有环路时包不会永远转下去，`traceroute` 就是靠逐步加大 TTL 探路的。
- **协议号与端口分工不同**：IP 头里的协议号（TCP=6，UDP=17，ICMP=1）决定交给哪个传输层协议；TCP/UDP 头里的端口决定交给哪个进程。IP 定位主机，端口定位进程。
- **一次 HTTP 请求的真实路径**：应用层组报文 → TCP 分段 → IP 打包 → 网卡发帧 → 交换机/路由器逐跳转发 → 目的主机逐层解封装 → 交给 80/443 端口的进程。
- **常见误解**：以为「HTTP 是应用层所以跟 TCP 无关」——HTTP/1.1 的队头阻塞、HTTP/2 的多路复用、HTTP/3 换 QUIC，全都是传输层特性在决定应用层表现。
- 易错点：把 MTU 和 MSS 混为一谈（差 40 B）；以为分片是 TCP 干的（是 IP 干的）；用 `ping -f` 打不分片包来探测 MTU 时忘了 IPv4 头也可能带选项。

**示例代码**
```java
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


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
```

**课后练习**
1. 把 `encapsulation()` 改成打印 UDP 版本的封装栈（UDP 头 8 B），再算一遍 20 B 载荷的头部占比，与 TCP 版本比较。
2. 写一个方法 `maxPayloadWithoutFragment(int mtu, boolean ipv6)`：IPv6 固定头 40 B、TCP 头 20 B，返回不分片时应用层最多能放多少字节。
3. 用 `ttlPath` 的思路实现一个 `traceroute` 模拟器：给定一个「跳数 → 路由器名」的数组，从 TTL=1 开始逐次探测，打印每一跳的路由器。
4. 思考题：为什么 QUIC 把「连接 ID」放在 UDP 载荷里，而不是像 TCP 那样用四元组标识连接？这与网络分层有什么冲突？

---

### 35-02 TCP 三次握手与四次挥手

**知识点**
- TCP 是**面向连接**的：通信前双方要先协商出各自的初始序号（ISN），并确认对方「在线且愿意收」。这个协商过程就是三次握手。
- **三次握手的每一步**：① client 发 SYN（seq=x），进入 `SYN_SENT`；② server 回 SYN+ACK（seq=y, ack=x+1），进入 `SYN_RCVD`；③ client 回 ACK（ack=y+1），双方进入 `ESTABLISHED`。SYN 和 FIN 各消耗一个序号，所以确认号要 +1。
- **为什么必须三次**：两次握手时，一个在网络里滞留很久的旧 SYN 到达 server，server 会直接建立连接并分配资源，而 client 根本不认这条连接，资源就空转到超时。第三次 ACK 让 server 确认「client 真的收到了我的 SYN+ACK」，即「这是本次连接，不是历史残留」。
- **ISN 为什么随机**：如果 ISN 可预测，攻击者就能伪造报文注入连接（序列号攻击）。Linux 用时钟 + 四元组哈希生成，同一四元组短时间内不会重复。
- **SYN 队列与 accept 队列**：server 收到 SYN 后先把连接放进 SYN 队列（半连接），收到第三次 ACK 才移入 accept 队列（全连接），应用 `accept()` 取走。两个队列都有上限，被打满就丢新 SYN —— 这就是 SYN Flood 的原理，防御手段是 SYN Cookie。
- **四次挥手**：① 主动方发 FIN，进入 `FIN_WAIT_1`；② 被动方回 ACK，进入 `CLOSE_WAIT`，主动方进入 `FIN_WAIT_2`；③ 被动方把剩余数据发完后再发 FIN，进入 `LAST_ACK`；④ 主动方回 ACK，进入 `TIME_WAIT`，被动方收到后 `CLOSED`。
- **为什么挥手要四次**：TCP 是全双工的，一方说「我没数据要发了」不代表另一方也发完了。中间那两个报文（ACK 和 FIN）不能合并，因为被动方可能还有数据要传。这就是**半关闭**状态，HTTP 里 server 收完请求后还能把响应写完，靠的就是它。
- **TIME_WAIT 要等 2MSL**：一是保证最后那个 ACK 能到达（丢了对方会重发 FIN，主动方还能再回一次）；二是让这条连接的重复报文在网络中自然消亡，避免污染下一个用相同四元组的新连接。MSL 通常取 30 s 或 60 s。
- **CLOSE_WAIT 堆积是代码问题**：大量 `CLOSE_WAIT` 说明对端已经关了，本进程却没调 `close()` —— 通常是忘了关流、异常分支漏了 `finally`。`TIME_WAIT` 多则多半是短连接太频繁，该上连接池或长连接。
- 易错点：把 `TIME_WAIT` 当成 bug 去「优化」掉（关掉它会让旧报文串进新连接）；以为 `close()` 一定发 FIN（还有数据没发完时只是半关闭）；在 `SYN_SENT` 阶段就认为连接已建立。

**示例代码**
```java
public class TcpHandshake {
    /** 只保留与握手/挥手相关的状态，省略 CLOSING 等少见分支 */
    enum State {
        CLOSED, LISTEN, SYN_SENT, SYN_RCVD, ESTABLISHED,
        FIN_WAIT_1, FIN_WAIT_2, CLOSE_WAIT, LAST_ACK, TIME_WAIT
    }

    /** 连接的一端：角色、状态、下一个可用序号 */
    static class Endpoint {
        final String role;
        State state;
        long nextSeq;

        Endpoint(String role, State state, long nextSeq) {
            this.role = role;
            this.state = state;
            this.nextSeq = nextSeq;
        }

        /** SYN / FIN 各占一个序号，普通数据按字节数递增（这里简化为 1） */
        long takeSeq() {
            long s = nextSeq;
            nextSeq++;
            return s;
        }

        void moveTo(State s) {
            state = s;
        }
    }

    static void threeWay() {
        Endpoint client = new Endpoint("client", State.CLOSED, 1000);
        Endpoint server = new Endpoint("server", State.LISTEN, 5000);

        System.out.println("【三次握手】");
        long cSeq = client.takeSeq();
        client.moveTo(State.SYN_SENT);
        System.out.printf("  1) client 发 SYN       seq=%d          client: CLOSED -> SYN_SENT%n", cSeq);

        long sSeq = server.takeSeq();
        server.moveTo(State.SYN_RCVD);
        System.out.printf("  2) server 回 SYN+ACK   seq=%d ack=%d    server: LISTEN -> SYN_RCVD%n",
                sSeq, cSeq + 1);

        client.moveTo(State.ESTABLISHED);
        System.out.printf("  3) client 回 ACK       ack=%d           client: SYN_SENT -> ESTABLISHED%n",
                sSeq + 1);
        server.moveTo(State.ESTABLISHED);
        System.out.println("     server 收到 ACK                      server: SYN_RCVD -> ESTABLISHED");
        System.out.printf("  之后 client 从 seq=%d 发数据，server 从 seq=%d 发数据%n",
                client.nextSeq, server.nextSeq);
        System.out.println();
    }

    static void whyThree() {
        System.out.println("【为什么不能只握手两次】");
        long stale = 700;
        System.out.printf("  30 秒前一条连接的 SYN(seq=%d) 在网络里滞留，现在才到 server%n", stale);
        System.out.println("  两次握手：server 一收到就 ESTABLISHED 并分配接收缓冲，但 client 不认这条连接");
        System.out.println("            -> 连接空转到超时，攻击者可以用大量伪造 SYN 把缓冲区耗光");
        System.out.println("  三次握手：client 收到 SYN+ACK 后发现 ack 不是自己这次期望的值，直接回 RST");
        System.out.printf("            本次 client 期望的 ack 是 %d，迟到的报文对不上%n", 1001L);
        System.out.println("  另外 ISN 必须随机：可预测的序号会让攻击者能伪造后续数据段");
        System.out.println();
    }

    static void fourWay() {
        Endpoint client = new Endpoint("client", State.ESTABLISHED, 1000);
        Endpoint server = new Endpoint("server", State.ESTABLISHED, 5000);

        System.out.println("【四次挥手：client 主动关闭】");
        long cFin = client.takeSeq();
        client.moveTo(State.FIN_WAIT_1);
        System.out.printf("  1) client 发 FIN        seq=%d          client: ESTABLISHED -> FIN_WAIT_1%n", cFin);

        server.moveTo(State.CLOSE_WAIT);
        System.out.printf("  2) server 回 ACK        ack=%d          server: ESTABLISHED -> CLOSE_WAIT%n", cFin + 1);
        client.moveTo(State.FIN_WAIT_2);
        System.out.println("     client: FIN_WAIT_1 -> FIN_WAIT_2");
        System.out.println("     此刻是半关闭：client 不再发数据，但 server 还能把响应写完");

        long sFin = server.takeSeq();
        server.moveTo(State.LAST_ACK);
        System.out.printf("  3) server 发 FIN+ACK    seq=%d ack=%d   server: CLOSE_WAIT -> LAST_ACK%n", sFin, cFin + 1);

        client.moveTo(State.TIME_WAIT);
        System.out.printf("  4) client 回 ACK        ack=%d          client: FIN_WAIT_2 -> TIME_WAIT%n", sFin + 1);
        server.moveTo(State.CLOSED);
        System.out.println("     server 收到 ACK                      server: LAST_ACK -> CLOSED");

        int mslSeconds = 60;
        System.out.printf("  client 还要在 TIME_WAIT 等 2MSL = %d 秒才真正 CLOSED%n", 2 * mslSeconds);
        System.out.println("  等 2MSL 的两个原因：");
        System.out.println("    1) 最后那个 ACK 若丢了，对方会重发 FIN，client 还能再回一次 ACK");
        System.out.println("    2) 让这条连接的重复报文在网络中消亡，不污染同四元组的新连接");
        System.out.println();
    }

    static void backlogOverflow() {
        int synQueue = 3;
        int incoming = 5;
        System.out.printf("【SYN 队列只有 %d 个位置，同时来了 %d 个 SYN】%n", synQueue, incoming);
        for (int i = 1; i <= incoming; i++) {
            if (i <= synQueue) {
                System.out.printf("  SYN #%d：入队，回 SYN+ACK%n", i);
            } else {
                System.out.printf("  SYN #%d：队列满，直接丢弃（client 会在 1s/2s/4s 后指数退避重试）%n", i);
            }
        }
        System.out.println("  SYN Flood 就是这个队列被打满；防御靠 SYN Cookie：不回队列，把状态编码进 seq");
        System.out.println();
    }

    static void closeWaitLeak() {
        System.out.println("【线上两种异常状态的含义】");
        System.out.println("  CLOSE_WAIT 堆积  -> 对端已发 FIN，本进程没调 close()：忘关流、异常分支漏了 finally");
        System.out.println("  TIME_WAIT 堆积   -> 短连接太频繁：该用连接池、长连接或 HTTP keep-alive");
        System.out.println("  把 TIME_WAIT 直接关掉（tcp_tw_reuse 滥用）会让旧连接的报文串进新连接，得不偿失");
        System.out.println();
    }

    public static void main(String[] args) {
        threeWay();
        whyThree();
        fourWay();
        backlogOverflow();
        closeWaitLeak();
    }
}
```

**课后练习**
1. 给 `Endpoint` 加一个 `Set<State> history`，在每次 `moveTo` 时记录迁移路径，握手和挥手结束后打印完整的状态轨迹。
2. 实现被动关闭版本的四次挥手：由 server 主动发 FIN，画出两端的六步状态变化。
3. 模拟「第三次 ACK 丢失」：client 已 `ESTABLISHED`，server 还在 `SYN_RCVD`，说明 server 会怎样重传 SYN+ACK、重传几次后放弃，以及 client 收到重复 SYN+ACK 时应该怎么做。
4. 思考题：为什么 `TIME_WAIT` 出现在**主动关闭**的一方，而不是被动关闭的一方？如果两端同时关闭（同时发 FIN），状态机会走哪条路径？

---

### 35-03 TCP 可靠传输与拥塞控制

**知识点**
- TCP 的可靠性靠四件事：**序号**（每个字节都有编号，接收方按序号重排）、**确认**（ACK 告诉对方我收到了哪之前的所有字节）、**重传**（超时没收到 ACK 就重发）、**校验和**（头部和数据一起算，坏了就丢）。
- **累计确认**：ACK=n 表示「n 之前的字节我全收到了」，不表示只收到第 n 个。所以 ACK 号只会单调前进，中间丢了也会被后面的 ACK 覆盖 —— 这叫**累积性**。
- **滑动窗口**解决「发太快对方接不住」：接收方在 ACK 里带一个 `window`，发送方最多只能有 `window` 个未确认字节在途。窗口为 0 时发送方停发，靠**零窗口探测**定时询问。
- **快重传 vs 超时重传**：收到 3 个重复 ACK（同一 ACK 号连发三次）说明中间那段丢了，立刻重传，不用等 RTO —— 这是快重传（fast retransmit），把恢复时间从「一个 RTO」缩短到「几个 RTT」。
- **RTO 是算出来的，不是拍脑袋**：用 Jacobson/Karels 算法维护平滑 RTT（SRTT）和 RTT 偏差（RTTVAR）：`SRTT = 0.875*SRTT + 0.125*RTT`，`RTTVAR = 0.75*RTTVAR + 0.25*|SRTT-RTT|`，`RTO = SRTT + 4*RTTVAR`。RTO 有下限（Linux 200 ms），避免过度重传。
- **拥塞控制四个阶段**：**慢启动**（cwnd 每 RTT 翻倍，指数增长）→ 到 `ssthresh` 转**拥塞避免**（每 RTT 加 1 个 MSS，线性增长）→ 丢包时**快速恢复**（Reno：`ssthresh=cwnd/2`，`cwnd=ssthresh`）→ 超时就回到慢启动（`cwnd=1`）。
- **Tahoe 与 Reno 的区别**：Tahoe 一遇丢包就把 cwnd 打回 1，Reno 用快重传 + 快恢复只减半。现代 Linux 默认 **CUBIC**，用三次函数代替线性增长，高带宽长距离链路上恢复更快；**BBR** 则完全不看丢包，直接估带宽和最小 RTT。
- **流量控制 vs 拥塞控制**：前者是接收方说「我处理不过来」，后者是网络说「路上堵了」。两者取最小值决定实际发送速率。
- **Nagle 算法与延迟 ACK 的死锁**：Nagle 攒小包等 ACK，延迟 ACK 等更多数据一起确认，两边互等就可能卡住 40 ms。实时性要求高的场景用 `TCP_NODELAY` 关掉 Nagle。
- 易错点：以为 ACK 是「逐包确认」（是累计确认）；把 `ssthresh` 和 `cwnd` 搞混（前者是切换阈值，后者是当前窗口）；认为丢包一定是网络故障（也可能是链路误码、队列溢出）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class TcpReliable {
    /** 简化发送端：窗口、累计确认、重复 ACK 计数 */
    static class Sender {
        final int window;
        int base;
        int next;
        int dupAcks;
        int retransmits;
        final List<Integer> delivered = new ArrayList<>();

        Sender(int window) {
            this.window = window;
        }

        boolean canSend() {
            return next < base + window;
        }

        int send() {
            return next++;
        }

        /** 返回 true 表示触发了快重传 */
        boolean onAck(int ack) {
            if (ack > base) {
                for (int s = base; s < ack; s++) {
                    delivered.add(s);
                }
                base = ack;
                dupAcks = 0;
                return false;
            }
            if (ack == base) {
                dupAcks++;
            }
            return dupAcks == 3;
        }

        void onTimeout() {
            retransmits++;
        }
    }

    static void slidingWindow() {
        Sender s = new Sender(4);
        System.out.println("【滑动窗口 + 累计确认 + 快重传】");
        while (s.canSend()) {
            System.out.print("  发送段 " + s.send() + "\n");
        }
        System.out.printf("  base=%d next=%d，窗口 [%d,%d)%n", s.base, s.next, s.base, s.base + s.window);

        System.out.println("  收到 ACK=2（0、1 累计确认）");
        s.onAck(2);
        System.out.printf("    base=%d，窗口右移，可再发两段%n", s.base);
        while (s.canSend()) {
            System.out.println("  发送段 " + s.send());
        }

        System.out.println("  段 2 丢失，段 3/4/5 到达，接收方只能一直回 ACK=2");
        for (int i = 0; i < 3; i++) {
            boolean fast = s.onAck(2);
            System.out.printf("    第 %d 个重复 ACK -> dupAcks=%d%s%n",
                    i + 1, s.dupAcks, fast ? "，触发快重传段 2" : "");
        }
        s.onTimeout();
        System.out.println("  重传段 2 后，接收方回 ACK=6，累计确认 2~5");
        s.onAck(6);
        System.out.printf("  已交付：%s，base=%d，重传次数=%d%n", s.delivered, s.base, s.retransmits);
        System.out.println("  关键：不用等 RTO，3 个重复 ACK 就能判断丢包");
        System.out.println();
    }

    static void rtoEstimate() {
        System.out.println("【RTO 怎么算：Jacobson/Karels】");
        double srtt = -1;
        double rttvar = 0;
        long[] samples = {100, 120, 90, 400, 110};
        for (long r : samples) {
            if (srtt < 0) {
                srtt = r;
                rttvar = r / 2.0;
            } else {
                rttvar = 0.75 * rttvar + 0.25 * Math.abs(srtt - r);
                srtt = 0.875 * srtt + 0.125 * r;
            }
            double rto = srtt + Math.max(4 * rttvar, 1);
            System.out.printf("  采样 RTT=%3d ms -> SRTT=%6.1f RTTVAR=%6.1f RTO=%6.1f ms%n",
                    r, srtt, rttvar, rto);
        }
        System.out.println("  第 4 个采样突然到 400 ms，RTO 立刻抬高：网络抖了就先等久一点，别乱重传");
        System.out.println("  实际实现还有下限 200 ms、上限 120 s，并且指数退避");
        System.out.println();
    }

    /** Reno：丢包走快恢复（减半），超时回到 cwnd=1 */
    static void congestionReno(int rounds, Set<Integer> lossRounds) {
        int cwnd = 1;
        int ssthresh = 16;
        System.out.println("【拥塞控制：Reno】");
        for (int r = 1; r <= rounds; r++) {
            boolean loss = lossRounds.contains(r);
            String phase = cwnd < ssthresh ? "慢启动" : "拥塞避免";
            System.out.printf("  第%2d 轮  阶段=%-4s cwnd=%2d ssthresh=%2d", r, phase, cwnd, ssthresh);
            if (loss) {
                ssthresh = Math.max(cwnd / 2, 2);
                cwnd = ssthresh;
                System.out.printf("  丢包 -> 快恢复：ssthresh=%d，cwnd=%d%n", ssthresh, cwnd);
            } else if (cwnd < ssthresh) {
                cwnd *= 2;
                System.out.println("  cwnd 翻倍（指数增长）");
            } else {
                cwnd += 1;
                System.out.println("  cwnd +1（线性增长）");
            }
        }
        System.out.println();
    }

    /** Tahoe：同样的丢包，cwnd 直接回 1 */
    static void congestionTahoe(int rounds, Set<Integer> lossRounds) {
        int cwnd = 1;
        int ssthresh = 16;
        System.out.println("【拥塞控制：Tahoe（对比）】");
        for (int r = 1; r <= rounds; r++) {
            boolean loss = lossRounds.contains(r);
            String phase = cwnd < ssthresh ? "慢启动" : "拥塞避免";
            System.out.printf("  第%2d 轮  阶段=%-4s cwnd=%2d ssthresh=%2d", r, phase, cwnd, ssthresh);
            if (loss) {
                ssthresh = Math.max(cwnd / 2, 2);
                cwnd = 1;
                System.out.printf("  丢包 -> 慢启动重启：ssthresh=%d，cwnd=1%n", ssthresh);
            } else if (cwnd < ssthresh) {
                cwnd *= 2;
                System.out.println("  cwnd 翻倍（指数增长）");
            } else {
                cwnd += 1;
                System.out.println("  cwnd +1（线性增长）");
            }
        }
        System.out.println();
    }

    static void nagleDeadlock() {
        System.out.println("【Nagle + 延迟 ACK 的 40 ms 卡顿】");
        System.out.println("  客户端：小包先攒着，等前一个包的 ACK 到了再发");
        System.out.println("  服务端：收到小包先不回 ACK，等 40 ms 看有没有更多数据可以一起确认");
        System.out.println("  两边互等 -> 每个小请求多出约 40 ms 延迟");
        System.out.println("  解决：实时性优先的连接设 TCP_NODELAY，或者应用层自己合并写");
        System.out.println();
    }

    public static void main(String[] args) {
        slidingWindow();
        rtoEstimate();
        congestionReno(12, Set.of(4, 9));
        congestionTahoe(12, Set.of(4, 9));
        nagleDeadlock();
    }
}
```

**课后练习**
1. 给 `Sender` 加一个 `receiveWindow` 字段，让 `canSend()` 同时受拥塞窗口和接收窗口限制，模拟「接收方处理慢」导致的停发。
2. 实现零窗口探测：当接收方通告 `window=0` 时，发送方每隔一段时间发一个 1 字节探测包，直到窗口重新打开。
3. 把 `congestionReno` 改成 CUBIC 风格的增长：`cwnd` 按 `C*(t-K)³+Wmax` 变化，简化成每轮增量递减再递增，打印曲线对比 Reno。
4. 思考题：为什么快重传要求「3 个」重复 ACK，而不是 1 个？如果链路上只是乱序而非丢包，快重传会带来什么后果？

---

### 35-04 UDP 与 QUIC

**知识点**
- UDP 只做四件事：**端口复用**、**校验和**、**长度**、**无连接发送**。没有握手、没有序号、没有重传、没有窗口、没有拥塞控制。头部固定 8 B，TCP 是 20 B 起。
- **UDP 不保证任何东西**：可能丢、可能重复、可能乱序、可能比先发的后到。应用层要用就得自己补：序号、确认、重传、去重、分片重组。
- **UDP 的价值在「不加延迟」**：DNS 一次查询一问一答，用 TCP 要先握手 1 个 RTT；游戏、语音、直播宁可丢一帧也不要等重传；QUIC 就是拿 UDP 当「裸 IP 通道」，在用户态自己实现可靠传输。
- **TCP 的队头阻塞有两层**：传输层是「前一个段丢了，后面到达的段也不能交付给应用」；HTTP/1.1 应用层是「前一个响应没写完，后面的响应不能开始」。HTTP/2 解决了第二层（多路复用），但没解决第一层 —— 因为 TCP 只有一条有序字节流。
- **QUIC 的核心设计**：在 UDP 之上实现**独立的流（stream）**，每条流有自己的序号和重组缓冲。流 A 丢包只阻塞流 A，流 B 照常交付 —— 这是真正消除传输层队头阻塞。
- **QUIC 把 TLS 1.3 内置**：握手和密钥协商合并，1-RTT 建立安全连接，恢复会话时可以 **0-RTT** 直接带数据。TCP+TLS 是「先三次握手，再 TLS 握手」，至少 2 个 RTT。
- **连接迁移**：TCP 用四元组标识连接，手机从 Wi-Fi 切到 4G，IP 一变连接就断。QUIC 用**连接 ID**，只要 ID 不变，换 IP/端口也能继续，切网不掉线。
- **QUIC 的代价**：UDP 在很多企业网络和中间设备里被限速或直接封；用户态实现比内核 TCP 更吃 CPU；调试工具链不如 TCP 成熟。
- **什么时候仍用 TCP**：内网服务间调用、需要内核零拷贝和成熟拥塞控制的场景、对中间设备兼容性要求高的场景。UDP 不是「更快的 TCP」，是「更自由的传输层」。
- 易错点：以为 UDP 一定比 TCP 快（小包一问一答确实快，大流量下没有拥塞控制的 UDP 会把网络打崩）；以为 HTTP/3 就是「HTTP over UDP」（是 HTTP over QUIC，QUIC over UDP）；以为 QUIC 不用重传（它自己实现重传，只是按流隔离）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


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
```

**课后练习**
1. 给 `LossyLink` 加一个 `reorderWindow` 参数，模拟「只在最近 k 个包内乱序」的链路，观察去重逻辑是否需要调整。
2. 实现一个极简的「可靠 UDP」：发送端保存未确认包并定时重传，接收端回 ACK 并去重，在 `lossRate=0.3` 下把 20 个包全部送达。
3. 在 `quicPerStream` 里加入流优先级：B 流是关键流，A 流是低优先级，丢包时优先重传 B 流，打印重传顺序。
4. 思考题：QUIC 的 0-RTT 有什么安全风险？为什么服务端要对 0-RTT 数据做重放防护？

---

### 35-05 HTTP/1.1 报文格式与状态码

**知识点**
- **报文结构**：请求 = 请求行（`GET /path HTTP/1.1`）+ 头部（`Key: Value`，每行一个）+ 空行 + 可选 body。响应 = 状态行（`HTTP/1.1 200 OK`）+ 头部 + 空行 + body。**空行是分界线，不能省**。
- **方法语义**：`GET` 查（安全、幂等）、`POST` 提交（不安全、不幂等）、`PUT` 全量替换（幂等）、`PATCH` 局部更新（不保证幂等）、`DELETE` 删除（幂等）、`HEAD` 只要头部、`OPTIONS` 探测能力（CORS 预检就用它）。
- **幂等的实际意义**：网络超时后客户端敢不敢重试。`GET`/`PUT`/`DELETE` 重试一次结果一样；`POST` 重试可能下两单，所以要么加幂等键，要么别自动重试。
- **状态码分五类**：`1xx` 信息（`101 Switching Protocols` 升级 WebSocket）、`2xx` 成功（`200`、`201 Created`、`204 No Content`、`206 Partial Content`）、`3xx` 重定向（`301` 永久、`302` 临时、`304 Not Modified`、`307/308` 保持方法与 body）、`4xx` 客户端错（`400`、`401` 未认证、`403` 无权限、`404`、`405` 方法不允许、`409` 冲突、`429` 限流）、`5xx` 服务端错（`500`、`502` 网关收到坏响应、`503` 不可用、`504` 网关超时）。
- **`401` 与 `403` 的区别**：401 是「你是谁我不知道」，要带 `WWW-Authenticate`；403 是「我知道你是谁，但你不能干这个」。搞混会让前端不知道该跳登录还是提示无权限。
- **`301` 与 `302` 的代价**：301 会被浏览器长期缓存，改回来很难；不确定时用 302/307。`307`/`308` 保证重定向后方法和 body 不变，`301`/`302` 历史上会把 POST 改成 GET。
- **`Host` 头是 HTTP/1.1 强制的**：一台 IP 上跑多个站点靠它区分虚拟主机。缺了它服务器只能回 400。
- **`Content-Length` 与 `Transfer-Encoding: chunked` 二选一**：知道长度就用前者；边生成边发（大文件、流式响应）用后者，每块前面写十六进制长度，最后以 `0` 长度块结束。两个同时出现是请求走私的经典入口，服务端必须拒绝。
- **`Connection: keep-alive`** 让一条 TCP 连接跑多个请求，省掉重复握手；HTTP/1.1 默认开启。但一条连接同一时刻只能有一个在途请求，这就是**应用层队头阻塞**。
- 易错点：把 `204` 当 `200` 用还带 body（规范禁止）；用 `200` 返回错误信息（监控和网关都识别不了）；`Content-Length` 写成字符数而不是字节数（中文会截断）。

**示例代码**
```java
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class HttpMessage {
    /** 解析后的请求：方法、路径、版本、头部（大小写不敏感）、body */
    record Request(String method, String path, String version,
                   Map<String, String> headers, String body) {
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    static Request parseRequest(String raw) {
        int split = raw.indexOf("\r\n\r\n");
        String head = split < 0 ? raw : raw.substring(0, split);
        String body = split < 0 ? "" : raw.substring(split + 4);
        String[] lines = head.split("\r\n");
        String[] first = lines[0].split(" ");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) {
                headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT),
                        lines[i].substring(c + 1).trim());
            }
        }
        return new Request(first[0], first[1], first[2], headers, body);
    }

    static void parseDemo() {
        String raw = "POST /api/orders?from=web HTTP/1.1\r\n"
                + "Host: shop.example.com\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: 22\r\n"
                + "Connection: keep-alive\r\n"
                + "\r\n"
                + "{\"sku\":\"A1\",\"count\":2}";
        Request req = parseRequest(raw);
        System.out.println("【解析请求报文】");
        System.out.printf("  请求行：method=%s path=%s version=%s%n", req.method(), req.path(), req.version());
        System.out.println("  头部：");
        req.headers().forEach((k, v) -> System.out.printf("    %-14s = %s%n", k, v));
        System.out.printf("  body：%s（%d 字节）%n",
                req.body(), req.body().getBytes(StandardCharsets.UTF_8).length);

        int declared = Integer.parseInt(req.header("content-length"));
        int actual = req.body().getBytes(StandardCharsets.UTF_8).length;
        System.out.printf("  Content-Length=%d，实际=%d -> %s%n",
                declared, actual, declared == actual ? "一致" : "不一致，服务端应回 400");
        System.out.println();
    }

    static void lengthVsChunked() {
        System.out.println("【两种 body 定界方式】");
        System.out.println("  Content-Length: 22   一次说清总长度，适合已知大小的响应");
        String chunked = "HTTP/1.1 200 OK\r\n"
                + "Transfer-Encoding: chunked\r\n"
                + "\r\n"
                + "7\r\n{\"name\"\r\n"
                + "6\r\n:\"张三\"\r\n"
                + "1\r\n}\r\n"
                + "0\r\n\r\n";
        System.out.println("  Transfer-Encoding: chunked  边算边发，每块前缀是十六进制长度");
        System.out.println("  原始字节（按行展示，行尾是 CRLF）：");
        for (String line : chunked.split("\r\n")) {
            System.out.println("    | " + line);
        }
        System.out.println("  以 0 长度块结束。两个头同时出现必须拒绝 —— 这是请求走私的入口");
        System.out.println();
    }

    static String reason(int code) {
        return switch (code) {
            case 101 -> "Switching Protocols";
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 206 -> "Partial Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Unknown";
        };
    }

    static String meaning(int code) {
        return switch (code / 100) {
            case 1 -> "信息性：协议切换或继续发送";
            case 2 -> "成功：请求已被接受并处理";
            case 3 -> "重定向：需要去另一个地址取资源";
            case 4 -> "客户端错误：请求本身有问题，重试无用";
            case 5 -> "服务端错误：服务自身或上游出问题";
            default -> "";
        };
    }

    static void statusTable() {
        System.out.println("【状态码：记住语义比背全表重要】");
        int[] codes = {101, 200, 201, 204, 206, 301, 302, 304, 307,
                400, 401, 403, 404, 405, 409, 429, 500, 502, 503, 504};
        for (int c : codes) {
            System.out.printf("  %d %-20s %s%n", c, reason(c), meaning(c));
        }
        System.out.println();
    }

    static void idempotentRetry() {
        System.out.println("【超时之后敢不敢重试】");
        String[][] cases = {
                {"GET", "/orders/1001", "true", "安全且幂等，重试无副作用"},
                {"PUT", "/orders/1001", "true", "整体替换，重试结果相同"},
                {"DELETE", "/orders/1001", "true", "删两次和删一次结果一样"},
                {"POST", "/orders", "false", "重试可能创建两笔订单，需幂等键"},
                {"PATCH", "/orders/1001", "false", "增量更新重试会叠加"},
        };
        for (String[] c : cases) {
            System.out.printf("  %-7s %-14s 幂等=%-5s %s%n", c[0], c[1], c[2], c[3]);
        }
        System.out.println("  所以网关重试只对幂等方法开放；POST 要靠 Idempotency-Key 兜底");
        System.out.println();
    }

    static void keepAliveHOL() {
        System.out.println("【keep-alive 省了什么，又卡在哪】");
        System.out.println("  省：每次请求不再付 TCP 三次握手 + 慢启动的代价");
        System.out.println("  卡：一条连接同一时刻只能有一个在途请求");
        List<String> queue = new ArrayList<>(List.of("请求A(慢)", "请求B", "请求C"));
        System.out.println("  连接上排队的请求：" + queue);
        System.out.println("  请求A 处理了 800 ms，B 和 C 只能干等 —— 应用层队头阻塞");
        System.out.println("  浏览器对策：对同一域名开 6 条连接（所以域名分片曾经有用）");
        System.out.println("  协议对策：HTTP/2 多路复用，把请求拆成帧交错发送");
        System.out.println();
    }

    static void commonMistakes() {
        System.out.println("【三个高频坑】");
        System.out.println("  1) 204 带 body：规范禁止，部分客户端会读到脏数据");
        System.out.println("  2) 业务错误回 200：网关、监控、重试策略全部失效，应该用 4xx/5xx");
        System.out.println("  3) Content-Length 用 String.length()：中文按字节算，UTF-8 下「张三」是 6 字节不是 2");
        System.out.printf("     验证：\"张三\".length()=%d，UTF-8 字节数=%d%n",
                "张三".length(), "张三".getBytes(StandardCharsets.UTF_8).length);
        System.out.println();
    }

    public static void main(String[] args) {
        parseDemo();
        lengthVsChunked();
        statusTable();
        idempotentRetry();
        keepAliveHOL();
        commonMistakes();
    }
}
```

**课后练习**
1. 给 `parseRequest` 加上对 `Transfer-Encoding: chunked` 的解码，把分块 body 拼成完整字符串并校验结尾是否为 0 长度块。
2. 实现 `parseResponse(String raw)`，解析状态行与头部，并对 3xx 响应检查是否带 `Location`。
3. 写一个 `validateRequest(Request req)`：缺少 `Host` 回 400，`Content-Length` 与实际 body 字节数不符回 400，`Content-Length` 与 `Transfer-Encoding` 同时存在回 400，返回状态码。
4. 思考题：为什么 `302` 在历史上会把 POST 改成 GET，而 `307` 不会？这对支付类回调接口意味着什么？

---

### 35-06 HTTP 缓存：强缓存与协商缓存

**知识点**
- 缓存的价值是**省掉整条链路**：不发请求 > 发请求但回 304 > 回 200。所以优化的优先级永远是「能不请求就不请求」。
- **强缓存**：浏览器直接用本地副本，**一个字节都不发给服务器**。靠 `Cache-Control: max-age=秒` 或 `Expires`（绝对时间，已过时）控制。命中时 DevTools 显示 `from disk cache` / `from memory cache`。
- **协商缓存**：本地副本过期了，但还想确认能不能复用，于是带上校验器问服务器。服务器回 `304 Not Modified`（无 body），浏览器继续用本地副本。**省的是 body 带宽，不是往返**。
- **两组校验器**：`ETag` ↔ `If-None-Match`（内容指纹，优先级高）；`Last-Modified` ↔ `If-Modified-Since`（秒级精度，文件被改回原内容也判为变了）。两者都给时以 ETag 为准。
- **`ETag` 强弱之分**：强 ETag 要求字节完全一致；弱 ETag 带 `W/` 前缀，表示语义等价即可。压缩代理会改写 body，所以它会削弱 ETag。
- **`Cache-Control` 常用指令**：`max-age=N`（新鲜期）、`s-maxage`（只对共享缓存/CDN 生效）、`no-cache`（**可以存，但每次用前必须校验**）、`no-store`（**完全不许存**，含敏感数据）、`private`（只允许浏览器存）、`public`（CDN 也可存）、`must-revalidate`（过期后必须校验，不许用旧副本）、`immutable`（内容永不变，连刷新都不校验）。
- **`no-cache` 与 `no-store` 最容易被搞混**：前者是「先问再用」，后者是「根本别存」。存密码、token、身份证号的响应必须 `no-store`。
- **`Vary` 决定缓存键的维度**：`Vary: Accept-Encoding` 表示 gzip 版和原文版是两个缓存条目；`Vary: *` 等于不可缓存。漏了 `Vary` 会把 A 用户的个性化页面喂给 B 用户。
- **缓存决策顺序**：先看 `Cache-Control`，没有才看 `Expires`，都没有则用启发式（`Last-Modified` 的 10%）。所以只给 `Last-Modified` 的响应也会被强缓存，很多人不知道。
- **最佳实践**：带内容哈希的静态资源（`app.3f9a2c.js`）用 `max-age=31536000, immutable`；HTML 入口用 `no-cache`（每次都校验）；接口数据按业务定，用户私有数据 `private, no-store` 或短 `max-age`。
- 易错点：给 `index.html` 设一年强缓存导致发版后用户白屏；以为 304 能省 RTT（不能，只省 body）；用 `Last-Modified` 做精确变更检测（秒级精度不够）。

**示例代码**
```java
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;


public class HttpCache {
    /** 一条缓存条目：内容 + 校验器 + 新鲜期 */
    static class CacheEntry {
        final String url;
        final String body;
        final String etag;
        final long lastModifiedEpochSec;
        final int maxAgeSec;
        final boolean noStore;
        final boolean noCache;
        final String vary;
        long storedAtSec;

        CacheEntry(String url, String body, String etag, long lastModifiedEpochSec,
                   int maxAgeSec, boolean noStore, boolean noCache, String vary, long storedAtSec) {
            this.url = url;
            this.body = body;
            this.etag = etag;
            this.lastModifiedEpochSec = lastModifiedEpochSec;
            this.maxAgeSec = maxAgeSec;
            this.noStore = noStore;
            this.noCache = noCache;
            this.vary = vary;
            this.storedAtSec = storedAtSec;
        }

        boolean fresh(long nowSec) {
            return !noCache && !noStore && nowSec - storedAtSec < maxAgeSec;
        }

        String cacheControl() {
            StringBuilder sb = new StringBuilder();
            if (noStore) {
                sb.append("no-store");
            } else if (noCache) {
                sb.append("no-cache");
            } else {
                sb.append("max-age=").append(maxAgeSec);
            }
            if (vary != null) {
                sb.append(", Vary: ").append(vary);
            }
            return sb.toString();
        }
    }

    /** 极简浏览器缓存 + 极简服务器 */
    static class Browser {
        final Map<String, CacheEntry> store = new LinkedHashMap<>();
        int requests;
        int bytesFromNetwork;

        void get(String url, Server server, long nowSec, Map<String, String> requestHeaders) {
            CacheEntry e = store.get(url);
            if (e == null) {
                System.out.println("  [" + url + "] 本地无缓存 -> 发请求");
                fetch(url, server, nowSec);
                return;
            }
            if (e.fresh(nowSec)) {
                System.out.printf("  [%s] 强缓存命中（剩余 %d 秒）-> 不请求，from disk cache%n",
                        url, e.maxAgeSec - (nowSec - e.storedAtSec));
                return;
            }
            System.out.printf("  [%s] 缓存已过期 -> 带校验器协商%n", url);
            Map<String, String> h = new LinkedHashMap<>(requestHeaders);
            if (e.etag != null) {
                h.put("If-None-Match", e.etag);
            }
            h.put("If-Modified-Since", String.valueOf(e.lastModifiedEpochSec));
            int code = server.handle(url, h);
            requests++;
            if (code == 304) {
                System.out.println("    服务器回 304，复用本地副本，只付了一个 RTT");
                e.storedAtSec = nowSec;
            } else {
                System.out.println("    服务器回 200，内容变了，替换本地副本");
                fetch(url, server, nowSec);
            }
        }

        private void fetch(String url, Server server, long nowSec) {
            CacheEntry e = server.serve(url);
            requests++;
            if (e.noStore) {
                System.out.println("    响应 no-store：用完即弃，不写入缓存");
                return;
            }
            e.storedAtSec = nowSec;
            store.put(url, e);
            bytesFromNetwork += e.body.length();
            System.out.printf("    200 OK，缓存策略 [%s]，body %d 字符%n", e.cacheControl(), e.body.length());
        }
    }

    static class Server {
        final Map<String, CacheEntry> resources = new LinkedHashMap<>();
        int hits200;
        int hits304;

        void put(CacheEntry e) {
            resources.put(e.url, e);
        }

        CacheEntry serve(String url) {
            hits200++;
            CacheEntry src = resources.get(url);
            return new CacheEntry(src.url, src.body, src.etag, src.lastModifiedEpochSec,
                    src.maxAgeSec, src.noStore, src.noCache, src.vary, 0);
        }

        int handle(String url, Map<String, String> headers) {
            CacheEntry src = resources.get(url);
            if (src == null) {
                hits200++;
                return 404;
            }
            String inm = headers.get("If-None-Match");
            if (inm != null && inm.equals(src.etag)) {
                hits304++;
                return 304;
            }
            String ims = headers.get("If-Modified-Since");
            if (inm == null && ims != null
                    && Long.parseLong(ims) >= src.lastModifiedEpochSec) {
                hits304++;
                return 304;
            }
            hits200++;
            return 200;
        }
    }

    static void strongCache() {
        System.out.println("【强缓存：max-age 内完全不发请求】");
        Server server = new Server();
        server.put(new CacheEntry("/logo.png", "PNG-BYTES", "\"v1\"", 1000, 3600, false, false, null, 0));
        Browser browser = new Browser();
        long now = 10_000;
        for (int i = 0; i < 3; i++) {
            System.out.printf(" t=%ds%n", now - 10_000);
            browser.get("/logo.png", server, now, Map.of());
            now += 20;
        }
        System.out.println("  t=3601s，超过 max-age");
        browser.get("/logo.png", server, 10_000 + 3601, Map.of());
        System.out.printf("  合计：网络请求 %d 次，服务器 200=%d 次 304=%d 次%n",
                browser.requests, server.hits200, server.hits304);
        System.out.println();
    }

    static void revalidate() {
        System.out.println("【协商缓存：过期后靠 ETag 拿 304】");
        Server server = new Server();
        server.put(new CacheEntry("/api/profile", "{\"name\":\"张三\"}", "\"abc123\"", 5_000, 60, false, false, null, 0));
        Browser browser = new Browser();
        long now = 20_000;
        browser.get("/api/profile", server, now, Map.of());
        now += 30;
        System.out.println("  t=+30s，仍在 max-age=60 内");
        browser.get("/api/profile", server, now, Map.of());
        now += 40;
        System.out.println("  t=+70s，已过期 -> 带 If-None-Match 协商，内容没变拿 304");
        browser.get("/api/profile", server, now, Map.of());
        System.out.println("  内容真的改了之后：");
        server.put(new CacheEntry("/api/profile", "{\"name\":\"李四\"}", "\"def456\"", 6_000, 60, false, false, null, 0));
        browser.get("/api/profile", server, now + 10, Map.of());
        System.out.printf("  服务器 200=%d 次，304=%d 次%n", server.hits200, server.hits304);
        System.out.println();
    }

    static void directives() {
        System.out.println("【Cache-Control 指令速查】");
        String[][] rows = {
                {"max-age=3600", "新鲜期 3600 秒，期内不发请求"},
                {"s-maxage=600", "只约束 CDN 等共享缓存，浏览器仍看 max-age"},
                {"no-cache", "可以存，但每次使用前必须向服务器校验（通常回 304）"},
                {"no-store", "完全不许存，敏感数据必须用它"},
                {"private", "只允许浏览器缓存，CDN 不许存"},
                {"public", "CDN 也可以缓存"},
                {"must-revalidate", "过期后必须校验，不许用陈旧副本兜底"},
                {"immutable", "内容永不改变，连强制刷新都跳过校验"},
                {"stale-while-revalidate=30", "先返回旧副本，后台异步更新，30 秒内允许"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-28s %s%n", r[0], r[1]);
        }
        System.out.println();
    }

    static void etagVsLastModified() {
        System.out.println("【ETag 与 Last-Modified 的差别】");
        System.out.println("  Last-Modified 只到秒：1 秒内改两次，第二次的改动检测不到");
        System.out.println("  Last-Modified 语义是「修改时间」：文件内容改回原样，时间也变了，会误判为已变");
        System.out.println("  ETag 是内容指纹：只有字节真的变了才变，精度最高");
        System.out.println("  两者同时存在时，服务器应以 If-None-Match 为准（RFC 9110）");
        System.out.println("  压缩代理会改 body，强 ETag 会失效，所以常见做法是弱 ETag：W/\"abc\"");
        System.out.println();
    }

    static void varyTrap() {
        System.out.println("【Vary 漏配的事故现场】");
        System.out.println("  /home 返回个性化内容，响应头只有 Cache-Control: max-age=60");
        System.out.println("  CDN 把「张三的首页」缓存了 60 秒，期间所有用户都看到张三的数据");
        System.out.println("  正确做法：Vary: Cookie（或干脆 private, no-store）");
        System.out.println("  Vary 决定缓存键的维度：Vary: Accept-Encoding 让 gzip 版与原文版分开存");
        System.out.println("  Vary: * 等于宣告「不可缓存」");
        System.out.println();
    }

    static void strategy() {
        System.out.println("【一套能上线的缓存策略】");
        System.out.println("  index.html           Cache-Control: no-cache            发版立即生效");
        System.out.println("  app.3f9a2c.js        Cache-Control: max-age=31536000, immutable   文件名带哈希");
        System.out.println("  /api/public/config   Cache-Control: public, max-age=60");
        System.out.println("  /api/me              Cache-Control: private, no-store    含个人信息");
        System.out.println("  关键：内容一变，文件名就要变。否则一年强缓存就是事故");
        System.out.println();
    }

    public static void main(String[] args) {
        strongCache();
        revalidate();
        directives();
        etagVsLastModified();
        varyTrap();
        strategy();
    }
}
```

**课后练习**
1. 给 `CacheEntry` 加 `stale-while-revalidate` 支持：过期但在宽限期内先返回旧副本，并标记「需要后台刷新」。
2. 实现 `Vary` 的真实分桶：按请求头 `Accept-Encoding` 的值把缓存拆成多个条目，验证 gzip 与原文不会互相污染。
3. 写一个 `shouldStore(CacheEntry e, boolean sharedCache)`：`no-store` 一律不存；`private` 在共享缓存中不存；无 `max-age` 但有 `Expires` 时按 `Expires` 计算；都没有时按 `Last-Modified` 的 10% 做启发式。
4. 思考题：为什么给静态资源加内容哈希后可以用 `immutable`，而 HTML 不行？如果 CDN 缓存了旧 HTML 指向已被删除的旧 JS，会出现什么现象？

---

### 35-07 HTTPS 与 TLS 握手

**知识点**
- HTTPS = HTTP + TLS。它解决三件事：**机密性**（对称加密传数据）、**完整性**（MAC/AEAD 防篡改）、**身份认证**（证书证明「这个公钥真属于这个域名」）。缺了第三件，前两件都能被中间人轻易破解。
- **为什么用混合加密**：非对称加密（RSA/ECDHE）慢，只用来协商出会话密钥；真正传数据用对称加密（AES-GCM/ChaCha20-Poly1305），快几十到几百倍。
- **证书链**：服务器证书 ← 中间 CA ← 根 CA。浏览器/系统内置根证书，逐级验签直到信任锚。任何一级签名不对、域名不匹配、过期，都会报错。
- **证书里有什么**：域名（SAN）、公钥、有效期、签发者、签名算法、扩展（是否 CA、用途）。**CN 字段已废弃**，现代浏览器只看 SAN。
- **TLS 1.2 握手（ECDHE）**：ClientHello（支持的套件 + 随机数 + 密钥交换参数）→ ServerHello + Certificate + ServerKeyExchange + ServerHelloDone → ClientKeyExchange + ChangeCipherSpec + Finished → 服务端 Finished。**2-RTT**。
- **TLS 1.3 握手**：ClientHello 里直接带 key_share，服务端回 ServerHello + 证书 + Finished，**1-RTT** 就能发应用数据。而且删掉了 RSA 密钥交换、CBC 模式、RC4、SHA-1 等一堆历史包袱。
- **前向保密（forward secrecy）**：用 ECDHE 而非 RSA 密钥交换，每次会话的私钥用完即弃。即使服务器长期私钥将来泄露，也解不开历史流量。这是 TLS 1.3 强制 ECDHE 的原因。
- **SNI**：一个 IP 上托管多个 HTTPS 站点时，客户端在 ClientHello 里用 SNI 明文中说明要访问哪个域名 —— 这是隐私缺口，所以有 ECH（Encrypted Client Hello）。
- **HSTS**：响应头 `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload` 告诉浏览器「以后这个域名只用 HTTPS」，防止 SSL 剥离攻击。首次访问仍是 HTTP，所以有 preload 列表。
- **常见配置错误**：证书链不完整（部分客户端能过、安卓报错）；用了 CBC 模式（BEAST/POODLE）；开着 TLS 1.0/1.1；私钥权限过宽；把 `verify=false` 写进生产代码。
- 易错点：以为 HTTPS 能防「服务器被黑」（不能，它只保护传输）；以为证书验证只是「有没有证书」（要验签、验域名、验有效期、验吊销）；把 `InsecureSkipVerify` 当成解决自签证书的正式方案。

**示例代码**
```java
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class TlsHandshake {
    /** 极简 DH 参数：真实用 RFC 7919 的 2048 位素数与生成元，这里为了可读性用小素数 */
    static final BigInteger P = BigInteger.valueOf(1_000_003);
    static final BigInteger G = BigInteger.valueOf(5);

    record Cert(String subject, String san, String issuer, long notAfterEpochSec,
                BigInteger publicKey, boolean ca) {
    }

    static class Ca {
        final String name;
        final BigInteger privateKey;
        final BigInteger publicKey;

        Ca(String name, BigInteger privateKey) {
            this.name = name;
            this.privateKey = privateKey;
            this.publicKey = G.modPow(privateKey, P);
        }

        Cert issue(String san, BigInteger subjectKey, long notAfter) {
            return new Cert(san, san, name, notAfter, subjectKey, false);
        }
    }

    /** 用对称密钥做一次真实的加解密演示（XOR 只是演示，真实用 AES-GCM）
     *  密文必须以 byte[] 传递：中途转成 String 会被 UTF-8 解码改写，解不回来 */
    static byte[] xor(byte[] in, byte[] key) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (byte) (in[i] ^ key[i % key.length]);
        }
        return out;
    }

    static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void keyExchange() {
        System.out.println("【ECDHE 的数学本质：双方各自算，结果相同】");
        BigInteger serverPriv = BigInteger.valueOf(48271);
        BigInteger clientPriv = BigInteger.valueOf(16807);

        BigInteger serverPub = G.modPow(serverPriv, P);
        BigInteger clientPub = G.modPow(clientPriv, P);
        System.out.printf("  server 私钥 a=%d，公开 A=g^a mod p=%d%n", serverPriv, serverPub);
        System.out.printf("  client 私钥 b=%d，公开 B=g^b mod p=%d%n", clientPriv, clientPub);

        BigInteger serverSecret = clientPub.modPow(serverPriv, P);
        BigInteger clientSecret = serverPub.modPow(clientPriv, P);
        System.out.printf("  server 算 B^a=%d%n  client 算 A^b=%d%n", serverSecret, clientSecret);
        System.out.println("  相同 -> 中间人只看到 A、B，反推 a/b 要解离散对数，计算上不可行");
        System.out.println("  每次会话都换一对临时密钥，所以叫「临时」ECDHE：前向保密靠它");
        System.out.println();

        byte[] sessionKey = sha256(serverSecret.toString());
        String plain = "POST /pay HTTP/1.1 ... card=6222****1234";
        byte[] cipher = xor(plain.getBytes(StandardCharsets.UTF_8), sessionKey);
        byte[] recovered = xor(cipher, sessionKey);
        System.out.printf("  会话密钥（SHA-256 前 8 字节）：%s%n",
                HexFormat.of().formatHex(sessionKey).substring(0, 16));
        System.out.println("  明文：" + plain);
        System.out.println("  密文（Base64）：" + java.util.Base64.getEncoder().encodeToString(cipher));
        System.out.println("  解密：" + new String(recovered, StandardCharsets.UTF_8));
        System.out.println("  密文必须以 byte[] 传递：先转 String 再转回来会被 UTF-8 解码改坏");
        System.out.println("  真实协议用 AES-GCM：同时给机密性和完整性，篡改一个字节就解不开");
        System.out.println();
    }

    static void verifyChain() {
        System.out.println("【证书链校验：四道关】");
        Ca root = new Ca("GlobalRoot CA", BigInteger.valueOf(999_331));
        Ca inter = new Ca("GlobalRoot Server CA", BigInteger.valueOf(777_733));

        BigInteger leafKey = G.modPow(BigInteger.valueOf(123_457), P);
        Cert leaf = inter.issue("shop.example.com", leafKey, 1_800_000_000L);

        System.out.printf("  leaf  subject=%s  issuer=%s%n", leaf.subject(), leaf.issuer());
        System.out.println("  中间 CA 名称=" + inter.name + "，根 CA 名称=" + root.name);

        boolean chainOk = leaf.issuer().equals(inter.name);
        System.out.println("  1) 链是否连得上：" + (chainOk ? "是" : "否"));
        boolean domainOk = leaf.san().equals("shop.example.com");
        System.out.println("  2) 域名是否匹配 SAN：" + (domainOk ? "是" : "否"));
        boolean timeOk = 1_700_000_000L < leaf.notAfterEpochSec();
        System.out.println("  3) 是否在有效期内：" + (timeOk ? "是" : "否"));
        boolean caOk = !leaf.ca() && inter.name.contains("CA");
        System.out.println("  4) 用途是否正确（leaf 不能是 CA）：" + (caOk ? "是" : "否"));

        System.out.println("  常见故障：只发了 leaf 没发中间证书 -> 浏览器能自动补，安卓和 curl 直接失败");
        System.out.println("  只看 CN 不看 SAN -> 通配符、多域名场景直接误判");
        System.out.println();
    }

    static void tls12Vs13() {
        System.out.println("【握手轮次对比】");
        String[][] t12 = {
                {"1", "ClientHello：套件列表 + client_random + ECDHE 参数"},
                {"1", "ServerHello + Certificate + ServerKeyExchange + ServerHelloDone"},
                {"2", "ClientKeyExchange + ChangeCipherSpec + Finished"},
                {"2", "ChangeCipherSpec + Finished  -> 可以发应用数据"},
        };
        String[][] t13 = {
                {"1", "ClientHello：套件 + key_share（直接带 ECDHE 公钥）+ 支持的组"},
                {"1", "ServerHello + {EncryptedExtensions, Certificate, CertVerify, Finished}  -> 可发数据"},
                {"2", "客户端 Finished + 应用数据（也可在 1-RTT 内一起发）"},
        };
        System.out.println("  TLS 1.2（ECDHE，2-RTT）：");
        for (String[] r : t12) {
            System.out.printf("    RTT %s  %s%n", r[0], r[1]);
        }
        System.out.println("  TLS 1.3（1-RTT）：");
        for (String[] r : t13) {
            System.out.printf("    RTT %s  %s%n", r[0], r[1]);
        }
        System.out.println("  TLS 1.3 删掉的：RSA 密钥交换（无前向保密）、CBC、RC4、SHA-1、压缩、重协商");
        System.out.println("  0-RTT：会话恢复时第一个包就带数据，但可被重放，只该用于幂等请求");
        System.out.println();
    }

    static void hstsAndSni() {
        System.out.println("【HSTS 与 SNI】");
        System.out.println("  没有 HSTS：用户输 example.com -> HTTP 明文 -> 中间人 302 到钓鱼站");
        System.out.println("  有 HSTS：浏览器本地记住「只用 HTTPS」，直接跳过明文那一步");
        System.out.println("  Strict-Transport-Security: max-age=31536000; includeSubDomains; preload");
        System.out.println("  首次访问无法防（还没拿到 HSTS 头），所以有浏览器内置的 preload 列表");
        System.out.println();
        System.out.println("  SNI：一个 IP 多个站点，ClientHello 里明文写域名，服务器据此选证书");
        System.out.println("  副作用：旁观者能看出你访问哪个站点；对策是 ECH（加密 ClientHello）");
        System.out.println();
    }

    static void checklist() {
        System.out.println("【上线检查清单】");
        Map<String, String> items = new LinkedHashMap<>();
        items.put("协议版本", "只开 TLS 1.2 / 1.3，关掉 1.0、1.1、SSLv3");
        items.put("套件", "优先 AEAD：TLS_AES_128_GCM_SHA256、TLS_CHACHA20_POLY1305_SHA256");
        items.put("密钥交换", "只用 ECDHE，保证前向保密");
        items.put("证书链", "服务器必须下发 leaf + 中间证书，别指望客户端补");
        items.put("HSTS", "max-age 至少半年，确认全站 HTTPS 后再加 includeSubDomains");
        items.put("客户端校验", "生产代码禁止 trustAll / InsecureSkipVerify");
        items.put("私钥", "权限 600，放密钥管理服务，别进 Git");
        items.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
        System.out.println();
    }

    public static void main(String[] args) {
        keyExchange();
        verifyChain();
        tls12Vs13();
        hstsAndSni();
        checklist();
    }
}
```

**课后练习**
1. 把 `keyExchange` 改成三组不同素数，验证「p 太小会被暴力破解」：写一个循环从 1 试到 p，找出私钥，说明为什么真实场景要求 2048 位以上。
2. 实现 `verifyChain(List<Cert> chain, String host, long now)`：逐级校验 issuer 是否等于上一级 subject、是否过期、leaf 是否 CA，返回第一个失败原因。
3. 模拟中间人：把 `clientPub` 替换成攻击者的公钥，说明为什么没有证书校验时双方仍会「成功」协商出**不同**的密钥，以及证书校验如何阻断这一步。
4. 思考题：为什么 TLS 1.3 的 0-RTT 数据可能被重放？如果支付接口接受 0-RTT 的 POST，会有什么后果？

---

### 35-08 HTTP/2 多路复用与帧

**知识点**
- HTTP/2 把报文改成**二进制帧**：每个帧有 9 字节头（长度 24 位、类型 8 位、标志 8 位、流 ID 31 位），载荷是帧内容。文本协议变成二进制，解析更快也更严格。
- **一条 TCP 连接承载所有请求**，每个请求是一条**流（stream）**，用奇数 ID 由客户端发起、偶数 ID 由服务端发起。帧可以交错发送，接收端按流 ID 归位 —— 这就是**多路复用**。
- **解决的是应用层队头阻塞**：HTTP/1.1 里请求 B 必须等 A 的响应写完；HTTP/2 里 A 和 B 的帧可以交替上路。但**传输层队头阻塞没解决**，因为底层还是一条 TCP 字节流 —— 这是 HTTP/3 换 QUIC 的动机。
- **HPACK 头部压缩**：静态表（61 个常见头，如 `:method: GET`）+ 动态表（本次连接里出现过的头）+ 哈夫曼编码。第二个请求的头部可能只有几个字节。
- **伪头部**：`:method`、`:path`、`:scheme`、`:authority` 替代请求行。它们必须出现在所有普通头之前。
- **流状态机**：`idle` → `open` → `half-closed (local/remote)` → `closed`。还有 `reserved` 用于服务端推送。乱序发帧、在 closed 流上发帧都是协议错误，要回 `RST_STREAM` 或 `GOAWAY`。
- **流量控制是两级的**：连接级窗口 + 流级窗口，默认 65535 字节，靠 `WINDOW_UPDATE` 扩大。接收方可以只给某条流小窗口，实现「慢请求别占满带宽」。
- **优先级**：HTTP/2 原本有复杂的依赖树（`PRIORITY` 帧），实际效果不好，RFC 9113 已废弃，改用 `Priority` 头 + `PRIORITY_UPDATE` 帧的简化方案。
- **服务端推送（Server Push）已被弃用**：浏览器无法精确知道客户端是否已有缓存，推了常常是浪费。Chrome 已移除。
- **`h2` 与 `h2c`**：`h2` 是 TLS 上的（ALPN 协商），`h2c` 是明文升级（`Upgrade: h2c`），生产基本只用 `h2`。
- **常见误区**：以为 HTTP/2 一定更快（高丢包链路上单连接反而更糟，因为一个包丢了所有流都等）；以为多路复用等于无限并发（`SETTINGS_MAX_CONCURRENT_STREAMS` 有限制）；以为 HTTP/2 不需要域名分片（确实不需要，分片反而有害，因为要建多条连接）。
- 易错点：在 HTTP/2 里继续用 `Connection: keep-alive`（这是 HTTP/1.1 的头，HTTP/2 禁止连接相关头）；忘记 `:authority` 导致虚拟主机路由失败；用 HTTP/2 明文端口跑 `h2`（必须 TLS + ALPN）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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
```

**课后练习**
1. 给 `Frame` 加 `wireSize()` 的完整实现，并统计上面 8 个帧在线上总共占多少字节（含 9 字节帧头）。
2. 实现 `Hpack.decode(List<Integer> indices)`，把编码后的索引序列还原成头部列表，验证第二个请求能复用动态表。
3. 写一个流状态机 `Stream`：`onHeaders`、`onData`、`onEndStream`、`onRst`，非法迁移时抛异常，覆盖上面列出的五种状态。
4. 思考题：为什么 HTTP/2 在丢包率高的移动网络上可能比 HTTP/1.1 还慢？HTTP/3 用 QUIC 后这个问题如何缓解？

---

### 35-09 DNS 解析与 CDN 调度

**知识点**
- DNS 的作用是**把域名翻译成 IP**，但它的真实职责远不止「查表」：它还承担流量调度（CDN、多活机房、灰度）、故障切换和负载均衡。很多「服务端没问题但用户打不开」的故障，根因在 DNS。
- **解析是递归 + 迭代的组合**：客户端问本地 DNS（递归，替你跑腿）→ 本地 DNS 问根服务器（返回 `.com` 的 NS）→ 问 TLD 服务器（返回 `example.com` 的 NS）→ 问权威服务器（返回 A 记录）。本地 DNS 会把结果按 TTL 缓存。
- **记录类型**：`A`（IPv4）、`AAAA`（IPv6）、`CNAME`（别名，指向另一个域名）、`MX`（邮件）、`NS`（该域由谁权威解析）、`TXT`（校验、SPF、域名所有权）、`SRV`（服务+端口+优先级）、`CAA`（谁能给这个域签证书）。
- **TTL 是「变更延迟」与「查询量」的权衡**：TTL=3600 意味着改了记录要等最多 1 小时才全网生效；TTL=60 生效快，但查询量涨 60 倍。故障切换场景常用 60~300 秒。
- **CNAME 不能和 A 记录共存于同一名字**，也不能用在根域（`example.com` 本身）—— 根域必须直接给 A/AAAA。这是「根域不能直接接 CDN」的技术原因，常见解法是 `A` 记录 + 厂商提供的固定 IP，或用 CNAME 拉平（CNAME flattening）。
- **DNS 缓存有多层**：浏览器缓存 → 操作系统缓存 → 本地 DNS 缓存 → 各级权威的 TTL。排查「域名改了不生效」要逐层清：`ipconfig /flushdns`（Windows）、`systemd-resolve --flush-caches`（Linux）、浏览器 `chrome://net-internals/#dns`。
- **CDN 的本质是「就近 + 缓存」**：用户请求先到最近的边缘节点，命中就直接返回，未命中才回源。命中率每提高 10 个点，源站带宽和响应时间都明显下降。
- **调度靠 DNS 或 Anycast**：DNS 调度按运营商/地域返回不同 IP（GSLB），粒度粗、受 TTL 影响；Anycast 让同一个 IP 在全球多地宣告，由 BGP 选最近路径，切换快但成本高。
- **回源要防雪崩**：缓存过期瞬间大量请求同时回源叫**缓存击穿**；同一批 key 同时过期叫**缓存雪崩**。对策：回源合并（singleflight）、过期时间加随机抖动、stale-while-revalidate、源站限流。
- **`Host` 与 SNI 必须对得上**：CDN 靠 `Host` 头（HTTP）或 SNI（HTTPS）判断回哪个源站。配置漏了会 404 或证书报错。
- 易错点：以为 DNS 改了就立刻生效（TTL 没到就是旧的）；用 CNAME 指向另一个 CNAME 形成环（解析失败）；把 TTL 设成 0 想「实时生效」（很多递归服务器有最小缓存下限）；忽略 DNS 查询本身的耗时（首次解析可能要几十到几百毫秒）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class DnsCdn {
    /** 一条资源记录 */
    record Record(String name, String type, String value, int ttlSec) {
    }

    /** 极简 DNS 体系：根 -> TLD -> 权威，本地解析器带缓存 */
    static class DnsSystem {
        final Map<String, List<Record>> zones = new HashMap<>();
        final Map<String, String> rootDelegation = new LinkedHashMap<>();
        final Map<String, String> tldDelegation = new LinkedHashMap<>();
        final Map<String, Cached> localCache = new HashMap<>();
        int queries;
        int cacheHits;

        record Cached(String value, long expireAtSec) {
        }

        void addZone(String zone, Record... records) {
            zones.computeIfAbsent(zone, k -> new ArrayList<>()).addAll(List.of(records));
        }

        void delegateRoot(String tld, String nsName) {
            rootDelegation.put(tld, nsName);
        }

        void delegateTld(String domain, String nsName) {
            tldDelegation.put(domain, nsName);
        }

        /** 递归解析：本地缓存 -> 根 -> TLD -> 权威 */
        String resolve(String fqdn, long nowSec, boolean verbose) {
            Cached c = localCache.get(fqdn);
            if (c != null && nowSec < c.expireAtSec()) {
                cacheHits++;
                if (verbose) {
                    System.out.printf("    本地缓存命中：%s -> %s（还剩 %d 秒）%n",
                            fqdn, c.value(), c.expireAtSec() - nowSec);
                }
                return c.value();
            }
            queries++;
            String[] parts = fqdn.split("\\.");
            String tld = parts[parts.length - 1];
            String domain = parts[parts.length - 2] + "." + tld;

            if (verbose) {
                System.out.printf("    问根服务器：%s 谁管？-> 返回 .%s 的 NS（%s）%n",
                        fqdn, tld, rootDelegation.getOrDefault(tld, "未知"));
                System.out.printf("    问 .%s 服务器：%s 谁管？-> 返回权威 NS（%s）%n",
                        tld, domain, tldDelegation.getOrDefault(domain, "未知"));
            }

            List<Record> records = zones.get(domain);
            if (records == null) {
                return null;
            }
            Record hit = null;
            for (Record r : records) {
                if (r.name().equals(fqdn) || r.name().equals("*." + domain)) {
                    hit = r;
                    break;
                }
            }
            if (hit == null) {
                return null;
            }
            String value = hit.value();
            int ttl = hit.ttlSec();
            if (hit.type().equals("CNAME")) {
                if (verbose) {
                    System.out.printf("    权威返回 CNAME：%s -> %s，继续解析%n", fqdn, value);
                }
                String resolved = resolve(value, nowSec, verbose);
                if (resolved != null) {
                    value = resolved;
                }
            } else if (verbose) {
                System.out.printf("    权威返回 %s：%s（TTL=%d）%n", hit.type(), value, ttl);
            }
            localCache.put(fqdn, new Cached(value, nowSec + ttl));
            return value;
        }
    }

    static void resolution() {
        System.out.println("【递归 + 迭代解析全过程】");
        DnsSystem dns = new DnsSystem();
        dns.delegateRoot("com", "a.root-servers.net");
        dns.delegateTld("example.com", "ns1.example.com");
        dns.addZone("example.com",
                new Record("shop.example.com", "A", "203.0.113.10", 300),
                new Record("www.example.com", "CNAME", "shop.example.com", 300),
                new Record("api.example.com", "A", "203.0.113.11", 60));

        long now = 1_000;
        System.out.println("  第一次解析 shop.example.com：");
        String ip = dns.resolve("shop.example.com", now, true);
        System.out.println("  结果：" + ip);

        System.out.println("  第二次解析（TTL 内）：");
        dns.resolve("shop.example.com", now + 100, true);
        System.out.println("  第三次解析（TTL 过期）：");
        dns.resolve("shop.example.com", now + 400, true);

        System.out.println("  解析 www.example.com（CNAME 链）：");
        String www = dns.resolve("www.example.com", now, true);
        System.out.println("  结果：" + www);
        System.out.printf("  统计：真实查询 %d 次，缓存命中 %d 次%n", dns.queries, dns.cacheHits);
        System.out.println();
    }

    static void ttlTradeoff() {
        System.out.println("【TTL 的权衡：改一条记录要多久全网生效】");
        int[] ttls = {60, 300, 3600, 86400};
        long qps = 2000;
        for (int ttl : ttls) {
            long queriesPerDay = qps * 86400L / Math.max(ttl, 1);
            System.out.printf("  TTL=%6d s  最长生效延迟 %5.1f 分钟  单域名日均查询量约 %8d 次%n",
                    ttl, ttl / 60.0, queriesPerDay);
        }
        System.out.println("  故障切换场景：先提前把 TTL 降到 60，切换完再调回，别临时改");
        System.out.println("  注意：TTL=0 不等于实时，多数递归服务器有最小缓存下限");
        System.out.println();
    }

    static void recordTypes() {
        System.out.println("【记录类型速查】");
        String[][] rows = {
                {"A", "域名 -> IPv4", "shop.example.com. 300 IN A 203.0.113.10"},
                {"AAAA", "域名 -> IPv6", "shop.example.com. 300 IN AAAA 2001:db8::10"},
                {"CNAME", "别名 -> 另一个域名", "www.example.com. 300 IN CNAME shop.example.com."},
                {"NS", "该域由谁权威解析", "example.com. 86400 IN NS ns1.example.com."},
                {"MX", "邮件服务器（带优先级）", "example.com. 3600 IN MX 10 mail.example.com."},
                {"TXT", "校验/SPF/所有权", "example.com. 3600 IN TXT \"v=spf1 -all\""},
                {"SRV", "服务 + 端口 + 权重", "_https._tcp.example.com. 60 IN SRV 10 5 443 a.example.com."},
                {"CAA", "谁可以给本域签证书", "example.com. 3600 IN CAA 0 issue \"letsencrypt.org\""},
        };
        for (String[] r : rows) {
            System.out.printf("  %-6s %-20s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  CNAME 不能和 A 记录同名共存，也不能用在根域 —— 这是根域接 CDN 的难点");
        System.out.println();
    }

    static void cdnScheduling() {
        System.out.println("【CDN 调度：让用户连到最近的边缘节点】");
        Map<String, String> edgeOfRegion = new LinkedHashMap<>();
        edgeOfRegion.put("北京-联通", "edge-bj-01 (203.0.113.101, 8ms)");
        edgeOfRegion.put("上海-电信", "edge-sh-02 (203.0.113.102, 12ms)");
        edgeOfRegion.put("广州-移动", "edge-gz-03 (203.0.113.103, 15ms)");
        edgeOfRegion.put("成都-电信", "edge-cd-04 (203.0.113.104, 22ms)");
        edgeOfRegion.forEach((region, edge) ->
                System.out.printf("  %-10s -> %s%n", region, edge));
        System.out.println("  调度方式一：DNS/GSLB 按「递归 DNS 出口 IP」判断归属地，返回对应边缘 IP");
        System.out.println("              粒度粗（受 LocalDNS 影响）、切换受 TTL 限制");
        System.out.println("  调度方式二：Anycast，同一个 IP 全球多点宣告，BGP 自动选最近");
        System.out.println("              切换快、无 TTL 问题，但成本高、排障难");
        System.out.println();
    }

    static void cacheHitAndOrigin() {
        System.out.println("【边缘缓存：命中率决定一切】");
        int totalRequests = 1000;
        double[] hitRates = {0.70, 0.85, 0.95};
        for (double rate : hitRates) {
            int origin = (int) Math.round(totalRequests * (1 - rate));
            System.out.printf("  命中率 %4.0f%%：边缘直接返回 %4d 次，回源 %3d 次%n",
                    rate * 100, totalRequests - origin, origin);
        }
        System.out.println();

        System.out.println("【回源雪崩的三种形态与对策】");
        String[][] cases = {
                {"缓存击穿", "热点 key 过期瞬间，大量请求同时回源",
                        "回源合并（singleflight）：同一 key 只放一个请求出去"},
                {"缓存雪崩", "大批 key 同时过期，或边缘节点整体重启",
                        "过期时间加随机抖动；分层回源；stale-while-revalidate"},
                {"缓存穿透", "查询不存在的资源，每次都穿透到源站",
                        "负缓存（短 TTL）+ 参数校验 + 布隆过滤器"},
        };
        for (String[] c : cases) {
            System.out.printf("  %-8s %s%n           对策：%s%n", c[0], c[1], c[2]);
        }
        System.out.println();
    }

    static void troubleshooting() {
        System.out.println("【DNS 排障顺序】");
        String[] steps = {
                "dig +trace shop.example.com     从根开始看每一跳，定位是哪一级返回不对",
                "dig shop.example.com @8.8.8.8   换一个递归服务器，判断是不是本地 DNS 缓存旧值",
                "ipconfig /flushdns              清操作系统缓存（Windows）；Linux 用 resolvectl flush-caches",
                "nslookup shop.example.com       快速看当前解析结果，注意区分 CNAME 链",
                "curl -v --resolve shop.example.com:443:203.0.113.10 https://shop.example.com",
                "                                绕过 DNS 直连指定 IP，验证「是不是 DNS 的问题」",
        };
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  常见坑：CDN 回源漏配 Host/SNI -> 源站 404 或证书不匹配");
        System.out.println();
    }

    public static void main(String[] args) {
        resolution();
        ttlTradeoff();
        recordTypes();
        cdnScheduling();
        cacheHitAndOrigin();
        troubleshooting();
    }
}
```

**课后练习**
1. 给 `DnsSystem` 加负缓存：解析不到时缓存 `NXDOMAIN` 并设短 TTL（如 30 秒），统计穿透到权威服务器的次数变化。
2. 实现 CNAME 环检测：当 CNAME 链长度超过 8 或回到已访问的名字时返回错误，而不是无限递归。
3. 写一个 `pickEdge(String clientRegion, Map<String, Integer> edgeLatency)`：按延迟选最优边缘节点，并在最优节点不可用时按顺序降级。
4. 思考题：为什么根域（`example.com`）不能直接 CNAME 到 CDN 域名？「CNAME 拉平」是怎么绕开这个限制的，代价是什么？

---

### 35-10 WebSocket 与长连接

**知识点**
- **短轮询 / 长轮询 / SSE / WebSocket 的区别**：短轮询按固定间隔问（延迟高、空请求多）；长轮询挂起直到有数据（省空请求，但仍是一次次重建）；SSE 是服务端单向推流（HTTP 响应不结束，`text/event-stream`）；WebSocket 是**全双工**，双方随时能发。
- **握手借用了 HTTP**：客户端发一个带 `Upgrade: websocket`、`Connection: Upgrade`、`Sec-WebSocket-Key: <16字节随机 Base64>` 的 GET 请求。服务端把 key 拼上固定 GUID `258EAFA5-E914-47DA-95CA-C5AB0DC85B11`，算 SHA-1 再 Base64，作为 `Sec-WebSocket-Accept` 返回，状态码 `101 Switching Protocols`。之后这条 TCP 连接就不再走 HTTP 了。
- **`Sec-WebSocket-Key` 不是安全机制**：它只是防止缓存代理把「普通 HTTP 响应」误当成握手成功。真正的认证靠 Cookie/Token 在握手请求里带过去。
- **帧格式**：2 字节起 —— `FIN`(1 bit) + `RSV`(3 bit) + `opcode`(4 bit)，然后是 `MASK`(1 bit) + `payload len`(7 bit，126 表示后跟 2 字节长度，127 表示后跟 8 字节)。客户端发往服务端的帧**必须掩码**，服务端发往客户端的**必须不掩码**，违反就断开。
- **opcode**：`0x0` 续帧、`0x1` 文本、`0x2` 二进制、`0x8` 关闭、`0x9` ping、`0xA` pong。控制帧不能分片，载荷不超过 125 字节。
- **分片（fragmentation）**：大消息拆成「首帧 FIN=0 + 若干续帧 + 末帧 FIN=1」。用于边生成边发，不必先算总长度。
- **心跳必须自己做**：WebSocket 没有内置保活。中间代理通常在 60 秒空闲后断开连接，所以客户端要定时发 ping（或应用层心跳），服务端回 pong。只靠 TCP keepalive 不够，因为它默认 2 小时才探测。
- **重连要退避 + 补偿**：断线后立刻重连会造成惊群。用指数退避 + 随机抖动（1s、2s、4s…上限 30s）。重连后要重新同步状态（拉一次全量或按最后一条消息 ID 增量拉）。
- **鉴权与安全**：浏览器 WebSocket API 不能自定义头，所以 token 一般放在 URL 参数或首个消息里 —— 放 URL 会进日志，优先用「握手时用 Cookie」或「连接后第一帧发 token 并设短超时」。必须校验 `Origin`，否则任意站点都能用用户 Cookie 建立连接（CSWSH）。
- **水平扩展**：连接分散在多台机器上，A 机器上的用户要给 B 机器上的用户发消息，需要**会话路由**（Redis 记录 uid→节点）或**广播总线**（Redis Pub/Sub、Kafka）。
- 易错点：以为 WebSocket 能自动重连（不能，要自己写）；用 `setInterval` 发心跳但页面隐藏时被节流导致误判断线；忘记处理 `onclose` 的 code（1006 是异常断开，不能用于业务判断）；在消息里直接 `JSON.parse` 不校验（大消息会卡主线程）。

**示例代码**
```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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
```

**课后练习**
1. 实现 `Frame.decode(byte[] wire)`：解析 FIN、opcode、长度（含 126/127 扩展长度）、掩码并还原 payload，与 `encode` 互逆。
2. 写一个 `WebSocketServer` 状态机：`handshaking` → `open` → `closing` → `closed`，收到 CLOSE 帧时回一个 CLOSE 并停止收帧，超时未收到对端 CLOSE 则强制断开。
3. 实现带抖动的心跳超时判定：连续 N 次未收到 PONG 触发重连，并打印每次的 RTT 统计（最小/最大/平均）。
4. 思考题：为什么客户端帧必须掩码？如果允许不掩码，会给中间代理带来什么风险？

---

### 35-11 综合案例：一次请求的完整链路

**知识点**
- 把前面十课串起来：**DNS → TCP → TLS → HTTP → 缓存 → 后端 → 响应**。每一步都有耗时，也都有失败可能。优化的前提是知道时间花在哪。
- **典型耗时分布**（跨境、冷启动）：DNS 20~120 ms、TCP 握手 1 RTT、TLS 握手 1~2 RTT、请求传输 1 RTT、服务端处理 5~200 ms、响应传输 1 RTT。RTT=150 ms 时，光握手就 450 ms。
- **优化顺序应该是**：① 复用连接（省掉 TCP+TLS）→ ② CDN/边缘缓存（省掉整条回源链路）→ ③ 减少往返次数（合并请求、HTTP/2）→ ④ 服务端提速（索引、缓存、并发）→ ⑤ 前端渲染优化。
- **连接复用是收益最大的一步**：HTTP/2 一条连接跑所有请求，TLS 会话复用（session ticket）让第二次连接 1-RTT 甚至 0-RTT。连接池要设上限和空闲回收，否则会打满服务端的 fd。
- **超时要有层级**：客户端超时 > 网关超时 > 服务超时 > 数据库超时。反过来会导致「下层还在算，上层已经放弃并重试」，把压力放大。
- **重试只对幂等操作开放**，且必须带退避和次数上限。重试放大的经典事故：3 层各重试 3 次，实际请求量变成 27 倍。
- **失败要能定位**：一次请求至少要有 trace id、客户端 IP、UA、耗时分解、状态码、上游耗时。没有这些，线上只能靠猜。
- **可观测性三段**：指标（QPS/延迟分位/错误率）、日志（结构化、带 trace id）、链路追踪（每跳 span）。分位数比平均值有用得多 —— 平均 50 ms 可能藏着 5% 的 3 秒请求。
- **首屏感知 ≠ 服务端耗时**：TTFB、FCP、LCP 是不同阶段。服务端 100 ms 但 CSS 阻塞渲染，用户照样觉得慢。
- 易错点：只看平均值不看 P99；在客户端重试又在网关重试（放大）；把所有超时设成同一个值（失去层级）；缓存了 HTML 却没给静态资源加哈希（发版白屏）；忽略 DNS 首次解析的耗时。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class RequestJourney {
    /** 一个阶段的耗时与是否命中「优化」 */
    record Span(String name, double ms, String note) {
    }

    /** 一次请求的完整时间线 */
    static class Timeline {
        final List<Span> spans = new ArrayList<>();

        Timeline add(String name, double ms, String note) {
            spans.add(new Span(name, ms, note));
            return this;
        }

        double total() {
            double sum = 0;
            for (Span s : spans) {
                sum += s.ms();
            }
            return sum;
        }

        void print(String title) {
            System.out.println("【" + title + "】");
            double acc = 0;
            for (Span s : spans) {
                acc += s.ms();
                System.out.printf("  %-22s %7.1f ms  累计 %7.1f ms  %s%n",
                        s.name(), s.ms(), acc, s.note());
            }
            System.out.printf("  %-22s %7.1f ms%n", "合计", total());
            System.out.println();
        }
    }

    /** 冷启动：什么都没有，全部现算 */
    static Timeline coldStart(double rtt) {
        return new Timeline()
                .add("DNS 解析", rtt * 0.5, "本地无缓存，递归查一遍")
                .add("TCP 三次握手", rtt, "1 RTT")
                .add("TLS 1.3 握手", rtt, "1 RTT（首次，无会话复用）")
                .add("发送请求", rtt * 0.5, "半个 RTT 上路")
                .add("服务端处理", 35, "查库 + 组装 JSON")
                .add("响应回传", rtt * 0.5, "半个 RTT 回来");
    }

    /** 优化后：连接复用 + CDN 命中 + 服务端缓存 */
    static Timeline optimized(double rtt) {
        return new Timeline()
                .add("DNS 解析", 0, "已缓存，0 ms")
                .add("TCP 握手", 0, "连接池复用，0 ms")
                .add("TLS 握手", 0, "会话复用，0 ms")
                .add("发送请求", rtt * 0.5, "复用连接，直接发")
                .add("边缘缓存命中", 2, "CDN 直接返回，不回源")
                .add("响应回传", rtt * 0.5, "半个 RTT 回来");
    }

    static void compare(double rtt) {
        System.out.printf("【RTT = %.0f ms 时的耗时对比】%n", rtt);
        Timeline cold = coldStart(rtt);
        Timeline warm = optimized(rtt);
        cold.print("冷启动（无缓存、无复用）");
        warm.print("优化后（连接复用 + CDN 命中）");
        double saved = cold.total() - warm.total();
        System.out.printf("  节省 %.1f ms（%.0f%%）：省掉 DNS+TCP+TLS 共 %.1f ms，省掉回源处理 33.0 ms%n",
                saved, saved * 100 / cold.total(), rtt * 2.5);
        System.out.println();
    }

    static void optimizationOrder(double rtt) {
        System.out.println("【优化手段的收益排序（RTT=150ms）】");
        double base = coldStart(rtt).total();
        String[][] steps = {
                {"连接复用（keep-alive / HTTP/2）", "省掉 TCP+TLS 共 2 RTT", "300"},
                {"TLS 会话复用（session ticket）", "第二次连接 1-RTT 甚至 0-RTT", "150"},
                {"服务端加缓存/索引", "把 35ms 降到 5ms", "30"},
                {"CDN 边缘缓存命中", "省掉回源与源站处理", "35"},
                {"HTTP/2 多路复用", "并发请求不再排队，单请求 RTT 不变", "0"},
                {"前端资源优化", "不影响 TTFB，影响 FCP/LCP", "0"},
        };
        for (String[] s : steps) {
            double gain = Double.parseDouble(s[2]);
            System.out.printf("  %-30s 省 %6.1f ms   %s%n", s[0], gain, s[1]);
        }
        System.out.printf("  基线 %.1f ms。先做前两项，能砍掉一半以上；服务端优化排在后面%n", base);
        System.out.println();
    }

    static void timeoutHierarchy() {
        System.out.println("【超时层级：必须从外到内递减】");
        String[][] good = {
                {"浏览器/客户端", "10000"},
                {"网关", "3000"},
                {"业务服务", "1500"},
                {"下游 RPC", "800"},
                {"数据库", "500"},
        };
        for (String[] row : good) {
            System.out.printf("  %-14s %5s ms%n", row[0], row[1]);
        }
        System.out.println("  正确：外层比内层大，下层超时先触发，上层拿到明确的「下游超时」");
        System.out.println();
        System.out.println("  错误示例：全部 3000 ms");
        System.out.println("    数据库卡住 3000 ms，业务服务也等 3000 ms，网关同样 3000 ms");
        System.out.println("    结果：网关先超时并重试，此时下层还在算 -> 请求量翻倍，雪崩");
        System.out.println();
    }

    static void retryAmplification() {
        System.out.println("【重试放大的算术】");
        int layers = 3;
        int retries = 3;
        int base = 1;
        int amplified = base;
        for (int i = 0; i < layers; i++) {
            amplified *= retries;
        }
        System.out.printf("  %d 层各重试 %d 次：1 个用户请求 -> 实际打到最底层 %d 次%n",
                layers, retries, amplified);
        System.out.println("  规则：只对幂等方法重试；只在最靠近失败点的一层重试；带退避与熔断");
        System.out.println();
    }

    static void observability() {
        System.out.println("【可观测性：一次请求至少要留下什么】");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("traceId", "贯穿全链路的唯一 ID，日志与追踪都要带");
        fields.put("clientIp / ua", "定位是特定客户端还是全局问题");
        fields.put("status", "状态码 + 业务错误码");
        fields.put("durationMs", "总耗时");
        fields.put("spans", "DNS/TCP/TLS/排队/处理/回源 各段耗时");
        fields.put("upstream", "调用了哪些下游、各自耗时与结果");
        fields.forEach((k, v) -> System.out.printf("  %-14s %s%n", k, v));
        System.out.println();
        System.out.println("  看分位数，不看平均值：P50=50ms、P99=3000ms 的系统，平均可能只有 80ms");
        double[] latencies = {20, 25, 30, 35, 40, 45, 50, 60, 70, 80,
                90, 100, 120, 150, 200, 400, 900, 1500, 2200, 3000};
        double avg = 0;
        for (double v : latencies) {
            avg += v;
        }
        avg /= latencies.length;
        System.out.printf("  示例：平均 %.1f ms，P50=%.0f ms，P90=%.0f ms，P99=%.0f ms%n",
                avg, pct(latencies, 0.50), pct(latencies, 0.90), pct(latencies, 0.99));
        System.out.println("  平均值把长尾藏起来了，而长尾才是用户投诉的来源");
        System.out.println();
    }

    static double pct(double[] sorted, double p) {
        int idx = (int) Math.ceil(p * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    static void failureModes() {
        System.out.println("【这条链路上每一步会怎么坏】");
        String[][] rows = {
                {"DNS", "解析到旧 IP / NXDOMAIN", "多地域拨测；TTL 提前调小"},
                {"TCP", "SYN 无响应、连接被重置", "检查安全组、SYN 队列、中间设备"},
                {"TLS", "证书过期 / 链不完整 / 域名不匹配", "到期监控；下发中间证书；只开 1.2+"},
                {"HTTP", "502/504、响应被截断", "看网关日志与上游耗时；校验 Content-Length"},
                {"缓存", "缓存了用户私有数据", "Vary / private / no-store"},
                {"服务端", "慢查询、连接池耗尽、线程池打满", "看 P99、连接池指标、线程转储"},
                {"客户端", "重试风暴、超时设置不当", "退避 + 幂等 + 层级超时"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-6s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void fullWalkthrough() {
        System.out.println("【一次真实请求的完整走位】");
        String[] steps = {
                "1. 浏览器查 DNS：本地缓存 -> 系统缓存 -> 递归解析，拿到 CDN 边缘 IP",
                "2. 与边缘节点建 TCP 连接（复用则跳过），TLS 1.3 握手（会话复用则 0-RTT）",
                "3. 发 GET /product/1001，带 If-None-Match 与 Cookie",
                "4. 边缘节点查缓存：命中 -> 直接回 200/304，整条回源链路省掉",
                "5. 未命中 -> 回源，带 Host 与 SNI，源站按 Vary 决定缓存键",
                "6. 源站查 Redis，未命中查数据库，组装 JSON，设置 Cache-Control",
                "7. 响应逐跳回传，边缘按策略缓存，浏览器按 max-age 存本地",
                "8. 浏览器解析 HTML，加载带哈希的静态资源（强缓存命中，0 请求）",
                "9. 页面发起 XHR，走同一条 HTTP/2 连接，多路复用不排队",
                "10. 全链路 traceId 串起来，慢在哪一段一眼能看到",
        };
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        compare(150);
        compare(20);
        optimizationOrder(150);
        timeoutHierarchy();
        retryAmplification();
        observability();
        failureModes();
        fullWalkthrough();
    }
}
```

**课后练习**
1. 给 `Timeline` 加 `parallel(List<Span>)`：把可并行的阶段（如多个静态资源）按「取最大值」而不是求和计入总耗时，对比串行与并行的差距。
2. 实现一个 `Simulator`：输入 RTT、缓存命中率、服务端耗时、重试层数，输出端到端 P50/P99 估算，并找出瓶颈段。
3. 写一个 `diagnose(Timeline t)`：按「DNS > 100ms」「握手 > 2×RTT」「服务端 > 200ms」「回源 > 50ms」给出具体的优化建议。
4. 思考题：为什么把客户端超时设得比网关短会导致「请求已经成功但用户看到失败」？这种情况下重试会造成什么后果？

---

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
