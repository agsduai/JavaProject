/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-08
 * 课名：跨分片查询与聚合
 * ------------------------------------------------------------
 * 知识点：
 *   - **分片后的查询分三类**：① 带分片键 → 单分片，性能不变；② 带分片键以外的唯一索引（如按 order_id 查，路由靠基因）→ 单分片；③ 不带任何路由键（后台统计、全局搜索）→ **广播**，扇出到所有分片再归并。设计目标永远是让查询落在前两类。
 *   - **归并排序是跨分片查询的核心动作**：每个分片返回本地 top N（LIMIT N 不是 LIMIT offset+N —— 分片内也要取够排序数据），协调层做**多路归并**取全局 top N。K 路归并成本 O(K×N)，K 是分片数。第 34 章的堆在这里直接复用：K 个流各出队首进小顶堆。
 *   - **分页归并的陷阱**：全局 `LIMIT 1000, 20` 时每分片要取前 1020 条，页越深每分片成本越高——**跨分片分页继承第 39-03 课的全部问题**。游标分页（全局唯一排序键）在分片场景同样成本恒定，是首选。
 *   - **聚合归并的语义**：COUNT/SUM/MAX 可直接分片值相加/比较（可分解）；AVG 不能直接平均（要 SUM 与 COUNT 分别汇总再算）；COUNT DISTINCT 与 MEDIAN **不可分解**，必须收集明细在协调层重算，数据量大时只能用近似或异构副本。
 *   - **GROUP BY 归并**：每分片按组返回（group, aggValue），协调层按组合并（SUM 相加、AVG 用两元组）。组数多时协调层内存压力上升，组数爆炸的报表应走异构（同步到数仓/ES）而不是实时广播聚合。
 *   - **order by 全局唯一性问题**：按 create_time 排序时各分片时钟独立、同毫秒冲突，归并顺序不稳定——与 search_after 相同，**全局排序键必须附加唯一列（id）兜底**。
 *   - 易错点：以为 `LIMIT 10` 在每个分片取 10 条就够了（排序不均匀时会漏，必须每分片取够 N 且在协调层重排序）；AVG 各分片平均后平均（数学错误）；把管理后台的全量查询直接打在生产分库（应该打异构副本）；跨分片 JOIN 硬上（中间件大多退化为内存 join，先把数据冗余到一起再查）。
 * ============================================================
 */

package com.heima.ch39;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

// ==================== 示例代码 ====================

public class CrossShardQuery {
    record Order(long orderId, long userId, double amount, long createTime) {
    }

    /** 4 个分片的本地数据（模拟同构表） */
    static List<List<Order>> shards() {
        List<List<Order>> s = new ArrayList<>();
        s.add(List.of(new Order(11, 1, 200, 1001), new Order(12, 2, 350, 1005)));
        s.add(List.of(new Order(21, 3, 500, 1003), new Order(22, 1, 90, 1007)));
        s.add(List.of(new Order(31, 2, 120, 1002), new Order(32, 4, 800, 1006)));
        s.add(List.of(new Order(41, 5, 30, 1004), new Order(42, 3, 660, 1008)));
        return s;
    }

    static void topNMerge() {
        System.out.println("【全局 top 3 金额：K 路归并】");
        List<List<Order>> shards = shards();
        record Entry(Order o, int shard) {
        }
        PriorityQueue<Entry> heap = new PriorityQueue<>(
                Comparator.comparingDouble((Entry e) -> e.o().amount()).reversed());
        // 每个分片本地排序取 top3（真实 SQL：ORDER BY amount DESC LIMIT 3）
        for (int i = 0; i < shards.size(); i++) {
            List<Order> localTop = shards.get(i).stream()
                    .sorted(Comparator.comparingDouble(Order::amount).reversed())
                    .limit(3).toList();
            System.out.printf("  分片%d 本地top: %s%n", i, localTop.stream().map(o -> o.orderId()).toList());
            if (!localTop.isEmpty()) {
                heap.add(new Entry(localTop.get(0), i));
            }
        }
        List<Order> globalTop = new ArrayList<>();
        List<List<Order>> cursors = new ArrayList<>();
        for (int i = 0; i < shards.size(); i++) {
            cursors.add(shards.get(i).stream()
                    .sorted(Comparator.comparingDouble(Order::amount).reversed()).toList());
        }
        while (globalTop.size() < 3 && !heap.isEmpty()) {
            Entry e = heap.poll();
            globalTop.add(e.o());
            int next = cursors.get(e.shard()).indexOf(e.o()) + 1;
            if (next < cursors.get(e.shard()).size()) {
                heap.add(new Entry(cursors.get(e.shard()).get(next), e.shard()));
            }
        }
        System.out.println("  全局 top3: " + globalTop.stream().map(Order::orderId).toList());
        System.out.println("  关键：每分片本地取 LIMIT N（不是 offset+N），协调层归并重排");
        System.out.println();
    }

    static void aggregations() {
        System.out.println("【聚合归并的可分解性】");
        long count = 0;
        double sum = 0, naiveAvgSum = 0;
        Map<Long, Double> sumByUser = new LinkedHashMap<>();
        Map<Long, Long> cntByUser = new LinkedHashMap<>();
        for (List<Order> s : shards()) {
            long c = s.size();
            double sm = s.stream().mapToDouble(Order::amount).sum();
            count += c;
            sum += sm;
            naiveAvgSum += sm / c;                    // 错误做法：平均的平均
            for (Order o : s) {
                sumByUser.merge(o.userId(), o.amount(), Double::sum);
                cntByUser.merge(o.userId(), 1L, Long::sum);
            }
        }
        System.out.printf("  COUNT：分片相加 = %d ✓%n", count);
        System.out.printf("  SUM  ：分片相加 = %.0f ✓%n", sum);
        System.out.printf("  AVG  ：先汇总 SUM/COUNT 再除 = %.1f；平均的平均 = %.1f ✗%n",
                sum / count, naiveAvgSum);
        System.out.println("  COUNT DISTINCT / MEDIAN：不可分解 -> 收集明细重算或近似");
        System.out.println("  GROUP BY 用户：");
        for (Long u : sumByUser.keySet()) {
            System.out.printf("    user %d: sum=%.0f count=%d%n", u, sumByUser.get(u), cntByUser.get(u));
        }
        System.out.println();
    }

    static void pagingRule() {
        System.out.println("【跨分片分页：每分片必须取 offset+N】");
        int offset = 1000, limit = 20;
        System.out.printf("  全局 LIMIT %d, %d -> 每分片取前 %d 条，4 分片共 %d 条%n",
                offset, limit, offset + limit, (offset + limit) * 4);
        System.out.println("  深页成本 = 分片数 × 页深，继承 39-03 的所有问题");
        System.out.println("  首选：全局游标（create_time+id 唯一键），每分片 WHERE 键 > 游标 LIMIT N");
        System.out.println("  兜底：后台深翻页走异构副本（ES/数仓），不占交易库");
    }

    public static void main(String[] args) {
        topNMerge();
        aggregations();
        pagingRule();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 topNMerge 升级为"全局第 2 页（offset=3, limit=3）"，写出每分片需要取多少条，并用数据验证归并结果正确。
 * 2. 实现一个跨分片 GROUP BY（按用户 SUM/COUNT）+ 全局排序取 top2 用户，解释为什么 AVG 必须带 COUNT 归并。
 * 3. 思考题：COUNT DISTINCT 在协调层重算的内存上限如何估算？什么规模后必须改用近似算法或异构副本？
 * ============================================================
 */
