/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-11
 * 课名：综合案例：订单数据离线加工链
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一条完整的离线加工链**：ODS（raw 订单事件）-> Spark 清洗（窄依赖 map/filter，59-02）-> 按城市/品类聚合（宽依赖 groupBy + Shuffle，59-05）-> 倾斜处理（加盐两阶段，59-08）-> 写 DWS 分区（分区覆盖幂等，59-10）-> 与维表 join（Broadcast，59-07）-> 输出报表。
 *   - **性能预算与调优点**：读 20GB -> 过滤后 8GB（59-03 先过滤）-> 聚合 Shuffle 2GB（combiner 预聚合）-> 输出 40 个文件（128MB 目标，59-04）；**每一步都有可量化的开销**，优化要对准最大项。
 *   - **正确性验收**：与串行实现结果对拍（33 章思想）、重跑幂等（两次结果一致）、质量校验（行数/金额合计 46-08）——**Spark 作业的验收不是"跑通了"，是"跑对了且可复现"**。
 *   - **运维要点**：作业名与标签（可追溯）、失败重试与告警（42 章）、Shuffle 临时目录磁盘水位、输出小文件监控。
 *   - 易错点：先聚合后过滤（Shuffle 量爆炸）；输出用 append 导致重跑翻倍；倾斜不处理（Stage 卡在 1 个 Task）；无质量校验（数据错了当成功）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class OfflinePipelineCase {
    record Order(long id, String city, String category, long amountCents, long ts) {
    }

    record Stat(String city, String category, long orders, long gmvCents) {
    }

    /** 完整加工链：过滤 -> 局部聚合（模拟 combiner） -> 全局聚合 -> 输出 */
    static List<Stat> pipeline(List<Order> raw, long minAmountCents) {
        // 1) 窄依赖：过滤（下推到读取阶段）
        List<Order> filtered = raw.stream()
                .filter(o -> o.amountCents() >= minAmountCents)
                .toList();
        System.out.printf("  1) 过滤：%d -> %d 条（窄依赖，无 Shuffle）%n", raw.size(), filtered.size());

        // 2) Map 端预聚合（combiner 思想，减少 Shuffle 量）
        Map<String, long[]> local = new LinkedHashMap<>();
        for (Order o : filtered) {
            String key = o.city() + "|" + o.category();
            long[] agg = local.computeIfAbsent(key, k -> new long[2]);
            agg[0] += 1;
            agg[1] += o.amountCents();
        }
        System.out.printf("  2) Map 端预聚合：%d 条 -> %d 个局部聚合结果%n",
                filtered.size(), local.size());

        // 3) 全局聚合（宽依赖，Shuffle）
        Map<String, long[]> global = new LinkedHashMap<>();
        local.forEach((key, agg) -> {
            long[] g = global.computeIfAbsent(key, k -> new long[2]);
            g[0] += agg[0];
            g[1] += agg[1];
        });
        System.out.printf("  3) 全局聚合：%d 个结果（Shuffle 移动量已由预聚合缩小）%n", global.size());

        // 4) 输出
        List<Stat> out = new ArrayList<>();
        global.forEach((key, agg) -> {
            String[] parts = key.split("\\|");
            out.add(new Stat(parts[0], parts[1], agg[0], agg[1]));
        });
        return out;
    }

    public static void main(String[] args) {
        List<Order> raw = new ArrayList<>();
        String[] cities = {"杭州", "上海", "北京"};
        String[] categories = {"phone", "laptop", "stand"};
        for (int i = 0; i < 5000; i++) {
            raw.add(new Order(i, cities[i % 3], categories[i % 3],
                    (i % 50) * 1000L + 500, i));
        }
        System.out.println("【订单离线加工链（5000 条样本）】");
        List<Stat> stats = pipeline(raw, 10_000);

        System.out.println();
        System.out.println("【DWS 输出样本（前 4 行）】");
        stats.stream().limit(4).forEach(s -> System.out.printf(
                "  %-4s %-7s 订单%5d GMV=%d 分%n", s.city(), s.category(), s.orders(), s.gmvCents()));

        // 正确性对拍：串行直接聚合
        Map<String, long[]> naive = new LinkedHashMap<>();
        for (Order o : raw) {
            if (o.amountCents() < 10_000) {
                continue;
            }
            long[] agg = naive.computeIfAbsent(o.city() + "|" + o.category(), k -> new long[2]);
            agg[0]++;
            agg[1] += o.amountCents();
        }
        boolean equal = stats.size() == naive.size() && stats.stream().allMatch(s ->
                naive.containsKey(s.city() + "|" + s.category())
                        && naive.get(s.city() + "|" + s.category())[0] == s.orders());
        System.out.println();
        System.out.println("【对拍结果】管道结果 == 串行结果：" + equal);
        System.out.println("【重跑幂等】分区覆盖写入，二次执行结果一致（59-10）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展加工链：加入"按日分区"与"倾斜 key（爆款）"的处理，输出各阶段数据量与耗时估算。
 * 2. 为这条链设计验收清单（对拍/幂等/质量校验/文件数/耗时基线），并逐项给出验证方法。
 * 3. 思考题：为什么"对拍"是离线作业上线前的必做项？从"分布式实现易错点（聚合/去重/类型）"与"数据错误的下游成本"分析。
 * ============================================================
 */
