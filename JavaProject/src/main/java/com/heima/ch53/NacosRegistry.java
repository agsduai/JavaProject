/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：53-02
 * 课名：Nacos 注册中心实战
 * ------------------------------------------------------------
 * 知识点：
 *   - **Nacos naming 的核心行为**（29 章原理的 Nacos 版）：服务注册（PUT /nacos/v1/ns/instance）、心跳续约（默认 5s，15s 未收到标记不健康，30s 剔除——**比 ZK 宽松，容忍网络抖动**）、服务发现（客户端拉取 + UDP 推送变更）、保护阈值（健康实例占比低于阈值时返回全部实例——**防止雪崩，牺牲准确性**）。
 *   - **临时实例 vs 持久实例**：临时实例（ephemeral=true，默认）——客户端心跳维持，掉线自动删（适合服务发现）；持久实例——服务端存储，健康检查由服务端主动探（适合数据库/Redis 等基础设施登记）。**选错的典型事故**：把 DB 实例注册成临时实例，网络抖动被剔除导致配置漂移告警。
 *   - **集群与一致性**：Nacos 集群用 Raft（45 章正是它的底座）维护持久数据，临时实例走 **Distro 协议**（AP 倾向：异步复制，分区时各自可用）——**注册中心选 AP**（29 章结论的组件级印证：宁可列表短暂不全，不可全部停服）。
 *   - **命名空间与分组**：namespace 隔离环境（dev/test/prod 各一个），group 隔离业务域——**环境隔离用 namespace 而不是 group**（group 是逻辑分域，跨 group 不互通）。混淆两者的后果：test 的服务发现到 prod 实例。
 *   - **生产化动作**：3 节点集群（45-10 共识规模）、客户端心跳参数调优（GC 停顿 >5s 的服务调大超时）、保护阈值配置、注册中心自身监控（53-01 版本矩阵 + 42 章四信号）。
 *   - 易错点：注册中心单点（全公司服务发现挂）；临时/持久实例混用错场景；namespace 与 group 混淆；保护阈值不理解就关掉（雪崩防线消失）。
 * ============================================================
 */

package com.heima.ch53;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class NacosRegistry {
    record Instance(String service, String ip, boolean healthy, boolean ephemeral) {
    }

    /** 保护阈值：健康实例不足时返回全部（宁返回可能坏的，不返回空） */
    static List<Instance> discover(List<Instance> all, String service, double protectThreshold) {
        List<Instance> healthy = all.stream()
                .filter(i -> i.service().equals(service) && i.healthy())
                .toList();
        List<Instance> allOfService = all.stream()
                .filter(i -> i.service().equals(service))
                .toList();
        double healthyRatio = allOfService.isEmpty() ? 0
                : (double) healthy.size() / allOfService.size();
        if (healthyRatio < protectThreshold && !allOfService.isEmpty()) {
            System.out.printf("  健康占比 %.2f < 阈值 %.2f -> 保护模式：返回全部 %d 实例%n",
                    healthyRatio, protectThreshold, allOfService.size());
            return allOfService;
        }
        System.out.printf("  正常返回健康实例 %d 个%n", healthy.size());
        return healthy;
    }

    public static void main(String[] args) {
        List<Instance> registry = new ArrayList<>(List.of(
                new Instance("order", "10.0.0.1", true, true),
                new Instance("order", "10.0.0.2", false, true),
                new Instance("order", "10.0.0.3", false, true)));
        discover(registry, "order", 0.5);
        System.out.println();
        System.out.println("  保护模式的意义：宁可把流量给可能恢复的实例，也不让上游无服务可用");
        System.out.println("  对照：关闭保护 -> 返回 0 个实例 -> 上游全线 503（雪崩起点）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 Nacos 模拟器（注册/心跳/剔除/保护阈值/订阅回调），复现"1 实例假死 -> 保护模式 -> 恢复"全周期。
 * 2. 对比临时实例与持久实例的行为差异（心跳维持 vs 服务端探测），各给一个适用场景。
 * 3. 思考题：为什么注册中心选 AP 而配置中心选 CP（53-03）？从"服务列表短暂不全"与"配置分发错误"的后果差异论证。
 * ============================================================
 */
