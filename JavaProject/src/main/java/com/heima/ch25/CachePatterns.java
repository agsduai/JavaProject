/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-08
 * 课名：缓存的读写模式
 * ------------------------------------------------------------
 * 知识点：
 *   - **Cache-Aside（旁路缓存）**：读时先查缓存、未命中查库并回写；写时**先更新数据库，再删除缓存**。最常用。
 *   - 为什么是"删除"而不是"更新"缓存：并发写两个请求时，更新缓存可能因执行顺序错乱留下旧值；删除是幂等的，下次读自然回源。
 *   - 为什么"先库后缓存"：先删缓存再更新库，在删除与更新之间若有读请求，会把旧值重新写回缓存，且长期不失效（经典不一致）。
 *   - **延迟双删**：更新库后删一次缓存，再延迟 500ms 左右删第二次，覆盖"读请求把旧值回写"的时间窗。
 *   - **Read-Through / Write-Through**：缓存层自己负责回源和落库，业务只跟缓存打交道（Spring Cache 的 `@Cacheable` 接近 Read-Through）。
 *   - **Write-Behind**：先写缓存，异步批量刷库，吞吐高但可能丢数据，适合计数、日志类。
 *   - 强一致要求高的场景（如账户余额）不要靠缓存兜底，直接读库或用分布式锁串行化。
 *   - 易错：给缓存设了超长 TTL 又不主动删除，数据库改了缓存还是旧值；删缓存失败没有补偿（重试/消息队列/binlog 订阅）。
 * ============================================================
 */

package com.heima.ch25;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class CachePatterns {
    // 模拟数据库
    static final Map<Long, String> DB = new LinkedHashMap<>();
    // 模拟缓存
    static final Map<Long, String> CACHE = new LinkedHashMap<>();

    public static void main(String[] args) {
        DB.put(1L, "机械键盘 v1");
        DB.put(2L, "无线鼠标 v1");

        System.out.println("=== Cache-Aside 读流程 ===");
        System.out.println("  read(1) -> " + read(1L) + "（未命中，回源并回写）");
        System.out.println("  read(1) -> " + read(1L) + "（命中，直接返回）");
        System.out.println("  缓存内容 = " + CACHE);

        System.out.println();
        System.out.println("=== 写流程：先更新库，再删缓存 ===");
        DB.put(1L, "机械键盘 v2");
        CACHE.remove(1L);            // 删除，而不是 update
        System.out.println("  更新数据库为 v2，删除缓存 key=1");
        System.out.println("  read(1) -> " + read(1L) + "（重新回源拿到 v2）");

        System.out.println();
        System.out.println("=== 反面教材：先删缓存，再更新库 ===");
        CACHE.remove(2L);
        System.out.println("  T1 删除缓存 key=2");
        System.out.println("  T2 读请求未命中，查到旧值 v1，回写缓存");
        CACHE.put(2L, DB.get(2L));
        DB.put(2L, "无线鼠标 v2");
        System.out.println("  T1 更新数据库为 v2");
        System.out.println("  -> 缓存里还是 v1，且不会自动失效，长期不一致");

        System.out.println();
        System.out.println("=== 延迟双删补救 ===");
        DB.put(2L, "无线鼠标 v3");
        CACHE.remove(2L);
        System.out.println("  第 1 次删除");
        CACHE.put(2L, "无线鼠标 v2");   // 模拟并发读把旧值写回
        System.out.println("  模拟并发读回写了旧值");
        CACHE.remove(2L);
        System.out.println("  延迟 500ms 后第 2 次删除 -> 缓存干净，下次读到 v3");

        System.out.println();
        System.out.println("=== 四种模式对比 ===");
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("Cache-Aside", "业务自己管缓存，最灵活，最常用");
        modes.put("Read/Write-Through", "缓存层代理回源与落库，业务无感");
        modes.put("Write-Behind", "异步刷库，吞吐最高，可能丢数据");
        modes.put("双写", "写库同时写缓存，一致性差，不推荐");
        modes.forEach((k, v) -> System.out.printf("  %-20s %s%n", k, v));
    }

    static String read(Long id) {
        String v = CACHE.get(id);
        if (v != null) {
            return v + "（缓存命中）";
        }
        v = DB.get(id);
        CACHE.put(id, v);
        return v + "（回源）";
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 详细说明"先更新数据库再删除缓存"为什么优于"先删除缓存再更新数据库"，画时序图说明并发读导致不一致的过程。
 * 2. 延迟双删的延迟时间怎么定？延迟太短和太长分别有什么问题？
 * 3. 一个秒杀商品的库存，适合用 Cache-Aside 吗？说明理由并给出替代方案。
 * ============================================================
 */
