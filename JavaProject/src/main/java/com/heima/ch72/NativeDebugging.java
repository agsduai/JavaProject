/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-06
 * 课名：原生镜像的调试与排障
 * ------------------------------------------------------------
 * 知识点：
 *   - **native 镜像的典型故障**：**`NoSuchMethodException`**（反射未配置）、**`FileNotFoundException`/null 资源**（资源未配置）、**`Proxy` 创建失败**（代理未配置）、**构建期初始化错误**（静态块连了外部服务）、**`NoClassDefFoundError`**（可达性剪枝误删）。
 *   - **排障四步**：**看异常栈**（native 的栈比 JVM 简略）-> **用 `--trace-class-initialization`/`--trace-object-instantiation`**（定位类初始化）-> **用 agent 重新收集配置**（补充遗漏）-> **本地最小复现**（剥离到最小用例）。
 *   - **日志与诊断能力的差异**：**没有 JFR 的完整能力**、**没有 attach 调试器**（部分支持 gdb 级调试）、**堆转储受限**——**所以要在构建期做好"可观测性预埋"**（42 章的埋点必须在镜像里生效）。
 *   - **与 66 章的关系**：Agent 在 native 下**不可用**（没有字节码）——**AOT 与 Agent 是互斥的运行时形态**，监控方案要换（用框架埋点/代理层）。
 *   - **排障的工程化**：**保留 JVM 版本并行**（native 出问题可切回）、**灰度发布**（41-07）、**集成测试覆盖**（native 下跑一遍全量用例）。
 *   - 易错点：只跑单元测试不跑集成测试（反射问题在启动时才暴露）；出问题就回滚不排查（丢弃 native 收益）；本地能跑镜像不能跑（构建环境差异）；无 JVM 版本兜底（故障时无退路）。
 * ============================================================
 */

package com.heima.ch72;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class NativeDebugging {
    record Symptom(String error, String cause, String fix) {
    }

    static void symptomTable() {
        System.out.println("【常见故障与修复】");
        List<Symptom> symptoms = List.of(
                new Symptom("NoSuchMethodException: X.getY()",
                        "反射调用未在 reflect-config 注册", "补 reflect-config 或收窄反射使用"),
                new Symptom("getResourceAsStream 返回 null",
                        "资源文件未打进镜像", "补 resource-config（含通配符）"),
                new Symptom("IllegalArgumentException: interface not visible",
                        "动态代理接口未在 proxy-config", "补 proxy-config"),
                new Symptom("构建时报连接超时",
                        "某类在构建期初始化并连接外部服务", "标记该类为运行期初始化"),
                new Symptom("NoClassDefFoundError 运行期",
                        "动态加载的类被可达性剪枝删除", "显式注册或改为静态引用"),
                new Symptom("配置值全是默认值",
                        "配置类在构建期初始化，读到构建机环境", "改为运行期初始化"));
        for (Symptom s : symptoms) {
            System.out.printf("  %-38s%n    原因：%-28s 修复：%s%n",
                    s.error(), s.cause(), s.fix());
        }
        System.out.println();
    }

    static void diagnoseFlow() {
        System.out.println("【排障四步】");
        Map<String, String> steps = new LinkedHashMap<>();
        steps.put("1 看栈", "native 栈信息较少，先定位到类与方法");
        steps.put("2 跟踪", "--trace-class-initialization=类名 / --trace-object-instantiation");
        steps.put("3 收集", "-agentlib:native-image-agent 跑测试，补配置");
        steps.put("4 复现", "剥离到最小用例（单类 + 单配置）验证");
        steps.forEach((k, v) -> System.out.printf("  %s. %s%n", k, v));
        System.out.println();
    }

    static void capabilities() {
        System.out.println("【能力差异：JVM vs native】");
        String[][] rows = {
                {"JFR", "完整", "受限（部分事件不可用）"},
                {"Heap dump", "支持", "受限"},
                {"Attach 调试器", "支持", "不支持（需 gdb 级）"},
                {"动态 Agent", "支持（66 章）", "不可用（无字节码）"},
                {"System.gc 手动触发", "支持", "受限（GC 策略固定）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-18s JVM=%-12s native=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  含义：可观测性要在构建期预埋（42 章埋点必须生效）");
        System.out.println();
    }

    static void rollbackPlan() {
        System.out.println("【上线策略：保留两条路】");
        List<String> plan = List.of(
                "构建双产物：JVM 镜像 + native 镜像（都推仓库）",
                "灰度：先 5% 流量走 native（41-07）",
                "对比指标：启动时间/内存/P99 错误率/业务指标",
                "异常 -> 切回 JVM 镜像（秒级，41-11 回滚）",
                "全量后保留 JVM 镜像 1 个月作兜底");
        plan.forEach(p -> System.out.println("  - " + p));
    }

    public static void main(String[] args) {
        symptomTable();
        diagnoseFlow();
        capabilities();
        rollbackPlan();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 构造一个"反射未配置"的 native 故障模拟，按四步排障流程走一遍，输出每一步的证据。
 * 2. 列出你服务的"动态特性清单"（反射/代理/资源/动态类加载），标注哪些需要 AOT 配置。
 * 3. 思考题：为什么 native 下"Agent 不可用"？从"没有字节码可增强"与"类在构建期已编译"分析。
 * ============================================================
 */
