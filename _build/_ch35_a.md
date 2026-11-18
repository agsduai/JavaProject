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
