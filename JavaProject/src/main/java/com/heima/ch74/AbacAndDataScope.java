/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-05
 * 课名：权限模型进阶：ABAC 与数据权限
 * ------------------------------------------------------------
 * 知识点：
 *   - **RBAC 不够用的场景**：**"客服只能看自己负责的订单"**（数据维度依赖用户属性）、**"工作时间外禁止导出"**（环境属性）、**"金额超 1 万需主管审批"**（资源属性）——**RBAC 只回答"能不能做这个动作"，不回答"能对哪些数据做"**。
 *   - **ABAC（基于属性的访问控制）**：**主体属性**（用户等级/部门）、**资源属性**（订单金额/归属）、**环境属性**（时间/IP）、**动作**——用**策略表达式**描述（如 `subject.dept == resource.dept && env.hour in 9..18`）。
 *   - **数据权限的四种范围**（企业标配）：**本人**（OWNER）、**本部门**（DEPT）、**本部门及下级**（DEPT_AND_CHILD 74-03）、**全部**（ALL）——**按角色配置范围，查询时注入条件**（68-05 的落地）。
 *   - **数据权限的实现位置**：**SQL 注入**（查询条件自动加 `dept_id IN (...)`，55-03 同款）、**数据权限中间件**、**数据库行级安全**——**应用层注入最通用**。
 *   - 与 68-05 的关系：68-05 讲"数据权限的实现"，本章讲"权限模型怎么组织"（RBAC + ABAC + 数据范围三层叠加）——**模型与实现分开看**。
 *   - 易错点：数据权限只在前端过滤（接口返回全量）；范围计算不递归（主管看不到下级部门）；ABAC 策略太复杂（无法排查）；环境属性可伪造（IP 头/时间客户端传）。
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

public class AbacAndDataScope {
    enum Scope {
        OWNER, DEPT, DEPT_AND_CHILD, ALL
    }

    record Subject(String userId, String deptId, String level, Set<Long> deptTree) {
    }

    record Order(String orderId, String ownerId, String deptId, long amountCents) {
    }

    record ResourceAttr(String ownerId, String deptId, long amountCents) {
    }

    /** 数据范围 -> 可见的 dept 集合（ALL 返回 null 表示不过滤） */
    static Set<String> scopeDepts(Scope scope, Subject s) {
        return switch (scope) {
            case OWNER -> Set.of();
            case DEPT -> Set.of(s.deptId());
            case DEPT_AND_CHILD -> {
                Set<String> out = new LinkedHashSet<>();
                for (Long d : s.deptTree()) {
                    out.add(String.valueOf(d));
                }
                yield out;
            }
            case ALL -> null;
        };
    }

    /** 行级过滤 */
    static List<Order> filter(List<Order> orders, Scope scope, Subject s) {
        if (scope == Scope.ALL) {
            return orders;
        }
        List<Order> out = new ArrayList<>();
        Set<String> depts = scopeDepts(scope, s);
        for (Order o : orders) {
            if (scope == Scope.OWNER && o.ownerId().equals(s.userId())) {
                out.add(o);
            } else if (scope == Scope.DEPT && o.deptId().equals(s.deptId())) {
                out.add(o);
            } else if (scope == Scope.DEPT_AND_CHILD && depts.contains(o.deptId())) {
                out.add(o);
            }
        }
        return out;
    }

    /** ABAC 策略：主体属性 + 资源属性 + 环境属性 */
    record Policy(String name, String expr) {
    }

    static final List<Policy> policies = List.of(
            new Policy("工作时间外禁止导出",
                    "env.hour < 9 || env.hour > 18 -> DENY export"),
            new Policy("大额订单仅主管可退",
                    "resource.amount > 1000000 && subject.level != LEAD -> DENY refund"),
            new Policy("跨部门订单只读",
                    "resource.dept != subject.dept -> READ_ONLY"));

    static String evaluate(String action, Subject s, ResourceAttr r, int hour) {
        if (action.equals("export") && (hour < 9 || hour > 18)) {
            return "DENY（工作时间外禁止导出：" + hour + " 点）";
        }
        if (action.equals("refund") && r.amountCents() > 1_000_000
                && !s.level().equals("LEAD")) {
            return "DENY（大额订单需主管）";
        }
        if (!r.deptId().equals(s.deptId())) {
            return "READ_ONLY（跨部门）";
        }
        return "ALLOW";
    }

    public static void main(String[] args) {
        Subject agent = new Subject("u-1", "100", "STAFF",
                Set.of(100L, 1001L, 1002L));
        Subject lead = new Subject("u-2", "100", "LEAD", Set.of(100L, 1001L, 1002L));

        List<Order> orders = List.of(
                new Order("o-1", "u-1", "100", 69_00),
                new Order("o-2", "u-9", "100", 199_00),
                new Order("o-3", "u-8", "1001", 39_00),
                new Order("o-4", "u-7", "200", 129_00));

        System.out.println("【数据范围对比】");
        for (Scope scope : Scope.values()) {
            List<String> ids = filter(orders, scope, agent).stream().map(Order::orderId).toList();
            System.out.printf("  %-16s -> %s%n", scope, ids);
        }

        System.out.println();
        System.out.println("【ABAC 策略】");
        policies.forEach(p -> System.out.printf("  %-18s %s%n", p.name(), p.expr()));

        System.out.println();
        System.out.println("【策略求值】");
        ResourceAttr big = new ResourceAttr("u-9", "100", 5_000_000);
        ResourceAttr crossDept = new ResourceAttr("u-7", "200", 129_00);
        System.out.println("  agent(10 点) export 本部门：" + evaluate("export", agent, big, 10));
        System.out.println("  agent(22 点) export 本部门：" + evaluate("export", agent, big, 22));
        System.out.println("  agent 退款大额：" + evaluate("refund", agent, big, 10));
        System.out.println("  lead  退款大额：" + evaluate("refund", lead, big, 10));
        System.out.println("  agent 操作跨部门：" + evaluate("export", agent, crossDept, 10));

        System.out.println();
        System.out.println("【模型分层】RBAC 管功能（74-04）-> 数据范围管行（本章）-> 字段脱敏管列（68-03）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现四种数据范围的行级过滤，用 4 条订单验证 OWNER/DEPT/DEPT_AND_CHILD/ALL 的差异。
 * 2. 实现 3 条 ABAC 策略（时间/金额/跨部门），用 5 个请求验证求值结果。
 * 3. 思考题：为什么"环境属性（时间/IP）"不能信任客户端传值？构造伪造 IP 头绕过策略的例子。
 * ============================================================
 */
