/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：48-06
 * 课名：容量规划与 QPS
 * ------------------------------------------------------------
 * 知识点：
 *   - **推理容量的单位是 token/秒，不是 QPS**：QPS × 平均输出 token = 引擎吞吐需求——**两个 10 QPS 服务（输出 100 vs 2000 token）需要的算力差 20 倍**。容量规划的输入是 token 流量画像。
 *   - **规划公式链**：`需求吞吐 = QPS × 平均输出 token`；`单卡吞吐 = 压测最优工作点吞吐`（48-03 曲线）；`所需卡数 = 需求吞吐 ÷ 单卡吞吐 × 冗余系数(1.5~2)`。**冗余给灰度/故障/峰值留余量**——与 42-05 错误预算同样的保守哲学。
 *   - **峰值画像决定弹性策略**：白天高峰/夜间低谷（K8s 按时段扩缩）、大促尖峰（预留池）、工作日与周末差异——**按画像分时段容量，而不是按峰值常驻**（47-08 成本思想）。
 *   - **排队论直觉**：利用率越高排队越长（M/M/c 的非线性）——**90% 利用率的推理服务 P99 必然爆炸**，生产目标利用率 60~70%。这是“压测 32 并发很好、上线 40 并发就崩”的原因。
 *   - **多模型池的容量分配**：大模型池（复杂请求）、小模型池（分类）、本地池（合规）——按 44-10 路由后的流量分布分配容量，**路由变更会改容量需求**（联动的容量管理）。
 *   - **扩容的滞后**：GPU 供给（云上申请分钟级、采购月级）——**容量水位告警前置**（峰值吞吐 70% 即告警），扩容演练提前于业务增长。
 *   - 易错点：按 QPS 而非 token 估容量（差一个数量级）；单卡吞吐用官方跑分（自己的 prompt 长度/输出分布才是真实值）；目标利用率 90%+（排队延迟非线性爆炸）；容量告警晚于用户感知（水位 70% 不告警）。
 * ============================================================
 */

package com.heima.ch48;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ServingCapacity {
    static void tokenVsQps() {
        System.out.println("【容量单位：token/秒 吞噬 QPS 直觉】");
        record Service(String name, int qps, int avgOut, long tokPerSec) {
        }
        List<Service> services = List.of(
                new Service("意图分类", 100, 20, 2_000),
                new Service("工单摘要", 20, 400, 8_000),
                new Service("营销文案", 5, 1500, 7_500));
        for (Service s : services) {
            System.out.printf("  %-8s QPS=%-4d 平均输出=%-5d -> %6d tok/s 需求%n",
                    s.name(), s.qps(), s.avgOut(), s.tokPerSec());
        }
        System.out.println("  20 QPS 的摘要服务比 100 QPS 的分类更吃算力 —— 按输出算，不按请求数");
        System.out.println();
    }

    static void capacityFormula() {
        System.out.println("【卡数规划公式链】");
        long demand = 30_000;                 // 集群 tok/s 需求（峰值）
        double perGpu = 900;                  // 压测最优工作点单卡吞吐
        double utilization = 0.65;            // 生产目标利用率（排队论）
        double redundancy = 1.3;              // 故障/灰度冗余
        double need = demand / (perGpu * utilization) * redundancy;
        System.out.printf("  需求 %d tok/s ÷ (单卡 %.0f × 利用率 %.2f) × 冗余 %.1f = %.0f 卡%n",
                demand, perGpu, utilization, redundancy, need);
        System.out.println("  三个人为参数都要有依据：利用率来自排队论（60~70%），冗余来自 SLO 承诺");
        System.out.println();
    }

    static void utilizationCliff() {
        System.out.println("【利用率悬崖：90% 的 P99 灾难】");
        record Load(double util, int queueDepth, double p99Factor) {
        }
        List<Load> loads = List.of(
                new Load(0.5, 1, 1.2),
                new Load(0.65, 2, 1.6),
                new Load(0.8, 5, 3.0),
                new Load(0.9, 12, 8.0),
                new Load(0.95, 30, 20.0));
        for (Load l : loads) {
            System.out.printf("  利用率 %.0f%% -> 排队深度≈%2d，P99 延迟放大 ×%.1f%n",
                    l.util() * 100, l.queueDepth(), l.p99Factor());
        }
        System.out.println("  排队论非线性：最后 10% 的利用率吃掉 6 倍延迟");
        System.out.println("  生产目标：峰值利用率 60~70%，水位 70% 告警");
        System.out.println();
    }

    static void capacityAlarm() {
        System.out.println("【容量水位告警（前置于用户感知）】");
        List<String> rules = List.of(
                "峰值时段吞吐达压测最优点的 70% -> 容量预警（扩容立项）",
                "网关排队深度持续 > 0 -> 当前副本不足",
                "GPU 利用率均值 > 75% 持续 30min -> 排查批配置或扩容",
                "路由流量分布变化（44-10 改路由）-> 重算各池容量");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  联动：容量预警 -> 47-08 弹性（K8s 扩副本）或采购流程");
    }

    public static void main(String[] args) {
        tokenVsQps();
        capacityFormula();
        utilizationCliff();
        capacityAlarm();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用真实流量画 token 需求画像（分时段），代入公式链算出所需卡数，并标注三个人为参数的依据。
 * 2. 压测找出“利用率悬崖”：阶梯加压记录 P99 与利用率，标出你的服务应设的目标利用率。
 * 3. 思考题：为什么 44-08 的路由变更会引发容量重算？构造一个“路由把流量从小模型切到大模型”导致大池容量告警的场景。
 * ============================================================
 */
