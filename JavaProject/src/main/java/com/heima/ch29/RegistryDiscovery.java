/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-03
 * 课名：注册中心与服务发现
 * ------------------------------------------------------------
 * 知识点：
 *   - 微服务实例的 IP 和端口是**动态的**（容器重建、弹性扩容都会变），硬编码地址必然失效。注册中心解决"服务 B 现在有哪些可用实例"。
 *   - 流程：服务启动 → 向注册中心**注册**（服务名 + IP + 端口 + 元数据）→ 定期**心跳**续约 → 消费者**拉取/订阅**实例列表 → 本地缓存 + 变更推送。
 *   - 健康检查两种模式：**客户端心跳**（服务自己上报，简单但可能"假活"）与**服务端探测**（注册中心主动探活，更准但有延迟）。
 *   - 主流实现：Nacos（注册 + 配置二合一，国内主流）、Eureka（AP，自我保护模式）、Consul（CP，强一致）、ZooKeeper（CP，临时节点）。
 *   - CAP 取舍：注册中心通常选 **AP**——网络分区时宁可返回稍旧的实例列表，也不能让整个调用链不可用。短暂的不一致由客户端重试和熔断兜底。
 *   - 易错：消费者每次调用都查一次注册中心（应本地缓存 + 订阅推送）；实例下线后本地缓存不刷新（要靠推送或定时全量拉取）；把注册中心当配置中心用。
 * ============================================================
 */

package com.heima.ch29;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RegistryDiscovery {
    /** 一个服务实例 */
    static class Instance {
        final String host;
        final int port;
        long lastHeartbeat;
        boolean healthy = true;

        Instance(String host, int port, long now) {
            this.host = host;
            this.port = port;
            this.lastHeartbeat = now;
        }

        String addr() {
            return host + ":" + port;
        }
    }

    /** 极简注册中心 */
    static class Registry {
        final Map<String, List<Instance>> services = new HashMap<>();
        static final long TTL = 30_000;

        void register(String service, Instance ins, long now) {
            services.computeIfAbsent(service, k -> new ArrayList<>()).add(ins);
            System.out.println("  注册 " + service + " -> " + ins.addr());
        }

        void heartbeat(String service, String addr, long now) {
            for (Instance i : services.getOrDefault(service, List.of())) {
                if (i.addr().equals(addr)) {
                    i.lastHeartbeat = now;
                }
            }
        }

        /** 剔除超过 TTL 未续约的实例 */
        int evict(long now) {
            int removed = 0;
            for (Map.Entry<String, List<Instance>> e : services.entrySet()) {
                List<Instance> list = e.getValue();
                for (int i = list.size() - 1; i >= 0; i--) {
                    if (now - list.get(i).lastHeartbeat > TTL) {
                        System.out.println("  剔除失联实例 " + e.getKey() + " -> " + list.get(i).addr());
                        list.remove(i);
                        removed++;
                    }
                }
            }
            return removed;
        }

        List<Instance> discover(String service) {
            List<Instance> list = new ArrayList<>();
            for (Instance i : services.getOrDefault(service, List.of())) {
                if (i.healthy) {
                    list.add(i);
                }
            }
            return list;
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();
        long now = 0;
        System.out.println("=== 服务启动时注册 ===");
        registry.register("item-service", new Instance("10.0.0.11", 8081, now), now);
        registry.register("item-service", new Instance("10.0.0.12", 8081, now), now);
        registry.register("item-service", new Instance("10.0.0.13", 8081, now), now);

        System.out.println();
        System.out.println("=== 消费者发现实例（本地缓存，不是每次调用都查）===");
        List<Instance> cached = registry.discover("item-service");
        System.out.println("  本地缓存实例：" + cached.stream().map(Instance::addr).toList());

        System.out.println();
        System.out.println("=== 心跳续约：10.0.0.11 和 12 正常，13 挂掉 ===");
        now += 20_000;
        registry.heartbeat("item-service", "10.0.0.11:8081", now);
        registry.heartbeat("item-service", "10.0.0.12:8081", now);
        registry.evict(now);
        System.out.println("  当前可用：" + registry.discover("item-service").stream().map(Instance::addr).toList());

        System.out.println();
        System.out.println("=== TTL 到期后剔除失联实例 ===");
        now += 20_000;
        registry.heartbeat("item-service", "10.0.0.11:8081", now);
        registry.heartbeat("item-service", "10.0.0.12:8081", now);
        int removed = registry.evict(now);
        System.out.println("  本轮剔除 " + removed + " 个，可用实例："
                + registry.discover("item-service").stream().map(Instance::addr).toList());

        System.out.println();
        System.out.println("=== 扩容：新实例加入，推送变更 ===");
        registry.register("item-service", new Instance("10.0.0.14", 8081, now), now);
        System.out.println("  消费者收到推送，缓存刷新为："
                + registry.discover("item-service").stream().map(Instance::addr).toList());
        System.out.println("  CAP 取舍：注册中心选 AP，分区时宁可给旧列表也不能整体不可用");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Registry` 加一个"实例主动下线（优雅停机）"的方法，对比与心跳超时的区别。
 * 2. 说明为什么消费者要缓存实例列表并订阅变更，而不是每次调用都查注册中心。
 * 3. 对比 Nacos 与 Eureka 在 CAP、健康检查、配置管理上的差异。
 * ============================================================
 */
