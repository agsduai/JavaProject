/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-03
 * 课名：key的设计与过期策略
 * ------------------------------------------------------------
 * 知识点：
 *   - 命名规范：`业务:对象:标识:字段`，如 `shop:item:10086:stock`；用冒号分层，便于按前缀统计和批量操作，长度控制在可读范围内。
 *   - 禁止大 key：单个 String 不超过 10KB，Hash/List/Set/ZSet 元素数不超过 5000；大 key 会让删除、迁移、网络传输全部变慢。
 *   - 禁止热 key 集中：同一个 key 被超高并发访问会打满单个分片，需要本地缓存或 key 打散。
 *   - 过期命令：`EXPIRE key 秒`、`PEXPIRE key 毫秒`、`SET key value EX 60`（推荐，设置值和过期一步完成，避免 SET 后进程挂掉留下永不过期的 key）。
 *   - 过期删除策略：**惰性删除**（访问时才检查是否过期）+ **定期删除**（每 100ms 随机抽查 20 个带过期时间的 key，删掉过期的，超过 25% 过期就继续抽查）。
 *   - 内存淘汰策略（`maxmemory-policy`）：`noeviction`（默认，写满报错）、`allkeys-lru`、`allkeys-lfu`、`volatile-lru`、`volatile-ttl`、`allkeys-random`。缓存场景通常选 `allkeys-lru` 或 `allkeys-lfu`。
 *   - 易错：只设 `maxmemory` 不设淘汰策略，写满后所有写命令报 `OOM command not allowed`；给缓存设了过期时间但业务读不到时又回源失败，导致空值反复穿透（见 25-09）。
 * ============================================================
 */

package com.heima.ch25;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class KeyDesignAndExpire {
    public static void main(String[] args) {
        System.out.println("=== key 命名对比 ===");
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("user1name", "缺少分隔符，看不出层级");
        bad.put("k_10086", "缩写无意义，半年后没人看得懂");
        bad.put("shop_item_10086_stock_cache_v2", "过长，浪费内存也难敲");
        Map<String, String> good = new LinkedHashMap<>();
        good.put("user:1:name", "业务:对象:标识:字段");
        good.put("shop:item:10086:stock", "同一前缀便于统计和批量删除");
        good.put("sms:code:13800138000", "带业务语义，能一眼看出用途");
        System.out.println("  反例：");
        bad.forEach((k, v) -> System.out.printf("    %-36s %s%n", k, v));
        System.out.println("  正例：");
        good.forEach((k, v) -> System.out.printf("    %-28s %s%n", k, v));

        System.out.println();
        System.out.println("=== 惰性删除 + 定期删除 ===");
        // key -> 过期时间戳（毫秒），0 表示永不过期
        Map<String, Long> store = new LinkedHashMap<>();
        long now = 1000L;
        store.put("a", now + 100);        // 100ms 后过期
        store.put("b", now + 10000);      // 10s 后过期
        store.put("c", 0L);               // 永不过期
        store.put("d", now + 50);
        System.out.println("  写入 4 个 key，其中 3 个带过期时间");

        // 惰性删除：get 时才发现 a 过期
        System.out.println("  GET a  -> " + lazyGet(store, "a", now + 200) + "（访问时才发现过期并删除）");
        System.out.println("  剩余 key: " + store.keySet());

        // 定期删除：后台线程每 100ms 抽查
        System.out.println("  定期删除抽查一轮（随机取 2 个带过期时间的 key）：");
        int checked = 0;
        Iterator<Map.Entry<String, Long>> it = store.entrySet().iterator();
        while (it.hasNext() && checked < 2) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() == 0) continue;
            checked++;
            if (e.getValue() <= now + 300) {
                System.out.println("    删除过期 key: " + e.getKey());
                it.remove();
            }
        }
        System.out.println("  剩余 key: " + store.keySet());

        System.out.println();
        System.out.println("=== 内存淘汰策略 ===");
        Map<String, String> policies = new LinkedHashMap<>();
        policies.put("noeviction", "不淘汰，写满直接报错（默认，适合当数据库用）");
        policies.put("allkeys-lru", "所有 key 里淘汰最久未使用的（纯缓存首选）");
        policies.put("allkeys-lfu", "所有 key 里淘汰访问频率最低的（能扛住偶发批量扫描）");
        policies.put("volatile-lru", "只在设了过期时间的 key 里淘汰（缓存与持久数据混存时用）");
        policies.put("volatile-ttl", "优先淘汰剩余存活时间最短的");
        policies.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));
        System.out.println();
        System.out.println("  配置示例：maxmemory 2gb / maxmemory-policy allkeys-lru");
    }

    /** 惰性删除：取的时候发现过期就删掉并返回 null */
    static String lazyGet(Map<String, Long> store, String key, long now) {
        Long expireAt = store.get(key);
        if (expireAt == null) return null;
        if (expireAt != 0 && expireAt <= now) {
            store.remove(key);
            return null;
        }
        return "<" + key + " 的值>";
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为电商系统设计 5 个 key（商品详情、库存、购物车、用户 token、首页楼层），写出完整 key 名和所用类型。
 * 2. 解释为什么推荐 `SET key value EX 60` 而不是 `SET` 之后单独 `EXPIRE`。
 * 3. 缓存集群内存被打满，现象是什么？给出 `maxmemory` 与 `maxmemory-policy` 的配置建议并说明理由。
 * ============================================================
 */
