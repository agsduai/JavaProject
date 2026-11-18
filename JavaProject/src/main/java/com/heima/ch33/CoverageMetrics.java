/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-04
 * 课名：覆盖率：行覆盖、分支覆盖与未覆盖分支分析
 * ------------------------------------------------------------
 * 知识点：
 *   - **覆盖率**回答"测试跑了多少代码"，是**发现测试盲区**的探针，不是质量目标。追 100% 覆盖率会写出大量"执行了但没断言"的空测试。
 *   - 四种粒度：
 *   - **行覆盖**（Line）：某行被执行过。最弱，`if (a && b)` 一行也算覆盖。
 *   - **分支覆盖**（Branch）：每个 `if`/`switch`/三元/`&&`/`||` 的两个方向都走到。比行覆盖有意义得多。
 *   - **条件覆盖**：复合条件里每个原子条件取过 true/false。
 *   - **路径覆盖**：方法内所有执行路径组合。分支多时路径爆炸，实际少用。
 *   - JaCoCo 的原理：**字节码插桩**——在 class 文件里每个分支跳转前插入探针（记录 boolean 数组），运行结束后 dump 出 `.exec` 文件，再结合源码生成报告。它不修改源码。
 *   - 覆盖率报告要看的不是总数，而是**红色（未覆盖）的那几行**：往往是异常分支、`null` 判断、`default` 分支、边界 `else`。这些恰是最容易出 bug 的地方。
 *   - 覆盖率的合理区间：核心业务（资金、库存、权限）分支覆盖 80% 以上；DTO/getter/setter、配置类不必强求；生成的代码排除。
 *   - **覆盖率不等于断言**：`service.calculate()` 被调用就算覆盖，但不断言结果，测试毫无价值。变异测试（PIT）才能验证"断言是否有效"——故意改坏代码，看测试会不会红。
 *   - 未覆盖分支的常见成因：只写 happy path、`catch` 块无法触发（依赖抛异常）、防御性 `null` 检查、`default` 兜底分支、并发分支。
 *   - 纯 JDK 无法直接读字节码覆盖率，本课用**显式埋点 + 分支表**模拟 JaCoCo 的统计思路：业务代码在每个分支处打点，测试跑完后统计"哪些点没被命中"。
 *   - 易错：把覆盖率当 KPI 导致写"调用即通过"的测试；只统计行覆盖不看分支；忽略 `catch` 分支；覆盖率高但断言缺失（变异测试会暴露）。
 * ============================================================
 */

package com.heima.ch33;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class CoverageMetrics {
    /** 探针：模拟 JaCoCo 插入的 boolean 数组 */
    static class Probe {
        static final Set<String> HIT = new LinkedHashSet<>();
        static final Set<String> ALL = new LinkedHashSet<>();

        static void declare(String id) {
            ALL.add(id);
        }

        static void hit(String id) {
            HIT.add(id);
        }

        static void reset() {
            HIT.clear();
        }

        static double rate() {
            return ALL.isEmpty() ? 1.0 : (double) HIT.size() / ALL.size();
        }
    }

    /** 被测：运费计算，每个分支埋一个探针 */
    static class ShippingCalculator {
        static {
            Probe.declare("B1_weight<=0");
            Probe.declare("B2_weight<=1");
            Probe.declare("B3_weight<=5");
            Probe.declare("B4_remote");
            Probe.declare("B5_vip");
            Probe.declare("B6_freeThreshold");
            Probe.declare("B7_negativeAmount");
        }

        /** @return 运费（分） */
        long calc(int weightGram, boolean remote, boolean vip, long orderCents) {
            if (weightGram <= 0) {
                Probe.hit("B1_weight<=0");
                throw new IllegalArgumentException("重量必须为正: " + weightGram);
            }
            long base;
            if (weightGram <= 1000) {
                Probe.hit("B2_weight<=1");
                base = 800;
            } else if (weightGram <= 5000) {
                Probe.hit("B3_weight<=5");
                base = 800 + (weightGram - 1000) / 500 * 200;
            } else {
                base = 800 + 8 * 200 + (weightGram - 5000) / 1000 * 300;
            }
            if (remote) {
                Probe.hit("B4_remote");
                base += 1000;
            }
            if (vip) {
                Probe.hit("B5_vip");
                base = base * 80 / 100;
            }
            if (orderCents >= 9_900L) {
                Probe.hit("B6_freeThreshold");
                return 0;
            }
            if (orderCents < 0) {
                Probe.hit("B7_negativeAmount");
                throw new IllegalStateException("订单金额为负");
            }
            return base;
        }
    }

    static class Case {
        final String desc;
        final int weight;
        final boolean remote;
        final boolean vip;
        final long order;
        final boolean expectThrow;

        Case(String desc, int weight, boolean remote, boolean vip, long order, boolean expectThrow) {
            this.desc = desc;
            this.weight = weight;
            this.remote = remote;
            this.vip = vip;
            this.order = order;
            this.expectThrow = expectThrow;
        }
    }

    static long lastResult;

    static void run(ShippingCalculator c, Case k) {
        try {
            lastResult = c.calc(k.weight, k.remote, k.vip, k.order);
            if (k.expectThrow) {
                System.out.printf("    %-30s 期望抛异常但没抛 -> FAIL%n", k.desc);
            } else {
                System.out.printf("    %-30s 运费=%-6d OK%n", k.desc, lastResult);
            }
        } catch (RuntimeException e) {
            if (k.expectThrow) {
                System.out.printf("    %-30s 抛出 %-22s OK%n", k.desc, e.getClass().getSimpleName());
            } else {
                System.out.printf("    %-30s 意外异常 %s -> FAIL%n", k.desc, e.getMessage());
            }
        }
    }

    static void report(String title) {
        System.out.println("  " + title);
        System.out.printf("    分支覆盖率 = %d/%d = %.0f%%%n",
                Probe.HIT.size(), Probe.ALL.size(), Probe.rate() * 100);
        List<String> missed = new ArrayList<>(Probe.ALL);
        missed.removeAll(Probe.HIT);
        if (missed.isEmpty()) {
            System.out.println("    未覆盖分支: 无");
        } else {
            System.out.println("    未覆盖分支: " + missed);
        }
    }

    public static void main(String[] args) {
        ShippingCalculator calc = new ShippingCalculator();

        System.out.println("=== 1. 第一轮：只有 happy path（典型的低质量测试）===");
        Probe.reset();
        List<Case> round1 = new ArrayList<>();
        round1.add(new Case("1kg 普通 订单 50 元", 1000, false, false, 5_000, false));
        round1.add(new Case("3kg 普通 订单 50 元", 3_000, false, false, 5_000, false));
        for (Case k : round1) {
            run(calc, k);
        }
        report("第一轮结果");

        System.out.println();
        System.out.println("=== 2. 第二轮：补齐边界与异常分支 ===");
        Probe.reset();
        List<Case> round2 = new ArrayList<>();
        round2.add(new Case("1kg 普通", 1_000, false, false, 5_000, false));
        round2.add(new Case("1kg 偏远", 1_000, true, false, 5_000, false));
        round2.add(new Case("1kg VIP", 1_000, false, true, 5_000, false));
        round2.add(new Case("满 99 免运费", 1_000, false, false, 9_900, false));
        round2.add(new Case("0g 非法", 0, false, false, 5_000, true));
        round2.add(new Case("负金额", 1_000, false, false, -1, true));
        for (Case k : round2) {
            run(calc, k);
        }
        report("第二轮结果");

        System.out.println();
        System.out.println("=== 3. 两轮对比 ===");
        System.out.println("  第一轮 2 个用例：只覆盖常规路径，异常与优惠分支全漏");
        System.out.println("  第二轮 6 个用例：分支覆盖补齐到 100%");
        System.out.println("  关键：第二轮多出来的 4 个用例，正好是 4 个高风险分支");

        System.out.println();
        System.out.println("=== 4. 覆盖率的四种粒度 ===");
        String[][] t = {
                {"行覆盖", "该行是否执行过", "最弱，复合条件一行算一个"},
                {"分支覆盖", "if/switch/&&/|| 两方向", "性价比最高，项目基线"},
                {"条件覆盖", "复合条件内每个原子条件", "适合复杂布尔表达式"},
                {"路径覆盖", "所有路径组合", "分支多时爆炸，慎用"},
                {"变异覆盖", "改坏代码测试是否会红", "验证断言有效性（PIT）"}
        };
        for (String[] r : t) {
            System.out.printf("  %-12s %-28s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("=== 5. 覆盖率报告该看什么 ===");
        String[] points = {
                "看红色行，不看总百分比",
                "catch 块是否被触发过（异常路径最易漏）",
                "default / else 兜底分支是否覆盖",
                "null 判断分支是否两边都走到",
                "覆盖率涨了但断言没加 -> 无效覆盖",
                "DTO / getter / 配置类从统计中排除"
        };
        for (String p : points) {
            System.out.println("  - " + p);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `calc` 增加"首重 1kg 内偏远地区加价"的规则，先补探针再补用例，让分支覆盖保持 100%。
 * 2. 写一个"覆盖率高但断言缺失"的测试类（只调用不断言），说明为什么 PIT 变异测试能发现它。
 * 3. 说明为什么 `if (orderCents >= 9_900L) return 0;` 之后的 `if (orderCents < 0)` 是不可达分支，并给出修正顺序。
 * ============================================================
 */
