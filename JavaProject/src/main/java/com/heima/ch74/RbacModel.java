/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-04
 * 课名：RBAC：用户-角色-权限模型
 * ------------------------------------------------------------
 * 知识点：
 *   - **RBAC 的三张表**：**用户**（user）、**角色**（role）、**权限**（permission）+ 两张关联表（user_role、role_permission）——**用户不直接绑权限，通过角色间接获得**，这是可维护性的关键（1000 个用户不用配 1000 次权限）。
 *   - **权限的最小粒度是"资源 + 操作"**：`order:read`、`order:refund`、`user:delete`——**用"资源:动作"命名**，与 64-02 的 scope 同构；**不可用"接口路径"直接当权限**（路径变了权限就失效）。
 *   - **角色的两种设计**：**业务角色**（运营/客服/财务——贴近组织）与**功能角色**（订单管理员/审核员——贴近功能）——**推荐"业务角色 + 少量功能角色"混合**，避免角色爆炸。
 *   - **权限的继承与分组**：角色可继承（"财务主管"继承"财务专员"再追加权限）、权限可分组（菜单树 74-06）——**继承要谨慎**（多层继承难排查）。
 *   - 与 68-05 的关系：RBAC 管"功能权限"，数据权限（行/列）要额外建模（74-05）——**RBAC 不自带数据范围**。
 *   - 易错点：用户直接绑权限（绕过角色，无法批量调整）；角色数量爆炸（每个用户一个角色）；权限命名用中文/路径（不可维护）；不校验后端（只前端隐藏按钮）。
 * ============================================================
 */

package com.heima.ch74;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class RbacModel {
    record Permission(String code, String name, String group) {
    }

    record Role(String code, String name, Set<String> permissions,
                List<String> inherits) {
    }

    record UserAccount(String id, String name, Set<String> roles) {
    }

    static final Map<String, Permission> permissions = new LinkedHashMap<>();
    static final Map<String, Role> roles = new LinkedHashMap<>();
    static final Map<String, UserAccount> users = new LinkedHashMap<>();

    static void seed() {
        permissions.put("order:read", new Permission("order:read", "查看订单", "订单"));
        permissions.put("order:refund", new Permission("order:refund", "订单退款", "订单"));
        permissions.put("order:export", new Permission("order:export", "导出订单", "订单"));
        permissions.put("user:read", new Permission("user:read", "查看用户", "用户"));
        permissions.put("user:delete", new Permission("user:delete", "删除用户", "用户"));

        roles.put("agent", new Role("agent", "客服", Set.of("order:read", "user:read"),
                List.of()));
        roles.put("agent-lead", new Role("agent-lead", "客服主管",
                Set.of("order:refund"), List.of("agent")));
        roles.put("finance", new Role("finance", "财务", Set.of("order:export"), List.of()));
        roles.put("admin", new Role("admin", "管理员",
                Set.of("order:read", "order:refund", "order:export",
                        "user:read", "user:delete"), List.of()));

        users.put("u-1", new UserAccount("u-1", "小客服", Set.of("agent")));
        users.put("u-2", new UserAccount("u-2", "客服主管", Set.of("agent-lead")));
        users.put("u-3", new UserAccount("u-3", "财务", Set.of("finance", "agent")));
        users.put("u-4", new UserAccount("u-4", "管理员", Set.of("admin")));
    }

    /** 解析用户的有效权限：角色 + 继承链（防环用 visited） */
    static Set<String> effectivePermissions(String userId) {
        UserAccount u = users.get(userId);
        if (u == null) {
            return Set.of();
        }
        Set<String> perms = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String roleCode : u.roles()) {
            collect(roleCode, perms, visited);
        }
        return perms;
    }

    static void collect(String roleCode, Set<String> perms, Set<String> visited) {
        if (!visited.add(roleCode)) {
            return;
        }
        Role r = roles.get(roleCode);
        if (r == null) {
            return;
        }
        perms.addAll(r.permissions());
        for (String parent : r.inherits()) {
            collect(parent, perms, visited);
        }
    }

    static boolean can(String userId, String permissionCode) {
        return effectivePermissions(userId).contains(permissionCode);
    }

    /** 权限校验入口（统一收口，避免散落） */
    static String check(String userId, String permissionCode) {
        boolean ok = can(userId, permissionCode);
        System.out.printf("  %-4s 请求 %-13s -> %s%n", userId, permissionCode,
                ok ? "允许" : "拒绝（403）");
        return ok ? "ALLOW" : "DENY";
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【角色与权限】");
        roles.forEach((code, r) -> System.out.printf("  %-10s %-6s 权限=%s 继承=%s%n",
                code, r.name(), r.permissions(), r.inherits()));

        System.out.println();
        System.out.println("【有效权限（含继承）】");
        users.forEach((id, u) -> System.out.printf("  %-4s %-6s roles=%-24s effective=%s%n",
                id, u.name(), u.roles(), effectivePermissions(id)));

        System.out.println();
        System.out.println("【权限校验】");
        check("u-2", "order:refund");      // 继承 agent-lead -> 有
        check("u-2", "order:export");      // 无
        check("u-3", "order:export");      // finance -> 有
        check("u-1", "user:delete");       // 无

        System.out.println();
        System.out.println("【设计要点】");
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("权限命名", "资源:动作（order:refund），不用路径/中文");
        notes.put("角色来源", "业务角色为主（客服/财务），少量功能角色");
        notes.put("继承深度", "≤2 层（再深难排查）");
        notes.put("校验收口", "统一 can() 入口，禁止散落 if");
        notes.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 RBAC：角色继承 + 有效权限解析（防环）+ 统一校验入口，用 4 个用户验证。
 * 2. 对比"用户直连权限"与"用户-角色-权限"在"100 人调岗"场景下的维护成本。
 * 3. 思考题：为什么权限命名用"资源:动作"而不是接口路径？从"接口重构"与"跨端一致"分析。
 * ============================================================
 */
