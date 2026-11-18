### 31-01 设计模式概览与六大原则

**知识点**
- 设计模式是"被反复验证过的、针对特定问题的解决方案模板"，它不是语法，而是一套**命名与沟通工具**：说出"这里用策略"比描述三百字更快。
- GoF 23 种按目的分三类：**创建型** 5 种（单例、工厂方法、抽象工厂、建造者、原型）、**结构型** 7 种（代理、装饰、适配器、外观、桥接、组合、享元）、**行为型** 11 种（策略、状态、观察者、责任链、模板方法、命令、迭代器、中介者、备忘录、访问者、解释器）。
- 六大原则：**单一职责 SRP**（一个类只有一个变化原因）、**开闭 OCP**（对扩展开放、对修改关闭）、**里氏替换 LSP**（子类可替换父类且不破坏语义）、**接口隔离 ISP**（小接口优于胖接口）、**依赖倒置 DIP**（依赖抽象不依赖实现）、**迪米特 LoD**（最少知道原则）。
- 原则是权衡工具，不是教条：给一个只有 3 行的类写"接口 + 工厂 + 配置"，抽象成本远高于收益，这叫**过度设计**。
- 认场景比背结构重要：看到"按类型 if/else 分支"→ 策略或工厂；看到"创建过程参数多且可选"→ 建造者；看到"不改源码要加行为"→ 代理或装饰；看到"一长串 if 逐级判断"→ 责任链。
- 依赖倒置的完整含义有两层：**高层模块不依赖低层模块，两者都依赖抽象**；**抽象不依赖细节，细节依赖抽象**。只抽了接口、却仍在业务类里 `new` 具体实现，倒置只做了一半。
- 里氏替换最容易被违反：子类抛父类没声明的受检异常、子类把 `add` 改成"不支持"、子类收窄参数范围，都会让"面向父类编程"的代码在运行期崩掉。
- 迪米特法则的实操判断：方法里出现 `a.getB().getC().doSomething()` 这种连续两跳，就说明"知道了太多"，应让 `a` 自己暴露一个方法。
- 易错：把"用上模式"当成绩（面试能背、代码更绕）；模式套错场景（用装饰器做权限校验、用单例装全局可变状态）；以为模式能替代测试。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class DesignPatternIntro {
    /** 变化点抽成接口：接口隔离 + 依赖倒置的落点 */
    interface Discount {
        String name();

        double apply(double price);
    }

    static class FullReduction implements Discount {
        public String name() {
            return "满100减20";
        }

        public double apply(double price) {
            return price >= 100 ? price - 20 : price;
        }
    }

    static class PercentOff implements Discount {
        public String name() {
            return "全场9折";
        }

        public double apply(double price) {
            return price * 0.9;
        }
    }

    /** 新增活动只加一个类，不改 Checkout —— 开闭原则 */
    static class NewUserCoupon implements Discount {
        public String name() {
            return "新人立减15";
        }

        public double apply(double price) {
            return Math.max(0, price - 15);
        }
    }

    /** 违规版：按类型分支，每加一种活动都要改这里，且要回归所有旧分支 */
    static class BadCheckout {
        double total(int type, double price) {
            if (type == 1) {
                return price >= 100 ? price - 20 : price;
            } else if (type == 2) {
                return price * 0.9;
            } else if (type == 3) {
                return Math.max(0, price - 15);
            }
            return price;
        }
    }

    /** 重构版：只依赖抽象，折扣可以任意组合、任意排序 */
    static class Checkout {
        private final List<Discount> chain = new ArrayList<>();

        Checkout add(Discount d) {
            chain.add(d);
            return this;
        }

        double total(double price) {
            double p = price;
            for (Discount d : chain) {
                double next = round2(d.apply(p));
                System.out.printf("    - %-10s %7.2f -> %7.2f%n", d.name(), p, next);
                p = next;
            }
            return p;
        }
    }

    static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    public static void main(String[] args) {
        System.out.println("=== 违规版：类型分支 ===");
        BadCheckout bad = new BadCheckout();
        System.out.printf("  type=1 100元 -> %.2f%n", bad.total(1, 100));
        System.out.printf("  type=2 100元 -> %.2f%n", bad.total(2, 100));
        System.out.println("  问题：想加 type=4，必须改 BadCheckout 源码，还得回归 type=1/2/3");

        System.out.println();
        System.out.println("=== 重构版：组合折扣链 ===");
        Checkout ck = new Checkout()
                .add(new FullReduction())
                .add(new PercentOff())
                .add(new NewUserCoupon());
        System.out.println("  原价 200.00，依次应用：");
        System.out.printf("  最终应付 %.2f%n", ck.total(200));

        System.out.println();
        System.out.println("=== 六大原则速查 ===");
        String[][] principles = {
                {"单一职责 SRP", "一个类只有一个变化原因", "UserService 里既校验又发邮件"},
                {"开闭 OCP", "对扩展开放、对修改关闭", "加活动要改 if/else"},
                {"里氏替换 LSP", "子类可替换父类不破坏语义", "子类抛父类没声明的异常"},
                {"接口隔离 ISP", "小接口优于胖接口", "一个 20 个方法的 UserDao"},
                {"依赖倒置 DIP", "依赖抽象不依赖实现", "业务类里 new MySQLDao()"},
                {"迪米特 LoD", "最少知道原则", "a.getB().getC().doIt()"}
        };
        for (String[] r : principles) {
            System.out.printf("  %-14s %-26s 反例：%s%n", r[0], r[1], r[2]);
        }
    }
}
```

**课后练习**
1. 给 `Checkout` 增加"第二件半价"活动，要求不改动任何已有类，说明这体现了哪条原则。
2. 找一个你写过的类，列出它的"变化原因"，判断是否违反单一职责，并给出拆分方案。
3. 举一个"子类替换父类后语义被破坏"的例子，说明为什么编译期发现不了。

### 31-02 单例模式：五种写法与两种破坏

**知识点**
- 单例要保证：**构造私有**、**自行创建唯一实例**、**对外提供统一访问点**。缺任何一条都不是单例。
- **饿汉式**：类加载即创建，天生线程安全，但可能创建了却从没用过（有初始化开销时浪费）。
- **懒汉式（无同步）**：`if (instance == null) instance = new X()` 在多线程下会创建多个实例，是典型的"看起来对、并发下错"。
- **双重检查锁 DCL**：`instance` 必须 `volatile`。因为 `new` 不是原子操作，分三步（分配内存、初始化、赋值引用），重排序会让另一个线程看到"引用非空但字段还是默认值"的半成品对象。
- **静态内部类**：利用类加载机制保证线程安全与懒加载，是"无锁懒加载"的最简写法；`Holder` 类只在第一次调用 `getInstance()` 时被初始化。
- **枚举**：`enum` 的构造由 JVM 保证只执行一次，且**反射无法创建枚举实例**、**反序列化不会产生新对象**，是《Effective Java》推荐写法。
- 两种破坏手段：**反射**（对私有构造 `setAccessible(true)` 后 `newInstance`）、**反序列化**（`readObject` 会新建对象，需写 `readResolve`）。
- 防御手段：枚举最彻底；普通类可在构造器里加"已创建则抛异常"的判断；反序列化场景补 `readResolve()` 返回单例。
- 易错：以为 `synchronized` 加在方法上就万事大吉（性能差且没解决反序列化）；DCL 漏写 `volatile`；把单例当"全局变量容器"存请求级数据，直接引发线程安全问题。

**示例代码**
```java
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SingletonPattern {
    /** 写法一：双重检查锁，instance 必须 volatile */
    static class DclSingleton {
        private static volatile DclSingleton instance;
        private final String tag;

        private DclSingleton() {
            tag = "DCL#" + System.identityHashCode(this);
        }

        static DclSingleton getInstance() {
            if (instance == null) {
                synchronized (DclSingleton.class) {
                    if (instance == null) {
                        instance = new DclSingleton();
                    }
                }
            }
            return instance;
        }

        String tag() {
            return tag;
        }
    }

    /** 写法二：静态内部类，无锁懒加载 */
    static class HolderSingleton {
        private HolderSingleton() {
        }

        private static class Holder {
            private static final HolderSingleton INSTANCE = new HolderSingleton();
        }

        static HolderSingleton getInstance() {
            return Holder.INSTANCE;
        }
    }

    /** 写法三：枚举，天然防反射、防反序列化 */
    enum EnumSingleton {
        INSTANCE;

        String tag() {
            return "ENUM#" + System.identityHashCode(this);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 200 线程并发取 DCL 实例 ===");
        Set<Integer> ids = Collections.newSetFromMap(new ConcurrentHashMap<>());
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(200);
        for (int i = 0; i < 200; i++) {
            pool.submit(() -> {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ids.add(System.identityHashCode(DclSingleton.getInstance()));
                done.countDown();
            });
        }
        gate.countDown();
        done.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        System.out.println("  不同实例个数 = " + ids.size() + "（正确结果应为 1）");
        System.out.println("  实例标识 = " + DclSingleton.getInstance().tag());

        System.out.println();
        System.out.println("=== 2. 静态内部类懒加载 ===");
        System.out.println("  调用前 Holder 未初始化，首次 getInstance 才加载 Holder 类");
        System.out.println("  a == b : " + (HolderSingleton.getInstance() == HolderSingleton.getInstance()));

        System.out.println();
        System.out.println("=== 3. 反射破坏 DCL 单例 ===");
        Constructor<DclSingleton> ctor = DclSingleton.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        DclSingleton hacked = ctor.newInstance();
        System.out.println("  反射创建成功，是否同一对象: " + (hacked == DclSingleton.getInstance()));
        System.out.println("  第二个实例 = " + hacked.tag());

        System.out.println();
        System.out.println("=== 4. 枚举单例防反射 ===");
        try {
            Constructor<EnumSingleton> ec = EnumSingleton.class.getDeclaredConstructor(String.class, int.class);
            ec.setAccessible(true);
            ec.newInstance("HACK", 1);
            System.out.println("  意外：枚举被反射破坏");
        } catch (Exception e) {
            System.out.println("  防御生效：" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println("  枚举实例 = " + EnumSingleton.INSTANCE.tag());

        System.out.println();
        System.out.println("=== 结论 ===");
        System.out.println("  普通类单例：反射/反序列化都能破坏，需额外防御");
        System.out.println("  枚举单例：JVM 层保证唯一，无需任何防御代码");
    }
}
```

**课后练习**
1. 把 `DclSingleton` 的 `volatile` 去掉，说明在什么重排序下会读到"半成品对象"，画出三步指令顺序。
2. 给 `DclSingleton` 实现 `readResolve()`，验证反序列化后仍是同一实例。
3. 用"构造器内判断已创建则抛异常"的方式给 DCL 单例加反射防御，并说明它为什么挡不住反序列化。

### 31-03 工厂模式：简单工厂、工厂方法、抽象工厂

**知识点**
- **简单工厂**：一个静态方法按 `type` 造对象。优点是调用方只认接口；缺点是新增产品要改工厂，违反开闭，且工厂会越来越胖。
- **工厂方法**：每种产品一个工厂类，`Factory` 接口只声明 `create()`，新增产品 = 新增"产品类 + 工厂类"，符合开闭。
- **抽象工厂**：一个工厂负责创建**一族**相关产品（如"支付渠道 + 对账文件 + 退款接口"）。换一家服务商时整族一起换，不会出现"支付宝支付配微信对账"的错配。
- 三者是递进关系：产品单一且稳定 → 简单工厂；产品会不断新增 → 工厂方法；产品成族出现 → 抽象工厂。
- 抽象工厂的代价：新增"一族里的新产品种类"（如再加"发票"）要改所有工厂接口，这是它的固有缺陷，属于"维度方向"的选择题。
- 工厂的职责边界：**只负责创建**，不要在工厂里塞业务逻辑（下单、扣库存），否则工厂变成"上帝类"。
- 与 DI 容器的关系：Spring 的 `BeanFactory` 本质就是"配置驱动的工厂"，把 `new` 从代码里搬到配置/注解，这也是依赖倒置的落地方式。
- 易错：工厂返回 `null` 表示"不支持"（调用方漏判就 NPE），应抛带上下文的异常或返回 `Optional`；把简单工厂当万能，`switch` 涨到 30 个分支。

**示例代码**
```java
public class FactoryPattern {
    /** 产品接口：支付渠道 */
    interface PayChannel {
        String name();

        boolean pay(long cents);
    }

    static class AliPay implements PayChannel {
        public String name() {
            return "支付宝";
        }

        public boolean pay(long cents) {
            System.out.printf("    [支付宝] 扣款 %.2f 元%n", cents / 100.0);
            return true;
        }
    }

    static class WeChatPay implements PayChannel {
        public String name() {
            return "微信支付";
        }

        public boolean pay(long cents) {
            System.out.printf("    [微信] 扣款 %.2f 元%n", cents / 100.0);
            return true;
        }
    }

    static class BankCardPay implements PayChannel {
        public String name() {
            return "银行卡";
        }

        public boolean pay(long cents) {
            System.out.printf("    [银行卡] 扣款 %.2f 元%n", cents / 100.0);
            return true;
        }
    }

    /** 简单工厂：调用方只认 key，不认实现类 */
    static class PayChannelFactory {
        static PayChannel create(String type) {
            switch (type) {
                case "alipay":
                    return new AliPay();
                case "wechat":
                    return new WeChatPay();
                case "bank":
                    return new BankCardPay();
                default:
                    throw new IllegalArgumentException("不支持的支付渠道: " + type);
            }
        }
    }

    /** 抽象工厂：一族产品 = 支付 + 对账 */
    interface Reconciler {
        String desc();
    }

    static class AliReconciler implements Reconciler {
        public String desc() {
            return "支付宝日对账单（CSV）";
        }
    }

    static class WeChatReconciler implements Reconciler {
        public String desc() {
            return "微信支付对账单（Excel）";
        }
    }

    interface PayKit {
        PayChannel pay();

        Reconciler reconciler();
    }

    static class AliKit implements PayKit {
        public PayChannel pay() {
            return new AliPay();
        }

        public Reconciler reconciler() {
            return new AliReconciler();
        }
    }

    static class WeChatKit implements PayKit {
        public PayChannel pay() {
            return new WeChatPay();
        }

        public Reconciler reconciler() {
            return new WeChatReconciler();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 简单工厂：按 key 造对象 ===");
        for (String type : new String[]{"alipay", "wechat", "bank"}) {
            PayChannel ch = PayChannelFactory.create(type);
            System.out.println("  渠道: " + ch.name());
            ch.pay(19900);
        }
        try {
            PayChannelFactory.create("unionpay");
        } catch (IllegalArgumentException e) {
            System.out.println("  未知渠道被拒绝: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 抽象工厂：整族一起换，不会错配 ===");
        String provider = "wechat";
        PayKit kit = "alipay".equals(provider) ? new AliKit() : new WeChatKit();
        System.out.println("  支付: " + kit.pay().name());
        kit.pay().pay(29900);
        System.out.println("  对账: " + kit.reconciler().desc());

        System.out.println();
        System.out.println("=== 三种工厂对比 ===");
        System.out.println("  简单工厂   1 个方法 + switch     新增产品要改工厂（违反开闭）");
        System.out.println("  工厂方法   1 产品 1 工厂类       新增产品只加类，符合开闭");
        System.out.println("  抽象工厂   1 工厂 1 产品族       换供应商整族切换，维度增加要改接口");
    }
}
```

**课后练习**
1. 给简单工厂加"云闪付"渠道，说明要改哪几处；再用工厂方法实现一遍，对比改动范围。
2. 给抽象工厂增加"退款"产品，列出所有需要改动的类，并说明这暴露了抽象工厂的什么缺陷。
3. 用 `Map<String, Supplier<PayChannel>>` 重写简单工厂，去掉 `switch`，说明这样改的收益与代价。

### 31-04 建造者模式：链式调用与不可变对象

**知识点**
- 建造者解决两类问题：**参数过多**（4 个以上可选参数，构造器调用变成"位置记忆游戏"）和**创建过程需要校验/分步**。
- 标准结构：目标类构造器私有 → 静态内部类 `Builder` 持有全部字段 → 每个 setter 返回 `this` 支持链式 → `build()` 里统一校验后调私有构造器。
- **不可变对象**是建造者的最佳搭档：字段全 `final`、集合用 `Collections.unmodifiableXxx` 包装、不提供 setter。线程安全且可以安全共享。
- `build()` 是唯一的校验入口：必填项缺失、数值越界、互斥参数冲突，都在这里抛 `IllegalStateException` 或 `IllegalArgumentException`，避免"对象造出来了但状态非法"。
- 与"重叠构造器"（telescoping constructor）对比：4 个可选参数就需要 16 个构造器组合，或者出现 `new User("张三", null, null, 0, false)` 这种看不懂的调用。
- 与 setter 模式对比：setter 允许"造到一半就使用"，且对象可变；建造者保证"要么完整、要么不存在"。
- 与工厂的区别：工厂关心"造哪一类"，建造者关心"怎么一步步造出这一个"。两者可以组合：工厂方法返回 `Builder`。
- 易错：`Builder` 里直接返回 `this` 却忘了 `build()` 做防御性拷贝（外部改集合会污染对象）；`build()` 不校验，把非法状态推到运行期；给不可变对象留了 setter。

**示例代码**
```java
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class BuilderPattern {
    /** 不可变对象：字段全 final，集合不可修改 */
    static final class HttpRequest {
        private final String url;
        private final String method;
        private final Map<String, String> headers;
        private final String body;
        private final int timeoutMs;
        private final int retry;

        private HttpRequest(Builder b) {
            this.url = b.url;
            this.method = b.method;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(b.headers));
            this.body = b.body;
            this.timeoutMs = b.timeoutMs;
            this.retry = b.retry;
        }

        static Builder builder(String url) {
            return new Builder(url);
        }

        @Override
        public String toString() {
            return method + " " + url + " timeout=" + timeoutMs + "ms retry=" + retry
                    + " headers=" + headers + (body == null ? "" : " body=" + body);
        }

        static final class Builder {
            private final String url;
            private String method = "GET";
            private final Map<String, String> headers = new LinkedHashMap<>();
            private String body;
            private int timeoutMs = 3000;
            private int retry = 0;

            private Builder(String url) {
                this.url = url;
            }

            Builder method(String m) {
                this.method = m;
                return this;
            }

            Builder header(String k, String v) {
                this.headers.put(k, v);
                return this;
            }

            Builder body(String b) {
                this.body = b;
                return this;
            }

            Builder timeoutMs(int t) {
                this.timeoutMs = t;
                return this;
            }

            Builder retry(int r) {
                this.retry = r;
                return this;
            }

            /** 唯一校验入口：不合法就不让对象存在 */
            HttpRequest build() {
                if (url == null || !url.startsWith("http")) {
                    throw new IllegalStateException("url 必须以 http 开头: " + url);
                }
                if (timeoutMs <= 0) {
                    throw new IllegalStateException("超时必须大于 0");
                }
                if (retry < 0 || retry > 5) {
                    throw new IllegalStateException("重试次数必须在 0~5 之间: " + retry);
                }
                if ("GET".equals(method) && body != null) {
                    throw new IllegalStateException("GET 请求不应带 body");
                }
                return new HttpRequest(this);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 建造者：只写关心的参数 ===");
        HttpRequest get = HttpRequest.builder("https://api.example.com/orders")
                .header("Authorization", "Bearer token")
                .header("Accept", "application/json")
                .timeoutMs(2000)
                .build();
        System.out.println("  " + get);

        HttpRequest post = HttpRequest.builder("https://api.example.com/orders")
                .method("POST")
                .header("Content-Type", "application/json")
                .body("{\"sku\":\"A1001\",\"qty\":2}")
                .timeoutMs(5000)
                .retry(2)
                .build();
        System.out.println("  " + post);

        System.out.println();
        System.out.println("=== build() 拦截非法状态 ===");
        String[][] cases = {
                {"url 不合法", "ftp://x"},
                {"超时非正", "https://a.com"}
        };
        try {
            HttpRequest.builder("ftp://x").build();
        } catch (IllegalStateException e) {
            System.out.println("  [" + cases[0][0] + "] " + e.getMessage());
        }
        try {
            HttpRequest.builder("https://a.com").timeoutMs(0).build();
        } catch (IllegalStateException e) {
            System.out.println("  [" + cases[1][0] + "] " + e.getMessage());
        }
        try {
            HttpRequest.builder("https://a.com").body("x").build();
        } catch (IllegalStateException e) {
            System.out.println("  [GET 带 body] " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 不可变性验证 ===");
        Map<String, String> ext = new LinkedHashMap<>();
        ext.put("X-Trace", "abc");
        HttpRequest r = HttpRequest.builder("https://a.com").build();
        try {
            r.headers.put("hack", "1");
        } catch (UnsupportedOperationException e) {
            System.out.println("  外部修改 headers 被拒绝: UnsupportedOperationException");
        }
        System.out.println("  结论：对象创建后状态不可变，可安全共享给多线程");
    }
}
```

**课后练习**
1. 用重叠构造器实现同样功能，数一数需要多少个构造方法，说明可读性差在哪。
2. 给 `HttpRequest` 增加 `List<String> tags` 字段，验证外部拿到列表后不能修改。
3. 把 `Builder` 改成"每步返回新 Builder"的不可变建造者，说明它相比可变 Builder 的优劣。

### 31-05 代理模式与装饰器模式

**知识点**
- **代理模式**：为目标对象提供一个替身，客户端通过替身访问目标。核心是"控制访问"，常见用途：权限校验、延迟加载、远程调用、日志埋点。
- **静态代理**：手写一个实现同接口的类，持有目标引用。缺点是一种接口就要写一个代理类，接口方法一多就是体力活。
- **JDK 动态代理**：`Proxy.newProxyInstance(loader, interfaces, handler)` 在运行期生成代理类，`InvocationHandler.invoke` 里统一织入逻辑。前提是**目标必须实现接口**。
- **CGLIB 代理**：通过继承生成子类，不要求接口，但无法代理 `final` 类/`final` 方法。Spring AOP 默认"有接口用 JDK，无接口用 CGLIB"。
- **装饰器模式**：同样实现同接口并持有目标，但目的是"**增强功能**"而不是"控制访问"，且可以层层叠加（`new BufferedInputStream(new FileInputStream(...))`）。
- 代理与装饰的判别口诀：**代理控制"能不能用"，装饰增强"用完什么样"**；代理通常由框架生成、客户端不感知，装饰由客户端显式组装。
- 装饰器的顺序有意义：`缓存(日志(实现))` 与 `日志(缓存(实现))` 的日志条数不同，前者只记录未命中，后者记录每次调用。
- 易错：动态代理里 `method.invoke(target, args)` 传错目标（传成 `proxy` 会无限递归）；`invoke` 里吞掉异常导致业务错误消失；装饰器忘了转发某个方法（编译能过，运行时行为缺失）。

**示例代码**
```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class ProxyDecorator {
    interface UserService {
        String findName(long id);
    }

    static class UserServiceImpl implements UserService {
        public String findName(long id) {
            sleep(60);
            return "用户" + id;
        }
    }

    /** 静态代理：只能代理 UserService，换接口要重写 */
    static class LoggingUserService implements UserService {
        private final UserService target;

        LoggingUserService(UserService target) {
            this.target = target;
        }

        public String findName(long id) {
            System.out.println("    [日志] 查询用户 id=" + id);
            return target.findName(id);
        }
    }

    /** 装饰器：加缓存，与目标同接口，可任意叠加 */
    static class CachedUserService implements UserService {
        private final UserService target;
        private final Map<Long, String> cache = new HashMap<>();

        CachedUserService(UserService target) {
            this.target = target;
        }

        public String findName(long id) {
            String v = cache.get(id);
            if (v == null) {
                v = target.findName(id);
                cache.put(id, v);
                System.out.println("    [缓存] 未命中，已写入 id=" + id);
            } else {
                System.out.println("    [缓存] 命中 id=" + id);
            }
            return v;
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 静态代理 + 装饰器叠加 ===");
        UserService svc = new CachedUserService(new LoggingUserService(new UserServiceImpl()));
        long t0 = System.nanoTime();
        System.out.println("  结果: " + svc.findName(1001));
        System.out.println("  结果: " + svc.findName(1001));
        System.out.printf("  两次调用总耗时 %.1f ms（第二次走缓存）%n", (System.nanoTime() - t0) / 1e6);

        System.out.println();
        System.out.println("=== 2. JDK 动态代理：不写代理类，统一加耗时统计 ===");
        final UserService target = new UserServiceImpl();
        UserService dynamic = (UserService) Proxy.newProxyInstance(
                UserService.class.getClassLoader(),
                new Class<?>[]{UserService.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] a) throws Throwable {
                        long start = System.nanoTime();
                        try {
                            return method.invoke(target, a);
                        } finally {
                            System.out.printf("    [耗时] %s.%s = %.1f ms%n",
                                    "UserService", method.getName(), (System.nanoTime() - start) / 1e6);
                        }
                    }
                });
        System.out.println("  代理类: " + dynamic.getClass().getName());
        System.out.println("  结果: " + dynamic.findName(2002));

        System.out.println();
        System.out.println("=== 3. 顺序不同的装饰器，行为不同 ===");
        UserService logOutside = new LoggingUserService(new CachedUserService(new UserServiceImpl()));
        System.out.println("  日志在外层（每次调用都记日志）：");
        logOutside.findName(3003);
        logOutside.findName(3003);
        UserService cacheOutside = new CachedUserService(new LoggingUserService(new UserServiceImpl()));
        System.out.println("  缓存在外层（第二次不进日志）：");
        cacheOutside.findName(3003);
        cacheOutside.findName(3003);

        System.out.println();
        System.out.println("=== 代理 vs 装饰 ===");
        System.out.println("  代理：控制访问（权限/延迟/远程），通常框架生成，客户端不感知");
        System.out.println("  装饰：增强功能（缓存/压缩/加密），客户端显式 new 出来层层包装");
    }
}
```

**课后练习**
1. 用动态代理实现"权限校验 + 耗时统计"两个增强，说明 `InvocationHandler` 里如何判断方法是否需要鉴权。
2. 把 `CachedUserService` 改成有容量上限的 LRU 缓存装饰器（可参考 `LinkedHashMap.removeEldestEntry`）。
3. 解释为什么 `method.invoke(proxy, args)` 会导致无限递归，画出调用栈。

### 31-06 适配器模式与外观模式

**知识点**
- **适配器**：把一个类的接口转换成客户端期望的另一个接口，解决"接口不兼容但功能要复用"的问题。典型场景：接入第三方 SDK、老系统改造、替换中间件。
- 两种写法：**类适配器**（继承被适配者 + 实现目标接口，受单继承限制）、**对象适配器**（组合被适配者，推荐，能适配多个实现）。
- 适配器只做"翻译"：参数格式转换、方法名映射、返回值包装。**不要在里面写业务逻辑**，否则它会变成"隐形业务层"，最难维护。
- 双向适配器：同时实现两个接口，让两边互相调用，用于渐进式替换老系统。
- **外观（Facade）**：为一组复杂子系统提供统一的高层接口，降低调用方的认知负担。它不封装子系统本身，子系统仍可被直接使用。
- 外观 vs 适配器的区别：适配器改变**接口形状**（一个变另一个），外观**简化调用流程**（多个变一个）；外观不改变子系统的接口。
- 外观的边界：只做"编排 + 顺序 + 异常归一"，不要在外观里塞业务规则，否则又造出一个上帝类。
- 易错：适配器里抛原始异常（调用方无法统一处理），应转成自己的异常体系；外观把子系统的返回 `null` 直接透出；类适配器因继承把被适配者的所有方法都暴露出去。

**示例代码**
```java
public class AdapterFacade {
    /** 我方统一接口 */
    interface SmsSender {
        boolean send(String phone, String text);
    }

    /** 第三方老接口：方法名、参数、返回值都不同 */
    static class LegacySmsApi {
        int sendMessage(String[] mobiles, String content, int priority) {
            System.out.printf("    [Legacy] 批量发送 %d 条，优先级 %d，内容：%s%n",
                    mobiles.length, priority, content);
            return mobiles.length;
        }
    }

    /** 对象适配器：组合 + 翻译，只做接口转换 */
    static class LegacySmsAdapter implements SmsSender {
        private final LegacySmsApi api = new LegacySmsApi();

        public boolean send(String phone, String text) {
            if (phone == null || phone.length() != 11) {
                return false;
            }
            int sent = api.sendMessage(new String[]{phone}, text, 1);
            return sent == 1;
        }
    }

    /** 另一家服务商：接口长得像，但参数语义不同（毫秒 vs 秒） */
    static class CloudSmsApi {
        boolean push(String mobile, String msg, long delaySeconds) {
            System.out.printf("    [Cloud] 发送到 %s，延迟 %d 秒：%s%n", mobile, delaySeconds, msg);
            return true;
        }
    }

    static class CloudSmsAdapter implements SmsSender {
        private final CloudSmsApi api = new CloudSmsApi();

        public boolean send(String phone, String text) {
            return api.push(phone, text, 0);
        }
    }

    /** 外观：把下单涉及的 4 个子系统收成一个方法 */
    static class InventoryService {
        boolean lock(String sku, int qty) {
            System.out.println("    [库存] 锁定 " + sku + " x" + qty);
            return true;
        }
    }

    static class PromotionService {
        long discount(String sku, long cents) {
            System.out.println("    [优惠] 计算折扣");
            return cents >= 10000 ? 1500 : 0;
        }
    }

    static class PaymentService {
        boolean pay(long cents) {
            System.out.println("    [支付] 扣款 " + cents / 100.0 + " 元");
            return true;
        }
    }

    static class OrderFacade {
        private final InventoryService inventory = new InventoryService();
        private final PromotionService promotion = new PromotionService();
        private final PaymentService payment = new PaymentService();
        private final SmsSender sms = new LegacySmsAdapter();

        /** 调用方只需这一个方法，不用知道 4 个子系统的顺序 */
        boolean placeOrder(String phone, String sku, int qty, long unitCents) {
            if (!inventory.lock(sku, qty)) {
                return false;
            }
            long total = unitCents * qty;
            long pay = total - promotion.discount(sku, total);
            if (!payment.pay(pay)) {
                return false;
            }
            sms.send(phone, "下单成功，实付 " + pay / 100.0 + " 元");
            return true;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 适配器：统一两种第三方短信 ===");
        SmsSender[] senders = {new LegacySmsAdapter(), new CloudSmsAdapter()};
        for (SmsSender s : senders) {
            System.out.println("  使用 " + s.getClass().getSimpleName() + "：");
            System.out.println("    结果 = " + s.send("13800138000", "您的验证码是 8888"));
        }
        System.out.println("  非法手机号：");
        System.out.println("    结果 = " + senders[0].send("123", "x"));

        System.out.println();
        System.out.println("=== 2. 外观：一次调用编排 4 个子系统 ===");
        OrderFacade facade = new OrderFacade();
        boolean ok = facade.placeOrder("13800138000", "SKU-A1001", 2, 6990);
        System.out.println("  下单结果 = " + ok);

        System.out.println();
        System.out.println("=== 适配器 vs 外观 ===");
        System.out.println("  适配器：接口形状转换（一个接口变另一个），只翻译不加工");
        System.out.println("  外观：调用流程简化（多个变一个），只编排不封装");
    }
}
```

**课后练习**
1. 写一个类适配器版本的 `LegacySmsAdapter`（继承 `LegacySmsApi`），说明它为什么把父类所有方法都暴露了。
2. 给 `OrderFacade` 增加"取消订单"流程（反向：退款 → 解锁库存 → 发通知），说明编排逻辑应该放在哪一层。
3. 找一个真实场景（如日志框架 SLF4J 适配 Log4j），说明适配器在其中扮演的角色。
