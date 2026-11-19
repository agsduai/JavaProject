/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-01
 * 课名：LBS 全景：坐标、距离与索引
 * ------------------------------------------------------------
 * 知识点：
 *   - **LBS（基于位置的服务）的三个基本问题**：**"我在哪"**（定位与坐标系，67-02）、**"离我多远"**（距离计算，67-03）、**"附近有什么"**（空间查询与索引，67-04~67-06）——**外卖、打车、到店、社交都建立在这三件事上**。
 *   - **坐标的两种表示**：**经纬度（球面）**与**平面坐标（投影）**——**距离计算在球面上做（Haversine），范围/索引在平面上做（GeoHash/网格/R 树）**；混用的后果是"算出的距离与查出的范围对不上"。
 *   - **空间数据的特殊挑战**：**二维范围查询**（普通 B+ 树只能一维）——**解法是把二维降成一维**（GeoHash 67-04）或**专门的二维索引**（R 树/四叉树 67-05）。
 *   - **LBS 的典型查询**：**附近 N 公里**（范围查询）、**最近的 K 个**（KNN）、**是否在区域内**（点在多边形 67-07）、**路径规划**（图最短路 67-08）。
 *   - **与 39/46 章的关系**：位置数据同样是"海量 + 按范围查"——**分片键可以是 GeoHash 前缀**（同一格子的数据物理相邻），**索引可以是空间索引**（46-07 的 MPP 引擎也支持空间函数）。
 *   - 易错点：用平面距离公式算经纬度（在纬度 40 度误差可达 20%以上）；把经纬度写反（lat/lng 顺序）；用矩形近似圆形查询（边角误差，需二次过滤 67-06）。
 * ============================================================
 */

package com.heima.ch67;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class LbsOverview {
    record Point(double lat, double lng) {
        String display() {
            return String.format("(%.4f, %.4f)", lat, lng);
        }
    }

    static void threeQuestions() {
        System.out.println("【LBS 三个基本问题】");
        String[][] rows = {
                {"我在哪", "GPS/WiFi/基站定位 -> 坐标系", "67-02 坐标转换"},
                {"离我多远", "球面距离（Haversine）", "67-03"},
                {"附近有什么", "空间范围查询 + 索引", "67-04 ~ 67-06"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-30s 对应：%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void planarVsSpherical() {
        Point hangzhou = new Point(30.2741, 120.1551);
        Point shanghai = new Point(31.2304, 121.4737);
        // 球面距离（Haversine）
        double spherical = haversineKm(hangzhou, shanghai);
        // 平面近似：按纬度换算（1 度纬度≈111km，1 度经度≈111*cos(lat)）
        double latKm = (shanghai.lat() - hangzhou.lat()) * 111.0;
        double avgLat = Math.toRadians((hangzhou.lat() + shanghai.lat()) / 2);
        double lngKm = (shanghai.lng() - hangzhou.lng()) * 111.0 * Math.cos(avgLat);
        double planar = Math.sqrt(latKm * latKm + lngKm * lngKm);
        System.out.println("【球面 vs 平面近似】");
        System.out.printf("  杭州 %s -> 上海 %s%n", hangzhou.display(), shanghai.display());
        System.out.printf("  Haversine = %.1f km%n", spherical);
        System.out.printf("  平面近似 = %.1f km（误差 %.1f%%）%n", planar,
                Math.abs(planar - spherical) / spherical * 100);
        System.out.println("  结论：短距离（<100km）平面近似可用；长距离必须球面");
        System.out.println();
    }

    /** Haversine：标准球面距离公式 */
    static double haversineKm(Point a, Point b) {
        final double R = 6371.0;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    static void queryTypes() {
        System.out.println("【LBS 典型查询】");
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("附近 3 公里", "范围查询（半径 + 边界框）");
        queries.put("最近的 10 家店", "KNN（按距离排序取 TopK）");
        queries.put("是否在配送范围内", "点在多边形/圆内判定（67-07）");
        queries.put("从 A 到 B 怎么走", "图最短路（67-08）");
        queries.forEach((k, v) -> System.out.printf("  %-18s %s%n", k, v));
    }

    public static void main(String[] args) {
        threeQuestions();
        planarVsSpherical();
        queryTypes();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Haversine 距离计算，用 5 组坐标（跨城市、同城、相邻楼宇）验证结果合理性。
 * 2. 对比"平面近似"与"Haversine"在 1km/10km/100km/1000km 四档距离下的误差，给出可用阈值。
 * 3. 思考题：为什么位置数据需要"二维索引"而普通 B+ 树不够？从"经纬度联合查询"的索引失效分析。
 * ============================================================
 */
