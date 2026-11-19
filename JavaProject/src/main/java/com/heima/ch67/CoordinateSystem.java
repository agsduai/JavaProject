/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-02
 * 课名：坐标系与坐标转换
 * ------------------------------------------------------------
 * 知识点：
 *   - **国内三套坐标系**是 LBS 最常见的坑：**WGS84**（GPS 原始坐标，国际标准）、**GCJ-02**（国测局加密坐标，高德/腾讯用）、**BD-09**（百度在 GCJ-02 上二次加密）——**同一地点在三套坐标系下的数值差异可达数百米**。
 *   - **转换的方向与不可逆性**：WGS84 -> GCJ-02 是**偏移算法**（公开但非官方，俗称"火星坐标"）；GCJ-02 -> BD-09 是另一层偏移；**GCJ-02 -> WGS84 只能近似反算**（迭代逼近，精度有损）。
 *   - **GPS 漂移**：定位本身有误差（城市峡谷 50~100 米），**坐标转换的误差要与定位误差一起评估**——**转换错坐标系比漂移更致命**（系统性偏移）。
 *   - **工程做法**：**统一存储 WGS84**（真值），**展示/导航时按地图 SDK 转换**；或者"对接哪家地图就统一用它的坐标系"（简单但绑定）——**关键是把"当前坐标是哪个坐标系"作为字段显式声明**。
 *   - **与 67-01 的呼应**：距离计算出错，可能是"公式错"，也可能是"坐标系错"——**排查顺序：先看坐标系一致性，再看公式**。
 *   - 易错点：混用三套坐标（导航偏几百米）；以为转换可逆（BD-09 -> WGS84 误差累积）；不声明坐标系统（数据无法追溯）；海外数据用国内坐标系（偏移算法只在中国境内有效）。
 * ============================================================
 */

package com.heima.ch67;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class CoordinateSystem {
    enum Crs {
        WGS84, GCJ02, BD09
    }

    record Coord(double lat, double lng, Crs crs) {
        String display() {
            return String.format("%s(%.6f, %.6f)", crs, lat, lng);
        }
    }

    /** WGS84 -> GCJ-02：公开的偏移算法（国内适用） */
    static Coord wgs84ToGcj02(double lat, double lng) {
        double a = 6378245.0;
        double ee = 0.00669342162296594323;
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - ee * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * Math.PI);
        dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new Coord(lat + dLat, lng + dLng, Crs.GCJ02);
    }

    static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
                + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y
                + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }

    /** GCJ-02 -> BD-09 */
    static Coord gcj02ToBd09(Coord gcj) {
        double x = gcj.lng();
        double y = gcj.lat();
        double z = Math.sqrt(x * x + y * y) + 0.00002 * Math.sin(y * Math.PI);
        double theta = Math.atan2(y, x) + 0.000003 * Math.cos(x * Math.PI);
        return new Coord(z * Math.sin(theta) + 0.006, z * Math.cos(theta) + 0.0065, Crs.BD09);
    }

    static void printAll(double lat, double lng) {
        Coord wgs = new Coord(lat, lng, Crs.WGS84);
        Coord gcj = wgs84ToGcj02(lat, lng);
        Coord bd = gcj02ToBd09(gcj);
        System.out.printf("  基准 %s%n", wgs.display());
        System.out.printf("       -> %s%n", gcj.display());
        System.out.printf("       -> %s%n", bd.display());
        System.out.printf("  GCJ 偏移 %.1f m，BD 再偏移 %.1f m%n",
                distanceMeters(wgs, gcj), distanceMeters(gcj, bd));
    }

    /** 粗略米级距离（短距离平面近似） */
    static double distanceMeters(Coord a, Coord b) {
        double dLat = (b.lat() - a.lat()) * 111_000;
        double dLng = (b.lng() - a.lng()) * 111_000 * Math.cos(Math.toRadians(a.lat()));
        return Math.sqrt(dLat * dLat + dLng * dLng);
    }

    public static void main(String[] args) {
        System.out.println("【同一地点在三套坐标系下的数值】");
        printAll(30.2741, 120.1551);
        System.out.println();
        printAll(39.9042, 116.4074);

        System.out.println();
        System.out.println("【工程做法】");
        Map<String, String> practice = new LinkedHashMap<>();
        practice.put("存储", "统一存 WGS84（真值），字段声明 crs");
        practice.put("展示", "按地图 SDK 转（高德要 GCJ-02，百度要 BD-09）");
        practice.put("海外", "GCJ-02 偏移只在中国境内有效，海外直接用 WGS84");
        practice.put("排查", "距离异常先查坐标系一致性，再查公式");
        practice.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三套坐标系的双向转换（含 GCJ-02 -> WGS84 的迭代反算），并量化"反算误差"（米级）。
 * 2. 构造一个"高德坐标直接给百度 SDK 用"的错误场景，计算偏移距离并说明用户感知。
 * 3. 思考题：为什么统一存 WGS84 更好？从"数据可追溯"与"避免二次转换误差"两个角度分析。
 * ============================================================
 */
