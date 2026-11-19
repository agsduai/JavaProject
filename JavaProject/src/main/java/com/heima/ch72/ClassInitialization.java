/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-04
 * 课名：类初始化时机
 * ------------------------------------------------------------
 * 知识点：
 *   - **AOT 的类初始化有两个时机**：**构建期初始化**（`--initialize-at-build-time`，把静态初始化提前到构建，启动更快、内存更省）与**运行期初始化**（默认，`--initialize-at-run-time`）——**选错会导致"构建期读了构建机的配置"**（如数据库连接在构建期被建立）。
 *   - **构建期初始化的适用**：**纯常量计算**（静态常量表、枚举映射）、**无副作用的工具类**——**安全且收益大**（省启动时间）。
 *   - **运行期初始化的必须**：**读环境变量/配置/文件**、**连数据库/网络**、**依赖运行时状态的单例**——**构建期执行会用构建机的环境，产出错误结果**（最隐蔽的 bug）。
 *   - **Spring Boot 的处理**：框架大部分类标记为构建期初始化（提升启动），用户类默认运行期——**自定义 `@Configuration` 若在静态块里读了环境变量，必须标记运行期**。
 *   - **与 66 章的关系**：Agent 也是"在类加载前后做事"（premain 在 main 前）——**AOT 的"构建期初始化"相当于把"运行期的一次性初始化"提前到构建期**，本质是"时间前移"。
 *   - 易错点：静态块读环境变量（构建期读到构建机的值）；静态单例连数据库（构建失败或连错库）；随机数/时间戳在构建期固定（每次启动都是同一个值）；无 `--trace-class-initialization` 排查（不知道哪个类在构建期初始化了）。
 * ============================================================
 */

package com.heima.ch72;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ClassInitialization {
    /** 类定义：静态初始化做什么 */
    record ClassDef(String name, String staticInit, boolean pureComputation,
                    boolean readsEnvironment, String recommendation) {
    }

    static final List<ClassDef> classes = new ArrayList<>();

    static {
        classes.add(new ClassDef("Constants", "构建常量表（如枚举映射）", true, false,
                "构建期初始化（安全，启动更快）"));
        classes.add(new ClassDef("DateFormats", "创建 DateTimeFormatter 实例", true, false,
                "构建期初始化（无状态、线程安全）"));
        classes.add(new ClassDef("DbConfig", "static { 读 DB_URL 环境变量 }", false, true,
                "运行期初始化（构建期会读到构建机的值）"));
        classes.add(new ClassDef("CacheWarmup", "static { 连接 Redis 预加载 }", false, true,
                "运行期初始化（副作用 + 外部依赖）"));
        classes.add(new ClassDef("InstanceId", "static { id = UUID.randomUUID() }", false,
                true, "运行期初始化（否则所有实例同一个 ID）"));
        classes.add(new ClassDef("RegExPool", "预编译正则 Pattern", true, false,
                "构建期初始化（无损）"));
    }

    /** 模拟构建：把"构建期初始化的类"在构建时执行一次 */
    static void simulateBuild(String buildMachineEnv) {
        System.out.println("【构建期执行静态初始化（构建机环境：" + buildMachineEnv + "）】");
        Map<String, String> builtValues = new LinkedHashMap<>();
        for (ClassDef c : classes) {
            if (c.readsEnvironment()) {
                builtValues.put(c.name(), "构建机值：" + buildMachineEnv);
            } else if (c.pureComputation()) {
                builtValues.put(c.name(), "纯计算结果（与机器无关）");
            }
        }
        builtValues.forEach((k, v) -> System.out.printf("  %-14s -> %s%n", k, v));
        System.out.println();

        System.out.println("【部署到生产（生产环境：prod-db）】");
        for (ClassDef c : classes) {
            if (c.readsEnvironment()) {
                System.out.printf("  %-14s 若构建期初始化 -> 仍用「%s」（错！）%n",
                        c.name(), builtValues.get(c.name()));
                System.out.printf("  %-14s 运行期初始化 -> 读到「prod-db」（正确）%n",
                        c.name());
            }
        }
        System.out.println();
    }

    static void decisionTable() {
        System.out.println("【初始化时机决策表】");
        String[][] rows = {
                {"纯常量/无副作用", "构建期", "启动快、内存省"},
                {"读环境变量/配置", "运行期", "避免构建机环境泄漏"},
                {"连数据库/网络", "运行期", "构建期不该有外部依赖"},
                {"生成随机数/时间戳", "运行期", "否则所有实例共享同一值"},
                {"预编译正则/格式化器", "构建期", "无损且有收益"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-20s %-8s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        simulateBuild("build-agent-ci");
        decisionTable();
        System.out.println("【排查手段】--trace-class-initialization=类名 观察该类在哪个阶段初始化");
        System.out.println("  Spring Boot：默认用户类运行期初始化；有静态副作用的类显式标注");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"初始化时机决策表"判定器：给定类定义（是否读环境/是否有副作用），输出建议时机与理由。
 * 2. 复现"构建期读环境变量"的 bug：构建机是测试库，生产启动后仍连测试库，说明修复方式。
 * 3. 思考题：为什么"随机数在构建期固定"是隐蔽 bug？从"所有实例共享同一 ID"的后果分析。
 * ============================================================
 */
