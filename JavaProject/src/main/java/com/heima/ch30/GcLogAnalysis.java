/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-09
 * 课名：GC 日志分析与参数调优
 * ------------------------------------------------------------
 * 知识点：
 *   - 开 GC 日志：JDK 9+ 用 `-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=50M`，JDK 8 用 `-XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log`。
 *   - 日志里要抓四个数：**Young GC 频率**、**Young GC 平均停顿**、**Full GC 次数**、**Full GC 后老年代占用**。前两个决定"平时卡不卡"，后两个决定"会不会雪崩"。
 *   - 典型判读：
 *   - Young GC 很频繁（< 1s 一次）且每次回收量小 → 新生代太小，调 `-Xmn` 或 `-XX:NewRatio`。
 *   - 每次 Young GC 后存活对象很多 → 对象生命周期偏长，检查是否有大缓存或批处理。
 *   - Full GC 频繁但回收后老年代降不下来 → 内存泄漏，去 dump。
 *   - Full GC 后老年代降下来了但仍频繁 → 老年代太小或晋升太快，调 `-Xmx`/`-XX:MaxTenuringThreshold`。
 *   - **停顿目标**不是越小越好：`MaxGCPauseMillis` 设太小，G1 每次只回收少量 Region，GC 次数暴增、吞吐下降。
 *   - **堆大小**经验：`-Xms` 与 `-Xmx` 设成一样，避免动态扩容抖动；容器里必须让 JVM 感知 cgroup 限制（JDK 10+ 默认 `UseContainerSupport`，或显式 `-XX:MaxRAMPercentage=75`）。
 *   - 易错：只调 `-Xmx` 不调 `-Xmn`（新生代默认只占 1/3）；把 `-Xmn` 设得过大导致老年代过小、Full GC 更频繁；在 32GB 边界上开堆导致指针压缩失效。
 * ============================================================
 */

package com.heima.ch30;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

public class GcLogAnalysis {
    /** 一行 Young GC 日志的简化形态 */
    static final String[] LOG = {
            "[2.113s][info][gc] GC(0) Pause Young (Normal) 512M->48M(1024M) 8.120ms",
            "[2.980s][info][gc] GC(1) Pause Young (Normal) 560M->52M(1024M) 7.640ms",
            "[3.851s][info][gc] GC(2) Pause Young (Normal) 564M->55M(1024M) 9.010ms",
            "[4.702s][info][gc] GC(3) Pause Young (Normal) 567M->58M(1024M) 8.330ms",
            "[5.560s][info][gc] GC(4) Pause Young (Normal) 570M->60M(1024M) 7.980ms",
            "[6.401s][info][gc] GC(5) Pause Young (Normal) 572M->61M(1024M) 8.450ms",
            "[7.260s][info][gc] GC(6) Pause Young (Normal) 573M->62M(1024M) 8.100ms",
            "[8.120s][info][gc] GC(7) Pause Young (Normal) 574M->63M(1024M) 8.770ms",
            "[9.010s][info][gc] GC(8) Pause Young (Normal) 575M->64M(1024M) 8.220ms",
            "[9.880s][info][gc] GC(9) Pause Young (Normal) 576M->65M(1024M) 8.600ms",
            "[10.750s][info][gc] GC(10) Pause Young (Normal) 577M->66M(1024M) 9.140ms",
            "[12.100s][info][gc] GC(11) Pause Full (Ergonomics) 1000M->820M(1024M) 620.500ms",
            "[13.400s][info][gc] GC(12) Pause Young (Normal) 700M->120M(1024M) 9.300ms",
            "[14.300s][info][gc] GC(13) Pause Young (Normal) 720M->125M(1024M) 9.050ms",
            "[15.200s][info][gc] GC(14) Pause Young (Normal) 725M->128M(1024M) 9.400ms",
            "[16.100s][info][gc] GC(15) Pause Young (Normal) 728M->130M(1024M) 9.200ms",
            "[17.000s][info][gc] GC(16) Pause Young (Normal) 730M->132M(1024M) 9.600ms",
            "[18.900s][info][gc] GC(17) Pause Full (Ergonomics) 1010M->880M(1024M) 780.200ms",
            "[20.200s][info][gc] GC(18) Pause Young (Normal) 800M->150M(1024M) 9.800ms",
            "[21.100s][info][gc] GC(19) Pause Young (Normal) 802M->152M(1024M) 9.500ms"
    };

    static final Pattern P = Pattern.compile(
            "GC\\((\\d+)\\)\\s+Pause\\s+(Young|Full)[^\\n]*?(\\d+)M->(\\d+)M\\((\\d+)M\\)\\s+([\\d.]+)ms");

    public static void main(String[] args) {
        System.out.println("=== 解析 GC 日志（简化样例 " + LOG.length + " 行）===");
        List<Event> events = new ArrayList<>();
        for (String line : LOG) {
            Matcher m = P.matcher(line);
            if (m.find()) {
                events.add(new Event(Integer.parseInt(m.group(1)), m.group(2),
                        Long.parseLong(m.group(3)), Long.parseLong(m.group(4)),
                        Long.parseLong(m.group(5)), Double.parseDouble(m.group(6))));
            }
        }
        System.out.println("  成功解析 " + events.size() + " 条，未匹配 " + (LOG.length - events.size()) + " 条");

        System.out.println();
        System.out.println("=== 关键指标 ===");
        int youngCount = 0, fullCount = 0;
        double youngPauseSum = 0, youngPauseMax = 0, fullPauseSum = 0, fullPauseMax = 0;
        long heapMb = 0, lastFullAfter = 0, peakBefore = 0;
        for (Event e : events) {
            heapMb = e.heapMb;
            peakBefore = Math.max(peakBefore, e.beforeMb);
            if ("Young".equals(e.type)) {
                youngCount++;
                youngPauseSum += e.pauseMs;
                youngPauseMax = Math.max(youngPauseMax, e.pauseMs);
            } else {
                fullCount++;
                fullPauseSum += e.pauseMs;
                fullPauseMax = Math.max(fullPauseMax, e.pauseMs);
                lastFullAfter = e.afterMb;
            }
        }
        double durationSec = 21.1;
        System.out.printf("  运行时长            %.1fs%n", durationSec);
        System.out.printf("  Young GC 次数       %d（平均 %.0fms 一次）%n",
                youngCount, durationSec * 1000 / Math.max(1, youngCount));
        System.out.printf("  Young GC 平均停顿   %.1fms，最大 %.1fms%n",
                youngPauseSum / Math.max(1, youngCount), youngPauseMax);
        System.out.printf("  Full  GC 次数       %d%n", fullCount);
        System.out.printf("  Full  GC 平均停顿   %.1fms，最大 %.1fms%n",
                fullPauseSum / Math.max(1, fullCount), fullPauseMax);
        System.out.printf("  堆峰值              %dMB / %dMB%n", peakBefore, heapMb);
        System.out.printf("  Full GC 后老年代    %dMB（回收率仅 %.0f%%）%n",
                lastFullAfter, (1 - lastFullAfter * 1.0 / heapMb) * 100);
        System.out.printf("  GC 总停顿           %.1fms，占运行时间 %.1f%%%n",
                youngPauseSum + fullPauseSum, (youngPauseSum + fullPauseSum) / (durationSec * 1000) * 100);

        System.out.println();
        System.out.println("=== 诊断结论 ===");
        double avgYoungInterval = durationSec * 1000 / Math.max(1, youngCount);
        if (avgYoungInterval < 1500) {
            System.out.println("  [问题1] Young GC 间隔 " + (long) avgYoungInterval
                    + "ms，过于频繁 -> 新生代偏小，先加大 -Xmn");
        }
        if (fullCount > 0) {
            System.out.println("  [问题2] 出现 " + fullCount + " 次 Full GC，单次最长 "
                    + (long) fullPauseMax + "ms -> 会直接造成接口超时");
        }
        double reclaim = 1 - lastFullAfter * 1.0 / heapMb;
        if (reclaim < 0.3) {
            System.out.println("  [问题3] Full GC 后只回收了 " + Math.round(reclaim * 100)
                    + "%，老年代仍占 " + lastFullAfter + "MB -> 疑似内存泄漏，去 dump");
        }
        System.out.println("  [推断] 每次 Young GC 后存活约 "
                + (events.get(0).afterMb - 40) + "MB 以上并持续上涨，说明有对象在长期存活");

        System.out.println();
        System.out.println("=== 调参建议（堆 1GB，容器限制 1.5GB）===");
        String[][] params = {
                {"-Xms1g -Xmx1g", "固定堆，避免扩容抖动"},
                {"-Xmn400m", "新生代从 1/3 提到 40%，降低 Young GC 频率"},
                {"-XX:MaxGCPauseMillis=200", "G1 目标停顿，先给 200ms 别给 50ms"},
                {"-XX:MaxTenuringThreshold=6", "存活对象多时适当降低，避免长期占 Survivor"},
                {"-XX:MaxRAMPercentage=70", "容器内按比例设堆，给元空间/直接内存留余量"},
                {"-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=50M", "日志滚动，别写满磁盘"}
        };
        for (String[] p : params) {
            System.out.printf("  %-58s %s%n", p[0], p[1]);
        }
        System.out.println();
        System.out.println("  调参顺序：先定位（泄漏/太小/晋升快）-> 再改一个参数 -> 压测对比 -> 再改下一个");
        System.out.println("  一次改五个参数，出了问题根本不知道是哪个引起的");
    }

    record Event(int id, String type, long beforeMb, long afterMb, long heapMb, double pauseMs) {
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在示例里再加 3 条 Young GC 日志，说明平均间隔与停顿如何变化，结论是否改变。
 * 2. 如果 Full GC 后老年代能降到 100MB，但每 10 秒还是来一次 Full GC，应该调什么？为什么？
 * 3. 解释为什么 `-Xmn` 设得过大反而会让 Full GC 更频繁。
 * ============================================================
 */
