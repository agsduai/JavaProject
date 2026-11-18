/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-04
 * 课名：水位线与乱序处理
 * ------------------------------------------------------------
 * 知识点：
 *   - **水位线（watermark）回答"什么时候窗口可以关"**：事件时间窗口不能等全部数据（数据无界），需要一个时间进度信号——"水位线 = T 表示：T 之前的事件应该都到了（除非超过容忍度）"。**水位线是流系统对"数据完备性"的乐观声明**。
 *   - **水位线的生成**：通常 = 当前最大事件时间 - 有界乱序容忍度（如 5 秒）。事件到达会推进水位线；**某个分区/源停滞会拖住全局水位线**（所有上游的最小值）——这是"水位线卡住"故障的根源（空闲分区要发 idle 标记）。
 *   - **水位线触发窗口**：水位线越过窗口终点（终点 + 允许迟到）时触发计算并关闭窗口。**容忍度是"正确性与延迟"的交换**：容忍 5s -> 结果最多晚 5s+窗口长，但 5s 内的乱序都能正确归属；容忍 0 -> 最快但乱序全部迟到丢弃。
 *   - **迟到数据的旁路（side output）**：超过允许迟到的数据不进窗口，写入旁路流单独统计（"迟到丢弃数"本身是监控指标——突然升高说明上游时钟或网络出问题）。
 *   - **水位线的正确性边界**：它不是"保证不会有更早的数据"，而是"声明不会有"。事件时间 < 当前水位线的数据就是迟到数据。**水位线本质是赌"乱序有界"**，赌输的部分走旁路。
 *   - **幂等更新替代完美水位线**：下游支持重算（窗口结果带版本）时，水位线可以激进、迟到数据重发更新结果——**"可以修正的输出"比"完美的水位线"更工程化**（与 42 章窗口"是否最终"标记衔接）。
 *   - 易错点：水位线容忍度拍脑袋（不测真实乱序分布）；空闲分区拖住全局水位线（窗口迟迟不触发，表现为"实时大屏卡住"）；把水位线当精确时钟（它是启发式）；迟到数据静默丢弃（旁路统计缺失，丢数没人知道）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class WatermarkDemo {
    record Arrival(String order, long eventTs, long arrivalSec) {
    }

    /** 乱序容忍 5s；事件相对时间（秒） */
    static final long TOLERANCE = 5;

    static void watermarkWalkthrough() {
        System.out.println("【水位线推进：最大事件时间 - 容忍度】");
        List<Arrival> arrivals = List.of(
                new Arrival("o1", 100, 100),   // 正常到达
                new Arrival("o2", 108, 109),
                new Arrival("o3", 104, 111),   // 乱序：比 o2 的 108 小
                new Arrival("o4", 120, 120));
        for (Arrival a : arrivals) {
            System.out.printf("  %s 到达：eventTs=%d -> WM = %d - %d = %d%n",
                    a.order(), a.eventTs(), Math.max(a.eventTs(), 0), TOLERANCE,
                    Math.max(a.eventTs() - TOLERANCE, 0));
        }
        System.out.println("  窗口 [95,105) 在 WM >= 105+迟到容忍 时触发：o3(eventTs=104) 赶上了");
        System.out.println("  若容忍度=0：o3 在 WM=108 时才到 -> 已超窗口终点 -> 迟到");
        System.out.println();
    }

    static void idleSource() {
        System.out.println("【空闲分区拖住全局水位线】");
        List<String[]> sources = List.of(
                new String[]{"partition-0", "maxEventTs=200", "活跃"},
                new String[]{"partition-1", "maxEventTs=198", "活跃"},
                new String[]{"partition-2", "maxEventTs=180（该分区没流量了）", "空闲"});
        for (String[] s : sources) {
            System.out.printf("  %-12s %-30s %s%n", s[0], s[1], s[2]);
        }
        System.out.println("  全局 WM = min(195, 193, 175) = 175 <- 被空闲分区拖住");
        System.out.println("  现象：大屏“卡住”不刷新，窗口迟迟不关闭");
        System.out.println("  对策：源空闲检测 + idle 标记（不参与 WM 取 min）");
        System.out.println();
    }

    static void sideOutput() {
        System.out.println("【迟到旁路：丢弃也要被看见】");
        record Late(String order, int lateSec, String fate) {
        }
        List<Late> lates = List.of(
                new Late("o9", 8, "允许迟到（5s）内：更新窗口结果"),
                new Late("o10", 60, "超限：写入旁路流，计数+1"));
        for (Late l : lates) {
            System.out.printf("  %s 迟到 %ds -> %s%n", l.order(), l.lateSec(), l.fate());
        }
        System.out.println("  旁路指标 late_dropped_count 突增 = 上游时钟/网络异常的信号");
        System.out.println("  静默丢弃 = 丢数没人知道 —— 旁路统计必须有");
    }

    public static void main(String[] args) {
        watermarkWalkthrough();
        idleSource();
        sideOutput();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 生成一段带乱序的事件流（乱序 0~15s 随机），实测不同容忍度（0/5/10s）下的"正确归属率 vs 结果延迟"，画出交换曲线。
 * 2. 模拟"空闲分区拖住水位线"：三个源一个停止发数据，观察窗口触发延迟，然后实现 idle 标记修复。
 * 3. 思考题：为什么说水位线是"赌乱序有界"？结合旁路统计说明"赌输的部分"如何被工程化兜底。
 * ============================================================
 */
