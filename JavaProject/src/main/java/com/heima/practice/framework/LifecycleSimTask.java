/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 22-02
 * 课名：生命周期与插件钩子
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现迷你生命周期：validate -> compile -> test -> package 四个阶段（有序执行，前序失败即停）；插件绑定到阶段（compile 阶段挂 compiler 插件、test 段挂 surefire）；实现 `mvn package` 语义（执行到 package 含之前全部阶段）；跳过测试的 -DskipTests 语义实现。
 *   - 【考核点】生命周期相位有序性、插件目标绑定、命令映射到相位区间、skip 参数语义。
 *   - 【易错点】以为 `mvn package` 只跑 package（实际包含前置相位）；插件绑定错相位（测试插件挂到 compile）；跳过测试后 package 仍依赖 test 产物。
 *   - 【提示】相位用有序列表，执行区间 = 从头到目标相位；skipTests 只影响 test 相位的插件目标。
 * ============================================================
 */

package com.heima.practice.framework;

import java.util.List;

// ==================== 示例代码 ====================

public class LifecycleSimTask {
    static final List<String> PHASES = List.of("validate", "compile", "test", "package");

    static void run(String target, boolean skipTests) {
        int end = PHASES.indexOf(target);
        for (int i = 0; i <= end; i++) {
            String phase = PHASES.get(i);
            if (skipTests && phase.equals("test")) {
                System.out.println("  " + phase + " -> SKIPPED");
                continue;
            }
            System.out.println("  " + phase + " -> 执行绑定插件");
        }
    }

    public static void main(String[] args) {
        run("package", true);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整生命周期模拟（相位 + 插件绑定 + skip + 失败即停），用"test 失败"验证 package 不执行。
 * 2. 实现自定义插件绑定（clean 相位挂删除临时文件目标），演示绑定配置。
 * 3. 思考题：为什么 `mvn test` 会先执行 validate/compile？从生命周期的设计目的（保证前置状态）分析。
 * 
 * 
 * ## 第23章 MyBatis
 * ============================================================
 */
