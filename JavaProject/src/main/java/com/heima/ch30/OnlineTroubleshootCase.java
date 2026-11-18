/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-11
 * 课名：线上问题排查综合实战
 * ------------------------------------------------------------
 * 知识点：
 *   - 线上排查的固定套路：**先止损再定位**（限流/降级/回滚/扩容），**保留现场**（dump、日志、指标快照），**按证据缩小范围**，**根因 + 复现 + 修复 + 防复发**四件套。
 *   - 三类高频故障的入口信号：
 *   - **CPU 100%** → `top -Hp` + `jstack`，看是业务死循环、GC 线程还是线程数暴涨。
 *   - **内存涨/OOM** → GC 日志看 Full GC 后是否回落，`jmap -histo:live` 看谁在涨，dump 找引用链。
 *   - **接口超时/RT 上涨** → 看线程池活跃数与队列、连接池等待、下游 RT、慢 SQL、锁等待。
 *   - **雪崩链条**往往是"一个下游变慢 → 上游线程全被占住 → 上游也不可用"。所以必须有**超时 + 熔断 + 隔离 + 限流**四件套，且超时预算要逐层递减。
 *   - 排查时**时间线**极其重要：把"几点几分做了什么变更、指标怎么变"对齐，能直接指向根因（发布、配置变更、流量突增、定时任务、数据倾斜）。
 *   - 复盘要写：影响面（多少用户/订单/金额）、时间线、根因、为什么监控没提前发现、改进项与 owner。**不追责，追机制**。
 *   - 易错：一上来就重启（现场全丢，同样的坑下周再踩）；只修表象（把超时调大，雪崩更快）；没有回滚预案；复盘写成"某某操作失误"而不是"为什么这个操作能造成这么大影响"。
 * ============================================================
 */

package com.heima.ch30;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class OnlineTroubleshootCase {
    static final List<String> TIMELINE = new ArrayList<>();
    static final Map<String, String> METRICS = new LinkedHashMap<>();

    public static void main(String[] args) {
        System.out.println("=== 故障现象（14:02 开始）===");
        System.out.println("  订单服务 P99 从 80ms 涨到 3200ms，错误率 12%");
        System.out.println("  CPU 42%（不高），线程池 active=200/200，队列堆积 1.8 万");
        System.out.println("  下游「用户服务」RT 从 30ms 涨到 1200ms，但它的 CPU 只有 35%");
        System.out.println("  20 分钟前刚发过一个版本：给用户服务加了「查积分」的同步调用");

        System.out.println();
        System.out.println("=== 第一步：止损（先恢复，不查原因）===");
        step("14:05", "回滚用户服务到上一版本", "RT 从 1200ms 降到 40ms");
        step("14:06", "订单服务线程池队列清理 + 临时扩容 2 个实例", "active 从 200 降到 60");
        step("14:08", "错误率回到 0.2%，P99 回到 120ms", "业务恢复");
        System.out.println("  原则：能回滚就先回滚，定位可以在恢复之后做");

        System.out.println();
        System.out.println("=== 第二步：保留现场 ===");
        String[] evidence = {
                "订单服务：jstack x3（间隔 5s），看线程都卡在哪",
                "订单服务：jmap -histo:live，确认没有对象异常增长",
                "用户服务：GC 日志 + jstat，确认不是 GC 问题",
                "两边：线程池指标、连接池等待数、下游调用 RT 曲线",
                "变更记录：14:00 之前的所有发布/配置/DDL 操作",
                "入口流量：QPS 是否突增（本次没有，稳定在 900）"
        };
        for (String e : evidence) {
            System.out.println("  - " + e);
        }

        System.out.println();
        System.out.println("=== 第三步：按证据缩小范围 ===");
        diagnose("CPU 42% 不高", "排除死循环、GC 打满", "去看等待类问题");
        diagnose("线程池 active=200/200 且队列堆积", "请求进得来出不去", "瓶颈在依赖调用，不在计算");
        diagnose("用户服务 CPU 35% 但 RT 1200ms", "不是算力不足", "在等什么：锁、连接池、还是串行调用");
        diagnose("jstack 显示大量线程停在 getUserPoints", "订单线程都在等用户服务", "确认是同步调用拖死上游");
        diagnose("用户服务栈显示大量 BLOCKED 在同一把锁", "积分查询用了全局锁 + 串行查库", "根因锁定");

        System.out.println();
        System.out.println("=== 第四步：根因 ===");
        System.out.println("  新版本在「下单主链路」里同步调用了用户服务的积分查询；");
        System.out.println("  该查询内部用 synchronized 包住了一次全表扫描，QPS 900 时全部串行化；");
        System.out.println("  用户服务 RT 上升 -> 订单服务 200 个线程被占满 -> 队列堆积 -> 超时与错误率飙升。");
        System.out.println("  本质：把非核心依赖放进了核心链路，且没有超时、没有隔离、没有熔断。");

        System.out.println();
        System.out.println("=== 第五步：修复（分短期/长期）===");
        String[][] fixes = {
                {"短期", "积分查询改为异步消息，下单只发事件不等结果", "主链路不再依赖用户服务"},
                {"短期", "所有跨服务调用加超时（订单->用户 300ms）", "慢下游不会无限占用线程"},
                {"短期", "线程池按依赖隔离，用户服务调用独立池", "一个依赖挂掉不拖垮整个服务"},
                {"长期", "积分查询去掉全局锁，改成分片查询 + 缓存", "消除串行瓶颈"},
                {"长期", "接入熔断：连续失败 20 次或错误率 > 50% 直接快速失败", "故障时不再打满下游"},
                {"长期", "压测加入「下游变慢」场景，验证超时与降级", "把故障演练变成常规动作"}
        };
        System.out.printf("  %-8s %-52s %s%n", "类型", "动作", "目的");
        for (String[] f : fixes) {
            System.out.printf("  %-8s %-52s %s%n", f[0], f[1], f[2]);
        }

        System.out.println();
        System.out.println("=== 第六步：复盘要点 ===");
        String[] review = {
                "影响面：14:02-14:08，约 6 分钟，失败订单 1.2 万笔，涉及金额 86 万",
                "为什么监控没提前发现：只有 RT 告警，没有「线程池饱和度」与「下游 RT」告警",
                "为什么变更能上线：缺少「核心链路新增同步依赖」的评审卡点",
                "改进项：加线程池/连接池饱和度告警、下游 RT 告警、变更分级与灰度",
                "改进项 owner 与截止时间必须写清楚，否则复盘等于没做"
        };
        for (String r : review) {
            System.out.println("  - " + r);
        }

        System.out.println();
        System.out.println("=== 排查时间线 ===");
        for (String t : TIMELINE) {
            System.out.println("  " + t);
        }

        System.out.println();
        System.out.println("=== 关键指标快照 ===");
        METRICS.forEach((k, v) -> System.out.printf("  %-22s %s%n", k, v));

        System.out.println();
        System.out.println("=== 一句话总结 ===");
        System.out.println("  线上问题 90% 不是「代码写错了一行」，而是「依赖关系没设计好」：");
        System.out.println("  核心链路不能依赖非核心服务，任何跨进程调用都必须有超时、隔离和熔断。");
    }

    static void step(String time, String action, String result) {
        TIMELINE.add(time + "  " + action + "  ->  " + result);
        METRICS.put(time, result);
        System.out.println("  [" + time + "] " + action);
        System.out.println("          结果：" + result);
    }

    static void diagnose(String evidence, String inference, String next) {
        System.out.println("  证据：" + evidence);
        System.out.println("    推断：" + inference);
        System.out.println("    下一步：" + next);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 按本课的六步套路，为"Redis 连接池耗尽导致接口超时"写一份排查方案（现象、证据、根因、修复）。
 * 2. 说明为什么"把超时从 1s 调到 5s"在雪崩场景下会让情况更糟。
 * 3. 写一份复盘模板，至少包含影响面、时间线、根因、监控缺口、改进项与 owner 六项。
 * ============================================================
 */
