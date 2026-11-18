/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：55-03
 * 课名：租户上下文与路由
 * ------------------------------------------------------------
 * 知识点：
 *   - **租户上下文（TenantContext）的传播链**：入口解析租户（子域名/JWT claim/请求头）-> ThreadLocal/Context 存储 -> 全链路透传（线程池要传递——50 章 ThreadLocal 审计在多租户的场景化）-> 落库自动注入（MyBatis 拦截器改写 SQL，55-02）。
 *   - **自动注入 vs 手动传参**：**自动注入（SQL 拦截器改写 WHERE tenant_id）能防止"漏租户条件"的人为事故**——但魔法化要配套：拦截器日志可观测、绕过机制显式声明（管理后台跨租户查询用显式 API）。
 *   - **异步与定时任务的租户传播**：线程池任务丢失上下文（50-05 装饰器传递）；定时任务无请求上下文——**显式遍历租户执行**（按租户循环），且要配额限速（大租户任务不让小租户饿死）。
 *   - **MQ 的租户路由**：消息带租户属性 -> 消费者按租户处理；**按租户分区的顺序性**（同租户消息有序，39-08 分区键加租户）；**租户级消费限速**（噪声邻居防护 55-08）。
 *   - 易错点：上下文只覆盖同步链（异步丢失 -> 数据写错租户，最严重事故）；租户来源可伪造（HTTP 头直接信任，应从 JWT 取——44-09）；拦截器改写吞掉非业务 SQL（运维 SQL 被改写出错）。
 * ============================================================
 */

package com.heima.ch55;

import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class TenantContext {
    static final ThreadLocal<String> CTX = new ThreadLocal<>();

    static void set(String tenantId) {
        CTX.set(tenantId);
    }

    static String require() {
        String t = CTX.get();
        if (t == null) {
            throw new IllegalStateException("租户上下文缺失 —— 拒绝执行（防跨租户写）");
        }
        return t;
    }

    static void clear() {
        CTX.remove();               // 线程复用必须清理（50-06）
    }

    static void writeRecord(String data) {
        String tenant = require();
        System.out.printf("  写入数据 %s（tenant=%s）%n", data, tenant);
    }

    public static void main(String[] args) {
        set("t-100");
        writeRecord("order-9001");
        clear();                    // 线程归还前清理
        try {
            writeRecord("order-9002");
        } catch (IllegalStateException e) {
            System.out.println("  上下文缺失被拦截：" + e.getMessage());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整租户上下文（来源解析 + 线程池装饰器传递 + 落库注入 + 强制校验），异步场景验证不丢租户。
 * 2. 实现 MyBatis 拦截器模拟：改写 SQL 自动加 tenant_id 条件，验证绕过声明机制。
 * 3. 思考题：为什么租户 ID 必须从 JWT 取而不能信任请求头？构造一个伪造 X-Tenant-Id 头读取他租数据的攻击时序。
 * ============================================================
 */
