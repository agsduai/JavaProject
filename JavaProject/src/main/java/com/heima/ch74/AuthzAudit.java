/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-10
 * 课名：权限变更审计
 * ------------------------------------------------------------
 * 知识点：
 *   - **权限变更必须留痕**（68-09 的权限域）：**谁**在**何时**给**谁**授了**什么权限**、审批人是谁、原因是什么——**这是内部作恶的第一道防线**（51-07 审计）。
 *   - **三类需要审计的变更**：**授权/收权**（用户-角色、角色-权限）、**角色定义变更**（新增/删除权限）、**敏感操作**（平台管理员跨租户访问）——**变更比访问更危险**（提权一次就能持续作恶）。
 *   - **审计的四要素**：**变更前后快照**（diff 可读）、**操作人 + 审批人**（双人原则）、**生效时间**（立即/定时）、**关联工单**（为什么给）——**不可篡改**（append-only + 哈希链 68-09）。
 *   - **异常检测**：**批量授权**（一次给 100 人加权限）、**高危权限授予**（tenant:manage/user:delete）、**非工作时间变更**、**自授**（自己给自己加权限）——**命中即告警**。
 *   - 与 51-10 的关系：资金操作审计与权限操作审计同一套（append-only + 审计链）——**权限是"能做什么"，比资金更底层**。
 *   - 易错点：审计只记"谁改的"不记"改了什么"（无法复盘）；审计可被删（等于没有）；高危变更无审批（单人就能提权）；不检测异常（批量提权无人发现）。
 * ============================================================
 */

package com.heima.ch74;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class AuthzAudit {
    record AuditEntry(long seq, long atMs, String operator, String approver,
                      String targetUser, String change, String before, String after,
                      String ticket, boolean sensitive) {
    }

    static final List<AuditEntry> ledger = new ArrayList<>();
    static long seq = 0;

    static void grant(String operator, String approver, String targetUser,
                      String change, String before, String after, String ticket,
                      long atMs) {
        boolean sensitive = change.contains("tenant:manage") || change.contains("user:delete");
        seq++;
        ledger.add(new AuditEntry(seq, atMs, operator, approver, targetUser, change,
                before, after, ticket, sensitive));
        System.out.printf("  #%d %s 给 %s %s：%s -> %s（审批=%s，工单=%s）%s%n",
                seq, operator, targetUser, change, before, after, approver, ticket,
                sensitive ? " [高危]" : "");
    }

    /** 异常检测 */
    static void detectAnomalies(long workStart, long workEnd) {
        System.out.println("【异常检测】");
        Map<String, Integer> perOperator = new LinkedHashMap<>();
        for (AuditEntry e : ledger) {
            perOperator.merge(e.operator(), 1, Integer::sum);
            if (e.sensitive()) {
                System.out.printf("  高危授权：%s 给 %s 授 %s%n",
                        e.operator(), e.targetUser(), e.change());
            }
            if (e.atMs() < workStart || e.atMs() > workEnd) {
                System.out.printf("  非工作时间变更：#%d（%d 点）%n", e.seq(),
                        e.atMs() / 3_600_000);
            }
            if (e.operator().equals(e.targetUser())) {
                System.out.printf("  自授权限：#%d %s 给自己授 %s%n",
                        e.seq(), e.operator(), e.change());
            }
            if (e.approver() == null || e.approver().isBlank()) {
                System.out.printf("  无审批：#%d %s 授 %s%n", e.seq(), e.operator(), e.change());
            }
        }
        perOperator.forEach((op, count) -> {
            if (count >= 3) {
                System.out.printf("  批量授权：%s 共 %d 次 -> 需复核%n", op, count);
            }
        });
        System.out.println();
    }

    static void replay(String targetUser) {
        System.out.println("【权限变更历史（按目标用户）】");
        ledger.stream().filter(e -> e.targetUser().equals(targetUser)).forEach(e ->
                System.out.printf("  #%d %s %s：%s -> %s（操作人 %s，审批 %s）%n",
                        e.seq(), e.change(), e.before(), e.before(), e.after(),
                        e.operator(), e.approver()));
    }

    public static void main(String[] args) {
        long workStart = 9 * 3_600_000L;
        long workEnd = 18 * 3_600_000L;
        System.out.println("【授权记录】");
        grant("admin-a", "lead-b", "u-1", "role", "agent", "agent-lead", "T-1001", 10 * 3_600_000L);
        grant("admin-a", "lead-b", "u-2", "perm", "order:read", "order:read,order:refund",
                "T-1002", 11 * 3_600_000L);
        grant("admin-a", "", "u-3", "perm", "none", "tenant:manage", "T-1003", 23 * 3_600_000L);
        grant("admin-c", "lead-b", "admin-c", "role", "agent", "admin", "T-1004",
                14 * 3_600_000L);
        grant("admin-a", "lead-b", "u-4", "perm", "none", "user:delete", "T-1005",
                15 * 3_600_000L);

        System.out.println();
        detectAnomalies(workStart, workEnd);
        replay("u-1");

        System.out.println();
        System.out.println("【审计要求】");
        Map<String, String> req = new LinkedHashMap<>();
        req.put("四要素", "前后快照 + 操作人/审批人 + 生效时间 + 关联工单");
        req.put("不可篡改", "append-only + 哈希链（68-09）");
        req.put("高危双人", "tenant:manage / user:delete 必须审批");
        req.put("保留期", "≥6 个月（合规通常 3 年）");
        req.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现权限变更审计：记录四要素 + 前后快照，用 5 次授权验证可回溯。
 * 2. 实现异常检测四项（高危/非工作时间/自授/无审批）+ 批量授权告警，验证全部命中。
 * 3. 思考题：为什么"权限变更"比"权限访问"更需要审计？从"一次提权可长期作恶"分析。
 * ============================================================
 */
