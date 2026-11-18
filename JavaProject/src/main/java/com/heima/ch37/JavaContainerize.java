/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-08
 * 课名：Java 应用的容器化实践
 * ------------------------------------------------------------
 * 知识点：
 *   - **JVM 必须按容器限额来配置内存**：现代 JDK（8u191+/10+）默认 `UseContainerSupport`，能读 cgroup 限额。但**显式参数仍然优先**：`-Xmx` 写死了就不再感知容器。推荐用百分比方式：`-XX:MaxRAMPercentage=75.0`，给元空间、线程栈、直接内存留出余量。限额 1G 的容器，堆最多 768M，再叠 Netty 堆外缓冲与 Metaspace，实际也到顶了。
 *   - **CPU 配额同样影响 JVM**：容器限 0.5 核时，JVM 默认按配额算 `ActiveProcessorCount`，进而决定 GC 线程数与 ForkJoinPool 大小；老 JDK 会按宿主机 16 核建 15 条 GC 线程，在小容器里互相踩踏。
 *   - **exec 形式启动是优雅停机的前提**：`ENTRYPOINT ["java", "-jar", "app.jar"]` 让 java 直接成为 1 号进程，SIGTERM 直达 JVM；`ENTRYPOINT java -jar app.jar`（shell 形式）实际是 `sh -c ...`，SIGTERM 给了 sh，java 收不到，`docker stop` 必等满 10 秒。
 *   - **分层 jar 让依赖层稳定**：Spring Boot 的 layered jar 把依赖、loader、应用类拆成多层，依赖不变时 `COPY` 依赖层缓存命中，只有几 MB 的应用层重建 —— 改一行代码的镜像构建从分钟级降到秒级。
 *   - **基础镜像的选择是运行时契约**：`temurin:21-jre`（完整，几百 MB）、`21-jre-alpine`（小但 musl libc，字体/时区/JNI 库可能踩坑）、`distroless/java`（只有运行时，最安全最小，无 shell 可调试性差）。选型标准是"需要什么能力"而不是"越小越好"。
 *   - **镜像里常被遗忘的系统级配置**：时区（`TZ=Asia/Shanghai`）、字符集（日志乱码多因基础镜像无 locale）、DNS 缓存（JVM 默认缓存 30s，K8s 环境常调成 `networkaddress.cache.ttl=10`）、容器内 `ulimit`。
 *   - **可观测性从镜像开始**：把 `/actuator/health` 作为 HEALTHCHECK；日志一律 stdout/stderr（容器日志驱动收集），不要写文件进容器；开启 `-XX:+ExitOnOutOfMemoryError` 让 OOM 时快速失败，交给编排层重启。
 *   - 易错点：`-Xmx` 按宿主机内存写死（进小容器必被 137 杀）；shell 形式 ENTRYPOINT（优雅停机失效）；镜像里写日志文件路径（磁盘涨满）；alpine 上跑带字体渲染/JNI 的应用（缺 glibc 依赖崩溃）。
 * ============================================================
 */

package com.heima.ch37;

import java.util.List;

// ==================== 示例代码 ====================

public class JavaContainerize {
    record Cgroup(long memoryLimitMb, double cpuQuota) {
    }

    static void memoryBudget() {
        System.out.println("【容器限额下的 JVM 内存预算】");
        Cgroup c = new Cgroup(1024, 1.0);
        long heap = Math.round(c.memoryLimitMb() * 0.75);
        long metaspace = 96, direct = 64, stackAndOther = 64;
        long sum = heap + metaspace + direct + stackAndOther;
        System.out.printf("  容器限额 %dMB / %.1f 核%n", c.memoryLimitMb(), c.cpuQuota());
        System.out.printf("  堆          MaxRAMPercentage=75%% -> %dMB%n", heap);
        System.out.printf("  Metaspace   约 %dMB%n", metaspace);
        System.out.printf("  直接内存/Netty 约 %dMB%n", direct);
        System.out.printf("  线程栈与其他 约 %dMB%n", stackAndOther);
        System.out.printf("  合计 %dMB <= 限额 %dMB，余量 %dMB%n", sum, c.memoryLimitMb(),
                c.memoryLimitMb() - sum);
        System.out.println("  反例：-Xmx4g 写死 + 限额 1G -> 分配即 OOMKilled(137)");
        System.out.println();
    }

    static void cpuAwareness() {
        System.out.println("【CPU 配额对 JVM 内部线程数的影响】");
        long hostCores = Runtime.getRuntime().availableProcessors();
        double quota = 0.5;
        int containerAware = Math.max(1, (int) Math.round(quota));
        long legacy = hostCores;
        System.out.printf("  宿主机核数=%d，容器配额=%.1f 核%n", hostCores, quota);
        System.out.printf("  新 JDK：ActiveProcessorCount=%d -> GC 线程≈%d，FJP 并行度=%d%n",
                containerAware, containerAware, containerAware);
        System.out.printf("  老 JDK：按宿主机算 %d 核 -> GC 线程≈%d，小容器里线程互相争抢%n",
                legacy, legacy);
        System.out.println("  显式指定：-XX:ActiveProcessorCount=2 可人工定档");
        System.out.println();
    }

    static void entrypointTrap() {
        System.out.println("【ENTRYPOINT 两种写法与信号送达】");
        System.out.println("  exec 形式：ENTRYPOINT [\"java\",\"-jar\",\"app.jar\"]");
        System.out.println("    进程树：java(PID 1) -> SIGTERM 直达 JVM -> 优雅停机 ~1s");
        System.out.println("  shell 形式：ENTRYPOINT java -jar app.jar");
        System.out.println("    进程树：sh(PID 1) -> java(PID 7) -> SIGTERM 给了 sh");
        System.out.println("    java 不退出 -> 10s 宽限期满 -> SIGKILL，在途请求全丢");
        System.out.println("  兜底方案：sh -c 'exec java ...' 用 exec 替换 shell 自身");
        System.out.println();
    }

    static void layeredJar() {
        System.out.println("【分层 jar：改一行代码只重建哪层？】");
        record JarLayer(String name, int sizeMb, String rule) {
        }
        List<JarLayer> layers = List.of(
                new JarLayer("dependencies", 45, "第三方依赖，月级变化"),
                new JarLayer("spring-boot-loader", 1, "启动器，几乎不变"),
                new JarLayer("snapshot-dependencies", 3, "SNAPSHOT 依赖"),
                new JarLayer("application", 2, "业务类，日级变化"));
        for (JarLayer l : layers) {
            System.out.printf("  %-22s %3dMB  %s%n", l.name(), l.sizeMb(), l.rule());
        }
        System.out.println("  改业务代码：前三层 CACHED，只重传 application 层 ~2MB");
        System.out.println("  Dockerfile 顺序：COPY 依赖层 -> COPY 应用层");
        System.out.println();
    }

    static void imageChecklist() {
        System.out.println("【Java 镜像上线检查单】");
        List<String> items = List.of(
                "TZ=Asia/Shanghai + 字体/locale，验证日志时间戳与中文",
                "ENTRYPOINT 用 exec 形式，SIGTERM 实测 1s 内退出",
                "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError",
                "HEALTHCHECK 用 /actuator/health 并检查 HTTP 200",
                "日志到 stdout，容器内不写业务日志文件",
                "基础镜像选型留档：jre / alpine / distroless 的取舍理由");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        memoryBudget();
        cpuAwareness();
        entrypointTrap();
        layeredJar();
        imageChecklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实测内存预算：起 `docker run -m 1g` 的 JDK 21 容器，分别用 `-Xmx900m` 与 `MaxRAMPercentage=75` 跑压测，用 `docker stats` 对比峰值内存。
 * 2. 复现 shell 形式 ENTRYPOINT 的停机问题：两种写法各做一次 `docker stop`，用 `docker events` 或时间戳记录耗时差异，解释原因。
 * 3. 为 Spring Boot 项目开启分层 jar，构建两次（第二次只改业务类），用 `docker build` 日志证明依赖层 CACHED，记录两次构建耗时。
 * 4. 思考题：为什么 OOM 时应选择 `ExitOnOutOfMemoryError` 快速失败而不是"带病坚持"？结合 37-04 的重启策略说明完整自愈链路。
 * ============================================================
 */
