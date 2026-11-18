/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：52-10
 * 课名：综合毕业案例③：故障演练与毕业答辩
 * ------------------------------------------------------------
 * 知识点：
 *   - **毕业答辩的两个部分**：**故障演练现场**（给你一个注入故障的模拟环境，限时定位与恢复——验证"能排障"）与**架构答辩**（评审者围绕你的设计追问——验证"能设计与权衡"）。**这是 52-07 清单的最终考试**。
 *   - **演练题的出题原则**：从真实事故库出题（42-11/51 章案例）、故障链嵌套（表象掩盖根因）、时间压力真实（生产感）——**考过程纪律（42 章 SOP）而不只是答案**。
 *   - **答辩的追问网络**：每个决策被追问三层——为什么选它（52-02 ADR）？失败了怎么办（降级/回滚）？规模 ×10 会怎样（演进路线 52-09）？**答不上第三层说明没想过演进**。
 *   - **评分维度**：定位路径（有 SOP 还是乱试）、恢复速度（RTO 意识）、权衡表达（52-04 取舍模板）、知识连接（跨章引用 52-06 地图）——**四维评分，不是对错二分**。
 *   - 易错点：演练只背答案（换了故障形态就懵）；答辩只讲方案不讲代价（52-04 的教训）；故障定位跳过证据（靠猜，42 章纪律缺失）；时间管理失控（前 10 分钟讲不完主流程）。
 * ============================================================
 */

package com.heima.ch52;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class GradDefense {
    record DrillScenario(String fault, String symptom, String sopPath, String expectedRto) {
    }

    static void drillScenarios() {
        System.out.println("【演练题库（三选一限时 40 分钟）】");
        List<DrillScenario> drills = List.of(
                new DrillScenario("下单 P99 突增 5 倍",
                        "用户反馈卡，错误率正常",
                        "指标分桶（42-10）-> trace 定位段 -> 日志证据 -> 修复",
                        "20 分钟内恢复"),
                new DrillScenario("支付回调丢失",
                        "渠道账单 vs 本地对不上（长款）",
                        "对账差异单（51-05）-> 查单补处理 -> 复核",
                        "30 分钟内闭环"),
                new DrillScenario("MQ 积压 50 万",
                        "消费 lag 持续增长（43-08）",
                        "lag 指标 -> 消费者吞吐分析 -> 扩容/批量/跳过策略",
                        "1 小时内追平"));
        for (DrillScenario d : drills) {
            System.out.printf("  故障：%s%n    表象：%s%n    期望路径：%s%n    RTO：%s%n",
                    d.fault(), d.symptom(), d.sopPath(), d.expectedRto());
        }
        System.out.println();
    }

    static void defenseQuestions() {
        System.out.println("【答辩追问网络：三层深挖示例】");
        Map<String, List<String>> q = new LinkedHashMap<>();
        q.put("你用了 Redis 预扣库存", List.of(
                "为什么不用 DB 行锁？（39-01 热点行）",
                "Redis 挂了怎么办？（L4 兜底/对账补偿，44-08）",
                "流量 ×10 会怎样？（分桶预扣/多实例，39-05）"));
        q.put("你选了模块化单体", List.of(
                "为什么不上微服务？（49-08 触发条件未满足）",
                "未来怎么拆？（上下文边界已立，触发即拆）",
                "模块边界怎么守护？（ArchUnit 进 CI，49-08）"));
        for (Map.Entry<String, List<String>> e : q.entrySet()) {
            System.out.println("  " + e.getKey() + "：");
            for (String s : e.getValue()) {
                System.out.println("    - " + s);
            }
        }
        System.out.println("  第三问（规模演进）是区分度所在 —— 52-09 路线的价值");
        System.out.println();
    }

    static void scoring() {
        System.out.println("【四维评分表】");
        String[][] rows = {
                {"定位路径", "有 SOP：指标->trace->日志->修复", "42 章纪律"},
                {"恢复速度", "RTO 意识 + 先恢复后优化", "51-10 原则"},
                {"权衡表达", "每个决策带代价（52-04 模板）", "49 章 ADR 纪律"},
                {"知识连接", "跨章引用与思想迁移（52-06）", "地图索引能力"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-30s 来源：%s%n", r[0], r[1], r[2]);
        }
        System.out.println("  通过线：四维均达 60% 且无零分项 —— 毕业认证");
    }

    public static void main(String[] args) {
        drillScenarios();
        defenseQuestions();
        scoring();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 组织一次模拟答辩：同伴出题（演练+追问网络），你限时完成并录音复盘，对照四维评分自评。
 * 2. 把你此前做过的综合案例整理成"答辩作品集"（每个案例一页：问题/方案/权衡/证据）。
 * 3. 思考题：为什么答辩追问"规模 ×10"最能区分水平？从局部最优与演进准备的差异分析（52-09 路线的存在意义）。
 * ============================================================
 */
