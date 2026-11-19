/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-11
 * 课名：综合案例：一次请求的完整链路
 * ------------------------------------------------------------
 * 知识点：
 *   - 把前面十课串起来：**DNS → TCP → TLS → HTTP → 缓存 → 后端 → 响应**。每一步都有耗时，也都有失败可能。优化的前提是知道时间花在哪。
 *   - **典型耗时分布**（跨境、冷启动）：DNS 20~120 ms、TCP 握手 1 RTT、TLS 握手 1~2 RTT、请求传输 1 RTT、服务端处理 5~200 ms、响应传输 1 RTT。RTT=150 ms 时，光握手就 450 ms。
 *   - **优化顺序应该是**：① 复用连接（省掉 TCP+TLS）→ ② CDN/边缘缓存（省掉整条回源链路）→ ③ 减少往返次数（合并请求、HTTP/2）→ ④ 服务端提速（索引、缓存、并发）→ ⑤ 前端渲染优化。
 *   - **连接复用是收益最大的一步**：HTTP/2 一条连接跑所有请求，TLS 会话复用（session ticket）让第二次连接 1-RTT 甚至 0-RTT。连接池要设上限和空闲回收，否则会打满服务端的 fd。
 *   - **超时要有层级**：客户端超时 > 网关超时 > 服务超时 > 数据库超时。反过来会导致「下层还在算，上层已经放弃并重试」，把压力放大。
 *   - **重试只对幂等操作开放**，且必须带退避和次数上限。重试放大的经典事故：3 层各重试 3 次，实际请求量变成 27 倍。
 *   - **失败要能定位**：一次请求至少要有 trace id、客户端 IP、UA、耗时分解、状态码、上游耗时。没有这些，线上只能靠猜。
 *   - **可观测性三段**：指标（QPS/延迟分位/错误率）、日志（结构化、带 trace id）、链路追踪（每跳 span）。分位数比平均值有用得多 —— 平均 50 ms 可能藏着 5% 的 3 秒请求。
 *   - **首屏感知 ≠ 服务端耗时**：TTFB、FCP、LCP 是不同阶段。服务端 100 ms 但 CSS 阻塞渲染，用户照样觉得慢。
 *   - 易错点：只看平均值不看 P99；在客户端重试又在网关重试（放大）；把所有超时设成同一个值（失去层级）；缓存了 HTML 却没给静态资源加哈希（发版白屏）；忽略 DNS 首次解析的耗时。
 * ============================================================
 */

package com.heima.ch35;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Timeline` 加 `parallel(List<Span>)`：把可并行的阶段（如多个静态资源）按「取最大值」而不是求和计入总耗时，对比串行与并行的差距。
 * 2. 实现一个 `Simulator`：输入 RTT、缓存命中率、服务端耗时、重试层数，输出端到端 P50/P99 估算，并找出瓶颈段。
 * 3. 写一个 `diagnose(Timeline t)`：按「DNS > 100ms」「握手 > 2×RTT」「服务端 > 200ms」「回源 > 50ms」给出具体的优化建议。
 * 4. 思考题：为什么把客户端超时设得比网关短会导致「请求已经成功但用户看到失败」？这种情况下重试会造成什么后果？
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 跟踪一个 HTTPS 请求从输入域名到拿到响应的全过程：DNS 解析、TCP 握手、TLS 协商、HTTP 报文构造、缓存判断、HTTP/2 多路复用，每一段都用手写模拟或真实请求验证。
 * 【任务】
 * 1. 手写 DNS 解析流程模拟：本地缓存 → hosts → 递归查询，打印每一跳的耗时与结果，说明 TTL 的作用。
 * 2. 用状态机模拟 TCP 三次握手与四次挥手，打印每次状态迁移与报文标志位，说明为什么是三次不是两次。
 * 3. 手拼一个 HTTP/1.1 请求报文（请求行、头、空行、体），用 Socket 发给本机 HttpServer，解析响应状态行与响应头。
 * 4. 验证缓存：同一个 URL 连续请求两次，观察 200 与 304 的差异，说明强缓存与协商缓存的判定顺序。
 * 【验收清单】
 * [ ] DNS 模拟中命中缓存与未命中两条路径都能打印完整过程。
 * [ ] TCP 状态机迁移序列正确（SYN_SENT → ESTABLISHED 等），标志位标注无误。
 * [ ] 手拼报文能被服务端正确解析并返回 200，响应头能读出 Content-Length 与 Content-Type。
 * [ ] 第二次请求返回 304 且响应体为空，能说出是哪个头触发了协商缓存。
 * ============================================================
 */
