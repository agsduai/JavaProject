/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-11
 * 课名：综合案例：用 Java 21 重构订单模块
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一次真实重构**：老订单模块（可变实体 + if-else 状态处理 + 三套集合 API + 拼接 JSON）-> 现代形态（record 值对象 + sealed 状态事件 + 模式匹配分发 + Sequenced Collections 取最近记录 + 文本块 SQL）。
 *   - **重构的安全网**：先写"黄金样本"（20 组输入输出快照 49-10）-> 逐块替换（每块一个提交）-> 每步跑回归 -> 全部等价才算完成——**重构不是重写**。
 *   - **重构顺序（按收益/风险比）**：① 值对象改 record（无行为变更，收益最大）；② JSON 拼接改文本块模板（可读性）；③ 集合 API 统一（`getFirst`）；④ 状态处理改 sealed + switch（编译器保证穷举）；⑤ 最后才动业务逻辑。
 *   - **收益量化范本**：样板代码 -60%（record 替代 getter/equals）、状态遗漏风险归零（sealed 穷举）、注入风险下降（占位符/序列化）、可读性提升（文本块）。
 *   - **重构产出的文档**：变更清单（每项对应哪条语言特性）、行为等价证据（黄金样本 diff）、回退方案——**41 章发布纪律的代码重构版**。
 *   - 易错点：重构同时改业务逻辑（回归失败时无法归因）；一次性替换所有 getter（review 困难）；record 化时漏掉可变性（原实体会被外部改字段）。
 * ============================================================
 */

package com.heima.ch57;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ModernRefactorCase {
    /** 重构后：值对象 record + 状态 sealed + 模式匹配分发 */
    record Item(String sku, long unitCents, int qty) {
        long subtotal() {
            return unitCents * qty;
        }
    }

    sealed interface OrderState permits Placed, Paid, Closed {
    }

    record Placed(long placedAt) implements OrderState {
    }

    record Paid(long paidAt, String channel) implements OrderState {
    }

    record Closed(String reason) implements OrderState {
    }

    record Order(String id, List<Item> items, OrderState state) {
        Order {
            items = List.copyOf(items);
        }

        long totalCents() {
            return items.stream().mapToLong(Item::subtotal).sum();
        }

        /** 状态说明：新增状态 -> 此 switch 编译报错（穷举保障） */
        String describe() {
            return switch (state) {
                case Placed p -> "待支付（下单于 " + p.placedAt() + "）";
                case Paid pa -> "已支付（" + pa.channel() + "，金额 " + totalCents() + " 分）";
                case Closed c -> "已关闭（" + c.reason() + "）";
            };
        }
    }

    /** 最近的 N 条商品（Sequenced Collections 取尾部） */
    static List<Item> latest(List<Item> items, int n) {
        var copy = new ArrayList<>(items);
        var out = new ArrayList<Item>();
        for (int i = 0; i < n && !copy.isEmpty(); i++) {
            out.add(copy.removeLast());
        }
        return out;
    }

    public static void main(String[] args) {
        var items = List.of(new Item("SKU-A", 6900, 1), new Item("SKU-B", 3900, 2),
                new Item("SKU-C", 12900, 1));
        var orders = List.of(
                new Order("9001", items, new Placed(1000)),
                new Order("9002", items, new Paid(2000, "wechat")),
                new Order("9003", items, new Closed("用户取消")));
        orders.forEach(o -> System.out.printf("  %s：%s，总额 %d 分%n",
                o.id(), o.describe(), o.totalCents()));
        System.out.println("  最近 2 条商品：" + latest(items, 2));
        System.out.println();
        System.out.println("  重构收益：record 消除样板 / sealed 穷举保障 / 文本块 SQL（57-06）/ 集合 API 统一");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 选一个真实的"贫血实体 + if-else 状态处理"模块，按本章顺序完成五步重构，每步产出回归证据。
 * 2. 为本案例补充"退款"状态（`Refunded`），观察编译器指出的所有待改点并逐一处理。
 * 3. 思考题：为什么重构要"先写黄金样本"？从"行为等价的客观证据"与"重构与改需求混淆"两个角度分析。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 把一段 Java 8 风格的订单代码用 Java 21 特性重构：record 表达值对象、sealed 约束状态、switch 模式匹配替代 if 链、文本块写 SQL、var 简化局部变量、Sequenced Collections 统一首尾操作。
 * 【任务】
 * 1. 把 Order、Money、Address 改成 record，说明 record 的不可变语义与适用边界（什么时候不该用 record）。
 * 2. 用 sealed interface 定义订单状态，配合 switch 模式匹配穷举所有状态，验证漏写分支时编译报错。
 * 3. 用记录模式解构嵌套 record（如 Order(Money amount, Address addr)），在 switch 中直接取字段。
 * 4. 用文本块写多行 SQL 与 JSON，用 var 简化局部变量，用 SequencedCollection 的 getFirst/getLast 替代下标计算。
 * 【验收清单】
 * [ ] 重构后行为与重构前完全一致（原有测试全通过）。
 * [ ] 新增一个状态时，编译器能指出所有需要修改的 switch 位置。
 * [ ] record 组件不可变，任何「修改」都返回新对象。
 * [ ] 代码中不再出现 get(0)、get(size()-1) 这类下标写法。
 * ============================================================
 */
