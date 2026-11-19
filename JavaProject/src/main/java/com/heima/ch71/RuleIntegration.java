/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：71-05
 * 课名：规则引擎与低代码联动
 * ------------------------------------------------------------
 * 知识点：
 *   - **低代码的"业务逻辑"大多可以表达成规则**：金额校验（>5000 需总监审批）、字段联动（选了差旅才显示酒店）、自动计算（合计 = 明细求和）——**把这些交给规则引擎（58 章），低代码平台专注 UI 与流程**。
 *   - **规则的三类用途**：**校验规则**（提交时校验）、**计算规则**（字段自动计算）、**路由规则**（流程走向）——**统一用"条件-动作"模型**，避免每类各写一套。
 *   - **规则与元数据的绑定**：规则挂在"实体 + 字段 + 触发时机（beforeSave/afterLoad/onSubmit）"上——**规则也是元数据的一部分**（71-04 权限同款设计）。
 *   - **避免"配置地狱"**：规则数量增长后要能**分组/优先级/冲突检测**（58-05）——**低代码平台要把规则治理能力内建**，否则配置越堆越乱。
 *   - **与 58 章的差异**：58 章是独立风控引擎（高吞吐、低延迟）；低代码的规则是"配置驱动的轻量引擎"（低频、可调试）——**同一模型，不同量级**。
 *   - 易错点：规则堆在表单事件里（散落无治理）；计算规则用脚本（不可校验）；规则无优先级（冲突时结果随机）；规则变更不测试（配置错影响全租户）。
 * ============================================================
 */

package com.heima.ch71;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RuleIntegration {
    enum Trigger {
        BEFORE_SAVE, AFTER_LOAD, ON_SUBMIT
    }

    record RuleDef(String id, String entity, Trigger trigger, String when,
                   String action, int priority) {
    }

    static final List<RuleDef> rules = new ArrayList<>();

    static {
        rules.add(new RuleDef("R-1", "expense", Trigger.ON_SUBMIT,
                "amount>5000", "requireDirectorApproval", 100));
        rules.add(new RuleDef("R-2", "expense", Trigger.BEFORE_SAVE,
                "category=TRAVEL && hotelCity=null", "setError(hotelCity, 必填)", 100));
        rules.add(new RuleDef("R-3", "expense", Trigger.AFTER_LOAD,
                "always", "compute(total = sum(items))", 10));
        rules.add(new RuleDef("R-4", "expense", Trigger.ON_SUBMIT,
                "amount<=0", "setError(amount, 必须大于0)", 100));
    }

    /** 极简条件求值：支持 k>n 与 k=v 与 always */
    static boolean match(String when, Map<String, String> data) {
        if (when.equals("always")) {
            return true;
        }
        if (when.contains("&&")) {
            for (String part : when.split("&&")) {
                if (!match(part.trim(), data)) {
                    return false;
                }
            }
            return true;
        }
        if (when.contains(">")) {
            String[] p = when.split(">");
            try {
                double v = Double.parseDouble(data.getOrDefault(p[0].trim(), "0"));
                return v > Double.parseDouble(p[1].trim());
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (when.contains("<=")) {
            String[] p = when.split("<=");
            try {
                double v = Double.parseDouble(data.getOrDefault(p[0].trim(), "0"));
                return v <= Double.parseDouble(p[1].trim());
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (when.contains("=")) {
            String[] p = when.split("=");
            String actual = data.get(p[0].trim());
            return p[1].trim().equals(actual == null || actual.isEmpty() ? "null" : actual);
        }
        return false;
    }

    /** 执行某触发时机的规则（按优先级降序） */
    static List<String> fire(String entity, Trigger trigger, Map<String, String> data) {
        List<RuleDef> matched = new ArrayList<>();
        for (RuleDef r : rules) {
            if (r.entity().equals(entity) && r.trigger() == trigger && match(r.when(), data)) {
                matched.add(r);
            }
        }
        matched.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        List<String> actions = new ArrayList<>();
        for (RuleDef r : matched) {
            actions.add(r.id() + " -> " + r.action());
        }
        return actions;
    }

    public static void main(String[] args) {
        System.out.println("【规则配置（挂在实体+触发时机上）】");
        rules.forEach(r -> System.out.printf("  %-4s %-8s %-12s when=%-30s -> %s%n",
                r.id(), r.entity(), r.trigger(), r.when(), r.action()));

        Map<String, String> data = new LinkedHashMap<>();
        data.put("amount", "8000");
        data.put("category", "TRAVEL");
        data.put("hotelCity", "");

        System.out.println();
        System.out.println("【BEFORE_SAVE】");
        fire("expense", Trigger.BEFORE_SAVE, data).forEach(a -> System.out.println("  " + a));

        System.out.println();
        System.out.println("【ON_SUBMIT】");
        fire("expense", Trigger.ON_SUBMIT, data).forEach(a -> System.out.println("  " + a));

        System.out.println();
        System.out.println("【AFTER_LOAD（计算规则）】");
        fire("expense", Trigger.AFTER_LOAD, data).forEach(a -> System.out.println("  " + a));

        System.out.println();
        System.out.println("  规则治理：优先级 + 冲突检测 + 变更测试（58-05/58-09）");
        System.out.println("  计算规则用声明式而非脚本，才能被引擎校验与调试");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现规则引擎联动：三类触发（保存前/加载后/提交时）+ 优先级执行，用 4 条规则验证。
 * 2. 增加"规则冲突检测"：同一字段被两条规则设置不同值时报错，说明为何需要优先级与冲突检查。
 * 3. 思考题：为什么"计算规则不用脚本"？从"可校验、可调试、可跨端执行"三点分析。
 * ============================================================
 */
