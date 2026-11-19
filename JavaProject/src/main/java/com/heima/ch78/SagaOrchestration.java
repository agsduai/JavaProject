/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-06
 * 课名：Saga 与补偿事务
 * ------------------------------------------------------------
 * 知识点：
 *   - **Saga 解决"跨服务长事务"**：**把大事务拆成 N 个本地事务**，**每步成功发布事件/调用下一步**，**任一步失败就按反序执行补偿**——**没有全局锁，只有最终一致**。
 *   - **两种编排方式**：**协同（Choreography）**（服务间互相订阅事件，无中心）与**编排（Orchestration）**（一个 Saga 协调器按状态机驱动）——**复杂流程用编排，简单流程用协同**。
 *   - **补偿的三个坑**：**空补偿**（补偿一个没执行成功的步骤）、**悬挂**（补偿先到、原操作后到）、**重复补偿**（幂等）——**用"执行状态表 + 幂等键"解决**。
 *   - **Saga 不保证隔离性**：**中间状态对外可见**（如"库存已扣、支付未完成"）——**对策：业务状态机 + 预留/确认两阶段**（TCC 思想）。
 *   - 与 49/55 章的关系：49 章 DDD 的聚合边界决定"哪些步骤能拆"，**55 章 SaaS 的多租户一致性**也常靠 Saga——**分布式事务不是银弹，是业务妥协**。
 *   - 易错点：补偿接口不幂等（重复补偿多退钱）；不记录"哪些步骤已执行"（空补偿/漏补偿）；把 Saga 当强一致（中间态可见）；补偿失败无兜底（人工介入）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

// ==================== 示例代码 ====================

public class SagaOrchestration {
    static class SagaStep {
        final String name;
        final BooleanSupplier action;
        final Runnable compensation;
        boolean executed;

        SagaStep(String name, BooleanSupplier action, Runnable compensation) {
            this.name = name;
            this.action = action;
            this.compensation = compensation;
        }
    }

    static final List<String> log = new ArrayList<>();

    /** 编排器：顺序执行，失败反序补偿（只补偿已执行的步骤） */
    static boolean runSaga(List<SagaStep> steps) {
        List<SagaStep> executed = new ArrayList<>();
        for (SagaStep step : steps) {
            System.out.printf("  执行 %-12s ... ", step.name);
            boolean ok = step.action.getAsBoolean();
            if (ok) {
                step.executed = true;
                executed.add(step);
                System.out.println("成功");
            } else {
                System.out.println("失败 -> 开始补偿");
                for (int i = executed.size() - 1; i >= 0; i--) {
                    SagaStep s = executed.get(i);
                    System.out.printf("    补偿 %-12s ... ", s.name);
                    s.compensation.run();
                    System.out.println("完成");
                }
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println("【Saga：下单 -> 扣库存 -> 支付 -> 发货】");
        List<SagaStep> steps = new ArrayList<>();
        steps.add(new SagaStep("创建订单",
                () -> {
                    log.add("order-created");
                    return true;
                },
                () -> log.add("order-cancelled")));
        steps.add(new SagaStep("扣减库存",
                () -> {
                    log.add("stock-deducted");
                    return true;
                },
                () -> log.add("stock-restored")));
        steps.add(new SagaStep("支付扣款",
                () -> {
                    log.add("payment-failed");
                    return false;                    // 模拟支付失败
                },
                () -> log.add("payment-refunded")));
        steps.add(new SagaStep("创建发货单",
                () -> {
                    log.add("shipment-created");
                    return true;
                },
                () -> log.add("shipment-cancelled")));

        boolean ok = runSaga(steps);
        System.out.println("  Saga 结果：" + (ok ? "成功" : "失败（已补偿）"));
        System.out.println("  执行日志：" + log);

        System.out.println();
        System.out.println("【补偿的三个坑】");
        String[][] rows = {
                {"空补偿", "补偿了未执行的步骤", "记录 executed 列表，只补偿已执行"},
                {"悬挂", "补偿先到、原操作后到", "状态表 + 幂等键，拒绝已取消的操作"},
                {"重复补偿", "同一补偿执行多次", "补偿接口幂等（按业务键去重）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-24s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("【Saga vs TCC】");
        System.out.println("  Saga：本地事务 + 补偿，简单但中间态可见");
        System.out.println("  TCC：Try 预留 / Confirm 确认 / Cancel 取消，隔离性更好但侵入大");
        System.out.println("  选择：资金类用 TCC，长流程用 Saga");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Saga 编排器：5 步流程，第 4 步失败，验证只补偿前 3 步且顺序正确。
 * 2. 给补偿接口加幂等键，模拟"重复补偿"与"悬挂消息"，验证都不会重复退款。
 * 3. 思考题：为什么 Saga 不保证隔离性？从"中间状态可见"举例，并给出 TCC 的改进。
 * ============================================================
 */
