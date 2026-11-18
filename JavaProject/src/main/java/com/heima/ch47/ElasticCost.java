/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：47-08
 * 课名：弹性伸缩与成本
 * ------------------------------------------------------------
 * 知识点：
 *   - **K8s 弹性三层**：**HPA**（水平自动扩缩，按 CPU/内存/自定义指标）、**VPA**（垂直调整单 Pod 资源，需重启）、**Cluster Autoscaler**（节点池扩缩）。**FaaS 的弹性内置**（并发驱动），K8s 的弹性要自己配。
 *   - **伸缩的正确指标**：CPU 是滞后指标——**更贴近用户的是 QPS/并发/队列深度**（42-05 饱和度信号做伸缩输入）。缩容要防抖（稳定窗口），避免流量抖动引发雪崩式伸缩。
 *   - **KEDA 事件驱动伸缩**：按消息 lag、队列深度扩缩——**43 章消费积压直接驱动副本数**（lag>阈值 -> 扩容），缩容到零支持（低频消费者白天 0 副本）。
 *   - **Spot/抢占式实例的成本杠杆**：抢占式节点便宜 60~90%，适合无状态可重启负载（批处理、CI）；**核心服务不跑 Spot**（节点随时被回收）。组合：核心服务按需 + 批处理 Spot。
 *   - **成本可观测**：按命名空间/服务归因成本（request×时长）、闲置检测（requests 远高于实际用量 -> rightsizing）、资源配额防失控。**41-10 DORA 的成本版：成本也进例会**。
 *   - **弹性演练**：扩容速度实测（从触发到就绪的分钟级延迟——发布/扩容都有滞后），压测验证 HPA 配置的响应曲线（42 章方法论）。
 *   - 易错点：HPA 目标 CPU 设太高（打满才扩，已晚）；min/max 无上限（故障循环扩容烧钱）；缩容抖动（稳定窗口没配）；Spot 跑核心服务（被回收即事故）；VPA 与 HPA 同指标冲突（一个加 Pod 一个加内存打架）。
 * ============================================================
 */

package com.heima.ch47;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ElasticCost {
    static void hpaConfig() {
        System.out.println("【HPA 配置：指标、阈值与防抖】");
        record Param(String name, String value, String why) {
        }
        List<Param> params = List.of(
                new Param("目标指标", "QPS/并发（优先）或 CPU 65%", "CPU 滞后，QPS 贴近用户"),
                new Param("min/max 副本", "3 ~ 20", "下限保可用，上限防失控"),
                new Param("稳定窗口", "缩容 300s / 扩容 60s", "防流量抖动引发振荡"),
                new Param("行为策略", "扩容最多 +100%/15s", "防止瞬间翻倍打挂下游"));
        for (Param p : params) {
            System.out.printf("  %-10s %-26s %s%n", p.name(), p.value(), p.why());
        }
        System.out.println("  压测验证：阶梯加压，记录“QPS -> 副本数”的响应滞后");
        System.out.println();
    }

    static void kedaLag() {
        System.out.println("【KEDA：消费积压驱动伸缩】");
        record Trigger(String metric, String rule, String action) {
        }
        List<Trigger> ts = List.of(
                new Trigger("kafka lag", "lag > 10000 -> 扩容", "43-08 反压的自动处置"),
                new Trigger("队列空闲", "lag=0 持续 10min -> 缩到 0", "低频消费者夜间零成本"),
                new Trigger("冷启动保护", "min=1（有状态消费者）", "缩到零要求无状态/可冷启"));
        for (Trigger t : ts) {
            System.out.printf("  %-12s %-24s %s%n", t.metric(), t.rule(), t.action());
        }
        System.out.println();
    }

    static void spotStrategy() {
        System.out.println("【Spot 实例的成本杠杆】");
        List<String> rules = List.of(
                "适合：批处理、CI、无状态可重启服务（43 章流作业的批模式）",
                "不适合：共识集群（45-10）、核心常驻、有状态主节点",
                "组合：核心服务按需节点池 + 批处理 Spot 节点池 + 亲和性隔离",
                "兜底：Spot 回收告警 -> 工作负载漂移到按需池");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  收益：批处理成本降 60~90%；风险：2 分钟回收通知要能处理");
        System.out.println();
    }

    static void costObservability() {
        System.out.println("【成本可观测与治理】");
        List<String> items = List.of(
                "按 namespace/服务归因：request×时长折算月成本（41-10 台账思想）",
                "闲置检测：requests 与实际用量 diff -> rightsizing 建议",
                "配额：namespace ResourceQuota 防失控",
                "月度成本例会：Top5 成本服务与优化动作");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  原则：成本是架构决策的输入，不是季度末的惊讶");
    }

    public static void main(String[] args) {
        hpaConfig();
        kedaLag();
        spotStrategy();
        costObservability();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的服务配置 HPA（QPS 指标 + 防抖参数），压测验证扩缩曲线并记录响应滞后。
 * 2. 用 KEDA 把一个消费者的副本数绑定到 lag，构造积压场景验证自动扩容与缩容到零。
 * 3. 思考题：为什么 Spot 实例对共识集群是禁区？结合 45-10 成员变更与节点回收的关系分析。
 * ============================================================
 */
