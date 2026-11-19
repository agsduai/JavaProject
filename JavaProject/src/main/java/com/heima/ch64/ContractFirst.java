/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：64-06
 * 课名：契约优先与 OpenAPI
 * ------------------------------------------------------------
 * 知识点：
 *   - **契约优先（Contract-First）**：先写 **OpenAPI 规范**（YAML/JSON），再生成服务端骨架与客户端 SDK——**契约为唯一事实源**，代码与文档不漂移（46-08 数据契约的 API 版）。
 *   - **OpenAPI 的核心内容**：路径与操作、请求/响应 schema、参数、错误码、鉴权方式、示例——**它同时是文档、测试依据、SDK 生成源、Mock 服务源**。
 *   - **契约的三种用途**：**生成代码**（服务端/客户端骨架）、**生成 Mock**（前端在服务未上线时联调）、**契约测试**（验证实现是否符合契约，64-05 的保障）。
 *   - **契约评审（API Review）**：新增/变更接口先改契约并评审（命名/错误码/分页/幂等/权限），**通过后再写实现**——**与 49-11 建模评审同构：先评审设计再写代码**。
 *   - **契约的治理**：契约进 Git（版本化）、CI 校验（lint + 向后兼容检查 64-05）、发布后自动更新开发者门户（64-09）。
 *   - 易错点：先写代码后补文档（必然漂移）；契约里没有错误码（调用方无法处理异常）；示例缺失（集成方靠猜）；契约变更不评审（破坏兼容）。
 * ============================================================
 */

package com.heima.ch64;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ContractFirst {
    record ApiSpec(String path, String method, String summary, List<String> params,
                   List<String> responses, List<String> auth) {
    }

    /** 契约 lint：检查必备要素 */
    static List<String> lint(ApiSpec spec) {
        List<String> issues = new ArrayList<>();
        if (spec.summary() == null || spec.summary().isBlank()) {
            issues.add("缺少接口说明（summary）");
        }
        if (spec.responses().stream().noneMatch(r -> r.startsWith("200"))) {
            issues.add("缺少成功响应定义");
        }
        if (spec.responses().stream().noneMatch(r -> r.startsWith("4"))) {
            issues.add("缺少错误响应定义（调用方无法处理异常）");
        }
        if (spec.auth().isEmpty()) {
            issues.add("未声明鉴权方式");
        }
        if (spec.params().stream().noneMatch(p -> p.contains("example"))) {
            issues.add("参数缺少示例（集成方只能靠猜）");
        }
        return issues;
    }

    static void generatedArtifacts() {
        System.out.println("【一份契约，五种产出】");
        Map<String, String> outputs = new LinkedHashMap<>();
        outputs.put("服务端骨架", "接口签名 + DTO 类 + 参数校验注解");
        outputs.put("客户端 SDK", "方法 + 类型 + 重试/签名（64-07）");
        outputs.put("Mock 服务", "前端在服务未上线时联调");
        outputs.put("契约测试", "实现 vs 契约的自动比对（CI 门禁）");
        outputs.put("开发者文档", "门户自动渲染（64-09）");
        outputs.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.println();
    }

    static void reviewChecklist() {
        System.out.println("【API 评审清单】");
        List<String> items = List.of(
                "命名：资源复数 + 动作语义（GET /orders/{id}）",
                "分页：游标优先，避免 offset 深翻页（39-03）",
                "幂等：写接口支持幂等键（39/43 章）",
                "错误码：业务码 + 可读消息 + 定位字段（64-09）",
                "鉴权：每个接口声明所需 scope（64-02）",
                "兼容：新增可选/不删不改（64-05）",
                "示例：请求与响应都带示例");
        items.forEach(i -> System.out.println("  - " + i));
    }

    public static void main(String[] args) {
        ApiSpec bad = new ApiSpec("/orders", "POST", "", List.of("body"),
                List.of("200 ok"), List.of());
        ApiSpec good = new ApiSpec("/orders", "POST", "创建订单（支持幂等键）",
                List.of("header Idempotency-Key example=req-9001", "body example={\"amount\":6900}"),
                List.of("201 创建成功", "409 幂等冲突", "429 配额超限"),
                List.of("scope: order:write"));
        System.out.println("【契约 lint：差示例】");
        lint(bad).forEach(i -> System.out.println("  ✗ " + i));
        System.out.println("【契约 lint：好示例】");
        List<String> issues = lint(good);
        System.out.println("  " + (issues.isEmpty() ? "✓ 通过全部检查" : issues));
        System.out.println();
        generatedArtifacts();
        reviewChecklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为一个"查询订单"接口写完整 OpenAPI 契约（含参数示例、三类响应、鉴权 scope），跑一遍 lint 确保零问题。
 * 2. 实现契约测试雏形：把契约定义转成断言（响应字段/状态码/错误码），对 mock 实现做验证。
 * 3. 思考题：为什么"契约先行"能防止文档漂移？从"单一事实源"与"生成物不可手改"两个角度分析。
 * ============================================================
 */
