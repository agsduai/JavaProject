/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：61-04
 * 课名：任务依赖与 DAG 编排
 * ------------------------------------------------------------
 * 知识点：
 *   - **DAG（有向无环图）是任务编排的核心表达**：节点=任务，边=依赖（`A -> B` 表示 B 依赖 A）。**无环是硬约束**（有环 = 死锁，编译期/注册期就要检测）。
 *   - **依赖的两种粒度**：**任务级**（A 成功 -> 触发 B）与**分片级**（A 的所有分片成功才触发 B）——**跨天依赖**（今天的任务依赖昨天的同任务完成）是数仓最常见形态（46-01 分层调度）。
 *   - **DAG 的执行语义**：**拓扑排序**决定执行顺序；同一层的节点可并行（受资源限制）；**上游失败的处理**：下游跳过（不跑）或标记"等待补数"（跑批场景通常跳过 + 告警，补数后手动触发）。
 *   - **关键路径与 SLA 保证**：DAG 的总耗时 = 关键路径长度（59-03 的调度版）——**想让报表 9 点出，就要反推关键路径的最晚启动时间**（`SLA - 关键路径耗时 = 最晚开始`），并给路径上的任务设"超时会拖累 SLA"的告警。
 *   - **依赖的配置与版本**：依赖关系存数据库（可查可改），**变更要走评审**（改错依赖 -> 全链路数据错位）；**依赖成环检测**在保存时做（DFS 检测）。
 *   - 易错点：DAG 有环（保存时应拒绝）；跨天依赖没配（下游读到不完整数据，46-08 的质量规则拦不住）；上游失败下游照样跑（用旧数据算出错结果）；关键路径无 SLA 告警（日报迟到才发现）。
 * ============================================================
 */

package com.heima.ch61;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DagOrchestration {
    /** 邻接表表示 DAG */
    static final Map<String, List<String>> graph = new LinkedHashMap<>();

    static void addTask(String name, String... deps) {
        graph.putIfAbsent(name, new ArrayList<>());
        for (String d : deps) {
            graph.computeIfAbsent(d, k -> new ArrayList<>()).add(name);
        }
    }

    /** DFS 检测环 */
    static boolean hasCycle() {
        Map<String, Integer> color = new HashMap<>();     // 0 未访问 1 访问中 2 已完成
        for (String node : graph.keySet()) {
            if (color.getOrDefault(node, 0) == 0 && dfs(node, color)) {
                return true;
            }
        }
        return false;
    }

    static boolean dfs(String node, Map<String, Integer> color) {
        color.put(node, 1);
        for (String next : graph.getOrDefault(node, List.of())) {
            int c = color.getOrDefault(next, 0);
            if (c == 1) {
                return true;                               // 回到访问中的节点 = 环
            }
            if (c == 0 && dfs(next, color)) {
                return true;
            }
        }
        color.put(node, 2);
        return false;
    }

    /** 拓扑排序 + 分层（同层可并行） */
    static List<List<String>> layers() {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        graph.keySet().forEach(k -> indegree.put(k, 0));
        graph.values().forEach(list -> list.forEach(n -> indegree.merge(n, 1, Integer::sum)));

        List<List<String>> result = new ArrayList<>();
        List<String> frontier = new ArrayList<>();
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() == 0) {
                frontier.add(e.getKey());
            }
        }
        while (!frontier.isEmpty()) {
            result.add(new ArrayList<>(frontier));
            List<String> next = new ArrayList<>();
            for (String node : frontier) {
                for (String child : graph.getOrDefault(node, List.of())) {
                    int d = indegree.merge(child, -1, Integer::sum);
                    if (d == 0) {
                        next.add(child);
                    }
                }
            }
            frontier = next;
        }
        return result;
    }

    public static void main(String[] args) {
        addTask("ods_orders_sync");
        addTask("dwd_orders_clean", "ods_orders_sync");
        addTask("dwd_payments_clean", "ods_orders_sync");
        addTask("dws_city_gmv", "dwd_orders_clean");
        addTask("dws_channel_gmv", "dwd_payments_clean");
        addTask("ads_daily_report", "dws_city_gmv", "dws_channel_gmv");

        System.out.println("【DAG 结构】");
        graph.forEach((k, v) -> System.out.printf("  %-18s -> %s%n", k, v));
        System.out.println();
        System.out.println("【环检测】有环=" + hasCycle());
        System.out.println();
        System.out.println("【拓扑分层（同层可并行）】");
        List<List<String>> ls = layers();
        for (int i = 0; i < ls.size(); i++) {
            System.out.printf("  L%d：%s%n", i, ls.get(i));
        }
        System.out.println("  关键路径长度 = " + ls.size() + " 层 -> 决定 DAG 最短完成时间");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展 DAG 引擎：支持"节点耗时"参数，计算关键路径与每层可并行度，输出"9 点前出报表的最晚启动时间"。
 * 2. 实现"跨天依赖"：今天的任务依赖昨天同任务成功，构造"昨天失败 -> 今天自动跳过并告警"的逻辑。
 * 3. 思考题：为什么"上游失败下游跳过"比"下游继续跑"更安全？从"用旧/缺数据算出错误结果"与质量规则的拦截边界分析。
 * ============================================================
 */
