### 31-07 策略模式与状态模式

**知识点**
- **策略模式**：把一组可互换的算法各自封装成类，运行时选择其一。它把"选择逻辑"和"算法实现"分开，消灭按类型铺开的长 `if/else`。
- 策略的组成：`Strategy` 接口（算法契约）+ 多个 `ConcreteStrategy`（实现）+ `Context`（持有策略并委托调用）。
- 策略的选择方式：调用方传入、`Map` 注册表按 key 取、Spring 注入 `List<Strategy>` 后按 `supports()` 匹配。注册表方式最常用。
- 策略的局限：客户端必须知道有哪些策略（要传 key）；策略类数量会膨胀；策略之间无法自然共享中间状态。
- **状态模式**：对象在内部状态改变时改变它的行为，看起来像"换了类"。与策略的结构几乎一样，但**意图相反**：策略由外部选择、状态由内部流转。
- 状态机的关键：**状态迁移表**比 `if` 嵌套可靠得多。把 `(当前状态, 事件) -> 下一状态` 写成表，新增状态只需加行，且能一眼看出所有合法路径。
- 状态模式 vs 状态枚举 + switch：状态少（3 个以内）、迁移简单时，枚举 + switch 更直观；状态多、每个状态行为差异大时，状态模式更好。
- 两者最容易被混淆的判断：**谁决定用哪个**。外部传入 = 策略；由对象自己根据事件切换 = 状态。
- 易错：策略里访问 `Context` 的可变字段造成耦合；状态机允许非法迁移（如"已取消"还能"发货"）；把状态和业务状态（数据库 status 字段）搞成两套不一致的定义。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class StrategyState {
    /** ===== 策略：运费计算 ===== */
    interface ShippingStrategy {
        String key();

        long fee(long weightGram, long amountCents);
    }

    static class FlatStrategy implements ShippingStrategy {
        public String key() {
            return "flat";
        }

        public long fee(long weightGram, long amountCents) {
            return 800;
        }
    }

    static class WeightStrategy implements ShippingStrategy {
        public String key() {
            return "weight";
        }

        public long fee(long weightGram, long amountCents) {
            long kg = (weightGram + 999) / 1000;
            return 500 + kg * 200;
        }
    }

    static class FreeOverStrategy implements ShippingStrategy {
        public String key() {
            return "freeOver";
        }

        public long fee(long weightGram, long amountCents) {
            return amountCents >= 9900 ? 0 : 1000;
        }
    }

    /** 注册表：新增策略只加类 + 一行注册 */
    static class ShippingCalculator {
        private final Map<String, ShippingStrategy> registry = new LinkedHashMap<>();

        ShippingCalculator register(ShippingStrategy s) {
            registry.put(s.key(), s);
            return this;
        }

        long calc(String key, long weightGram, long amountCents) {
            ShippingStrategy s = registry.get(key);
            if (s == null) {
                throw new IllegalArgumentException("未知运费策略: " + key);
            }
            return s.fee(weightGram, amountCents);
        }
    }

    /** ===== 状态：订单状态机 ===== */
    enum OrderState {
        CREATED, PAID, SHIPPED, FINISHED, CANCELLED
    }

    enum OrderEvent {
        PAY, SHIP, CONFIRM, CANCEL
    }

    static class OrderStateMachine {
        /** (状态, 事件) -> 下一状态；没有条目 = 非法迁移 */
        private static final Map<String, OrderState> TABLE = new LinkedHashMap<>();

        static {
            put(OrderState.CREATED, OrderEvent.PAY, OrderState.PAID);
            put(OrderState.CREATED, OrderEvent.CANCEL, OrderState.CANCELLED);
            put(OrderState.PAID, OrderEvent.SHIP, OrderState.SHIPPED);
            put(OrderState.PAID, OrderEvent.CANCEL, OrderState.CANCELLED);
            put(OrderState.SHIPPED, OrderEvent.CONFIRM, OrderState.FINISHED);
        }

        static void put(OrderState s, OrderEvent e, OrderState next) {
            TABLE.put(s + "|" + e, next);
        }

        private OrderState state = OrderState.CREATED;

        OrderState state() {
            return state;
        }

        boolean fire(OrderEvent e) {
            OrderState next = TABLE.get(state + "|" + e);
            if (next == null) {
                System.out.printf("    非法迁移：%s --%s--> ?%n", state, e);
                return false;
            }
            System.out.printf("    %s --%s--> %s%n", state, e, next);
            state = next;
            return true;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 策略：同一份数据，三种运费算法 ===");
        ShippingCalculator calc = new ShippingCalculator()
                .register(new FlatStrategy())
                .register(new WeightStrategy())
                .register(new FreeOverStrategy());
        long[][] cases = {{500, 5000}, {2500, 5000}, {2500, 12000}};
        for (long[] c : cases) {
            System.out.printf("  重量 %4dg 金额 %6.2f 元:%n", c[0], c[1] / 100.0);
            for (String key : new String[]{"flat", "weight", "freeOver"}) {
                System.out.printf("    %-9s = %5.2f 元%n", key, calc.calc(key, c[0], c[1]) / 100.0);
            }
        }
        try {
            calc.calc("express", 100, 100);
        } catch (IllegalArgumentException e) {
            System.out.println("  " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 2. 状态：合法路径走得通 ===");
        OrderStateMachine m = new OrderStateMachine();
        m.fire(OrderEvent.PAY);
        m.fire(OrderEvent.SHIP);
        m.fire(OrderEvent.CONFIRM);
        System.out.println("  最终状态 = " + m.state());

        System.out.println();
        System.out.println("=== 3. 状态：非法路径被拒绝 ===");
        OrderStateMachine m2 = new OrderStateMachine();
        m2.fire(OrderEvent.SHIP);
        m2.fire(OrderEvent.CONFIRM);
        m2.fire(OrderEvent.CANCEL);
        System.out.println("  状态未被污染 = " + m2.state());

        System.out.println();
        System.out.println("=== 策略 vs 状态 ===");
        System.out.println("  策略：外部选择算法，算法之间互不感知，用哪个由调用方决定");
        System.out.println("  状态：内部驱动迁移，每个状态知道能接受哪些事件，用哪个由对象自己决定");
    }
}
```

**课后练习**
1. 给 `ShippingCalculator` 增加"按地区阶梯计价"策略，要求不改 `ShippingCalculator`，说明这依赖哪条原则。
2. 在状态迁移表里补上"已发货可退货"（`SHIPPED --REFUND--> CANCELLED`），说明加一行会影响哪些已有路径。
3. 用状态枚举 + switch 重写 `OrderStateMachine`，对比两种写法在"新增状态"时的改动量。

### 31-08 观察者模式与责任链模式

**知识点**
- **观察者**：主题维护观察者列表，状态变化时通知所有观察者。这就是"发布-订阅"在单机内的形态，也是事件驱动架构的起点。
- 两种实现：**推**（通知时把数据传过去，观察者无需回查）和**拉**（只通知"变了"，观察者自己来取）。推更简单，拉更灵活。
- 关键工程问题：**观察者抛异常不能影响其他观察者**。必须逐个 `try-catch`，否则第一个失败的订阅者会让后面全部收不到通知。
- 另一个坑：**通知过程中增删观察者**会引发 `ConcurrentModificationException`，应遍历快照（`new ArrayList<>(list)`）或使用 `CopyOnWriteArrayList`。
- 同步通知 vs 异步通知：同步简单但会拉长主流程耗时；异步要处理顺序、丢失与线程池耗尽。生产上常用"同步落库 + 异步发事件"。
- **责任链**：把处理者串成链，请求沿链传递，直到某个处理者处理或链结束。适合"多级审批""多条件过滤""网关过滤器"。
- 链的两种语义：**找到即停**（如审批、异常处理）和**每级都执行**（如过滤器、拦截器）。设计时必须明确是哪一种。
- 责任链的风险：链太长导致栈深（递归实现）或调试困难；某个处理者忘记调用 `next` 导致请求"静默消失"。
- 易错：观察者里做重活拖慢主流程；责任链没有终止条件造成无限循环；链的顺序硬编码在业务代码里。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

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
```

**课后练习**
1. 给 `OrderEventBus` 增加"取消订阅"方法，说明为什么不能在 `publishPaid` 遍历过程中直接 `remove`。
2. 把审批链改成"每级都要签字"（如财务 + 法务 + 总监），说明这与"找到即停"在实现上的差别。
3. 用 `java.util.concurrent.CopyOnWriteArrayList` 替换 `ArrayList`，验证并发增删订阅者不再抛异常。

### 31-09 模板方法与回调

**知识点**
- **模板方法**：父类定义算法骨架（`final` 方法），把可变步骤留给子类实现（`abstract` 或可覆盖的钩子）。它把"流程"和"细节"分开。
- 三个角色：**模板方法**（`final`，定顺序）、**抽象方法**（必须实现）、**钩子方法**（`protected` 空实现或默认实现，子类可选覆盖）。
- 模板方法常与"钩子"配合控制流程：`boolean needValidate()` 默认返回 `true`，子类可返回 `false` 跳过某步。
- 里氏替换在这里的体现：模板方法必须对所有子类都成立，所以父类里不能假设"子类一定如何"，只能依赖抽象步骤。
- **回调**：把一段逻辑作为参数传进去，由被调用方在合适时机执行。Java 里用函数式接口（`Runnable`、`Supplier`、`Consumer`、自定义 `@FunctionalInterface`）表达。
- 回调 vs 模板方法的区别：模板方法靠**继承**，编译期绑定，一个类只能有一个父类；回调靠**组合**，运行期传入，可以任意组合多个。
- 现代 Java 更推荐回调（组合优于继承）：`list.forEach(...)`、`Optional.map(...)`、`CompletableFuture.thenApply(...)` 都是回调思想。
- 模板方法的常见误用：父类塞了太多具体逻辑导致子类难以覆盖；钩子方法命名不清（`doSomething` 到底是必须还是可选）。
- 易错：模板方法忘了 `final`，子类把整个流程改掉；回调里抛异常无人处理；回调中捕获了外部可变变量造成意外共享。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class TemplateCallback {
    /** ===== 模板方法：数据导入骨架 ===== */
    static abstract class DataImporter {
        /** 骨架：顺序固定，子类不能改 */
        final int run() {
            System.out.println("    [骨架] 1. 打开数据源");
            open();
            System.out.println("    [骨架] 2. 读取原始行");
            List<String> raw = read();
            System.out.println("    [骨架] 3. 校验" + (needValidate() ? "（启用）" : "（跳过）"));
            List<String> valid = new ArrayList<>();
            for (String line : raw) {
                if (!needValidate() || validate(line)) {
                    valid.add(line);
                } else {
                    System.out.println("      - 丢弃非法行: " + line);
                }
            }
            System.out.println("    [骨架] 4. 转换");
            List<String> rows = new ArrayList<>();
            for (String line : valid) {
                rows.add(convert(line));
            }
            System.out.println("    [骨架] 5. 写入 " + rows.size() + " 条");
            write(rows);
            close();
            return rows.size();
        }

        abstract void open();

        abstract List<String> read();

        abstract String convert(String raw);

        abstract void write(List<String> rows);

        abstract void close();

        /** 钩子：默认校验，子类可关掉 */
        boolean needValidate() {
            return true;
        }

        boolean validate(String line) {
            return line != null && !line.trim().isEmpty();
        }
    }

    static class CsvImporter extends DataImporter {
        void open() {
            System.out.println("      CsvImporter.open() -> 读取本地 csv");
        }

        List<String> read() {
            return List.of("A1001,键盘,199", "", "A1002,鼠标,89", "  ");
        }

        String convert(String raw) {
            String[] p = raw.split(",");
            return p[0] + "|" + p[1] + "|" + (Long.parseLong(p[2]) * 100) + "分";
        }

        void write(List<String> rows) {
            for (String r : rows) {
                System.out.println("      -> " + r);
            }
        }

        void close() {
            System.out.println("      CsvImporter.close()");
        }
    }

    /** 子类关掉校验：数据源已保证干净 */
    static class TrustedImporter extends DataImporter {
        void open() {
            System.out.println("      TrustedImporter.open() -> 内部可信数据源");
        }

        List<String> read() {
            return List.of("B2001,显示器,1299");
        }

        String convert(String raw) {
            return raw.toUpperCase();
        }

        void write(List<String> rows) {
            System.out.println("      -> " + rows);
        }

        void close() {
            System.out.println("      TrustedImporter.close()");
        }

        boolean needValidate() {
            return false;
        }
    }

    /** ===== 回调：把"做什么"作为参数传进去 ===== */
    static class RetryTemplate {
        /** 重试骨架，业务逻辑用 Supplier 传进来 */
        static <T> T execute(String name, int maxTry, long backoffMs, Supplier<T> action) {
            RuntimeException last = null;
            for (int i = 1; i <= maxTry; i++) {
                try {
                    System.out.printf("    第 %d 次执行 %s%n", i, name);
                    return action.get();
                } catch (RuntimeException e) {
                    last = e;
                    System.out.println("      失败：" + e.getMessage());
                    if (i < maxTry) {
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
            throw new IllegalStateException(name + " 重试 " + maxTry + " 次仍失败", last);
        }

        /** 成功回调 + 失败回调 */
        static <T> T execute(String name, Supplier<T> action,
                             Consumer<T> onSuccess, Consumer<RuntimeException> onFailure) {
            try {
                T r = action.get();
                onSuccess.accept(r);
                return r;
            } catch (RuntimeException e) {
                onFailure.accept(e);
                throw e;
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 模板方法：同一骨架，两种数据源 ===");
        System.out.println("  CSV 导入（默认校验）：");
        int n1 = new CsvImporter().run();
        System.out.println("  导入条数 = " + n1);

        System.out.println();
        System.out.println("  Trusted 导入（钩子关闭校验）：");
        int n2 = new TrustedImporter().run();
        System.out.println("  导入条数 = " + n2);

        System.out.println();
        System.out.println("=== 2. 回调：重试骨架 + 业务逻辑外传 ===");
        int[] counter = {0};
        String result = RetryTemplate.execute("查询库存", 3, 10, () -> {
            counter[0]++;
            if (counter[0] < 3) {
                throw new RuntimeException("连接超时 #" + counter[0]);
            }
            return "库存=42";
        });
        System.out.println("  最终结果 = " + result);

        System.out.println();
        System.out.println("=== 3. 回调：成功/失败各走一条路 ===");
        Function<Integer, Integer> doubleIt = x -> x * 2;
        int[] trace = {0};
        Integer v = RetryTemplate.execute("计算", () -> doubleIt.apply(21),
                r -> System.out.println("    成功回调：结果 " + r),
                e -> System.out.println("    失败回调：" + e.getMessage()));
        System.out.println("  返回值 = " + v);

        System.out.println();
        System.out.println("=== 模板方法 vs 回调 ===");
        System.out.println("  模板方法：继承，编译期绑定，一个子类一套流程");
        System.out.println("  回调：组合，运行期传入，同一骨架可配任意逻辑");
    }
}
```

**课后练习**
1. 给 `DataImporter` 增加一个 `afterWrite()` 钩子（默认空实现），让 `CsvImporter` 覆盖它打印统计信息。
2. 用 `RetryTemplate.execute` 实现"指数退避"重试（100ms、200ms、400ms），说明为什么实际项目要加随机抖动。
3. 把 `DataImporter` 的继承结构改成"骨架 + 回调"（传 `Function`/`Consumer`），对比两者在扩展性上的差异。

### 31-10 代码坏味道与重构手法

**知识点**
- **坏味道（code smell）** 是"能跑但会变难改"的信号，不是 bug。识别它是重构的起点，比记住 20 种手法更重要。
- 常见坏味道：**过长方法**（超过一屏）、**过大类**（几百行、职责混杂）、**长参数列表**（4 个以上）、**重复代码**（复制粘贴改一处漏一处）、**发散式变化**（一个类因多种原因被改）、**霰弹式修改**（一个改动要改十几个类）、**基本类型偏执**（用 `String` 表示金额、手机号）、**数据泥团**（三四个字段总是同时出现）、**switch 语句**（按类型分支，新增类型要改多处）。
- 对应手法：**提炼方法**（Extract Method）、**内联**（Inline）、**引入参数对象**（Introduce Parameter Object）、**以多态取代条件**（Replace Conditional with Polymorphism）、**搬移方法/字段**（Move Method/Field）、**提炼类**（Extract Class）、**以值对象取代基本类型**（Replace Primitive with Value Object）、**以查询取代临时变量**（Replace Temp with Query）。
- 重构的纪律：**小步 + 每步可编译 + 每步跑测试**。一次改 500 行再调试，等于重新开发。
- 重构与重写的区别：重构是"外部行为不变、内部结构变好"；重写是"推倒重来"。能重构就别重写，重写会丢光历史 bug 修复。
- 重构的时机：加新功能前（先让改动变容易）、修 bug 时（顺手清理）、代码评审时。**不要**在发布前夜重构。
- 度量视角：圈复杂度（分支数）、方法行数、类行数、重复率、扇入扇出。工具能算，但阈值要按团队约定。
- 易错：重构时顺手改行为（"既然改到这里了，顺便加个功能"）——这让测试无法判断是重构引入的问题还是新功能的问题；没有测试就大改；把"重构"当"重命名"。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
```

**课后练习**
1. 把 `BadOrderService` 里的渠道附加费从"硬编码 200"改成"可配置"，说明这属于哪种手法。
2. 用 `record` 定义 `Phone` 值对象，在构造时校验 11 位，替换掉所有 `String phone` 参数。
3. 列出你最近写的一个超过 80 行的方法，按"提炼方法"拆成 4 个以内的小方法，并说明如何验证行为未变。

### 31-11 综合案例：优惠计算引擎重构

**知识点**
- 综合运用：**策略**（多种优惠算法）+ **责任链**（优惠叠加顺序与互斥）+ **建造者**（构造复杂促销规则）+ **值对象**（金额）+ **模板方法**（统一的计算骨架）。
- 优惠计算的真实难点不在"算"，而在**规则组合**：满减、折扣、券、会员价可能互斥、可能叠加、可能有先后顺序，顺序不同结果不同。
- 工程做法：把每个优惠抽象成 `Promotion`，带 `priority`（顺序）、`stackable`（能否叠加）、`canApply`（适用条件）、`apply`（计算结果）。
- 计算骨架固定：按优先级排序 → 依次判断是否适用 → 判断是否与已应用规则冲突 → 应用 → 记录明细。这样"加一种优惠"只是加一个类。
- **必须输出明细**：只给最终价，出问题时无法解释"为什么是 87.3 元"。生产系统里优惠明细是客诉排查的第一手材料。
- 金额一律用 `long` 存分，避免 `double` 精度问题；四舍五入规则要显式（`Math.round` 还是"分以下抹零"），且全链路统一。
- 边界要显式处理：优惠后不能为负、优惠总额不能超过商品总额、券不可与折扣同享、过期券不可用。
- 易错：用 `double` 算钱；顺序硬编码在 `if` 里；优惠明细不落库；叠加规则写在注释里而不是代码里。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PromotionEngineCase {
    /** 值对象：金额（分） */
    record Money(long cents) {
        static Money ofYuan(double y) {
            return new Money(Math.round(y * 100));
        }

        Money minus(Money o) {
            return new Money(Math.max(0, cents - o.cents));
        }

        Money percentOff(int pct) {
            return new Money(cents - Math.round(cents * pct / 100.0));
        }

        @Override
        public String toString() {
            return String.format("%.2f", cents / 100.0);
        }
    }

    /** 促销规则：策略 + 元数据 */
    interface Promotion {
        String name();

        int priority();

        /** 能否与已应用的规则叠加 */
        boolean stackable();

        boolean canApply(Money current, Cart cart);

        Money apply(Money current, Cart cart);
    }

    /** 购物车上下文 */
    static class Cart {
        final Money original;
        final int memberLevel;
        final boolean hasCoupon;
        final boolean couponExpired;

        Cart(Money original, int memberLevel, boolean hasCoupon, boolean couponExpired) {
            this.original = original;
            this.memberLevel = memberLevel;
            this.hasCoupon = hasCoupon;
            this.couponExpired = couponExpired;
        }
    }

    static class MemberDiscount implements Promotion {
        public String name() {
            return "会员折扣";
        }

        public int priority() {
            return 10;
        }

        public boolean stackable() {
            return true;
        }

        public boolean canApply(Money cur, Cart c) {
            return c.memberLevel >= 2;
        }

        public Money apply(Money cur, Cart c) {
            return cur.percentOff(c.memberLevel >= 3 ? 15 : 8);
        }
    }

    static class FullReduction implements Promotion {
        public String name() {
            return "满300减50";
        }

        public int priority() {
            return 20;
        }

        public boolean stackable() {
            return true;
        }

        public boolean canApply(Money cur, Cart c) {
            return cur.cents() >= 30000;
        }

        public Money apply(Money cur, Cart c) {
            return cur.minus(Money.ofYuan(50));
        }
    }

    static class Coupon implements Promotion {
        public String name() {
            return "满200减30券";
        }

        public int priority() {
            return 30;
        }

        /** 券不可与会员折扣同享：这是业务规则，写在代码里而不是注释里 */
        public boolean stackable() {
            return false;
        }

        public boolean canApply(Money cur, Cart c) {
            return c.hasCoupon && !c.couponExpired && cur.cents() >= 20000;
        }

        public Money apply(Money cur, Cart c) {
            return cur.minus(Money.ofYuan(30));
        }
    }

    static class ExpiredCoupon implements Promotion {
        public String name() {
            return "过期券（不可用）";
        }

        public int priority() {
            return 30;
        }

        public boolean stackable() {
            return false;
        }

        public boolean canApply(Money cur, Cart c) {
            return c.hasCoupon && c.couponExpired;
        }

        public Money apply(Money cur, Cart c) {
            return cur;
        }
    }

    /** 计算明细行 */
    record Line(String promotion, Money before, Money after, String note) {
    }

    /** 建造者：组装引擎 */
    static class EngineBuilder {
        private final List<Promotion> list = new ArrayList<>();

        EngineBuilder add(Promotion p) {
            list.add(p);
            return this;
        }

        PromotionEngine build() {
            list.sort(Comparator.comparingInt(Promotion::priority));
            return new PromotionEngine(list);
        }
    }

    /** 模板骨架：排序 -> 适用 -> 冲突 -> 应用 -> 记账 */
    static class PromotionEngine {
        private final List<Promotion> promotions;

        PromotionEngine(List<Promotion> promotions) {
            this.promotions = promotions;
        }

        static EngineBuilder builder() {
            return new EngineBuilder();
        }

        List<Line> run(Cart cart) {
            List<Line> lines = new ArrayList<>();
            Money cur = cart.original;
            boolean usedNonStackable = false;

            for (Promotion p : promotions) {
                if (!p.canApply(cur, cart)) {
                    lines.add(new Line(p.name(), cur, cur, "条件不满足"));
                    continue;
                }
                if (usedNonStackable && p.stackable()) {
                    lines.add(new Line(p.name(), cur, cur, "与不可叠加规则冲突"));
                    continue;
                }
                if (!p.stackable() && usedNonStackable) {
                    lines.add(new Line(p.name(), cur, cur, "已有不可叠加规则"));
                    continue;
                }
                Money next = p.apply(cur, cart);
                String note = next.cents() == cur.cents() ? "无变化" : "已应用";
                lines.add(new Line(p.name(), cur, next, note));
                cur = next;
                if (!p.stackable()) {
                    usedNonStackable = true;
                }
            }
            return lines;
        }

        Money total(Cart cart) {
            List<Line> lines = run(cart);
            return lines.isEmpty() ? cart.original : lines.get(lines.size() - 1).after();
        }
    }

    public static void main(String[] args) {
        PromotionEngine engine = PromotionEngine.builder()
                .add(new MemberDiscount())
                .add(new FullReduction())
                .add(new Coupon())
                .add(new ExpiredCoupon())
                .build();

        System.out.println("=== 场景 1：会员3级 + 满300 + 有效券 ===");
        Cart c1 = new Cart(Money.ofYuan(400), 3, true, false);
        print(engine, c1);

        System.out.println();
        System.out.println("=== 场景 2：普通会员 + 金额不足 + 过期券 ===");
        Cart c2 = new Cart(Money.ofYuan(150), 1, true, true);
        print(engine, c2);

        System.out.println();
        System.out.println("=== 场景 3：会员2级 + 满300 + 无券 ===");
        Cart c3 = new Cart(Money.ofYuan(320), 2, false, false);
        print(engine, c3);

        System.out.println();
        System.out.println("=== 场景 4：大额 + 券与会员折扣的叠加边界 ===");
        Cart c4 = new Cart(Money.ofYuan(1000), 3, true, false);
        print(engine, c4);

        System.out.println();
        System.out.println("=== 设计要点 ===");
        System.out.println("  1. 加一种优惠 = 加一个类 + 一次 register，不改引擎");
        System.out.println("  2. 顺序由 priority 决定，不靠 if 的书写顺序");
        System.out.println("  3. 叠加/互斥是数据（stackable），不是注释");
        System.out.println("  4. 金额全程用分（long），最后才格式化");
        System.out.println("  5. 每一步都产出明细，客诉可解释");
    }

    static void print(PromotionEngine engine, Cart cart) {
        System.out.printf("  原价 %s 元（会员%d级，券=%s%s）%n",
                cart.original, cart.memberLevel,
                cart.hasCoupon ? "有" : "无",
                cart.couponExpired ? "/已过期" : "");
        for (Line l : engine.run(cart)) {
            System.out.printf("    %-12s %8s -> %8s  %s%n",
                    l.promotion(), l.before(), l.after(), l.note());
        }
        System.out.printf("  应付 %s 元%n", engine.total(cart));
    }
}
```

**课后练习**
1. 增加"限时秒杀价"优惠（优先级最高、不可叠加），验证它与其他规则的冲突处理是否正确。
2. 给 `run` 增加"优惠总额不超过商品总额"的保护，说明应该在哪一层做这个校验。
3. 把 `stackable` 扩展成"可叠加组"（同组互斥、不同组可叠加），说明数据结构要怎么改。
4. 为这个引擎写 5 个测试用例（含边界：0 元、优惠超过总价、券刚过期），说明哪些是等价类、哪些是边界值。
