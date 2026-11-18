/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-09
 * 课名：模板方法与回调
 * ------------------------------------------------------------
 * 知识点：
 *   - **模板方法**：父类定义算法骨架（`final` 方法），把可变步骤留给子类实现（`abstract` 或可覆盖的钩子）。它把"流程"和"细节"分开。
 *   - 三个角色：**模板方法**（`final`，定顺序）、**抽象方法**（必须实现）、**钩子方法**（`protected` 空实现或默认实现，子类可选覆盖）。
 *   - 模板方法常与"钩子"配合控制流程：`boolean needValidate()` 默认返回 `true`，子类可返回 `false` 跳过某步。
 *   - 里氏替换在这里的体现：模板方法必须对所有子类都成立，所以父类里不能假设"子类一定如何"，只能依赖抽象步骤。
 *   - **回调**：把一段逻辑作为参数传进去，由被调用方在合适时机执行。Java 里用函数式接口（`Runnable`、`Supplier`、`Consumer`、自定义 `@FunctionalInterface`）表达。
 *   - 回调 vs 模板方法的区别：模板方法靠**继承**，编译期绑定，一个类只能有一个父类；回调靠**组合**，运行期传入，可以任意组合多个。
 *   - 现代 Java 更推荐回调（组合优于继承）：`list.forEach(...)`、`Optional.map(...)`、`CompletableFuture.thenApply(...)` 都是回调思想。
 *   - 模板方法的常见误用：父类塞了太多具体逻辑导致子类难以覆盖；钩子方法命名不清（`doSomething` 到底是必须还是可选）。
 *   - 易错：模板方法忘了 `final`，子类把整个流程改掉；回调里抛异常无人处理；回调中捕获了外部可变变量造成意外共享。
 * ============================================================
 */

package com.heima.ch31;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `DataImporter` 增加一个 `afterWrite()` 钩子（默认空实现），让 `CsvImporter` 覆盖它打印统计信息。
 * 2. 用 `RetryTemplate.execute` 实现"指数退避"重试（100ms、200ms、400ms），说明为什么实际项目要加随机抖动。
 * 3. 把 `DataImporter` 的继承结构改成"骨架 + 回调"（传 `Function`/`Consumer`），对比两者在扩展性上的差异。
 * ============================================================
 */
