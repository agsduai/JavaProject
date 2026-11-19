/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-08
 * 课名：路径规划基础：图与最短路
 * ------------------------------------------------------------
 * 知识点：
 *   - **路网就是一张图**：交叉口是顶点、道路是带权边（权重=距离/时间）——**路径规划 = 图上的最短路问题**（34 章的图论在 LBS 的应用）。
 *   - **三种最短路算法**：**Dijkstra**（单源最短路，非负权，用优先队列 O((V+E)logV)）；**A\***（Dijkstra + 启发式（直线距离下界），目标明确时更快）；**Floyd**（全源，O(V³)，小图或预处理用）。
 *   - **A\* 的启发函数是关键**：**必须"可采纳"（不高估真实距离）**——欧氏/直线距离是合法启发；**高估会导致次优路径**（如用 0.5×直线距离）。
 *   - **工程化的四个问题**：**图太大**（全国路网千万节点 -> 分层图/预处理（Contraction Hierarchies））、**动态权重**（实时路况 -> 定期重算/分层缓存）、**多目标**（最短/最快/少收费 -> 多权重）、**批量查询**（算路缓存）。
 *   - **与 34 章的关系**：34 章讲 Dijkstra 的原理，本章讲"路网建模 + 启发式 + 工程约束"——**从算法到服务**。
 *   - 易错点：A\* 启发函数高估（次优解）；权重为负（Dijkstra 失效，需 Bellman-Ford）；图未考虑单向道路（导航逆行）；每次查询都跑全图（无预处理/缓存）。
 * ============================================================
 */

package com.heima.ch67;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

// ==================== 示例代码 ====================

public class PathPlanning {
    record Edge(String to, double weight) {
    }

    static final Map<String, List<Edge>> graph = new LinkedHashMap<>();
    static final Map<String, double[]> coords = new LinkedHashMap<>();

    static void addEdge(String from, String to, double km) {
        graph.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, km));
        graph.computeIfAbsent(to, k -> new ArrayList<>());
    }

    static void addCoord(String node, double x, double y) {
        coords.put(node, new double[]{x, y});
    }

    /** Dijkstra：优先队列 + 距离表 */
    static Map<String, Object> dijkstra(String start, String end) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(
                n -> dist.getOrDefault(n, Double.MAX_VALUE)));
        dist.put(start, 0.0);
        pq.add(start);
        int visited = 0;
        while (!pq.isEmpty()) {
            String cur = pq.poll();
            visited++;
            if (cur.equals(end)) {
                break;
            }
            for (Edge e : graph.getOrDefault(cur, List.of())) {
                double nd = dist.getOrDefault(cur, Double.MAX_VALUE) + e.weight();
                if (nd < dist.getOrDefault(e.to(), Double.MAX_VALUE)) {
                    dist.put(e.to(), nd);
                    prev.put(e.to(), cur);
                    pq.add(e.to());
                }
            }
        }
        return Map.of("dist", dist.getOrDefault(end, Double.MAX_VALUE),
                "path", path(prev, start, end), "visited", visited);
    }

    /** A*：Dijkstra + 直线距离启发 */
    static Map<String, Object> aStar(String start, String end) {
        Map<String, Double> g = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(
                n -> g.getOrDefault(n, Double.MAX_VALUE) + h(n, end)));
        g.put(start, 0.0);
        pq.add(start);
        int visited = 0;
        while (!pq.isEmpty()) {
            String cur = pq.poll();
            visited++;
            if (cur.equals(end)) {
                break;
            }
            for (Edge e : graph.getOrDefault(cur, List.of())) {
                double ng = g.getOrDefault(cur, Double.MAX_VALUE) + e.weight();
                if (ng < g.getOrDefault(e.to(), Double.MAX_VALUE)) {
                    g.put(e.to(), ng);
                    prev.put(e.to(), cur);
                    pq.add(e.to());
                }
            }
        }
        return Map.of("dist", g.getOrDefault(end, Double.MAX_VALUE),
                "path", path(prev, start, end), "visited", visited);
    }

    /** 启发函数：两点直线距离（欧氏，可采纳） */
    static double h(String from, String to) {
        double[] a = coords.get(from);
        double[] b = coords.get(to);
        if (a == null || b == null) {
            return 0;
        }
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2));
    }

    static List<String> path(Map<String, String> prev, String start, String end) {
        List<String> out = new ArrayList<>();
        String cur = end;
        while (cur != null) {
            out.add(0, cur);
            if (cur.equals(start)) {
                break;
            }
            cur = prev.get(cur);
        }
        return out;
    }

    public static void main(String[] args) {
        // 简化路网（带坐标，用于启发函数）
        addCoord("A", 0, 0);
        addCoord("B", 2, 0);
        addCoord("C", 4, 0);
        addCoord("D", 1, 2);
        addCoord("E", 3, 2);
        addCoord("F", 4, 4);
        addEdge("A", "B", 2);
        addEdge("B", "C", 2);
        addEdge("A", "D", 2.5);
        addEdge("D", "E", 2.2);
        addEdge("E", "C", 2.4);
        addEdge("C", "F", 4);
        addEdge("E", "F", 2.2);

        System.out.println("【Dijkstra：A -> F】");
        Map<String, Object> d = dijkstra("A", "F");
        System.out.println("  距离=" + d.get("dist") + " 路径=" + d.get("path")
                + " 访问节点=" + d.get("visited"));

        System.out.println();
        System.out.println("【A*：同样的图 + 直线距离启发】");
        Map<String, Object> a = aStar("A", "F");
        System.out.println("  距离=" + a.get("dist") + " 路径=" + a.get("path")
                + " 访问节点=" + a.get("visited"));

        System.out.println();
        System.out.println("【对比】");
        System.out.println("  两者距离一致（启发可采纳）-> " + d.get("dist").equals(a.get("dist")));
        System.out.println("  A* 访问节点更少（有方向性）-> 大图上优势更明显");

        System.out.println();
        System.out.println("【工程约束】");
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("单向道路", "边要有方向（有向图）");
        notes.put("实时路况", "权重动态更新，缓存要带 TTL");
        notes.put("大图", "分层/预处理（CH），单次查询毫秒级");
        notes.put("多目标", "最短/最快/少收费 -> 多套权重");
        notes.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Dijkstra 与 A\*，用 20 节点路网对比"访问节点数"与"最终距离"，验证启发函数可采纳时结果一致。
 * 2. 构造一个"启发函数高估"（用 1.5× 直线距离）的 A\*，验证得到次优路径，说明可采纳性要求。
 * 3. 思考题：为什么全国路网不能每次查询都跑 Dijkstra？从"节点规模"与"实时性要求"两个角度分析，并给出分层预处理的思路。
 * ============================================================
 */
