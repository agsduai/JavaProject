/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：52-01
 * 课名：需求分析与容量估算
 * ------------------------------------------------------------
 * 知识点：
 *   - **系统设计的第一步不是画架构图，是量化问题**：多少用户、多少 QPS、多少数据、多大延迟容忍——**没有数字的架构讨论都是玄学**。容量估算给后续每个决策（缓存/分片/引擎）提供标尺。
 *   - **估算的标准动作**：① 日活/峰值比例（日活 100 万 -> 峰值 QPS = 日活 × 人均请求数 ÷ 86400 × 集中系数，通常再乘 3~5 倍峰值因子）；② **读写比**（读多写少决定缓存收益，写多决定分库压力）；③ 数据增长（三年后量，39-01）；④ 单请求成本（token/字节/DB 行）。
 *   - **估算的价值在范围不是精度**：算出"峰值 QPS 约 2000±500"就足够定架构——**不需要精确到个位**，需要的是量级正确（100 还是 10000 的差别是架构级的）。
 *   - **非功能需求的量化**：可用性目标（99.9% = 月 43 分钟）、延迟 P99、一致性级别（45-02）、成本上限——**每个都写进需求文档**，否则架构评审没有裁决依据。
 *   - **反向估算校验需求**：估算出"需要 20 台机器 + 5 人团队维护"时，回头看需求是否真的值得——**容量估算也是可行性检查**（商业模式撑不起架构成本就砍需求）。
 *   - 易错点：按峰值常备容量（47-08 弹性才是答案）；把日活当 QPS（差两个数量级）；忽略读写比（缓存决策失效）；非功能需求不量化（"要快""要稳"无法验收）。
 * ============================================================
 */

package com.heima.ch52;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RequirementCapacity {
    static void estimateFlow() {
        System.out.println("【订单系统容量估算五步】");
        long dau = 1_000_000;
        double reqPerUser = 10;                 // 人均下单相关请求/日
        double concentration = 0.4;             // 集中在 20% 时段（86400×0.2）
        double peakFactor = 3.0;                // 大促/突发峰值因子
        long avgQps = Math.round(dau * reqPerUser / (86400 * 0.2));
        long peakQps = Math.round(avgQps * peakFactor);
        System.out.printf("  日活 %d × 人均 %.0f 请求 = 日请求 %.0f 万%n", dau, reqPerUser, dau * reqPerUser / 1e4);
        System.out.printf("  集中时段（20%% 天时间）平均 QPS ≈ %d%n", avgQps);
        System.out.printf("  峰值 QPS ≈ %d（×%.0f 峰值因子）%n", peakQps, peakFactor);
        System.out.printf("  读写比 10:1 -> 读 %d / 写 %d QPS（写是分库依据，39 章）%n",
                peakQps / 11 * 10, peakQps / 11);
        System.out.println();
    }

    static void nfrTable() {
        System.out.println("【非功能需求量化表（写进需求文档）】");
        Map<String, String> nfr = new LinkedHashMap<>();
        nfr.put("可用性", "核心链路 99.95%（月 22 分钟预算，42-05）");
        nfr.put("延迟", "下单 P99 < 300ms；列表 P99 < 50ms");
        nfr.put("一致性", "库存扣减强一致；评论最终一致（45-02）");
        nfr.put("数据增长", "订单三年 5.4 亿行 -> 16 分片（39-01）");
        nfr.put("成本上限", "基础设施 ≤ GMV 的 3%");
        for (Map.Entry<String, String> e : nfr.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  每项都可验收 —— 架构评审的裁决依据");
        System.out.println();
    }

    static void feasibilityCheck() {
        System.out.println("【反向校验：架构成本 vs 商业价值】");
        record Cost(String item, String estimate, String verdict) {
        }
        List<Cost> costs = List.of(
                new Cost("基础设施", "18 卡 GPU + 40 节点 ≈ 月 30 万", "占 GMV 2.5% -> 通过"),
                new Cost("人力", "5 人长期维护", "核心域值得"),
                new Cost("风险", "新架构学习成本 3 个月", "纳入排期"));
        for (Cost c : costs) {
            System.out.printf("  %-6s %-28s %s%n", c.item(), c.estimate(), c.verdict());
        }
        System.out.println("  估算的终点是决策：做、不做、还是砍需求");
    }

    public static void main(String[] args) {
        estimateFlow();
        nfrTable();
        feasibilityCheck();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的业务做完整容量估算（QPS/读写比/数据增长/非功能表），每个数字标注推导过程。
 * 2. 找一个"要快、要稳"的模糊需求，改写成可验收的非功能量化表。
 * 3. 思考题：为什么容量估算"要范围不要精度"？从架构决策的敏感性（量级变化才改变决策）分析。
 * ============================================================
 */
