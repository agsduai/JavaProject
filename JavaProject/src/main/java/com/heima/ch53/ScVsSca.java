/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：53-10
 * 课名：Spring Cloud vs Spring Cloud Alibaba 对照
 * ------------------------------------------------------------
 * 知识点：
 *   - **两套栈的组件对照表**（29 章抽象的两侧实现）：Eureka/ZK/Consul vs Nacos；Hystrix/Resilience4j vs Sentinel；Ribbon/LoadBalancer vs Dubbo LB；Zuul/Gateway vs SCG；Config vs Nacos Config；Feign vs Dubbo/Feign——**接口语义相同，实现与运维特性不同**。
 *   - **运维特性差异**：Nacos 一体化（注册+配置一个控制台）vs 分离组件（Eureka+Config+Bus 组合）；Sentinel 控制台实时规则 vs Resilience4j 代码级；SkyWalking vs Zipkin+Grafana 组合。**运维集成度是 SCA 的显著优势**（51-10 成本的运维税更低）。
 *   - **迁移的真实成本**：SC -> SCA：注册中心替换（客户端依赖+注解）+ 熔断替换 + 网关替换——**接口抽象层做得好则成本低**（49-04 端口适配器：业务依赖抽象接口，实现可换）；没有抽象层的系统迁移成本 = 重构。
 *   - **选型建议**：国内业务 + 团队熟悉阿里生态 + 需要 Nacos/Sentinel 一体化 -> SCA；多云/出海/强 OTel 栈 -> 通用 SC + 自选组件；**两者混用要谨慎**（同一服务注册两套注册中心的治理混乱）。
 *   - 易错点：混用两套注册中心；把 SCA 当"必须的"（29 章抽象层 + 任意实现都行）；对照表死记硬背（理解抽象接口即可推演）。
 * ============================================================
 */

package com.heima.ch53;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ScVsSca {
    record Pair(String concern, String sc, String sca, String diffNote) {
    }

    static void compare() {
        System.out.println("【组件对照：同一抽象，两侧实现】");
        List<Pair> pairs = List.of(
                new Pair("注册发现", "Eureka/Consul", "Nacos", "Nacos 一体化注册+配置+保护阈值"),
                new Pair("配置", "Config+Bus", "Nacos Config", "Nacos 长轮询免 Bus"),
                new Pair("熔断", "Resilience4j", "Sentinel", "Sentinel 控制台+热点+持久化"),
                new Pair("网关", "SC Gateway", "SC Gateway", "相同；SCG 常与 Sentinel 联动"),
                new Pair("RPC", "OpenFeign", "Dubbo/Feign", "Dubbo 长连接性能+泛化调用"),
                new Pair("追踪", "Zipkin/OTel", "SkyWalking", "SkyWalking 零侵入+拓扑+剖析"));
        for (Pair p : pairs) {
            System.out.printf("  %-8s SC=%-16s SCA=%-14s 差异：%s%n",
                    p.concern(), p.sc(), p.sca(), p.diffNote());
        }
        System.out.println();
        System.out.println("  结论：抽象接口（29 章）掌握后，两侧实现可互换、可混选、可迁移");
    }

    public static void main(String[] args) {
        compare();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为六个关注点各写一段"切换实现"的迁移清单（代码改动面/配置改动面/风险）。
 * 2. 检查你的项目是否存在抽象层：把熔断接口抽象出来（49-04），验证 Resilience4j 与模拟 Sentinel 实现可互换。
 * 3. 思考题：为什么"混用两套注册中心"是治理灾难？从服务发现的唯一事实源与排障路径分析。
 * ============================================================
 */
