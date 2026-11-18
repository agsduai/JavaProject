/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-07
 * 课名：幂等与防重复提交
 * ------------------------------------------------------------
 * 知识点：
 *   - 幂等：同一个请求执行一次和执行 N 次，结果一样。`GET`/`PUT`/`DELETE` 天然幂等，`POST` 不幂等（下单、支付、发券）。
 *   - 重复提交的三个来源：用户连点、网络超时后客户端重试、消息队列重复投递。前端置灰按钮只是体验优化，不是防护。
 *   - 主流方案：
 *   - 幂等键（idempotency-key）：客户端生成唯一 key 随请求带上，服务端用 `SETNX` 或唯一索引占位，占不到直接返回上次结果。
 *   - 数据库唯一索引：`uk_order_no`，重复插入报 `DuplicateKeyException`，捕获后当作"已处理"返回。最可靠，作为最后一道防线。
 *   - 状态机：订单只能从 `PENDING` 流转到 `PAID`，已支付的再收到支付回调直接返回成功，不重复加钱。
 *   - 分布式锁：同一业务 key 只允许一个请求进入，其它等待或快速失败。
 *   - 幂等表设计：`idempotency_key`（唯一）、`biz_type`、`result`（缓存首次结果）、`status`（PROCESSING / DONE）、`expire_at`。处理中重复请求要返回"处理中，请稍后"而不是重复执行。
 *   - 幂等键的选取：不能用"用户 id + 时间戳"（同一秒重复），要用客户端生成的 UUID 或"业务号"（订单号天然唯一）。
 *   - 易错：先查后插（并发下两个请求都查到"不存在"）；幂等记录和业务数据不在同一个事务里；幂等 key 永不过期导致表无限膨胀。
 * ============================================================
 */

package com.heima.ch27;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// ==================== 示例代码 ====================

public class IdempotentSubmit {
    /** 幂等记录表：key -> 记录 */
    static final Map<String, IdemRecord> IDEM_TABLE = new ConcurrentHashMap<>();
    /** 订单表：orderNo -> 金额 */
    static final Map<String, Integer> ORDER_TABLE = new ConcurrentHashMap<>();
    static final AtomicInteger CREATE_COUNT = new AtomicInteger();

    static class IdemRecord {
        String status;      // PROCESSING / DONE
        String result;

        IdemRecord(String status, String result) {
            this.status = status;
            this.result = result;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 场景一：用户连点 5 次（同一幂等键）===");
        String key = UUID.randomUUID().toString();
        for (int i = 1; i <= 5; i++) {
            System.out.println("  第 " + i + " 次: " + submit(key, "ITEM-1", 1));
        }
        System.out.println("  实际创建订单数=" + CREATE_COUNT.get() + "（期望 1）");

        System.out.println();
        System.out.println("=== 场景二：10 个线程并发提交同一个键 ===");
        CREATE_COUNT.set(0);
        ORDER_TABLE.clear();
        IDEM_TABLE.clear();
        String sameKey = "client-uuid-0001";
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            pool.execute(() -> {
                try {
                    gate.await();
                    String r = submit(sameKey, "ITEM-2", 1);
                    System.out.println("  " + Thread.currentThread().getName() + " -> " + r);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        gate.countDown();
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();
        System.out.println("  实际创建订单数=" + CREATE_COUNT.get() + "（期望 1）");

        System.out.println();
        System.out.println("=== 场景三：不同幂等键，正常下两单 ===");
        CREATE_COUNT.set(0);
        ORDER_TABLE.clear();
        IDEM_TABLE.clear();
        System.out.println("  " + submit(UUID.randomUUID().toString(), "ITEM-3", 1));
        System.out.println("  " + submit(UUID.randomUUID().toString(), "ITEM-3", 1));
        System.out.println("  实际创建订单数=" + CREATE_COUNT.get() + "（期望 2）");

        System.out.println();
        System.out.println("=== 场景四：唯一索引兜底（幂等表被绕过时）===");
        ORDER_TABLE.clear();
        String orderNo = "ORD202611170001";
        System.out.println("  " + insertOrder(orderNo, 9900));
        System.out.println("  " + insertOrder(orderNo, 9900));

        System.out.println();
        System.out.println("=== 场景五：状态机幂等（重复支付回调）===");
        Map<String, String> orderStatus = new LinkedHashMap<>();
        orderStatus.put("ORD-1", "PENDING");
        System.out.println("  " + payCallback(orderStatus, "ORD-1", 9900));
        System.out.println("  " + payCallback(orderStatus, "ORD-1", 9900));
        System.out.println("  最终状态=" + orderStatus.get("ORD-1") + "，只加了一次钱");

        System.out.println();
        System.out.println("=== 幂等表结构 ===");
        System.out.println("  idempotency_key VARCHAR(64) PRIMARY KEY  -- 客户端 UUID 或业务号");
        System.out.println("  biz_type        VARCHAR(32)              -- ORDER / PAY / COUPON");
        System.out.println("  status          VARCHAR(16)              -- PROCESSING / DONE");
        System.out.println("  result          TEXT                     -- 首次执行结果，重复请求直接返回");
        System.out.println("  expire_at       DATETIME                 -- 定时任务清理，避免表无限膨胀");
    }

    /** 幂等提交：占位 -> 执行 -> 回填结果 */
    static String submit(String idemKey, String itemId, int count) {
        IdemRecord existing = IDEM_TABLE.putIfAbsent(idemKey, new IdemRecord("PROCESSING", null));
        if (existing != null) {
            if ("DONE".equals(existing.status)) {
                return "幂等命中，返回首次结果: " + existing.result;
            }
            return "处理中，请稍后查询（key=" + idemKey.substring(0, 8) + "...）";
        }
        try {
            String orderNo = "ORD" + System.nanoTime();
            CREATE_COUNT.incrementAndGet();
            ORDER_TABLE.put(orderNo, 9900 * count);
            String result = "下单成功 orderNo=" + orderNo;
            IDEM_TABLE.put(idemKey, new IdemRecord("DONE", result));
            return result;
        } catch (RuntimeException e) {
            IDEM_TABLE.remove(idemKey);        // 执行失败要释放占位，允许重试
            return "下单失败: " + e.getMessage();
        }
    }

    /** 数据库唯一索引：重复插入抛异常，捕获后视为已处理 */
    static String insertOrder(String orderNo, int amountCent) {
        if (ORDER_TABLE.putIfAbsent(orderNo, amountCent) != null) {
            return "DuplicateKeyException(uk_order_no) -> 视为重复请求，返回成功";
        }
        return "插入成功 " + orderNo;
    }

    /** 状态机：只有 PENDING 才能变 PAID */
    static String payCallback(Map<String, String> orderStatus, String orderNo, int amountCent) {
        String status = orderStatus.get(orderNo);
        if (status == null) {
            return "订单不存在";
        }
        if ("PAID".equals(status)) {
            return "已是 PAID，重复回调直接返回成功（不重复加钱）";
        }
        if (!"PENDING".equals(status)) {
            return "状态 " + status + " 不允许支付";
        }
        orderStatus.put(orderNo, "PAID");
        return "支付成功，金额 " + amountCent + " 分";
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为什么"先 `SELECT` 判断幂等键不存在，再 `INSERT`"在并发下会失效？说出两种正确的实现。
 * 2. 幂等键由客户端生成还是服务端生成？各自适用于什么场景，分别有什么风险。
 * 3. 幂等记录和业务数据必须放在同一个事务里，如果不在会发生什么？举一个"扣了钱但幂等记录没写进去"的后果。
 * ============================================================
 */
