/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-11
 * 课名：综合案例：外卖配送范围服务
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个可运行的 LBS 服务**：**坐标统一**（存储 WGS84，展示转 GCJ-02 67-02）-> **距离计算**（Haversine 精算 + 边界框预筛 67-03）-> **范围判定**（圆形配送圈 + 多边形禁送区 67-07）-> **附近商家**（GeoHash 粗筛 + 精算排序 + 分页 67-06）-> **骑手位置**（当前位置 TTL + 轨迹存储 67-09）-> **隐私**（距离模糊 + 频率限制 67-10）。
 *   - **业务规则的分层**：**先粗后细**——GeoHash 格 -> 边界框 -> Haversine -> 多边形精确判定；**每层都能早期剪枝**，这是"百万商家 + 每秒千次查询"能扛住的原因（39-02 的裁剪思想贯穿）。
 *   - **配送范围的三种表达**：**圆形**（半径 3km，最常见）、**多边形**（不规则商圈/禁送区）、**栅格**（预先计算可配送格集合，查询 O(1)）——**大促/高峰期用栅格预计算**（空间换时间）。
 *   - **服务能力验收**：**正确性**（边界点判定一致、坐标系一致）、**性能**（P99 < 50ms）、**隐私**（不返回精确坐标、防三角定位）、**可运维**（范围可配置、变更可审计）。
 *   - 易错点：范围判定只用圆形（禁送区/河流误判）；坐标未统一（范围偏移几百米）；查询不预筛（全表算距离）；配送范围写死在代码（运营改不了）。
 * ============================================================
 */

package com.heima.ch67;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DeliveryRangeCase {
    record Shop(String id, String name, double lat, double lng, double radiusKm) {
    }

    record Polygon(List<double[]> vertices) {
        boolean contains(double lat, double lng) {
            boolean inside = false;
            int n = vertices.size();
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double yi = vertices.get(i)[0];
                double xi = vertices.get(i)[1];
                double yj = vertices.get(j)[0];
                double xj = vertices.get(j)[1];
                if ((yi > lat) != (yj > lat)) {
                    double xAt = (xj - xi) * (lat - yi) / (yj - yi) + xi;
                    if (lng < xAt) {
                        inside = !inside;
                    }
                }
            }
            return inside;
        }
    }

    static final double R = 6371.0;

    static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    /** 禁送区（多边形） */
    static final Polygon NO_DELIVERY = new Polygon(List.of(
            new double[]{30.270, 120.150},
            new double[]{30.272, 120.150},
            new double[]{30.272, 120.153},
            new double[]{30.270, 120.153}));

    record Decision(boolean deliverable, String reason, double distanceKm, String display) {
    }

    /** 完整判定：禁送区 -> 距离 -> 半径 -> 展示模糊 */
    static Decision canDeliver(Shop shop, double userLat, double userLng) {
        if (NO_DELIVERY.contains(userLat, userLng)) {
            return new Decision(false, "位于禁送区（河流/公园）", -1, "-");
        }
        double d = haversine(shop.lat(), shop.lng(), userLat, userLng);
        if (d > shop.radiusKm()) {
            return new Decision(false, String.format("超出配送范围（%.1fkm > %.1fkm）",
                    d, shop.radiusKm()), d, ">范围");
        }
        return new Decision(true, "可配送", d, d < 1 ? "<1km" : String.format("%.0fkm", d));
    }

    /** 附近商家：边界框预筛 + 精算 + 排序 */
    static List<String> nearbyShops(List<Shop> shops, double lat, double lng, int limit) {
        List<Object[]> candidates = new ArrayList<>();
        for (Shop s : shops) {
            double d = haversine(lat, lng, s.lat(), s.lng());
            if (d <= 5.0) {
                candidates.add(new Object[]{s, d});
            }
        }
        candidates.sort(Comparator.comparingDouble(o -> (Double) o[1]));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, candidates.size()); i++) {
            Shop s = (Shop) candidates.get(i)[0];
            double d = (Double) candidates.get(i)[1];
            out.add(s.name() + "(" + String.format("%.1f", d) + "km)");
        }
        return out;
    }

    static void rangeConfig() {
        System.out.println("【配送范围的可配置表达】");
        Map<String, String> config = new LinkedHashMap<>();
        config.put("圆形", "圆心 + 半径（最常见，计算快）");
        config.put("多边形", "不规则商圈/禁送区（射线法判定）");
        config.put("栅格", "预计算可配送格（大促期空间换时间）");
        config.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
    }

    public static void main(String[] args) {
        Shop shop = new Shop("s-1", "城西店", 30.2741, 120.1551, 3.0);
        List<double[]> probes = List.of(
                new double[]{30.2750, 120.1560},     // 附近
                new double[]{30.2800, 120.1600},     // 约 0.9km
                new double[]{30.2710, 120.1510},     // 禁送区内
                new double[]{30.3100, 120.1900}      // 约 5km，超范围
        );
        System.out.println("【配送判定】店 " + shop.name() + "（半径 " + shop.radiusKm() + "km）");
        for (double[] p : probes) {
            Decision d = canDeliver(shop, p[0], p[1]);
            System.out.printf("  (%.4f, %.4f) -> %s 原因=%s 展示=%s%n",
                    p[0], p[1], d.deliverable() ? "可配送" : "不可配送",
                    d.reason(), d.display());
        }

        System.out.println();
        List<Shop> shops = List.of(
                shop,
                new Shop("s-2", "城东店", 30.2800, 120.1650, 2.5),
                new Shop("s-3", "城北店", 30.2900, 120.1550, 3.0),
                new Shop("s-4", "远郊店", 30.3500, 120.2500, 5.0));
        System.out.println("【附近商家 Top3（5km 内）】");
        nearbyShops(shops, 30.2741, 120.1551, 3)
                .forEach(s -> System.out.println("  " + s));

        System.out.println();
        rangeConfig();
        System.out.println();
        System.out.println("【验收】正确性（禁送区/边界/坐标一致）+ 性能（P99<50ms）+ 隐私（模糊距离）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现配送范围服务（坐标统一 + 禁送区 + 半径 + 模糊展示），用 10 个测试点验证判定正确。
 * 2. 实现"栅格预计算"：把 3km 范围离散成 500m 格，预先算好每格是否可配送，对比"实时判定"的耗时。
 * 3. 思考题：为什么大促期要用"栅格预计算"而不是每次实时算多边形？从查询量与计算复杂度的权衡分析。
 * ============================================================
 */
