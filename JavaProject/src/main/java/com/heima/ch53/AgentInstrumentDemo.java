/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：53-08
 * 课名：链路追踪 SkyWalking
 * ------------------------------------------------------------
 * 知识点：
 *   - **SkyWalking 的架构**：Agent（字节码增强，零侵入埋点——42-07 的自动探针）-> OAP 收集聚合（gRPC 上报）-> 存储（ES/达梦）-> UI。**与 OpenTelemetry 的关系**：OTel 是 API/协议标准，SkyWalking 是带 UI 的完整 APM——42-08 的"概念 vs 组件"论断在此印证。
 *   - **字节码增强的原理**：Agent 启动时改写字节码（Transformer），在插件匹配的类方法前后插入埋点——**零侵入的代价是版本兼容**（框架升级后插件失效），42 章的"自动探针为主 + 关键手工补充"在此有工程细节。
 *   - **SkyWalking 的特色功能**：拓扑图自动生成、告警规则（OAP 内置 Webhook——42-06 告警对接）、JVM 指标内置采集、** Allegro：无侵入性能剖析**（48-09 火焰图的平台化）。
 *   - **采样与存储**：Agent 采样率配置（42-08 头部采样）；存储 TTL 管理（ES 索引按天，46-03 生命周期思想）；OAP 集群水平扩展。
 *   - 易错点：Agent 版本与框架不兼容（埋点静默失效——**上线后必须验证 trace 完整性**）；OAP 单点；ES 存储无 TTL（磁盘爆）；采样率全量（42-08 成本教训）。
 *   - **与 42 章的自建观测对照**：42 章手写了 Span 树/采样/告警——SkyWalking 是这些概念的产品化集合。
 * ============================================================
 */

package com.heima.ch53;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class AgentInstrumentDemo {
    static void simulate(String frameworkVersion, boolean agentPluginSupports) {
        Map<String, String> trace = new LinkedHashMap<>();
        if (agentPluginSupports) {
            trace.put("span-1", "http GET /order (auto)");
            trace.put("span-2", "mysql SELECT (auto)");
        } else {
            trace.put("span-1", "http GET /order (auto)");
            trace.put("gap", "mysql 调用未被增强 -> 链路断裂（42-07 断裂点）");
        }
        System.out.printf("  框架版本 %s / 插件支持=%b：%s%n",
                frameworkVersion, agentPluginSupports, trace);
        System.out.println();
    }

    public static void main(String[] args) {
        simulate("3.2.x", true);
        simulate("3.5.x(新)", false);
        System.out.println("  上线验证清单：任抽 3 条请求核对 span 完整性 —— 静默失效的防线");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 模拟 Agent 字节码增强：为给定类方法生成"前后插桩"的伪代码，验证三框架版本的插件匹配。
 * 2. 实现"trace 完整性巡检"：抽样请求核对首尾 span 与中间断裂点，输出缺失报告（42-07 断裂点清单的自动化）。
 * 3. 思考题：为什么 SkyWalking 的"字节码增强"比"SDK 显式埋点"更适合大规模推广？从侵入成本与版本兼容代价的权衡分析。
 * ============================================================
 */
