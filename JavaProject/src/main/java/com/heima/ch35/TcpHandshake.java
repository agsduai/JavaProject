/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-02
 * 课名：TCP 三次握手与四次挥手
 * ------------------------------------------------------------
 * 知识点：
 *   - TCP 是**面向连接**的：通信前双方要先协商出各自的初始序号（ISN），并确认对方「在线且愿意收」。这个协商过程就是三次握手。
 *   - **三次握手的每一步**：① client 发 SYN（seq=x），进入 `SYN_SENT`；② server 回 SYN+ACK（seq=y, ack=x+1），进入 `SYN_RCVD`；③ client 回 ACK（ack=y+1），双方进入 `ESTABLISHED`。SYN 和 FIN 各消耗一个序号，所以确认号要 +1。
 *   - **为什么必须三次**：两次握手时，一个在网络里滞留很久的旧 SYN 到达 server，server 会直接建立连接并分配资源，而 client 根本不认这条连接，资源就空转到超时。第三次 ACK 让 server 确认「client 真的收到了我的 SYN+ACK」，即「这是本次连接，不是历史残留」。
 *   - **ISN 为什么随机**：如果 ISN 可预测，攻击者就能伪造报文注入连接（序列号攻击）。Linux 用时钟 + 四元组哈希生成，同一四元组短时间内不会重复。
 *   - **SYN 队列与 accept 队列**：server 收到 SYN 后先把连接放进 SYN 队列（半连接），收到第三次 ACK 才移入 accept 队列（全连接），应用 `accept()` 取走。两个队列都有上限，被打满就丢新 SYN —— 这就是 SYN Flood 的原理，防御手段是 SYN Cookie。
 *   - **四次挥手**：① 主动方发 FIN，进入 `FIN_WAIT_1`；② 被动方回 ACK，进入 `CLOSE_WAIT`，主动方进入 `FIN_WAIT_2`；③ 被动方把剩余数据发完后再发 FIN，进入 `LAST_ACK`；④ 主动方回 ACK，进入 `TIME_WAIT`，被动方收到后 `CLOSED`。
 *   - **为什么挥手要四次**：TCP 是全双工的，一方说「我没数据要发了」不代表另一方也发完了。中间那两个报文（ACK 和 FIN）不能合并，因为被动方可能还有数据要传。这就是**半关闭**状态，HTTP 里 server 收完请求后还能把响应写完，靠的就是它。
 *   - **TIME_WAIT 要等 2MSL**：一是保证最后那个 ACK 能到达（丢了对方会重发 FIN，主动方还能再回一次）；二是让这条连接的重复报文在网络中自然消亡，避免污染下一个用相同四元组的新连接。MSL 通常取 30 s 或 60 s。
 *   - **CLOSE_WAIT 堆积是代码问题**：大量 `CLOSE_WAIT` 说明对端已经关了，本进程却没调 `close()` —— 通常是忘了关流、异常分支漏了 `finally`。`TIME_WAIT` 多则多半是短连接太频繁，该上连接池或长连接。
 *   - 易错点：把 `TIME_WAIT` 当成 bug 去「优化」掉（关掉它会让旧报文串进新连接）；以为 `close()` 一定发 FIN（还有数据没发完时只是半关闭）；在 `SYN_SENT` 阶段就认为连接已建立。
 * ============================================================
 */

package com.heima.ch35;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Endpoint` 加一个 `Set<State> history`，在每次 `moveTo` 时记录迁移路径，握手和挥手结束后打印完整的状态轨迹。
 * 2. 实现被动关闭版本的四次挥手：由 server 主动发 FIN，画出两端的六步状态变化。
 * 3. 模拟「第三次 ACK 丢失」：client 已 `ESTABLISHED`，server 还在 `SYN_RCVD`，说明 server 会怎样重传 SYN+ACK、重传几次后放弃，以及 client 收到重复 SYN+ACK 时应该怎么做。
 * 4. 思考题：为什么 `TIME_WAIT` 出现在主动关闭的一方，而不是被动关闭的一方？如果两端同时关闭（同时发 FIN），状态机会走哪条路径？
 * ============================================================
 */
