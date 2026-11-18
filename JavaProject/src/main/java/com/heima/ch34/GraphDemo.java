/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-08
 * 课名：图：邻接表、BFS/DFS 与拓扑排序
 * ------------------------------------------------------------
 * 知识点：
 *   - 图的两种存法：**邻接矩阵**（`n×n`，查边 `O(1)`，空间 `O(n²)`，适合稠密图）和**邻接表**（每个点挂一个邻居列表，空间 `O(n+m)`，遍历邻居快，适合稀疏图）。工程上 99% 用邻接表。
 *   - **BFS** 用队列，按"距离起点的层数"扩散，所以**无权图的最短路径**就是 BFS 第一次到达的层数。**DFS** 用栈（或递归），一条路走到黑，适合找连通分量、判环、拓扑排序。
 *   - 两者都要 **visited 标记**，否则有环图会死循环。BFS 在**入队时**标记（避免同一节点重复入队），DFS 在**访问时**标记。
 *   - **连通分量**：对每个未访问节点做一次 DFS/BFS，次数就是分量个数。
 *   - **拓扑排序**（有向无环图 DAG）两种实现：**Kahn 算法**（统计入度，入度为 0 的入队，出队时把邻居入度减 1）和 **DFS 后序逆序**。Kahn 的副产品是**能检测环**：如果输出节点数 < 总数，说明有环。
 *   - **拓扑排序的实际用途**：任务依赖调度、构建系统（Maven 模块依赖）、课程先修关系、包管理器依赖解析。出现环就是"循环依赖"，必须报错。
 *   - **Dijkstra** 是 BFS 的加权版：用优先队列每次取当前距离最小的节点，要求**边权非负**。有负权要用 Bellman-Ford。
 *   - **并查集（Union-Find）** 解决"连通性"问题：`find` 带路径压缩 + `union` 按秩合并，均摊接近 `O(1)`。用于判环、Kruskal 最小生成树、朋友圈合并。
 *   - 易错：BFS 用 `ArrayList` 当队列（`remove(0)` 是 `O(n)`）；有环图忘记 visited 导致栈溢出；拓扑排序只统计入度忘了处理"多个起点"；`Dijkstra` 用 `visited` 提前返回导致重复入队节点处理错误。
 * ============================================================
 */

package com.heima.ch34;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

// ==================== 示例代码 ====================

public class GraphDemo {
    /** 邻接表有向图，节点用字符串表示 */
    static final class Graph {
        private final Map<String, List<String>> adj = new HashMap<>();
        private final Map<String, Integer> indegree = new HashMap<>();

        void addNode(String v) {
            adj.putIfAbsent(v, new ArrayList<>());
            indegree.putIfAbsent(v, 0);
        }

        void addEdge(String from, String to) {
            addNode(from);
            addNode(to);
            adj.get(from).add(to);
            indegree.merge(to, 1, Integer::sum);
        }

        void addUndirectedEdge(String a, String b) {
            addEdge(a, b);
            addEdge(b, a);
        }

        int nodeCount() {
            return adj.size();
        }

        int edgeCount() {
            return adj.values().stream().mapToInt(List::size).sum();
        }

        /** BFS：返回从 start 到各点的最短边数 */
        Map<String, Integer> bfs(String start) {
            Map<String, Integer> dist = new HashMap<>();
            if (!adj.containsKey(start)) {
                return dist;
            }
            Deque<String> queue = new ArrayDeque<>();
            dist.put(start, 0);
            queue.offer(start);
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                for (String next : adj.get(cur)) {
                    if (!dist.containsKey(next)) {
                        dist.put(next, dist.get(cur) + 1);
                        queue.offer(next);
                    }
                }
            }
            return dist;
        }

        /** DFS 迭代版：显式栈，避免深图栈溢出 */
        List<String> dfs(String start) {
            List<String> order = new ArrayList<>();
            if (!adj.containsKey(start)) {
                return order;
            }
            Deque<String> stack = new ArrayDeque<>();
            java.util.Set<String> visited = new java.util.HashSet<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                String cur = stack.pop();
                if (!visited.add(cur)) {
                    continue;
                }
                order.add(cur);
                List<String> neighbors = adj.get(cur);
                for (int i = neighbors.size() - 1; i >= 0; i--) {
                    if (!visited.contains(neighbors.get(i))) {
                        stack.push(neighbors.get(i));
                    }
                }
            }
            return order;
        }

        /** Kahn 拓扑排序；有环时返回的列表短于节点数 */
        List<String> topoSortKahn() {
            Map<String, Integer> deg = new HashMap<>(indegree);
            Deque<String> queue = new ArrayDeque<>();
            for (Map.Entry<String, Integer> e : deg.entrySet()) {
                if (e.getValue() == 0) {
                    queue.offer(e.getKey());
                }
            }
            List<String> order = new ArrayList<>();
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                order.add(cur);
                for (String next : adj.get(cur)) {
                    int d = deg.merge(next, -1, Integer::sum);
                    if (d == 0) {
                        queue.offer(next);
                    }
                }
            }
            return order;
        }

        /** DFS 后序逆序拓扑排序 */
        List<String> topoSortDfs() {
            List<String> order = new ArrayList<>();
            java.util.Set<String> visited = new java.util.HashSet<>();
            for (String v : adj.keySet()) {
                if (!visited.contains(v)) {
                    dfsPost(v, visited, order);
                }
            }
            java.util.Collections.reverse(order);
            return order;
        }

        private void dfsPost(String v, java.util.Set<String> visited, List<String> order) {
            visited.add(v);
            for (String next : adj.get(v)) {
                if (!visited.contains(next)) {
                    dfsPost(next, visited, order);
                }
            }
            order.add(v);
        }

        int connectedComponents() {
            java.util.Set<String> visited = new java.util.HashSet<>();
            int count = 0;
            for (String v : adj.keySet()) {
                if (!visited.contains(v)) {
                    count++;
                    Deque<String> stack = new ArrayDeque<>();
                    stack.push(v);
                    while (!stack.isEmpty()) {
                        String cur = stack.pop();
                        if (!visited.add(cur)) {
                            continue;
                        }
                        for (String next : adj.get(cur)) {
                            if (!visited.contains(next)) {
                                stack.push(next);
                            }
                        }
                    }
                }
            }
            return count;
        }

        boolean hasCycleDirected() {
            return topoSortKahn().size() < adj.size();
        }
    }

    /** 带权图 + Dijkstra */
    static final class WeightedGraph {
        record Edge(String to, int weight) { }

        private final Map<String, List<Edge>> adj = new HashMap<>();

        void addEdge(String from, String to, int w) {
            adj.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, w));
            adj.computeIfAbsent(to, k -> new ArrayList<>());
        }

        /** 返回起点到各点的最短距离；不可达的点不出现在结果里 */
        Map<String, Integer> dijkstra(String start) {
            Map<String, Integer> dist = new HashMap<>();
            record Node(String v, int d) { }
            PriorityQueue<Node> pq = new PriorityQueue<>((x, y) -> Integer.compare(x.d(), y.d()));
            dist.put(start, 0);
            pq.offer(new Node(start, 0));
            java.util.Set<String> settled = new java.util.HashSet<>();
            while (!pq.isEmpty()) {
                Node cur = pq.poll();
                if (!settled.add(cur.v())) {
                    continue;
                }
                for (Edge e : adj.getOrDefault(cur.v(), List.of())) {
                    int nd = cur.d() + e.weight();
                    if (nd < dist.getOrDefault(e.to(), Integer.MAX_VALUE)) {
                        dist.put(e.to(), nd);
                        pq.offer(new Node(e.to(), nd));
                    }
                }
            }
            return dist;
        }
    }

    /** 并查集：路径压缩 + 按秩合并 */
    static final class UnionFind {
        private final int[] parent;
        private final int[] rank;
        private int components;

        UnionFind(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
            components = n;
        }

        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        boolean union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return false;
            }
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra;
            } else {
                parent[rb] = ra;
                rank[ra]++;
            }
            components--;
            return true;
        }

        int components() {
            return components;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 有向图 + BFS 最短边数 ===");
        Graph g = new Graph();
        g.addEdge("A", "B");
        g.addEdge("A", "C");
        g.addEdge("B", "D");
        g.addEdge("C", "D");
        g.addEdge("D", "E");
        System.out.println("  节点数=" + g.nodeCount() + " 边数=" + g.edgeCount());
        System.out.println("  从 A 出发的 BFS 距离: " + g.bfs("A"));
        System.out.println("  从 C 出发的 BFS 距离: " + g.bfs("C"));

        System.out.println();
        System.out.println("=== 2. DFS 遍历顺序 ===");
        System.out.println("  DFS(A) = " + g.dfs("A"));
        System.out.println("  （BFS 按层，DFS 一条路走到底，顺序不同但都覆盖全部可达点）");

        System.out.println();
        System.out.println("=== 3. 拓扑排序：任务依赖 ===");
        Graph tasks = new Graph();
        tasks.addEdge("解析需求", "设计接口");
        tasks.addEdge("设计接口", "写代码");
        tasks.addEdge("设计接口", "写文档");
        tasks.addEdge("写代码", "单元测试");
        tasks.addEdge("写文档", "评审");
        tasks.addEdge("单元测试", "评审");
        System.out.println("  Kahn: " + tasks.topoSortKahn());
        System.out.println("  DFS : " + tasks.topoSortDfs());
        System.out.println("  两者都是合法拓扑序：每个任务都排在其依赖之后。");

        System.out.println();
        System.out.println("=== 4. 环检测 ===");
        Graph cyclic = new Graph();
        cyclic.addEdge("A", "B");
        cyclic.addEdge("B", "C");
        cyclic.addEdge("C", "A");
        System.out.println("  A->B->C->A 的拓扑序长度=" + cyclic.topoSortKahn().size()
                + "，节点数=" + cyclic.nodeCount() + "，hasCycle=" + cyclic.hasCycleDirected());
        System.out.println("  （拓扑序不完整就说明剩下的点都在环里）");

        System.out.println();
        System.out.println("=== 5. 连通分量（无向图）===");
        Graph undirected = new Graph();
        undirected.addUndirectedEdge("1", "2");
        undirected.addUndirectedEdge("2", "3");
        undirected.addUndirectedEdge("5", "6");
        undirected.addNode("9");
        System.out.println("  节点数=" + undirected.nodeCount()
                + "，连通分量数=" + undirected.connectedComponents() + "（期望 3）");

        System.out.println();
        System.out.println("=== 6. Dijkstra 最短路 ===");
        WeightedGraph wg = new WeightedGraph();
        wg.addEdge("北京", "天津", 120);
        wg.addEdge("北京", "石家庄", 280);
        wg.addEdge("天津", "济南", 300);
        wg.addEdge("石家庄", "郑州", 410);
        wg.addEdge("济南", "郑州", 380);
        wg.addEdge("郑州", "武汉", 520);
        Map<String, Integer> dist = wg.dijkstra("北京");
        System.out.println("  从北京出发的最短距离:");
        dist.forEach((city, d) -> System.out.println("    " + city + " = " + d + " km"));
        System.out.println("  注意：BFS 只能算无权图，带权图必须用 Dijkstra（边权不能为负）。");

        System.out.println();
        System.out.println("=== 7. 并查集判环 ===");
        int[][] edges = {{0, 1}, {1, 2}, {2, 0}, {3, 4}};
        UnionFind uf = new UnionFind(5);
        for (int[] e : edges) {
            if (!uf.union(e[0], e[1])) {
                System.out.println("  加入边 " + Arrays.toString(e) + " 时发现环");
            }
        }
        System.out.println("  最终连通分量数=" + uf.components());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"岛屿数量"：给定 `char[][]` 的 `'1'`/`'0'` 网格，用 DFS 或 BFS 统计连通块个数，并对比两种写法的代码量。
 * 2. 用拓扑排序实现"课程表"：给定 `numCourses` 与先修关系数组，判断能否修完所有课程，能则返回一个可行顺序。
 * 3. 实现 Dijkstra 的"路径还原"：除了距离，还要返回从起点到终点的具体路径（提示：记录每个节点的前驱）。
 * 4. 用并查集实现 Kruskal 最小生成树，在 5 个城市的带权图上求出最小连通代价，并说明为什么必须"按边权排序 + 判环"。
 * ============================================================
 */
