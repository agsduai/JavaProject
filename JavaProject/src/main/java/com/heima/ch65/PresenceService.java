/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-09
 * 课名：在线状态与 Presence
 * ------------------------------------------------------------
 * 知识点：
 *   - **Presence（在线状态）是"高频变更 + 低价值读"的数据**：用户在线/离线/忙碌/离开，状态变化频繁（每次登录/登出/切后台），**读取方多但一致性要求低**（看到几秒前的状态可接受）。
 *   - **状态的三级粒度**：**全局在线**（是否登录）、**设备级**（手机在线/Pad 离线）、**活跃度**（前台活跃/后台挂起）——**业务通常只需要"全局 + 最近活跃时间"**。
 *   - **实现方式**：**连接注册表推导**（有活跃连接即在线，65-02）+ **显式状态上报**（用户手动设"忙碌"）+ **TTL 过期**（心跳超时自动转离线）——**推导 + 覆盖 + 过期**三件套。
 *   - **广播风暴是 Presence 的经典问题**：10 万在线用户互相订阅状态，每次状态变更广播给所有好友——**必须限制**：只订阅"当前可见的会话/好友"、批量合并（1 秒内的变更合并一次）、**只推给"最近有交互"的联系人**。
 *   - **不持久化 vs 持久化**：在线状态放 Redis（TTL 自动过期，55-05 配额思想的存储版）；**最近活跃时间持久化**（用于"3 天前活跃"展示）；**手动状态（忙碌/勿扰）持久化**（用户设置）。
 *   - 易错点：状态变更全量广播（广播风暴）；用数据库存在线状态（高频写打爆 DB）；状态无 TTL（进程崩溃永远"在线"）；把在线状态当强一致数据（不值得）。
 * ============================================================
 */

package com.heima.ch65;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PresenceService {
    /** 在线状态：userId -> (状态, 最后心跳, 手动状态) */
    record Presence(String status, long lastHeartbeatMs, String manualStatus) {
        boolean online(long now, long ttlMs) {
            return now - lastHeartbeatMs <= ttlMs;
        }
    }

    static final Map<String, Presence> presence = new LinkedHashMap<>();
    static final long TTL = 60_000;

    static void heartbeat(String userId, long now) {
        Presence p = presence.getOrDefault(userId, new Presence("online", now, null));
        presence.put(userId, new Presence("online", now, p.manualStatus()));
        System.out.printf("  %s 心跳 -> 在线%n", userId);
    }

    static void setManual(String userId, String manual, long now) {
        Presence p = presence.getOrDefault(userId, new Presence("online", now, null));
        presence.put(userId, new Presence(p.status(), p.lastHeartbeatMs(), manual));
        System.out.printf("  %s 手动设置 -> %s%n", userId, manual);
    }

    static void logout(String userId, long now) {
        Presence p = presence.getOrDefault(userId, new Presence("offline", now, null));
        presence.put(userId, new Presence("offline", now, p.manualStatus()));
        System.out.printf("  %s 登出 -> 离线%n", userId);
    }

    /** 查询：推导在线 + 覆盖手动状态 + 超时降级 */
    static String query(String userId, long now) {
        Presence p = presence.get(userId);
        if (p == null) {
            return "offline（无记录）";
        }
        if (p.manualStatus() != null) {
            return p.manualStatus() + "（手动）";
        }
        if (!p.online(now, TTL)) {
            return "offline（心跳超时，最后活跃 " + (now - p.lastHeartbeatMs()) / 1000 + "s 前）";
        }
        return "online";
    }

    /** 批量订阅：只查当前可见的好友，且合并变更（1s 窗口） */
    static void batchQuery(List<String> visible, long now) {
        System.out.println("  批量查询（可见 " + visible.size() + " 人）：");
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String u : visible) {
            String s = query(u, now).split("（")[0];
            byStatus.merge(s, 1, Integer::sum);
        }
        System.out.println("    " + byStatus + "（一次批量，不逐个广播）");
    }

    public static void main(String[] args) {
        long now = 1_000_000;
        System.out.println("【状态流转】");
        heartbeat("u-1", now);
        setManual("u-1", "忙碌", now);
        heartbeat("u-2", now);
        logout("u-3", now);
        System.out.println();

        System.out.println("【查询】");
        System.out.println("  u-1：" + query("u-1", now + 10_000));
        System.out.println("  u-2：" + query("u-2", now + 10_000));
        System.out.println("  u-3：" + query("u-3", now + 10_000));
        System.out.println("  u-9：" + query("u-9", now));
        System.out.println();

        System.out.println("【心跳超时自动降级】");
        System.out.println("  u-2 @+70s：" + query("u-2", now + 70_000));
        System.out.println();

        batchQuery(List.of("u-1", "u-2", "u-3", "u-9"), now + 70_000);
        System.out.println();
        System.out.println("  三件套：连接推导 + 手动覆盖 + TTL 过期；广播要按可见范围批量");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Presence 服务：心跳刷新/手动状态/登出/TTL 过期，用 4 个用户跑出各状态与降级。
 * 2. 实现"广播限流"：状态变更只推给"最近 7 天有会话"的联系人，并合并 1 秒内的多次变更，量化推送量下降。
 * 3. 思考题：为什么在线状态适合放 Redis 而不放 MySQL？从"高频写 + TTL 自动过期 + 可容忍丢失"三个特征分析。
 * ============================================================
 */
