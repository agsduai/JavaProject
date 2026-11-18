/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-10
 * 课名：代码坏味道与重构手法
 * ------------------------------------------------------------
 * 知识点：
 *   - **坏味道（code smell）** 是"能跑但会变难改"的信号，不是 bug。识别它是重构的起点，比记住 20 种手法更重要。
 *   - 常见坏味道：**过长方法**（超过一屏）、**过大类**（几百行、职责混杂）、**长参数列表**（4 个以上）、**重复代码**（复制粘贴改一处漏一处）、**发散式变化**（一个类因多种原因被改）、**霰弹式修改**（一个改动要改十几个类）、**基本类型偏执**（用 `String` 表示金额、手机号）、**数据泥团**（三四个字段总是同时出现）、**switch 语句**（按类型分支，新增类型要改多处）。
 *   - 对应手法：**提炼方法**（Extract Method）、**内联**（Inline）、**引入参数对象**（Introduce Parameter Object）、**以多态取代条件**（Replace Conditional with Polymorphism）、**搬移方法/字段**（Move Method/Field）、**提炼类**（Extract Class）、**以值对象取代基本类型**（Replace Primitive with Value Object）、**以查询取代临时变量**（Replace Temp with Query）。
 *   - 重构的纪律：**小步 + 每步可编译 + 每步跑测试**。一次改 500 行再调试，等于重新开发。
 *   - 重构与重写的区别：重构是"外部行为不变、内部结构变好"；重写是"推倒重来"。能重构就别重写，重写会丢光历史 bug 修复。
 *   - 重构的时机：加新功能前（先让改动变容易）、修 bug 时（顺手清理）、代码评审时。**不要**在发布前夜重构。
 *   - 度量视角：圈复杂度（分支数）、方法行数、类行数、重复率、扇入扇出。工具能算，但阈值要按团队约定。
 *   - 易错：重构时顺手改行为（"既然改到这里了，顺便加个功能"）——这让测试无法判断是重构引入的问题还是新功能的问题；没有测试就大改；把"重构"当"重命名"。
 * ============================================================
 */

package com.heima.ch31;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RefactorSmells {
    /** ===== 重构前：一个方法干完所有事，参数多、分支多、魔法值多 ===== */
    static class BadOrderService {
        String handle(String userName, String phone, String sku, int qty,
                      long priceCents, int userLevel, boolean useCoupon, String channel) {
            if (userName == null || userName.trim().isEmpty()) {
                return "ERR_NAME";
            }
            if (phone == null || phone.length() != 11) {
                return "ERR_PHONE";
            }
            if (qty <= 0) {
                return "ERR_QTY";
            }
            long total = priceCents * qty;
            if (userLevel == 3) {
                total = total - total * 15 / 100;
            } else if (userLevel == 2) {
                total = total - total * 8 / 100;
            }
            if (useCoupon && total >= 10000) {
                total = total - 1000;
            }
            if ("app".equals(channel)) {
                total = total + 0;
            } else if ("h5".equals(channel)) {
                total = total + 200;
            }
            String sms;
            if ("app".equals(channel)) {
                sms = "APP推送";
            } else {
                sms = "短信到 " + phone;
            }
            return "OK total=" + total + " notify=" + sms;
        }
    }

    /** ===== 重构后：值对象 + 策略 + 小方法 ===== */
    /** 以值对象取代基本类型：金额自己管自己 */
    record Money(long cents) {
        Money multiply(int n) {
            return new Money(cents * n);
        }

        Money minus(Money o) {
            return new Money(Math.max(0, cents - o.cents));
        }

        Money percentOff(int percent) {
            return new Money(cents - cents * percent / 100);
        }

        @Override
        public String toString() {
            return String.format("%.2f元", cents / 100.0);
        }
    }

    /** 数据泥团：总是同时出现的字段收成一个对象 */
    record OrderRequest(String userName, String phone, String sku, int qty,
                        Money unitPrice, int userLevel, boolean useCoupon, String channel) {
    }

    static class Validator {
        /** 提炼方法：每个校验独立可测 */
        void validate(OrderRequest r) {
            if (r.userName() == null || r.userName().trim().isEmpty()) {
                throw new IllegalArgumentException("收货人不能为空");
            }
            if (r.phone() == null || r.phone().length() != 11) {
                throw new IllegalArgumentException("手机号必须是 11 位");
            }
            if (r.qty() <= 0) {
                throw new IllegalArgumentException("数量必须大于 0");
            }
        }
    }

    /** 以多态取代条件：等级折扣 */
    interface LevelDiscount {
        int percent();
    }

    static final Map<Integer, Integer> LEVEL_PERCENT = new LinkedHashMap<>(Map.of(3, 15, 2, 8));

    static class Pricing {
        Money base(OrderRequest r) {
            return r.unitPrice().multiply(r.qty());
        }

        /** 等级折扣：查表取代 if/else 链 */
        Money afterLevel(Money m, int level) {
            Integer pct = LEVEL_PERCENT.get(level);
            return pct == null ? m : m.percentOff(pct);
        }

        Money afterCoupon(Money m, boolean useCoupon) {
            return useCoupon && m.cents() >= 10000 ? m.minus(new Money(1000)) : m;
        }

        /** 渠道附加费：把魔法值 200 提成常量并说明含义 */
        static final Money H5_SERVICE_FEE = new Money(200);

        Money afterChannel(Money m, String channel) {
            return "h5".equals(channel) ? new Money(m.cents() + H5_SERVICE_FEE.cents()) : m;
        }

        Money total(OrderRequest r) {
            Money m = base(r);
            m = afterLevel(m, r.userLevel());
            m = afterCoupon(m, r.useCoupon());
            m = afterChannel(m, r.channel());
            return m;
        }
    }

    static class Notifier {
        String plan(OrderRequest r) {
            return "app".equals(r.channel()) ? "APP推送" : "短信到 " + r.phone();
        }
    }

    static class OrderService {
        private final Validator validator = new Validator();
        private final Pricing pricing = new Pricing();
        private final Notifier notifier = new Notifier();

        String handle(OrderRequest r) {
            validator.validate(r);
            Money total = pricing.total(r);
            return "OK total=" + total + " notify=" + notifier.plan(r);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 重构前：一个方法 30 行，8 个参数 ===");
        BadOrderService bad = new BadOrderService();
        System.out.println("  " + bad.handle("张三", "13800138000", "SKU-1", 3, 4990, 3, true, "h5"));
        System.out.println("  " + bad.handle("", "13800138000", "SKU-1", 1, 1000, 1, false, "app"));
        System.out.println("  问题：改折扣要动这个方法、改通知也要动、新增渠道还要动");

        System.out.println();
        System.out.println("=== 2. 重构后：职责拆开，每块独立可测 ===");
        OrderService good = new OrderService();
        OrderRequest r1 = new OrderRequest("张三", "13800138000", "SKU-1", 3,
                new Money(4990), 3, true, "h5");
        System.out.println("  " + good.handle(r1));
        OrderRequest r2 = new OrderRequest("李四", "13900139000", "SKU-2", 1,
                new Money(1000), 1, false, "app");
        System.out.println("  " + good.handle(r2));
        try {
            good.handle(new OrderRequest("", "13900139000", "SKU-2", 1,
                    new Money(1000), 1, false, "app"));
        } catch (IllegalArgumentException e) {
            System.out.println("  校验异常：" + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 3. 坏味道 -> 手法 对照 ===");
        String[][] map = {
                {"过长方法", "提炼方法 Extract Method"},
                {"长参数列表", "引入参数对象 Introduce Parameter Object"},
                {"数据泥团", "提炼类 Extract Class"},
                {"switch 分支", "以多态/查表取代条件"},
                {"基本类型偏执", "以值对象取代基本类型（Money）"},
                {"重复代码", "提炼方法 + 上移 Pull Up"}
        };
        for (String[] m : map) {
            System.out.printf("  %-12s -> %s%n", m[0], m[1]);
        }

        System.out.println();
        System.out.println("=== 4. 重构纪律 ===");
        List<String> rules = new ArrayList<>(List.of(
                "小步走：每步都能编译、都能跑测试",
                "外部行为不变：只改结构，不改功能",
                "先补测试再重构：没有测试网就不动刀",
                "一次只治一种坏味道：混着改无法定位问题",
                "发布前夜不重构：风险窗口太小"));
        for (int i = 0; i < rules.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + rules.get(i));
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `BadOrderService` 里的渠道附加费从"硬编码 200"改成"可配置"，说明这属于哪种手法。
 * 2. 用 `record` 定义 `Phone` 值对象，在构造时校验 11 位，替换掉所有 `String phone` 参数。
 * 3. 列出你最近写的一个超过 80 行的方法，按"提炼方法"拆成 4 个以内的小方法，并说明如何验证行为未变。
 * ============================================================
 */
