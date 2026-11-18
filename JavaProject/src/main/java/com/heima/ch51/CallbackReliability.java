/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：51-04
 * 课名：异步回调与通知可靠
 * ------------------------------------------------------------
 * 知识点：
 *   - **回调是支付结果的主通道，但不可靠**：渠道回调可能重复（多次通知）、乱序（查单与回调交错）、丢失（渠道事故）——**回调处理必须幂等 + 查询兜底**（51-02），回调只是"加速器"，不是"唯一事实来源"。
 *   - **回调处理的黄金流程**：验签 -> 幂等检查（notifyId/channelTxId）-> 状态机裁决 -> 本地事务（状态+账务+outbox）-> 返回渠道应答（success）——**任何一步失败都返回失败应答**让渠道重试（渠道会按退避重发）。
 *   - **应答协议要严格遵守**：微信返回 XML/JSON success、支付宝返回 "success" 字符串——**应答格式错误渠道会无限重发**（打爆接口）；应答 success 但处理失败 = 静默丢事件（对账才能发现）。
 *   - **对外通知（商户侧回调）同样要可靠**：支付成功后通知下游商户——**同样的重试 + 幂等 + 签名**（你是渠道，商户是你的下游），通知失败进重试队列（28 章延迟队列）。
 *   - **回调风暴防护**：渠道重试风暴（网络故障恢复后积压回调齐发）——限流 + 去重后处理；**去重表先行**（验签前先查已处理，省验签开销可权衡）。
 *   - 易错点：处理失败也返回 success（丢事件）；验签前先改状态（伪造回调直接改钱）；回调处理超时（渠道判定失败重发 -> 重复处理，幂等救场）；通知商户不带签名（商户侧无法验真）。
 * ============================================================
 */

package com.heima.ch51;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class CallbackReliability {
    static class CallbackHandler {
        final Map<String, String> processed = new LinkedHashMap<>();    // notifyId -> 应答
        final Map<String, String> payments = new LinkedHashMap<>();     // txId -> 状态

        String handle(String notifyId, String txId, String event,
                      boolean signatureValid, boolean processWillFail) {
            // 1) 幂等前置：同 notifyId 已处理 -> 直接返回首次应答
            String prior = processed.get(notifyId);
            if (prior != null) {
                System.out.printf("  notify=%s 重复 -> 返回首次应答 %s（不重复处理）%n",
                        notifyId, prior);
                return prior;
            }
            // 2) 验签
            if (!signatureValid) {
                return "FAIL:invalid-signature";
            }
            // 3) 处理（状态机 + 记账 + outbox）
            String ack;
            if (processWillFail) {
                ack = "FAIL:processing-error";      // 处理失败 -> 返回失败，渠道会重试
            } else {
                payments.put(txId, event);
                ack = "success";
                processed.put(notifyId, ack);
                System.out.printf("  notify=%s 处理成功：txId=%s -> %s%n", notifyId, txId, event);
            }
            return ack;
        }
    }

    static void flowDemo() {
        System.out.println("【回调处理黄金流程与失败重试】");
        CallbackHandler handler = new CallbackHandler();
        System.out.println("  第一次回调（处理成功）：");
        System.out.println("    应答：" + handler.handle("n-1", "ch-1", "SUCCESS", true, false));
        System.out.println("  渠道重发同通知（网络原因）：");
        System.out.println("    应答：" + handler.handle("n-1", "ch-1", "SUCCESS", true, false));
        System.out.println("  处理中途故障的回调：");
        String ack = handler.handle("n-2", "ch-2", "SUCCESS", true, true);
        System.out.println("    应答：" + ack + " -> 渠道按退避重试 -> 下次成功");
        System.out.println("  关键：失败必须返回失败应答，绝不能返回 success 掩盖问题");
        System.out.println();
    }

    static void ackProtocol() {
        System.out.println("【应答协议：格式错误 = 无限重发】");
        String[][] rows = {
                {"微信支付", "返回 XML/JSON success 标记", "格式错 -> 渠道按退避持续重发"},
                {"支付宝", "纯文本 success", "多余空格都算失败"},
                {"通用原则", "成功才 success；失败返回失败标记", "让渠道的重试机制工作"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  事故对照：处理失败却返回 success -> 渠道认为完成 -> 事件永久丢失");
        System.out.println("  兜底：即便如此，51-05 对账仍能发现（对账是最后防线）");
        System.out.println();
    }

    static void notifyDownstream() {
        System.out.println("【对外通知商户：你是渠道的角色】");
        List<String> duties = List.of(
                "通知内容签名（商户可验真，51-02 反向）",
                "重试策略：指数退避 + 最大次数 + 死信人工（28 章队列语义）",
                "商户确认应答校验（商户回 success 才算送达）",
                "通知查询接口：商户可主动拉取（回调丢失的兜底，对称 51-02）",
                "审计：每次通知的请求/应答/耗时全落库（41-10 台账思想）");
        for (String d : duties) {
            System.out.println("  - " + d);
        }
        System.out.println("  对称性：渠道如何对你，你就如何对商户 —— 协议镜像原则");
    }

    public static void main(String[] args) {
        flowDemo();
        ackProtocol();
        notifyDownstream();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现回调处理器完整版（幂等前置 + 验签 + 状态机 + 失败应答），模拟渠道重试风暴（100 个重复通知）验证去重。
 * 2. 复现"处理失败返回 success"的事故：观察事件丢失如何在下游表现为"订单永远待支付"，然后修复应答逻辑。
 * 3. 思考题：为什么"回调只是加速器，查询才是事实兜底"？从回调丢失、乱序、重复三种失效模式推演只依赖回调的风险。
 * ============================================================
 */
