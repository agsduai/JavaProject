/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-02
 * 课名：五种基本数据类型
 * ------------------------------------------------------------
 * 知识点：
 *   - `String`：最常用，可存文本、JSON、序列化对象、数字；单 key 最大 512MB；`INCR/DECR` 原子计数，`SETNX` 做锁。
 *   - `Hash`：field-value 结构，适合存对象的多个字段，可单独更新某个字段（`HSET user:1 name 张三`），比整个 String 覆盖省流量。
 *   - `List`：双向链表，`LPUSH/RPUSH` 入队、`LPOP/RPOP` 出队，可做栈、队列、消息列表；`LRANGE` 分页。
 *   - `Set`：无序去重集合，`SADD/SMEMBERS`、`SINTER/SUNION/SDIFF` 做交集并集差集，适合标签、共同好友、抽奖去重。
 *   - `ZSet`：带 score 的有序集合，底层跳表 + 哈希，`ZADD/ZRANGE/ZREVRANGE/ZINCRBY`，适合排行榜、延时队列。
 *   - 选择原则：能用一个 key 表达的整体用 String；需要按字段更新用 Hash；需要顺序或去重用 List/Set；需要排序用 ZSet。
 *   - 易错：用 `KEYS *` 遍历（生产禁用，会阻塞）；用 List 存海量数据做队列（应用 Stream 或专业 MQ）；ZSet 的 score 用浮点，精度只到 2^53。
 * ============================================================
 */

package com.heima.ch25;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// ==================== 示例代码 ====================

public class FiveDataTypes {
    public static void main(String[] args) {
        System.out.println("=== String：计数与缓存 ===");
        Map<String, String> stringStore = new LinkedHashMap<>();
        stringStore.put("article:1001:views", "0");
        // 模拟 INCR：Redis 的 INCR 是原子操作，这里用同步块保证多线程安全
        for (int i = 0; i < 3; i++) {
            int v = Integer.parseInt(stringStore.get("article:1001:views")) + 1;
            stringStore.put("article:1001:views", String.valueOf(v));
        }
        System.out.println("  阅读量 = " + stringStore.get("article:1001:views"));
        System.out.println("  命令：INCR article:1001:views  -> 返回自增后的值");

        System.out.println();
        System.out.println("=== Hash：对象的字段级更新 ===");
        Map<String, Map<String, String>> hashStore = new LinkedHashMap<>();
        Map<String, String> user = new LinkedHashMap<>();
        user.put("name", "张三");
        user.put("age", "18");
        user.put("city", "杭州");
        hashStore.put("user:1", user);
        hashStore.get("user:1").put("age", "19");   // HSET user:1 age 19
        System.out.println("  HGETALL user:1 -> " + hashStore.get("user:1"));
        System.out.println("  只改了 age 一个字段，name/city 不用重新序列化");

        System.out.println();
        System.out.println("=== List：队列与栈 ===");
        List<String> list = new ArrayList<>();
        list.add("msg-1");                                  // RPUSH
        list.add("msg-2");
        list.add(0, "msg-0");                               // LPUSH
        System.out.println("  LRANGE queue 0 -1 -> " + list);
        System.out.println("  LPOP -> " + list.remove(0) + "（队列：先进先出）");
        System.out.println("  RPOP -> " + list.remove(list.size() - 1) + "（栈：后进先出）");
        System.out.println("  剩余 -> " + list);

        System.out.println();
        System.out.println("=== Set：去重与集合运算 ===");
        Set<String> tagA = new LinkedHashSet<>(List.of("Java", "Redis", "MySQL"));
        Set<String> tagB = new LinkedHashSet<>(List.of("Redis", "MQ", "Java"));
        Set<String> inter = new LinkedHashSet<>(tagA);
        inter.retainAll(tagB);
        Set<String> union = new LinkedHashSet<>(tagA);
        union.addAll(tagB);
        Set<String> diff = new LinkedHashSet<>(tagA);
        diff.removeAll(tagB);
        System.out.println("  A = " + tagA);
        System.out.println("  B = " + tagB);
        System.out.println("  SINTER 交集 = " + inter);
        System.out.println("  SUNION 并集 = " + union);
        System.out.println("  SDIFF  A-B  = " + diff);

        System.out.println();
        System.out.println("=== ZSet：排行榜 ===");
        TreeMap<Integer, String> rank = new TreeMap<>((a, b) -> b - a);  // 分数倒序
        rank.put(980, "选手A");     // ZADD board 980 选手A
        rank.put(760, "选手B");
        rank.put(1120, "选手C");
        rank.put(880, "选手D");
        System.out.println("  ZREVRANGE board 0 -1 WITHSCORES ->");
        int pos = 1;
        for (Map.Entry<Integer, String> e : rank.entrySet()) {
            System.out.println("    " + pos++ + ". " + e.getValue() + "  " + e.getKey());
        }
        System.out.println("  ZINCRBY board 50 选手B -> 分数变 810，名次自动重排");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 设计一个"商品详情"的 key 结构，要求：能单独更新库存、能整体失效、能统计浏览量。说明每个 key 用哪种类型。
 * 2. 用 Set 实现"共同关注"和"可能认识的人"，写出对应的集合运算。
 * 3. 为什么排行榜用 ZSet 而不是每次 `ORDER BY` 查数据库？从复杂度和数据库压力两方面回答。
 * ============================================================
 */
