/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：71-10
 * 课名：API 与集成
 * ------------------------------------------------------------
 * 知识点：
 *   - **低代码平台必须"能被集成"**：开放 API（元数据 CRUD、数据 CRUD、触发流程 64 章）、Webhook（数据变更通知）、**与外部系统的连接器**（数据库/HTTP/MQ）——**否则就是数据孤岛**。
 *   - **API 的设计原则**（64 章复用）：**元数据驱动**（`/api/meta/{formId}` 返回字段定义，前端动态渲染）、**通用数据接口**（`/api/data/{formId}` 统一 CRUD，不每个表单一个接口）、**版本化**（64-05）。
 *   - **连接器的抽象**：**入站**（外部数据写入低代码：HTTP 回调/MQ 消费）、**出站**（低代码调用外部：HTTP/DB/消息）——**连接器配置也是元数据**（含鉴权、重试、超时）。
 *   - **与 69/70 章的关系**：开放业务 API 要签名/配额（69-03/04）、多语言错误码（70-09）——**低代码平台的 API 也要遵守开放平台规范**。
 *   - 易错点：每个表单写一套接口（维护爆炸）；连接器无重试（外部抖动就丢数据）；Webhook 无序（接收方处理乱序）；无鉴权（内部平台裸奔）。
 * ============================================================
 */

package com.heima.ch71;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class Integration {
    /** 通用元数据 API：返回字段定义 */
    static Map<String, Object> getFormMeta(String formId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("formId", formId);
        meta.put("version", "v2");
        List<Map<String, String>> fields = new ArrayList<>();
        fields.add(Map.of("name", "title", "type", "string", "required", "true"));
        fields.add(Map.of("name", "amount", "type", "decimal", "required", "true"));
        fields.add(Map.of("name", "status", "type", "enum", "required", "false"));
        meta.put("fields", fields);
        return meta;
    }

    /** 通用数据 API：CRUD 走同一套路由（formId 作为参数） */
    static String handleData(String method, String formId, Map<String, String> body) {
        if (!List.of("GET", "POST", "PUT", "DELETE").contains(method)) {
            return "405 不支持的方法";
        }
        if (formId == null || formId.isBlank()) {
            return "400 缺少 formId";
        }
        return switch (method) {
            case "GET" -> "200 查询 " + formId + " 列表（分页）";
            case "POST" -> "201 创建 " + formId + "：" + body;
            case "PUT" -> "200 更新 " + formId;
            default -> "204 删除 " + formId;
        };
    }

    /** Webhook：数据变更通知（带重试与顺序号） */
    record WebhookEvent(String eventId, long seq, String formId, String action,
                        Map<String, String> data) {
    }

    static final List<WebhookEvent> outbox = new ArrayList<>();
    static long seqCounter = 0;

    static void emit(String formId, String action, Map<String, String> data) {
        seqCounter++;
        outbox.add(new WebhookEvent("ev-" + seqCounter, seqCounter, formId, action, data));
        System.out.printf("  发出事件 %s（seq=%d）：%s %s%n", "ev-" + seqCounter,
                seqCounter, action, formId);
    }

    /** 连接器：出站 HTTP 调用的配置化封装 */
    record Connector(String name, String type, String endpoint, int timeoutMs,
                     int maxRetry) {
    }

    static void callConnector(Connector c, boolean simulateFailure) {
        for (int attempt = 1; attempt <= c.maxRetry(); attempt++) {
            if (attempt <= 1 && simulateFailure) {
                System.out.printf("  [%s] 第 %d 次调用失败 -> 退避重试%n", c.name(), attempt);
                continue;
            }
            System.out.printf("  [%s] 第 %d 次调用成功（%s，timeout=%dms）%n",
                    c.name(), attempt, c.endpoint(), c.timeoutMs());
            return;
        }
        System.out.printf("  [%s] 重试耗尽 -> 进死信（%d 次）%n", c.name(), c.maxRetry());
    }

    public static void main(String[] args) {
        System.out.println("【元数据 API】" + getFormMeta("expense"));

        System.out.println();
        System.out.println("【通用数据 API】");
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", "差旅");
        body.put("amount", "1200");
        System.out.println("  " + handleData("GET", "expense", Map.of()));
        System.out.println("  " + handleData("POST", "expense", body));
        System.out.println("  " + handleData("POST", "", body));
        System.out.println("  " + handleData("PATCH", "expense", body));

        System.out.println();
        System.out.println("【Webhook 事件（带 seq 保序）】");
        emit("expense", "CREATED", body);
        emit("expense", "APPROVED", body);
        outbox.forEach(e -> System.out.printf("  待投递 %s seq=%d%n", e.eventId(), e.seq()));

        System.out.println();
        System.out.println("【连接器：超时 + 重试】");
        callConnector(new Connector("erp-sync", "HTTP",
                "https://erp.internal/api/expense", 3000, 3), true);

        System.out.println();
        System.out.println("  规范：通用接口 + 版本化（64-05）+ 签名配额（64-03/04）+ 事件保序（65-05）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现通用数据 API（元数据/数据/事件三类），用 5 个请求验证 200/201/400/405 各分支。
 * 2. 实现连接器重试器（超时 + 指数退避 + 死信），注入失败验证重试与最终死信。
 * 3. 思考题：为什么"每个表单一套接口"是维护灾难？从"接口数量爆炸"与"版本不一致"分析。
 * ============================================================
 */
