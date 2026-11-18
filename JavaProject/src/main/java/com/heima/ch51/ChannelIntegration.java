/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：51-02
 * 课名：渠道对接与签名验签
 * ------------------------------------------------------------
 * 知识点：
 *   - **渠道对接的三件事**：① **请求签名**（用商户私钥对参数签名，渠道验签防篡改）；② **回调验签**（渠道用其私钥回调，你用渠道公钥验签——**验签不过一律拒绝**，36 章安全原则的资金级执行）；③ **证书/密钥管理**（密钥不入库不入 git，KMS/Vault 管理，36 章供应链教训）。
 *   - **渠道协议的共性**：下单接口（返回支付凭据如 prepay_id）、查询接口（主动查单补状态）、回调通知（异步告知结果）、退款接口、对账文件下载（51-05）。**抽象成统一渠道网关接口**（49-04 端口），各渠道适配器实现——新增渠道=新增适配器。
 *   - **渠道返回码的语义分级**：成功 / 处理中（未知态！）/ 失败 ——**"处理中"必须转查询轮询**，绝不能当失败（51-03 状态机）。渠道码翻译进防腐层（49-07），领域只见自己的状态。
 *   - **主动查询补状态**：回调可能丢（渠道侧事故）——**支付单超时未终态 -> 定时查渠道**（1s/5s/30s 退避，指数退避 40-02），查询结果与回调结果幂等合并（先到先得，状态机只进不退）。
 *   - **渠道路由**：多渠道可用时按**成功率/费率/限额**路由——渠道熔断（连续失败摘除，47-03 电路思想）+ 降级到备用渠道（44-08 阶梯）。渠道故障是支付系统最常见的生产事故。
 *   - 易错点：回调不验签直接处理（伪造回调=资金损失）；"处理中"当失败关单（渠道随后成功 -> 已关单但扣了钱 -> 客诉+资损）；密钥进代码库（36 章教训）；渠道超时与支付单超时不匹配（渠道还在处理你已关单）。
 * ============================================================
 */

package com.heima.ch51;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ChannelIntegration {
    /** 渠道返回码 -> 领域状态（防腐层 49-07） */
    enum PaymentStatus {
        SUCCESS, PENDING, FAILED
    }

    record ChannelResult(PaymentStatus status, String channelTxId, String rawCode) {
    }

    static class ChannelAdapter {
        private static final Map<String, PaymentStatus> CODE_MAP = Map.of(
                "SUCCESS", PaymentStatus.SUCCESS,
                "PROCESSING", PaymentStatus.PENDING,
                "USERPAYING", PaymentStatus.PENDING,
                "FAIL", PaymentStatus.FAILED);

        ChannelResult normalize(String code, String txId, String signature, boolean signatureValid) {
            if (!signatureValid) {
                throw new IllegalStateException("回调验签失败 -> 拒绝处理（防伪造）");
            }
            PaymentStatus s = CODE_MAP.getOrDefault(code, PaymentStatus.PENDING);   // 未知码按 PENDING 走查询
            return new ChannelResult(s, txId, code);
        }
    }

    static void normalizeDemo() {
        System.out.println("【渠道码归一：未知码按 PENDING 处理】");
        ChannelAdapter adapter = new ChannelAdapter();
        for (String[] raw : new String[][]{{"SUCCESS", "ok"}, {"PROCESSING", "ok"},
                {"USERPAYING", "ok"}, {"NEWCODE_X", "ok（未映射）"}}) {
            ChannelResult r = adapter.normalize(raw[0], "ch-tx-1", "", true);
            System.out.printf("  渠道码 %-12s -> %s（txId=%s）%n", raw[0], r.status(), r.channelTxId());
        }
        try {
            adapter.normalize("SUCCESS", "ch-tx-2", "", false);
        } catch (IllegalStateException e) {
            System.out.println("  验签失败：" + e.getMessage());
        }
        System.out.println("  关键：PENDING 不是终态 -> 进入查询轮询（下一段代码）");
        System.out.println();
    }

    static void queryPolling() {
        System.out.println("【主动查询：回调丢失时的补状态机制】");
        long[] backoffs = {1000, 5000, 30000, 60000};
        String current = "PENDING";
        for (long backoff : backoffs) {
            if (current.equals("PENDING")) {
                // 模拟第 3 次查询渠道返回成功
                current = backoff == 30000 ? "SUCCESS" : "PENDING";
                System.out.printf("  等待 %ds 后查询渠道 -> %s%n", backoff / 1000, current);
            }
        }
        System.out.println("  终态后停止轮询；全部轮询用尽 -> 差异单进人工（51-05）");
        System.out.println("  幂等合并：查询与回调谁先到都一样（状态机只进不退，51-03）");
        System.out.println();
    }

    static void routing() {
        System.out.println("【渠道路由：成功率/费率/熔断】");
        record Channel(String name, double feeRate, double successRate, boolean circuitOpen) {
        }
        List<Channel> channels = List.of(
                new Channel("wechat", 0.006, 0.999, false),
                new Channel("alipay", 0.006, 0.998, false),
                new Channel("union-b", 0.005, 0.97, true));   // 熔断中：连续失败被摘除
        for (Channel c : channels) {
            System.out.printf("  %-8s 费率=%.1f%% 成功率=%.1f%% 熔断=%s%n",
                    c.name(), c.feeRate() * 100, c.successRate() * 100,
                    c.circuitOpen() ? "开（摘除）" : "关");
        }
        System.out.println("  路由决策：过滤熔断渠道 -> 剩余按费率/成功率加权选择");
        System.out.println("  熔断恢复：半开探测成功 -> 重新纳入（47-03 同款三态）");
    }

    public static void main(String[] args) {
        normalizeDemo();
        queryPolling();
        routing();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为两个虚构渠道实现统一渠道网关接口（端口 49-04），含防腐层翻译与验签失败拒绝，写出翻译矩阵测试。
 * 2. 实现查询轮询器（指数退避 + 终态停止 + 次数用尽进差异单），用回调丢失场景验证补状态。
 * 3. 思考题：为什么"未知渠道码默认 PENDING"比默认 FAILED 安全？从"渠道后续成功"的时序推演两种默认值的资损/客诉差异。
 * ============================================================
 */
