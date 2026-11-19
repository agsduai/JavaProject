/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：64-05
 * 课名：API 版本演进与兼容策略
 * ------------------------------------------------------------
 * 知识点：
 *   - **版本策略的核心是"给集成方留出升级时间"**：**URL 版本**（`/v1/orders`，最直观）、**Header 版本**（`Accept-Version: 2`，URL 稳定）、**参数版本**（`?version=2`，最不推荐）——**主流是 URL 版本**（清晰、易缓存、易观测）。
 *   - **兼容的判定标准**：**新增可选字段/新增接口 = 兼容**（调用方可忽略）；**删字段/改类型/改语义/改必填 = 破坏性**（必须新版本）；**改错误码含义**也是破坏性（调用方按码分支）。
 *   - **三种演进手段**：**并行支持**（v1/v2 同时跑，v1 标记废弃并给下线日期）、**适配层**（v2 内部转 v1，减少维护成本）、**灰度迁移**（新版本先小流量，41 章）。
 *   - **废弃（Deprecation）的规范**：`Deprecation`/`Sunset` 响应头 + 文档标注 + **提前 6 个月通知** + **调用量监控**（还有谁在调）——**"静默下线"是开放平台的大忌**。
 *   - **契约测试是兼容性的保障**（64-06）：每次发布对"契约快照"做 diff，**破坏性变更在 CI 阶段就被拦下**（41-03 门禁）。
 *   - 易错点：改字段语义但不升版本（调用方逻辑静默出错）；废弃无通知（集成方线上崩）；版本数量无限膨胀（维护成本线性涨）；新版本文档缺失（集成方只能看代码）。
 * ============================================================
 */

package com.heima.ch64;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ApiVersioning {
    record Field(String name, String type, boolean required) {
    }

    record ApiContract(String version, List<Field> fields, List<String> endpoints,
                       String status) {
    }

    enum ChangeType {
        ADD_OPTIONAL, ADD_ENDPOINT, REMOVE_FIELD, CHANGE_TYPE, CHANGE_REQUIRED, CHANGE_SEMANTIC
    }

    /** 变更分类：是否破坏兼容 */
    static boolean isBreaking(ChangeType type) {
        return switch (type) {
            case ADD_OPTIONAL, ADD_ENDPOINT -> false;
            case REMOVE_FIELD, CHANGE_TYPE, CHANGE_REQUIRED, CHANGE_SEMANTIC -> true;
        };
    }

    static void diff(ApiContract v1, ApiContract v2, List<ChangeType> changes) {
        System.out.println("【契约 diff】v" + v1.version() + " -> v" + v2.version());
        List<String> breaking = new ArrayList<>();
        for (ChangeType c : changes) {
            System.out.printf("  %-16s -> %s%n", c, isBreaking(c) ? "破坏性" : "兼容");
            if (isBreaking(c)) {
                breaking.add(c.name());
            }
        }
        if (breaking.isEmpty()) {
            System.out.println("  结论：可直接发布同一大版本（小版本升级）");
        } else {
            System.out.println("  结论：必须新开大版本 + 并行支持 + 废弃通知：" + breaking);
        }
        System.out.println();
    }

    static void deprecationPolicy() {
        System.out.println("【废弃规范】");
        Map<String, String> policy = new LinkedHashMap<>();
        policy.put("响应头", "Deprecation: true / Sunset: 2027-06-01");
        policy.put("文档", "接口列表标记 deprecated，给出迁移指南");
        policy.put("通知", "提前 6 个月 + 每月提醒，直到调用量归零");
        policy.put("监控", "按 appId 统计 v1 调用量，联系尾部集成方");
        policy.put("下线", "调用量归零 + 公告期结束 -> 关闭并保留 30 天兜底");
        policy.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println();
    }

    public static void main(String[] args) {
        ApiContract v1 = new ApiContract("1", List.of(
                new Field("orderId", "string", true),
                new Field("amount", "int", true),
                new Field("status", "string", true)), List.of("/v1/orders"), "deprecated");
        ApiContract v2 = new ApiContract("2", List.of(
                new Field("orderId", "string", true),
                new Field("amount", "long", true),
                new Field("status", "enum", true),
                new Field("channel", "string", false)), List.of("/v2/orders"), "stable");
        System.out.println("  v1 字段：" + v1.fields());
        System.out.println("  v2 字段：" + v2.fields());
        System.out.println();
        diff(v1, v2, List.of(ChangeType.CHANGE_TYPE, ChangeType.CHANGE_REQUIRED,
                ChangeType.ADD_OPTIONAL, ChangeType.ADD_ENDPOINT));
        deprecationPolicy();
        System.out.println("  铁律：新增字段可兼容；删/改/改语义一律新版本");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的 API 制定版本策略：URL 版本 + 契约快照 + 破坏性变更判定表，并写一份废弃公告模板。
 * 2. 实现契约 diff 工具：对比两版 OpenAPI 字段（新增/删除/类型/必填），自动输出"兼容/破坏"结论。
 * 3. 思考题：为什么"改字段语义"也算破坏性变更？构造"amount 从分改为元"导致集成方金额放大 100 倍的用例。
 * ============================================================
 */
