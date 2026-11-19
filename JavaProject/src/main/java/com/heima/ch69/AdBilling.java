/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-09
 * 课名：广告计费与对账
 * ------------------------------------------------------------
 * 知识点：
 *   - **计费的三类事件**：**曝光**（CPM 扣费）、**点击**（CPC 扣费）、**转化**（CPA 扣费）——**每次计费都要有幂等键**（eventId/clickId），**重复上报不能重复扣费**（39/51 章原则）。
 *   - **计费的两层结构**：**明细（ledger）**（每次计费一条记录：广告主/广告/事件/金额/时间）+ **汇总（账单）**（按日/月聚合）——**明细是事实源，汇总是派生**（63-06 的事实表思想）。
 *   - **扣费与预算的原子性**：扣费成功才允许展示/归因 + 预算扣减（69-05）——**两者要么都成，要么都不成**（本地事务或原子脚本）。
 *   - **对账的三个维度**：**平台内部**（明细汇总 = 账单）、**与广告主**（平台账单 vs 广告主后台数据）、**与流量方**（平台计费 vs 媒体结算）——**差异必须能定位到具体事件**（51-05 对账思想）。
 *   - **无效流量的扣回**：作弊点击（69-08）事后识别 -> 生成"负向明细"（冲正）-> 下期账单抵扣——**不能修改历史明细**（append-only 51-10）。
 *   - 易错点：重复上报重复扣费（无幂等）；扣费与预算不同步（超投或漏扣）；账单与明细对不上（无法解释）；扣回改历史记录（审计失效）。
 * ============================================================
 */

package com.heima.ch69;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AdBilling {
    record BillableEvent(String eventId, String advertiser, String adId, String type,
                         double amountCents) {
    }

    record LedgerEntry(String eventId, String advertiser, String type, double amountCents,
                       boolean reversal, String note) {
    }

    static final List<LedgerEntry> ledger = new ArrayList<>();
    static final Map<String, Double> budgetLeft = new LinkedHashMap<>();
    static final Map<String, Boolean> processed = new HashMap<>();

    static void initBudget() {
        budgetLeft.put("brand-A", 10_000.0);
        budgetLeft.put("brand-B", 5_000.0);
    }

    /** 计费：幂等 + 预算原子扣减 */
    static String charge(BillableEvent e) {
        if (processed.containsKey(e.eventId())) {
            return "重复事件 " + e.eventId() + " -> 忽略（幂等）";
        }
        double left = budgetLeft.getOrDefault(e.advertiser(), 0.0);
        if (left < e.amountCents()) {
            return "预算不足（剩余 " + left + "）-> 不计费";
        }
        budgetLeft.put(e.advertiser(), left - e.amountCents());
        ledger.add(new LedgerEntry(e.eventId(), e.advertiser(), e.type(),
                e.amountCents(), false, "正常计费"));
        processed.put(e.eventId(), true);
        return "计费成功 " + e.amountCents() + " 分（剩余预算 "
                + budgetLeft.get(e.advertiser()) + "）";
    }

    /** 冲正：作弊识别后负向明细（不改历史） */
    static void reverse(String eventId, String reason) {
        for (LedgerEntry e : ledger) {
            if (e.eventId().equals(eventId) && !e.reversal()) {
                ledger.add(new LedgerEntry(eventId + "-REV", e.advertiser(), e.type(),
                        -e.amountCents(), true, "冲正：" + reason));
                budgetLeft.merge(e.advertiser(), e.amountCents(), Double::sum);
                System.out.printf("  冲正 %s：%+.0f 分（%s）%n", eventId, -e.amountCents(), reason);
                return;
            }
        }
    }

    static double sumBy(String advertiser, boolean includeReversal) {
        double sum = 0;
        for (LedgerEntry e : ledger) {
            if (e.advertiser().equals(advertiser) && (includeReversal || !e.reversal())) {
                sum += e.amountCents();
            }
        }
        return sum;
    }

    public static void main(String[] args) {
        initBudget();
        System.out.println("【计费流水】");
        List<BillableEvent> events = List.of(
                new BillableEvent("e-1", "brand-A", "ad-1", "IMPRESSION", 30),
                new BillableEvent("e-2", "brand-A", "ad-1", "CLICK", 200),
                new BillableEvent("e-2", "brand-A", "ad-1", "CLICK", 200),
                new BillableEvent("e-3", "brand-B", "ad-2", "CLICK", 150));
        for (BillableEvent e : events) {
            System.out.printf("  %-4s %-8s %-10s %.0f 分 -> %s%n",
                    e.eventId(), e.advertiser(), e.type(), e.amountCents(), charge(e));
        }

        System.out.println();
        System.out.println("【作弊识别后冲正 e-2】");
        reverse("e-2", "设备指纹命中刷量");

        System.out.println();
        System.out.println("【对账】");
        for (String adv : new String[]{"brand-A", "brand-B"}) {
            double gross = sumBy(adv, false);
            double net = sumBy(adv, true);
            System.out.printf("  %-8s 毛计费=%.0f 冲正后净额=%.0f%n", adv, gross, net);
        }
        System.out.println();

        System.out.println("【明细（append-only）】");
        ledger.forEach(e -> System.out.printf("  %-8s %-8s %-10s %+8.0f %s%n",
                e.eventId(), e.advertiser(), e.type(), e.amountCents(), e.note()));

        System.out.println();
        System.out.println("  对账三层：明细汇总=账单 / 平台 vs 广告主后台 / 平台 vs 媒体结算");
        System.out.println("  冲正用负向明细，绝不修改历史（51-10 审计原则）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现计费中心：幂等 + 预算原子扣减 + 冲正，用 4 个事件（含 1 个重复）验证不重复扣费。
 * 2. 实现对账：对比"平台明细汇总"与"模拟的广告主后台数据"，找出差异事件并解释原因。
 * 3. 思考题：为什么冲正要"负向明细"而不是"删除原记录"？从审计证据与账单可解释性分析。
 * ============================================================
 */
