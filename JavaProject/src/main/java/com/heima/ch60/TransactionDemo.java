/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-05
 * 课名：Spring 事务：代理与传播行为
 * ------------------------------------------------------------
 * 知识点：
 *   - **声明式事务本质是 AOP**：`@Transactional` 由 `BeanPostProcessor` 生成代理，代理在方法前后**开启/提交/回滚事务**——**它和其他切面一样受"自调用失效"限制**（60-04 的结论直接适用）。**事务失效的第一大原因就是自调用**。
 *   - **七个传播行为（Propagation）的语义**：`REQUIRED`（默认：有事务加入，无则新建）、`REQUIRES_NEW`（挂起当前，新建独立事务）、`NESTED`（嵌套，用 savepoint）、`SUPPORTS`（有则加入，无则以非事务执行）、`NOT_SUPPORTED`（挂起事务，非事务执行）、`MANDATORY`（必须在事务中）、`NEVER`（必须无事务）——**最常用 REQUIRED 与 REQUIRES_NEW，混用是事故源**。
 *   - **回滚规则的默认值**：**只对 `RuntimeException` 与 `Error` 回滚，受检异常不回滚**——`@Transactional(rollbackFor = Exception.class)` 是必须显式声明的（36 章"异常吞掉"的代价在事务上表现为"提交了半个业务"）。
 *   - **事务失效的五大原因**：自调用、方法非 public（代理无法拦截）、异常被 catch 未抛出、类未被 Spring 管理、传播行为配错——**每一个都对应一条排障路径**（60-01 的问题驱动法）。
 *   - **事务与数据库隔离级别的分工**：Spring 的传播行为管"事务边界"，数据库的隔离级别管"并发可见性"（20-02）——**两层概念不能混**。
 *   - 易错点：自调用导致事务失效（把方法移到另一个 Bean 或用 `AopContext.currentProxy()`）；受检异常被吞（未声明 rollbackFor）；`REQUIRES_NEW` 用在循环里（连接池耗尽）；大事务里做远程调用（60-05 的经典反模式：事务持有时间被网络 IO 拉长）。
 * ============================================================
 */

package com.heima.ch60;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class TransactionDemo {
    /** 简化的"事务管理器"：记录事务边界与回滚决策 */
    static class TxManager {
        final List<String> log = new ArrayList<>();
        int depth = 0;

        void begin(String name, String propagation) {
            if (propagation.equals("REQUIRED")) {
                if (depth == 0) {
                    log.add("BEGIN " + name);
                } else {
                    log.add("JOIN  " + name + "（加入当前事务）");
                }
                depth++;
            } else if (propagation.equals("REQUIRES_NEW")) {
                log.add("SUSPEND 当前事务，BEGIN " + name + "（独立事务）");
                depth++;
            }
        }

        void commit(String name) {
            depth--;
            log.add("COMMIT " + name);
        }

        void rollback(String name, String reason) {
            depth--;
            log.add("ROLLBACK " + name + "（" + reason + "）");
        }
    }

    /** 模拟：外层 REQUIRED，内层 REQUIRES_NEW，内层失败 */
    static void simulate() {
        var tx = new TxManager();
        tx.begin("outer.placeOrder", "REQUIRED");
        System.out.println("  执行：写订单");
        tx.begin("inner.logAudit", "REQUIRES_NEW");
        System.out.println("  执行：写审计（独立事务）");
        tx.rollback("inner.logAudit", "受检异常未声明 rollbackFor");
        System.out.println("  外层继续（独立事务回滚不影响外层）");
        tx.commit("outer.placeOrder");
        System.out.println();
        System.out.println("【事务日志】");
        tx.log.forEach(l -> System.out.println("  " + l));
        System.out.println();
    }

    /** 回滚规则演示：受检异常默认不回滚 */
    static void rollbackRule() {
        record Case(String exceptionType, boolean checked, boolean rollbackForDeclared,
                    String outcome) {
        }
        List<Case> cases = List.of(
                new Case("RuntimeException", false, false, "回滚（默认规则）"),
                new Case("IOException（受检）", true, false, "提交！（默认不回滚，高危）"),
                new Case("IOException（受检）", true, true, "回滚（声明了 rollbackFor）"));
        System.out.println("【回滚规则】");
        for (Case c : cases) {
            System.out.printf("  %-22s 受检=%-5s 声明rollbackFor=%-5s -> %s%n",
                    c.exceptionType(), c.checked(), c.rollbackForDeclared(), c.outcome());
        }
    }

    public static void main(String[] args) {
        simulate();
        rollbackRule();
        System.out.println();
        System.out.println("  铁律：@Transactional(rollbackFor = Exception.class) 应成为默认写法");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现传播行为模拟器：支持 REQUIRED/REQUIRES_NEW/NESTED 三种，用"外层成功+内层失败"与"外层失败+内层成功"两组场景输出事务日志。
 * 2. 在真实 Spring 应用里复现"自调用导致事务失效"：同 Bean 内方法 A 调 B（B 有 @Transactional），验证 B 的事务未生效，然后用抽 Bean 或 `AopContext.currentProxy()` 修复。
 * 3. 思考题：为什么"大事务里做远程调用"是反模式？从事务持有数据库连接与锁的时长分析（并给出拆分方案）。
 * ============================================================
 */
