/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-06
 * 课名：附近的人：查询与分页
 * ------------------------------------------------------------
 * 知识点：
 *   - **"附近的人"的完整查询链路**：**GeoHash 前缀（或网格）粗筛** -> **边界框过滤**（67-03） -> **Haversine 精算距离** -> **按距离排序** -> **分页**——**每一步都在缩小候选集**（39-02 的裁剪思想）。
 *   - **分页的特殊性**：**距离排序无法用 offset 稳定分页**（用户移动后排序会变）——**用"距离游标"**（`WHERE distance > lastDistance`，类似 39-03 的游标分页）；或**限定返回数量**（近 20 个）。
 *   - **候选集大小的控制**：GeoHash 精度决定格子大小——**查询半径 3km 选 6 位（1.2km）格 + 9 格搜索**，候选约几千个；**若候选太多**（市中心），先按格子内热门度/在线状态过滤，再精算。
 *   - **"人"与"店"的差异**：**店的集合小且稳定**（适合预计算距离/缓存）；**人的位置高频变化**（骑手每秒上报，适合内存/GeoHash 索引 + TTL 过期 65-09 的 Presence 同源）。
 *   - **隐私红线**（67-10）：返回"附近的人"时**距离模糊化**（1km 内显示"<1km"）、**不返回精确坐标**、**防三角定位**（多个查询可以反推目标位置）——**LBS 的隐私问题在"附近的人"最突出**。
 *   - 易错点：全表算距离（不预筛）；用 offset 分页（移动后重复/漏项）；返回精确坐标（隐私泄露）；不限制返回数量（大范围查询拖死）。
 * ============================================================
 */

package com.heima.ch67;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ==================== 示例代码 ====================

public class NearByQuery {
    record Person(String id, double lat, double lng, long lastActiveMs) {
    }

    static final double EARTH_RADIUS_KM = 6371.0;

    static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /** 距离显示模糊化（隐私，67-10） */
    static String displayDistance(double km) {
        if (km < 1) {
            return "<1km";
        }
        if (km < 10) {
            return (int) km + "km";
        }
        return ">10km";
    }

    record NearResult(String id, double distanceKm, String display) {
    }

    /** 一次查询：边界框预筛 + 精算 + 排序 + 限制数量 */
    static List<NearResult> queryNearby(List<Person> people, double lat, double lng,
                                        double radiusKm, int limit) {
        double dLat = radiusKm / 111.0;
        double dLng = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        List<NearResult> hits = new ArrayList<>();
        int prefiltered = 0;
        for (Person p : people) {
            if (Math.abs(p.lat() - lat) > dLat || Math.abs(p.lng() - lng) > dLng) {
                continue;
            }
            prefiltered++;
            double d = haversine(lat, lng, p.lat(), p.lng());
            if (d <= radiusKm) {
                hits.add(new NearResult(p.id(), d, displayDistance(d)));
            }
        }
        hits.sort(Comparator.comparingDouble(NearResult::distanceKm));
        System.out.printf("  候选 %d -> 命中 %d -> 返回前 %d%n",
                prefiltered, hits.size(), Math.min(limit, hits.size()));
        return hits.subList(0, Math.min(limit, hits.size()));
    }

    /** 距离游标分页：避免 offset 漂移 */
    static List<NearResult> pageAfter(List<NearResult> sorted, double lastDistanceKm,
                                      int limit) {
        List<NearResult> out = new ArrayList<>();
        for (NearResult r : sorted) {
            if (r.distanceKm() > lastDistanceKm && out.size() < limit) {
                out.add(r);
            }
        }
        return out;
    }

    public static void main(String[] args) {
        List<Person> people = new ArrayList<>();
        // 我：30.2741, 120.1551
        for (int i = 0; i < 200; i++) {
            double lat = 30.2741 + (i % 20 - 10) * 0.005;
            double lng = 120.1551 + (i / 20 - 5) * 0.005;
            people.add(new Person("u-" + i, lat, lng, 1_000_000L - i * 1000L));
        }
        System.out.println("【附近 3km（最多 5 个）】");
        List<NearResult> page1 = queryNearby(people, 30.2741, 120.1551, 3.0, 5);
        page1.forEach(r -> System.out.printf("  %-6s 距离显示=%s（精确 %.3fkm 仅日志）%n",
                r.id(), r.display(), r.distanceKm()));

        System.out.println();
        System.out.println("【游标分页：上一页最后距离 %.3fkm】".formatted(
                page1.get(page1.size() - 1).distanceKm()));
        double last = page1.get(page1.size() - 1).distanceKm();
        List<NearResult> all = queryNearby(people, 30.2741, 120.1551, 3.0, 1000);
        List<NearResult> page2 = pageAfter(all, last, 5);
        page2.forEach(r -> System.out.printf("  %-6s %s%n", r.id(), r.display()));

        System.out.println();
        System.out.println("【隐私处理】");
        System.out.println("  对外只返回 <1km / 3km 这类模糊距离，不返回对方的精确坐标");
        System.out.println("  防三角定位：连续多次不同位置查询可反推目标 -> 需限制频率与精度（67-10）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"附近的人"完整查询：GeoHash 粗筛 + 边界框 + 精算 + 距离游标分页，用 1000 人验证性能与正确性。
 * 2. 实现距离模糊化与频率限制：同一查询方 1 分钟内只能查 3 次，验证防三角定位。
 * 3. 思考题：为什么"距离排序 + offset 分页"会重复/漏项？构造"用户移动导致排序变化"的用例说明。
 * ============================================================
 */
