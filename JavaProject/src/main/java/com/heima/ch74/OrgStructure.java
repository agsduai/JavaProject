/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-03
 * 课名：组织架构与用户体系
 * ------------------------------------------------------------
 * 知识点：
 *   - **权限系统的地基是组织架构**：**租户 -> 公司 -> 部门（树）-> 用户**，用户可**兼职多部门**、部门可**层级嵌套**——**数据权限（74-05）依赖这棵树**（"本部门及下级"）、**审批流依赖这棵树**（61-07 的审批人规则）。
 *   - **用户的三种身份来源**：**内部员工**（HR 系统同步）、**外部客户**（注册）、**系统账号**（服务间调用）——**同一套权限模型要能容纳三类**（用"账号类型"区分）。
 *   - **组织架构的变更很频繁**：部门合并/拆分/人员调动——**权限要"跟着组织走"而不是"跟着人走"**（角色绑在岗位/部门上，人调岗自动获得新权限、失去旧权限）。
 *   - **与 55 章的关系**：多租户下"组织架构也是租户隔离的"（`tenantId + deptId` 双键）；**跨租户的组织数据绝不能混**（55-04）。
 *   - 易错点：部门树用单表 parentId 但查询"所有下级"用递归查询（性能差，应用闭包表/物化路径 34 章）；兼职多部门只支持一个（权限缺失）；人员离职只禁用账号不回收权限（僵尸权限）。
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

public class OrgStructure {
    record Dept(long id, String name, Long parentId, String path) {
        /** 物化路径：/1/10/100/ 便于查"所有下级" */
        String display() {
            return id + ":" + name + " path=" + path;
        }
    }

    record User(String id, String name, List<Long> deptIds, String accountType,
                boolean active) {
    }

    static final Map<Long, Dept> depts = new LinkedHashMap<>();
    static final List<User> users = new ArrayList<>();

    static void seed() {
        depts.put(1L, new Dept(1, "集团", null, "/1/"));
        depts.put(10L, new Dept(10, "电商事业部", 1L, "/1/10/"));
        depts.put(100L, new Dept(100, "订单组", 10L, "/1/10/100/"));
        depts.put(101L, new Dept(101, "支付组", 10L, "/1/10/101/"));
        depts.put(20L, new Dept(20, "技术中心", 1L, "/1/20/"));
        users.add(new User("u-1", "张三", List.of(100L), "EMPLOYEE", true));
        users.add(new User("u-2", "李四", List.of(100L, 101L), "EMPLOYEE", true));  // 兼职
        users.add(new User("u-3", "王五", List.of(20L), "EMPLOYEE", false));         // 离职
        users.add(new User("svc-1", "订单服务", List.of(), "SYSTEM", true));
    }

    /** 查所有下级部门（用物化路径前缀，避免递归） */
    static List<Long> descendantsOf(long deptId) {
        Dept root = depts.get(deptId);
        List<Long> out = new ArrayList<>();
        for (Dept d : depts.values()) {
            if (d.id() == deptId || d.path().startsWith(root.path())) {
                out.add(d.id());
            }
        }
        return out;
    }

    /** 用户可见部门范围（含兼职） */
    static Set<Long> visibleDepts(String userId, boolean includeChildren) {
        Set<Long> out = new LinkedHashSet<>();
        User u = users.stream().filter(x -> x.id().equals(userId)).findFirst().orElse(null);
        if (u == null || !u.active()) {
            return out;
        }
        for (Long d : u.deptIds()) {
            out.add(d);
            if (includeChildren) {
                out.addAll(descendantsOf(d));
            }
        }
        return out;
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【部门树（物化路径）】");
        depts.values().forEach(d -> System.out.println("  " + d.display()));

        System.out.println();
        System.out.println("【查所有下级（路径前缀）】");
        System.out.println("  事业部 10 的下级：" + descendantsOf(10));
        System.out.println("  技术中心 20 的下级：" + descendantsOf(20));

        System.out.println();
        System.out.println("【用户可见范围】");
        System.out.println("  u-1（订单组，含下级）：" + visibleDepts("u-1", true));
        System.out.println("  u-2（订单组+支付组，兼职）：" + visibleDepts("u-2", true));
        System.out.println("  u-3（离职）：" + visibleDepts("u-3", true));
        System.out.println("  svc-1（系统账号，无部门）：" + visibleDepts("svc-1", true));

        System.out.println();
        System.out.println("【设计要点】");
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("物化路径", "path 存 /1/10/100/，查下级用 LIKE '/1/10/%'，避免递归");
        notes.put("兼职", "用户与部门多对多；权限取并集");
        notes.put("离职", "active=false + 权限回收任务（防僵尸权限 74-10）");
        notes.put("租户", "所有表带 tenantId（55-04）");
        notes.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现部门树（物化路径）与"所有下级"查询，用 5 个部门验证；对比递归查询的复杂度。
 * 2. 实现兼职用户的多部门可见范围（取并集），验证 u-2 能同时看到两个组的数据。
 * 3. 思考题：为什么"权限要跟着组织走"？从人员调岗后"权限残留/权限缺失"两个问题分析。
 * ============================================================
 */
