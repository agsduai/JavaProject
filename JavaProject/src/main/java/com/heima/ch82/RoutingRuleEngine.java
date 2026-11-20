/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-03
 * 课名：分流规则引擎与稳定分桶
 * ------------------------------------------------------------
 * 知识点：
 *   - **分流规则要可配置、可组合**：**白名单（指定用户）→ 地域/机型（定向放量）→ 比例（随机放量）**——**按优先级短路求值，命中即返回**。
 *   - **比例分流必须稳定**：**同一个用户在 5% 阶段进了灰度，扩到 10% 时必须还在灰度**——**靠「分桶值固定 + 阈值单调递增」实现**，**不能每次请求重新随机**。
 *   - **稳定分桶的做法**：**`bucket = hash(userId) % 10000`**，**5% 就是 `bucket < 500`，10% 就是 `bucket < 1000`**——**阈值变大只增不减，天然嵌套**。
 *   - **hash 必须用稳定算法**：**不能用 `String.hashCode()` 之外的随机源，也不能加时间戳/随机数**——**同一用户在任何机器、任何时间算出来必须是同一个值**。
 *   - **嵌套性要能验证**：**`5% 的用户集合 ⊆ 10% 的用户集合 ⊆ 25% 的用户集合`**——**这条不成立，实验数据就废了（同一用户在不同阶段换了组）**。
 *   - 易错点：每次请求 `Math.random() < 0.05`（用户反复横跳）；扩容后取模的模数变了（`% 100` 改 `% 1000`，老用户全被打散）；白名单用户被比例规则覆盖。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class RoutingRuleEngine {
    // ---------- 请求上下文 ----------
    record Ctx(String userId, String region) {
    }

    // ---------- 规则：命中就短路 ----------
    interface Rule {
        String name();

        boolean match(Ctx c);
    }

    record WhitelistRule(Set<String> users) implements Rule {
        public String name() {
            return "白名单";
        }

        public boolean match(Ctx c) {
            return users.contains(c.userId());
        }
    }

    record RegionRule(Set<String> regions) implements Rule {
        public String name() {
            return "地域";
        }

        public boolean match(Ctx c) {
            return regions.contains(c.region());
        }
    }

    // 比例规则：bucket 固定，阈值可变 -> 天然嵌套
    record PercentRule(int permille) implements Rule {
        public String name() {
            return "比例" + permille / 10.0 + "%";
        }

        public boolean match(Ctx c) {
            return bucket(c.userId()) < permille * 10;
        }
    }

    // 稳定分桶：同一 userId 永远落在同一个桶
    static int bucket(String userId) {
        int h = 0x811C9DC5;                        // FNV-1a 32 位
        for (int i = 0; i < userId.length(); i++) {
            h ^= userId.charAt(i);
            h *= 0x01000193;
        }
        return Math.floorMod(h, 10000);
    }

    static class Engine {
        final List<Rule> rules = new ArrayList<>();

        Engine add(Rule r) {
            rules.add(r);
            return this;
        }

        String route(Ctx c) {
            for (Rule r : rules) {
                if (r.match(c)) {
                    return "灰度(" + r.name() + ")";
                }
            }
            return "基线";
        }
    }

    // 不稳定的对照实现：每次请求重新随机（固定种子，保证每次运行数字一致）
    static final Random RND = new Random(20261120L);

    static String unstableRoute(Ctx c, double pct) {
        return RND.nextDouble() < pct ? "灰度" : "基线";
    }

    public static void main(String[] args) {
        Set<String> white = Set.of("u1001", "u1002");
        Set<String> regions = Set.of("杭州", "深圳");
        Engine e5 = new Engine().add(new WhitelistRule(white)).add(new RegionRule(regions))
                .add(new PercentRule(50));
        Engine e10 = new Engine().add(new WhitelistRule(white)).add(new RegionRule(regions))
                .add(new PercentRule(100));

        System.out.println("【规则优先级】命中白名单就直接进灰度，不再看地域和比例");
        System.out.println("  u1001(上海) -> " + e5.route(new Ctx("u1001", "上海")));
        System.out.println("  u2001(杭州) -> " + e5.route(new Ctx("u2001", "杭州")));
        System.out.println("  u3001(北京) -> " + e5.route(new Ctx("u3001", "北京")));

        System.out.println();
        System.out.println("【嵌套性】5% 的用户集合必须被 10% 完整包含");
        Set<String> g5 = new HashSet<>();
        Set<String> g10 = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            Ctx c = new Ctx("u" + i, "北京");
            if (e5.route(c).startsWith("灰度")) {
                g5.add(c.userId());
            }
            if (e10.route(c).startsWith("灰度")) {
                g10.add(c.userId());
            }
        }
        Set<String> leak = new HashSet<>(g5);
        leak.removeAll(g10);
        System.out.printf("  5%% 组 %d 人，10%% 组 %d 人，5%% 里不在 10%% 的：%d 人%n",
                g5.size(), g10.size(), leak.size());
        System.out.printf("  实际比例：5%% 档 %.2f%%，10%% 档 %.2f%%（各 10 万人抽样）%n",
                g5.size() / 1000.0, g10.size() / 1000.0);

        System.out.println();
        System.out.println("【对照】每次请求重新随机的「不稳定分流」：同一用户 20 次请求里换了几次组");
        Ctx fixed = new Ctx("u777", "北京");
        int flips = 0;
        String last = unstableRoute(fixed, 0.10);
        for (int i = 1; i < 20; i++) {
            String now = unstableRoute(fixed, 0.10);
            if (!now.equals(last)) {
                flips++;
                last = now;
            }
        }
        System.out.printf("  不稳定分流：20 次请求换组 %d 次%n", flips);
        int stableFlips = 0;
        last = e10.route(fixed);
        for (int i = 1; i < 20; i++) {
            String now = e10.route(fixed);
            if (!now.equals(last)) {
                stableFlips++;
                last = now;
            }
        }
        System.out.printf("  稳定分桶  ：20 次请求换组 %d 次（bucket=%d）%n", stableFlips, bucket("u777"));

        System.out.println();
        System.out.println("【结论】比例分流的正确姿势：bucket 固定不变，只让阈值单调变大；");
        System.out.println("  这样 5% -> 10% 只是「再多放 5% 进来」，原来那 5% 一个都不会被踢出去。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 PercentRule 的阈值从 50 改成 250（25%），验证 10% 的集合仍被 25% 完整包含。
 * 2. 加一条「排除名单」规则（优先级最高），验证被排除的用户即使命中白名单也进不了灰度。
 * 3. 思考题：如果把分桶从 `% 10000` 改成 `% 1000`，已有灰度用户会发生什么？为什么说这是最隐蔽的事故？
 * ============================================================
 */

