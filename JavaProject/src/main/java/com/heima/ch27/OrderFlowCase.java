/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-11
 * 课名：综合案例：下单全流程
 * ------------------------------------------------------------
 * 知识点：
 *   - 一个下单请求串起前面所有内容：参数校验（27-03）-> 查商品（27-02 分层）-> 扣库存（27-06 原子更新）-> 建订单（27-07 幂等）-> 统一异常（27-04）-> 日志与 traceId（27-08）-> 返回统一响应（27-01）。
 *   - 关键顺序：先校验后扣减，先扣库存后建订单（库存是稀缺资源，早点失败）；扣库存失败立即返回，不要建"待支付但没货"的订单。
 *   - 金额计算全部用分：`总价 = 单价 * 数量`，优惠单独算，最后一次性汇总，避免多次浮点累加。
 *   - 订单号生成：`业务前缀 + 日期 + 序列`（`ORD20261117` + 6 位），唯一索引兜底；不要用"当前时间戳"（同毫秒并发会撞）。
 *   - 状态机：`CREATED -> PAID -> SHIPPED -> COMPLETED`，`CREATED -> CANCELLED`。每次流转校验前置状态，非法流转直接拒绝。
 *   - 全链路一个 traceId：入口生成，日志、响应头（`X-Trace-Id`）都带上，用户报障时让他提供这个 id 就能定位。
 *   - 易错：把"扣库存"和"建订单"放在两个事务里（扣了库存但订单没建成，库存永久丢失）；订单号用 `System.currentTimeMillis()`；返回体里带上内部字段（成本价、库存表 id）。
 * ============================================================
 */

package com.heima.ch27;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// ==================== 示例代码 ====================

public class OrderFlowCase {
    static final ThreadLocal<String> TRACE = new ThreadLocal<>();
    static final Map<Long, Item> ITEM_TABLE = new ConcurrentHashMap<>();
    static final Map<String, Order> ORDER_TABLE = new ConcurrentHashMap<>();
    static final Map<String, String> IDEM = new ConcurrentHashMap<>();
    static final AtomicLong ORDER_SEQ = new AtomicLong(0);
    static final List<String> LOGS = new ArrayList<>();

    static class Item {
        Long id;
        String name;
        int priceCent;
        final AtomicInteger stock;

        Item(Long id, String name, int priceCent, int stock) {
            this.id = id;
            this.name = name;
            this.priceCent = priceCent;
            this.stock = new AtomicInteger(stock);
        }
    }

    static class Order {
        String orderNo;
        Long itemId;
        int count;
        int amountCent;
        String status = "CREATED";

        Order(String orderNo, Long itemId, int count, int amountCent) {
            this.orderNo = orderNo;
            this.itemId = itemId;
            this.count = count;
            this.amountCent = amountCent;
        }

        Map<String, Object> toVo() {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("orderNo", orderNo);
            vo.put("itemId", itemId);
            vo.put("count", count);
            vo.put("amount", String.format("%.2f", amountCent / 100.0));
            vo.put("status", status);
            return vo;
        }
    }

    public static void main(String[] args) {
        ITEM_TABLE.put(1L, new Item(1L, "机械键盘", 39900, 3));
        ITEM_TABLE.put(2L, new Item(2L, "显示器", 129900, 0));

        System.out.println("=== 正常下单 ===");
        System.out.println("  " + place("req-" + UUID.randomUUID(), 1L, 2));

        System.out.println();
        System.out.println("=== 库存不足（只剩 1 件，要 2 件）===");
        System.out.println("  " + place("req-" + UUID.randomUUID(), 1L, 2));

        System.out.println();
        System.out.println("=== 完全没货 ===");
        System.out.println("  " + place("req-" + UUID.randomUUID(), 2L, 1));

        System.out.println();
        System.out.println("=== 重复提交（同一幂等键）===");
        String sameReq = "req-fixed-key-001";
        System.out.println("  第1次: " + place(sameReq, 1L, 1));
        System.out.println("  第2次: " + place(sameReq, 1L, 1));
        System.out.println("  剩余库存=" + ITEM_TABLE.get(1L).stock.get() + "（期望 0）");

        System.out.println();
        System.out.println("=== 参数校验 ===");
        System.out.println("  " + place("req-" + UUID.randomUUID(), 1L, 0));
        System.out.println("  " + place("req-" + UUID.randomUUID(), 999L, 1));

        System.out.println();
        System.out.println("=== 状态流转 ===");
        String orderNo = ORDER_TABLE.keySet().iterator().next();
        System.out.println("  " + transit(orderNo, "PAID"));
        System.out.println("  " + transit(orderNo, "PAID"));
        System.out.println("  " + transit(orderNo, "SHIPPED"));
        System.out.println("  " + transit(orderNo, "CANCELLED"));

        System.out.println();
        System.out.println("=== 全链路日志（同一 traceId）===");
        for (String line : LOGS) {
            System.out.println("  " + line);
        }
    }

    /** 下单：校验 -> 扣库存 -> 建订单 -> 响应 */
    static Map<String, Object> place(String idemKey, Long itemId, int count) {
        TRACE.set(UUID.randomUUID().toString().substring(0, 8));
        try {
            log("INFO", "OrderController", "收到下单 itemId=" + itemId + " count=" + count);

            if (count <= 0) {
                return fail(400, "购买数量必须大于 0");
            }
            if (count > 99) {
                return fail(400, "单次最多购买 99 件");
            }

            String done = IDEM.get(idemKey);
            if (done != null) {
                log("INFO", "OrderService", "幂等命中 key=" + idemKey + " orderNo=" + done);
                return ok("重复请求，返回首次订单 " + done);
            }

            Item item = ITEM_TABLE.get(itemId);
            if (item == null) {
                log("WARN", "ItemRepository", "商品不存在 id=" + itemId);
                return fail(1001, "商品不存在");
            }
            log("INFO", "ItemRepository", "查询商品 " + item.name + " 单价=" + item.priceCent + "分");

            if (!deduct(item, count)) {
                log("WARN", "OrderService", "库存不足 itemId=" + itemId + " 剩余=" + item.stock.get());
                return fail(2001, "库存不足，仅剩 " + item.stock.get() + " 件");
            }
            log("INFO", "OrderService", "扣库存成功 itemId=" + itemId + " 剩余=" + item.stock.get());

            String orderNo = nextOrderNo();
            int amount = item.priceCent * count;
            ORDER_TABLE.put(orderNo, new Order(orderNo, itemId, count, amount));
            IDEM.put(idemKey, orderNo);
            log("INFO", "OrderService", "创建订单 orderNo=" + orderNo + " amount=" + amount + "分");

            Map<String, Object> resp = ok("下单成功");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            data.putAll(ORDER_TABLE.get(orderNo).toVo());
            data.put("traceId", TRACE.get());
            return resp;
        } catch (RuntimeException e) {
            log("ERROR", "OrderService", "下单异常 " + e);
            return fail(500, "系统繁忙，请稍后重试");
        } finally {
            TRACE.remove();
        }
    }

    /** 等价于 UPDATE item SET stock = stock - ? WHERE id = ? AND stock >= ? */
    static boolean deduct(Item item, int count) {
        while (true) {
            int cur = item.stock.get();
            if (cur < count) {
                return false;
            }
            if (item.stock.compareAndSet(cur, cur - count)) {
                return true;
            }
        }
    }

    static String nextOrderNo() {
        return "ORD20261117" + String.format("%06d", ORDER_SEQ.incrementAndGet());
    }

    static Map<String, Object> transit(String orderNo, String target) {
        Order order = ORDER_TABLE.get(orderNo);
        if (order == null) {
            return fail(1001, "订单不存在");
        }
        boolean legal = switch (order.status + "->" + target) {
            case "CREATED->PAID", "CREATED->CANCELLED", "PAID->SHIPPED",
                 "SHIPPED->COMPLETED" -> true;
            default -> false;
        };
        if (!legal) {
            return fail(2002, "非法状态流转 " + order.status + " -> " + target);
        }
        order.status = target;
        return ok("订单 " + orderNo + " 状态变为 " + target);
    }

    static Map<String, Object> ok(String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("msg", msg);
        body.put("data", new LinkedHashMap<String, Object>());
        return body;
    }

    static Map<String, Object> fail(int code, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("data", null);
        return body;
    }

    static void log(String level, String logger, String msg) {
        String line = "2026-11-17T10:23:45 " + level + " [" + TRACE.get() + "] " + logger + " - " + msg;
        synchronized (LOGS) {
            if (LOGS.size() < 40) {
                LOGS.add(line);
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把"扣库存"和"建订单"改成真正的两阶段：先 `UPDATE ... WHERE stock >= ?`，再 `INSERT`，说明如果 `INSERT` 失败该回滚什么、用哪个字段做回滚依据。
 * 2. 订单号生成用"日期 + 数据库自增序列"和用"雪花算法"各有什么优缺点？单库和分库分表场景分别选哪个。
 * 3. 给下单接口加上"同一用户 1 分钟内最多下 5 单"的限流，说明限流键怎么设计，以及限流和幂等的区别。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 把前几章的能力拼成一条下单链路：接口设计 → 分层 → DTO/VO 转换 → 参数校验 → 事务扣库存 → 幂等防重 → 统一异常 → 日志追踪 → 打包部署。这是第一个「完整业务闭环」的综合题。
 * 【任务】
 * 1. 设计下单接口：URL、方法、状态码、请求体、响应体各写清楚，说明为什么下单用 POST 而不是 GET、为什么创建成功返回 201。
 * 2. 实现 Service 层下单事务：校验商品与库存 → 扣库存（用 update ... where stock >= n 防超卖）→ 写订单与明细 → 清购物车，任一步失败整体回滚。
 * 3. 实现幂等：客户端传 requestId，服务端用唯一索引或 Redis setnx 保证同一 requestId 只成功一次，重复请求返回首次结果。
 * 4. 用 MDC 给每次请求生成 traceId 并打进日志，串联 Controller 与 Service 的日志；最后打成 jar 用 java -jar 启动验证。
 * 【验收清单】
 * [ ] 并发 100 次抢 10 件库存，成功订单恰好 10 笔，库存不为负。
 * [ ] 同一 requestId 重复提交 10 次只产生 1 笔订单。
 * [ ] 库存不足、商品下架、参数非法三种失败场景都返回明确错误码且数据无脏写。
 * [ ] 同一请求的日志能靠 traceId 串起来，jar 包在干净环境可启动。
 * ============================================================
 */
