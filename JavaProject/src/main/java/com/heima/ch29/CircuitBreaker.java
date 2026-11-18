/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-08
 * 课名：熔断、降级与服务隔离
 * ------------------------------------------------------------
 * 知识点：
 *   - **雪崩**：下游 B 变慢 → 调用 B 的线程全部阻塞 → A 的线程池耗尽 → A 也不可用 → 上游 C 跟着挂。一个慢接口能拖垮整条链路。
 *   - **熔断器三态**：`CLOSED`（正常放行，统计失败率）→ 失败率超阈值 → `OPEN`（直接快速失败，不发起调用）→ 等待窗口后 → `HALF_OPEN`（放少量试探请求）→ 成功则回 `CLOSED`，失败则回 `OPEN`。
 *   - **降级**：熔断或异常时返回兜底结果——缓存旧值、默认值、空列表、友好提示。降级要**有损但可用**，不能降级成报错。
 *   - **隔离**：线程池隔离（每个下游独立线程池，慢的不拖累快的）、信号量隔离（轻量，限制并发数）、集群隔离（不同业务用不同实例）。
 *   - 熔断参数：失败率阈值（如 50%）、最小请求数（样本太少不熔断）、熔断时长（给下游恢复时间）、半开试探数量。
 *   - 易错：熔断后没有兜底，直接把异常抛给用户；熔断时长设太短（下游还没恢复就放流量）；所有下游共用一个线程池，等于没隔离。
 * ============================================================
 */

package com.heima.ch29;

// ==================== 示例代码 ====================

public class CircuitBreaker {
    enum State { CLOSED, OPEN, HALF_OPEN }

    static class Breaker {
        State state = State.CLOSED;
        int total;
        int failed;
        long openUntilMs;
        int halfOpenPermits;

        final int minRequests = 10;
        final double failRateThreshold = 0.5;
        final long openDurationMs = 5000;
        final int halfOpenMax = 3;

        /** 返回 true 表示放行调用 */
        boolean allow(long now) {
            if (state == State.OPEN) {
                if (now >= openUntilMs) {
                    state = State.HALF_OPEN;
                    halfOpenPermits = halfOpenMax;
                    System.out.println("    [熔断器] 冷却结束 -> HALF_OPEN，允许 " + halfOpenMax + " 个试探");
                } else {
                    return false;
                }
            }
            if (state == State.HALF_OPEN) {
                if (halfOpenPermits <= 0) {
                    return false;
                }
                halfOpenPermits--;
            }
            return true;
        }

        void record(boolean ok, long now) {
            total++;
            if (!ok) {
                failed++;
            }
            if (state == State.HALF_OPEN) {
                if (ok) {
                    System.out.println("    [熔断器] 试探成功 -> CLOSED，恢复正常");
                    reset();
                } else {
                    System.out.println("    [熔断器] 试探失败 -> 重新 OPEN");
                    trip(now);
                }
                return;
            }
            if (total >= minRequests) {
                double rate = (double) failed / total;
                if (rate >= failRateThreshold) {
                    System.out.println("    [熔断器] 失败率 " + Math.round(rate * 100) + "% 超阈值 -> OPEN");
                    trip(now);
                }
            }
        }

        void trip(long now) {
            state = State.OPEN;
            openUntilMs = now + openDurationMs;
            reset();
            state = State.OPEN;
            openUntilMs = now + openDurationMs;
        }

        void reset() {
            total = 0;
            failed = 0;
        }
    }

    /** 下游服务：前若干次失败，之后恢复 */
    static boolean callDownstream(int seq) {
        return seq > 12;
    }

    /** 降级兜底 */
    static String fallback() {
        return "降级：返回缓存中的旧商品列表（可能不是最新）";
    }

    public static void main(String[] args) {
        Breaker breaker = new Breaker();
        long now = 0;
        int seq = 0;

        System.out.println("=== 下游持续失败：熔断器打开 ===");
        for (int i = 1; i <= 20; i++) {
            seq++;
            now += 100;
            if (!breaker.allow(now)) {
                System.out.println("  第 " + i + " 次请求：熔断 OPEN，直接走降级 -> " + fallback());
                continue;
            }
            boolean ok = callDownstream(seq);
            System.out.println("  第 " + i + " 次请求：调用下游 -> " + (ok ? "成功" : "超时"));
            breaker.record(ok, now);
            if (!ok) {
                System.out.println("    -> " + fallback());
            }
        }

        System.out.println();
        System.out.println("=== 冷却 5s 后进入半开，试探恢复 ===");
        now += 5000;
        for (int i = 21; i <= 26; i++) {
            seq++;
            now += 100;
            if (!breaker.allow(now)) {
                System.out.println("  第 " + i + " 次请求：仍不放行 -> 降级");
                continue;
            }
            boolean ok = callDownstream(seq);
            System.out.println("  第 " + i + " 次请求：试探调用 -> " + (ok ? "成功" : "失败"));
            breaker.record(ok, now);
        }

        System.out.println();
        System.out.println("=== 隔离方式对比 ===");
        System.out.println("  线程池隔离：每个下游独立池（如 20 线程），慢的下游只耗尽自己的池");
        System.out.println("  信号量隔离：只限并发数，不换线程，开销小但无法超时中断");
        System.out.println("  集群隔离：核心业务和非核心业务部署在不同实例组，互不影响");
        System.out.println("  反例：所有下游共用一个 200 线程池，一个慢接口就能拖垮全部");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `minRequests` 改成 20、阈值改成 0.3，重跑示例并观察熔断时机变化。
 * 2. 实现"线程池隔离"：为商品服务和积分服务各建一个固定大小线程池，验证慢的不会占满另一个。
 * 3. 设计一个降级矩阵：列出 5 个下游，分别给出"熔断后返回什么"。
 * ============================================================
 */
