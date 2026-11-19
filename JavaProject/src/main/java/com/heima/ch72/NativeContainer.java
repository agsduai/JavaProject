/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-08
 * 课名：与容器/Serverless 结合
 * ------------------------------------------------------------
 * 知识点：
 *   - **native 镜像天然适合容器**：**镜像小**（基于 `scratch` 或 `distroless`，20~80MB vs JVM 镜像 200MB+）、**启动快**（毫秒级，滚动更新/扩容瞬时完成）、**内存低**（同样资源跑更多副本）。
 *   - **与 37 章的结合**：容器的"秒级启动"是为了应用启动服务的——**如果应用启动要 30 秒，容器秒级启动也没用**（37-01 的对照）；native 让"容器快"真正落地。
 *   - **与 47-07 Serverless 的结合**：FaaS 的核心成本是**冷启动计费时间**——native 把冷启动从秒级降到毫秒级，**直接降低 FaaS 成本并提升体验**（72-01 的成本模型）。
 *   - **构建与部署实践**：**多阶段构建**（构建阶段用 GraalVM 镜像，运行阶段用 scratch）、**静态链接**（glibc vs musl 选择，跨发行版更稳）、**镜像扫描**（36-10 的供应链安全，native 镜像无 shell 更利于安全）。
 *   - **与 41/47 章的联动**：CI 流水线增加 native 构建（41-03 的产物）、K8s 的启动探针可缩短（47-02 的 `startupProbe` 从 30s 改 1s）、HPA 扩容更快（47-08）。
 *   - 易错点：用 libc 动态链接（跨基础镜像版本可能失败）；镜像里留 shell（安全面变大）；CI 构建时间过长（native 构建 5~15 分钟）；忘记调小 `startupProbe`（白白等待）。
 * ============================================================
 */

package com.heima.ch72;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class NativeContainer {
    static void imageCompare() {
        System.out.println("【镜像体积与启动】");
        String[][] rows = {
                {"JVM + 完整 JDK 镜像", "~450MB", "15~30s", "启动慢、镜像大"},
                {"JVM + JRE slim", "~200MB", "10~20s", "常见折衷"},
                {"native + distroless", "~60~120MB", "0.05~0.2s", "最优启动"},
                {"native + scratch", "~30~60MB", "0.05~0.1s", "最小，需静态链接"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-24s %-12s %-12s %s%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println();
    }

    /** 扩容速度：从 0 到可服务 */
    static void scaleOut() {
        System.out.println("【扩容到可服务的时间】");
        double jvmBoot = 20, nativeBoot = 0.15;
        double imagePullJvm = 25, imagePullNative = 4;   // 镜像拉取（秒）
        double probeJvm = 30, probeNative = 2;           // 探针等待
        double totalJvm = imagePullJvm + jvmBoot + probeJvm;
        double totalNative = imagePullNative + nativeBoot + probeNative;
        System.out.printf("  JVM：拉镜像 %.0fs + 启动 %.0fs + 探针等待 %.0fs = %.0fs%n",
                imagePullJvm, jvmBoot, probeJvm, totalJvm);
        System.out.printf("  native：拉镜像 %.0fs + 启动 %.1fs + 探针等待 %.0fs = %.1fs%n",
                imagePullNative, nativeBoot, probeNative, totalNative);
        System.out.printf("  差距 %.0f 倍 -> 突发流量的响应能力完全不同（47-08）%n",
                totalJvm / totalNative);
        System.out.println();
    }

    static void buildPipeline() {
        System.out.println("【多阶段构建】");
        Map<String, String> stages = new LinkedHashMap<>();
        stages.put("阶段 1 构建", "FROM ghcr.io/graalvm/native-image AS build；拷贝源码并 native-image 编译");
        stages.put("阶段 2 运行", "FROM gcr.io/distroless/base（或 scratch）；只 COPY 可执行文件");
        stages.put("产物", "单文件可执行（无 JVM、无 shell、无包管理器）");
        stages.put("收益", "体积小、攻击面小（36-10）、启动快");
        stages.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.println();
    }

    static void k8sTuning() {
        System.out.println("【K8s 配置调整（对比 JVM 版）】");
        String[][] rows = {
                {"startupProbe.initialDelay", "30s", "1s"},
                {"startupProbe.failureThreshold", "30", "5"},
                {"livenessProbe.period", "10s", "5s"},
                {"resources.requests.memory", "512Mi", "128Mi"},
                {"HPA 扩容响应", "~1min", "~10s"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-32s JVM=%-10s native=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  注意：native 内存更省但峰值 CPU 可能更高（无 JIT 优化）-> 压测确认 limits");
    }

    public static void main(String[] args) {
        imageCompare();
        scaleOut();
        buildPipeline();
        k8sTuning();
        System.out.println("  与 37 章的关系：容器让交付一致，native 让「容器快」名副其实");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 计算"从 0 扩容到可服务"在 JVM 与 native 下的耗时（含拉镜像与探针），量化对突发流量的影响。
 * 2. 写出 native 多阶段 Dockerfile（构建阶段 + distroless 运行阶段），标注每行的作用。
 * 3. 思考题：为什么 native 镜像"攻击面更小"？从"无 shell/无包管理器/无 JVM"分析（36 章供应链安全）。
 * ============================================================
 */
