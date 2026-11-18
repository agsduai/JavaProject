/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：53-01
 * 课名：阿里生态全景与选位
 * ------------------------------------------------------------
 * 知识点：
 *   - **Spring Cloud Alibaba 是通用 Spring Cloud 规范的阿里实现集**：29 章讲的注册中心/配置中心/网关/熔断是**抽象接口**，阿里栈给出具体落地——Nacos（注册+配置二合一）、Sentinel（限流熔断）、Seata（分布式事务）、Dubbo（RPC）、RocketMQ（消息，28 章已讲语义）。**学过 29 章的原理，本章只学"这套实现怎么用、有什么差异"**。
 *   - **组件选位图**：注册发现=Nacos naming；配置=Nacos config（对比 Apollo/Azure 类）；RPC=Dubbo 或 OpenFeign；限流熔断=Sentinel（对比 Hystrix/Resilience4j）；分布式事务=Seata（对比 39 章本地消息表）；链路=SkyWalking（对比 42 章 OpenTelemetry）。**每个组件都有"通用替代品"**——掌握原理后替换组件是低成本的。
 *   - **版本与兼容矩阵是第一坑**：Spring Boot / Spring Cloud / Spring Cloud Alibaba 三者版本必须按官方矩阵对齐（如 Boot 3.2.x 对应 SC 2023.0.x 对应 SCA 2023.0.x）——**错配的典型症状**：Nacos 注册成功但服务发现为空、配置监听不生效。上线前把矩阵写进 ADR。
 *   - **与自研/开源替代的取舍**：阿里栈的优势是**文档全、社区活跃、国内生产案例多**；代价是**与特定云厂商生态绑定较深**（ACK/ARMS 联动）。多云或出海系统要评估替代组件的迁移成本（49 章演进框架）。
 *   - **本章的模拟策略**：全部组件用纯 JDK 模拟核心行为（Nacos 的心跳注册表、Sentinel 的滑动窗口、Seata 的两阶段、Dubbo 的代理调用）——**机制理解优先于 API 记忆**，API 在真实环境一周即可上手。
 *   - **生产落地清单预览**：注册中心集群化（45 章共识）、配置灰度（41 章思想）、限流规则持久化（Nacos 存储）、事务模式选型（AT vs TCC 51-09）、链路采样（42-08）——**每个组件的"生产化动作"本章逐个讲**。
 *   - 易错点：组件堆砌（每个都上但没用对场景）；版本错配硬扛；把 Sentinel 当配额管理（它是流量防护不是计费）；Seata AT 模式用在高性能核心链路（全局锁的代价）。
 * ============================================================
 */

package com.heima.ch53;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AlibabaLandscape {
    record Component(String name, String duty, String abstraction, String alternative) {
    }

    static void landscape() {
        System.out.println("【阿里栈组件选位图】");
        List<Component> cs = List.of(
                new Component("Nacos", "注册中心 + 配置中心", "DiscoveryClient / Config", "Eureka/ZK/Consul"),
                new Component("Sentinel", "限流/熔断/降级", "CircuitBreaker 接口", "Resilience4j/Hystrix"),
                new Component("Seata", "分布式事务", "PlatformTransactionManager 扩展", "本地消息表/TCC 手写"),
                new Component("Dubbo", "RPC 框架", "服务调用抽象", "OpenFeign/gRPC"),
                new Component("RocketMQ", "消息队列", "Message 抽象", "Kafka/RabbitMQ（28 章）"),
                new Component("SkyWalking", "链路追踪", "Observability API", "OpenTelemetry（42 章）"));
        for (Component c : cs) {
            System.out.printf("  %-10s %-16s 抽象=%-28s 替代=%s%n",
                    c.name(), c.duty(), c.abstraction(), c.alternative());
        }
        System.out.println("  学过原理：换实现是配置级成本；只学 API：换实现是重学");
        System.out.println();
    }

    static void versionMatrix() {
        System.out.println("【版本矩阵：三件套对齐是第一坑】");
        Map<String, String> matrix = new LinkedHashMap<>();
        matrix.put("Spring Boot", "3.2.x");
        matrix.put("Spring Cloud", "2023.0.x");
        matrix.put("Spring Cloud Alibaba", "2023.0.x");
        for (Map.Entry<String, String> e : matrix.entrySet()) {
            System.out.printf("  %-20s %s%n", e.getKey(), e.getValue());
        }
        List<String> symptoms = List.of(
                "错配症状1：Nacos 注册成功但服务发现列表为空",
                "错配症状2：配置监听不回调（@RefreshScope 失效）",
                "错配症状3：Sentinel 注解不生效（切面未注册）");
        for (String s : symptoms) {
            System.out.println("  " + s);
        }
        System.out.println("  处方：版本矩阵进 ADR；升级按官方兼容表三件套同步");
        System.out.println();
    }

    static void simulationStrategy() {
        System.out.println("【本章模拟策略：机制优先】");
        List<String> rules = List.of(
                "Nacos：用内存注册表模拟心跳注册/剔除（45 章共识底座）",
                "Sentinel：滑动窗口 + 令牌桶真实计算（42/44 章思想）",
                "Seata：两阶段提交状态机真跑（45 章基础）",
                "Dubbo：JDK 代理模拟 RPC 调用与负载均衡（50 章基础）",
                "目的：生产环境 API 一周上手，机制不懂则处处是坑");
        for (String r : rules) {
            System.out.println("  " + r);
        }
    }

    public static void main(String[] args) {
        landscape();
        versionMatrix();
        simulationStrategy();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 画出你公司（或假想项目）的阿里栈选位图：每个组件标注"用的什么、替代品是谁、替换成本"。
 * 2. 实地验证版本错配的一个症状：故意用不匹配的 SC 版本起服务，记录现象与诊断过程。
 * 3. 思考题：为什么"学原理 > 学 API"？用 Nacos 与 Eureka 的切换成本做论证（都实现注册中心抽象）。
 * ============================================================
 */
