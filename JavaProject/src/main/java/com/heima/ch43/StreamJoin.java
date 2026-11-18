/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-07
 * 课名：流式 Join 与维度关联
 * ------------------------------------------------------------
 * 知识点：
 *   - **流上的 Join 难在"两边都在动"**：批处理 join 时两张表都静止；流上订单流与支付流都在到达，**必须各自缓存等待配对**——缓存多大、等多久，是流 join 的核心参数。
 *   - **窗口 join**：两边各按窗口截取，窗口内互相配对（如 10 分钟内订单与支付匹配）。实现简单，代价是**边界订单丢失**（订单 11:59:58、支付 00:00:01 跨窗口）。适合"相关性窗口明确"的场景。
 *   - **区间 join（interval join）**：按业务时间区间配对——`支付时间 BETWEEN 下单时间 AND 下单时间+30min`。语义精确（每个订单在自己区间内找支付），是订单-支付类 join 的标准解。实现上要缓存左流直到区间终点（水位线推进才清理）。
 *   - **维表关联（stream-table join）**：订单流关联商品维表——维表是"慢变的静态数据"。三种实现：**同步查库**（简单但 QPS 高时打爆 DB）、**本地缓存 + 定期刷新**（热路径首选，代价是短暂不一致）、**维表变更流广播**（CDC 推送变更，一致性最好）。
 *   - **迟到与配对失败的处理**：等了 30 分钟没有支付的订单——进"未配对旁路"（对账用），不能静默丢弃。**join 的输出要覆盖三种情况：配对成功、左等待、右等待**。
 *   - **状态量估算**：join 缓存 = 左流速率 × 区间长度。订单 1000 QPS × 30 分钟 = 180 万条在缓存——**区间长度与状态量的乘积关系**决定 join 设计。
 *   - 易错点：join 缓存无清理（状态爆炸，43-05）；用处理时间做区间（乱序下配对错乱）；维表本地缓存无刷新（商品改价后订单一直用旧价）；配对失败的订单静默丢失（财务对不上）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class StreamJoin {
    record Order(long id, long eventTs, double amount) {
    }

    record Payment(long orderId, long eventTs) {
    }

    /** 区间 join：支付需落在订单后 30 分钟内（秒） */
    static final long INTERVAL_SEC = 1800;

    static void intervalJoin() {
        System.out.println("【区间 join：订单流 × 支付流】");
        List<Order> orders = List.of(
                new Order(1, 100, 199.0),
                new Order(2, 200, 69.0),
                new Order(3, 300, 129.0));
        List<Payment> payments = List.of(
                new Payment(1, 150),          // 50s 后支付：命中
                new Payment(2, 2400),         // 2200s 后支付：超区间
                new Payment(3, 320));         // 20s：命中
        for (Order o : orders) {
            Payment hit = payments.stream()
                    .filter(p -> p.orderId() == o.id())
                    .filter(p -> p.eventTs() >= o.eventTs()
                            && p.eventTs() <= o.eventTs() + INTERVAL_SEC)
                    .findFirst().orElse(null);
            if (hit != null) {
                System.out.printf("  配对成功：订单%d（%.0f元）<-> 支付（延迟 %ds）%n",
                        o.id(), o.amount(), hit.eventTs() - o.eventTs());
            } else {
                System.out.printf("  未配对：订单%d -> 进入未配对旁路（对账）%n", o.id());
            }
        }
        System.out.println("  左流缓存存活 = 区间长度；水位线推进过区间终点才清理");
        System.out.println();
    }

    static void dimensionLookup() {
        System.out.println("【维表关联：三种实现的取舍】");
        String[][] rows = {
                {"同步查库", "每事件查商品表", "简单，QPS 高时打爆 DB"},
                {"本地缓存+刷新", "热数据常驻，30s 刷新", "首选；短暂不一致可接受"},
                {"维表 CDC 广播", "变更流推到每个算子", "一致性最好；要处理广播状态合并"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-24s %s%n", r[0], r[1], r[2]);
        }
        record Order2(long id, String cacheVersion, String realPrice, String verdict) {
        }
        List<Order2> cases = List.of(
                new Order2(101, "价格59（30s前）", "59", "一致，正常"),
                new Order2(102, "价格59（刚刷新前）", "49（刚改价）", "短暂不一致：财务口径要声明"));
        for (Order2 c : cases) {
            System.out.printf("  订单%d 缓存=%s 实际=%s -> %s%n",
                    c.id(), c.cacheVersion(), c.realPrice(), c.verdict());
        }
        System.out.println();
    }

    static void stateBudget() {
        System.out.println("【join 的状态预算】");
        long qps = 1000, intervalSec = 1800;
        long cacheSize = qps * intervalSec;
        System.out.printf("  左流 %d QPS × 区间 %ds = %d 万条在缓存%n",
                qps, intervalSec, cacheSize / 10000);
        System.out.printf("  每条 200B -> 约 %d MB 状态（RocksDB 可扛，但要监控）%n", cacheSize * 200 / 1024 / 1024);
        System.out.println("  缩减手段：缩短区间（业务允许吗）、预过滤（只缓存未支付订单）");
    }

    public static void main(String[] args) {
        intervalJoin();
        dimensionLookup();
        stateBudget();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现区间 join 的完整模拟：含"未配对旁路"与"缓存清理（水位线推进）"，用 1000 单验证三种输出（配对/左等待/超时）的比例。
 * 2. 对比维表关联三种实现：以商品改价为测试用例，观察同步查库/本地缓存/CDC 广播在一致性上的差异。
 * 3. 思考题：为什么 join 缓存的清理必须由水位线驱动而不是定时器？从乱序数据"回来配对"的可能性推导。
 * ============================================================
 */
