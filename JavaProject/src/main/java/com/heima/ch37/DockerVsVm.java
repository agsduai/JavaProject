/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-01
 * 课名：容器与虚拟机的区别
 * ------------------------------------------------------------
 * 知识点：
 *   - **容器不是"轻量虚拟机"**：虚拟机虚拟的是一整套硬件外加自己的操作系统内核，容器共享宿主机内核，只隔离进程视图与资源额度。启动一个 Ubuntu 容器是秒级的，启动一台 Ubuntu 虚拟机是分钟级的，差别不在磁盘大小，而在**有没有自己的内核**。
 *   - **隔离 = namespace（视图）+ cgroup（额度）**：namespace 让进程"看不见"别人 —— PID namespace（容器里的进程从 1 开始编号）、NET namespace（独立网卡、独立端口空间）、MNT namespace（独立文件系统视图）；cgroup 限制"能用多少" —— CPU 配额、内存上限、块设备 IO 速率。**容器本质就是一个被这两样东西圈起来的普通 Linux 进程**。
 *   - **容器里没有完整的 init**：1 号进程就是 ENTRYPOINT 指定的那个进程，它一退出容器就退出。所以容器里没有 systemd，也不该有 —— 一个容器只做一件事。
 *   - **镜像是模板，容器是运行实例**：同一个镜像可以起 N 个容器，互相完全隔离，关系类似类与对象。
 *   - **容器的优势**：秒级启动、密度高（一台宿主机跑几十个）、交付物一致（镜像自带运行时与依赖，"在我机器上是好的"被根治）。
 *   - **容器的代价**：共享内核，只能跑与宿主机内核兼容的系统（Windows 宿主机不能直接跑 Linux 容器）；隔离强度弱于虚拟机，内核漏洞可能导致容器逃逸；默认无持久状态，删容器即丢数据，必须显式挂卷。
 *   - **Java 与容器的关系**：JDK 8u191+ / JDK 10+ 默认开启 `UseContainerSupport`，JVM 会读取 cgroup 配额，按容器限额而不是宿主机内存来计算堆大小；老 JDK 看到的是宿主机的 64G 内存，按默认比例开堆，一进小容器就被内核 OOM 杀掉（退出码 137）。
 *   - 易错点：把容器当虚拟机用（装 systemd、SSH、在里面改内核参数）；以为容器里的数据是持久的（没挂卷，删容器全丢）；内存限额 1G 却让 JVM 堆开 4G（必然 OOMKilled）；把"容器秒级启动"理解成"应用秒级就绪"（进程起了不代表能接流量，要看健康检查）。
 * ============================================================
 */

package com.heima.ch37;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DockerVsVm {
    record Dim(String name, String vm, String container) {
    }

    record ProcView(int hostPid, int containerPid, String cmd) {
    }

    static void compareTable() {
        System.out.println("【虚拟机 vs 容器：六个维度】");
        List<Dim> dims = List.of(
                new Dim("启动时间", "分钟级（引导内核+init）", "秒级（起一个进程）"),
                new Dim("内核", "每个 VM 自己的内核", "共享宿主机内核"),
                new Dim("隔离强度", "硬件级，最强", "进程级，强但可逃逸"),
                new Dim("单机密度", "几个到几十个", "几十到几百个"),
                new Dim("镜像大小", "GB 级（整个系统盘）", "MB 级（只含运行时）"),
                new Dim("典型用途", "异构 OS、强隔离多租户", "应用交付、CI/CD、微服务"));
        for (Dim d : dims) {
            System.out.printf("  %-8s | 虚拟机：%-22s | 容器：%s%n", d.name(), d.vm(), d.container());
        }
        System.out.println();
    }

    static void pidNamespace() {
        System.out.println("【PID namespace：每个容器都以为自己是 1 号】");
        List<ProcView> javaApp = List.of(new ProcView(4721, 1, "java -jar app.jar"));
        List<ProcView> nginx = List.of(
                new ProcView(5310, 1, "nginx: master"),
                new ProcView(5377, 5, "nginx: worker"));
        printView("容器 java-app", javaApp);
        printView("容器 nginx", nginx);
        System.out.println("  宿主机视角：这些只是普通进程，PID 连续、共享同一内核");
        System.out.println("  教训：容器内没有 init，1 号进程退出 = 容器退出");
        System.out.println();
    }

    private static void printView(String label, List<ProcView> views) {
        System.out.println("  " + label + "（容器内视角）");
        for (ProcView p : views) {
            System.out.printf("    容器PID=%-3d <- 宿主机PID=%-5d %s%n",
                    p.containerPid(), p.hostPid(), p.cmd());
        }
    }

    static void networkNamespace() {
        System.out.println("【NET namespace：两个容器可以同时“监听 8080”】");
        Map<String, String> javaApp = Map.of("eth0", "172.17.0.2", "内部监听", "8080");
        Map<String, String> redis = Map.of("eth0", "172.17.0.3", "内部监听", "6379");
        System.out.println("  java-app : " + javaApp);
        System.out.println("  redis    : " + redis);
        System.out.println("  宿主机通过 docker0 网桥(172.17.0.1)与 veth pair 连到每个容器");
        System.out.println("  端口映射：-p 8081:8080 表示 宿主机8081 -> 容器8080");
        System.out.println();
    }

    static void cgroupQuota() {
        System.out.println("【cgroup：CPU 配额与内存上限的真实记账】");
        // CPU：period=100ms，quota=50ms，即限 0.5 核
        int period = 100, quota = 50;
        int wantCpu = 80;
        int throttled = Math.max(0, wantCpu - quota);
        System.out.printf("  CPU 限额 0.5 核（每 %dms 周期最多用 %dms）%n", period, quota);
        System.out.printf("  本周期想用 %dms -> 放行 %dms，节流 %dms（表现为接口变慢而非报错）%n",
                wantCpu, Math.min(wantCpu, quota), throttled);
        // 内存：limit=256MB
        int limit = 256;
        int used1 = 200, used2 = 300;
        System.out.printf("  内存限额 %dMB：用到 %dMB 正常；用到 %dMB -> 内核 OOM killer 杀掉主进程%n",
                limit, used1, used2);
        System.out.printf("  docker ps 看到 Exited (137)，137 = 128 + SIGKILL(9)%n");
        System.out.println("  推论：JVM 堆必须按容器限额算，不能按宿主机内存算");
        System.out.println();
    }

    static void sameImageManyContainers() {
        System.out.println("【同一镜像起 3 个容器：类与对象的关系】");
        List<String> names = List.of("order-1", "order-2", "order-3");
        for (String n : names) {
            System.out.printf("  %-8s 镜像=app:1.0  IP=172.17.0.%d  独立文件系统(读写层)  独立进程空间%n",
                    n, 2 + names.indexOf(n));
        }
        System.out.println("  镜像只存一份，容器各自叠加一个可写层");
        System.out.println();
    }

    public static void main(String[] args) {
        compareTable();
        pidNamespace();
        networkNamespace();
        cgroupQuota();
        sameImageManyContainers();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用自己的话解释：为什么"容器里 ps 看不到宿主机的其他进程"，而"在容器里 top 看到的内存却可能是宿主机的"？分别对应哪种隔离机制？
 * 2. 写一个函数 `jvmHeapSuggestion(long containerLimitMb, boolean legacyJdk)`：容器限额 1024MB 时，新 JDK 与老 JDK 分别建议堆多大？返回并解释理由。
 * 3. 在本机（或虚拟机）安装 Docker，运行 `docker run -m 256m --cpus=0.5 openjdk:21` 并在里面压测，观察 137 退出与 CPU 节流现象，记录 `docker inspect` 里的关键证据。
 * 4. 思考题：一台宿主机上同时跑 100 个基于同一镜像的容器，磁盘上的镜像数据存了几份？容器各自独有的是哪部分？
 * ============================================================
 */
