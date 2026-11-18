/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：47-02
 * 课名：Sidecar 与数据面
 * ------------------------------------------------------------
 * 知识点：
 *   - **Sidecar 的流量劫持机制**：Pod 网络命名空间内，iptables 把出入流量重定向到 Sidecar 端口——**业务进程对代理无感**（以为在和真实对端通话）。进站流量先过代理再进业务容器，出站反之。
 *   - **Envoy 的配置模型**：Listener（监听）-> Route（路由规则）-> Cluster（上游服务）-> 健康检查/负载均衡；控制面把 VirtualService/DestinationRule 翻译成这些配置。**理解"策略最终编译成 Envoy 配置"才能排查 Mesh 问题**。
 *   - **Sidecar 注入与生命周期**：注入是给 Pod 加一个容器（istio-proxy）；**Sidecar 必须比业务容器先起后停**（否则流量黑洞）——pod 生命周期钩子保证顺序，升级业务镜像时 Sidecar 常驻。
 *   - **超时与重试的执行位置**：重试发生在 Sidecar 而不是业务线程——**业务线程只发起一次调用**，等待时间由 Sidecar 管控（超时预算 44-08 同款思想）。这是"治理下沉"的实质：业务线程不再花时间做重试。
 *   - **常见故障模式**：Sidecar 未就绪业务先起（启动期 503）；iptables 规则冲突（流量绕过代理 -> 遥测丢失）；出站拦截把外部 API 也劫持了（要配出站白名单 ALLOW 外部地址段）。
 *   - **资源与延迟的账**：每跳 Sidecar 增加 ~0.3~1ms 与一次本地环回拷贝；1000 个 Pod = 1000 份 Sidecar 资源（内存 50MB 级）——**延迟与资源开销要进容量规划**（47-06 细讲 sidecarless）。
 *   - 易错点：出站流量默认 BLOCK 后业务调第三方 API 全挂（配 outboundTrafficPolicy）；Sidecar 与业务容器启动顺序错（启动竞态 503）；把业务健康检查端口也被劫持（探针走不通 -> Pod 被反复重启）；Envoy 配置下发的热更新竞态（策略短暂不一致）。
 * ============================================================
 */

package com.heima.ch47;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class SidecarDataplane {
    static void interception() {
        System.out.println("【一次调用的完整路径（带 Sidecar）】");
        List<String> path = List.of(
                "1) 业务容器 A 发起 HTTP -> 目标 order:8080",
                "2) iptables OUTPUT 链劫持 -> 重定向到 Sidecar A 的 15001 端口",
                "3) Sidecar A 查路由：service=order, subset=v2, 重试=2, 超时=200ms",
                "4) mTLS 加密 -> 发往 Sidecar B（目标 Pod）",
                "5) Sidecar B 解密 -> 转发到业务容器 B 的 8080",
                "6) 响应原路返回；两端遥测上报");
        for (String s : path) {
            System.out.println("  " + s);
        }
        System.out.println("  业务代码视角：就像直连 —— 治理全部透明发生");
        System.out.println();
    }

    static void startupRace() {
        System.out.println("【启动竞态：503 的来源与治理】");
        List<String> bad = List.of(
                "Pod 启动：业务容器 3s 就绪，Sidecar 8s 才就绪",
                "就绪探针通过 -> Pod 接流量 -> 流量进 Sidecar（还没配好路由）-> 503");
        for (String s : bad) {
            System.out.println("  " + s);
        }
        List<String> fix = List.of(
                "holdApplicationUntilProxyStarts: true（Sidecar 先起）",
                "就绪探针改为检查“代理链路”而不是业务端口",
                "发布期用 preStop 保证 Sidecar 后停");
        for (String s : fix) {
            System.out.println("  治理：" + s);
        }
        System.out.println("  现象：每次滚动更新开始 30 秒内 5xx 尖刺 —— 典型启动竞态");
        System.out.println();
    }

    static void outboundPolicy() {
        System.out.println("【出站策略：第三方 API 被劫持事故】");
        String[][] modes = {
                {"ALLOW_ANY", "未声明的外部地址直连放行", "宽松，易漏管"},
                {"REGISTRY_ONLY", "只放行网格内服务", "安全，但第三方 API 全断"},
        };
        for (String[] m : modes) {
            System.out.printf("  %-14s %-26s %s%n", m[0], m[1], m[2]);
        }
        System.out.println("  事故：切到 REGISTRY_ONLY 后短信服务不通 -> 配 ServiceEntry 放行白名单");
        System.out.println("  原则：外部依赖显式注册（地址+端口+TLS 策略），默认拒绝");
        System.out.println();
    }

    static void costAccounting() {
        System.out.println("【Sidecar 的资源账】");
        long pods = 500;
        double memPerSidecar = 60, cpuPerSidecar = 0.1;
        System.out.printf("  %d 个 Pod × (内存 %.0fMB + CPU %.1f核) = %.0fGB 内存 / %.0f 核%n",
                pods, memPerSidecar, cpuPerSidecar, pods * memPerSidecar / 1024, pods * cpuPerSidecar);
        double extraLatency = 0.8;
        System.out.printf("  每跳延迟 +%.1fms（进+出各一次代理）%n", extraLatency);
        System.out.println("  结论：Sidecar 成本是网格级的常量开销，进容量规划与 P99 预算");
    }

    public static void main(String[] args) {
        interception();
        startupRace();
        outboundPolicy();
        costAccounting();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在 minikube 里注入 Sidecar，用 tcpdump/日志验证"业务以为直连、实际经过代理"的劫持路径。
 * 2. 复现启动竞态 503：关闭 holdApplicationUntilProxyStarts 观察发布期 5xx 尖刺，然后修复并对比。
 * 3. 思考题：为什么"业务线程只发起一次调用"是治理下沉的实质收益？结合 44-08 超时预算分析重试位置对线程池的影响。
 * ============================================================
 */
