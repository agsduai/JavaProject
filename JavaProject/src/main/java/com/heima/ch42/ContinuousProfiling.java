/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：42-09
 * 课名：持续剖析与资源画像
 * ------------------------------------------------------------
 * 知识点：
 *   - **指标告诉你 CPU 高了，Profile 告诉你 CPU 在干什么**：持续剖析（continuous profiling）周期性采集进程的调用栈样本（CPU、内存分配、锁等待、IO），聚合成火焰图。**它是"指标 -> 代码行"的最后一跳**，与第 30 章 JVM 工具（jstack 一次性快照）的区别在于持续与聚合。
 *   - **火焰图的读法**：横轴是 CPU 时间占比（越宽越热），纵轴是调用栈深度（上层的由下层调用）。**找宽的平顶**——又宽又不能继续展开的帧就是热点。采样型 profiler 开销 1~2%，可常驻生产。
 *   - **四类剖析各有用途**：CPU profile（热点函数、正则灾难、JSON 序列化）、alloc profile（内存分配大户，GC 压力来源）、block/lock profile（锁竞争）、off-CPU（等待 IO）。**"CPU 不高但慢"的谜题通常在 off-CPU profile 里解开**。
 *   - **持续剖析的服务形态**：agent 常驻采集（每 10~60s 一轮）-> 上报到剖析平台 -> 按服务/时间/版本聚合对比。**版本对比（diff 火焰图）是发布验证利器**：2.4.0 vs 2.4.1 的 CPU 热点变化一眼可见。
 *   - **与指标的联动**：CPU 饱和度告警触发 -> 平台自动拉取该时段 CPU profile -> 热点函数直出。**告警到根因的自动化路径**是可观测性平台的进阶形态。
 *   - **Go/Java 的生态差异**：Go 的 pprof 是标杆（低成本常驻）；Java 主流是 async-profiler（event-based，低开销）+ JFR（JDK 自带，持续记录）。JFR 是 Java 团队的默认起点——**JDK 21 的 JFR 已经能覆盖 CPU/alloc/lock 全场景**。
 *   - 易错点：只在出事时 attach 工具（错过现场，CPU 尖峰是瞬时的）；采样 profiler 与侵入式 tracer 混淆（前者低开销可常驻）；只看 CPU profile 忽略 alloc（很多"GC 频繁"的真凶是某个函数狂分配）；火焰图存档不保留（没法做版本对比）。
 * ============================================================
 */

package com.heima.ch42;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ContinuousProfiling {
    /** 模拟 CPU profile：函数名 -> 自身采样数 */
    record Frame(String name, int samples, String parent) {
        double pct(int total) {
            return samples * 100.0 / total;
        }
    }

    static void flameReading() {
        System.out.println("【火焰图数据：一次 30s 的 CPU 采样（共 3000 样本）】");
        List<Frame> frames = List.of(
                new Frame("main.run", 3000, null),
                new Frame("http.handle", 2400, "main.run"),
                new Frame("json.serialize", 1050, "http.handle"),
                new Frame("regex.match", 720, "http.handle"),
                new Frame("db.query", 400, "http.handle"),
                new Frame("gc.pause", 100, "main.run"));
        int total = 3000;
        for (Frame f : frames) {
            String bar = "█".repeat(Math.max(1, (int) (f.pct(total) / 4)));
            System.out.printf("  %-18s %6.1f%% %s%n", f.name(), f.pct(total), bar);
        }
        System.out.println("  读法：json.serialize 与 regex.match 是两个“宽平顶” -> 热点");
        System.out.println("  行动：正则预编译 + JSON 缓存，预计省 40% CPU");
        System.out.println();
    }

    static void profileTypes() {
        System.out.println("【四类剖析的适用问题】");
        String[][] rows = {
                {"cpu", "CPU 高、吞吐低", "热点函数、正则灾难、序列化"},
                {"alloc", "GC 频繁、内存涨", "分配大户（临时对象工厂）"},
                {"lock", "线程多但吞吐低", "锁竞争热点"},
                {"off-cpu", "CPU 不高但慢", "等待 IO、锁等待、连接池等待"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-16s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  「CPU 不高但接口慢」-> 先看 off-CPU：多数是等待而非计算");
        System.out.println();
    }

    static void jfrPath() {
        System.out.println("【Java 团队的默认起点：JFR】");
        List<String> steps = List.of(
                "启动参数：-XX:StartFlightRecording=duration=60s,filename=... 或常驻 JFR",
                "jcmd <pid> JFR.start duration=60s（在线触发）",
                "jfr print/summary 分析；或接持续剖析平台聚合",
                "开销 1~2%，可常驻生产（区别于 jstat 轮询的侵入）");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  进阶：async-profiler 出火焰图 + 平台化聚合 + 版本 diff");
    }

    public static void main(String[] args) {
        flameReading();
        profileTypes();
        jfrPath();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 JFR 采集你的应用 60 秒（压测中），输出 top 5 CPU 热点方法，并给出一条可执行的优化。
 * 2. 实现"alloc 热点"分析：模拟两个版本的分配数据，输出 diff 火焰图数据，找出 2.4.1 新引入的分配大户。
 * 3. 思考题：为什么"CPU 不高但接口慢"要先看 off-CPU 而不是 CPU？从等待与计算的资源占用差异推导排查顺序。
 * ============================================================
 */
