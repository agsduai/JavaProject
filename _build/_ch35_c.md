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
