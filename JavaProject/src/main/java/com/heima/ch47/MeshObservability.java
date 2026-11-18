/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：47-05
 * 课名：Mesh 的可观测性
 * ------------------------------------------------------------
 * 知识点：
 *   - **Mesh 送的第二份大礼：零侵入的全量遥测**——每个 Sidecar 天然上报请求级指标（QPS/延迟/错误率，带 source/destination/version 标签）与调用拓扑。**42 章埋点清单里的“接口四信号”，Mesh 场景自动获得**。
 *   - **Mesh 指标的独特维度**：`source_workload`、`destination_service`、`response_code`、`canary=v2`——**跨服务的错误归属**（谁先报错）与版本对比（41-07）开箱即用。业务代码仍需补业务指标（下单量等），**基础设施指标与业务指标互补**。
 *   - **trace 传播的网格协助**：Sidecar 自动注入/传递 trace headers（W3C traceparent），**应用只需要把头接起来**——42-07 的“断裂点”清单在 Mesh 下大幅缩短（异步与手写 HTTP 仍要自己传）。
 *   - **Mesh 自身的可观测**：控制面（istiod 配置分发成功率、证书签发延迟）、数据面（Envoy 配置版本一致性、连接池水位）——**网格是新的基础设施组件，也要四信号**（42-05），否则“策略没生效”这种故障无法定位。
 *   - **遥测的量与采样**：Sidecar 全量上报基础指标（便宜），访问日志（envoy access log）按需开启（量大），trace 采样走 42-08 的策略（头部 1% + 尾部全保错误）。
 *   - 易错点：只看业务指标不看网格指标（策略未生效时业务指标照旧，错误无从归因）；访问日志全开（存储爆炸）；把 Mesh 遥测当业务监控的替代（业务语义指标缺失）；控制面无监控（配置分发失败全网无感）。
 * ============================================================
 */

package com.heima.ch47;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MeshObservability {
    static void freeMetrics() {
        System.out.println("【Sidecar 免费送的指标（42-01 埋点清单的对账）】");
        Map<String, String> auto = new LinkedHashMap<>();
        auto.put("QPS/延迟/错误率", "按 source/destination/version 维度全量上报");
        auto.put("依赖拓扑", "服务图自动生成（42-07 扇出分析）");
        auto.put("trace 传播", "headers 自动注入/传递（应用补业务 span）");
        Map<String, String> manual = new LinkedHashMap<>();
        manual.put("业务指标", "下单量/支付成功率（Sidecar 不懂业务）");
        manual.put("资源内因", "JVM 内存池、连接池（42-05 饱和度）");
        for (Map.Entry<String, String> e : auto.entrySet()) {
            System.out.printf("  自动：%-16s %s%n", e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : manual.entrySet()) {
            System.out.printf("  仍需埋：%-14s %s%n", e.getKey(), e.getValue());
        }
        System.out.println();
    }

    static void blameAttribution() {
        System.out.println("【错误归属：source/destination 维度的威力】");
        record Row(String src, String dst, String code, long qps, String verdict) {
        }
        List<Row> rows = List.of(
                new Row("gateway", "order", "500", 4, "order 内部错误（真正的病灶）"),
                new Row("order", "stock", "503", 42, "stock 过载/熔断（上游表象）"),
                new Row("order", "db", "-", 0, "无连接错误 -> 排除 DB"));
        for (Row r : rows) {
            System.out.printf("  %-8s -> %-6s code=%-4s %3d rps  %s%n",
                    r.src(), r.dst(), r.code(), r.qps(), r.verdict());
        }
        System.out.println("  归因链：gateway 看到的 500，根因在 order<-stock 的 503");
        System.out.println("  对照无 Mesh：只有各自服务的日志，归属靠人翻");
        System.out.println();
    }

    static void meshSelfOps() {
        System.out.println("【网格自身的四信号：别忘了自己也是组件】");
        List<String> signals = List.of(
                "控制面：配置分发成功率/延迟（策略没生效=隐性故障）",
                "证书：签发延迟与到期监控（47-04 轮换事故预防）",
                "数据面：Sidecar 配置版本一致性、Envoy 内存水位",
                "遥测管道：指标丢弃率（上报过载时先丢遥测再丢请求）");
        for (String s : signals) {
            System.out.println("  - " + s);
        }
        System.out.println("  排障口诀：策略不生效 -> 先查分发，再查 Pod 是否注入");
        System.out.println();
    }

    static void samplingPolicy() {
        System.out.println("【遥测的量治理（42-08 在网格的落地）】");
        List<String> rules = List.of(
                "基础指标全量（聚合数字，便宜）",
                "访问日志按需开启：排障期临时开 + 生产默认关",
                "trace 头部 1% + 错误全保（尾部采样在网关聚合）",
                "指标 label 基数红线依旧：不把 userId 塞进网格指标");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        freeMetrics();
        blameAttribution();
        meshSelfOps();
        samplingPolicy();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 接入网格前后各采集一次遥测，制作“埋点清单对账表”（哪些从手工埋点变成免费赠送）。
 * 2. 用 source/destination 指标做一次错误归因演练：注入 stock 故障，从 gateway 视角追到根因服务并记录耗时。
 * 3. 思考题：为什么“策略没生效”是 Mesh 特有的隐性故障？设计一套控制面分发健康度的监控指标。
 * ============================================================
 */
