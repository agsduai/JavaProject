/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-09
 * 课名：DNS 解析与 CDN 调度
 * ------------------------------------------------------------
 * 知识点：
 *   - DNS 的作用是**把域名翻译成 IP**，但它的真实职责远不止「查表」：它还承担流量调度（CDN、多活机房、灰度）、故障切换和负载均衡。很多「服务端没问题但用户打不开」的故障，根因在 DNS。
 *   - **解析是递归 + 迭代的组合**：客户端问本地 DNS（递归，替你跑腿）→ 本地 DNS 问根服务器（返回 `.com` 的 NS）→ 问 TLD 服务器（返回 `example.com` 的 NS）→ 问权威服务器（返回 A 记录）。本地 DNS 会把结果按 TTL 缓存。
 *   - **记录类型**：`A`（IPv4）、`AAAA`（IPv6）、`CNAME`（别名，指向另一个域名）、`MX`（邮件）、`NS`（该域由谁权威解析）、`TXT`（校验、SPF、域名所有权）、`SRV`（服务+端口+优先级）、`CAA`（谁能给这个域签证书）。
 *   - **TTL 是「变更延迟」与「查询量」的权衡**：TTL=3600 意味着改了记录要等最多 1 小时才全网生效；TTL=60 生效快，但查询量涨 60 倍。故障切换场景常用 60~300 秒。
 *   - **CNAME 不能和 A 记录共存于同一名字**，也不能用在根域（`example.com` 本身）—— 根域必须直接给 A/AAAA。这是「根域不能直接接 CDN」的技术原因，常见解法是 `A` 记录 + 厂商提供的固定 IP，或用 CNAME 拉平（CNAME flattening）。
 *   - **DNS 缓存有多层**：浏览器缓存 → 操作系统缓存 → 本地 DNS 缓存 → 各级权威的 TTL。排查「域名改了不生效」要逐层清：`ipconfig /flushdns`（Windows）、`systemd-resolve --flush-caches`（Linux）、浏览器 `chrome://net-internals/#dns`。
 *   - **CDN 的本质是「就近 + 缓存」**：用户请求先到最近的边缘节点，命中就直接返回，未命中才回源。命中率每提高 10 个点，源站带宽和响应时间都明显下降。
 *   - **调度靠 DNS 或 Anycast**：DNS 调度按运营商/地域返回不同 IP（GSLB），粒度粗、受 TTL 影响；Anycast 让同一个 IP 在全球多地宣告，由 BGP 选最近路径，切换快但成本高。
 *   - **回源要防雪崩**：缓存过期瞬间大量请求同时回源叫**缓存击穿**；同一批 key 同时过期叫**缓存雪崩**。对策：回源合并（singleflight）、过期时间加随机抖动、stale-while-revalidate、源站限流。
 *   - **`Host` 与 SNI 必须对得上**：CDN 靠 `Host` 头（HTTP）或 SNI（HTTPS）判断回哪个源站。配置漏了会 404 或证书报错。
 *   - 易错点：以为 DNS 改了就立刻生效（TTL 没到就是旧的）；用 CNAME 指向另一个 CNAME 形成环（解析失败）；把 TTL 设成 0 想「实时生效」（很多递归服务器有最小缓存下限）；忽略 DNS 查询本身的耗时（首次解析可能要几十到几百毫秒）。
 * ============================================================
 */

package com.heima.ch35;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `DnsSystem` 加负缓存：解析不到时缓存 `NXDOMAIN` 并设短 TTL（如 30 秒），统计穿透到权威服务器的次数变化。
 * 2. 实现 CNAME 环检测：当 CNAME 链长度超过 8 或回到已访问的名字时返回错误，而不是无限递归。
 * 3. 写一个 `pickEdge(String clientRegion, Map<String, Integer> edgeLatency)`：按延迟选最优边缘节点，并在最优节点不可用时按顺序降级。
 * 4. 思考题：为什么根域（`example.com`）不能直接 CNAME 到 CDN 域名？「CNAME 拉平」是怎么绕开这个限制的，代价是什么？
 * ============================================================
 */
