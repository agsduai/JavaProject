/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：64-07
 * 课名：SDK 设计与开发者体验
 * ------------------------------------------------------------
 * 知识点：
 *   - **SDK 是开放平台的"第一印象"**：开发者先看 SDK 好不好用，再决定是否接入——**SDK 的质量直接影响生态规模**。
 *   - **SDK 的必备能力**：**类型安全**（方法 + DTO，不传 Map）、**鉴权自动化**（签名/令牌刷新由 SDK 处理，64-02/03）、**重试与退避**（对 429/5xx 自动，40-02）、**超时与连接池**、**错误映射**（统一异常类型，带业务码）、**日志与调试**（可开关的请求日志）。
 *   - **SDK 的分层**：**传输层**（HTTP/签名/重试）-> **API 层**（每个接口一个方法）-> **模型层**（DTO）——**业务方只碰 API 层**；**SDK 必须与契约同源生成**（64-06），避免手写漂移。
 *   - **多语言 SDK 的成本**：契约生成 + 少量手写增强（每个语言）；**一致性测试**（同一请求在 Java/Go SDK 发出相同签名与报文）——**跨语言一致性是 SDK 质量的硬指标**。
 *   - **开发者体验（DX）的细节**：**一条命令跑通示例**（Quick Start）、**沙箱环境**（64-01）、**错误信息可行动**（"signature mismatch: 检查参数排序"而不是"invalid request"）、**升级指南**（64-05）。
 *   - 易错点：SDK 把密钥硬编码进示例（泄露）；重试对非幂等接口默认开启（重复下单）；错误吞掉只返回 null；SDK 版本与服务版本绑定（升级耦合）。
 * ============================================================
 */

package com.heima.ch64;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class SdkDesign {
    record SdkCapability(String name, String why, String failureIfMissing) {
    }

    /** SDK 的错误映射：统一异常 + 可行动信息 */
    static class ApiException extends RuntimeException {
        final int httpStatus;
        final String bizCode;
        final String hint;

        ApiException(int httpStatus, String bizCode, String message, String hint) {
            super(message);
            this.httpStatus = httpStatus;
            this.bizCode = bizCode;
            this.hint = hint;
        }

        String actionable() {
            return "[" + httpStatus + "/" + bizCode + "] " + getMessage() + " -> " + hint;
        }
    }

    /** 重试策略：只对幂等方法与 429/5xx 重试 */
    static boolean shouldRetry(String method, int status, boolean idempotentKeyPresent) {
        boolean retryableStatus = status == 429 || status >= 500;
        boolean safeMethod = List.of("GET", "HEAD", "PUT", "DELETE").contains(method)
                || idempotentKeyPresent;
        return retryableStatus && safeMethod;
    }

    static void capabilities() {
        System.out.println("【SDK 六项必备能力】");
        List<SdkCapability> caps = List.of(
                new SdkCapability("类型安全", "编译期发现错误", "调用方到处传 Map，线上才炸"),
                new SdkCapability("鉴权自动化", "签名/令牌刷新由 SDK 处理", "每个调用方自己实现签名，错法五花八门"),
                new SdkCapability("重试退避", "429/5xx 自动退避", "调用方无脑重试打爆平台（44-08）"),
                new SdkCapability("超时与连接池", "避免连接泄漏", "生产环境句柄耗尽"),
                new SdkCapability("错误映射", "统一异常 + 业务码", "调用方只能看 HTTP 状态猜原因"),
                new SdkCapability("可开关日志", "联调可开、生产默认关", "日志泄露密钥或淹没磁盘"));
        caps.forEach(c -> System.out.printf("  %-10s %-24s 缺失后果：%s%n",
                c.name(), c.why(), c.failureIfMissing()));
        System.out.println();
    }

    static void dxChecklist() {
        System.out.println("【开发者体验清单】");
        Map<String, String> dx = new LinkedHashMap<>();
        dx.put("Quick Start", "5 分钟内跑通第一个成功调用（含沙箱密钥）");
        dx.put("沙箱", "与生产隔离的环境，可随意试错");
        dx.put("错误信息", "可行动：指出哪个参数/字段/如何修复");
        dx.put("示例", "每个接口都有 curl + SDK 双版本");
        dx.put("升级", "版本变更指南 + 兼容性说明（64-05）");
        dx.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
    }

    public static void main(String[] args) {
        capabilities();
        System.out.println("【错误映射示例】");
        ApiException e = new ApiException(401, "SIGNATURE_MISMATCH",
                "签名校验失败", "检查参数是否按字典序排序、时间戳是否在 5 分钟内");
        System.out.println("  " + e.actionable());
        System.out.println();
        System.out.println("【重试判据】");
        String[][] cases = {
                {"GET", "503", "无", "重试（幂等 + 5xx）"},
                {"POST", "503", "有幂等键", "重试（幂等键保证不重复）"},
                {"POST", "503", "无幂等键", "不重试（可能重复下单）"},
                {"POST", "429", "无幂等键", "不重试（先退避，由调用方决定）"},
                {"GET", "400", "无", "不重试（参数错误重试无意义）"},
        };
        for (String[] c : cases) {
            boolean retry = shouldRetry(c[0], Integer.parseInt(c[1]),
                    c[2].contains("有"));
            System.out.printf("  %-4s %-3s %-10s -> %s（%s）%n", c[0], c[1], c[2],
                    retry ? "重试" : "不重试", c[3]);
        }
        System.out.println();
        dxChecklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为一个虚构的订单查询 API 写 SDK 设计文档（分层、六项能力、错误映射表、重试判据）。
 * 2. 实现 SDK 重试策略：按方法/状态码/幂等键判定是否重试，用 8 组用例验证，并说明"POST 无幂等键不重试"的理由。
 * 3. 思考题：为什么"SDK 与契约同源生成"能降低维护成本？从多语言一致性与手写漂移两个角度分析。
 * ============================================================
 */
