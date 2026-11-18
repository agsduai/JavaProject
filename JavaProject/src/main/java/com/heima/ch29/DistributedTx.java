/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-09
 * 课名：分布式事务：从 2PC 到最终一致
 * ------------------------------------------------------------
 * 知识点：
 *   - 本地事务靠数据库 ACID，跨服务后没有统一的锁和日志，只能用**补偿**或**协调者**来近似。
 *   - **2PC/XA**：协调者先 `prepare` 所有参与者，全部就绪再 `commit`。强一致，但同步阻塞、协调者单点、性能差，不适合高并发互联网场景。
 *   - **TCC**（Try-Confirm-Cancel）：业务层实现预留/确认/取消三个接口。性能好、可控性强，但侵入大，要处理空回滚、悬挂、幂等三个经典问题。
 *   - **Saga**：把长事务拆成一串本地事务，失败时按反序执行补偿。适合长流程（旅游预订：机票 + 酒店 + 租车），但**没有隔离性**，中间状态对外可见。
 *   - **本地消息表 / 事务消息**：只保证"本地操作 + 消息发送"原子，下游异步消费，是互联网最常用的**最终一致**方案（详见第 28-09 课）。
 *   - **最大努力通知**：不断重试通知对方，直到成功或达到上限，适合对账、支付回调这类"必须通知到但可以晚"的场景。
 *   - 选型口诀：强一致且低频 → 2PC；高并发且能补偿 → TCC/Saga；只要最终一致 → 本地消息表；对外通知 → 最大努力通知。
 *   - 易错：以为加了 `@Transactional` 就能跨服务；TCC 的 Cancel 没有做幂等，重试时把已确认的又取消了；Saga 补偿顺序搞反。
 * ============================================================
 */

package com.heima.ch29;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DistributedTx {
    /** 参与者：账户，余额 + 冻结 */
    static class Account {
        final String name;
        int balance;
        int frozen;

        Account(String name, int balance) {
            this.name = name;
            this.balance = balance;
        }

        boolean tryFreeze(int amount) {
            if (balance < amount) {
                return false;
            }
            balance -= amount;
            frozen += amount;
            System.out.println("    [" + name + "] Try：冻结 " + amount + "，余额 " + balance + "，冻结中 " + frozen);
            return true;
        }

        void confirm(int amount) {
            frozen -= amount;
            System.out.println("    [" + name + "] Confirm：扣减冻结 " + amount + "，余额 " + balance + "，冻结中 " + frozen);
        }

        void cancel(int amount) {
            frozen -= amount;
            balance += amount;
            System.out.println("    [" + name + "] Cancel：解冻 " + amount + "，余额 " + balance + "，冻结中 " + frozen);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== TCC：转账 100（A -> B）===");
        Account a = new Account("A", 500);
        Account b = new Account("B", 100);
        int amount = 100;

        List<Runnable> confirms = new ArrayList<>();
        List<Runnable> cancels = new ArrayList<>();

        boolean okA = a.tryFreeze(amount);
        if (okA) {
            confirms.add(() -> a.confirm(amount));
            cancels.add(() -> a.cancel(amount));
        }
        boolean okB = b.tryFreeze(amount);
        if (okB) {
            confirms.add(() -> b.confirm(amount));
            cancels.add(() -> b.cancel(amount));
        }

        System.out.println("  Try 阶段结果：A=" + okA + "，B=" + okB);
        if (okA && okB) {
            System.out.println("  全部 Try 成功 -> 执行 Confirm");
            confirms.forEach(Runnable::run);
        } else {
            System.out.println("  有 Try 失败 -> 反序执行 Cancel");
            for (int i = cancels.size() - 1; i >= 0; i--) {
                cancels.get(i).run();
            }
        }
        System.out.println("  最终：A 余额 " + a.balance + "，B 余额 " + b.balance);

        System.out.println();
        System.out.println("=== Saga：机票 -> 酒店 -> 租车，酒店失败 ===");
        List<String> done = new ArrayList<>();
        boolean hotelOk = false;
        if (true) {
            done.add("机票已订");
            System.out.println("  步骤1 订机票 成功");
        }
        if (hotelOk) {
            done.add("酒店已订");
            System.out.println("  步骤2 订酒店 成功");
        } else {
            System.out.println("  步骤2 订酒店 失败 -> 反序补偿");
            for (int i = done.size() - 1; i >= 0; i--) {
                System.out.println("    补偿：" + done.get(i).replace("已订", "已取消"));
            }
            System.out.println("  步骤3 租车 未执行");
        }

        System.out.println();
        System.out.println("=== 方案对比 ===");
        Map<String, String> table = new LinkedHashMap<>();
        table.put("2PC/XA", "强一致，同步阻塞，协调者单点，适合低频强一致");
        table.put("TCC", "高性能可控，侵入大，要处理空回滚/悬挂/幂等");
        table.put("Saga", "适合长流程，无隔离性，中间状态可见");
        table.put("本地消息表", "最终一致，最常用，需下游幂等");
        table.put("最大努力通知", "反复重试直到成功，适合对账与支付回调");
        for (Map.Entry<String, String> e : table.entrySet()) {
            System.out.println("  " + e.getKey() + "：" + e.getValue());
        }

        System.out.println();
        System.out.println("=== TCC 三个坑 ===");
        System.out.println("  空回滚：Try 没执行就收到 Cancel -> Cancel 要能识别并直接返回成功");
        System.out.println("  悬挂：Cancel 先到，Try 后到 -> Try 要检查是否已 Cancel，拒绝执行");
        System.out.println("  幂等：Confirm/Cancel 重试多次 -> 结果必须一致");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Account.tryFreeze` 加上"已 Cancel 则拒绝"的标记，演示悬挂问题被解决。
 * 2. 说明为什么 2PC 在高并发场景下性能差，从锁持有时间的角度分析。
 * 3. 为"下单扣库存 + 扣优惠券 + 加积分"设计事务方案，说明每一步用哪种模式。
 * ============================================================
 */
