/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-05
 * 课名：空间索引：R 树与网格
 * ------------------------------------------------------------
 * 知识点：
 *   - **三种空间索引的取舍**：**网格索引**（把地图切格子，每格存点列表——简单、查询快，但**点分布不均时热点格爆炸**）；**R 树**（用最小外接矩形（MBR）组织空间对象，支持任意形状——数据库空间索引的主流，46-07 的 MPP 也支持）；**四叉树**（递归四分，适合点集——地图瓦片服务的常用结构）。
 *   - **R 树的核心思想**：用**包围盒层级**（根节点框住全部，子节点框住局部）做"先粗后细"的过滤——**查询时先判断"这个框是否与查询范围相交"，不相交整棵子树剪掉**（与 46-02 的 Row Group 裁剪同源）。
 *   - **网格索引的工程要点**：**格子大小与查询半径匹配**（半径 3km 用 1km 格，查 9 格）；**热点格拆分**（市中心一个格塞 10 万点 -> 用二级网格/GeoHash 细分）。
 *   - **数据库与 ES 的空间索引**：MySQL 的 `SPATIAL INDEX`（R 树，需 MyISAM 或 5.7+ InnoDB 的 GIS 支持）、PostGIS（最完整的空间能力）、ES 的 `geo_point`（底层用 BKD 树/GeoHash 前缀）——**与 38/46 章的技术栈衔接**。
 *   - 易错点：网格索引热点格不拆（单格百万点）；R 树误以为能"点查询"（它是范围查询优化）；空间索引与坐标系不匹配（投影坐标系才能建空间索引，经纬度要转换或用 GIS 类型）。
 * ============================================================
 */

package com.heima.ch67;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class SpatialIndex {
    record Point(String id, double x, double y) {
    }

    /** 网格索引：cellSize 度一格 */
    static class GridIndex {
        final double cellSize;
        final Map<String, List<Point>> cells = new LinkedHashMap<>();

        GridIndex(double cellSize) {
            this.cellSize = cellSize;
        }

        String cellOf(double x, double y) {
            return (long) Math.floor(x / cellSize) + ":" + (long) Math.floor(y / cellSize);
        }

        void add(Point p) {
            cells.computeIfAbsent(cellOf(p.x(), p.y()), k -> new ArrayList<>()).add(p);
        }

        /** 范围查询：遍历覆盖范围的格子 */
        List<Point> range(double x1, double y1, double x2, double y2) {
            List<Point> out = new ArrayList<>();
            long c1 = (long) Math.floor(x1 / cellSize), c2 = (long) Math.floor(x2 / cellSize);
            long r1 = (long) Math.floor(y1 / cellSize), r2 = (long) Math.floor(y2 / cellSize);
            int scannedCells = 0;
            for (long cx = c1; cx <= c2; cx++) {
                for (long cy = r1; cy <= r2; cy++) {
                    scannedCells++;
                    for (Point p : cells.getOrDefault(cx + ":" + cy, List.of())) {
                        if (p.x() >= x1 && p.x() <= x2 && p.y() >= y1 && p.y() <= y2) {
                            out.add(p);
                        }
                    }
                }
            }
            System.out.printf("    扫描格子 %d 个（总格子 %d 个）-> 命中 %d%n",
                    scannedCells, cells.size(), out.size());
            return out;
        }

        void hotSpotReport() {
            String hot = null;
            int max = 0;
            for (Map.Entry<String, List<Point>> e : cells.entrySet()) {
                if (e.getValue().size() > max) {
                    max = e.getValue().size();
                    hot = e.getKey();
                }
            }
            double avg = cells.values().stream().mapToInt(List::size).average().orElse(0);
            System.out.printf("  热点格 %s：%d 点（均值 %.1f）-> %s%n", hot, max, avg,
                    max > avg * 5 ? "倾斜，需二级网格" : "分布均匀");
        }
    }

    /** 简化 R 树节点：MBR + 子节点 */
    record Mbr(double minX, double minY, double maxX, double maxY) {
        boolean intersects(Mbr o) {
            return !(maxX < o.minX() || o.maxX() < minX
                    || maxY < o.minY() || o.maxY() < minY);
        }
    }

    static void rtreeExplain() {
        System.out.println("【R 树：包围盒层级过滤】");
        Mbr root = new Mbr(0, 0, 100, 100);
        List<Mbr> children = new ArrayList<>(List.of(
                new Mbr(0, 0, 40, 40), new Mbr(60, 60, 100, 100),
                new Mbr(0, 60, 40, 100)));
        Mbr query = new Mbr(30, 30, 70, 70);
        System.out.printf("  查询框：%s%n", query);
        for (Mbr child : children) {
            System.out.printf("  子节点 %-28s 相交=%b -> %s%n", child,
                    child.intersects(query), child.intersects(query) ? "进入" : "整棵剪掉");
        }
        System.out.println("  与 46-02 的 Row Group 裁剪同源：先粗过滤再精算");
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【网格索引：1 度一格，1000 个点】");
        GridIndex grid = new GridIndex(1.0);
        // 均匀点 + 一个热点区域
        for (int i = 0; i < 900; i++) {
            grid.add(new Point("p" + i, (i * 37) % 20, (i * 53) % 20));
        }
        for (int i = 0; i < 100; i++) {
            grid.add(new Point("hot" + i, 10 + i * 0.001, 10 + i * 0.001));
        }
        grid.hotSpotReport();
        System.out.println("  范围查询 (9,9)-(12,12)：");
        grid.range(9, 9, 12, 12);
        System.out.println();
        rtreeExplain();

        System.out.println("【选型】");
        Map<String, String> choice = new LinkedHashMap<>();
        choice.put("网格", "点集、查询简单、分布均匀（外卖骑手位置）");
        choice.put("R 树", "任意形状、范围查询（店铺配送范围、行政区）");
        choice.put("四叉树", "地图瓦片、点集递归细分");
        choice.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现网格索引：支持热点格二级细分（格子点数超阈值就再切 10×10），验证热点格查询仍快。
 * 2. 实现简化 R 树：插入 100 个矩形并做范围查询，输出"访问节点数"与"暴力遍历"的对比。
 * 3. 思考题：为什么"经纬度"不能直接建 MySQL SPATIAL 索引？从坐标系与索引的空间语义分析。
 * ============================================================
 */
