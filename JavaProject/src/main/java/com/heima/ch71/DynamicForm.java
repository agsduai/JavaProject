/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：71-03
 * 课名：动态表单渲染
 * ------------------------------------------------------------
 * 知识点：
 *   - **动态表单 = 元数据 -> UI 描述 -> 渲染**：后端返回表单定义（字段 + 布局 + 校验 + 联动），前端按定义渲染组件——**一份定义驱动 Web/App/小程序多端**（70-08 的多端一致思想）。
 *   - **表单定义的三层**：**字段层**（类型/校验/默认值）、**布局层**（分组/栅格/顺序）、**联动层**（A 变化影响 B 的显隐/可选值/必填）——**联动是最复杂的一层**（声明式 vs 脚本）。
 *   - **校验的两处执行**：**前端即时校验**（体验）+ **后端强制校验**（安全，36 章）——**前端校验永远不可信**，元数据里的规则必须后端再跑一遍。
 *   - **联动的实现方式**：**声明式规则**（`when category == TRAVEL then show hotel_field`——可控、可校验，推荐）、**脚本钩子**（JS/Groovy 表达式——灵活但难治理，71-09）。
 *   - **与 58 章的关系**：表单联动就是"小规则引擎"——**用规则引擎的模型（条件-动作）描述联动**，避免自己发明一套语法。
 *   - 易错点：只在前端校验（绕过）；联动写死在组件里（改配置要发版）；表单定义无版本（已提交数据的展示错乱）；字段类型与组件映射硬编码（新增类型要改多处）。
 * ============================================================
 */

package com.heima.ch71;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DynamicForm {
    record FormField(String name, String label, String type, boolean required,
                     String visibleWhen, String optionsFrom) {
    }

    record FormDef(String id, String title, List<FormField> fields) {
    }

    /** 联动求值：visibleWhen 形如 "category=TRAVEL" */
    static boolean visible(FormField f, Map<String, String> values) {
        if (f.visibleWhen() == null || f.visibleWhen().isBlank()) {
            return true;
        }
        String[] parts = f.visibleWhen().split("=");
        return parts[1].equals(values.getOrDefault(parts[0].trim(), ""));
    }

    /** 校验：必填 + 类型 + 条件必填 */
    static List<String> validate(FormDef def, Map<String, String> values) {
        List<String> errors = new ArrayList<>();
        for (FormField f : def.fields()) {
            boolean shouldShow = visible(f, values);
            if (!shouldShow) {
                continue;
            }
            String v = values.get(f.name());
            if (f.required() && (v == null || v.isBlank())) {
                errors.add(f.label() + "：必填");
                continue;
            }
            if (v == null || v.isBlank()) {
                continue;
            }
            if (f.type().equals("number") && !v.matches("-?\\d+(\\.\\d+)?")) {
                errors.add(f.label() + "：必须是数字");
            }
            if (f.type().equals("date") && !v.matches("\\d{4}-\\d{2}-\\d{2}")) {
                errors.add(f.label() + "：日期格式应为 yyyy-MM-dd");
            }
        }
        return errors;
    }

    /** 渲染：字段类型 -> 组件（声明式映射） */
    static String component(String type) {
        return switch (type) {
            case "string" -> "Input";
            case "number" -> "NumberInput";
            case "date" -> "DatePicker";
            case "enum" -> "Select";
            case "text" -> "TextArea";
            case "bool" -> "Switch";
            default -> "Input";
        };
    }

    public static void main(String[] args) {
        FormDef def = new FormDef("f-expense", "报销申请", List.of(
                new FormField("title", "标题", "string", true, null, null),
                new FormField("category", "类别", "enum", true, null, "dict.category"),
                new FormField("amount", "金额", "number", true, null, null),
                new FormField("apply_date", "申请日期", "date", true, null, null),
                new FormField("hotel_city", "出差城市", "string", true,
                        "category=TRAVEL", null),
                new FormField("meal_count", "餐补天数", "number", true,
                        "category=MEAL", null),
                new FormField("remark", "备注", "text", false, null, null)));

        System.out.println("【表单定义】" + def.title());
        def.fields().forEach(f -> System.out.printf(
                "  %-12s %-6s 组件=%-12s 必填=%-5s 条件=%s%n",
                f.name(), f.label(), component(f.type()), f.required(),
                f.visibleWhen() == null ? "始终显示" : f.visibleWhen()));

        System.out.println();
        System.out.println("【场景一：类别=TRAVEL，缺字段】");
        Map<String, String> v1 = new LinkedHashMap<>();
        v1.put("title", "杭州出差");
        v1.put("category", "TRAVEL");
        v1.put("amount", "1200.50");
        v1.put("apply_date", "2026-11-18");
        validate(def, v1).forEach(e -> System.out.println("  错误：" + e));

        System.out.println();
        System.out.println("【场景二：补全后通过】");
        v1.put("hotel_city", "杭州");
        List<String> errs = validate(def, v1);
        System.out.println("  校验结果：" + (errs.isEmpty() ? "通过" : errs));

        System.out.println();
        System.out.println("【场景三：类别=MEAL，出差城市不显示】");
        Map<String, String> v2 = new LinkedHashMap<>(v1);
        v2.put("category", "MEAL");
        v2.remove("hotel_city");
        System.out.println("  校验结果：" + (validate(def, v2).isEmpty() ? "通过"
                : validate(def, v2)));

        System.out.println();
        System.out.println("  铁律：前端校验是体验，后端必须按同一份元数据再校验一遍（36 章）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现动态表单引擎：字段类型映射组件 + 条件显示 + 校验，用 3 个场景验证。
 * 2. 把"条件联动"扩展成"多条件组合"（如 `category=TRAVEL AND amount>500`），说明为何要复用规则引擎模型（58 章）。
 * 3. 思考题：为什么"只在前端校验"是安全漏洞？从前端可绕过（curl 直接调接口）分析。
 * ============================================================
 */
