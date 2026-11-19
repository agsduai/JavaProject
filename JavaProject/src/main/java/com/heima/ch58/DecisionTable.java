/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：58-04
 * 课名：决策表与规则矩阵
 * ------------------------------------------------------------
 * 知识点：
 *   - **决策表（Decision Table）是"业务方最易读"的规则形式**：行=规则，列=条件与动作（Excel 风格）——**业务方能在表格里直接看到"什么情况下做什么"**，评审效率远高于表达式。
 *   - **条件列的写法**：精确值（`=100`）、范围（`100-500`）、通配（`*` 任意/`-` 不关心）、枚举集合（`wechat|alipay`）——**通配符语义要严格定义**（"不关心"与"必须为空"是两回事）。
 *   - **决策表的完整性检查**：**规则覆盖是否完整**（所有条件组合都有命中）、**是否有矛盾行**（同一组合多个动作）、**是否有不可达行**（被前序行遮蔽）——**这三项检查是决策表上线的门禁**（可自动化）。
 *   - **行序即优先级**（first-match）：决策表从上到下匹配，第一个命中生效——**行序是业务语义的一部分**，调整行序 = 改变规则（需要版本与评审）。
 *   - **决策表到执行引擎的编译**：表格解析 -> 规则对象列表 -> 求值（58-03）；**双向**：人工维护表格，系统渲染成可视化编辑器（业务方在 UI 上改，存储仍是结构化数据）。
 *   - 易错点：通配符语义不清（`*` 被理解成"必须为空"）；行序调整无版本记录（隐性变更）；不检查覆盖完整（漏配组合静默放行）；决策表无责任人（改了不知道找谁）。
 * ============================================================
 */

package com.heima.ch58;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class DecisionTable {
    /** 一行决策表：条件用 "范围|通配*|集合a,b"，动作为决策 */
    record Row(String ruleId, String amountRange, String channel, String decision) {
    }

    record Fact(long amountCents, String channel) {
    }

    static boolean condMatch(String cond, String value) {
        String c = cond.strip();
        if (c.equals("*")) {
            return true;                              // 不关心
        }
        if (c.contains("-")) {
            String[] p = c.split("-");
            long v = Long.parseLong(value);
            return v >= Long.parseLong(p[0]) && v <= Long.parseLong(p[1]);
        }
        if (c.contains(",")) {
            return List.of(c.split(",")).contains(value);
        }
        return c.equals(value);
    }

    static boolean condMatchNum(String cond, long value) {
        String c = cond.strip();
        if (c.equals("*")) {
            return true;
        }
        if (c.contains("-")) {
            String[] p = c.split("-");
            return value >= Long.parseLong(p[0]) && value <= Long.parseLong(p[1]);
        }
        return Long.parseLong(c) == value;
    }

    /** first-match：自上而下，第一个命中生效 */
    static String decide(List<Row> table, Fact fact) {
        for (Row r : table) {
            if (condMatchNum(r.amountRange(), fact.amountCents())
                    && condMatch(r.channel(), fact.channel())) {
                return r.ruleId() + " -> " + r.decision();
            }
        }
        return "NO_MATCH（默认放行）";
    }

    /** 覆盖性检查：抽样枚举条件组合，找出无命中的组合 */
    static List<String> coverageGaps(List<Row> table) {
        List<String> gaps = new ArrayList<>();
        long[] amounts = {0, 5000, 50000, 500000};
        String[] channels = {"wechat", "alipay", "bank"};
        for (long a : amounts) {
            for (String ch : channels) {
                boolean hit = table.stream().anyMatch(r -> condMatchNum(r.amountRange(), a)
                        && condMatch(r.channel(), ch));
                if (!hit) {
                    gaps.add("amount=" + a + ",channel=" + ch);
                }
            }
        }
        return gaps;
    }

    public static void main(String[] args) {
        List<Row> table = List.of(
                new Row("T-01", "100000-999999", "*", "CHALLENGE"),
                new Row("T-02", "0-99999", "wechat,alipay", "ALLOW"),
                new Row("T-03", "0-99999", "bank", "LOG"),
                new Row("T-04", "*", "*", "BLOCK"));
        System.out.println("【决策表执行（first-match）】");
        System.out.println("  20 万 wechat -> " + decide(table, new Fact(200_000, "wechat")));
        System.out.println("  5 万 bank    -> " + decide(table, new Fact(50_000, "bank")));
        System.out.println("  巨大金额     -> " + decide(table, new Fact(9_000_000, "alipay")));
        System.out.println();
        System.out.println("【覆盖性检查】缺口=" + coverageGaps(table)
                + "（T-04 兜底后应无缺口）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整决策表引擎：范围/集合/通配/精确四类条件 + first-match + 覆盖与矛盾检查（给出可自动化的检查函数）。
 * 2. 设计一张支付渠道费率决策表（金额档 × 渠道 = 费率），用手写矩阵验证引擎结果与人工计算一致。
 * 3. 思考题：为什么"行序"必须视为业务语义？构造一个"调整行序导致高危规则被低危规则遮蔽"的用例。
 * ============================================================
 */
