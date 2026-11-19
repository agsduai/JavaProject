/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-03
 * 课名：sealed：受限继承与穷举保障
 * ------------------------------------------------------------
 * 知识点：
 *   - **sealed 把继承"关进白名单"**：`sealed interface Shape permits Circle, Square, Triangle`——**只有 permits 列出的类能实现它**，子类必须声明 `final`（或 `sealed`/`non-sealed`）。**这解决了"继承谱系不可知"的问题**：领域模型能精确表达"就这三种形态"。
 *   - **穷举检查是核心价值**：sealed + switch 模式匹配时，**编译器校验所有许可子类都被覆盖**——漏一个分支直接编译错误（而非运行期漏处理）。**这是"编译期防腐"，比文档与测试更硬**。
 *   - **三种子类标记**：`final`（不再继承，最常见）、`sealed`（继续受限）、`non-sealed`（重新开放继承，**慎用**——一旦有人 non-sealed，穷举保障就出现缺口）。
 *   - **与 31 章策略模式的关系**：策略族（可扩展、外部可插拔）用接口 + 实现类；**封闭的领域枚举（支付状态/订单类型/形状）用 sealed**——判据是"谱系是否封闭"，封闭用 sealed 换穷举保障。
 *   - **sealed 的工程收益**：新增子类时编译器把"所有需要处理的位置"都标红（改动面自动暴露）；删除子类同样被检查——**模型演进时有安全网**（49-10 演进思想的语言级支持）。
 *   - **与 record 的黄金组合**：`sealed interface Event permits OrderCreated, OrderPaid, OrderCancelled` + 三个 `record`——**事件驱动建模的最强表达**（49-03 领域事件的语言级实现），switch 处理零遗漏。
 *   - 易错点：non-sealed 滥用（穷举保障被破坏）；同模块限制（sealed 子类通常要在同一模块/包，跨模块受限）；把可扩展插件体系做成 sealed（扩展性反而被杀）。
 * ============================================================
 */

package com.heima.ch57;

// ==================== 示例代码 ====================

public class SealedDemo {
    /** 封闭事件谱系：只允许这三种事件 */
    sealed interface DomainEvent permits OrderCreated, OrderPaid, OrderCancelled {
        String orderId();
    }

    record OrderCreated(String orderId, long amountCents) implements DomainEvent {
    }

    record OrderPaid(String orderId, long paidAtMs) implements DomainEvent {
    }

    record OrderCancelled(String orderId, String reason) implements DomainEvent {
    }

    /** 只修改这一个方法，就能触发"是否有遗漏分支"的编译期问题 */
    static void handle(DomainEvent e, long now) {
        String desc = switch (e) {
            case OrderCreated c -> "创建：" + c.orderId() + " 金额 " + c.amountCents();
            case OrderPaid p -> "支付：" + p.orderId() + " 时间 " + p.paidAtMs();
            case OrderCancelled x -> "取消：" + x.orderId() + " 原因 " + x.reason();
        };
        System.out.println("  [t=" + now + "] " + desc);
    }

    public static void main(String[] args) {
        handle(new OrderCreated("9001", 6900), 0);
        handle(new OrderPaid("9001", 1000), 1000);
        handle(new OrderCancelled("9002", "用户申请"), 2000);
        System.out.println("  新增事件类型 -> switch 处理处全部编译报错，改动面自动暴露");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 49 章的订单领域事件改为 sealed + record 谱系，实现一个 switch 处理器，然后故意新增一个事件类型观察编译错误。
 * 2. 为 `sealed interface PaymentResult permits Success, Pending, Failed` 写三个子类型，分别给一个处理函数。
 * 3. 思考题：什么情况下不该用 sealed？从"插件化扩展"与"跨团队独立迭代"两个场景分析 non-sealed 的取舍。
 * ============================================================
 */
