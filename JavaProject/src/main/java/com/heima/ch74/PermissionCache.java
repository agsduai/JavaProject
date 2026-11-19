/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-07
 * 课名：权限缓存与失效
 * ------------------------------------------------------------
 * 知识点：
 *   - **权限校验是高频操作**：每个请求都要查权限（74-04 的 `can()`）——**每次都查库会把权限表打爆**，必须缓存。
 *   - **缓存的三层**：**本地缓存**（Caffeine/ConcurrentHashMap，微秒级，但多实例不一致）、**分布式缓存**（Redis，毫秒级，一致性好 25 章）、**会话内缓存**（登录时算好塞进 token/session，零查询但失效难）——**常用：本地 + Redis 两级**。
 *   - **失效的三种触发**：**权限变更**（改角色权限）、**用户角色变更**（调岗）、**用户登出/被禁用**——**失效要广播**（多实例本地缓存要清，55-08 同款问题）。
 *   - **失效策略**：**主动失效**（变更时删缓存 + 广播，实时性好）、**TTL 过期**（简单但有滞后窗口）、**版本号**（权限数据带版本，缓存 key 含版本，变更即换 key——最优雅）——**推荐"版本号 + 短期 TTL 兜底"**。
 *   - 与 55/68 章的关系：多租户缓存 key 要含 tenantId（55-04）、权限数据本身是敏感数据（68-02）。
 *   - 易错点：缓存永不过期（改权限不生效，用户投诉）；只清本地缓存不清其他实例（部分节点权限错乱）；缓存 key 不含租户（跨租户串权限）；改了权限不通知用户（需重新登录）。
 * ============================================================
 */

package com.heima.ch74;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class PermissionCache {
    /** 权限版本：每次变更 +1，缓存 key 含版本 -> 旧缓存自然失效 */
    static long permVersion = 1;

    /** 模拟两级缓存：本地 + 分布式 */
    static final Map<String, Set<String>> localCache = new LinkedHashMap<>();
    static final Map<String, Set<String>> redisCache = new LinkedHashMap<>();

    /** 权限源（数据库） */
    static final Map<String, Set<String>> db = new LinkedHashMap<>();

    static void seed() {
        db.put("u-1", Set.of("order:read"));
        db.put("u-2", Set.of("order:read", "order:refund"));
    }

    static String key(String tenantId, String userId) {
        return tenantId + ":" + userId + ":v" + permVersion;
    }

    static Set<String> getPermissions(String tenantId, String userId) {
        String k = key(tenantId, userId);
        Set<String> hit = localCache.get(k);
        if (hit != null) {
            System.out.printf("    本地命中 %s%n", k);
            return hit;
        }
        hit = redisCache.get(k);
        if (hit != null) {
            System.out.printf("    Redis 命中 %s%n", k);
            localCache.put(k, hit);
            return hit;
        }
        hit = db.getOrDefault(userId, Set.of());
        System.out.printf("    回源 DB %s -> %s%n", userId, hit);
        redisCache.put(k, hit);
        localCache.put(k, hit);
        return hit;
    }

    static void changePermission(String userId, Set<String> newPerms) {
        db.put(userId, newPerms);
        permVersion++;
        System.out.printf("  权限变更：%s -> %s，版本升到 v%d%n", userId, newPerms, permVersion);
        System.out.println("  旧版本缓存（v" + (permVersion - 1) + "）不再被查询到 -> 等价于失效");
    }

    /** 主动失效：广播到所有实例（示意） */
    static void broadcastInvalidate(String userId) {
        System.out.printf("  广播失效：清本地缓存中 %s 的所有版本%n", userId);
        localCache.keySet().removeIf(k -> k.contains(":" + userId + ":"));
    }

    static void ttlFallback() {
        System.out.println("【TTL 兜底】");
        System.out.println("  即使广播丢失，缓存也有 5 分钟 TTL -> 最多滞后 5 分钟");
        System.out.println("  高敏感权限（删除/审批）：TTL 缩短到 30 秒或直接不缓存");
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【首次查询（回源）】");
        System.out.println("  u-1 -> " + getPermissions("t-100", "u-1"));
        System.out.println("  u-1 再查（本地命中）：" + getPermissions("t-100", "u-1"));

        System.out.println();
        System.out.println("【权限变更 + 版本号】");
        changePermission("u-1", Set.of("order:read", "order:export", "order:refund"));
        System.out.println("  u-1 查询（新版本 key，未命中 -> 回源）："
                + getPermissions("t-100", "u-1"));

        System.out.println();
        System.out.println("【多实例本地缓存失效】");
        System.out.println("  实例 A / B 各有本地缓存 -> 变更时需广播");
        broadcastInvalidate("u-1");

        System.out.println();
        System.out.println("【缓存 key 设计】");
        System.out.println("  " + key("t-100", "u-1") + "（含租户 + 用户 + 版本）");
        System.out.println("  缺租户 -> 跨租户串权限（55-04）");
        System.out.println("  缺版本 -> 变更后仍读旧缓存");

        System.out.println();
        ttlFallback();
        System.out.println();
        System.out.println("  推荐组合：版本号（主）+ 广播（快）+ 短期 TTL（兜底）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现两级缓存 + 版本号失效：验证权限变更后"新 key 未命中回源"，旧缓存自然废弃。
 * 2. 实现"广播失效"：模拟 3 个实例的本地缓存，变更后全部清除（含广播丢失时的 TTL 兜底）。
 * 3. 思考题：为什么"缓存 key 不含租户"会导致跨租户串权限？构造两个租户同 userId 的场景说明。
 * ============================================================
 */
