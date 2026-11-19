/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-01
 * 课名：为什么需要原生镜像
 * ------------------------------------------------------------
 * 知识点：
 *   - **JVM 的两个"贵"**：**启动慢**（类加载 + 解释执行 + JIT 预热，Spring Boot 应用常见 10~30 秒）与**内存高**（JVM 固定开销 + 元空间 + JIT 代码缓存，小服务常驻 300MB+）——**在 Serverless/弹性场景下，这两点直接决定成本与体验**（47-07 的冷启动问题）。
 *   - **原生镜像（Native Image）的思路**：**AOT（提前编译）**——在构建期把字节码编译成机器码，产出**独立可执行文件**（不依赖 JVM），**启动毫秒级、内存几十 MB**。
 *   - **代价也是两个**：**构建慢**（分钟级，需完整静态分析）、**峰值性能可能不如 JIT**（JIT 能在运行时根据实际热点优化，AOT 只能按静态信息优化）——**"启动快"与"峰值高"是一对取舍**。
 *   - **适用场景的判据**：**启动时间敏感**（Serverless/FaaS、CLI 工具、短生命周期任务）、**内存受限**（边缘/嵌入式）、**扩容频繁**（弹性伸缩）——**长驻高吞吐服务未必需要**（预热后 JIT 更强）。
 *   - 与 37/47 章的关系：容器化解决"环境一致"，原生镜像解决"启动与内存"——**两者结合是 Serverless 的最优形态**（72-08）。
 *   - 易错点：以为原生镜像"全面更快"（峰值往往更慢）；不考虑构建时间（CI 变慢）；忽略动态特性不兼容（反射/代理 72-03）；盲目全量迁移（应先评估 72-10）。
 * ============================================================
 */

package com.heima.ch72;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WhyNativeImage {
    record Dimension(String name, String jvm, String nativeImage, String verdict) {
    }

    static void compare() {
        System.out.println("【JVM vs 原生镜像】");
        List<Dimension> dims = List.of(
                new Dimension("启动时间", "10~30s（Spring Boot）", "0.05~0.2s", "原生大幅胜出"),
                new Dimension("常驻内存", "200~500MB", "30~100MB", "原生胜出"),
                new Dimension("峰值吞吐", "高（JIT 优化）", "略低（静态编译）", "JVM 胜出"),
                new Dimension("构建时间", "秒级", "分钟级（静态分析）", "JVM 胜出"),
                new Dimension("动态能力", "反射/代理/动态类全支持", "需显式配置", "JVM 胜出"),
                new Dimension("可调试性", "成熟（JFR/调试器）", "受限（无 JIT 视图）", "JVM 胜出"));
        for (Dimension d : dims) {
            System.out.printf("  %-8s JVM=%-24s 原生=%-20s %s%n",
                    d.name(), d.jvm(), d.nativeImage(), d.verdict());
        }
        System.out.println();
    }

    static void fitMatrix() {
        System.out.println("【适用场景判断】");
        String[][] rows = {
                {"Serverless / FaaS", "强推荐", "冷启动决定体验与成本"},
                {"CLI 工具", "强推荐", "毫秒级启动，用户无感"},
                {"边缘/嵌入式", "推荐", "内存受限"},
                {"长驻高吞吐服务", "不推荐", "预热后 JIT 更强"},
                {"依赖大量反射的框架", "谨慎", "配置成本高（72-03）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-20s %-8s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void costModel() {
        System.out.println("【成本模型：启动时间如何换算成钱】");
        long requestsPerDay = 100_000;
        double startupSec = 15;
        double nativeStartupSec = 0.1;
        long coldStartsPerDay = 500;
        double jvmSeconds = coldStartsPerDay * startupSec;
        double nativeSeconds = coldStartsPerDay * nativeStartupSec;
        System.out.printf("  日请求 %,d，日冷启动 %d 次%n", requestsPerDay, coldStartsPerDay);
        System.out.printf("  JVM 冷启动耗时 %.0f 秒/日；原生 %.0f 秒/日（省 %.0f%%）%n",
                jvmSeconds, nativeSeconds, (1 - nativeSeconds / jvmSeconds) * 100);
        System.out.println("  换算成计费时间（按秒计费）：省的启动时间 = 直接的成本节省");
        System.out.println("  更关键的是用户体验：冷启动 15s 几乎等于不可用");
        System.out.println();
    }

    public static void main(String[] args) {
        compare();
        fitMatrix();
        costModel();
        System.out.println("  结论：启动/内存敏感 -> 原生；峰值/动态能力敏感 -> JVM");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 量化你服务的启动时间与常驻内存，按"日冷启动次数"算 JVM 与原生镜像的成本差异（可用模拟参数）。
 * 2. 为 6 类服务（FaaS/CLI/边缘/长驻/批处理/桌面）判断是否适合原生镜像，写出判据。
 * 3. 思考题：为什么"原生镜像峰值性能可能更低"？从 JIT 的运行时热点优化与 AOT 的静态分析局限对比。
 * ============================================================
 */
