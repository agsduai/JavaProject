/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-03
 * 课名：窗口：滚动、滑动与会话
 * ------------------------------------------------------------
 * 知识点：
 *   - **窗口是把无界流切成可聚合片段的机制**，三种基础形态：**滚动窗口**（tumbling，固定长度不重叠：每分钟 GMV）、**滑动窗口**（sliding，固定长度带步长重叠：近 5 分钟 QPS 每 10 秒刷新一次）、**会话窗口**（session，按活动间隔动态划分：用户一次浏览会话）。
 *   - **窗口的关键参数语义**：滚动窗口只有 size；滑动窗口有 size（统计范围）与 slide（刷新频率），slide < size 时同一事件归属多个窗口（被多次统计）；会话窗口的参数是**间隔 gap**（gap 内无活动则窗口关闭）。
 *   - **窗口归属由事件时间决定**（43-02 的直接应用）：事件落在 `[窗口起点, 终点)`。**窗口是"开区间闭区间"的口径问题**，与批 SQL 的 `GROUP BY date_trunc` 完全对齐——对不上批就是口径错了。
 *   - **迟到与窗口生命周期**：窗口在其事件时间终点 + 水位线（43-04）到达后触发计算；允许迟到期间窗口保持"可更新"状态。**窗口状态的存在时间 = size + 迟到容忍**，直接影响状态存储量。
 *   - **会话窗口的实现成本**：每个 key 维护"当前会话"状态，gap 内合并、gap 外新开——状态按活跃用户数计。大促日活跃千万级时会话窗口的状态量要提前估算。
 *   - **窗口与下游的契约**：窗口结果带（窗口起点、终点、水位线）元数据输出——下游要知道"这个 11:59 的数字是几点几分最终确定的"。**不带窗口元数据的输出无法被信任**。
 *   - 易错点：滑动窗口理解成"每 slide 秒重新统计全部"（事件重复计入多个窗口是设计而非 bug，但下游消费要明确）；会话 gap 设得太小（会话被切碎）；窗口边界口径与批 SQL 不一致（对不上账）；窗口结果没有"最终/临时"标记（下游把临时值当最终值）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// ==================== 示例代码 ====================

public class WindowsDemo {
    record Event(long ts, double amount) {
    }

    /** 事件流（相对秒） */
    static final List<Event> EVENTS = List.of(
            new Event(5, 60), new Event(35, 80), new Event(65, 40),
            new Event(95, 100), new Event(125, 70), new Event(155, 30));

    static void tumbling() {
        System.out.println("【滚动窗口：size=60s，每分钟 GMV】");
        TreeMap<Long, Double> windows = new TreeMap<>();
        for (Event e : EVENTS) {
            long win = e.ts() / 60;          // 归属哪一分钟
            windows.merge(win, e.amount(), Double::sum);
        }
        for (Map.Entry<Long, Double> w : windows.entrySet()) {
            System.out.printf("  [min%d, min%d)  GMV=%.0f 元%n", w.getKey(), w.getKey() + 1, w.getValue());
        }
        System.out.println("  特点：不重叠、每个事件只属一个窗口；等价于批的 GROUP BY 分钟");
        System.out.println();
    }

    static void sliding() {
        System.out.println("【滑动窗口：size=120s，slide=60s（近2分钟GMV每分钟刷新）】");
        for (long slide = 0; slide <= 2; slide++) {
            long start = slide * 60, end = start + 120;
            double sum = EVENTS.stream()
                    .filter(e -> e.ts() >= start && e.ts() < end)
                    .mapToDouble(Event::amount).sum();
            System.out.printf("  窗口[%3d, %3d) 当前累计 %.0f 元（刷新于 slide=%d）%n",
                    start, end, sum, slide);
        }
        System.out.println("  事件 ts=65 同时落在窗口[0,120) 与 [60,180)：被统计两次是设计使然");
        System.out.println("  下游消费契约：滑动结果用于“当前水平”，不用于累加求和");
        System.out.println();
    }

    static void sessionWindow() {
        System.out.println("【会话窗口：gap=20s，按用户活动划分】");
        record Click(long userId, long ts) {
        }
        List<Click> clicks = List.of(
                new Click(1, 0), new Click(1, 10), new Click(1, 28),   // 10<20 合并；28-10=18<20 合并
                new Click(1, 60),                                       // 60-28=32>20 新会话
                new Click(2, 5), new Click(2, 90));                     // gap 85 -> 各自成会话
        Map<Long, List<Long>> sessions = new LinkedHashMap<>();
        for (Click c : clicks) {
            List<Long> list = sessions.computeIfAbsent(c.userId(), k -> new ArrayList<>());
            if (!list.isEmpty() && c.ts() - list.get(list.size() - 1) > 20) {
                list = new ArrayList<>();
                sessions.put(c.userId(), list);
            }
            list.add(c.ts());
        }
        for (Map.Entry<Long, List<Long>> e : sessions.entrySet()) {
            System.out.printf("  用户%d 会话数=%d：%s%n", e.getKey(),
                    e.getValue().size(), e.getValue());
        }
        System.out.println("  参数是间隔而非长度：会话边界由数据自己决定");
        System.out.println();
    }

    static void lifecycle() {
        System.out.println("【窗口生命周期：状态存在多久】");
        long sizeSec = 60, allowedLateness = 300;
        System.out.printf("  size=%ds + 允许迟到=%ds -> 每个窗口状态存活 %d 秒%n",
                sizeSec, allowedLateness, sizeSec + allowedLateness);
        System.out.println("  状态量估算：活跃窗口数 = 360s 内的事件 key 数（大促需提前算）");
        System.out.println("  输出契约：结果带 (窗口起止, 是否最终) 元数据，下游据此消费");
    }

    public static void main(String[] args) {
        tumbling();
        sliding();
        sessionWindow();
        lifecycle();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用示例数据实现三种窗口，验证"滑动窗口的事件重复归属"与"会话窗口的动态合并"行为，画出每个事件的归属矩阵。
 * 2. 为"近 5 分钟错误率每 30 秒刷新"设计窗口参数（size/slide），并说明为什么结果不能下游累加。
 * 3. 思考题：为什么窗口输出必须带"是否最终"标记？结合允许迟到机制说明下游把临时值当最终值的业务后果。
 * ============================================================
 */
