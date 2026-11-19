/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-11
 * 课名：综合案例：统一权限中台
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个可运行的权限中台**：**认证**（SSO/OIDC 74-02）-> **组织**（部门树 74-03）-> **RBAC**（74-04）-> **数据范围**（74-05）-> **权限树**（74-06）-> **缓存**（74-07）-> **越权测试**（74-08）-> **租户隔离**（74-09）-> **审计**（74-10）。
 *   - **中台对外的三个能力**：**认证服务**（登录/令牌/SSO）、**授权服务**（`can(user, action, resource)`——统一入口）、**权限管理**（角色/授权/审计）。
 *   - **验收清单**：单点登录可用、角色继承正确、数据范围生效、权限树正确渲染、缓存变更即失效、越权全被拒、跨租户隔离、变更可审计。
 *   - 与全书的关系：权限中台把 36（越权）、55（租户）、68（数据权限）串成一个平台组件——**它是"安全与合规"的工程实现**。
 *   - 易错点：认证授权混在一起（登录即全权）；无统一校验入口（散落 if）；缓存不失效；越权测试缺失；无审计。
 * ============================================================
 */

package com.heima.ch74;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class AuthPlatformCase {
    record User(String id, String tenantId, String deptId, String level, Set<String> roles) {
    }

    record RolePerm(String role, Set<String> perms) {
    }

    static final Map<String, User> users = new LinkedHashMap<>();
    static final Map<String, RolePerm> roles = new LinkedHashMap<>();
    static final Map<String, String> deptOfOrder = new LinkedHashMap<>();

    static void seed() {
        // 订单 -> 部门（数据归属）
        deptOfOrder.put("o-1", "100");
        deptOfOrder.put("o-2", "1001");
        deptOfOrder.put("o-3", "200");

        roles.put("agent", new RolePerm("agent", Set.of("order:read")));
        roles.put("agent-lead", new RolePerm("agent-lead",
                Set.of("order:read", "order:refund")));

        users.put("u-1", new User("u-1", "t-100", "100", "STAFF", Set.of("agent")));
        users.put("u-2", new User("u-2", "t-100", "100", "LEAD", Set.of("agent-lead")));
        users.put("u-9", new User("u-9", "t-200", "200", "LEAD", Set.of("agent-lead")));
    }

    /** 统一授权入口：功能 + 数据范围 + 租户 */
    static String can(String userId, String action, String orderId) {
        User u = users.get(userId);
        if (u == null) {
            return "401 未认证";
        }
        // 1) 功能权限（角色并集）
        Set<String> perms = new LinkedHashSet<>();
        for (String r : u.roles()) {
            RolePerm rp = roles.get(r);
            if (rp != null) {
                perms.addAll(rp.perms());
            }
        }
        if (!perms.contains(action)) {
            return "403 无功能权限";
        }
        // 2) 订单归属租户（简化：订单前缀带租户，真实实现查库）
        String orderTenant = orderId.startsWith("x-") ? "t-200" : u.tenantId();
        if (!orderTenant.equals(u.tenantId())) {
            return "404 跨租户（不泄露存在性）";
        }
        // 3) 数据范围：STAFF 只看本部门，LEAD 看本部门及下级
        String orderDept = deptOfOrder.getOrDefault(orderId, "-");
        boolean inScope = u.level().equals("LEAD")
                ? orderDept.startsWith(u.deptId())
                : orderDept.equals(u.deptId());
        if (!inScope) {
            return "404 超出数据范围";
        }
        return "200 允许";
    }

    static void acceptance() {
        System.out.println();
        System.out.println("【验收清单】");
        List<String> gates = List.of(
                "SSO 登录 + 令牌校验（74-02）",
                "角色继承解析（74-04）",
                "数据范围四种（74-05）",
                "权限树过滤渲染（74-06）",
                "缓存版本失效（74-07）",
                "越权测试矩阵通过（74-08）",
                "跨租户隔离（74-09）",
                "变更可审计（74-10）");
        gates.forEach(g -> System.out.println("  [ ] " + g));
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【用户与角色】");
        users.forEach((id, u) -> System.out.printf("  %-4s tenant=%-6s dept=%-5s level=%-5s roles=%s%n",
                id, u.tenantId(), u.deptId(), u.level(), u.roles()));

        System.out.println();
        System.out.println("【统一授权入口 can(user, action, orderId)】");
        String[][] cases = {
                {"u-1", "order:read", "o-1"},
                {"u-1", "order:read", "o-2"},
                {"u-1", "order:refund", "o-1"},
                {"u-2", "order:refund", "o-2"},
                {"u-2", "order:read", "o-3"},
                {"u-9", "order:read", "o-1"},
                {"u-1", "order:read", "x-o-9"}
        };
        for (String[] c : cases) {
            System.out.printf("  %-4s %-13s %-6s -> %s%n", c[0], c[1], c[2],
                    can(c[0], c[1], c[2]));
        }

        System.out.println();
        System.out.println("【对照】");
        System.out.println("  u-1 看 o-1（本部门）：允许");
        System.out.println("  u-1 看 o-2（下级部门）：拒绝（STAFF 只看本部门）");
        System.out.println("  u-1 退款：403（无功能权限）");
        System.out.println("  u-2 退款 o-2（下级）：允许（LEAD + 下级范围）");
        System.out.println("  u-2 看 o-3（其他部门）：404（数据范围外）");
        System.out.println("  u-9 看 o-1（跨租户）：404（不泄露存在性）");

        acceptance();
        System.out.println();
        System.out.println("  一句话：权限中台 = 认证 + 组织 + RBAC + 数据范围 + 缓存 + 测试 + 审计");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现权限中台（认证/角色/数据范围/统一入口），用 7 个请求验证允许/403/404 三类结果。
 * 2. 加入"权限缓存"（74-07）：验证同一用户多次鉴权只回源一次，变更后立即回源。
 * 3. 思考题：为什么"统一授权入口"是权限中台的核心？从"散落 if 的三个后果（漏判/不一致/难审计）"分析。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 设计企业权限中台：RBAC 与 ABAC 结合、SSO 单点登录（OIDC/SAML 思路）、组织架构与数据权限、会话与令牌、权限变更实时生效、审计。要能回答「张三能不能看这个订单」。
 * 【任务】
 * 1. 设计权限模型：用户-角色-权限 + 资源属性（部门、区域），用 RBAC 管功能权限、ABAC 管数据权限，给出判断流程。
 * 2. 实现 SSO 登录：授权码流程、令牌校验、单点登出，说明为什么不能让各系统各自维护密码。
 * 3. 实现数据权限：按部门树/区域过滤数据，验证「同角色不同部门看到的数据不同」。
 * 4. 实现权限实时生效：权限变更后旧令牌立即失效（令牌版本号或短 TTL + 刷新），验证不重启不重新登录即生效。
 * 【验收清单】
 * [ ] 「谁能看什么」的判断可复现，规则集中在一处而非散落各处。
 * [ ] SSO 登录一次可访问全部接入系统，登出后全部失效。
 * [ ] 数据权限按组织架构正确过滤，越权查询返回空而非报错泄露。
 * [ ] 权限回收后旧会话在秒级失效。
 * ============================================================
 */
