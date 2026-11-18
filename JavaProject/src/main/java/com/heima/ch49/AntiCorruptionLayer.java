/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：49-07
 * 课名：上下文集成与防腐层
 * ------------------------------------------------------------
 * 知识点：
 *   - **上下文之间的集成模式**：**共享内核**（共用部分模型，耦合强，慎用）、**客户-供应商**（下游适配上游）、**防腐层（ACL，Anti-Corruption Layer）**（在边界上做模型翻译，保护自己的领域不被外部模型污染）、**事件集成**（49-03 的默认选项）。**防腐层是跨上下文集成的标配防线**。
 *   - **防腐层解决什么**：第三方/遗留系统的模型与你的领域模型不一致——**翻译发生在边界适配器**，领域内永远说自己的通用语言。"外部返回 status=A1/B2" vs 你的 OrderStatus 枚举——没有 ACL，外部枚举会渗透进领域代码。
 *   - **遗留系统集成是 ACL 的主战场**：不改老系统（改不动），在边界写翻译层——**绞杀者模式（Strangler Fig）**：新系统逐功能接管，ACL 承接过渡期流量，旧功能逐个"绞杀"下线。
 *   - **ACL 的实现位置**：适配器层（49-04 六边形的外圈）——`PaymentGatewayAdapter` 把渠道返回码翻译为领域事件；**领域层只认识自己的语言**。
 *   - **集成测试的重心**：ACL 的翻译正确性是跨上下文 Bug 高发区——**翻译表（映射矩阵）驱动测试**：外部状态 × 内部状态全覆盖（40-03 few-shot 边界示例同源思想）。
 *   - 易错点：外部模型直接进领域（DTO 直接当聚合用）；ACL 退化成透传（没有真正翻译）；绞杀者改造无流量开关（无法回退旧系统）；翻译表无测试（外部升级一改，静默错译）。
 * ============================================================
 */

package com.heima.ch49;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AntiCorruptionLayer {
    /** 外部遗留系统的模型（不能渗透进领域） */
    static class LegacyPaymentResponse {
        String retCode;      // "00"=成功, "A1"=处理中, "B2"=失败
        String retMsg;
        String amt;          // 单位：分（字符串！）
    }

    /** 领域自己的语言 */
    enum PaymentStatus {
        SUCCESS, PENDING, FAILED
    }

    /** 防腐层：翻译发生在边界 */
    static class PaymentGatewayAdapter {
        record Translation(LegacyPaymentResponse raw, PaymentStatus status, int amountCents) {
        }

        private static final Map<String, PaymentStatus> CODE_MAP = Map.of(
                "00", PaymentStatus.SUCCESS,
                "A1", PaymentStatus.PENDING,
                "B2", PaymentStatus.FAILED);

        Translation translate(LegacyPaymentResponse resp) {
            PaymentStatus status = CODE_MAP.getOrDefault(resp.retCode, PaymentStatus.FAILED);
            int cents;
            try {
                cents = Integer.parseInt(resp.amt);
            } catch (NumberFormatException e) {
                cents = -1;                      // 异常金额 -> FAILED，绝不透传垃圾
            }
            return new Translation(resp, cents >= 0 ? status : PaymentStatus.FAILED, cents);
        }
    }

    static void aclDemo() {
        System.out.println("【防腐层：外部模型在边界被翻译】");
        PaymentGatewayAdapter adapter = new PaymentGatewayAdapter();
        for (String[] raw : new String[][]{{"00", "OK", "6900"}, {"A1", "处理中", "6900"},
                {"B2", "银行拒绝", "6900"}, {"X9", "未知码", "abc"}}) {
            LegacyPaymentResponse resp = new LegacyPaymentResponse();
            resp.retCode = raw[0];
            resp.retMsg = raw[1];
            resp.amt = raw[2];
            var t = adapter.translate(resp);
            System.out.printf("  外部 retCode=%-3s amt=%-5s -> 领域状态=%s 金额=%d 分%n",
                    resp.retCode, resp.amt, t.status(), t.amountCents());
        }
        System.out.println("  领域代码只见 PaymentStatus.SUCCESS —— 未知码被安全兜底");
        System.out.println();
    }

    static void stranglerPlan() {
        System.out.println("【绞杀者模式：逐步替换遗留订单系统】");
        List<String> steps = List.of(
                "阶段0：新订单上下文上线，ACL 承接遗留系统的读取（数据仍由旧系统写）",
                "阶段1：新订单接管创建（写切新，读兼容旧）—— 按流量灰度",
                "阶段2：查询/报表切新 —— 旧系统只处理历史只读",
                "阶段3：历史数据迁湖（46-09）-> 旧系统下线，ACL 移除");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  每阶段有流量开关可回退（41-07 灰度纪律的基础设施版）");
        System.out.println();
    }

    static void translationTests() {
        System.out.println("【翻译矩阵测试：ACL 的单元测试核心】");
        List<String> cases = List.of(
                "retCode 全枚举 × amt 合法/非法/空 -> 状态与金额全覆盖",
                "新增外部码 X9（未映射）-> 默认 FAILED 而不是崩溃",
                "amt 字段类型漂移（分->元）-> 校验规则当场拦截（46-08 契约同源）");
        for (String c : cases) {
            System.out.println("  - " + c);
        }
        System.out.println("  ACL 是外部系统的“看门人”：翻译矩阵就是它的测试用例集");
    }

    public static void main(String[] args) {
        aclDemo();
        stranglerPlan();
        translationTests();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为一个第三方接口（或虚构）写完整防腐层：翻译矩阵 + 未知码兜底 + 金额校验，并用翻译矩阵测试全覆盖。
 * 2. 制定一个绞杀者改造计划：三个阶段各自的范围、流量开关、回退条件。
 * 3. 思考题：为什么防腐层"宁做翻译也不透传"？从领域语言纯度与外部变更影响面两个角度分析。
 * ============================================================
 */
