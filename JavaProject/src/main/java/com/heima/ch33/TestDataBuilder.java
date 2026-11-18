/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-07
 * 课名：测试数据构建：ObjectMother、Builder 与夹具清理
 * ------------------------------------------------------------
 * 知识点：
 *   - 测试代码里最烦的两件事：**造一个合法对象要写 10 行**、**用例之间数据互相污染**。前者用 ObjectMother/Builder 解决，后者用夹具（fixture）清理解决。
 *   - **ObjectMother**：一组静态工厂方法，返回"典型场景的现成对象"。`Users.aVipUser()`、`Orders.paidOrder()`。优点是语义清晰、一行造数据；缺点是固定值多，容易变成"什么都想加参数"的巨型工厂。
 *   - **Test Data Builder**：链式构建 + 合理默认值，只覆盖关心的字段。`anOrder().withAmount(9900).build()`。比 ObjectMother 灵活，是当前主流（`java-faker`、`Instancio` 都是这个思路）。
 *   - 关键设计：**Builder 必须有能构建出"合法对象"的默认值**。每个用例只改它要测的那一个字段，其余保持默认——这样用例失败时原因唯一。
 *   - **测试夹具（Fixture）**：用例运行前准备好的数据与环境。清理三原则：`finally`/`@AfterEach` 一定执行、删除自己创建的东西、不删除别人的。
 *   - **唯一性**：并发或共享库场景下，用 `UUID` / 时间戳 / 自增序号做后缀，避免主键冲突。不要用固定 ID（`id=1`）在共享库里写数据。
 *   - **临时文件**：用 `Files.createTempDirectory` 而不是硬编码路径；测试结束递归删除；JUnit 5 的 `@TempDir` 会自动清理。
 *   - **不变式断言**：造完数据后先断言"这个夹具确实是我以为的样子"（如"库存确实为 5"），否则夹具写错会让测试失败指向错误的地方。
 *   - 反模式：`@BeforeAll` 里造全局共享可变数据（用例顺序耦合）；Builder 的默认值不合法（每次都要改三个字段）；测试里硬编码时间（用固定 `Clock`）；一个 Builder 服务所有场景导致参数爆炸。
 *   - 易错：Builder 复用同一个实例（`build()` 应每次返回新对象，或至少不共享可变状态）；清理时漏掉关联表；用生产数据做测试夹具（含真实手机号）。
 * ============================================================
 */

package com.heima.ch33;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

// ==================== 示例代码 ====================

public class TestDataBuilder {
    /** 被测领域对象：不可变 */
    static final class Order {
        final String orderNo;
        final long userId;
        final long amountCents;
        final String status;
        final String couponCode;
        final long createdAt;

        Order(String orderNo, long userId, long amountCents, String status,
              String couponCode, long createdAt) {
            this.orderNo = orderNo;
            this.userId = userId;
            this.amountCents = amountCents;
            this.status = status;
            this.couponCode = couponCode;
            this.createdAt = createdAt;
        }

        boolean payable() {
            return "CREATED".equals(status) && amountCents > 0;
        }

        Order withStatus(String s) {
            return new Order(orderNo, userId, amountCents, s, couponCode, createdAt);
        }

        @Override
        public String toString() {
            return "Order{" + orderNo + ", user=" + userId + ", cents=" + amountCents
                    + ", status=" + status + ", coupon=" + couponCode + "}";
        }
    }

    /** Test Data Builder：默认值即"合法可支付订单" */
    static class OrderBuilder {
        private String orderNo = "ORD-DEFAULT";
        private long userId = 1000L;
        private long amountCents = 9_900L;
        private String status = "CREATED";
        private String couponCode = null;
        private long createdAt = 1_700_000_000_000L;

        static OrderBuilder anOrder() {
            return new OrderBuilder();
        }

        OrderBuilder withOrderNo(String v) {
            this.orderNo = v;
            return this;
        }

        OrderBuilder withUserId(long v) {
            this.userId = v;
            return this;
        }

        OrderBuilder withAmountCents(long v) {
            this.amountCents = v;
            return this;
        }

        OrderBuilder withStatus(String v) {
            this.status = v;
            return this;
        }

        OrderBuilder withCoupon(String v) {
            this.couponCode = v;
            return this;
        }

        OrderBuilder createdAt(long v) {
            this.createdAt = v;
            return this;
        }

        Order build() {
            if (orderNo == null || orderNo.isBlank()) {
                throw new IllegalStateException("orderNo 不能为空");
            }
            if (status == null) {
                throw new IllegalStateException("status 不能为空");
            }
            return new Order(orderNo, userId, amountCents, status, couponCode, createdAt);
        }
    }

    /** ObjectMother：常见场景一行拿到 */
    static class Orders {
        static Order aNormalOrder() {
            return OrderBuilder.anOrder().build();
        }

        static Order aPaidOrder() {
            return OrderBuilder.anOrder().withStatus("PAID").build();
        }

        static Order aCancelledOrder() {
            return OrderBuilder.anOrder().withStatus("CANCELLED").build();
        }

        static Order anOrderWithCoupon(String code) {
            return OrderBuilder.anOrder().withCoupon(code).build();
        }

        static List<Order> threeOrdersOfSameUser() {
            List<Order> list = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                list.add(OrderBuilder.anOrder()
                        .withOrderNo("ORD-U1000-" + i)
                        .withUserId(1000L)
                        .withAmountCents(1_000L * i)
                        .build());
            }
            return list;
        }
    }

    /** 夹具：临时目录 + 文件，结束时清理 */
    static class FileFixture implements AutoCloseable {
        final Path dir;
        final List<Path> created = new ArrayList<>();

        FileFixture() throws IOException {
            this.dir = Files.createTempDirectory("order-test-");
        }

        Path write(String name, String content) throws IOException {
            Path p = dir.resolve(name);
            Files.writeString(p, content, StandardCharsets.UTF_8);
            created.add(p);
            return p;
        }

        List<String> listNames() throws IOException {
            try (Stream<Path> s = Files.list(dir)) {
                return s.map(p -> p.getFileName().toString()).sorted().toList();
            }
        }

        public void close() throws IOException {
            try (Stream<Path> s = Files.walk(dir)) {
                List<Path> all = s.sorted(Comparator.reverseOrder()).toList();
                for (Path p : all) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    static int passed;
    static final List<String> failures = new ArrayList<>();

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.printf("    %-52s OK%n", name);
        } else {
            failures.add(name + " -> " + detail);
            System.out.printf("    %-52s FAIL: %s%n", name, detail);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. Builder：只改关心的字段 ===");
        Order cheap = OrderBuilder.anOrder().withAmountCents(1L).build();
        System.out.println("    " + cheap);
        check("1 分订单仍可支付", cheap.payable(), "payable=false");

        Order big = OrderBuilder.anOrder().withAmountCents(999_999L).build();
        check("大额订单不影响其他字段", big.userId == 1000L && "CREATED".equals(big.status),
                "默认值被意外改动");

        System.out.println();
        System.out.println("=== 2. ObjectMother：语义化的一行数据 ===");
        System.out.println("    普通订单: " + Orders.aNormalOrder());
        System.out.println("    已支付  : " + Orders.aPaidOrder());
        System.out.println("    已取消  : " + Orders.aCancelledOrder());
        System.out.println("    带券    : " + Orders.anOrderWithCoupon("CASH10"));

        check("已支付订单不可再支付", !Orders.aPaidOrder().payable(), "payable 应为 false");
        check("已取消订单不可支付", !Orders.aCancelledOrder().payable(), "payable 应为 false");
        check("带券订单仍可支付", Orders.anOrderWithCoupon("CASH10").payable(), "payable 应为 true");

        System.out.println();
        System.out.println("=== 3. 批量夹具：同一用户的 3 笔订单 ===");
        List<Order> batch = Orders.threeOrdersOfSameUser();
        long total = batch.stream().mapToLong(o -> o.amountCents).sum();
        System.out.println("    订单数=" + batch.size() + " 总金额=" + total + " 分");
        check("批量夹具数量正确", batch.size() == 3, "size=" + batch.size());
        check("批量夹具总额 = 6000", total == 6_000L, "total=" + total);
        check("全部属于同一用户", batch.stream().allMatch(o -> o.userId == 1000L), "userId 不一致");

        System.out.println();
        System.out.println("=== 4. 唯一性：避免共享库主键冲突 ===");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Order unique = OrderBuilder.anOrder().withOrderNo("ORD-" + suffix).build();
        System.out.println("    唯一订单号: " + unique.orderNo);
        check("订单号带唯一后缀", unique.orderNo.length() > 12, "长度不足");
        check("默认订单号未被污染", "ORD-DEFAULT".equals(Orders.aNormalOrder().orderNo),
                "Builder 共享了可变状态");

        System.out.println();
        System.out.println("=== 5. 夹具清理：try-with-resources 保证删除 ===");
        Path created;
        try (FileFixture fx = new FileFixture()) {
            created = fx.write("orders.csv", "orderNo,amount\nORD-1,9900\n");
            fx.write("users.csv", "userId,name\n1000,张三\n");
            System.out.println("    临时目录: " + fx.dir);
            System.out.println("    文件列表: " + fx.listNames());
            check("写入 2 个文件", fx.listNames().size() == 2, "数量不符");
            check("文件内容可读", Files.readString(created, StandardCharsets.UTF_8).contains("ORD-1"),
                    "内容不符");
        }
        check("夹具关闭后目录已删除", !Files.exists(created), "文件仍存在: " + created);

        System.out.println();
        System.out.println("=== 6. 固定时钟：让时间相关的用例可重复 ===");
        Clock fixed = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneId.of("Asia/Shanghai"));
        Order fresh = OrderBuilder.anOrder().createdAt(fixed.millis()).build();
        long ageMs = fixed.millis() - fresh.createdAt;
        check("刚创建的订单 age = 0", ageMs == 0, "age=" + ageMs);
        Clock later = Clock.offset(fixed, java.time.Duration.ofMinutes(31));
        long laterAge = later.millis() - fresh.createdAt;
        check("31 分钟后 age = 31 分钟", laterAge == 31 * 60_000L, "age=" + laterAge);
        check("31 分钟后超过 30 分钟未支付阈值", laterAge > 30 * 60_000L, "未超阈值");

        System.out.println();
        System.out.println("=== 7. 造数据的四条原则 ===");
        String[] rules = {
                "默认值必须合法：每个用例只改一个字段",
                "build() 每次返回新对象，不共享可变状态",
                "语义化命名：aPaidOrder() 比 new Order(...,\"PAID\",...) 更可读",
                "夹具必清理：try-with-resources / @AfterEach / @TempDir"
        };
        for (String r : rules) {
            System.out.println("    - " + r);
        }

        System.out.println();
        System.out.println("=== 结果 ===");
        System.out.println("  通过 " + passed + "，失败 " + failures.size());
        for (String f : failures) {
            System.out.println("    " + f);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `OrderBuilder` 增加 `withItems(int count)`，自动生成明细行，并保证 `build()` 时校验"明细总额 == 订单金额"。
 * 2. 用 `@TempDir` 的等价写法（`Files.createTempDirectory` + `finally`）改写 `FileFixture`，说明两者在异常路径上的差别。
 * 3. 找出"Builder 复用同一实例导致用例互相污染"的一种具体写法，并说明为什么 `build()` 返回不可变对象能规避它。
 * ============================================================
 */
