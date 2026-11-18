/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-04
 * 课名：持久化：RDB与AOF
 * ------------------------------------------------------------
 * 知识点：
 *   - Redis 是内存数据库，进程退出内存就没了，持久化解决"重启后数据还在"的问题。
 *   - **RDB**（快照）：把某一时刻的全量数据二进制写到 `dump.rdb`；触发方式有 `save`（阻塞）、`bgsave`（fork 子进程，写时复制）、配置 `save 900 1`（900 秒内至少 1 次写就触发）。
 *   - 优点：文件紧凑、恢复快，适合备份和全量迁移。
 *   - 缺点：两次快照之间的数据会丢；fork 大内存实例时会有短暂卡顿。
 *   - **AOF**（追加日志）：把每条写命令追加到 `appendonly.aof`；刷盘策略 `appendfsync always/everysec/no`，生产一般用 `everysec`（最多丢 1 秒）。
 *   - 优点：数据更安全，可读、可修复。
 *   - 缺点：文件比 RDB 大，恢复慢；需要 `BGREWRITEAOF` 重写压缩。
 *   - 混合持久化（4.0+，`aof-use-rdb-preamble yes`）：AOF 文件前半段是 RDB 全量，后半段是增量命令，兼顾恢复速度与数据安全。
 *   - 主从 + 哨兵/集群解决的是"机器挂了"，持久化解决的是"进程/机器重启后数据还在"，两者不能互相替代。
 *   - 易错：只开 RDB 就以为数据万无一失；`appendfsync always` 会显著拖慢写入；AOF 重写期间磁盘写满会导致 Redis 拒绝写入。
 * ============================================================
 */

package com.heima.ch25;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PersistenceDemo {
    public static void main(String[] args) {
        System.out.println("=== RDB：定时拍快照 ===");
        // 模拟内存数据 + RDB 文件（这里用 List 表示快照内容）
        Map<String, String> memory = new LinkedHashMap<>();
        List<String> rdbFile = new ArrayList<>();
        memory.put("user:1:name", "张三");
        memory.put("user:1:age", "18");
        memory.put("item:100:stock", "50");

        System.out.println("  触发条件：save 900 1（900 秒内至少 1 次写）");
        System.out.println("  执行 BGSAVE：fork 子进程，把当前全量数据写入 dump.rdb");
        rdbFile.addAll(memory.keySet());
        System.out.println("  快照内容: " + rdbFile);
        System.out.println("  快照之后又写了 2 条新数据（这些还没进 RDB）");
        memory.put("item:101:stock", "20");
        memory.put("order:9001:status", "PAID");
        System.out.println("  当前内存: " + memory.keySet());
        System.out.println("  -> 此刻断电，重启后只能恢复到快照那一刻，丢失 2 条");

        System.out.println();
        System.out.println("=== AOF：追加每条写命令 ===");
        List<String> aofFile = new ArrayList<>();
        aofFile.add("SET user:1:name 张三");
        aofFile.add("SET user:1:age 18");
        aofFile.add("SET item:100:stock 50");
        aofFile.add("SET item:101:stock 20");
        aofFile.add("SET order:9001:status PAID");
        System.out.println("  文件内容：");
        aofFile.forEach(s -> System.out.println("    " + s));
        System.out.println("  刷盘策略 appendfsync everysec -> 最多丢 1 秒的数据");
        System.out.println("  -> 此刻断电，重放这 5 条命令，数据一条不少");

        System.out.println();
        System.out.println("=== AOF 重写 ===");
        List<String> before = new ArrayList<>(List.of(
                "SET counter 0", "INCR counter", "INCR counter", "INCR counter",
                "INCR counter", "INCR counter", "DEL tmp", "SET tmp a", "SET tmp b"));
        List<String> after = new ArrayList<>(List.of("SET counter 5", "SET tmp b"));
        System.out.println("  重写前 " + before.size() + " 条：" + before);
        System.out.println("  重写后 " + after.size() + " 条：" + after);
        System.out.println("  原理：读当前内存状态，用最少命令重建同样的数据");

        System.out.println();
        System.out.println("=== 三种方案对比 ===");
        Map<String, String> cmp = new LinkedHashMap<>();
        cmp.put("RDB", "恢复快、文件小；可能丢几分钟数据");
        cmp.put("AOF", "数据安全；文件大、恢复慢");
        cmp.put("混合持久化", "RDB 打底 + AOF 增量，生产推荐");
        cmp.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
        System.out.println();
        System.out.println("  推荐配置：appendonly yes / appendfsync everysec / aof-use-rdb-preamble yes");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 分别说明 RDB 的 `save` 和 `bgsave` 的区别，`bgsave` 为什么能边写边拍快照（提示：写时复制）。
 * 2. 一个只开 RDB 的 Redis 配置为 `save 300 10`，最坏情况下会丢多少数据？
 * 3. 为什么生产环境推荐混合持久化而不是单纯 AOF？从恢复速度和数据安全两个角度回答。
 * ============================================================
 */
