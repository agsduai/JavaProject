/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：71-04
 * 课名：元数据驱动的权限
 * ------------------------------------------------------------
 * 知识点：
 *   - **低代码的权限必须"配在元数据上"，而不是写死在代码里**：字段级（谁能看/改哪个字段）、行级（谁能看哪些数据）、操作级（谁能提交/审批/删除）——**权限规则作为元数据的一部分，随表单/实体一起版本化**。
 *   - **三层的实现对应**：**字段级**（渲染时过滤 + 后端脱敏 68-03）、**行级**（查询时注入数据范围 68-05）、**操作级**（按钮显隐 + 后端校验）——**前端只管显隐，后端必须强校验**（71-03 同款铁律）。
 *   - **权限与流程的联动**：审批节点的"当前处理人"能看到字段，其他人看不到——**权限是动态的（依赖角色 + 流程状态）**，比静态 RBAC 复杂（68-05 的 ABAC 场景）。
 *   - **配置权限本身要有权限**：谁能改表单定义、谁能改权限矩阵——**平台管理员与业务管理员分离**（51-07 最小权限）。
 *   - **与 55/68 章的关系**：多租户 SaaS 的租户隔离（55-04）+ 数据权限（68-05）——**低代码平台的权限是两者的组合**：租户内按角色 + 字段脱敏。
 *   - 易错点：权限只做按钮显隐（接口裸奔）；字段权限只在前端过滤（接口返回全量）；流程状态与权限不联动（离职人员还能审批）；改权限无审计（68-09）。
 * ============================================================
 */

package com.heima.ch71;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MetadataPermission {
    /** 字段权限规则：角色 + 可见/可编辑 + 条件 */
    record FieldRule(String field, String role, boolean readable, boolean writable,
                     String condition) {
    }

    /** 行级规则：数据范围 */
    record RowRule(String role, String scope) {
    }

    static final List<FieldRule> fieldRules = new ArrayList<>();
    static final List<RowRule> rowRules = new ArrayList<>();

    static {
        fieldRules.add(new FieldRule("title", "*", true, true, null));
        fieldRules.add(new FieldRule("amount", "employee", true, true, "status=DRAFT"));
        fieldRules.add(new FieldRule("amount", "manager", true, false, null));
        fieldRules.add(new FieldRule("amount", "finance", true, false, null));
        fieldRules.add(new FieldRule("bank_account", "employee", true, true, "status=DRAFT"));
        fieldRules.add(new FieldRule("bank_account", "manager", false, false, null));
        fieldRules.add(new FieldRule("bank_account", "finance", true, false, null));
        fieldRules.add(new FieldRule("remark", "manager", true, true, null));

        rowRules.add(new RowRule("employee", "OWNER"));
        rowRules.add(new RowRule("manager", "DEPT"));
        rowRules.add(new RowRule("finance", "ALL"));
    }

    /** 字段可见性：角色匹配 + 条件满足 */
    static boolean canRead(String field, String role, Map<String, String> ctx) {
        for (FieldRule r : fieldRules) {
            if (!r.field().equals(field)) {
                continue;
            }
            if (!r.role().equals("*") && !r.role().equals(role)) {
                continue;
            }
            if (r.condition() != null) {
                String[] p = r.condition().split("=");
                if (!p[1].equals(ctx.getOrDefault(p[0].trim(), ""))) {
                    continue;
                }
            }
            return r.readable();
        }
        return false;
    }

    static boolean canWrite(String field, String role, Map<String, String> ctx) {
        for (FieldRule r : fieldRules) {
            if (!r.field().equals(field)) {
                continue;
            }
            if (!r.role().equals("*") && !r.role().equals(role)) {
                continue;
            }
            if (r.condition() != null) {
                String[] p = r.condition().split("=");
                if (!p[1].equals(ctx.getOrDefault(p[0].trim(), ""))) {
                    continue;
                }
            }
            return r.writable();
        }
        return false;
    }

    /** 行级过滤 */
    static List<String> visibleRows(String role, List<String> rows) {
        String scope = rowRules.stream().filter(r -> r.role().equals(role))
                .map(RowRule::scope).findFirst().orElse("NONE");
        return switch (scope) {
            case "ALL" -> rows;
            case "DEPT" -> rows.stream().filter(r -> r.startsWith("dept:")).toList();
            case "OWNER" -> rows.stream().filter(r -> r.startsWith("owner:")).toList();
            default -> List.of();
        };
    }

    /** 渲染字段列表（可见字段） */
    static List<String> renderFields(List<String> allFields, String role,
                                     Map<String, String> ctx) {
        List<String> out = new ArrayList<>();
        for (String f : allFields) {
            if (canRead(f, role, ctx)) {
                out.add(f);
            }
        }
        return out;
    }

    public static void main(String[] args) {
        List<String> allFields = List.of("title", "amount", "bank_account", "remark");
        Map<String, String> draft = new LinkedHashMap<>();
        draft.put("status", "DRAFT");
        Map<String, String> approved = new LinkedHashMap<>();
        approved.put("status", "APPROVED");

        System.out.println("【字段级权限：草稿态】");
        for (String role : new String[]{"employee", "manager", "finance"}) {
            System.out.printf("  %-8s 可见=%-30s 可编辑=%s%n", role,
                    renderFields(allFields, role, draft),
                    allFields.stream().filter(f -> canWrite(f, role, draft)).toList());
        }

        System.out.println();
        System.out.println("【字段级权限：审批后（employee 不能再改金额）】");
        System.out.printf("  employee 可编辑=%s%n",
                allFields.stream().filter(f -> canWrite(f, "employee", approved)).toList());

        System.out.println();
        System.out.println("【行级权限】");
        List<String> rows = List.of("owner:o-1", "owner:o-2", "dept:d-1", "dept:d-2",
                "other:x-1");
        for (String role : new String[]{"employee", "manager", "finance"}) {
            System.out.printf("  %-8s -> %s%n", role, visibleRows(role, rows));
        }

        System.out.println();
        System.out.println("  铁律：前端按元数据显隐，后端按同一份元数据强校验（68-05）");
        System.out.println("  平台管理员 vs 业务管理员分离；权限变更进审计（68-09）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现字段级 + 行级权限引擎，用 3 个角色 × 2 个流程状态验证可见性与可编辑性。
 * 2. 增加"流程节点权限"（只有当前审批人可写审批意见），验证状态驱动的动态权限。
 * 3. 思考题：为什么"低代码平台的权限配置本身也要权限"？从"改配置 = 改所有租户行为"分析。
 * ============================================================
 */
