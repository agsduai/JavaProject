/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-07
 * 课名：电子围栏：点在多边形内
 * ------------------------------------------------------------
 * 知识点：
 *   - **电子围栏（Geo-fencing）是"判断一个点是否在指定区域内"**：配送范围、禁停区、商圈、行政区域——**区域用多边形表示**（顶点数组），**判断用射线法**（Ray Casting）。
 *   - **射线法的原理**：从点向任意方向发一条射线，**数它与多边形边的交点个数**——奇数在内部，偶数在外部；**实现要注意边界情况**（顶点在射线上、水平边、射线过顶点）。**标准做法是取"半开区间"**（`(y1 > y) != (y2 > y)`）避免重复计数。
 *   - **圆形的围栏更简单**：`Haversine(点, 圆心) <= 半径`——**配送范围常用圆形**（简单、快），多边形用于**不规则区域**（行政区、禁停区）。
 *   - **围栏的工程用法**：**进/出围栏事件**（骑手进入配送区 -> 开始计时）、**多点判断**（一个点是否在"任一"区域内 -> 遍历；在"全部"区域内 -> 原子判定）、**围栏的索引**（先按外接矩形粗筛，再精算 67-03/67-05）。
 *   - **与 67-05 的关系**：多边形是"任意形状"，R 树用 MBR 组织它——**查询时先用 MBR 相交粗筛，再射线法精算**。
 *   - 易错点：射线法边界处理错（顶点上重复计数）；多边形顶点顺序影响（顺时针/逆时针不影响奇偶判断，但影响面积计算）；用矩形近似不规则区域（边界外误判）；围栏判定不用外接矩形预筛（多边形边数多时慢）。
 * ============================================================
 */

package com.heima.ch67;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class GeoFence {
    record Point(double x, double y) {
    }

    record Polygon(List<Point> vertices) {
        /** 外接矩形（用于预筛） */
        double[] bbox() {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Point p : vertices) {
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
                maxX = Math.max(maxX, p.x());
                maxY = Math.max(maxY, p.y());
            }
            return new double[]{minX, minY, maxX, maxY};
        }
    }

    /** 射线法：奇偶判定，半开区间避免顶点重复计数 */
    static boolean contains(Polygon poly, Point p) {
        boolean inside = false;
        List<Point> vs = poly.vertices();
        int n = vs.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point a = vs.get(i);
            Point b = vs.get(j);
            boolean crosses = (a.y() > p.y()) != (b.y() > p.y());
            if (!crosses) {
                continue;
            }
            double xAtY = (b.x() - a.x()) * (p.y() - a.y()) / (b.y() - a.y()) + a.x();
            if (p.x() < xAtY) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 带预筛的判定 */
    static boolean containsFast(Polygon poly, Point p) {
        double[] b = poly.bbox();
        if (p.x() < b[0] || p.x() > b[2] || p.y() < b[1] || p.y() > b[3]) {
            return false;                          // 外接矩形外直接排除
        }
        return contains(poly, p);
    }

    /** 圆形围栏 */
    static boolean inCircle(Point center, Point p, double radius) {
        double dx = p.x() - center.x();
        double dy = p.y() - center.y();
        return dx * dx + dy * dy <= radius * radius;
    }

    public static void main(String[] args) {
        // 一个不规则五边形区域
        Polygon zone = new Polygon(List.of(
                new Point(0, 0), new Point(10, 0), new Point(12, 6),
                new Point(6, 10), new Point(0, 8)));
        System.out.println("【多边形围栏（顶点 " + zone.vertices().size() + " 个）】");
        double[] b = zone.bbox();
        System.out.printf("  外接矩形：[%.0f,%.0f] - [%.0f,%.0f]%n", b[0], b[1], b[2], b[3]);

        Point[] probes = {
                new Point(5, 5),      // 内部
                new Point(1, 1),      // 内部（左下）
                new Point(11, 9),     // 外部（右上缺口）
                new Point(-1, 5),     // 外部（左）
                new Point(6, 10)      // 顶点上（边界）
        };
        for (Point p : probes) {
            boolean fast = containsFast(zone, p);
            System.out.printf("  点 (%.0f, %.0f) -> %s%n", p.x(), p.y(),
                    fast ? "在围栏内" : "在围栏外");
        }

        System.out.println();
        System.out.println("【圆形围栏（配送 3km）】");
        Point shop = new Point(0, 0);
        double radius = 3.0;
        for (Point p : new Point[]{new Point(1, 1), new Point(2.1, 2.1), new Point(3, 0)}) {
            double dist = Math.sqrt(p.x() * p.x() + p.y() * p.y());
            System.out.printf("  (%.1f, %.1f) 距离=%.2f -> %s%n", p.x(), p.y(), dist,
                    inCircle(shop, p, radius) ? "配送范围内" : "超出配送范围");
        }

        System.out.println();
        System.out.println("【进/出围栏事件】");
        List<String> events = new ArrayList<>();
        boolean wasInside = false;
        for (Point p : new Point[]{new Point(-5, 5), new Point(5, 5),
                new Point(11, 9), new Point(5, 5)}) {
            boolean now = containsFast(zone, p);
            if (now && !wasInside) {
                events.add("ENTER (" + p.x() + "," + p.y() + ")");
            } else if (!now && wasInside) {
                events.add("EXIT  (" + p.x() + "," + p.y() + ")");
            }
            wasInside = now;
        }
        events.forEach(e -> System.out.println("  " + e));
        System.out.println();
        System.out.println("  要点：半开区间 (y1 > y) != (y2 > y) 避免顶点重复计数；先 MBR 预筛");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现多边形围栏判定：用 20 个测试点（含顶点上、边中点、凹多边形内部缺口）验证射线法正确。
 * 2. 实现"多点围栏"：判断一个点是否在 100 个多边形中的任意一个（用外接矩形预筛 + 精确判定），对比暴力遍历耗时。
 * 3. 思考题：为什么射线法要把 Y 比较写成半开区间？构造"射线正好穿过顶点"的用例说明重复计数。
 * ============================================================
 */
