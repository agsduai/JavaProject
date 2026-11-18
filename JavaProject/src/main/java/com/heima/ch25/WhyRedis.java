/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-01
 * 课名：为什么需要Redis
 * ------------------------------------------------------------
 * 知识点：
 *   - 数据库的瓶颈：连接数有限（MySQL 默认 151）、磁盘随机读慢、复杂查询占 CPU；一台 8C16G 的 MySQL 通常几千 QPS 就到顶。
 *   - 缓存的价值：把热点数据放内存，读走内存后响应从毫秒级降到微秒级，同时把数据库的读压力挡在外面。
 *   - Redis 为什么快：纯内存操作、单线程执行命令（无锁无上下文切换）、IO 多路复用（epoll）、高效的数据结构（SDS、跳表、压缩列表）。
 *   - 单线程指的是"命令执行"单线程；网络 IO 在 6.0 之后是多线程的，持久化、过期删除也有后台线程。
 *   - 适用场景：缓存、会话共享、排行榜、计数器、分布式锁、消息队列（Stream）、去重（Bitmap/HyperLogLog）。
 *   - 不适合：需要复杂事务与多表关联的强一致数据、单 key 超大 value（会阻塞主线程）、海量冷数据（内存贵）。
 *   - 易错：把 Redis 当数据库用（不做持久化兜底、不设过期时间），一旦重启或内存淘汰就丢数据。
 * ============================================================
 */

package com.heima.ch25;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class WhyRedis {
    public static void main(String[] args) {
        System.out.println("=== 一次查询的耗时对比 ===");
        Map<String, String> cost = new LinkedHashMap<>();
        cost.put("MySQL 主键查询（内存命中）", "0.5 ~ 2 ms");
        cost.put("MySQL 主键查询（走磁盘）", "5 ~ 20 ms");
        cost.put("MySQL 复杂关联查询", "50 ~ 500 ms");
        cost.put("Redis GET（本机）", "0.05 ~ 0.2 ms");
        cost.put("Redis GET（同机房网络）", "0.3 ~ 1 ms");
        cost.forEach((k, v) -> System.out.printf("  %-28s %s%n", k, v));

        System.out.println();
        System.out.println("=== 加缓存前后的数据库压力 ===");
        int qps = 10000;
        double hitRate = 0.95;
        System.out.println("  总请求        : " + qps + " QPS");
        System.out.println("  缓存命中率    : " + (int) (hitRate * 100) + "%");
        System.out.println("  打到数据库    : " + (int) (qps * (1 - hitRate)) + " QPS");
        System.out.println("  结论：命中率每提高 1 个点，数据库压力就少一截");

        System.out.println();
        System.out.println("=== Redis 快的四个原因 ===");
        String[] reasons = {
                "纯内存：读写不碰磁盘，纳秒级寻址",
                "单线程执行命令：没有锁竞争和线程切换开销",
                "IO 多路复用：一个线程用 epoll 管上万连接",
                "数据结构专为场景设计：跳表做有序集合、SDS 做字符串"};
        for (int i = 0; i < reasons.length; i++) {
            System.out.println("  " + (i + 1) + ". " + reasons[i]);
        }

        System.out.println();
        System.out.println("=== 常见用途 ===");
        Map<String, String> usage = new LinkedHashMap<>();
        usage.put("缓存", "商品详情、用户信息、首页楼层");
        usage.put("会话", "分布式 session、短信验证码");
        usage.put("计数", "阅读量、点赞数（INCR 原子自增）");
        usage.put("排行榜", "ZSET 按分数排序取 TOP N");
        usage.put("分布式锁", "SETNX + 过期时间，多实例互斥");
        usage.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 计算：某接口 20000 QPS，缓存命中率 99%，数据库实际承受多少 QPS？如果命中率掉到 90% 呢？
 * 2. 用自己的话解释"Redis 单线程为什么反而快"，并说明 6.0 的多线程 IO 改的是哪一部分。
 * 3. 列出三个不适合放 Redis 的数据，并说明原因。
 * ============================================================
 */
