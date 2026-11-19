/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：68-05
 * 课名：数据权限：行列级控制
 * ------------------------------------------------------------
 * 知识点：
 *   - **数据权限的三个粒度**：**库/表级**（能不能看这张表）、**行级**（能看到哪些行——如只能看本部门订单）、**列级**（能看到哪些字段——如客服看不到身份证）——**敏感数据的访问控制必须细到行列**。
 *   - **行级权限的两种实现**：**SQL 改写**（在查询条件里自动注入 `dept_id = 当前用户部门`，类似 55-03 的租户注入）、**视图/行安全策略**（数据库原生 RLS）——**应用层改写最通用**。
 *   - **列级权限的实现**：**动态脱敏**（68-03，无权看则掩码）、**字段过滤**（不返回该字段）、**加密字段**（无权者拿不到密钥）——**按角色配置"字段 × 角色"矩阵**。
 *   - **权限模型的演进**：**RBAC**（角色权限，最常用）-> **ABAC**（属性权限，按数据属性+用户属性动态判定，更灵活但更难测）——**多数系统 RBAC + 少量 ABAC 规则**足够。
 *   - **与 36-05/49 章的关系**：36-05 讲"越权漏洞"（IDOR）、49-07 讲"防腐层双键校验"——**行级权限就是"数据访问的 IDOR 防护"**（查不到不属于你的行，而不是查到了不给你看）。
 *   - 易错点：只做菜单权限不做数据权限（能调接口但看到全量）；行级权限在应用层拼 SQL（注释/绕过）；列级权限只在前端隐藏（接口仍返回）；权限矩阵无评审（越加越乱）。
 * ============================================================
 */

package com.heima.ch68;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DataPermission {
    record User(String id, String role, String dept) {
    }

    record OrderRow(String orderId, String dept, String ownerId,
                    long amountCents, String phone) {
    }

    /** 行级权限：按角色注入数据范围 + 列级权限：字段可见性 */
    static List<String> query(User user, List<OrderRow> all,
                              Map<String, List<String>> columnPerm) {
        List<String> out = new ArrayList<>();
        List<String> visibleCols = columnPerm.getOrDefault(user.role(), List.of());
        for (OrderRow row : all) {
            if (!visible(user, row)) {
                continue;                      // 行级过滤：看不到的行直接不返回
            }
            StringBuilder line = new StringBuilder();
            line.append(row.orderId());
            if (visibleCols.contains("amount")) {
                line.append(" 金额=").append(row.amountCents());
            }
            if (visibleCols.contains("phone")) {
                line.append(" 手机=").append(row.phone());
            } else if (visibleCols.contains("phone_masked")) {
                line.append(" 手机=").append(maskPhone(row.phone()));
            }
            out.add(line.toString());
        }
        return out;
    }

    /** 行级策略：管理员全量，主管看本部门，客服看本部门 */
    static boolean visible(User user, OrderRow row) {
        return switch (user.role()) {
            case "admin" -> true;
            case "manager", "agent" -> row.dept().equals(user.dept());
            case "owner" -> row.ownerId().equals(user.id());
            default -> false;
        };
    }

    static String maskPhone(String phone) {
        return phone.length() >= 7
                ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4)
                : "***";
    }

    public static void main(String[] args) {
        List<OrderRow> all = new ArrayList<>(List.of(
                new OrderRow("o-1", "杭州", "u-1", 6900, "13800001234"),
                new OrderRow("o-2", "杭州", "u-2", 3900, "13800005678"),
                new OrderRow("o-3", "上海", "u-3", 12900, "13900001111")));

        Map<String, List<String>> columnPerm = new LinkedHashMap<>();
        columnPerm.put("admin", List.of("amount", "phone"));
        columnPerm.put("manager", List.of("amount", "phone_masked"));
        columnPerm.put("agent", List.of("amount", "phone_masked"));
        columnPerm.put("owner", List.of("amount"));

        System.out.println("【列权限矩阵】");
        columnPerm.forEach((role, cols) -> System.out.printf("  %-8s 可见列=%s%n", role, cols));

        System.out.println();
        System.out.println("【不同角色的查询结果】");
        List<User> users = List.of(
                new User("u-0", "admin", "-"),
                new User("u-1", "manager", "杭州"),
                new User("u-2", "agent", "杭州"),
                new User("u-3", "owner", "上海"));
        for (User u : users) {
            System.out.printf("  %s（%s/%s）：%n", u.id(), u.role(), u.dept());
            query(u, all, columnPerm).forEach(line -> System.out.println("    " + line));
        }

        System.out.println();
        System.out.println("【要点】");
        System.out.println("  行级：不返回无权行（不是返回来再隐藏）——否则接口可被穷举");
        System.out.println("  列级：无权列要么不返回、要么掩码；绝不只在前端隐藏");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现行列级权限：3 个角色 × 2 个部门，验证每个角色只能看到授权行与授权列。
 * 2. 把"只在前端隐藏字段"改成"服务端不返回"，用抓包对比说明两者安全差异。
 * 3. 思考题：为什么行级权限要"不返回"而不是"返回空值"？从"存在性泄露"与"穷举攻击"分析（55-04 同款）。
 * ============================================================
 */
