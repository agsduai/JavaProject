/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-09
 * 课名：多租户与权限隔离
 * ------------------------------------------------------------
 * 知识点：
 *   - **多租户权限的两个层次**：**租户内权限**（RBAC/数据范围，74-04/05）与**租户间隔离**（A 租户的用户绝不可能看到 B 租户的任何数据）——**隔离是硬约束，权限是软约束**。
 *   - **隔离的三道防线**：**数据层**（所有表带 `tenant_id` + 查询强制注入 55-03）、**缓存层**（key 含 tenantId 74-07）、**令牌层**（token 里带 tenantId，服务端从 token 取而非请求头 55-03）。
 *   - **平台运营角色的特殊权限**：平台管理员要跨租户（运维/客服支持），但**必须审计 + 租户授权**（55-10 的授权代理模式）——**"跨租户能力"本身是最高危权限**。
 *   - **租户配置对权限的影响**：租户可自定义角色（55-06 的配置分层）——**平台内置角色 + 租户自定义角色**并存，**租户不能修改平台内置角色**（防越权）。
 *   - 与 55 章的关系：55 章讲多租户架构（隔离三方案），本章讲"权限在多租户下怎么组织"——**同一套 RBAC，多了租户维度**。
 *   - 易错点：权限表不带 tenantId（A 租户配的角色 B 租户能用）；平台管理员无审计（内部作恶 51-07）；租户自建角色可提权（拿到平台级权限）；缓存 key 缺租户（74-07）。
 * ============================================================
 */

package com.heima.ch74;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class MultiTenantAuthz {
    record RoleDef(String tenantId, String code, String name, Set<String> perms,
                   boolean builtin) {
    }

    static final Map<String, RoleDef> roleTable = new LinkedHashMap<>();

    static void seed() {
        // 平台内置角色（tenantId = PLATFORM）
        roleTable.put("PLATFORM:admin", new RoleDef("PLATFORM", "admin", "平台管理员",
                Set.of("tenant:read", "tenant:manage", "order:read"), true));
        // 租户角色
        roleTable.put("t-100:agent", new RoleDef("t-100", "agent", "客服",
                Set.of("order:read"), false));
        roleTable.put("t-100:finance", new RoleDef("t-100", "finance", "财务",
                Set.of("order:read", "order:export"), false));
        roleTable.put("t-200:agent", new RoleDef("t-200", "agent", "客服",
                Set.of("order:read", "user:read"), false));
    }

    /** 解析权限：必须带租户，内置角色仅平台可用 */
    static Set<String> permsOf(String tenantId, String roleCode) {
        RoleDef def = roleTable.get(tenantId + ":" + roleCode);
        if (def == null) {
            // 找不到本租户角色 -> 尝试平台内置（仅平台租户）
            def = roleTable.get("PLATFORM:" + roleCode);
            if (def == null || !tenantId.equals("PLATFORM")) {
                return Set.of();
            }
        }
        return def.perms();
    }

    /** 租户自建角色不能包含平台级权限 */
    static String createRole(String tenantId, String code, Set<String> perms) {
        if (tenantId.equals("PLATFORM")) {
            return "拒绝：平台角色需走变更流程";
        }
        for (String p : perms) {
            if (p.startsWith("tenant:") || p.startsWith("platform:")) {
                return "拒绝：租户角色不可含平台权限 " + p;
            }
        }
        roleTable.put(tenantId + ":" + code, new RoleDef(tenantId, code, code, perms, false));
        return "创建成功：" + tenantId + ":" + code + " -> " + perms;
    }

    /** 跨租户访问：平台管理员 + 审计 */
    static String crossTenantAccess(String actorTenant, Set<String> perms,
                                    String targetTenant, String action) {
        if (!actorTenant.equals("PLATFORM")) {
            return "拒绝：非平台账号（跨租户需 404）";
        }
        if (!perms.contains("tenant:manage")) {
            return "拒绝：缺少 tenant:manage";
        }
        System.out.printf("  [审计] 平台管理员访问 %s 的 %s（记录操作人与原因）%n",
                targetTenant, action);
        return "允许（已审计）";
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【角色表（含租户与内置标记）】");
        roleTable.forEach((k, v) -> System.out.printf("  %-18s 权限=%-36s 内置=%b%n",
                k, v.perms(), v.builtin()));

        System.out.println();
        System.out.println("【按租户解析权限】");
        System.out.println("  t-100 + agent：" + permsOf("t-100", "agent"));
        System.out.println("  t-200 + agent：" + permsOf("t-200", "agent")
                + "（同名角色，权限不同）");
        System.out.println("  t-100 + admin：" + permsOf("t-100", "admin")
                + "（租户不能拿平台角色）");
        System.out.println("  PLATFORM + admin：" + permsOf("PLATFORM", "admin"));

        System.out.println();
        System.out.println("【租户自建角色】");
        System.out.println("  " + createRole("t-100", "auditor", Set.of("order:read")));
        System.out.println("  " + createRole("t-100", "sneaky", Set.of("order:read", "tenant:manage")));

        System.out.println();
        System.out.println("【跨租户访问】");
        System.out.println("  t-100 客服查 t-200："
                + crossTenantAccess("t-100", permsOf("t-100", "agent"), "t-200", "order:read"));
        System.out.println("  平台管理员查 t-200："
                + crossTenantAccess("PLATFORM", permsOf("PLATFORM", "admin"), "t-200", "order:read"));

        System.out.println();
        System.out.println("【三道防线】数据层 tenant_id（55-03）+ 缓存 key（74-07）+ 令牌携带（55-03）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现多租户角色解析：同名角色在不同租户权限不同、租户不能拿平台角色，用 4 组验证。
 * 2. 实现"租户自建角色禁止平台权限"的校验，构造一次提权尝试验证拒绝。
 * 3. 思考题：为什么"跨租户能力"是最高危权限？从"单点作恶影响全租户"与"审计必要性"分析。
 * ============================================================
 */
