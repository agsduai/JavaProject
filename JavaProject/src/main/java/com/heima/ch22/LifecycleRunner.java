/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：22-06
 * 课名：生命周期与常用命令
 * ------------------------------------------------------------
 * 知识点：
 *   - Maven 有三套相互独立的生命周期：`clean`（清理）、`default`（构建）、`site`（站点）。
 *   - `default` 生命周期的关键阶段依次是：`validate → compile → test → package → verify → install → deploy`。
 *   - 执行某个阶段时，**它前面的所有阶段都会先执行**；所以 `mvn package` 会自动先 `compile` 和 `test`。
 *   - 每个阶段绑定若干插件的 goal，比如 `package` 阶段默认绑定 `maven-jar-plugin:jar`。
 *   - 常用命令：`mvn clean`、`mvn compile`、`mvn test`、`mvn package`、`mvn install`、`mvn clean package -DskipTests`。
 *   - `install` 把产物装进本地仓库供其它项目引用，`deploy` 推送到私服供团队引用。
 *   - 易错：`-DskipTests` 是跳过执行但仍编译测试代码；`-Dmaven.test.skip=true` 连编译都跳过。
 * ============================================================
 */

package com.heima.ch22;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// ==================== 示例代码 ====================

public class LifecycleRunner {
    public static void main(String[] args) {
        List<String> defaultLifecycle = new ArrayList<>(List.of(
                "validate", "initialize", "generate-sources", "process-sources",
                "generate-resources", "process-resources", "compile", "process-classes",
                "generate-test-sources", "process-test-sources", "generate-test-resources",
                "process-test-resources", "test-compile", "process-test-classes",
                "test", "prepare-package", "package", "verify", "install", "deploy"));

        List<String> cleanLifecycle = new ArrayList<>(List.of(
                "pre-clean", "clean", "post-clean"));

        run(cleanLifecycle, "clean", "mvn clean");
        run(defaultLifecycle, "package", "mvn clean package");

        System.out.println("说明：执行 package 时，它之前的所有阶段都会依次执行；");
        System.out.println("      install / deploy 在 package 之后，所以没有被触发。");
    }

    // 执行到 target 阶段为止（包含 target）
    private static void run(List<String> lifecycle, String target, String command) {
        int stop = lifecycle.indexOf(target);
        if (stop < 0) {
            System.out.println(command + " -> 未知阶段：" + target);
            return;
        }
        System.out.println("$ " + command);
        List<String> executed = new ArrayList<>(lifecycle.subList(0, stop + 1));
        for (int i = 0; i < executed.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + executed.get(i));
        }
        System.out.println("  共执行 " + executed.size() + " 个阶段，最后停在 " + target);
        System.out.println("  跳过的后续阶段：" + lifecycle.subList(stop + 1, lifecycle.size()));
        List<String> common = Arrays.asList("clean", "package", "install", "deploy");
        System.out.println("  提示：" + target + (common.contains(target) ? " 是常用目标阶段" : " 不是常用目标阶段"));
        System.out.println();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 依次执行 `mvn compile`、`mvn test`、`mvn package`、`mvn install`，观察每个命令实际触发了哪些阶段。
 * 2. 说明 `mvn clean package -DskipTests` 和 `-Dmaven.test.skip=true` 的区别。
 * 3. 如果 `package` 之后想立刻让另一个本地项目用上新版本，应该执行哪个命令？为什么是它而不是 `deploy`？
 * ============================================================
 */
