/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：64-09
 * 课名：错误码与文档体系
 * ------------------------------------------------------------
 * 知识点：
 *   - **错误码是开放 API 的"契约中的契约"**：调用方按码分支处理——**码必须稳定、唯一、可分类**（`AUTH_401_INVALID_SIGN`、`QUOTA_429_EXCEEDED`、`BIZ_400_ORDER_NOT_FOUND`）。
 *   - **错误响应的标准结构**：`code`（机器可读）+ `message`（人可读，可展示给终端用户）+ `requestId`（排查用）+ **`hint`/`field`（可行动信息，64-07）**——**不要只返回 "error"**。
 *   - **错误码的分层**：**协议层**（HTTP 状态码：401/403/404/429/5xx）、**平台层**（鉴权/配额/签名）、**业务层**（订单不存在/库存不足）——**调用方先看 HTTP 码，再看业务码**（64-08 的鉴权链对应）。
 *   - **文档体系的三件套**：**接口参考**（OpenAPI 渲染，64-06）、**指南**（Quick Start/鉴权/错误处理/最佳实践）、**变更日志**（每个版本的破坏性与新特性，64-05）。
 *   - **文档质量的判据**：**照着文档能独立跑通**（含鉴权、含错误处理、含重试示例）——**文档里的每个示例都要被 CI 跑过**（否则很快过期）。
 *   - 易错点：错误码随版本变化（调用方逻辑崩）；同一个含义多个码（无法统一处理）；message 直接暴露内部堆栈（36 章）；文档示例未验证（照抄报错）。
 * ============================================================
 */

package com.heima.ch64;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ErrorCodeSystem {
    record ErrorCode(String code, int httpStatus, String message, String hint,
                     String category) {
    }

    static final Map<String, ErrorCode> REGISTRY = new LinkedHashMap<>();

    static void register() {
        REGISTRY.put("AUTH.MISSING_SIGNATURE", new ErrorCode("AUTH.MISSING_SIGNATURE", 401,
                "缺少签名", "在 Header 中提供 X-Signature", "平台"));
        REGISTRY.put("AUTH.INVALID_SIGNATURE", new ErrorCode("AUTH.INVALID_SIGNATURE", 401,
                "签名校验失败", "检查参数是否字典序排序、时间戳是否在 5 分钟内", "平台"));
        REGISTRY.put("AUTH.SCOPE_DENIED", new ErrorCode("AUTH.SCOPE_DENIED", 403,
                "权限不足", "申请对应 scope 或联系平台开通", "平台"));
        REGISTRY.put("QUOTA.EXCEEDED", new ErrorCode("QUOTA.EXCEEDED", 429,
                "调用配额已耗尽", "次 00:00 重置，或申请提额", "平台"));
        REGISTRY.put("BIZ.ORDER_NOT_FOUND", new ErrorCode("BIZ.ORDER_NOT_FOUND", 404,
                "订单不存在", "确认 orderId 是否正确或是否属于当前应用", "业务"));
        REGISTRY.put("SYS.INTERNAL", new ErrorCode("SYS.INTERNAL", 500,
                "服务暂时不可用", "请稍后重试并携带 requestId 联系支持", "系统"));
    }

    /** 标准错误响应 */
    static String render(String code, String requestId) {
        ErrorCode ec = REGISTRY.get(code);
        if (ec == null) {
            return "{\"code\":\"SYS.UNKNOWN\",\"message\":\"未知错误\",\"requestId\":\""
                    + requestId + "\"}";
        }
        return String.format(
                "{\"code\":\"%s\",\"message\":\"%s\",\"hint\":\"%s\",\"requestId\":\"%s\"}",
                ec.code(), ec.message(), ec.hint(), requestId);
    }

    static void docKit() {
        System.out.println("【文档三件套】");
        Map<String, String> kit = new LinkedHashMap<>();
        kit.put("接口参考", "OpenAPI 渲染，含参数/示例/错误码（64-06）");
        kit.put("指南", "Quick Start + 鉴权 + 错误处理 + 重试 + 最佳实践");
        kit.put("变更日志", "每版本的新增/废弃/破坏性变更（64-05）");
        kit.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println();
        System.out.println("【文档验收判据】");
        List<String> checks = List.of(
                "照着文档能从 0 跑通第一个成功调用",
                "每个错误码都能查到原因与处理建议",
                "示例代码被 CI 真实执行（防过期）",
                "鉴权与配额规则有专门章节");
        checks.forEach(c -> System.out.println("  [ ] " + c));
    }

    public static void main(String[] args) {
        register();
        System.out.println("【错误码注册表】");
        REGISTRY.values().forEach(ec -> System.out.printf("  %-22s %3d %-8s %s%n",
                ec.code(), ec.httpStatus(), ec.category(), ec.message()));
        System.out.println();
        System.out.println("【标准错误响应】");
        System.out.println("  " + render("AUTH.INVALID_SIGNATURE", "req-7f3a"));
        System.out.println("  " + render("BIZ.ORDER_NOT_FOUND", "req-8b21"));
        System.out.println("  " + render("NOT.REGISTERED", "req-9c02"));
        System.out.println();
        docKit();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 设计一套错误码规范（分层 + 命名 + HTTP 码映射），注册 10 个错误码并实现标准响应渲染。
 * 2. 写一份"错误处理指南"：调用方应如何处理 401/403/429/5xx/业务错误，各给一段伪代码。
 * 3. 思考题：为什么错误码不能随版本变化？从"调用方按码分支"与"兼容性成本"分析（64-05 的延伸）。
 * ============================================================
 */
