/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-06
 * 课名：HTTP 缓存：强缓存与协商缓存
 * ------------------------------------------------------------
 * 知识点：
 *   - 缓存的价值是**省掉整条链路**：不发请求 > 发请求但回 304 > 回 200。所以优化的优先级永远是「能不请求就不请求」。
 *   - **强缓存**：浏览器直接用本地副本，**一个字节都不发给服务器**。靠 `Cache-Control: max-age=秒` 或 `Expires`（绝对时间，已过时）控制。命中时 DevTools 显示 `from disk cache` / `from memory cache`。
 *   - **协商缓存**：本地副本过期了，但还想确认能不能复用，于是带上校验器问服务器。服务器回 `304 Not Modified`（无 body），浏览器继续用本地副本。**省的是 body 带宽，不是往返**。
 *   - **两组校验器**：`ETag` ↔ `If-None-Match`（内容指纹，优先级高）；`Last-Modified` ↔ `If-Modified-Since`（秒级精度，文件被改回原内容也判为变了）。两者都给时以 ETag 为准。
 *   - **`ETag` 强弱之分**：强 ETag 要求字节完全一致；弱 ETag 带 `W/` 前缀，表示语义等价即可。压缩代理会改写 body，所以它会削弱 ETag。
 *   - **`Cache-Control` 常用指令**：`max-age=N`（新鲜期）、`s-maxage`（只对共享缓存/CDN 生效）、`no-cache`（**可以存，但每次用前必须校验**）、`no-store`（**完全不许存**，含敏感数据）、`private`（只允许浏览器存）、`public`（CDN 也可存）、`must-revalidate`（过期后必须校验，不许用旧副本）、`immutable`（内容永不变，连刷新都不校验）。
 *   - **`no-cache` 与 `no-store` 最容易被搞混**：前者是「先问再用」，后者是「根本别存」。存密码、token、身份证号的响应必须 `no-store`。
 *   - **`Vary` 决定缓存键的维度**：`Vary: Accept-Encoding` 表示 gzip 版和原文版是两个缓存条目；`Vary: *` 等于不可缓存。漏了 `Vary` 会把 A 用户的个性化页面喂给 B 用户。
 *   - **缓存决策顺序**：先看 `Cache-Control`，没有才看 `Expires`，都没有则用启发式（`Last-Modified` 的 10%）。所以只给 `Last-Modified` 的响应也会被强缓存，很多人不知道。
 *   - **最佳实践**：带内容哈希的静态资源（`app.3f9a2c.js`）用 `max-age=31536000, immutable`；HTML 入口用 `no-cache`（每次都校验）；接口数据按业务定，用户私有数据 `private, no-store` 或短 `max-age`。
 *   - 易错点：给 `index.html` 设一年强缓存导致发版后用户白屏；以为 304 能省 RTT（不能，只省 body）；用 `Last-Modified` 做精确变更检测（秒级精度不够）。
 * ============================================================
 */

package com.heima.ch35;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `CacheEntry` 加 `stale-while-revalidate` 支持：过期但在宽限期内先返回旧副本，并标记「需要后台刷新」。
 * 2. 实现 `Vary` 的真实分桶：按请求头 `Accept-Encoding` 的值把缓存拆成多个条目，验证 gzip 与原文不会互相污染。
 * 3. 写一个 `shouldStore(CacheEntry e, boolean sharedCache)`：`no-store` 一律不存；`private` 在共享缓存中不存；无 `max-age` 但有 `Expires` 时按 `Expires` 计算；都没有时按 `Last-Modified` 的 10% 做启发式。
 * 4. 思考题：为什么给静态资源加内容哈希后可以用 `immutable`，而 HTML 不行？如果 CDN 缓存了旧 HTML 指向已被删除的旧 JS，会出现什么现象？
 * ============================================================
 */
