/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 26-02
 * 课名：JWT 签发与验签
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】用 javax.crypto.HmacSHA256 实现迷你 JWT：签发（header/payload Base64Url + HMAC 签名）、验签（重算签名比对，常量时间比较）、过期校验（exp 字段）、payload 篡改检测（改角色字段后验签失败）；输出签发->验签->篡改->过期四个场景的结果。
 *   - 【考核点】JWT 三段结构、HMAC 签名与验签、Base64Url、exp 过期、篡改检测（36 章同款技术的闭环演练）。
 *   - 【易错点】签名比对用 equals（时序攻击，应 MessageDigest.isEqual）；不校验 exp（过期 token 可用）；把敏感信息放 payload（Base64 可解码）。
 *   - 【提示】Base64.getUrlEncoder().withoutPadding()；payload 是明文可读——只放非敏感声明。
 * ============================================================
 */

package com.heima.practice.middleware;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

// ==================== 示例代码 ====================

public class JwtMiniTask {
    static String sign(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes()));
    }

    static boolean verify(String data, String signature, String secret) throws Exception {
        byte[] expect = Base64.getUrlDecoder().decode(signature);
        byte[] actual = Base64.getUrlDecoder().decode(sign(data, secret));
        return java.security.MessageDigest.isEqual(expect, actual);   // 常量时间比较
    }

    public static void main(String[] args) throws Exception {
        String sig = sign("payload", "secret-key");
        System.out.println("  签名=" + sig);
        System.out.println("  正确验签：" + verify("payload", sig, "secret-key"));
        System.out.println("  篡改数据验签：" + verify("payload-tampered", sig, "secret-key"));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 JWT（签发/验签/exp/篡改检测），四场景输出结果，并解释 payload 为什么可被解码。
 * 2. 实现常量时间比较的对照实验：普通 equals 与 MessageDigest.isEqual 的时序差异说明。
 * 3. 思考题：为什么 JWT 无法"服务端注销"（除非黑名单）？从无状态验证机制推演登出语义的三种实现与代价。
 * 
 * 
 * ## 第27章 阶段项目实战
 * ============================================================
 */
