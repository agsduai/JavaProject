/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-11
 * 课名：综合案例：微服务化的下单链路
 * ------------------------------------------------------------
 * 知识点：
 *   - 把前面所有能力拼起来：网关统一鉴权限流 → 订单服务编排 → 调用商品服务扣库存（超时 + 重试 + 熔断）→ 调用用户服务取地址 → 本地事务写订单 → 发消息异步加积分 → 全链路 traceId。
 *   - 每一步都要有明确的**失败策略**：库存不足 → 直接失败；用户服务超时 → 用默认地址或让用户重填（可降级）；积分失败 → 不影响下单（异步 + 重试）。
 *   - 关键设计：**核心链路只包含必须同步的步骤**，任何"可以晚点做"的都移到异步。
 *   - 熔断与降级在案例中的位置：商品服务熔断时快速失败（不能超卖），积分服务熔断时静默降级（不影响下单）。
 *   - 配置中心提供超时/重试/开关参数，出问题可以动态调小超时或关闭某条旁路。
 *   - 易错：把积分调用放在订单事务里（事务被远程调用拉长，锁持有时间暴增）；库存扣减失败后没有回滚已扣的优惠券。
 * ============================================================
 */

package com.heima.ch29;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MicroserviceOrderCase {
    static final Map<String, Integer> STOCK = new HashMap<>(Map.of("ITEM-1", 10));
    static final Map<String, String> ORDERS = new LinkedHashMap<>();
    static final Map<String, Integer> POINTS = new HashMap<>();
    static final List<String> MQ = new ArrayList<>();
    static final List<String> TRACE = new ArrayList<>();

    /** 简单熔断器：连续失败 3 次打开 5s */
    static class Breaker {
        int fails;
        long openUntil;
        boolean allow(long now) {
            return now >= openUntil;
        }
        void record(boolean ok, long now) {
            if (ok) {
                fails = 0;
            } else if (++fails >= 3) {
                openUntil = now + 5000;
                log("WARN", "积分服务熔断打开 5s");
            }
        }
    }

    static void log(String level, String msg) {
        String line = "[trace-" + Integer.toHexString(TRACE.size() + 1) + "] " + level + " " + msg;
        TRACE.add(line);
        System.out.println("  " + line);
    }

    /** 商品服务：扣库存（核心链路，失败即整单失败） */
    static boolean deductStock(String itemId, int qty) {
        Integer cur = STOCK.get(itemId);
        if (cur == null || cur < qty) {
            log("ERROR", "item-service 扣库存失败：库存不足 " + cur);
            return false;
        }
        STOCK.put(itemId, cur - qty);
        log("INFO", "item-service 扣库存成功，剩余 " + (cur - qty));
        return true;
    }

    /** 用户服务：取地址（可降级） */
    static String getAddress(String userId, boolean timeout) {
        if (timeout) {
            log("WARN", "user-service 超时 300ms -> 降级：使用用户上次地址");
            return "默认地址（降级）";
        }
        log("INFO", "user-service 返回地址");
        return "北京市海淀区";
    }

    /** 积分服务：异步 + 熔断 + 幂等 */
    static void addPointsAsync(Breaker breaker, String orderNo, String userId, long now) {
        if (!breaker.allow(now)) {
            log("WARN", "points-service 熔断中，消息留在队列稍后重试");
            return;
        }
        try {
            if (orderNo.endsWith("BAD")) {
                throw new IllegalStateException("积分服务 500");
            }
            POINTS.merge(userId, 100, Integer::sum);
            breaker.record(true, now);
            log("INFO", "points-service 加积分成功，累计 " + POINTS.get(userId));
        } catch (RuntimeException e) {
            breaker.record(false, now);
            log("ERROR", "points-service 失败：" + e.getMessage() + " -> 进重试队列");
        }
    }

    public static void main(String[] args) {
        long now = 0;
        Breaker pointsBreaker = new Breaker();

        System.out.println("=== 场景一：正常下单 ===");
        createOrder(pointsBreaker, "ORD-001", "u1001", "ITEM-1", 2, false, now);

        System.out.println();
        System.out.println("=== 场景二：用户服务超时，降级继续 ===");
        createOrder(pointsBreaker, "ORD-002", "u1001", "ITEM-1", 1, true, now);

        System.out.println();
        System.out.println("=== 场景三：库存不足，核心链路失败 ===");
        createOrder(pointsBreaker, "ORD-003", "u1002", "ITEM-1", 999, false, now);

        System.out.println();
        System.out.println("=== 场景四：积分服务连续失败 -> 熔断 ===");
        for (int i = 0; i < 3; i++) {
            addPointsAsync(pointsBreaker, "ORD-BAD-" + i, "u1003", now);
        }
        addPointsAsync(pointsBreaker, "ORD-004", "u1003", now);
        System.out.println("  等待 5s 冷却后恢复：");
        now += 5000;
        addPointsAsync(pointsBreaker, "ORD-005", "u1003", now);

        System.out.println();
        System.out.println("=== 最终状态 ===");
        System.out.println("  库存=" + STOCK);
        System.out.println("  订单=" + ORDERS);
        System.out.println("  积分=" + POINTS);
        System.out.println("  待投递消息=" + MQ);
        System.out.println("  链路日志条数=" + TRACE.size());
    }

    static void createOrder(Breaker breaker, String orderNo, String userId, String itemId,
                            int qty, boolean userTimeout, long now) {
        log("INFO", "gateway 鉴权通过，限流放行，userId=" + userId);
        if (!deductStock(itemId, qty)) {
            log("ERROR", "order-service 下单失败，返回 409");
            return;
        }
        String address = getAddress(userId, userTimeout);
        ORDERS.put(orderNo, "CREATED:" + address);
        log("INFO", "order-service 写订单 " + orderNo + " 成功（本地事务）");
        MQ.add("order.created:" + orderNo);
        log("INFO", "投递 order.created -> MQ");
        addPointsAsync(breaker, orderNo, userId, now);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给案例加上"优惠券扣减"步骤，并保证库存扣减失败时优惠券能回滚。
 * 2. 把积分调用改成真正的异步（投 MQ 后立即返回），重新输出链路日志，对比总耗时。
 * 3. 画出这个案例的完整架构图，标出网关、各服务、MQ、配置中心、注册中心的位置与调用方向。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 把单体下单拆成订单、库存、用户三个服务：注册与发现、远程调用带超时、负载均衡与重试、网关鉴权限流、熔断降级、最终一致，最后用链路追踪串起一次下单。
 * 【任务】
 * 1. 用内存 Map 模拟注册中心：服务启动注册、定时心跳、超时剔除，客户端按服务名拿到实例列表。
 * 2. 实现远程调用：设置连接与读取超时，失败重试最多 2 次且只对幂等接口重试，说明为什么不幂等接口不能重试。
 * 3. 实现网关：路由转发、token 鉴权、令牌桶限流（每秒 20 个），超限返回 429 而不是 500。
 * 4. 实现熔断器三态（CLOSED/OPEN/HALF_OPEN）：下游连续失败 5 次打开、30 秒后放一个探测请求、成功则恢复；熔断期间走降级返回兜底数据。
 * 【验收清单】
 * [ ] 停掉一个服务实例后客户端能在心跳超时内把它剔除，请求不再打过去。
 * [ ] 下游超时场景下调用方在配置时间内返回，不出现线程堆积。
 * [ ] 限流触发时被拒绝的请求数与配置速率一致，返回 429。
 * [ ] 熔断打开后请求快速失败走降级，恢复后自动切回正常调用，日志能看清三态迁移。
 * ============================================================
 */
