/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-04
 * 课名：建造者模式：链式调用与不可变对象
 * ------------------------------------------------------------
 * 知识点：
 *   - 建造者解决两类问题：**参数过多**（4 个以上可选参数，构造器调用变成"位置记忆游戏"）和**创建过程需要校验/分步**。
 *   - 标准结构：目标类构造器私有 → 静态内部类 `Builder` 持有全部字段 → 每个 setter 返回 `this` 支持链式 → `build()` 里统一校验后调私有构造器。
 *   - **不可变对象**是建造者的最佳搭档：字段全 `final`、集合用 `Collections.unmodifiableXxx` 包装、不提供 setter。线程安全且可以安全共享。
 *   - `build()` 是唯一的校验入口：必填项缺失、数值越界、互斥参数冲突，都在这里抛 `IllegalStateException` 或 `IllegalArgumentException`，避免"对象造出来了但状态非法"。
 *   - 与"重叠构造器"（telescoping constructor）对比：4 个可选参数就需要 16 个构造器组合，或者出现 `new User("张三", null, null, 0, false)` 这种看不懂的调用。
 *   - 与 setter 模式对比：setter 允许"造到一半就使用"，且对象可变；建造者保证"要么完整、要么不存在"。
 *   - 与工厂的区别：工厂关心"造哪一类"，建造者关心"怎么一步步造出这一个"。两者可以组合：工厂方法返回 `Builder`。
 *   - 易错：`Builder` 里直接返回 `this` 却忘了 `build()` 做防御性拷贝（外部改集合会污染对象）；`build()` 不校验，把非法状态推到运行期；给不可变对象留了 setter。
 * ============================================================
 */

package com.heima.ch31;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class BuilderPattern {
    /** 不可变对象：字段全 final，集合不可修改 */
    static final class HttpRequest {
        private final String url;
        private final String method;
        private final Map<String, String> headers;
        private final String body;
        private final int timeoutMs;
        private final int retry;

        private HttpRequest(Builder b) {
            this.url = b.url;
            this.method = b.method;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(b.headers));
            this.body = b.body;
            this.timeoutMs = b.timeoutMs;
            this.retry = b.retry;
        }

        static Builder builder(String url) {
            return new Builder(url);
        }

        @Override
        public String toString() {
            return method + " " + url + " timeout=" + timeoutMs + "ms retry=" + retry
                    + " headers=" + headers + (body == null ? "" : " body=" + body);
        }

        static final class Builder {
            private final String url;
            private String method = "GET";
            private final Map<String, String> headers = new LinkedHashMap<>();
            private String body;
            private int timeoutMs = 3000;
            private int retry = 0;

            private Builder(String url) {
                this.url = url;
            }

            Builder method(String m) {
                this.method = m;
                return this;
            }

            Builder header(String k, String v) {
                this.headers.put(k, v);
                return this;
            }

            Builder body(String b) {
                this.body = b;
                return this;
            }

            Builder timeoutMs(int t) {
                this.timeoutMs = t;
                return this;
            }

            Builder retry(int r) {
                this.retry = r;
                return this;
            }

            /** 唯一校验入口：不合法就不让对象存在 */
            HttpRequest build() {
                if (url == null || !url.startsWith("http")) {
                    throw new IllegalStateException("url 必须以 http 开头: " + url);
                }
                if (timeoutMs <= 0) {
                    throw new IllegalStateException("超时必须大于 0");
                }
                if (retry < 0 || retry > 5) {
                    throw new IllegalStateException("重试次数必须在 0~5 之间: " + retry);
                }
                if ("GET".equals(method) && body != null) {
                    throw new IllegalStateException("GET 请求不应带 body");
                }
                return new HttpRequest(this);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 建造者：只写关心的参数 ===");
        HttpRequest get = HttpRequest.builder("https://api.example.com/orders")
                .header("Authorization", "Bearer token")
                .header("Accept", "application/json")
                .timeoutMs(2000)
                .build();
        System.out.println("  " + get);

        HttpRequest post = HttpRequest.builder("https://api.example.com/orders")
                .method("POST")
                .header("Content-Type", "application/json")
                .body("{\"sku\":\"A1001\",\"qty\":2}")
                .timeoutMs(5000)
                .retry(2)
                .build();
        System.out.println("  " + post);

        System.out.println();
        System.out.println("=== build() 拦截非法状态 ===");
        String[][] cases = {
                {"url 不合法", "ftp://x"},
                {"超时非正", "https://a.com"}
        };
        try {
            HttpRequest.builder("ftp://x").build();
        } catch (IllegalStateException e) {
            System.out.println("  [" + cases[0][0] + "] " + e.getMessage());
        }
        try {
            HttpRequest.builder("https://a.com").timeoutMs(0).build();
        } catch (IllegalStateException e) {
            System.out.println("  [" + cases[1][0] + "] " + e.getMessage());
        }
        try {
            HttpRequest.builder("https://a.com").body("x").build();
        } catch (IllegalStateException e) {
            System.out.println("  [GET 带 body] " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 不可变性验证 ===");
        Map<String, String> ext = new LinkedHashMap<>();
        ext.put("X-Trace", "abc");
        HttpRequest r = HttpRequest.builder("https://a.com").build();
        try {
            r.headers.put("hack", "1");
        } catch (UnsupportedOperationException e) {
            System.out.println("  外部修改 headers 被拒绝: UnsupportedOperationException");
        }
        System.out.println("  结论：对象创建后状态不可变，可安全共享给多线程");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用重叠构造器实现同样功能，数一数需要多少个构造方法，说明可读性差在哪。
 * 2. 给 `HttpRequest` 增加 `List<String> tags` 字段，验证外部拿到列表后不能修改。
 * 3. 把 `Builder` 改成"每步返回新 Builder"的不可变建造者，说明它相比可变 Builder 的优劣。
 * ============================================================
 */
