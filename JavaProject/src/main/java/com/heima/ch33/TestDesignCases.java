/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-03
 * 课名：测试用例设计：等价类、边界值与参数化
 * ------------------------------------------------------------
 * 知识点：
 *   - 测试用例的价值不在于数量，而在于**用最少用例覆盖最多风险**。核心方法：等价类划分、边界值分析、判定表、状态迁移、错误推测。
 *   - **等价类划分**：把输入域切成若干类，每类里任取一个值效果相同。有效等价类（正常输入）+ 无效等价类（非法输入）。**无效等价类必须逐个单独测**，因为一个非法值可能掩盖另一个。
 *   - **边界值分析**：bug 最爱藏在边界。对区间 `[a, b]`，至少测 `a-1, a, a+1, b-1, b, b+1` 六个点。off-by-one 是最高频缺陷。
 *   - **参数化测试**：一份逻辑 + 一张数据表。JUnit 5 用 `@ParameterizedTest` + `@ValueSource`（单值）、`@CsvSource`（多列）、`@MethodSource`（复杂对象）。手写版就是"数据表 + for 循环"。
 *   - **判定表**：多个条件组合时，列出所有条件组合与期望动作。适合"会员等级 × 是否首单 × 是否有券"这类规则叠加。
 *   - **状态迁移测试**：把业务状态机画出来，覆盖"合法迁移"和"非法迁移"两类。非法迁移（如已取消订单再支付）往往比合法路径更容易漏测。
 *   - **错误推测**：基于经验主动想"哪里会错"——空集合、`null`、超长字符串、并发、时区、闰年、金额精度、重复提交。
 *   - 用例设计产出物是**用例表**（输入 / 前置条件 / 期望），先写表再写代码，避免"看着实现写测试"（那样只会覆盖已实现的路径，漏掉没实现的需求）。
 *   - 覆盖强度阶梯：语句覆盖 < 判定覆盖 < 条件组合覆盖 < 路径覆盖。实际项目做到"判定 + 边界"通常足够，关键资金链路再上路径覆盖。
 *   - 易错：只测正常值（happy path）不测异常；用 `Random` 造数据（失败无法复现）；把多个无效等价类塞进一个用例；只测 `[0,100]` 里的 50，不测 -1、0、100、101。
 * ============================================================
 */

package com.heima.ch33;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class TestDesignCases {
    /** 被测：优惠券可用性校验 */
    static class CouponValidator {
        /** @return 不可用原因，可用时返回 "OK" */
        String validate(Integer amountCents, Integer thresholdCents, Boolean firstOrder,
                        Boolean member, String couponType) {
            if (amountCents == null || thresholdCents == null) {
                return "PARAM_NULL";
            }
            if (couponType == null) {
                return "TYPE_UNKNOWN";
            }
            if (amountCents < 0) {
                return "AMOUNT_NEGATIVE";
            }
            if (thresholdCents < 0) {
                return "THRESHOLD_NEGATIVE";
            }
            if (amountCents < thresholdCents) {
                return "BELOW_THRESHOLD";
            }
            switch (couponType) {
                case "CASH":
                    return "OK";
                case "NEW_USER":
                    return Boolean.TRUE.equals(firstOrder) ? "OK" : "NOT_FIRST_ORDER";
                case "MEMBER_ONLY":
                    return Boolean.TRUE.equals(member) ? "OK" : "NOT_MEMBER";
                default:
                    return "TYPE_UNKNOWN";
            }
        }
    }

    /** 迷你参数化：一行数据一个用例 */
    static class Case {
        final String desc;
        final Integer amount;
        final Integer threshold;
        final Boolean firstOrder;
        final Boolean member;
        final String type;
        final String expected;

        Case(String desc, Integer amount, Integer threshold, Boolean firstOrder,
             Boolean member, String type, String expected) {
            this.desc = desc;
            this.amount = amount;
            this.threshold = threshold;
            this.firstOrder = firstOrder;
            this.member = member;
            this.type = type;
            this.expected = expected;
        }
    }

    static int passed;
    static final List<String> failed = new ArrayList<>();

    static void runAll(String group, CouponValidator v, List<Case> cases) {
        System.out.println("  " + group);
        for (Case c : cases) {
            String actual = v.validate(c.amount, c.threshold, c.firstOrder, c.member, c.type);
            boolean ok = c.expected.equals(actual);
            if (ok) {
                passed++;
            } else {
                failed.add(c.desc + " 期望=" + c.expected + " 实际=" + actual);
            }
            System.out.printf("    %-4s %-34s 期望=%-16s 实际=%s%n",
                    ok ? "OK" : "FAIL", c.desc, c.expected, actual);
        }
    }

    static Case c(String desc, Integer amount, Integer threshold, Boolean first, Boolean member,
                  String type, String expected) {
        return new Case(desc, amount, threshold, first, member, type, expected);
    }

    public static void main(String[] args) {
        CouponValidator v = new CouponValidator();

        System.out.println("=== 1. 边界值：门槛 10000 分（100 元）===");
        List<Case> boundary = new ArrayList<>();
        boundary.add(c("9999 分（门槛-1）", 9999, 10000, false, false, "CASH", "BELOW_THRESHOLD"));
        boundary.add(c("10000 分（刚好达标）", 10000, 10000, false, false, "CASH", "OK"));
        boundary.add(c("10001 分（门槛+1）", 10001, 10000, false, false, "CASH", "OK"));
        boundary.add(c("0 分（下边界）", 0, 0, false, false, "CASH", "OK"));
        boundary.add(c("-1 分（下边界-1）", -1, 0, false, false, "CASH", "AMOUNT_NEGATIVE"));
        runAll("边界值用例（6 个点）", v, boundary);

        System.out.println();
        System.out.println("=== 2. 无效等价类：每个非法输入单独一个用例 ===");
        List<Case> invalid = new ArrayList<>();
        invalid.add(c("金额为 null", null, 100, false, false, "CASH", "PARAM_NULL"));
        invalid.add(c("门槛为 null", 100, null, false, false, "CASH", "PARAM_NULL"));
        invalid.add(c("券类型为 null", 100, 100, false, false, null, "TYPE_UNKNOWN"));
        invalid.add(c("券类型未知字符串", 100, 100, false, false, "FOO", "TYPE_UNKNOWN"));
        invalid.add(c("门槛为负", 100, -1, false, false, "CASH", "THRESHOLD_NEGATIVE"));
        invalid.add(c("金额为负", -1, 0, false, false, "CASH", "AMOUNT_NEGATIVE"));
        runAll("无效等价类（6 个独立用例）", v, invalid);

        System.out.println();
        System.out.println("=== 3. 判定表：首单券 × 会员券 的条件组合 ===");
        List<Case> table = new ArrayList<>();
        table.add(c("NEW_USER + 首单=true", 20000, 10000, true, false, "NEW_USER", "OK"));
        table.add(c("NEW_USER + 首单=false", 20000, 10000, false, false, "NEW_USER", "NOT_FIRST_ORDER"));
        table.add(c("NEW_USER + 首单=null", 20000, 10000, null, false, "NEW_USER", "NOT_FIRST_ORDER"));
        table.add(c("MEMBER_ONLY + 会员=true", 20000, 10000, false, true, "MEMBER_ONLY", "OK"));
        table.add(c("MEMBER_ONLY + 会员=false", 20000, 10000, false, false, "MEMBER_ONLY", "NOT_MEMBER"));
        table.add(c("MEMBER_ONLY + 会员=null", 20000, 10000, false, null, "MEMBER_ONLY", "NOT_MEMBER"));
        table.add(c("会员券但金额不达标", 5000, 10000, false, true, "MEMBER_ONLY", "BELOW_THRESHOLD"));
        runAll("判定表用例（条件组合优先于单条件）", v, table);

        System.out.println();
        System.out.println("=== 4. 只测 happy path 会漏掉多少 ===");
        int happyOnly = 1;
        int total = boundary.size() + invalid.size() + table.size();
        System.out.println("  只测 1 个正常用例 -> 覆盖 1 条路径");
        System.out.println("  按本课方法设计   -> " + total + " 个用例，覆盖 "
                + (total + 1) + " 条路径（含 OK 分支）");
        System.out.println("  漏测成本：金额为负、门槛为负、null 入参、类型未知、"
                + "首单/会员为 null 全部不会被发现");

        System.out.println();
        System.out.println("=== 5. 用例设计检查清单 ===");
        String[] checklist = {
                "每个区间是否测了 a-1 / a / a+1 / b-1 / b / b+1",
                "每个引用类型是否测了 null",
                "集合是否测了空集合与单元素",
                "字符串是否测了空串、超长、特殊字符",
                "金额是否用整数分而不是 double",
                "枚举/字符串类型是否测了未知值",
                "布尔条件是否测了 null（Boolean 而非 boolean）",
                "非法迁移（已取消再支付）是否覆盖",
                "并发与重复提交是否单独验证",
                "每条用例失败信息能否自解释"
        };
        for (int i = 0; i < checklist.length; i++) {
            System.out.printf("  %2d. %s%n", i + 1, checklist[i]);
        }

        System.out.println();
        System.out.println("=== 结果 ===");
        System.out.println("  通过 " + passed + "，失败 " + failed.size());
        for (String f : failed) {
            System.out.println("    " + f);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为 `CouponValidator` 补充"满减券阶梯门槛"（如 100-10、200-30），先用判定表列用例再写代码。
 * 2. 找出 `validate` 里一个边界值方法测不出、但路径覆盖能测出的分支，说明原因。
 * 3. 把 `amountCents` 改成 `double amount`，指出会产生什么类型的测试问题，并说明为什么金额必须用整数分。
 * ============================================================
 */
