/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-03
 * 课名：TCP 可靠传输与拥塞控制
 * ------------------------------------------------------------
 * 知识点：
 *   - TCP 的可靠性靠四件事：**序号**（每个字节都有编号，接收方按序号重排）、**确认**（ACK 告诉对方我收到了哪之前的所有字节）、**重传**（超时没收到 ACK 就重发）、**校验和**（头部和数据一起算，坏了就丢）。
 *   - **累计确认**：ACK=n 表示「n 之前的字节我全收到了」，不表示只收到第 n 个。所以 ACK 号只会单调前进，中间丢了也会被后面的 ACK 覆盖 —— 这叫**累积性**。
 *   - **滑动窗口**解决「发太快对方接不住」：接收方在 ACK 里带一个 `window`，发送方最多只能有 `window` 个未确认字节在途。窗口为 0 时发送方停发，靠**零窗口探测**定时询问。
 *   - **快重传 vs 超时重传**：收到 3 个重复 ACK（同一 ACK 号连发三次）说明中间那段丢了，立刻重传，不用等 RTO —— 这是快重传（fast retransmit），把恢复时间从「一个 RTO」缩短到「几个 RTT」。
 *   - **RTO 是算出来的，不是拍脑袋**：用 Jacobson/Karels 算法维护平滑 RTT（SRTT）和 RTT 偏差（RTTVAR）：`SRTT = 0.875*SRTT + 0.125*RTT`，`RTTVAR = 0.75*RTTVAR + 0.25*|SRTT-RTT|`，`RTO = SRTT + 4*RTTVAR`。RTO 有下限（Linux 200 ms），避免过度重传。
 *   - **拥塞控制四个阶段**：**慢启动**（cwnd 每 RTT 翻倍，指数增长）→ 到 `ssthresh` 转**拥塞避免**（每 RTT 加 1 个 MSS，线性增长）→ 丢包时**快速恢复**（Reno：`ssthresh=cwnd/2`，`cwnd=ssthresh`）→ 超时就回到慢启动（`cwnd=1`）。
 *   - **Tahoe 与 Reno 的区别**：Tahoe 一遇丢包就把 cwnd 打回 1，Reno 用快重传 + 快恢复只减半。现代 Linux 默认 **CUBIC**，用三次函数代替线性增长，高带宽长距离链路上恢复更快；**BBR** 则完全不看丢包，直接估带宽和最小 RTT。
 *   - **流量控制 vs 拥塞控制**：前者是接收方说「我处理不过来」，后者是网络说「路上堵了」。两者取最小值决定实际发送速率。
 *   - **Nagle 算法与延迟 ACK 的死锁**：Nagle 攒小包等 ACK，延迟 ACK 等更多数据一起确认，两边互等就可能卡住 40 ms。实时性要求高的场景用 `TCP_NODELAY` 关掉 Nagle。
 *   - 易错点：以为 ACK 是「逐包确认」（是累计确认）；把 `ssthresh` 和 `cwnd` 搞混（前者是切换阈值，后者是当前窗口）；认为丢包一定是网络故障（也可能是链路误码、队列溢出）。
 * ============================================================
 */

package com.heima.ch35;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Sender` 加一个 `receiveWindow` 字段，让 `canSend()` 同时受拥塞窗口和接收窗口限制，模拟「接收方处理慢」导致的停发。
 * 2. 实现零窗口探测：当接收方通告 `window=0` 时，发送方每隔一段时间发一个 1 字节探测包，直到窗口重新打开。
 * 3. 把 `congestionReno` 改成 CUBIC 风格的增长：`cwnd` 按 `C*(t-K)³+Wmax` 变化，简化成每轮增量递减再递增，打印曲线对比 Reno。
 * 4. 思考题：为什么快重传要求「3 个」重复 ACK，而不是 1 个？如果链路上只是乱序而非丢包，快重传会带来什么后果？
 * ============================================================
 */
