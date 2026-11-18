/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-08
 * 课名：观察者模式与责任链模式
 * ------------------------------------------------------------
 * 知识点：
 *   - **观察者**：主题维护观察者列表，状态变化时通知所有观察者。这就是"发布-订阅"在单机内的形态，也是事件驱动架构的起点。
 *   - 两种实现：**推**（通知时把数据传过去，观察者无需回查）和**拉**（只通知"变了"，观察者自己来取）。推更简单，拉更灵活。
 *   - 关键工程问题：**观察者抛异常不能影响其他观察者**。必须逐个 `try-catch`，否则第一个失败的订阅者会让后面全部收不到通知。
 *   - 另一个坑：**通知过程中增删观察者**会引发 `ConcurrentModificationException`，应遍历快照（`new ArrayList<>(list)`）或使用 `CopyOnWriteArrayList`。
 *   - 同步通知 vs 异步通知：同步简单但会拉长主流程耗时；异步要处理顺序、丢失与线程池耗尽。生产上常用"同步落库 + 异步发事件"。
 *   - **责任链**：把处理者串成链，请求沿链传递，直到某个处理者处理或链结束。适合"多级审批""多条件过滤""网关过滤器"。
 *   - 链的两种语义：**找到即停**（如审批、异常处理）和**每级都执行**（如过滤器、拦截器）。设计时必须明确是哪一种。
 *   - 责任链的风险：链太长导致栈深（递归实现）或调试困难；某个处理者忘记调用 `next` 导致请求"静默消失"。
 *   - 易错：观察者里做重活拖慢主流程；责任链没有终止条件造成无限循环；链的顺序硬编码在业务代码里。
 * ============================================================
 */

package com.heima.ch31;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ObserverChain {
    /** ===== 观察者：订单事件 ===== */
    interface OrderListener {
        String name();

        void onPaid(String orderNo, long cents);
    }

    static class PointListener implements OrderListener {
        public String name() {
            return "积分服务";
        }

        public void onPaid(String orderNo, long cents) {
            System.out.printf("    [积分] %s 加 %d 分%n", orderNo, cents / 100);
        }
    }

    static class StockListener implements OrderListener {
        public String name() {
            return "库存服务";
        }

        public void onPaid(String orderNo, long cents) {
            System.out.printf("    [库存] %s 扣减实际库存%n", orderNo);
        }
    }

    /** 故意抛异常，验证隔离性 */
    static class BrokenListener implements OrderListener {
        public String name() {
            return "故障服务";
        }

        public void onPaid(String orderNo, long cents) {
            throw new IllegalStateException("下游超时");
        }
    }

    static class OrderEventBus {
        private final List<OrderListener> listeners = new ArrayList<>();

        OrderEventBus subscribe(OrderListener l) {
            listeners.add(l);
            return this;
        }

        /** 逐个 try-catch：一个失败不影响其他 */
        void publishPaid(String orderNo, long cents) {
            for (OrderListener l : new ArrayList<>(listeners)) {
                try {
                    l.onPaid(orderNo, cents);
                } catch (RuntimeException e) {
                    System.out.printf("    [告警] %s 处理失败：%s（已隔离，继续通知）%n",
                            l.name(), e.getMessage());
                }
            }
        }
    }

    /** ===== 责任链：请假审批 ===== */
    static abstract class Approver {
        private Approver next;

        Approver setNext(Approver n) {
            this.next = n;
            return n;
        }

        /** 模板方法：先看自己能否处理，不能则交给下一级 */
        final void approve(String name, int days) {
            if (canHandle(days)) {
                System.out.printf("    %-6s 批准 %s 请假 %d 天%n", role(), name, days);
            } else if (next != null) {
                next.approve(name, days);
            } else {
                System.out.printf("    无人可批：%s 请假 %d 天（超出所有审批权限）%n", name, days);
            }
        }

        abstract String role();

        abstract boolean canHandle(int days);
    }

    static class Leader extends Approver {
        String role() {
            return "组长";
        }

        boolean canHandle(int days) {
            return days <= 2;
        }
    }

    static class Manager extends Approver {
        String role() {
            return "经理";
        }

        boolean canHandle(int days) {
            return days <= 5;
        }
    }

    static class Director extends Approver {
        String role() {
            return "总监";
        }

        boolean canHandle(int days) {
            return days <= 10;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 观察者：一个事件通知多个订阅者 ===");
        OrderEventBus bus = new OrderEventBus()
                .subscribe(new PointListener())
                .subscribe(new BrokenListener())
                .subscribe(new StockListener());
        System.out.println("  支付成功 orderNo=SO20261117001 amount=199.00");
        bus.publishPaid("SO20261117001", 19900);
        System.out.println("  结论：故障服务抛异常，但库存服务仍收到通知");

        System.out.println();
        System.out.println("=== 2. 责任链：按金额/天数逐级上报 ===");
        Approver leader = new Leader();
        Approver manager = new Manager();
        Approver director = new Director();
        leader.setNext(manager).setNext(director);

        int[] requests = {1, 3, 7, 15};
        for (int d : requests) {
            System.out.println("  申请 " + d + " 天：");
            leader.approve("张三", d);
        }

        System.out.println();
        System.out.println("=== 观察者 vs 责任链 ===");
        System.out.println("  观察者：一对多广播，所有订阅者都收到，彼此独立");
        System.out.println("  责任链：一对一传递，找到处理者即停（或每级都过一遍）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `OrderEventBus` 增加"取消订阅"方法，说明为什么不能在 `publishPaid` 遍历过程中直接 `remove`。
 * 2. 把审批链改成"每级都要签字"（如财务 + 法务 + 总监），说明这与"找到即停"在实现上的差别。
 * 3. 用 `java.util.concurrent.CopyOnWriteArrayList` 替换 `ArrayList`，验证并发增删订阅者不再抛异常。
 * ============================================================
 */
