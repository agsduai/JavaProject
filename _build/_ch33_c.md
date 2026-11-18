### 33-08 异常契约、参数校验与并发测试

**知识点**
- **异常契约**：方法除了"返回什么"，还要约定"什么情况下抛什么异常"。契约要写进 JavaDoc 的 `@throws`，测试要覆盖它。异常类型选错（该 `IllegalArgumentException` 却抛 `NullPointerException`）会让调用方无法精准处理。
- 异常分类的用法：**参数非法** → `IllegalArgumentException`（含 `Objects.requireNonNull` 的 `NullPointerException`）；**状态不允许** → `IllegalStateException`；**受检的业务失败** → 自定义 `XxxException extends Exception`；**不该发生** → `AssertionError` / 直接让 NPE 暴露。
- 不要用异常做流程控制：能用返回值表达"业务失败"（如 `Result.fail("NO_STOCK")`）就不要抛异常。异常只用于"调用方写错了"或"环境异常"。
- **异常包装**：底层异常（SQLException）不要直接抛给上层，要转成领域异常并**保留 cause**（`new OrderException("创建订单失败", e)`）。丢掉 cause 会毁掉排查线索。
- 测试异常的三个层次：**类型**（`assertThrows(IllegalArgumentException.class, ...)`）、**message 关键片段**（`contains("金额")`）、**cause 链**（`assertEquals(SQLException.class, e.getCause().getClass())`）。只测类型常常不够。
- **不该抛的异常**要显式验证：正常路径不应抛异常（用 `assertDoesNotThrow`），或者"某异常绝不该出现"（捕获后 `fail()`）。
- **参数校验**放在方法入口，越早越好（fail fast）。`Objects.requireNonNull(x, "x 不能为空")`、`Objects.checkIndex`、`Math.addExact`（溢出即抛）都是 JDK 自带的好工具。
- **并发测试**的难点是"不确定性"。三个手段：`CountDownLatch` 让线程同时起跑、重复多次（1000 轮）提高暴露概率、断言**不变式**（总量守恒）而不是具体交错顺序。
- 并发测试不要断言"顺序"，只断言"结果合法"。同时给测试设超时（`@Timeout`），避免挂死。
- 易错：`catch (Exception e) {}` 吞异常（测试永远绿）；断言 message 用全等（改文案就红，应用 `contains`）；并发测试只跑一次就认为没问题；用 `Thread.sleep` 代替同步。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExceptionContract {
    static class OrderException extends Exception {
        private static final long serialVersionUID = 1L;

        OrderException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 模拟底层存储异常 */
    static class DiskFullException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DiskFullException(String msg) {
            super(msg);
        }
    }

    static class OrderRepo {
        private final boolean broken;
        private final AtomicInteger writes = new AtomicInteger();

        OrderRepo(boolean broken) {
            this.broken = broken;
        }

        int write(String orderNo) {
            if (broken) {
                throw new DiskFullException("磁盘已满");
            }
            return writes.incrementAndGet();
        }

        int writes() {
            return writes.get();
        }
    }

    static class OrderService {
        private final OrderRepo repo;

        OrderService(OrderRepo repo) {
            this.repo = repo;
        }

        /**
         * 创建订单。
         *
         * @throws NullPointerException     orderNo 为 null
         * @throws IllegalArgumentException orderNo 为空白、金额不为正
         * @throws OrderException           底层写入失败（保留 cause）
         */
        String create(String orderNo, long amountCents) throws OrderException {
            Objects.requireNonNull(orderNo, "orderNo 不能为 null");
            if (orderNo.isBlank()) {
                throw new IllegalArgumentException("orderNo 不能为空白");
            }
            if (amountCents <= 0) {
                throw new IllegalArgumentException("金额必须为正，实际=" + amountCents);
            }
            try {
                repo.write(orderNo);
                return "CREATED:" + orderNo;
            } catch (RuntimeException e) {
                throw new OrderException("创建订单失败: " + orderNo, e);
            }
        }

        /** 状态类错误用 IllegalStateException */
        void pay(String status) {
            if (!"CREATED".equals(status)) {
                throw new IllegalStateException("当前状态不允许支付: " + status);
            }
        }
    }

    static int passed;
    static final List<String> failures = new ArrayList<>();

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.printf("    %-56s OK%n", name);
        } else {
            failures.add(name + " -> " + detail);
            System.out.printf("    %-56s FAIL: %s%n", name, detail);
        }
    }

    interface ThrowingCall {
        void run() throws Exception;
    }

    /** 断言异常类型 + message 片段 + cause 类型 */
    static void assertThrows(String name, Class<? extends Throwable> type,
                             String messagePart, Class<? extends Throwable> causeType,
                             ThrowingCall call) {
        try {
            call.run();
            check(name, false, "期望抛 " + type.getSimpleName() + "，但没抛");
        } catch (Throwable t) {
            if (!type.isInstance(t)) {
                check(name, false, "期望 " + type.getSimpleName() + "，实际 " + t.getClass().getSimpleName());
                return;
            }
            if (messagePart != null && (t.getMessage() == null || !t.getMessage().contains(messagePart))) {
                check(name, false, "message 不含 [" + messagePart + "]，实际=[" + t.getMessage() + "]");
                return;
            }
            if (causeType != null && (t.getCause() == null || !causeType.isInstance(t.getCause()))) {
                check(name, false, "cause 期望 " + causeType.getSimpleName()
                        + "，实际 " + (t.getCause() == null ? "null" : t.getCause().getClass().getSimpleName()));
                return;
            }
            check(name, true, "");
        }
    }

    static void assertDoesNotThrow(String name, ThrowingCall call) {
        try {
            call.run();
            check(name, true, "");
        } catch (Throwable t) {
            check(name, false, "不该抛却抛了 " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 参数非法：类型 + message 双重断言 ===");
        OrderService ok = new OrderService(new OrderRepo(false));
        assertThrows("orderNo=null -> NullPointerException", NullPointerException.class,
                "orderNo", null, () -> ok.create(null, 100L));
        assertThrows("orderNo 空白 -> IllegalArgumentException", IllegalArgumentException.class,
                "空白", null, () -> ok.create("   ", 100L));
        assertThrows("金额 0 -> IllegalArgumentException", IllegalArgumentException.class,
                "金额必须为正", null, () -> ok.create("ORD-1", 0L));
        assertThrows("金额 -1 -> IllegalArgumentException", IllegalArgumentException.class,
                "实际=-1", null, () -> ok.create("ORD-1", -1L));

        System.out.println();
        System.out.println("=== 2. 底层异常包装：保留 cause 才能排查 ===");
        OrderService broken = new OrderService(new OrderRepo(true));
        assertThrows("写入失败 -> OrderException 且 cause 是 DiskFullException",
                OrderException.class, "创建订单失败", DiskFullException.class,
                () -> broken.create("ORD-2", 100L));

        System.out.println();
        System.out.println("=== 3. 状态错误用 IllegalStateException ===");
        assertThrows("已支付订单再支付 -> IllegalStateException", IllegalStateException.class,
                "不允许支付", null, () -> ok.pay("PAID"));
        assertDoesNotThrow("CREATED 状态可以支付", () -> ok.pay("CREATED"));

        System.out.println();
        System.out.println("=== 4. 正常路径不该抛异常 ===");
        assertDoesNotThrow("合法订单创建成功", () -> ok.create("ORD-3", 9_900L));
        check("合法订单确实写入一次", ok.repo.writes() == 1, "writes=" + ok.repo.writes());

        System.out.println();
        System.out.println("=== 5. 吞异常的反面教材 ===");
        boolean swallowed;
        try {
            badCatch(() -> {
                throw new IllegalStateException("真实故障");
            });
            swallowed = true;
        } catch (Throwable t) {
            swallowed = false;
        }
        check("catch(Exception){} 会吞掉故障（所以测试永远绿）", swallowed,
                "异常竟然漏出来了");

        System.out.println();
        System.out.println("=== 6. 并发测试：同时起跑 + 断言不变式 ===");
        AtomicInteger counter = new AtomicInteger();
        int threads = 8;
        int perThread = 2000;
        int rounds = 3;
        int badRounds = 0;
        for (int round = 1; round <= rounds; round++) {
            counter.set(0);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            counter.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await(20, TimeUnit.SECONDS);
            pool.shutdownNow();
            int expect = threads * perThread;
            boolean okRound = counter.get() == expect;
            if (!okRound) {
                badRounds++;
            }
            System.out.printf("    第 %d 轮：期望 %d，实际 %d %s%n",
                    round, expect, counter.get(), okRound ? "OK" : "FAIL");
        }
        check("3 轮并发计数均无丢失", badRounds == 0, "异常轮数=" + badRounds);

        System.out.println();
        System.out.println("=== 7. 并发测试：非线程安全集合会暴露 ===");
        List<Integer> unsafe = new ArrayList<>();
        ExecutorService pool2 = Executors.newFixedThreadPool(8);
        CountDownLatch start2 = new CountDownLatch(1);
        CountDownLatch done2 = new CountDownLatch(8);
        for (int t = 0; t < 8; t++) {
            pool2.submit(() -> {
                try {
                    start2.await();
                    for (int i = 0; i < 500; i++) {
                        unsafe.add(i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done2.countDown();
                }
            });
        }
        start2.countDown();
        done2.await(20, TimeUnit.SECONDS);
        pool2.shutdownNow();
        System.out.println("    ArrayList 期望 4000 个元素，实际 " + unsafe.size()
                + "（可能丢失，且可能抛 ArrayIndexOutOfBoundsException）");
        check("不变式：元素数不应超过期望", unsafe.size() <= 4000, "size=" + unsafe.size());

        System.out.println();
        System.out.println("=== 8. 异常契约设计清单 ===");
        String[] rules = {
                "参数非法 -> IllegalArgumentException / NullPointerException",
                "状态不允许 -> IllegalStateException",
                "可恢复业务失败 -> 返回值 Result，不要抛异常",
                "底层异常必须包装并保留 cause",
                "JavaDoc 写明 @throws，测试覆盖类型 + message + cause",
                "正常路径显式断言不抛异常",
                "并发测试：同时起跑 + 多轮 + 只断言不变式"
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

    static void badCatch(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            // 反面教材：吞掉异常，测试无法发现问题
        }
    }
}
```

**课后练习**
1. 把 `badCatch` 改成"记录日志后重新抛出领域异常"，写出对应测试，验证 cause 链完整。
2. 为 `create` 增加 `amountCents` 溢出校验（用 `Math.addExact` 累加明细），测试溢出时抛什么异常。
3. 把并发计数改成 `int count++`（非原子），跑 10 轮统计有几轮丢数据，说明为什么"跑一次就绿"不可靠。

### 33-09 静态检查：规范、坏味道与自定义规则引擎

**知识点**
- **静态检查**在**不运行代码**的前提下发现问题，能在提交前拦住一大类缺陷：空指针风险、资源未关闭、`equals`/`hashCode` 不配对、可变静态字段、`catch` 空块、魔法值、命名不规范。
- 工具分工：
  - **Checkstyle**：纯风格（缩进、命名、行宽、import 顺序、JavaDoc）。快，但只能管"形"。
  - **SpotBugs**：字节码级缺陷检测（NPE 风险、`equals` 未覆盖 `hashCode`、`Stream` 未关闭、锁未释放）。能管"实"。
  - **PMD**：源码级坏味道（圈复杂度、重复代码、过长方法、未使用变量）。
  - **ErrorProne / SonarQube**：编译期/平台级，规则最全，Sonar 还能做质量门禁。
- **圈复杂度**（Cyclomatic Complexity）= 判定点数量 + 1。经验阈值：方法 > 10 就该拆，> 15 几乎必然难测。它是"可测性"的量化指标。
- **重复代码**检测思路：滑动窗口 + 归一化（去空白/去注释/变量名归一）+ 哈希比对。重复率高的地方，改一处漏一处。
- 规则引擎的最小实现：**解析**（按行/按 token）→ **匹配**（正则或 AST 访问）→ **报告**（文件:行:规则:说明）→ **分级**（ERROR 阻断构建，WARN 仅提示）。
- **质量门禁**（quality gate）：把静态检查结果变成"能不能合并"的硬条件——`ERROR = 0`、圈复杂度超标的文件数不增加、新增代码覆盖率 ≥ 80%。
- 规则要**可执行、可解释、可豁免**。豁免必须有理由和责任人（`@SuppressWarnings("rule")` + 注释说明），否则规则会被集体绕过。
- 渐进式落地：先只开 ERROR 级别的少数规则（空 `catch`、资源未关闭、`equals/hashCode`），全绿后再逐步加 WARN，最后接入 CI 阻断。一次性开 200 条规则必然被抵制。
- 易错：规则太多导致 `// CHECKSTYLE:OFF` 满天飞；只看报告不修（技术债累积）；把风格问题和缺陷问题混在一个等级；自研规则正则写得太宽（误报率高）或太窄（漏报）。

**示例代码**
```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class StaticAnalysis {
    enum Severity {
        ERROR, WARN, INFO
    }

    /** 一条规则 */
    static class Rule {
        final String id;
        final String desc;
        final Severity severity;
        final Pattern pattern;

        Rule(String id, String desc, Severity severity, String regex) {
            this.id = id;
            this.desc = desc;
            this.severity = severity;
            this.pattern = Pattern.compile(regex);
        }
    }

    /** 一条发现 */
    static class Finding {
        final String file;
        final int line;
        final Rule rule;
        final String snippet;

        Finding(String file, int line, Rule rule, String snippet) {
            this.file = file;
            this.line = line;
            this.rule = rule;
            this.snippet = snippet;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s:%d %s | %s", rule.severity, file, line,
                    rule.id, snippet.trim());
        }
    }

    static final List<Rule> RULES = List.of(
            new Rule("EMPTY_CATCH", "空的 catch 块会吞掉异常", Severity.ERROR,
                    "^\\s*catch\\s*\\([^)]*\\)\\s*\\{\\s*$"),
            new Rule("SYSTEM_OUT", "生产代码不应直接 System.out", Severity.WARN,
                    "System\\.(out|err)\\.print"),
            new Rule("MAGIC_NUMBER", "出现魔法数字，应提取常量", Severity.INFO,
                    "[^\\w.]\\d{3,}[^\\w.]"),
            new Rule("TODO_FIXME", "遗留 TODO/FIXME", Severity.INFO,
                    "//\\s*(TODO|FIXME)"),
            new Rule("THREAD_SLEEP", "测试/生产代码里的 sleep 是 flaky 源头", Severity.WARN,
                    "Thread\\.sleep\\s*\\("),
            new Rule("CATCH_GENERIC", "catch Exception 过宽", Severity.WARN,
                    "catch\\s*\\(\\s*Exception\\s+\\w+\\s*\\)")
    );

    /** 迷你规则引擎 */
    static class Analyzer {
        final List<Finding> findings = new ArrayList<>();
        final Map<String, Integer> perRule = new LinkedHashMap<>();

        void analyze(Path file) throws IOException {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String name = file.getFileName().toString();
            for (int i = 0; i < lines.size(); i++) {
                String text = lines.get(i);
                String stripped = stripComment(text);
                for (Rule rule : RULES) {
                    if (rule.pattern.matcher(stripped).find()) {
                        if ("EMPTY_CATCH".equals(rule.id) && !isEmptyCatchBlock(lines, i)) {
                            continue;
                        }
                        findings.add(new Finding(name, i + 1, rule, text));
                        perRule.merge(rule.id, 1, Integer::sum);
                    }
                }
            }
        }

        /** 判断 catch 行之后是否紧跟空块 */
        static boolean isEmptyCatchBlock(List<String> lines, int catchLine) {
            for (int j = catchLine + 1; j < Math.min(lines.size(), catchLine + 3); j++) {
                String t = lines.get(j).trim();
                if (t.isEmpty()) {
                    continue;
                }
                return t.startsWith("}") || t.startsWith("//") || t.startsWith("/*");
            }
            return false;
        }

        static String stripComment(String s) {
            int idx = s.indexOf("//");
            return idx >= 0 ? s.substring(0, idx) : s;
        }

        /** 圈复杂度：判定点 + 1 */
        static int cyclomatic(String source) {
            String[] keywords = {"if ", "else if", "for ", "while ", "case ", "catch ", "&&", "||", "?"};
            int score = 1;
            for (String k : keywords) {
                int from = 0;
                while (true) {
                    int idx = source.indexOf(k, from);
                    if (idx < 0) {
                        break;
                    }
                    score++;
                    from = idx + k.length();
                }
            }
            return score;
        }

        /** 重复代码：按 6 行滑动窗口做归一化哈希 */
        static Map<String, List<Integer>> duplicateBlocks(List<String> lines) {
            Map<String, List<Integer>> map = new HashMap<>();
            int window = 6;
            for (int i = 0; i + window <= lines.size(); i++) {
                StringBuilder sb = new StringBuilder();
                boolean meaningful = false;
                for (int j = i; j < i + window; j++) {
                    String t = lines.get(j).trim().replaceAll("\\s+", " ");
                    if (t.isEmpty() || t.startsWith("//") || t.equals("{") || t.equals("}")) {
                        continue;
                    }
                    meaningful = true;
                    sb.append(t).append(';');
                }
                if (meaningful) {
                    map.computeIfAbsent(sb.toString(), k -> new ArrayList<>()).add(i + 1);
                }
            }
            map.entrySet().removeIf(e -> e.getValue().size() < 2);
            return map;
        }
    }

    static final String SAMPLE = """
            public class BadSample {
                private static int total;

                public int calc(int a) {
                    try {
                        System.out.println("计算中");
                        Thread.sleep(100);
                    } catch (Exception e) {
                    }
                    if (a > 1000) {
                        total = a * 2;
                    }
                    // TODO 这里要改成配置
                    return total;
                }

                public int calc2(int a) {
                    try {
                        System.out.println("计算中");
                        Thread.sleep(100);
                    } catch (Exception e) {
                    }
                    if (a > 1000) {
                        total = a * 2;
                    }
                    return total;
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("static-");
        Path file = dir.resolve("BadSample.java");
        Files.writeString(file, SAMPLE, StandardCharsets.UTF_8);

        try {
            System.out.println("=== 1. 扫描示例文件 ===");
            Analyzer analyzer = new Analyzer();
            analyzer.analyze(file);

            for (Finding f : analyzer.findings) {
                System.out.println("  " + f);
            }
            System.out.println("  共 " + analyzer.findings.size() + " 条发现");

            System.out.println();
            System.out.println("=== 2. 按规则汇总 ===");
            analyzer.perRule.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey()))
                    .forEach(e -> System.out.printf("  %-14s %d 次%n", e.getKey(), e.getValue()));

            System.out.println();
            System.out.println("=== 3. 质量门禁判定 ===");
            long errors = analyzer.findings.stream()
                    .filter(f -> f.rule.severity == Severity.ERROR).count();
            long warns = analyzer.findings.stream()
                    .filter(f -> f.rule.severity == Severity.WARN).count();
            long infos = analyzer.findings.stream()
                    .filter(f -> f.rule.severity == Severity.INFO).count();
            System.out.printf("  ERROR=%d WARN=%d INFO=%d%n", errors, warns, infos);
            boolean gatePass = errors == 0;
            System.out.println("  门禁（ERROR 必须为 0）: " + (gatePass ? "PASS" : "BLOCKED"));
            if (!gatePass) {
                System.out.println("  阻断原因：存在空 catch 块，异常被吞掉，测试无法发现问题");
            }

            System.out.println();
            System.out.println("=== 4. 圈复杂度 ===");
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String whole = String.join("\n", lines);
            int cc = Analyzer.cyclomatic(whole);
            System.out.println("  整个文件圈复杂度 = " + cc);
            System.out.println("  单方法阈值参考：<=10 良好，11~15 需关注，>15 应拆分");

            System.out.println();
            System.out.println("=== 5. 重复代码检测（6 行窗口）===");
            Map<String, List<Integer>> dup = Analyzer.duplicateBlocks(lines);
            if (dup.isEmpty()) {
                System.out.println("  未发现重复块");
            } else {
                for (Map.Entry<String, List<Integer>> e : dup.entrySet()) {
                    System.out.println("  重复块出现在行 " + e.getValue()
                            + "，片段: " + e.getKey().substring(0, Math.min(60, e.getKey().length())) + "...");
                }
                System.out.println("  calc 与 calc2 的 try/catch 逻辑完全重复 -> 应抽成方法");
            }

            System.out.println();
            System.out.println("=== 6. 工具分工 ===");
            String[][] t = {
                    {"Checkstyle", "源码风格", "命名、缩进、行宽、import", "快，只管形"},
                    {"SpotBugs", "字节码缺陷", "NPE、equals/hashCode、资源泄漏", "能管实"},
                    {"PMD", "源码坏味道", "圈复杂度、重复代码、未用变量", "规则可自定义"},
                    {"ErrorProne", "编译期", "编译即报，接入零成本", "需改构建"},
                    {"SonarQube", "平台", "规则最全 + 质量门禁 + 趋势", "需部署"}
            };
            System.out.printf("  %-12s %-12s %-34s %s%n", "工具", "层次", "擅长", "特点");
            for (String[] r : t) {
                System.out.printf("  %-12s %-12s %-34s %s%n", r[0], r[1], r[2], r[3]);
            }

            System.out.println();
            System.out.println("=== 7. 渐进式落地建议 ===");
            String[] steps = {
                    "第 1 周：只开 ERROR 三条（空 catch、资源未关闭、equals/hashCode）",
                    "第 2 周：全绿后接入 CI，PR 时阻断",
                    "第 3 周：加 WARN（System.out、sleep、catch Exception）",
                    "第 4 周：加圈复杂度与重复率阈值，只对新增代码生效",
                    "持续：豁免必须写理由，每季度清理一次豁免清单"
            };
            for (String s : steps) {
                System.out.println("  - " + s);
            }
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
```

**课后练习**
1. 新增一条规则"公开方法缺少 JavaDoc"，用"上一行是否为 `*/`"判断，注意排除 `@Override` 方法。
2. 把 `cyclomatic` 改成**按方法统计**（用大括号配对找方法边界），输出每个方法的复杂度排名。
3. 说明为什么"一次性开启 200 条规则"会导致规则被集体绕过，给出你所在团队的两阶段落地计划。

### 33-10 CI 流水线与质量门禁

**知识点**
- **CI（持续集成）** 的核心不是"自动构建"，而是"**每次提交都在 10 分钟内给出可信的通过/失败结论**"。慢的流水线等于没有流水线。
- 标准阶段：**检出 → 编译 → 静态检查 → 单元测试 → 集成测试 → 打包 → 制品归档 → 通知**。前面的阶段失败就立刻终止（fail fast），不要跑完所有阶段才发现编译不过。
- **质量门禁**是流水线里的硬闸门：编译 0 error、静态检查 0 ERROR、单测全绿、新增代码覆盖率 ≥ 阈值、无高危依赖漏洞。任一不满足即阻断合并。
- **流水线要可复现**：固定 JDK 版本、固定依赖版本（`pom.xml` 锁版本）、固定随机种子、隔离外部依赖。本地能过、CI 不能过，多半是环境或时序差异。
- **构建缓存**：依赖缓存（`~/.m2`）、编译缓存能显著缩短时间。但缓存 key 要包含依赖文件哈希，否则会用到过期缓存。
- **并行化**：测试按模块/包拆分并行跑；静态检查与单元测试可并行。注意并行时数据隔离（数据库、端口、临时目录）。
- **失败信息要可行动**：报告要给出"哪个文件哪一行、期望什么、实际什么、怎么复现"。只输出 `BUILD FAILED` 等于没给信息。
- **制品（artifact）**：构建产物带版本号与提交哈希，可追溯到具体 commit。不要用 `latest` 这种可变标签。
- **流水线的可观测性**：每阶段耗时、失败率、平均修复时间。发现"测试阶段从 2 分钟涨到 8 分钟"就该处理，别等它变成 30 分钟。
- 易错：CI 里跑 `mvn test` 但跳过失败（`-Dmaven.test.failure.ignore=true`）；测试依赖本地数据库；覆盖率阈值设在总覆盖率而不是新增代码（老代码拖累永远不达标）；流水线里 `sleep 30` 等部署。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CiPipeline {
    enum Status {
        PASS, FAIL, SKIP
    }

    /** 阶段结果 */
    static class StageResult {
        final String name;
        final Status status;
        final long costMs;
        final String detail;

        StageResult(String name, Status status, long costMs, String detail) {
            this.name = name;
            this.status = status;
            this.costMs = costMs;
            this.detail = detail;
        }
    }

    /** 一个阶段 */
    interface Stage {
        StageResult run(Context ctx) throws Exception;
    }

    /** 流水线上下文：阶段间共享的"工作区" */
    static class Context {
        final Map<String, Object> data = new LinkedHashMap<>();
        final List<StageResult> results = new ArrayList<>();
        final List<String> artifacts = new ArrayList<>();
        boolean blocked;
        String blockReason;

        void block(String reason) {
            this.blocked = true;
            this.blockReason = reason;
        }
    }

    /** 质量门禁 */
    static class QualityGate {
        int minCoveragePercent = 70;
        int maxCyclomatic = 10;
        int maxStaticErrors = 0;
        boolean allowWarnings = true;

        String evaluate(Context ctx) {
            int errors = (int) ctx.data.getOrDefault("staticErrors", 0);
            int coverage = (int) ctx.data.getOrDefault("coverage", 0);
            int cc = (int) ctx.data.getOrDefault("maxCyclomatic", 0);
            int failedTests = (int) ctx.data.getOrDefault("failedTests", 0);

            List<String> reasons = new ArrayList<>();
            if (failedTests > 0) {
                reasons.add("单元测试失败 " + failedTests + " 个");
            }
            if (errors > maxStaticErrors) {
                reasons.add("静态检查 ERROR=" + errors + " 超过阈值 " + maxStaticErrors);
            }
            if (coverage < minCoveragePercent) {
                reasons.add("覆盖率 " + coverage + "% 低于阈值 " + minCoveragePercent + "%");
            }
            if (cc > maxCyclomatic) {
                reasons.add("最大圈复杂度 " + cc + " 超过阈值 " + maxCyclomatic);
            }
            return reasons.isEmpty() ? null : String.join("；", reasons);
        }
    }

    /** 流水线执行器 */
    static class Pipeline {
        final List<Stage> stages = new ArrayList<>();
        final QualityGate gate = new QualityGate();

        Pipeline add(Stage s) {
            stages.add(s);
            return this;
        }

        void run(String title) {
            System.out.println("=== " + title + " ===");
            Context ctx = new Context();
            long totalStart = System.nanoTime();
            for (Stage stage : stages) {
                String name = stage.getClass().getSimpleName();
                try {
                    StageResult r = stage.run(ctx);
                    ctx.results.add(r);
                    String mark = r.status == Status.PASS ? "PASS" : r.status == Status.FAIL ? "FAIL" : "SKIP";
                    System.out.printf("  [%-4s] %-22s %5d ms  %s%n",
                            mark, r.name, r.costMs, r.detail);
                    if (r.status == Status.FAIL) {
                        ctx.block("阶段 " + r.name + " 失败: " + r.detail);
                        break;
                    }
                } catch (Exception e) {
                    ctx.block("阶段异常: " + e.getMessage());
                    System.out.printf("  [FAIL] %-22s 异常 %s%n", name, e.getMessage());
                    break;
                }
            }
            long totalMs = (System.nanoTime() - totalStart) / 1_000_000;

            System.out.println();
            if (ctx.blocked) {
                System.out.println("  流水线结果: BLOCKED");
                System.out.println("  原因: " + ctx.blockReason);
            } else {
                String gateFail = gate.evaluate(ctx);
                if (gateFail != null) {
                    System.out.println("  流水线结果: BLOCKED（质量门禁）");
                    System.out.println("  原因: " + gateFail);
                } else {
                    System.out.println("  流水线结果: PASS");
                    System.out.println("  制品: " + ctx.artifacts);
                }
            }
            System.out.println("  总耗时: " + totalMs + " ms（真实流水线要求 10 分钟内）");
            System.out.println();
        }
    }

    /** ---------- 具体阶段 ---------- */
    static class CheckoutStage implements Stage {
        public StageResult run(Context ctx) {
            ctx.data.put("commit", "a1b2c3d");
            ctx.data.put("branch", "feature/order");
            return new StageResult("检出代码", Status.PASS, 120,
                    "commit=a1b2c3d branch=feature/order");
        }
    }

    static class CompileStage implements Stage {
        private final boolean broken;

        CompileStage(boolean broken) {
            this.broken = broken;
        }

        public StageResult run(Context ctx) {
            if (broken) {
                return new StageResult("编译", Status.FAIL, 800,
                        "OrderService.java:42 找不到符号 total");
            }
            return new StageResult("编译", Status.PASS, 800, "23 个源文件编译通过");
        }
    }

    static class StaticCheckStage implements Stage {
        private final int errors;
        private final int warns;

        StaticCheckStage(int errors, int warns) {
            this.errors = errors;
            this.warns = warns;
        }

        public StageResult run(Context ctx) {
            ctx.data.put("staticErrors", errors);
            return new StageResult("静态检查", Status.PASS, 400,
                    "ERROR=" + errors + " WARN=" + warns);
        }
    }

    static class UnitTestStage implements Stage {
        private final int total;
        private final int failed;
        private final int coverage;

        UnitTestStage(int total, int failed, int coverage) {
            this.total = total;
            this.failed = failed;
            this.coverage = coverage;
        }

        public StageResult run(Context ctx) {
            ctx.data.put("failedTests", failed);
            ctx.data.put("coverage", coverage);
            ctx.data.put("maxCyclomatic", 8);
            if (failed > 0) {
                return new StageResult("单元测试", Status.FAIL, 1500,
                        total + " 个用例，" + failed + " 个失败（DiscountServiceTest）");
            }
            return new StageResult("单元测试", Status.PASS, 1500,
                    total + " 个用例全通过，覆盖率 " + coverage + "%");
        }
    }

    static class PackageStage implements Stage {
        public StageResult run(Context ctx) {
            String version = "1.0.0-" + ctx.data.get("commit");
            ctx.artifacts.add("order-service-" + version + ".jar");
            return new StageResult("打包", Status.PASS, 1200, "产物 order-service-" + version + ".jar");
        }
    }

    static class NotifyStage implements Stage {
        public StageResult run(Context ctx) {
            return new StageResult("通知", Status.PASS, 50, "已推送结果到群聊");
        }
    }

    static Pipeline standardPipeline(boolean compileBroken, int staticErrors,
                                     int tests, int failed, int coverage) {
        return new Pipeline()
                .add(new CheckoutStage())
                .add(new CompileStage(compileBroken))
                .add(new StaticCheckStage(staticErrors, 3))
                .add(new UnitTestStage(tests, failed, coverage))
                .add(new PackageStage())
                .add(new NotifyStage());
    }

    public static void main(String[] args) {
        standardPipeline(false, 0, 42, 0, 85).run("场景 1：一切正常");

        standardPipeline(true, 0, 42, 0, 85).run("场景 2：编译失败（fail fast，后续阶段不跑）");

        standardPipeline(false, 2, 42, 0, 85).run("场景 3：静态检查有 ERROR");

        standardPipeline(false, 0, 42, 3, 85).run("场景 4：单元测试有失败");

        standardPipeline(false, 0, 42, 0, 55).run("场景 5：覆盖率不达标");

        System.out.println("=== 阶段耗时分布（优化依据）===");
        String[][] cost = {
                {"单元测试", "1500", "并行拆分、去掉 Spring 容器"},
                {"打包", "1200", "增量编译、缓存 ~/.m2"},
                {"编译", "800", "只编译变更模块"},
                {"静态检查", "400", "只扫变更文件（增量模式）"},
                {"检出代码", "120", "浅克隆 + 缓存"}
        };
        System.out.printf("  %-12s %-10s %s%n", "阶段", "耗时ms", "优化手段");
        for (String[] r : cost) {
            System.out.printf("  %-12s %-10s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("=== 门禁阈值建议 ===");
        String[][] gate = {
                {"编译", "0 error", "硬阻断"},
                {"静态检查", "0 ERROR", "硬阻断"},
                {"单元测试", "0 失败", "硬阻断"},
                {"新增代码覆盖率", ">= 80%", "硬阻断"},
                {"整体覆盖率", ">= 70%", "提示，不阻断"},
                {"最大圈复杂度", "<= 10", "只对新增文件阻断"},
                {"高危依赖漏洞", "0", "硬阻断"}
        };
        System.out.printf("  %-18s %-12s %s%n", "指标", "阈值", "级别");
        for (String[] r : gate) {
            System.out.printf("  %-18s %-12s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("=== 流水线常见坑 ===");
        String[] pits = {
                "用 -Dmaven.test.failure.ignore=true 让失败测试不阻断",
                "测试连本地数据库，CI 上必然红",
                "覆盖率门槛设在总量，老代码拖累永远不达标",
                "用 sleep 等部署完成，改成健康检查轮询",
                "缓存 key 不含 pom 哈希，用到过期依赖",
                "只输出 BUILD FAILED，不告诉人哪一行错了",
                "流水线 40 分钟，开发者改成攒一天再提交"
        };
        for (String p : pits) {
            System.out.println("  - " + p);
        }
    }
}
```

**课后练习**
1. 给流水线加一个"依赖漏洞扫描"阶段，输出高危依赖数，并把"高危 > 0"设为门禁阻断项。
2. 把 `UnitTestStage` 改造成并行执行（两个测试分片各跑一半），并保证总耗时减半、结果合并正确。
3. 设计一条"只对新增代码生效"的覆盖率门禁规则，说明为什么它比"整体覆盖率"更容易落地。

### 33-11 综合案例：为订单模块搭建质量保障体系

**知识点**
- 质量保障体系由四层构成：**规范层**（编码规范、异常契约）、**测试层**（单元/集成/E2E 金字塔）、**门禁层**（静态检查 + 覆盖率 + CI 阻断）、**观测层**（线上监控、日志、告警）。缺一层就有盲区。
- 落地顺序：**先有测试，再有门禁**。没有测试就设覆盖率门禁，只会逼团队写假测试。
- 关键业务（资金、库存、权限）的保障标准要**高于**普通业务：分支覆盖 ≥ 90%、必须有并发测试、必须有对账/幂等验证。
- **回归清单**：每个线上 bug 修复后必须补一个能复现它的测试（回归测试），否则同类问题必然复发。
- **缺陷分析**：把线上 bug 按"哪个环节本该拦住"归类（单测漏了/集成漏了/门禁没设/规范没写），归因结果直接变成下一轮改进项。
- **质量度量**要少而准：缺陷逃逸率（线上 bug / 总 bug）、平均修复时间、CI 通过率、新增代码覆盖率。指标过多会失真。
- 综合案例的结构：领域模型 → 业务规则 → 测试金字塔（单测覆盖规则、集成覆盖协作、E2E 覆盖链路）→ 门禁判定 → 缺陷归因。
- 易错：只做单测不做集成（SQL 映射错误测不出）；只做 E2E 不做单测（失败无法定位）；门禁设了但没人看告警；线上 bug 修了不补测试。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QualitySystemCase {
    /** ---------- 领域模型 ---------- */
    static class Money {
        final long cents;

        Money(long cents) {
            this.cents = cents;
        }

        Money plus(Money o) {
            return new Money(Math.addExact(cents, o.cents));
        }

        Money minus(Money o) {
            return new Money(cents - o.cents);
        }

        Money percent(int p) {
            return new Money(cents * p / 100);
        }

        boolean gte(Money o) {
            return cents >= o.cents;
        }

        @Override
        public String toString() {
            return String.format("%d.%02d", cents / 100, cents % 100);
        }
    }

    enum OrderStatus {
        CREATED, PAID, SHIPPED, CANCELLED
    }

    static class Order {
        final String no;
        final Money amount;
        final Money paid;
        OrderStatus status;

        Order(String no, Money amount) {
            this.no = no;
            this.amount = amount;
            this.paid = new Money(0);
            this.status = OrderStatus.CREATED;
        }
    }

    /** ---------- 业务规则（被测核心）---------- */
    static class OrderDomain {
        /** 支付：只有 CREATED 可支付，金额必须完全匹配 */
        void pay(Order o, Money paid) {
            if (o.status != OrderStatus.CREATED) {
                throw new IllegalStateException("状态不允许支付: " + o.status);
            }
            if (!paid.gte(o.amount)) {
                throw new IllegalArgumentException(
                        "支付金额不足，应付 " + o.amount + "，实付 " + paid);
            }
            if (paid.cents > o.amount.cents) {
                throw new IllegalArgumentException("支付金额超额，应付 " + o.amount + "，实付 " + paid);
            }
            o.status = OrderStatus.PAID;
        }

        /** 取消：PAID 需退款，已发货不可取消 */
        Money cancel(Order o) {
            if (o.status == OrderStatus.SHIPPED) {
                throw new IllegalStateException("已发货不可取消");
            }
            if (o.status == OrderStatus.CANCELLED) {
                throw new IllegalStateException("订单已取消");
            }
            Money refund = o.status == OrderStatus.PAID ? o.amount : new Money(0);
            o.status = OrderStatus.CANCELLED;
            return refund;
        }

        /** 发货：只有 PAID 可发货 */
        void ship(Order o) {
            if (o.status != OrderStatus.PAID) {
                throw new IllegalStateException("只有已支付订单可发货，当前 " + o.status);
            }
            o.status = OrderStatus.SHIPPED;
        }
    }

    /** ---------- 测试框架（极简）---------- */
    static int passed;
    static int failed;
    static final List<String> failureDetails = new ArrayList<>();
    static final Map<String, Integer> layerCount = new LinkedHashMap<>();

    static void caseOf(String layer, String name, Runnable body) {
        try {
            body.run();
            passed++;
            layerCount.merge(layer, 1, Integer::sum);
            System.out.printf("    [%-5s] %-48s OK%n", layer, name);
        } catch (Throwable t) {
            failed++;
            layerCount.merge(layer, 1, Integer::sum);
            String d = layer + " / " + name + " -> " + t.getMessage();
            failureDetails.add(d);
            System.out.printf("    [%-5s] %-48s FAIL: %s%n", layer, name, t.getMessage());
        }
    }

    static void eq(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("期望 " + expected + "，实际 " + actual);
        }
    }

    static void throwsEx(Class<? extends Throwable> type, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return;
            }
            throw new AssertionError("期望 " + type.getSimpleName() + "，实际 " + t.getClass().getSimpleName());
        }
        throw new AssertionError("期望抛 " + type.getSimpleName() + "，但没抛");
    }

    /** ---------- 覆盖统计（模拟探针）---------- */
    static class Coverage {
        static final Map<String, Boolean> BRANCHES = new LinkedHashMap<>();

        static void mark(String b) {
            BRANCHES.put(b, true);
        }

        static void declare(String b) {
            BRANCHES.putIfAbsent(b, false);
        }

        static int percent() {
            long hit = BRANCHES.values().stream().filter(Boolean::booleanValue).count();
            return BRANCHES.isEmpty() ? 100 : (int) (hit * 100 / BRANCHES.size());
        }

        static List<String> missed() {
            List<String> list = new ArrayList<>();
            BRANCHES.forEach((k, v) -> {
                if (!v) {
                    list.add(k);
                }
            });
            return list;
        }
    }

    public static void main(String[] args) {
        OrderDomain domain = new OrderDomain();
        Coverage.declare("pay:CREATED");
        Coverage.declare("pay:非CREATED");
        Coverage.declare("pay:金额不足");
        Coverage.declare("pay:金额超额");
        Coverage.declare("cancel:SHIPPED");
        Coverage.declare("cancel:已取消");
        Coverage.declare("cancel:PAID退款");
        Coverage.declare("cancel:CREATED无退款");
        Coverage.declare("ship:PAID");
        Coverage.declare("ship:非PAID");

        System.out.println("=== 第一层：单元测试（业务规则）===");

        caseOf("UNIT", "CREATED 全额支付成功", () -> {
            Order o = new Order("O1", new Money(9_900));
            domain.pay(o, new Money(9_900));
            Coverage.mark("pay:CREATED");
            eq(OrderStatus.PAID, o.status);
        });

        caseOf("UNIT", "已支付订单再支付抛 IllegalState", () -> {
            Order o = new Order("O2", new Money(100));
            domain.pay(o, new Money(100));
            Coverage.mark("pay:CREATED");
            throwsEx(IllegalStateException.class, () -> domain.pay(o, new Money(100)));
            Coverage.mark("pay:非CREATED");
        });

        caseOf("UNIT", "支付金额不足抛 IllegalArgument", () -> {
            Order o = new Order("O3", new Money(10_000));
            throwsEx(IllegalArgumentException.class, () -> domain.pay(o, new Money(9_999)));
            Coverage.mark("pay:金额不足");
            eq(OrderStatus.CREATED, o.status);
        });

        caseOf("UNIT", "支付金额超额抛 IllegalArgument", () -> {
            Order o = new Order("O4", new Money(10_000));
            throwsEx(IllegalArgumentException.class, () -> domain.pay(o, new Money(10_001)));
            Coverage.mark("pay:金额超额");
            eq(OrderStatus.CREATED, o.status);
        });

        caseOf("UNIT", "已发货订单不可取消", () -> {
            Order o = new Order("O5", new Money(500));
            domain.pay(o, new Money(500));
            Coverage.mark("pay:CREATED");
            domain.ship(o);
            Coverage.mark("ship:PAID");
            throwsEx(IllegalStateException.class, () -> domain.cancel(o));
            Coverage.mark("cancel:SHIPPED");
        });

        caseOf("UNIT", "重复取消抛 IllegalState", () -> {
            Order o = new Order("O6", new Money(500));
            domain.cancel(o);
            Coverage.mark("cancel:CREATED无退款");
            throwsEx(IllegalStateException.class, () -> domain.cancel(o));
            Coverage.mark("cancel:已取消");
        });

        caseOf("UNIT", "已支付订单取消返回全额退款", () -> {
            Order o = new Order("O7", new Money(12_345));
            domain.pay(o, new Money(12_345));
            Coverage.mark("pay:CREATED");
            Money refund = domain.cancel(o);
            Coverage.mark("cancel:PAID退款");
            eq(12_345L, refund.cents);
            eq(OrderStatus.CANCELLED, o.status);
        });

        caseOf("UNIT", "未支付订单取消不退款", () -> {
            Order o = new Order("O8", new Money(777));
            Money refund = domain.cancel(o);
            Coverage.mark("cancel:CREATED无退款");
            eq(0L, refund.cents);
        });

        caseOf("UNIT", "未支付订单不可发货", () -> {
            Order o = new Order("O9", new Money(100));
            throwsEx(IllegalStateException.class, () -> domain.ship(o));
            Coverage.mark("ship:非PAID");
        });

        caseOf("UNIT", "Money.plus 溢出抛 ArithmeticException", () -> {
            throwsEx(ArithmeticException.class,
                    () -> new Money(Long.MAX_VALUE).plus(new Money(1)));
        });

        System.out.println();
        System.out.println("=== 第二层：集成测试（状态机全链路）===");

        caseOf("INTEG", "下单->支付->发货 状态链完整", () -> {
            Order o = new Order("I1", new Money(5_000));
            domain.pay(o, new Money(5_000));
            Coverage.mark("pay:CREATED");
            domain.ship(o);
            Coverage.mark("ship:PAID");
            eq(OrderStatus.SHIPPED, o.status);
            throwsEx(IllegalStateException.class, () -> domain.cancel(o));
            Coverage.mark("cancel:SHIPPED");
        });

        caseOf("INTEG", "下单->取消->再支付 被拒", () -> {
            Order o = new Order("I2", new Money(5_000));
            domain.cancel(o);
            Coverage.mark("cancel:CREATED无退款");
            throwsEx(IllegalStateException.class, () -> domain.pay(o, new Money(5_000)));
            Coverage.mark("pay:非CREATED");
        });

        System.out.println();
        System.out.println("=== 第三层：端到端（模拟接口契约）===");
        caseOf("E2E", "接口契约：支付成功返回 200 + PAID", () -> {
            Order o = new Order("E1", new Money(3_000));
            String resp = apiPay(domain, o, 3_000);
            Coverage.mark("pay:CREATED");
            eq("200|PAID", resp);
        });
        caseOf("E2E", "接口契约：金额不符返回 400", () -> {
            Order o = new Order("E2", new Money(3_000));
            String resp = apiPay(domain, o, 2_999);
            Coverage.mark("pay:金额不足");
            eq("400|支付金额不足", resp);
        });
        caseOf("E2E", "接口契约：状态错误返回 409", () -> {
            Order o = new Order("E3", new Money(3_000));
            domain.cancel(o);
            Coverage.mark("cancel:CREATED无退款");
            String resp = apiPay(domain, o, 3_000);
            Coverage.mark("pay:非CREATED");
            eq("409|状态不允许支付", resp);
        });

        System.out.println();
        System.out.println("=== 质量报告 ===");
        System.out.println("  用例总数: " + (passed + failed));
        System.out.println("  通过: " + passed + "  失败: " + failed);
        System.out.println("  分层分布: " + layerCount);
        System.out.printf("  分支覆盖率: %d%%（%d 条分支，未覆盖 %s）%n",
                Coverage.percent(), Coverage.BRANCHES.size(), Coverage.missed());
        for (String d : failureDetails) {
            System.out.println("    失败: " + d);
        }

        System.out.println();
        System.out.println("=== 质量门禁判定 ===");
        List<String> gates = new ArrayList<>();
        if (failed > 0) {
            gates.add("单元/集成/E2E 存在失败用例");
        }
        if (Coverage.percent() < 90) {
            gates.add("分支覆盖率 " + Coverage.percent() + "% < 90%（资金链路要求）");
        }
        if (!Coverage.missed().isEmpty()) {
            gates.add("存在未覆盖分支: " + Coverage.missed());
        }
        if (gates.isEmpty()) {
            System.out.println("  结果: PASS（可合并）");
        } else {
            System.out.println("  结果: BLOCKED");
            for (String g : gates) {
                System.out.println("    - " + g);
            }
        }

        System.out.println();
        System.out.println("=== 缺陷归因表（把线上 bug 变成改进项）===");
        String[][] attribution = {
                {"支付金额超额未校验", "单测漏了超额分支", "补边界用例 + 分支覆盖门禁"},
                {"已发货订单被取消", "集成测试未覆盖非法迁移", "状态机全迁移用例"},
                {"并发支付重复扣款", "无并发测试", "加幂等键 + 并发用例"},
                {"退款金额算错", "金额用 double 精度丢失", "金额改整数分 + 静态规则"},
                {"接口 500 而非 400", "异常映射未测", "E2E 覆盖异常契约"}
        };
        System.out.printf("  %-22s %-24s %s%n", "线上缺陷", "本该在哪拦住", "改进项");
        for (String[] r : attribution) {
            System.out.printf("  %-22s %-24s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("=== 四层保障体系 ===");
        String[][] layers = {
                {"规范层", "异常契约、金额用整数分、命名规范", "Code Review + 静态检查"},
                {"测试层", "单测覆盖规则、集成覆盖状态机、E2E 覆盖契约", "本课三层用例"},
                {"门禁层", "0 失败、分支覆盖 >= 90%、0 ERROR", "CI 阻断合并"},
                {"观测层", "支付失败率、状态机异常告警、对账", "线上监控"}
        };
        System.out.printf("  %-8s %-44s %s%n", "层级", "内容", "载体");
        for (String[] r : layers) {
            System.out.printf("  %-8s %-44s %s%n", r[0], r[1], r[2]);
        }
    }

    /** 模拟接口层：把领域异常映射成 HTTP 语义 */
    static String apiPay(OrderDomain domain, Order o, long cents) {
        try {
            domain.pay(o, new Money(cents));
            return "200|PAID";
        } catch (IllegalArgumentException e) {
            return "400|" + e.getMessage().split("，")[0];
        } catch (IllegalStateException e) {
            return "409|" + e.getMessage().split(":")[0];
        }
    }
}
```

**课后练习**
1. 为"并发支付同一订单"补一个并发用例：10 个线程同时支付，断言只有 1 次成功、状态为 PAID、不重复扣款。
2. 给 `Order` 增加 `version` 字段实现乐观锁，补一个"版本冲突抛异常"的用例，并说明它对应哪条线上缺陷。
3. 把本课的三层用例整理成一张"用例-规则-分支"矩阵，找出仍有规则未被任何用例覆盖的格子。
