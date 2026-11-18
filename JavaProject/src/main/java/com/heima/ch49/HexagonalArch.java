/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：49-04
 * 课名：六边形架构与端口适配器
 * ------------------------------------------------------------
 * 知识点：
 *   - **六边形架构（端口与适配器）的依赖规则**：**领域层在最内圈，不依赖任何外圈**；应用层编排用例；外圈的适配器（Web 控制器、DB 仓储、MQ 生产者）实现领域定义的"端口"（接口）。**依赖倒置（11 章 DIP 原则）的架构级应用**：不是领域依赖 DB，是 DB 适配器实现领域接口。
 *   - **端口的两种方向**：**驱动侧端口**（左：用例接口，如 `PlaceOrderUseCase`，Web 控制器调用它）与**被驱动侧端口**（右：领域需要的出口，如 `OrderRepository`、`PaymentGateway`，由基础设施适配器实现）。**领域定义接口，外部实现接口**——业务可测试性（纯内存 Mock）与可替换性（换 DB 不改领域）由此而来。
 *   - **与三层架构的对照**：传统三层 Controller->Service->Dao（领域逻辑渗进 Service，Service 依赖 Dao 具体实现）；六边形 Controller->Application(端口)->Domain<-Adapter(实现端口)。**箭头方向相反**——这是本质差异不是目录差异。
 *   - **收益的验证方式**：领域模块 `mvn compile` 不依赖 Spring/JDBC 任何框架 jar（**无框架依赖测试**）；换存储时领域零改动；单元测试不启动容器。
 *   - **何时值得**：核心域、规则复杂、生命周期长（三年以上维护）的系统；一次性活动页/CRUD 用标准三层更省。**架构也是成本决策**（49-01 的复杂度投资论）。
 *   - 易错点：六边形只学目录不学依赖（领域层 import MyBatis，白搭）；端口粒度错（一个端口一个方法爆炸，按用例聚类）；为六边形而六边形（简单模块硬拆四层）；领域对象直接当 DTO 返回 Web（聚合边界泄漏到 API）。
 * ============================================================
 */

package com.heima.ch49;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class HexagonalArch {
    /** 被驱动侧端口：领域定义，基础设施实现 */
    interface OrderRepository {
        void save(String orderId, int amountCents, String status);
    }

    /** 领域服务（纯业务，零框架依赖） */
    static class OrderDomainService {
        private final OrderRepository repo;      // 依赖接口，不依赖实现

        OrderDomainService(OrderRepository repo) {
            this.repo = repo;
        }

        String placeOrder(String orderId, int amountCents) {
            if (amountCents <= 0) {
                throw new IllegalArgumentException("金额必须为正");
            }
            String status = "PLACED";
            repo.save(orderId, amountCents, status);
            return status;
        }
    }

    /** 适配器 1：内存实现（单元测试用） */
    static class InMemoryOrderRepository implements OrderRepository {
        final Map<String, String> store = new LinkedHashMap<>();

        public void save(String orderId, int amountCents, String status) {
            store.put(orderId, amountCents + ":" + status);
        }
    }

    /** 适配器 2：模拟 MyBatis 实现（生产用，领域零感知） */
    static class MybatisOrderRepository implements OrderRepository {
        public void save(String orderId, int amountCents, String status) {
            System.out.println("  [MyBatis] INSERT INTO orders ... (" + orderId + ")");
        }
    }

    static void dependencyDemo() {
        System.out.println("【依赖倒置：领域定义端口，适配器实现】");
        OrderDomainService svc1 = new OrderDomainService(new InMemoryOrderRepository());
        System.out.println("  单测（内存适配器，无框架）：" + svc1.placeOrder("9001", 6900));
        OrderDomainService svc2 = new OrderDomainService(new MybatisOrderRepository());
        System.out.println("  生产（MyBatis 适配器）：" + svc2.placeOrder("9002", 9900));
        System.out.println("  领域代码两处完全相同 —— 换存储领域零改动");
        System.out.println();
    }

    static void hexagonMap() {
        System.out.println("【六边形的端口清单（订单上下文）】");
        String[][] ports = {
                {"驱动侧", "PlaceOrderUseCase", "Web 控制器 / MQ 监听器 调用"},
                {"驱动侧", "CancelOrderUseCase", "客服后台调用"},
                {"被驱动侧", "OrderRepository", "JPA/MyBatis 适配器实现"},
                {"被驱动侧", "PaymentGateway", "渠道适配器实现（51-02）"},
                {"被驱动侧", "EventPublisher", "MQ 适配器实现（outbox）"},
        };
        for (String[] p : ports) {
            System.out.printf("  %-6s %-20s %s%n", p[0], p[1], p[2]);
        }
        System.out.println();
    }

    static void testabilityProof() {
        System.out.println("【收益验证：无框架依赖测试】");
        List<String> checks = List.of(
                "mvn dependency:tree | grep -v spring —— domain 模块零框架依赖",
                "聚合单测纯内存运行 < 50ms（不启动 Spring 上下文）",
                "换存储演练：Mock 仓储 -> 真仓储，领域代码 diff 为 0",
                "用例编排可 Mock：应用层测试只 Mock 端口");
        for (String c : checks) {
            System.out.println("  [ ] " + c);
        }
        System.out.println("  达标即“真六边形”，只有目录结构而无依赖规则即“假六边形”");
    }

    public static void main(String[] args) {
        dependencyDemo();
        hexagonMap();
        testabilityProof();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把你项目的某个 Service 改造为端口适配器形态：领域接口 + 两个实现（内存/真实），跑通"无框架依赖单测"。
 * 2. 列出你模块的完整端口清单（驱动侧/被驱动侧），检查是否存在"领域 import 框架"的违例并用依赖树验证。
 * 3. 思考题：为什么"领域不依赖框架"是可测试性的根源？从 Mock 成本与容器启动开销的角度量化对比。
 * ============================================================
 */
