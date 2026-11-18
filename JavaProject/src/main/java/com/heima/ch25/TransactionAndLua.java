/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-05
 * 课名：事务、Lua与管道
 * ------------------------------------------------------------
 * 知识点：
 *   - Redis 事务用 `MULTI` / `EXEC` / `DISCARD` / `WATCH`：命令入队后一次性顺序执行，中间不会插入其它客户端的命令。
 *   - 事务**不支持回滚**：某条命令语法错误，整个事务在入队阶段就报错；如果是运行时错误（如对 String 执行 `LPUSH`），只有那条命令失败，其余照常执行。这一点和 MySQL 事务完全不同。
 *   - `WATCH key` 实现乐观锁：EXEC 前如果 key 被别人改过，整个事务放弃执行，返回 nil。
 *   - Lua 脚本：`EVAL` 执行，脚本内所有命令原子执行，是 Redis 里做"复合原子操作"的标准手段（如"判断库存再扣减"）。
 *   - 管道（Pipeline）：客户端把多条命令一次性发出、一次性读回，减少 RTT；它**不保证原子性**，只是省网络往返。
 *   - 选择：需要原子性用 Lua（推荐）或事务；只是批量发送省 RTT 用 Pipeline；跨 key 强一致且要回滚，Redis 做不到，得上数据库事务或分布式事务方案。
 *   - 易错：在事务里做 `GET` 拿结果再判断——入队阶段拿不到返回值，只能靠 `WATCH`；Lua 脚本里执行耗时命令会阻塞整个 Redis。
 * ============================================================
 */

package com.heima.ch25;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TransactionAndLua {
    public static void main(String[] args) {
        System.out.println("=== MULTI / EXEC：命令入队后一起执行 ===");
        List<String> queue = new ArrayList<>();
        queue.add("SET balance 100");
        queue.add("DECRBY balance 30");
        queue.add("INCRBY frozen 30");
        System.out.println("  MULTI -> OK，后续命令进入队列：");
        queue.forEach(s -> System.out.println("    QUEUED " + s));
        System.out.println("  EXEC -> 依次执行 " + queue.size() + " 条，返回各自结果");

        System.out.println();
        System.out.println("=== 事务不回滚：运行时错误只影响那一条 ===");
        Map<String, String> store = new LinkedHashMap<>();
        store.put("name", "张三");       // String 类型
        store.put("count", "10");
        String[] cmds = {"SET count 11", "LPUSH name x", "SET count 12"};
        for (String cmd : cmds) {
            if (cmd.startsWith("LPUSH name")) {
                System.out.println("  " + cmd + " -> (error) WRONGTYPE，但事务继续");
            } else {
                String[] p = cmd.split(" ");
                store.put(p[1], p[2]);
                System.out.println("  " + cmd + " -> OK");
            }
        }
        System.out.println("  最终 count = " + store.get("count") + "（说明后面的命令照样执行了）");

        System.out.println();
        System.out.println("=== WATCH：乐观锁 ===");
        int version = 1;
        System.out.println("  WATCH stock  -> 记录版本 " + version);
        System.out.println("  另一个客户端把 stock 改了，版本变成 2");
        version = 2;
        System.out.println("  EXEC -> 检测到版本变化，事务放弃，返回 nil，需要重试");

        System.out.println();
        System.out.println("=== Lua：库存判断 + 扣减，原子完成 ===");
        int stock = 1;
        System.out.println("  脚本：if redis.call('GET', KEYS[1]) >= ARGV[1] then DECRBY ... end");
        System.out.println("  第 1 个请求：库存 " + stock + " >= 1 -> 扣减成功，剩余 " + (--stock));
        System.out.println("  第 2 个请求：库存 " + stock + " < 1  -> 直接返回 0，不会扣成负数");
        System.out.println("  两条命令在脚本内原子执行，不会出现超卖");

        System.out.println();
        System.out.println("=== Pipeline：省 RTT，不保证原子 ===");
        int rtt = 1;   // 假设一次往返 1ms
        int n = 100;
        System.out.println("  逐条发送 " + n + " 次：" + (n * rtt) + " ms");
        System.out.println("  Pipeline 一次发送：" + rtt + " ms（服务端仍逐条执行，中间可插入别的命令）");
        System.out.println();
        System.out.println("  结论：要原子用 Lua；要吞吐用 Pipeline；要回滚请回数据库。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 Lua 实现"秒杀扣库存"：库存不足返回 0，成功返回剩余库存，并说明为什么不能用 `GET` + `DECRBY` 两条命令。
 * 2. Redis 事务和 MySQL 事务在"回滚"上的差别是什么？举一个因为误以为能回滚而出事故的场景。
 * 3. 什么情况下 Pipeline 会让内存暴涨？如何控制每批的命令数量？
 * ============================================================
 */
