/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：71-08
 * 课名：多租户与扩展
 * ------------------------------------------------------------
 * 知识点：
 *   - **低代码平台天然多租户**（55 章）：每个租户有自己的表单/字段/流程/权限——**元数据要带 tenantId**，租户间完全隔离（55-04 双键校验）。
 *   - **租户定制的两种模式**：**独立配置**（租户各改各的，平台提供基础模板）与**继承覆盖**（租户继承平台标准表单，只覆盖差异字段）——**继承模式减少重复配置**（55-06 配置分层思想）。
 *   - **平台升级与租户定制的冲突**：平台发布新版本表单，租户已定制过——**用"差异合并"**（平台改基础，租户覆盖保留）或**"版本并存"**（租户继续用旧版）——**这是多租户低代码最难的工程问题**。
 *   - **扩展点隔离**：租户自定义脚本/组件在**沙箱**中执行（资源限制 + 权限限制 66-10 思想）——**一个租户的脚本不能拖垮平台**。
 *   - 与 55-08 的关系：低代码平台的"噪声邻居"是"某租户配了超大表单/复杂规则"——**要用配额与超时保护**（71-09）。
 *   - 易错点：元数据不带 tenantId（跨租户串数据）；租户定制直接改平台模板（升级即冲突）；无继承机制（每个租户全量配置，维护地狱）；扩展开放无沙箱（安全风险）。
 * ============================================================
 */

package com.heima.ch71;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TenantExtension {
    /** 表单定义：平台基础 + 租户覆盖 */
    record FormDef(String tenantId, String formId, List<String> baseFields,
                   List<String> extraFields, List<String> removedFields) {
        /** 合并结果：基础 - 删除 + 扩展 */
        List<String> effectiveFields() {
            List<String> out = new ArrayList<>();
            for (String f : baseFields) {
                if (!removedFields.contains(f)) {
                    out.add(f);
                }
            }
            out.addAll(extraFields);
            return out;
        }
    }

    static final List<FormDef> forms = new ArrayList<>();

    static void define(String tenantId, String formId, List<String> base,
                       List<String> extra, List<String> removed) {
        forms.add(new FormDef(tenantId, formId, base, extra, removed));
        System.out.printf("  [%s] %s：基础=%d 扩展=%s 删除=%s%n",
                tenantId, formId, base.size(), extra, removed);
    }

    static List<String> resolve(String tenantId, String formId) {
        return forms.stream()
                .filter(f -> f.tenantId().equals(tenantId) && f.formId().equals(formId))
                .findFirst().map(FormDef::effectiveFields).orElse(List.of());
    }

    /** 平台升级：基础字段变化，检查租户定制的影响 */
    static void upgradeImpact(String formId, List<String> newBase) {
        System.out.println("  平台升级 " + formId + " 基础字段 -> " + newBase);
        for (FormDef f : forms) {
            if (!f.formId().equals(formId)) {
                continue;
            }
            List<String> before = f.effectiveFields();
            FormDef upgraded = new FormDef(f.tenantId(), f.formId(), newBase,
                    f.extraFields(), f.removedFields());
            List<String> after = upgraded.effectiveFields();
            List<String> removedByBase = new ArrayList<>(before);
            removedByBase.removeAll(after);
            List<String> added = new ArrayList<>(after);
            added.removeAll(before);
            System.out.printf("    租户 %-6s 变化：新增=%s 消失=%s%n",
                    f.tenantId(), added, removedByBase);
            if (!removedByBase.isEmpty()) {
                System.out.println("      警告：升级会移除租户可见字段 -> 需人工确认");
            }
        }
        System.out.println();
    }

    public static void main(String[] args) {
        List<String> base = List.of("title", "amount", "apply_date", "remark");
        System.out.println("【租户表单定义（继承 + 覆盖）】");
        define("t-100", "expense", base, List.of("cost_center"), List.of("remark"));
        define("t-200", "expense", base, List.of(), List.of());
        define("t-300", "expense", base, List.of("project_code", "budget_year"),
                List.of("apply_date"));

        System.out.println();
        System.out.println("【各租户生效字段】");
        for (String t : new String[]{"t-100", "t-200", "t-300"}) {
            System.out.printf("  %-6s -> %s%n", t, resolve(t, "expense"));
        }

        System.out.println();
        System.out.println("【平台升级影响分析】");
        upgradeImpact("expense", List.of("title", "amount", "apply_date",
                "cost_center", "remark"));
        System.out.println("  策略：差异合并（保留租户扩展/删除意图）+ 破坏性变更人工确认");
        System.out.println();
        System.out.println("【隔离要求】元数据带 tenantId + 查询双键（55-04）+ 脚本沙箱（66-10）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"继承 + 覆盖"的租户表单解析（基础 - 删除 + 扩展），用 3 个租户验证生效字段。
 * 2. 实现平台升级影响分析：列出每个租户"新增字段/消失字段"，对消失字段给出确认提示。
 * 3. 思考题：为什么"租户直接改平台模板"会导致升级冲突？从"无差异记录"与"覆盖不可逆"分析。
 * ============================================================
 */
