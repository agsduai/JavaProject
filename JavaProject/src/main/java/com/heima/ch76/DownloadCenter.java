/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-09
 * 课名：下载中心与权限
 * ------------------------------------------------------------
 * 知识点：
 *   - **下载中心是"文件的统一入口"**：**列表**（历史导出/生成的文件）、**状态**（生成中/可下载/已过期）、**操作**（下载/删除/重新生成）——**用户不用再"点了等、等不到就重复点"**。
 *   - **权限三层**：**能否看到该文件**（归属校验：只能看自己/本部门 74-05）、**能否下载**（下载动作单独鉴权，防"看到链接就能拿"）、**链接有效期**（签名 URL 73-06，如 15 分钟）——**三层缺一都可能泄露**。
 *   - **文件归属与共享**：文件有 `ownerId + tenantId`（74-09），**共享要给"被共享人"单独授权**（不是把 URL 发出去）——**URL 即权限是最大的反模式**。
 *   - **过期与清理**：导出文件是"临时产物"（**保留 7 天**），到期删除（73-10 的生命周期）——**下载中心要显示"剩余有效期"**。
 *   - 与 73 章的关系：73 章讲"文件怎么存/怎么发"，**本节讲"谁能在下载中心看到并下载什么"**——**权限与生命周期是治理的两面**。
 *   - 易错点：只校验"登录"不校验"归属"（越权下载）；URL 永久有效（泄露即永久）；下载不记审计（76-10 无法追溯）；不清理（存储无限增长）。
 * ============================================================
 */

package com.heima.ch76;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DownloadCenter {
    record ExportFile(String fileId, String ownerId, String tenantId, String name,
                      LocalDateTime createdAt, Duration ttl, boolean shared) {
        boolean expired(LocalDateTime now) {
            return now.isAfter(createdAt.plus(ttl));
        }
    }

    record User(String userId, String tenantId, String deptId, boolean admin) {
    }

    record AccessResult(boolean allowed, String reason) {
    }

    static final Map<String, List<String>> shares = new LinkedHashMap<>();
    static final List<String> auditLog = new ArrayList<>();

    /** 三层校验：归属 -> 下载权限 -> 链接有效期 */
    static AccessResult canDownload(User user, ExportFile f, LocalDateTime now,
                                    String action) {
        if (!user.tenantId().equals(f.tenantId())) {
            return new AccessResult(false, "跨租户拒绝（74-09）");
        }
        boolean owner = user.userId().equals(f.ownerId());
        boolean sharedToMe = shares.getOrDefault(f.fileId(), List.of())
                .contains(user.userId());
        if (!owner && !sharedToMe && !user.admin()) {
            return new AccessResult(false, "无归属/未共享（越权）");
        }
        if (action.equals("download") && f.expired(now)) {
            return new AccessResult(false, "文件已过期（" + f.ttl().toDays() + " 天）");
        }
        return new AccessResult(true, owner ? "所有者" : sharedToMe ? "被共享" : "管理员");
    }

    /** 生成签名 URL：短有效期 + 绑定 fileId */
    static String signedUrl(ExportFile f, User user, LocalDateTime now) {
        if (!canDownload(user, f, now, "download").allowed()) {
            return null;
        }
        long expiresAt = now.plus(Duration.ofMinutes(15))
                .atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        String sig = Integer.toHexString((f.fileId() + ":" + expiresAt).hashCode());
        return "https://cdn.example.com/" + f.fileId() + "?expires=" + expiresAt
                + "&sig=" + sig;
    }

    static void record(User user, ExportFile f, String action, AccessResult r) {
        auditLog.add(String.format("%s %s %s %s -> %s",
                LocalDateTime.now().withNano(0), user.userId(), action, f.fileId(),
                r.allowed() ? "OK(" + r.reason() + ")" : "DENY(" + r.reason() + ")"));
    }

    public static void main(String[] args) {
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 10, 0);
        ExportFile mine = new ExportFile("f-1", "u-1", "t-1", "我的订单.xlsx",
                now.minusDays(1), Duration.ofDays(7), false);
        ExportFile others = new ExportFile("f-2", "u-2", "t-1", "别人的订单.xlsx",
                now.minusDays(1), Duration.ofDays(7), false);
        ExportFile shared = new ExportFile("f-3", "u-2", "t-1", "共享报表.xlsx",
                now.minusDays(2), Duration.ofDays(7), true);
        ExportFile old = new ExportFile("f-4", "u-1", "t-1", "过期导出.xlsx",
                now.minusDays(10), Duration.ofDays(7), false);
        shares.put("f-3", List.of("u-1"));

        User u1 = new User("u-1", "t-1", "d-1", false);
        User u2 = new User("u-2", "t-1", "d-1", false);
        User cross = new User("u-9", "t-2", "d-9", false);
        User admin = new User("admin", "t-1", "d-0", true);

        System.out.println("【下载中心列表（u-1 视角）】");
        for (ExportFile f : List.of(mine, others, shared, old)) {
            AccessResult r = canDownload(u1, f, now, "list");
            String state = f.expired(now) ? "已过期" : "可下载";
            System.out.printf("  %-14s owner=%-4s 共享=%-5s %-6s -> %s%n",
                    f.name(), f.ownerId(), f.shared(), state,
                    r.allowed() ? "可见" : "不可见（" + r.reason() + "）");
        }

        System.out.println();
        System.out.println("【下载鉴权】");
        record(u1, mine, "download", canDownload(u1, mine, now, "download"));
        record(u1, others, "download", canDownload(u1, others, now, "download"));
        record(u1, shared, "download", canDownload(u1, shared, now, "download"));
        record(u1, old, "download", canDownload(u1, old, now, "download"));
        record(cross, mine, "download", canDownload(cross, mine, now, "download"));
        record(admin, others, "download", canDownload(admin, others, now, "download"));
        auditLog.forEach(l -> System.out.println("  " + l));

        System.out.println();
        System.out.println("【签名 URL（15 分钟）】");
        System.out.println("  u-1 下载 mine：" + signedUrl(mine, u1, now));
        System.out.println("  u-1 下载 others：" + signedUrl(others, u1, now) + "（拒绝，返回 null）");

        System.out.println();
        String[][] rules = {
                {"归属校验", "只能看自己/被共享/管理员", "防越权"},
                {"下载鉴权", "list 与 download 分开校验", "防看到就能拿"},
                {"链接有效期", "签名 URL 15 分钟", "防链接泄露"},
                {"保留期", "导出文件 7 天清理", "73-10 生命周期"},
        };
        for (String[] r : rules) {
            System.out.printf("  %-10s %-30s %s%n", r[0], r[1], r[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现下载中心的三层校验（归属/下载/有效期），用"本人/他人/被共享/跨租户/管理员"5 种身份验证。
 * 2. 实现签名 URL 的生成与过期校验，说明"URL 即权限"为什么危险。
 * 3. 思考题：为什么"列表可见"与"下载可用"要分开鉴权？从"敏感文件的二次管控"分析。
 * ============================================================
 */
