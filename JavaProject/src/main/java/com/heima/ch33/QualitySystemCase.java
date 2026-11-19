/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-11
 * 课名：综合案例：为订单模块搭建质量保障体系
 * ------------------------------------------------------------
 * 知识点：
 *   - 质量保障体系由四层构成：**规范层**（编码规范、异常契约）、**测试层**（单元/集成/E2E 金字塔）、**门禁层**（静态检查 + 覆盖率 + CI 阻断）、**观测层**（线上监控、日志、告警）。缺一层就有盲区。
 *   - 落地顺序：**先有测试，再有门禁**。没有测试就设覆盖率门禁，只会逼团队写假测试。
 *   - 关键业务（资金、库存、权限）的保障标准要**高于**普通业务：分支覆盖 ≥ 90%、必须有并发测试、必须有对账/幂等验证。
 *   - **回归清单**：每个线上 bug 修复后必须补一个能复现它的测试（回归测试），否则同类问题必然复发。
 *   - **缺陷分析**：把线上 bug 按"哪个环节本该拦住"归类（单测漏了/集成漏了/门禁没设/规范没写），归因结果直接变成下一轮改进项。
 *   - **质量度量**要少而准：缺陷逃逸率（线上 bug / 总 bug）、平均修复时间、CI 通过率、新增代码覆盖率。指标过多会失真。
 *   - 综合案例的结构：领域模型 → 业务规则 → 测试金字塔（单测覆盖规则、集成覆盖协作、E2E 覆盖链路）→ 门禁判定 → 缺陷归因。
 *   - 易错：只做单测不做集成（SQL 映射错误测不出）；只做 E2E 不做单测（失败无法定位）；门禁设了但没人看告警；线上 bug 修了不补测试。
 * ============================================================
 */

package com.heima.ch33;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class QualitySystemCase {
    /** ---------- 领域模型 ---------- */
    static class Money {
        final long cents;

        Money(long cents) {
            this.cents = cents;
        }

        Money plus(Money o) {
            return new Money(Math.addExact(cents, o.cents));
        }

        Money minus(Money o) {
            return new Money(cents - o.cents);
        }

        Money percent(int p) {
            return new Money(cents * p / 100);
        }

        boolean gte(Money o) {
            return cents >= o.cents;
        }

        @Override
        public String toString() {
            return String.format("%d.%02d", cents / 100, cents % 100);
        }
    }

    enum OrderStatus {
        CREATED, PAID, SHIPPED, CANCELLED
    }

    static class Order {
        final String no;
        final Money amount;
        final Money paid;
        OrderStatus status;

        Order(String no, Money amount) {
            this.no = no;
            this.amount = amount;
            this.paid = new Money(0);
            this.status = OrderStatus.CREATED;
        }
    }

    /** ---------- 业务规则（被测核心）---------- */
    static class OrderDomain {
        /** 支付：只有 CREATED 可支付，金额必须完全匹配 */
        void pay(Order o, Money paid) {
            if (o.status != OrderStatus.CREATED) {
                throw new IllegalStateException("状态不允许支付: " + o.status);
            }
            if (!paid.gte(o.amount)) {
                throw new IllegalArgumentException(
                        "支付金额不足，应付 " + o.amount + "，实付 " + paid);
            }
            if (paid.cents > o.amount.cents) {
                throw new IllegalArgumentException("支付金额超额，应付 " + o.amount + "，实付 " + paid);
            }
            o.status = OrderStatus.PAID;
        }

        /** 取消：PAID 需退款，已发货不可取消 */
        Money cancel(Order o) {
            if (o.status == OrderStatus.SHIPPED) {
                throw new IllegalStateException("已发货不可取消");
            }
            if (o.status == OrderStatus.CANCELLED) {
                throw new IllegalStateException("订单已取消");
            }
            Money refund = o.status == OrderStatus.PAID ? o.amount : new Money(0);
            o.status = OrderStatus.CANCELLED;
            return refund;
        }

        /** 发货：只有 PAID 可发货 */
        void ship(Order o) {
            if (o.status != OrderStatus.PAID) {
                throw new IllegalStateException("只有已支付订单可发货，当前 " + o.status);
            }
            o.status = OrderStatus.SHIPPED;
        }
    }

    /** ---------- 测试框架（极简）---------- */
    static int passed;
    static int failed;
    static final List<String> failureDetails = new ArrayList<>();
    static final Map<String, Integer> layerCount = new LinkedHashMap<>();

    static void caseOf(String layer, String name, Runnable body) {
        try {
            body.run();
            passed++;
            layerCount.merge(layer, 1, Integer::sum);
            System.out.printf("    [%-5s] %-48s OK%n", layer, name);
        } catch (Throwable t) {
            failed++;
            layerCount.merge(layer, 1, Integer::sum);
            String d = layer + " / " + name + " -> " + t.getMessage();
            failureDetails.add(d);
            System.out.printf("    [%-5s] %-48s FAIL: %s%n", layer, name, t.getMessage());
        }
    }

    static void eq(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("期望 " + expected + "，实际 " + actual);
        }
    }

    static void throwsEx(Class<? extends Throwable> type, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return;
            }
            throw new AssertionError("期望 " + type.getSimpleName() + "，实际 " + t.getClass().getSimpleName());
        }
        throw new AssertionError("期望抛 " + type.getSimpleName() + "，但没抛");
    }

    /** ---------- 覆盖统计（模拟探针）---------- */
    static class Coverage {
        static final Map<String, Boolean> BRANCHES = new LinkedHashMap<>();

        static void mark(String b) {
            BRANCHES.put(b, true);
        }

        static void declare(String b) {
            BRANCHES.putIfAbsent(b, false);
        }

        static int percent() {
            long hit = BRANCHES.values().stream().filter(Boolean::booleanValue).count();
            return BRANCHES.isEmpty() ? 100 : (int) (hit * 100 / BRANCHES.size());
        }

        static List<String> missed() {
            List<String> list = new ArrayList<>();
            BRANCHES.forEach((k, v) -> {
                if (!v) {
                    list.add(k);
                }
            });
            return list;
        }
    }

    public static void main(String[] args) {
        OrderDomain domain = new OrderDomain();
        Coverage.declare("pay:CREATED");
        Coverage.declare("pay:非CREATED");
        Coverage.declare("pay:金额不足");
        Coverage.declare("pay:金额超额");
        Coverage.declare("cancel:SHIPPED");
        Coverage.declare("cancel:已取消");
        Coverage.declare("cancel:PAID退款");
        Coverage.declare("cancel:CREATED无退款");
        Coverage.declare("ship:PAID");
        Coverage.declare("ship:非PAID");

        System.out.println("=== 第一层：单元测试（业务规则）===");

        caseOf("UNIT", "CREATED 全额支付成功", () -> {
            Order o = new Order("O1", new Money(9_900));
            domain.pay(o, new Money(9_900));
            Coverage.mark("pay:CREATED");
            eq(OrderStatus.PAID, o.status);
        });

        caseOf("UNIT", "已支付订单再支付抛 IllegalState", () -> {
            Order o = new Order("O2", new Money(100));
            domain.pay(o, new Money(100));
            Coverage.mark("pay:CREATED");
            throwsEx(IllegalStateException.class, () -> domain.pay(o, new Money(100)));
            Coverage.mark("pay:非CREATED");
        });

        caseOf("UNIT", "支付金额不足抛 IllegalArgument", () -> {
            Order o = new Order("O3", new Money(10_000));
            throwsEx(IllegalArgumentException.class, () -> domain.pay(o, new Money(9_999)));
            Coverage.mark("pay:金额不足");
            eq(OrderStatus.CREATED, o.status);
        });

        caseOf("UNIT", "支付金额超额抛 IllegalArgument", () -> {
            Order o = new Order("O4", new Money(10_000));
            throwsEx(IllegalArgumentException.class, () -> domain.pay(o, new Money(10_001)));
            Coverage.mark("pay:金额超额");
            eq(OrderStatus.CREATED, o.status);
        });

        caseOf("UNIT", "已发货订单不可取消", () -> {
            Order o = new Order("O5", new Money(500));
            domain.pay(o, new Money(500));
            Coverage.mark("pay:CREATED");
            domain.ship(o);
            Coverage.mark("ship:PAID");
            throwsEx(IllegalStateException.class, () -> domain.cancel(o));
            Coverage.mark("cancel:SHIPPED");
        });

        caseOf("UNIT", "重复取消抛 IllegalState", () -> {
            Order o = new Order("O6", new Money(500));
            domain.cancel(o);
            Coverage.mark("cancel:CREATED无退款");
            throwsEx(IllegalStateException.class, () -> domain.cancel(o));
            Coverage.mark("cancel:已取消");
        });

        caseOf("UNIT", "已支付订单取消返回全额退款", () -> {
            Order o = new Order("O7", new Money(12_345));
            domain.pay(o, new Money(12_345));
            Coverage.mark("pay:CREATED");
            Money refund = domain.cancel(o);
            Coverage.mark("cancel:PAID退款");
            eq(12_345L, refund.cents);
            eq(OrderStatus.CANCELLED, o.status);
        });

        caseOf("UNIT", "未支付订单取消不退款", () -> {
            Order o = new Order("O8", new Money(777));
            Money refund = domain.cancel(o);
            Coverage.mark("cancel:CREATED无退款");
            eq(0L, refund.cents);
        });

        caseOf("UNIT", "未支付订单不可发货", () -> {
            Order o = new Order("O9", new Money(100));
            throwsEx(IllegalStateException.class, () -> domain.ship(o));
            Coverage.mark("ship:非PAID");
        });

        caseOf("UNIT", "Money.plus 溢出抛 ArithmeticException", () -> {
            throwsEx(ArithmeticException.class,
                    () -> new Money(Long.MAX_VALUE).plus(new Money(1)));
        });

        System.out.println();
        System.out.println("=== 第二层：集成测试（状态机全链路）===");

        caseOf("INTEG", "下单->支付->发货 状态链完整", () -> {
            Order o = new Order("I1", new Money(5_000));
            domain.pay(o, new Money(5_000));
            Coverage.mark("pay:CREATED");
            domain.ship(o);
            Coverage.mark("ship:PAID");
            eq(OrderStatus.SHIPPED, o.status);
            throwsEx(IllegalStateException.class, () -> domain.cancel(o));
            Coverage.mark("cancel:SHIPPED");
        });

        caseOf("INTEG", "下单->取消->再支付 被拒", () -> {
            Order o = new Order("I2", new Money(5_000));
            domain.cancel(o);
            Coverage.mark("cancel:CREATED无退款");
            throwsEx(IllegalStateException.class, () -> domain.pay(o, new Money(5_000)));
            Coverage.mark("pay:非CREATED");
        });

        System.out.println();
        System.out.println("=== 第三层：端到端（模拟接口契约）===");
        caseOf("E2E", "接口契约：支付成功返回 200 + PAID", () -> {
            Order o = new Order("E1", new Money(3_000));
            String resp = apiPay(domain, o, 3_000);
            Coverage.mark("pay:CREATED");
            eq("200|PAID", resp);
        });
        caseOf("E2E", "接口契约：金额不符返回 400", () -> {
            Order o = new Order("E2", new Money(3_000));
            String resp = apiPay(domain, o, 2_999);
            Coverage.mark("pay:金额不足");
            eq("400|支付金额不足", resp);
        });
        caseOf("E2E", "接口契约：状态错误返回 409", () -> {
            Order o = new Order("E3", new Money(3_000));
            domain.cancel(o);
            Coverage.mark("cancel:CREATED无退款");
            String resp = apiPay(domain, o, 3_000);
            Coverage.mark("pay:非CREATED");
            eq("409|状态不允许支付", resp);
        });

        System.out.println();
        System.out.println("=== 质量报告 ===");
        System.out.println("  用例总数: " + (passed + failed));
        System.out.println("  通过: " + passed + "  失败: " + failed);
        System.out.println("  分层分布: " + layerCount);
        System.out.printf("  分支覆盖率: %d%%（%d 条分支，未覆盖 %s）%n",
                Coverage.percent(), Coverage.BRANCHES.size(), Coverage.missed());
        for (String d : failureDetails) {
            System.out.println("    失败: " + d);
        }

        System.out.println();
        System.out.println("=== 质量门禁判定 ===");
        List<String> gates = new ArrayList<>();
        if (failed > 0) {
            gates.add("单元/集成/E2E 存在失败用例");
        }
        if (Coverage.percent() < 90) {
            gates.add("分支覆盖率 " + Coverage.percent() + "% < 90%（资金链路要求）");
        }
        if (!Coverage.missed().isEmpty()) {
            gates.add("存在未覆盖分支: " + Coverage.missed());
        }
        if (gates.isEmpty()) {
            System.out.println("  结果: PASS（可合并）");
        } else {
            System.out.println("  结果: BLOCKED");
            for (String g : gates) {
                System.out.println("    - " + g);
            }
        }

        System.out.println();
        System.out.println("=== 缺陷归因表（把线上 bug 变成改进项）===");
        String[][] attribution = {
                {"支付金额超额未校验", "单测漏了超额分支", "补边界用例 + 分支覆盖门禁"},
                {"已发货订单被取消", "集成测试未覆盖非法迁移", "状态机全迁移用例"},
                {"并发支付重复扣款", "无并发测试", "加幂等键 + 并发用例"},
                {"退款金额算错", "金额用 double 精度丢失", "金额改整数分 + 静态规则"},
                {"接口 500 而非 400", "异常映射未测", "E2E 覆盖异常契约"}
        };
        System.out.printf("  %-22s %-24s %s%n", "线上缺陷", "本该在哪拦住", "改进项");
        for (String[] r : attribution) {
            System.out.printf("  %-22s %-24s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("=== 四层保障体系 ===");
        String[][] layers = {
                {"规范层", "异常契约、金额用整数分、命名规范", "Code Review + 静态检查"},
                {"测试层", "单测覆盖规则、集成覆盖状态机、E2E 覆盖契约", "本课三层用例"},
                {"门禁层", "0 失败、分支覆盖 >= 90%、0 ERROR", "CI 阻断合并"},
                {"观测层", "支付失败率、状态机异常告警、对账", "线上监控"}
        };
        System.out.printf("  %-8s %-44s %s%n", "层级", "内容", "载体");
        for (String[] r : layers) {
            System.out.printf("  %-8s %-44s %s%n", r[0], r[1], r[2]);
        }
    }

    /** 模拟接口层：把领域异常映射成 HTTP 语义 */
    static String apiPay(OrderDomain domain, Order o, long cents) {
        try {
            domain.pay(o, new Money(cents));
            return "200|PAID";
        } catch (IllegalArgumentException e) {
            return "400|" + e.getMessage().split("，")[0];
        } catch (IllegalStateException e) {
            return "409|" + e.getMessage().split(":")[0];
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为"并发支付同一订单"补一个并发用例：10 个线程同时支付，断言只有 1 次成功、状态为 PAID、不重复扣款。
 * 2. 给 `Order` 增加 `version` 字段实现乐观锁，补一个"版本冲突抛异常"的用例，并说明它对应哪条线上缺陷。
 * 3. 把本课的三层用例整理成一张"用例-规则-分支"矩阵，找出仍有规则未被任何用例覆盖的格子。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 给订单模块补一套质量保障：手写测试运行器跑单测、用测试替身隔离依赖、设计等价类与边界用例、统计覆盖率、静态检查圈复杂度、接进 CI 门禁。
 * 【任务】
 * 1. 写一个基于反射的 MiniRunner：识别 @Test 注解方法、独立执行、统计通过/失败/跳过，失败时打印异常栈。
 * 2. 用动态代理手写 Stub 与 Mock：为库存服务造一个固定返回的 Stub，为通知服务造一个能验证调用次数的 Mock。
 * 3. 为「计算订单应付金额」设计用例：等价类（正常、边界、异常）+ 边界值（0 件、最大件数、折扣临界），并参数化跑一遍。
 * 4. 写静态检查脚本：统计每个方法的圈复杂度与重复代码块，超过阈值的给出告警，并把它作为提交前门禁。
 * 【验收清单】
 * [ ] MiniRunner 能正确区分通过/失败/跳过，失败用例不中断后续执行。
 * [ ] Mock 能断言「通知方法被调用了 1 次」，次数不符时测试失败。
 * [ ] 用例覆盖了金额为 0、数量为 0、折扣恰好等于阈值等边界，全部通过。
 * [ ] 静态检查能指出圈复杂度超标的方法，门禁脚本在超标时返回非 0 退出码。
 * ============================================================
 */
