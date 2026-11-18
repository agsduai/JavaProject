/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：53-04
 * 课名：Dubbo RPC 原理与调用
 * ------------------------------------------------------------
 * 知识点：
 *   - **Dubbo 调用的核心分层**：代理层（50 章 Mapper 代理同款：接口 -> Invoker）-> 集群容错（Failover 重试其他实例/Failfast 不重试/Failsafe 忽略——**按语义选**）-> 负载均衡（Random/LeastActive/一致性哈希——48-08 水位路由思想）-> 协议层（默认 dubbo 协议：Netty 长连接 + 异步）。
 *   - **RPC 与 REST 的取舍**：Dubbo（TCP 长连接、二进制序列化 hessian2、内网微服务间高频调用——性能与治理好）；REST/Feign（HTTP、通用性强、跨语言/对外友好）。**内网高性能用 Dubbo，对外或跨语言用 REST**——常见混合：对外 REST 网关 + 内网 Dubbo。
 *   - **泛化调用与泛化实现**：泛化调用（无接口依赖也能调，Map 传参）用于网关与测试平台；**泛化是网关转发 RPC 的标准解**（44-08 的 RPC 版）。
 *   - **序列化的安全坑**：hessian2 反序列化漏洞历史（36-08 同源）——**Dubbo 3 建议 Triple 协议（gRPC 兼容）**；不要把 Dubbo 端口暴露公网（注册中心里 IP 可见）。
 *   - **参数回调与隐式传参**：RpcContext 传递 trace_id/租户上下文（42 章链路传播的 Dubbo 版）；**隐式传参要显式清理**（线程复用残留是事故源）。
 *   - 易错点：Failover 重试非幂等接口（重复下单）；超时只在 provider 配（consumer 侧不配，被 provider 慢拖死）；序列化对象过大（每次传输几 MB）；注册中心挂了 Dubbo 集群仍可用（本地缓存订阅列表）——不了解这个特性会误判故障。
 * ============================================================
 */

package com.heima.ch53;

import java.lang.reflect.Proxy;
import java.util.Random;

// ==================== 示例代码 ====================

public class DubboRpc {
    interface StockService {
        String deduct(long sku);
    }

    /** Failover：失败换实例重试（幂等语义才可用） */
    static String invokeFailover(String[] instances, java.util.function.Function<String, String> call) {
        Random r = new Random();
        for (int i = 0; i < instances.length; i++) {
            String inst = instances[r.nextInt(instances.length)];
            try {
                return call.apply(inst);
            } catch (RuntimeException e) {
                System.out.printf("  实例 %s 失败：%s -> Failover 换下一实例%n", inst, e.getMessage());
            }
        }
        throw new IllegalStateException("全部实例失败");
    }

    public static void main(String[] args) {
        StockService stock = (StockService) Proxy.newProxyInstance(
                StockService.class.getClassLoader(), new Class<?>[]{StockService.class},
                (proxy, method, args1) -> invokeFailover(
                        new String[]{"inst-1", "inst-2", "inst-3"},
                        inst -> {
                            if (inst.equals("inst-1")) {
                                throw new RuntimeException("连接拒绝");
                            }
                            return "deducted-on-" + inst;
                        }));
        System.out.println("  结果：" + stock.deduct(1001L));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 Dubbo 模拟（代理 + Failover/Failfast 两策略 + LeastActive 负载均衡），对比非幂等接口用 Failover 的重复调用风险。
 * 2. 实现 RpcContext 隐式传参（trace_id 传递 + finally 清理），验证线程复用残留被清理。
 * 3. 思考题：为什么"注册中心挂了 Dubbo 集群仍可用"？从本地订阅缓存与注册中心职责（发现 vs 通信）分析，并说明这如何影响故障判断。
 * ============================================================
 */
