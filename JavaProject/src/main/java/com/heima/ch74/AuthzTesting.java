/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-08
 * 课名：越权防护与权限测试
 * ------------------------------------------------------------
 * 知识点：
 *   - **两类越权**：**水平越权**（同角色用户互相越界：用户 A 看 B 的订单）、**垂直越权**（低权限做高权限的事：普通用户调管理接口）——**水平越权更常见**（36-05 的 IDOR）。
 *   - **防护三原则**：**每个接口都鉴权**（不能依赖"这个接口没人知道"）、**资源归属双键校验**（`tenantId + ownerId`，55-04）、**拒绝返回 404 而非 403**（不泄露存在性 68-05）。
 *   - **权限测试的四个层次**：**功能点覆盖**（每个接口 × 每个角色）、**参数篡改**（改 id/租户/角色）、**边界权限**（有 read 无 write）、**越权路径**（绕过前端直接调 API）——**自动化程度决定测试覆盖率**。
 *   - **测试矩阵**：`角色 × 接口 × 预期结果`——**用数据驱动测试**（33 章的方法）；**重点覆盖"敏感接口"**（删除/退款/导出）。
 *   - 与 36-05 的关系：36-05 讲"IDOR 的原理与防护"，本章讲"怎么系统性地测出来"——**测试是防护的验证**。
 *   - 易错点：只测"有权限能通过"不测"无权限被拒"（漏掉 60% 的用例）；测试环境用管理员跑（全通过无意义）；不测参数篡改（改 id 就绕过）；越权返回 403（泄露存在性）。
 * ============================================================
 */

package com.heima.ch74;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class AuthzTesting {
    enum Expect {
        ALLOW, DENY, NOT_FOUND
    }

    record Case(String role, String userId, String action, String resourceOwner,
                String resourceTenant, String userTenant, Set<String> perms,
                Expect expect, String note) {
    }

    /** 模拟接口：功能权限 + 归属 + 租户 */
    static String invoke(Case c) {
        // 1) 功能权限
        if (!c.perms().contains(c.action())) {
            return "403";
        }
        // 2) 租户隔离
        if (!c.userTenant().equals(c.resourceTenant())) {
            return "404";
        }
        // 3) 归属（水平越权）
        if (!c.userId().equals(c.resourceOwner())) {
            return "404";
        }
        return "200";
    }

    static void runMatrix(List<Case> cases) {
        int pass = 0;
        int fail = 0;
        for (Case c : cases) {
            String actual = invoke(c);
            boolean ok = switch (c.expect()) {
                case ALLOW -> actual.equals("200");
                case DENY -> actual.equals("403");
                case NOT_FOUND -> actual.equals("404");
            };
            if (ok) {
                pass++;
            } else {
                fail++;
            }
            System.out.printf("  %-6s %-6s %-16s 期望=%-10s 实际=%-4s %s%n",
                    c.role, c.userId(), c.action(), c.expect(), actual,
                    ok ? "PASS" : "FAIL <<< " + c.note());
        }
        System.out.printf("  结果：pass=%d fail=%d%n", pass, fail);
        System.out.println();
    }

    public static void main(String[] args) {
        Set<String> agentPerms = Set.of("order:read");
        Set<String> adminPerms = Set.of("order:read", "order:delete", "user:delete");

        List<Case> cases = new ArrayList<>(List.of(
                // 有权限 + 自己的资源 -> 允许
                new Case("客服", "u-1", "order:read", "u-1", "t-100", "t-100",
                        agentPerms, Expect.ALLOW, "基线"),
                // 水平越权：改 ownerId
                new Case("客服", "u-1", "order:read", "u-2", "t-100", "t-100",
                        agentPerms, Expect.NOT_FOUND, "水平越权应 404"),
                // 垂直越权：客服调删除
                new Case("客服", "u-1", "order:delete", "u-1", "t-100", "t-100",
                        agentPerms, Expect.DENY, "垂直越权应 403"),
                // 跨租户
                new Case("客服", "u-1", "order:read", "u-1", "t-200", "t-100",
                        agentPerms, Expect.NOT_FOUND, "跨租户应 404"),
                // 管理员自己的资源
                new Case("管理员", "u-9", "order:delete", "u-9", "t-100", "t-100",
                        adminPerms, Expect.ALLOW, "管理员删除自己租户"),
                // 管理员也不能跨租户
                new Case("管理员", "u-9", "order:delete", "u-9", "t-200", "t-100",
                        adminPerms, Expect.NOT_FOUND, "管理员也受租户隔离")
        ));

        System.out.println("【权限测试矩阵】");
        runMatrix(cases);

        System.out.println("【补一个失败用例（演示 FAIL 输出）】");
        List<Case> bad = List.of(new Case("客服", "u-1", "order:read", "u-2",
                "t-100", "t-100", agentPerms, Expect.ALLOW, "误把越权写成允许"));
        runMatrix(bad);

        System.out.println("【测试设计要点】");
        Map<String, String> points = new LinkedHashMap<>();
        points.put("覆盖", "角色 × 接口 × 预期（ALLOW/DENY/NOT_FOUND）");
        points.put("必测", "无权限被拒（不能只测有权限通过）");
        points.put("参数篡改", "改 id / ownerId / tenantId / 角色字段");
        points.put("数据驱动", "用例表进代码库，随接口变更维护（33 章）");
        points.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的 3 个敏感接口（删除/退款/导出）× 3 个角色写权限测试矩阵（27 条），标注预期结果。
 * 2. 实现参数篡改用例：改 ownerId/tenantId 各一条，验证返回 404 而非 403。
 * 3. 思考题：为什么"越权返回 404"比 403 更安全？从"存在性枚举"与"攻击者侦察"分析。
 * ============================================================
 */
