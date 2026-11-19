/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：58-08
 * 课名：规则热更新与灰度验证
 * ------------------------------------------------------------
 * 知识点：
 *   - **规则热更新是规则引擎的核心诉求**：业务改规则不应等服务发版——**配置中心（53-03）承载规则**，客户端长轮询/推送获取变更，**秒级生效**；规则变更必须**版本化**（可回滚）与**审计**（51-10）。
 *   - **灰度验证的四道关**：① **离线回放**（历史样本重放，对比新旧规则决策差异）；② **影子模式**（新规则只算不生效，记录"本会做什么"）；③ **小流量灰度**（1%~5% 真实流量，看指标）；④ **全量 + 观察期**。**跳过任何一环都是事故温床**。
 *   - **规则变更的"影响面预览"**：新规则全量上线前**用最近 N 天样本跑一遍**，输出：新增拦截量、新增误拦（白名单客户被拦）、动作分布变化——**用数字说服业务方**（54-08 A/B 思想）。
 *   - **回滚设计**：规则版本号 + 一键回退（保留最近 N 版）；**回滚演练**（47-10）——规则回滚比代码回滚快（配置级），但要验证回滚后行为正确。
 *   - **变更风险的分类**：放宽类（少拦，资损风险）、收紧类（多拦，客诉风险）、符号/阈值类（最容易手滑）——**放宽类要更严格的门禁**（资损不可逆，客诉可补偿）。
 *   - 易错点：规则直接全量生效（无灰度）；变更影响面无评估（上线才发现误拦 VIP）；规则无版本（回滚靠手改）；放宽类变更无额外审批（资损）。
 * ============================================================
 */

package com.heima.ch58;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class RuleHotUpdate {
    record RuleVersion(String version, String expr, String action, long effectiveAtMs) {
    }

    record Sample(String eventId, long amount, boolean vip) {
    }

    record Impact(long total, long newBlocks, long newChallenges, List<String> vipFalsePositives) {
    }

    /** 影响面预览：历史样本回放，对比新旧决策 */
    static Impact preview(List<Sample> samples, long oldThreshold, long newThreshold) {
        long newBlocks = 0, newChallenges = 0;
        List<String> vipHits = new ArrayList<>();
        for (Sample s : samples) {
            boolean oldHit = s.amount() > oldThreshold;
            boolean newHit = s.amount() > newThreshold;
            if (!oldHit && newHit) {
                newBlocks++;
                if (s.vip()) {
                    vipHits.add(s.eventId());
                }
            }
        }
        return new Impact(samples.size(), newBlocks, newChallenges, vipHits);
    }

    /** 灰度放量：按事件 id 哈希分流 */
    static boolean inGray(String eventId, int percent) {
        return Math.abs(eventId.hashCode()) % 100 < percent;
    }

    static void rollout(List<Sample> samples, long oldThreshold, long newThreshold) {
        System.out.println("【灰度放量推演：10% -> 50% -> 100%】");
        for (int pct : new int[]{10, 50, 100}) {
            long grayCount = samples.stream()
                    .filter(s -> inGray(s.eventId(), pct)).count();
            long affected = samples.stream()
                    .filter(s -> inGray(s.eventId(), pct))
                    .filter(s -> s.amount() > newThreshold && s.amount() <= oldThreshold)
                    .count();
            System.out.printf("  灰度 %3d%%：覆盖样本 %d，其中新增拦截 %d%n", pct, grayCount, affected);
        }
    }

    public static void main(String[] args) {
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            samples.add(new Sample("e-" + i, 40_000 + (i * 137L) % 120_000, i % 20 == 0));
        }
        long oldT = 120_000, newT = 80_000;          // 收紧阈值（多拦）
        Impact imp = preview(samples, oldT, newT);
        System.out.printf("【影响面预览】样本 %d，新增拦截 %d（%.1f%%），涉及 VIP %d 个%n",
                imp.total(), imp.newBlocks(), imp.newBlocks() * 100.0 / imp.total(),
                imp.vipFalsePositives().size());
        System.out.println("  VIP 误伤清单：" + imp.vipFalsePositives()
                .subList(0, Math.min(3, imp.vipFalsePositives().size())));
        System.out.println("  决策：VIP 误伤需白名单豁免后再灰度");
        System.out.println();
        rollout(samples, oldT, newT);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整影响面预览器：多规则叠加、输出"新增拦截/新增放行/动作分布变化"，用 1 万历史样本验证。
 * 2. 实现影子模式：新规则只记录不生效，一周后统计"若生效会拦多少"，对比灰度实测差异。
 * 3. 思考题：为什么"放宽类"规则变更要比"收紧类"更严格？从资损不可逆、客诉可补偿、监管审计三个角度分析。
 * ============================================================
 */
