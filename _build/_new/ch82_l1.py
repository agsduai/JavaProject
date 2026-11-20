# -*- coding: utf-8 -*-
LESSONS = [
    dict(
        cls="ReleaseStrategies",
        title="四种发布形态：蓝绿、金丝雀、滚动、功能开关",
        points=[
            "**蓝绿部署**：**两套完整环境，切流量在网关做**——**回滚就是切回来（秒级）**，但**资源翻倍**，且**数据库是共享的，回滚救不了脏数据**。",
            "**金丝雀（灰度）**：**新版本只接一小部分流量（1%~10%），观察后再扩**——**爆炸半径可控**，是**互联网公司的主流选择**，代价是**要能按用户/请求分流**。",
            "**滚动发布**：**逐台替换实例**——**不额外占资源**，但**新旧版本会同时在线一段时间**，**回滚要把整个滚动过程倒着走一遍（分钟级）**，且**期间接口必须双向兼容**。",
            "**功能开关（Feature Flag）**：**代码先全量上线，行为由开关控制**——**回滚是改一个配置（亚秒级）**，但**开关会烂在代码里**，必须有**开关生命周期管理与清理机制**。",
            "**回滚耗时不是一个数，是四段之和**：**发现时间 + 决策时间 + 生效时间 + 数据修复时间**——**前两段是人的问题，后两段才是技术问题**，很多团队只优化了「生效时间」。",
            "易错点：把「能回滚代码」当成「能回滚」；蓝绿共享库却以为切回去就干净了；滚动发布期间改了接口字段（旧实例解析不了）；开关加了不删（一年后没人敢动）。",
        ],
        code=r'''import java.util.ArrayList;
import java.util.List;

public class ReleaseStrategies {
    // 一次发布要付出的代价，全部量化成数字
    record Strategy(String name, int extraInstancePct, int rollbackEffectSec,
                    int blastRadiusPct, boolean needsRouting) {
    }

    // 回滚总耗时 = 发现 + 决策 + 生效 + 数据修复
    record Timeline(int detectSec, int decideSec, int effectSec, int repairSec) {
        int total() {
            return detectSec + decideSec + effectSec + repairSec;
        }
    }

    static void table(List<Strategy> list) {
        System.out.printf("%-12s %-10s %-10s %-10s %s%n",
                "形态", "额外资源", "生效耗时", "爆炸半径", "需要分流能力");
        for (Strategy s : list) {
            System.out.printf("%-12s %-10s %-10s %-10s %s%n",
                    s.name(), s.extraInstancePct() + "%",
                    s.rollbackEffectSec() + "s",
                    s.blastRadiusPct() + "%",
                    s.needsRouting() ? "是" : "否");
        }
    }

    static void timeline(String name, Timeline t) {
        System.out.printf("  %-10s 发现 %3ds + 决策 %3ds + 生效 %3ds + 数据修复 %3ds = %4ds（%.1f 分钟）%n",
                name, t.detectSec(), t.decideSec(), t.effectSec(), t.repairSec(),
                t.total(), t.total() / 60.0);
    }

    public static void main(String[] args) {
        List<Strategy> list = new ArrayList<>();
        list.add(new Strategy("蓝绿", 100, 5, 100, true));
        list.add(new Strategy("金丝雀", 10, 5, 10, true));
        list.add(new Strategy("滚动", 0, 300, 60, false));
        list.add(new Strategy("功能开关", 0, 1, 100, true));
        table(list);

        System.out.println();
        System.out.println("【回滚耗时拆解】同一个「价格算错了」的事故，四种形态各自多久恢复");
        timeline("蓝绿", new Timeline(120, 60, 5, 600));
        timeline("金丝雀", new Timeline(120, 60, 5, 0));
        timeline("滚动", new Timeline(120, 60, 300, 600));
        timeline("功能开关", new Timeline(120, 60, 1, 0));

        System.out.println();
        System.out.println("【读结论】");
        System.out.println("  1. 金丝雀的爆炸半径只有 10%，所以「数据修复」可以是 0——脏数据只影响了 10% 用户");
        System.out.println("  2. 蓝绿和功能开关都是 100% 爆炸半径，代码切回去了，已经写错的订单还得人工修");
        System.out.println("  3. 滚动发布的 300s 生效耗时最容易被忽略：它要倒着滚一遍");
        System.out.println("  4. 四种形态的「发现 120s + 决策 60s」完全一样——这段是人的流程，不换架构就优化不掉");

        System.out.println();
        System.out.println("【选型】");
        System.out.println("  改动影响资金/库存  -> 金丝雀 + 功能开关（双重保险）");
        System.out.println("  纯前端/展示层改版  -> 功能开关（最快回滚）");
        System.out.println("  无状态服务扩容     -> 滚动（最省资源）");
        System.out.println("  数据库大版本升级   -> 蓝绿 + 双写（唯一能整体切换的方案）");
    }
}''',
        exercises=[
            "给滚动发布加一条约束：集群 20 台、每台启动 40s、滚动步长 2 台，算出全量替换与回滚各需多久。",
            "把「发现时间」从 120s 改成 30s（加自动告警），重算四种形态的总回滚耗时，说明瓶颈转移到了哪里。",
            "思考题：为什么说「蓝绿共享数据库时，切回去不等于回滚干净」？举一个具体的脏数据例子。",
        ],
    ),
    dict(
        cls="TrafficTagging",
        title="流量染色与全链路透传",
        points=[
            "**染色的本质**：**给一次请求打上「它属于哪个版本/哪个实验组」的标记**，**后续每一跳都要带着它**，否则**下游不知道自己该走哪条路**。",
            "**染色必须全链路透传**：**网关打标 → RPC 透传 → 线程池透传 → 消息队列透传 → 数据库/缓存 key 隔离**——**任何一跳丢失，下游就退化成基线**。",
            "**ThreadLocal 是默认做法，也是最容易丢的地方**：**异步任务、线程池复用、`CompletableFuture`、定时任务**都会丢——**必须显式装饰（wrap Runnable/Callable）或用支持透传的实现**。",
            "**染色的三种载体**：**HTTP Header（跨进程）**、**RPC attachment（跨服务）**、**ThreadLocal（进程内）**——**跨进程靠协议，进程内靠上下文**。",
            "**灰度数据也要隔离**：**缓存 key 加版本前缀、消息队列加 tag、数据库双写**——**否则灰度写的数据会被基线读到**，**这是比代码 bug 更难查的事故**。",
            "易错点：只在入口打标不做透传；用 `@Async` 就丢了上下文；线程池里复用旧标记；缓存不隔离导致灰度数据污染基线。",
        ],
        code=r'''import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TrafficTagging {
    // ---------- 进程内上下文 ----------
    static final ThreadLocal<String> TAG = new ThreadLocal<>();

    static void setTag(String t) {
        TAG.set(t);
    }

    static String tag() {
        return TAG.get();
    }

    static void clear() {
        TAG.remove();
    }

    // 下游服务：有 tag 走灰度，没 tag 走基线
    static String callDownstream(String where) {
        String t = tag();
        String route = (t == null) ? "基线" : ("灰度-" + t);
        System.out.printf("    %-22s 收到 tag=%-6s -> 走 %s%n", where, String.valueOf(t), route);
        return route;
    }

    // 手动装饰：把当前线程的 tag 捕获下来，交给线程池执行
    static Runnable wrap(Runnable task) {
        String captured = tag();
        return () -> {
            String old = tag();
            setTag(captured);
            try {
                task.run();
            } finally {
                if (old == null) {
                    clear();
                } else {
                    setTag(old);
                }
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        System.out.println("【场景1】同步调用链：网关打标，服务A、服务B 依次调用");
        setTag("gray-v2");
        callDownstream("服务A(同步)");
        callDownstream("服务B(同步)");
        clear();

        System.out.println();
        System.out.println("【场景2】服务A 把活丢给线程池（未装饰）：ThreadLocal 丢失");
        setTag("gray-v2");
        pool.submit(() -> callDownstream("服务B(线程池-裸提交)")).get();
        clear();

        System.out.println();
        System.out.println("【场景3】服务A 用 wrap 装饰后再提交：tag 被捕获并还原");
        setTag("gray-v2");
        pool.submit(wrap(() -> callDownstream("服务B(线程池-wrap)"))).get();
        clear();

        System.out.println();
        System.out.println("【场景4】线程池复用：上一个任务留下的脏标记污染了下一个基线任务");
        // 必须固定同一个线程才能复现「线程复用」，多线程池可能把两个任务分到不同线程
        ExecutorService single = Executors.newSingleThreadExecutor();
        single.submit(() -> {
            setTag("gray-v2");                     // 故意不清理
            callDownstream("服务B(不清理)");
        }).get();
        single.submit(() -> callDownstream("服务C(本该走基线)")).get();
        single.shutdown();
        single.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("【场景5】跨进程：tag 靠 Header 传递，每跳都要显式带上");
        Map<String, String> headers = new HashMap<>();
        headers.put("x-gray-tag", "gray-v2");
        System.out.println("    网关写入 Header: " + headers);
        System.out.println("    服务A 透传 Header: " + headers.get("x-gray-tag"));
        System.out.println("    服务B 忘了透传      : null -> 走基线（染色断在这里）");

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("【结论】染色不是「打一个标」就完了，是「每一跳都不能丢」：");
        System.out.println("  同步调用靠 ThreadLocal，异步必须 wrap，跨进程靠 Header/attachment，");
        System.out.println("  线程池用完必须 clear——否则脏标记会让基线用户走进灰度。");
    }
}''',
        exercises=[
            "把场景4 改成「用 finally 清理」，验证服务C 恢复走基线，说明为什么清理要放在 finally。",
            "用 `CompletableFuture.supplyAsync` 替代 `pool.submit` 复现场景2，再加一层自定义 Executor 修好它。",
            "思考题：灰度期间缓存 key 不加版本前缀，会出什么事故？从「基线读到灰度数据」推演一次。",
        ],
    ),
    dict(
        cls="RoutingRuleEngine",
        title="分流规则引擎与稳定分桶",
        points=[
            "**分流规则要可配置、可组合**：**白名单（指定用户）→ 地域/机型（定向放量）→ 比例（随机放量）**——**按优先级短路求值，命中即返回**。",
            "**比例分流必须稳定**：**同一个用户在 5% 阶段进了灰度，扩到 10% 时必须还在灰度**——**靠「分桶值固定 + 阈值单调递增」实现**，**不能每次请求重新随机**。",
            "**稳定分桶的做法**：**`bucket = hash(userId) % 10000`**，**5% 就是 `bucket < 500`，10% 就是 `bucket < 1000`**——**阈值变大只增不减，天然嵌套**。",
            "**hash 必须用稳定算法**：**不能用 `String.hashCode()` 之外的随机源，也不能加时间戳/随机数**——**同一用户在任何机器、任何时间算出来必须是同一个值**。",
            "**嵌套性要能验证**：**`5% 的用户集合 ⊆ 10% 的用户集合 ⊆ 25% 的用户集合`**——**这条不成立，实验数据就废了（同一用户在不同阶段换了组）**。",
            "易错点：每次请求 `Math.random() < 0.05`（用户反复横跳）；扩容后取模的模数变了（`% 100` 改 `% 1000`，老用户全被打散）；白名单用户被比例规则覆盖。",
        ],
        code=r'''import java.util.ArrayList;
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
}''',
        exercises=[
            "把 PercentRule 的阈值从 50 改成 250（25%），验证 10% 的集合仍被 25% 完整包含。",
            "加一条「排除名单」规则（优先级最高），验证被排除的用户即使命中白名单也进不了灰度。",
            "思考题：如果把分桶从 `% 10000` 改成 `% 1000`，已有灰度用户会发生什么？为什么说这是最隐蔽的事故？",
        ],
    ),
]
