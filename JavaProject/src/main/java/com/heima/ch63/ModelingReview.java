/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：63-10
 * 课名：建模评审与反模式
 * ------------------------------------------------------------
 * 知识点：
 *   - **建模评审的五个检查项**：① **业务过程是否清晰**（一张事实表对应一个可描述的业务事件）；② **粒度是否单一且已声明**；③ **维度是否覆盖核心分析方向**（日期/用户/商品/渠道）；④ **事实是否度量且可加性已标注**；⑤ **命名与数据类型是否规范**（统一后缀 `_key/_id/_amt/_cnt`）。
 *   - **七大反模式**：**事实表混粒度**（汇总翻倍）、**维度里放度量**（无法聚合）、**比率存事实**（错误汇总）、**NULL 外键无默认成员**（丢行）、**每报表一套维度**（口径分裂）、**拉链表区间重叠**（历史查错）、**全量快照替代拉链**（存储爆炸）。
 *   - **评审的可自动化部分**：用数据质量规则做"结构校验"（区间连续性/外键完整性/粒度唯一性 46-08）；**人工评审聚焦"业务语义"**（这个粒度对吗/维度够吗）——**机器查一致性，人查合理性**。
 *   - **建模的演进成本**：**改粒度 = 重建事实表**（最贵）；**加维度 = 加外键 + 回刷**（中等）；**加度量 = 加列**（最便宜）——**评审时要为未来留出"加维度"的空间**（63-02）。
 *   - **与 49-11 的关系**：49-11 是"领域模型评审"（聚合/不变量），本章是"分析模型评审"（粒度/维度/度量）——**两套评审清单都在回答"模型是否经得起追问"**。
 *   - 易错点：评审只看字段类型不看业务语义；反模式"先上线再改"（重建成本高）；评审记录不留档（下次同样的问题）；没有 owner（维度表无人维护）。
 * ============================================================
 */

package com.heima.ch63;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ModelingReview {
    record Check(String item, String question, String failExample) {
    }

    static void checklist() {
        System.out.println("【建模评审五问】");
        List<Check> checks = List.of(
                new Check("业务过程", "这张事实表对应哪个业务事件？", "一张表混了订单与支付"),
                new Check("粒度", "一行代表什么？是否唯一？", "既有订单行又有商品行"),
                new Check("维度", "核心分析方向都覆盖了吗？", "少了日期维度无法看趋势"),
                new Check("事实", "都是可度量值且可加性明确？", "把客单价当事实"),
                new Check("命名", "后缀规范一致吗？", "amount/cnt/price 混用"));
        for (Check c : checks) {
            System.out.printf("  %-6s %-24s 反例：%s%n", c.item(), c.question(), c.failExample());
        }
        System.out.println();
    }

    static void antiPatterns() {
        System.out.println("【七大反模式与修复】");
        Map<String, String> patterns = new LinkedHashMap<>();
        patterns.put("事实表混粒度", "拆成两张事实表（订单级/商品级）");
        patterns.put("维度里放度量", "度量移入事实表");
        patterns.put("比率存事实", "存分子分母，查询时计算");
        patterns.put("NULL 外键", "维度加默认成员 -1");
        patterns.put("每报表一套维度", "建一致性维度（63-08）");
        patterns.put("拉链区间重叠", "同批关旧开新 + 区间校验");
        patterns.put("全量快照", "改拉链表或周期快照");
        patterns.forEach((k, v) -> System.out.printf("  %-14s -> %s%n", k, v));
        System.out.println();
    }

    /** 可自动化的结构校验 */
    static void autoChecks() {
        System.out.println("【可自动化的结构校验】");
        record FactRow(String orderId, String productKey, String dateKey, long amount) {
        }
        List<FactRow> facts = List.of(
                new FactRow("9001", "P-1", "2026-11-18", 6900),
                new FactRow("9001", "P-2", "2026-11-18", 3900),
                new FactRow("9001", "P-1", "2026-11-18", 6900));       // 重复行
        Map<String, Integer> grainCheck = new LinkedHashMap<>();
        for (FactRow f : facts) {
            grainCheck.merge(f.orderId() + "|" + f.productKey() + "|" + f.dateKey(), 1, Integer::sum);
        }
        List<String> dup = new ArrayList<>();
        grainCheck.forEach((k, v) -> {
            if (v > 1) {
                dup.add(k + " x" + v);
            }
        });
        System.out.println("  粒度唯一性校验（订单+商品+日期 应唯一）：重复=" + dup);
        long nullFk = facts.stream().filter(f -> f.productKey() == null || f.productKey().isBlank()).count();
        System.out.println("  外键完整性：空外键行=" + nullFk);
        System.out.println("  金额合理性：非负行=" + facts.stream().filter(f -> f.amount() >= 0).count()
                + "/" + facts.size());
        System.out.println();
        System.out.println("  分工：机器查一致性（唯一性/完整性/区间），人查合理性（粒度/维度/语义）");
    }

    public static void main(String[] args) {
        checklist();
        antiPatterns();
        autoChecks();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用五问清单评审你项目里的一张事实表（或虚构），输出问题清单与修复优先级。
 * 2. 实现结构校验器：粒度唯一性、外键完整性、金额非负、拉链区间连续，用 20 行脏数据验证全部检出。
 * 3. 思考题：为什么"改粒度"是成本最高的演进？从事实表重建与下游改写的连锁影响分析。
 * ============================================================
 */
