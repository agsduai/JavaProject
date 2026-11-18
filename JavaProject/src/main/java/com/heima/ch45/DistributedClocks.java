/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-07
 * 课名：分布式时钟与顺序
 * ------------------------------------------------------------
 * 知识点：
 *   - **分布式系统的顺序不能靠物理时钟**：两台机器的时钟各有漂移（ppm 级），NTP 校准有毫秒级误差且可能回拨——**用时间戳排序事件 = 依赖一个不可靠的全局参照**（43-02 事件时间的教训在元数据层的同款）。
 *   - **时钟回拨的破坏力**：39-07 雪花 ID 拒绝回拨保 ID 唯一；45-03 任期号绝不用时钟保顺序；43-02 服务端打点但容忍乱序——**共识系统直接放弃物理时钟做顺序**。
 *   - **逻辑时钟（Lamport Clock）**：每个事件带 (counter, nodeId)，事件发生 counter++，收到消息 counter=max(local, remote)+1——**保证因果序**：a -> b 则 a 的时钟 < b 的时钟。但它不能区分"并发"与"先后"（时钟相等无法判定谁是因）。
 *   - **向量时钟（Vector Clock）**：每个节点维护全量计数向量，比较向量可判定**因果先后或并发**——Dynamo 类系统用它检测冲突（两个并发写 -> 冲突交给业务合并）。代价：向量长度 = 节点数，扩展性受限。
 *   - **混合时钟（HLC/Hybrid Logical Clock）**：物理时间戳 + 逻辑计数组合——保因果序又接近物理时间（对分桶/限流友好）。CockroachDB/Yugabyte 的选择，也是 43 章窗口聚合与共识世界的桥。
 *   - **外部一致性的真解是原子钟/TrueTime**：Spanner 用 GPS+原子钟给出有界误差的时间（±几 ms），等待误差过去再提交——**用硬件砸出全局顺序**，普通公司不复制，但知道天花板在哪。
 *   - 易错点：用 `System.currentTimeMillis` 排序分布式事件（漂移+回拨双重坑）；把 NTP 精度想成毫秒级（公网 NTP 可能几十 ms 且依赖网络）；逻辑时钟当物理时间用（不能做分桶/限流）；冲突检测缺失直接 last-write-wins（丢数据）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DistributedClocks {
    /** Lamport 逻辑时钟：counter + nodeId */
    static class Lamport {
        long counter = 0;
        final String node;

        Lamport(String node) {
            this.node = node;
        }

        long localEvent() {
            return ++counter;
        }

        long onReceive(long remote) {
            counter = Math.max(counter, remote) + 1;
            return counter;
        }

        String stamp() {
            return "(" + counter + "," + node + ")";
        }
    }

    static void lamportDemo() {
        System.out.println("【Lamport 时钟：跨节点保持因果序】");
        Lamport a = new Lamport("A"), b = new Lamport("B");
        long e1 = a.localEvent();                       // A 本地事件
        long e2 = a.localEvent();
        long sent = e2;                                  // A 发消息
        long e3 = b.onReceive(sent);                     // B 收到消息
        long e4 = b.localEvent();
        System.out.printf("  A: 本地事件 %s -> %s%n", e1, e2);
        System.out.printf("  A 发送消息(携带 %d) -> B 收到 -> %s -> 本地 %s%n", sent, e3, e4);
        System.out.println("  因果链 A.e2 -> B.e3：时钟 2 < 4，因果可判");
        System.out.println("  局限：A.e1(1) 与 B.e3(3) 无因果但时钟可比 —— 并发无法识别");
        System.out.println();
    }

    static void vectorClock() {
        System.out.println("【向量时钟：判别并发写冲突】");
        record VC(Map<String, Long> v) {
            boolean before(VC o) {
                boolean allLE = true, someLT = false;
                for (Map.Entry<String, Long> e : v.entrySet()) {
                    long other = o.v().getOrDefault(e.getKey(), 0L);
                    if (e.getValue() > other) {
                        allLE = false;
                    }
                    if (e.getValue() < other) {
                        someLT = true;
                    }
                }
                return allLE && someLT;
            }

            boolean concurrent(VC o) {
                return !before(o) && !o.before(this);
            }
        }
        Map<String, Long> a1 = new LinkedHashMap<>();
        a1.put("A", 2L);
        a1.put("B", 1L);
        Map<String, Long> b1 = new LinkedHashMap<>();
        b1.put("A", 1L);
        b1.put("B", 2L);
        VC va = new VC(a1), vb = new VC(b1);
        System.out.println("  客户端A 写入版本 " + a1);
        System.out.println("  客户端B 写入版本 " + b1 + "（基于同一旧版本，未见过 A 的写）");
        System.out.printf("  判定：%s%n", va.concurrent(vb) ? "并发 -> 冲突！需合并或业务裁决" : "有因果 -> 取较新者");
        System.out.println("  对照 last-write-wins：按墙钟比较会静默丢掉一方写入");
        System.out.println();
    }

    static void hybridClock() {
        System.out.println("【混合时钟：物理 + 逻辑的折衷】");
        long wallMs = 1763350000000L;
        long logical = 3;
        System.out.printf("  HLC = 物理部分 %d + 逻辑部分 %d%n", wallMs, logical);
        List<String> benefits = List.of(
                "保留物理时间语义：可做时间窗口分桶（43-03 兼容）",
                "同毫秒内用逻辑位排序：解决毫秒内并发",
                "时钟漂移被物理位吸收，回拨只影响逻辑位");
        for (String b : benefits) {
            System.out.println("  - " + b);
        }
        System.out.println("  CockroachDB/Yugabyte 的选择：对外像物理时间，内部保证因果");
    }

    public static void main(String[] args) {
        lamportDemo();
        vectorClock();
        hybridClock();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Lamport 时钟的三节点消息交互模拟，验证所有因果对的时钟序，并构造一对"时钟可比但实际并发"的事件。
 * 2. 实现向量时钟冲突检测：两个客户端基于不同副本并发写，输出冲突判定与合并策略建议。
 * 3. 思考题：为什么 45-03 的任期号选择"单调计数"而不是"物理时间"？从回拨与并发两个故障场景推导。
 * ============================================================
 */
