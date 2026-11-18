/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：55-09
 * 课名：多租户下的可观测
 * ------------------------------------------------------------
 * 知识点：
 *   - **可观测的租户维度化**（42 章体系的多租户特化）：**指标**带 tenant 标签（基数评估！大 SaaS 万级租户不能全量打标签——**用 TopN 租户标签 + 其余聚合**，42-04 基数红线）；**日志**带 tenant_id 字段（42-02 检索）；**trace** 属性带租户（42-07）。
 *   - **租户级 SLA 是 SaaS 的产品承诺**：每租户的可用性/延迟分位——**聚合指标掩盖单个大客户的劣化**（平均 50ms 里有个租户 2s）——**TopN 客户单独看板**（46-10 服务化）。
 *   - **告警的租户维度**：单租户错误率异常（可能该租户的配置/数据问题）vs 全局错误率（平台问题）——**两级告警定位**（42-06 分级）；按租户的告警通知路由（企业版客户有专属通道，42 章告警对接）。
 *   - **成本与配额的可观测**：租户资源消耗报表（计费依据 + 容量规划 55-05）——**观测即商业数据**。
 *   - 易错点：全量租户打指标标签（基数爆炸，42-04）；告警不分平台级/租户级（值班被单租户问题淹没）；租户级日志成本失控（42-03 分层存储按租户分级——企业版保留长）。
 * ============================================================
 */

package com.heima.ch55;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TenantObservability {
    static void metricStrategy() {
        System.out.println("【指标标签的租户策略：TopN + 聚合】");
        record Tenant(String id, long qps) {
        }
        List<Tenant> tenants = List.of(
                new Tenant("t-big-1", 8000), new Tenant("t-big-2", 5000),
                new Tenant("t-long-tail-1", 30), new Tenant("t-long-tail-2", 20));
        int topN = 2;
        long longTailQps = 0;
        for (Tenant t : tenants) {
            if (t.qps() >= 3000) {
                System.out.printf("  指标带标签 tenant=%s（TopN 独立序列）%n", t.id());
            } else {
                longTailQps += t.qps();
            }
        }
        System.out.printf("  长尾租户聚合为一个序列（qps=%d），基数受控%n", longTailQps);
        System.out.println("  排障时：长尾租户用日志/trace 按 tenant_id 查，不用指标（42-03）");
        System.out.println();
    }

    static void twoLevelAlert() {
        Map<String, String> alerts = new LinkedHashMap<>();
        alerts.put("平台级", "全局错误率 > 1% -> 全员告警（影响所有租户）");
        alerts.put("租户级", "单租户错误率 > 5% -> 租户支持队列（专属通道）");
        alerts.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
        System.out.println("  区分价值：单租户异常通常是该租户配置/数据问题，不是平台事故");
    }

    public static void main(String[] args) {
        metricStrategy();
        twoLevelAlert();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现租户指标策略（TopN 打标签 + 长尾聚合），验证基数受控与 TopN 排障可用。
 * 2. 实现两级告警路由（平台级 -> 值班；租户级 -> 客户支持队列），构造两类事故验证路由。
 * 3. 思考题：为什么"聚合指标掩盖单个大客户劣化"？构造一个平均正常但大客户 P99 爆炸的数据集，说明 TopN 看板的必要性。
 * ============================================================
 */
