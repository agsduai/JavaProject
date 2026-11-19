/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：61-03
 * 课名：任务分片与并行执行
 * ------------------------------------------------------------
 * 知识点：
 *   - **分片是"把一个大数据量任务拆给多个执行器并行跑"**：总片数固定（如 8），执行器数量可变（如 3 台）——**每台执行器拿到的分片数 = 8 ÷ 3 上取整**，跑不完的由后续执行器补（或按"分片数 = 执行器数"动态调）。
 *   - **分片参数的下发**：调度中心把 `(分片序号, 总片数)` 传给执行器，**执行器按"取模"或"范围"切分数据**——如 `WHERE id % totalShards = shardIndex`，或按 id 区间。**两种切法的性能差异巨大**（取模均匀但索引效果差，区间索引好但可能倾斜）。
 *   - **分片后的结果汇总**：分片执行各自写结果（分表/分文件），**汇总阶段在调度中心或下游聚合**——汇总也要幂等（61-05），失败重跑只重跑失败的分片。
 *   - **动态分片（弹性）**：执行器扩缩容时，**分片重新分配**（ElasticJob 的再平衡）——扩一台机器，分片自动从其他机器挪走一部分；**缩容时未完成的分片要重新派发**（不能丢）。
 *   - **分片与数据倾斜**（59-08 的调度版）：按 id 取模均匀但**按业务键分片可能倾斜**（大客户数据集中）；**分片粒度太粗**（4 片跑 1 亿）-> 单分片慢；**太细**（1000 片）-> 调度与汇总开销大。
 *   - 易错点：分片数与执行器数固定绑定（扩容无意义）；分片不幂等（重跑重复写）；分片失败不重派（数据缺一块）；汇总阶段重复计算（分片已成功又重跑）。
 * ============================================================
 */

package com.heima.ch61;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ShardingJob {
    record ShardAssignment(String executor, List<Integer> shards) {
        int count() {
            return shards.size();
        }
    }

    /** 静态分片：总片数固定，按执行器数均分（余数给前面的） */
    static Map<String, List<Integer>> assign(List<String> executors, int totalShards) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        int base = totalShards / executors.size();
        int remain = totalShards % executors.size();
        int shard = 0;
        for (int i = 0; i < executors.size(); i++) {
            List<Integer> mine = new ArrayList<>();
            int n = base + (i < remain ? 1 : 0);
            for (int j = 0; j < n; j++) {
                mine.add(shard++);
            }
            map.put(executors.get(i), mine);
        }
        return map;
    }

    /** 取模切分：分片号 = id % totalShards */
    static int shardByMod(long id, int totalShards) {
        return (int) (id % totalShards);
    }

    /** 范围切分：按 id 区间 */
    static int shardByRange(long id, long maxId, int totalShards) {
        long span = Math.max(1, maxId / totalShards);
        int idx = (int) (id / span);
        return Math.min(totalShards - 1, idx);
    }

    static void compareSplit(int totalShards, long maxId, int samples) {
        Map<Integer, Integer> modDist = new LinkedHashMap<>();
        Map<Integer, Integer> rangeDist = new LinkedHashMap<>();
        for (int i = 0; i < samples; i++) {
            long id = i + 1L;
            modDist.merge(shardByMod(id, totalShards), 1, Integer::sum);
            rangeDist.merge(shardByRange(id, maxId, totalShards), 1, Integer::sum);
        }
        System.out.printf("  取模分布：%s%n", modDist);
        System.out.printf("  范围分布：%s%n", rangeDist);
    }

    public static void main(String[] args) {
        System.out.println("【分片分配：8 片 / 3 执行器】");
        assign(List.of("exec-1", "exec-2", "exec-3"), 8)
                .forEach((k, v) -> System.out.printf("  %s -> %s (%d 片)%n", k, v, v.size()));
        System.out.println();
        System.out.println("【扩容到 4 执行器：再平衡】");
        assign(List.of("exec-1", "exec-2", "exec-3", "exec-4"), 8)
                .forEach((k, v) -> System.out.printf("  %s -> %s (%d 片)%n", k, v, v.size()));
        System.out.println();
        System.out.println("【两种切分方式的数据分布（200 条 / 4 片）】");
        compareSplit(4, 200, 200);
        System.out.println("  取模：均匀但索引利用率差；范围：索引好但大 id 段可能倾斜");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现分片调度器：支持静态分片与"执行器扩缩容再平衡"，验证扩容后分片不丢不重。
 * 2. 用 100 万模拟 id 对比取模与范围切分的"单分片最大负载"，并模拟"大客户 id 集中"场景观察倾斜。
 * 3. 思考题：为什么"分片任务的汇总阶段"也需要幂等？构造"汇总跑两次导致金额翻倍"的场景。
 * ============================================================
 */
