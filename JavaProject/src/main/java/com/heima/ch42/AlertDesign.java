/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：42-06
 * 课名：告警设计与降噪
 * ------------------------------------------------------------
 * 知识点：
 *   - **告警的第一原则：每一条告警都必须需要一个"人"采取行动**。发到群里没人处理的告警是噪音，噪音的累积效应是**告警疲劳**——真警报也被无视。宁可漏掉"可自愈的小事"，不可淹没"需要人的大事"。
 *   - **告警的三级分类**：P1（用户受损，电话/短信，5 分钟响应）、P2（风险积累，IM 通知，30 分钟响应）、P3（提示观察，日报汇总）。**分级依据是用户影响面，不是技术惊悚程度**。
 *   - **告警规则的正确形状**：`症状 + 持续时间 + 影响范围`。"P99 > 500ms" 不是告警，"P99 > 500ms 持续 5 分钟且影响下单主链路"才是。**瞬时抖动（GC、网络毛刺）用持续时间过滤**，机器周期性任务用时间窗屏蔽。
 *   - **降噪三板斧**：**聚合**（同一事故的 30 条同源告警合并成 1 条事件，按根因分组）、**抑制**（DB 挂了 -> 抑制它下游全部服务的连接错误告警，只发根因）、**静默**（已知维护窗口、已知变更期间，人工静默并记录）。告警平台要支持抑制链与静默窗口，这是工具能力不是纪律问题。
 *   - **自愈优于告警**：能自动恢复的（进程挂了拉起、主从切换、限流兜底）先自愈再告警"已自愈"（P3）。**值班名单里出现"重启一下就好"的告警，就是自动化欠账**。
 *   - **告警闭环度量**：每条告警记录"是否有效"（真实故障/误报/已知问题），月度统计误报率与响应时长。**误报率 > 30% 的规则要么修要么删**。
 *   - 易错点：一条规则告所有实例（实例越多告警越雪崩，应聚合到服务级）；把曲线抖动设成告警（没有持续时间条件）；告警直接发到没有响应责任的群（无 owner）；删掉烦人的告警而不是修好它（问题还在，只是没人看见了）。
 * ============================================================
 */

package com.heima.ch42;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AlertDesign {
    record Alert(String id, String symptom, int durationMin, String scope, String level,
                 boolean actionable) {
    }

    static void classify() {
        System.out.println("【告警分级：按用户影响面】");
        List<Alert> alerts = List.of(
                new Alert("A1", "下单成功率跌破 99%", 5, "全部用户", "P1（电话）", true),
                new Alert("A2", "库存服务 P99 > 800ms", 10, "下单链路", "P2（IM）", true),
                new Alert("A3", "缓存命中率下降 10%", 30, "内部回源增多", "P3（日报）", true),
                new Alert("A4", "某实例单次 GC 2 秒", 1, "无用户影响", "不告警", false));
        for (Alert a : alerts) {
            System.out.printf("  %-3s %-22s 持续%2dmin %-8s %s  可行动=%s%n",
                    a.id(), a.symptom(), a.durationMin(), a.scope(), a.level(), a.actionable());
        }
        System.out.println("  A4 这类瞬时抖动用持续时间条件自然过滤");
        System.out.println();
    }

    static void dedupeAndSuppress() {
        System.out.println("【降噪：聚合与抑制链】");
        record Raw(String time, String service, String msg) {
        }
        List<Raw> raw = List.of(
                new Raw("02:01:03", "db", "主库连接拒绝"),
                new Raw("02:01:05", "order", "DB 连接超时"),
                new Raw("02:01:05", "stock", "DB 连接超时"),
                new Raw("02:01:06", "order", "下单失败率上升"),
                new Raw("02:01:06", "pay", "DB 连接超时"));
        System.out.println("  原始告警 5 条：");
        for (Raw r : raw) {
            System.out.printf("    %s %-6s %s%n", r.time(), r.service(), r.msg());
        }
        System.out.println("  聚合抑制后 1 条事件：");
        System.out.println("    [P1] 根因：主库 db 连接拒绝（02:01:03）");
        System.out.println("    受影响下游：order/stock/pay（连接超时被抑制，不单独通知）");
        System.out.println("    行动：检查主库进程与连接数；下游无需逐个处理");
        System.out.println();
    }

    static void selfHealing() {
        System.out.println("【自愈优于告警：能自动恢复的先恢复】");
        String[][] rows = {
                {"实例进程挂", "K8s 自动重建", "P3 告警「已自愈」"},
                {"Redis 主挂", "哨兵自动切换", "P2 告警「已切换，请验证」"},
                {"接口限流触发", "网关限流兜底", "P2 告警「限流生效，观察」"},
                {"主库连接拒绝", "无法自愈", "P1 告警，需要人"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s -> %-14s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  检验标准：值班群里不再出现「重启一下就好」");
        System.out.println();
    }

    static void feedbackLoop() {
        System.out.println("【告警闭环：每条告警标注是否有效】");
        Map<String, Integer> outcome = new LinkedHashMap<>();
        outcome.put("真实故障（需行动）", 42);
        outcome.put("误报（规则问题）", 31);
        outcome.put("已知问题（待修）", 12);
        int total = outcome.values().stream().mapToInt(Integer::intValue).sum();
        for (Map.Entry<String, Integer> e : outcome.entrySet()) {
            System.out.printf("  %-16s %d 条（%.0f%%）%n", e.getKey(), e.getValue(), e.getValue() * 100.0 / total);
        }
        System.out.printf("  误报率 %.0f%% -> 超 30%% 红线：逐条修规则或删除%n",
                outcome.get("误报（规则问题）") * 100.0 / total);
        System.out.println("  告警质量是运营出来的：每月例会过一遍误报清单");
    }

    public static void main(String[] args) {
        classify();
        dedupeAndSuppress();
        selfHealing();
        feedbackLoop();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 重构你项目的告警规则清单：按 P1/P2/P3 重新分级，给每条规则补"持续时间 + 影响范围"条件，删除不可行动的规则。
 * 2. 实现一个迷你告警聚合器：输入带时间戳的告警流，按根因服务分组输出事件（含抑制的下游列表）。
 * 3. 思考题：为什么"删掉烦人的告警"是危险的，而"修好规则"是正确的？从信息损失与问题潜伏两个角度分析。
 * ============================================================
 */
