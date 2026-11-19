/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-04
 * 课名：GeoHash：把二维降成一维
 * ------------------------------------------------------------
 * 知识点：
 *   - **GeoHash 的核心思路：交替二分经纬度区间，把二维坐标编码成一维字符串**——每编码一位就把范围减半，**编码前缀越长的字符串，代表的地理格子越小**（1 位≈5000km，6 位≈1.2km，8 位≈38m）。
 *   - **前缀匹配即"附近"**：同一前缀的坐标落在同一格子——**"找附近"变成"前缀 LIKE 查询"**（39-02 的前缀索引可用）——**这是把空间查询塞进普通索引的关键技巧**。
 *   - **边界问题是 GeoHash 的固有缺陷**：两点在格子的两侧（相邻但跨格），前缀可能完全不同——**解法：查询时取"中心格 + 周围 8 个格"**（9 格搜索，67-06），或同时用多个精度。
 *   - **与 39-05 分片的关系**：**GeoHash 可作为分片键前缀**（同格子数据物理相邻），但**热点格子**（市中心）会倾斜——需要加盐或拆格（59-08 的倾斜治理）。
 *   - **Base32 编码**：GeoHash 用 32 个字符（0-9 + b-z 去掉 a/i/l/o）——**字母表要统一**（大小写、是否含 a/i/l/o），否则"同一位置不同编码"。
 *   - 易错点：只用前缀匹配不加邻格（跨界的点漏查）；精度选错（6 位 1.2km 用来找 100m 内的店）；经纬度顺序搞反（编码完全不同）；Base32 字母表不一致。
 * ============================================================
 */

package com.heima.ch67;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class GeoHash {
    static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    /** 编码：交替二分 lat/lng，precision 决定长度 */
    static String encode(double lat, double lng, int precision) {
        double latMin = -90, latMax = 90, lngMin = -180, lngMax = 180;
        StringBuilder sb = new StringBuilder();
        boolean even = true;                       // 偶数位编码经度
        int bit = 0, ch = 0;
        while (sb.length() < precision) {
            if (even) {
                double mid = (lngMin + lngMax) / 2;
                if (lng >= mid) {
                    ch = (ch << 1) | 1;
                    lngMin = mid;
                } else {
                    ch = ch << 1;
                    lngMax = mid;
                }
            } else {
                double mid = (latMin + latMax) / 2;
                if (lat >= mid) {
                    ch = (ch << 1) | 1;
                    latMin = mid;
                } else {
                    ch = ch << 1;
                    latMax = mid;
                }
            }
            even = !even;
            if (++bit == 5) {
                sb.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }
        return sb.toString();
    }

    /** 解码：返回中心点与误差范围 */
    static double[] decode(String hash) {
        double latMin = -90, latMax = 90, lngMin = -180, lngMax = 180;
        boolean even = true;
        for (char c : hash.toCharArray()) {
            int idx = BASE32.indexOf(c);
            for (int mask = 16; mask > 0; mask >>= 1) {
                boolean one = (idx & mask) != 0;
                if (even) {
                    double mid = (lngMin + lngMax) / 2;
                    if (one) {
                        lngMin = mid;
                    } else {
                        lngMax = mid;
                    }
                } else {
                    double mid = (latMin + latMax) / 2;
                    if (one) {
                        latMin = mid;
                    } else {
                        latMax = mid;
                    }
                }
                even = !even;
            }
        }
        return new double[]{(latMin + latMax) / 2, (lngMin + lngMax) / 2,
                (latMax - latMin), (lngMax - lngMin)};
    }

    /** 精度参考表 */
    static void precisionTable() {
        System.out.println("【GeoHash 精度参考】");
        Map<Integer, String> table = new LinkedHashMap<>();
        table.put(1, "约 5000km");
        table.put(4, "约 39km × 19.5km");
        table.put(5, "约 4.9km × 4.9km");
        table.put(6, "约 1.2km × 0.6km");
        table.put(7, "约 153m × 153m");
        table.put(8, "约 38m × 19m");
        table.forEach((p, size) -> System.out.printf("  %d 位 -> %s%n", p, size));
        System.out.println();
    }

    public static void main(String[] args) {
        precisionTable();
        double lat = 30.2741, lng = 120.1551;
        System.out.println("【杭州某点编码】" + lat + ", " + lng);
        for (int p : new int[]{5, 6, 7, 8}) {
            String hash = encode(lat, lng, p);
            double[] back = decode(hash);
            System.out.printf("  %d 位 = %-10s 解码中心=(%.5f, %.5f) 格子=%.4f°×%.4f°%n",
                    p, hash, back[0], back[1], back[2], back[3]);
        }

        System.out.println();
        System.out.println("【前缀即邻近：同城区扫街】");
        List<double[]> points = List.of(
                new double[]{30.2741, 120.1551},
                new double[]{30.2755, 120.1568},
                new double[]{30.1000, 120.2000},
                new double[]{39.9042, 116.4074});
        String center = encode(points.get(0)[0], points.get(0)[1], 5);
        System.out.println("  中心格 = " + center);
        for (double[] p : points) {
            String h = encode(p[0], p[1], 5);
            System.out.printf("  (%.4f, %.4f) hash=%-6s 同前缀=%b%n",
                    p[0], p[1], h, h.startsWith(center.substring(0, 4)));
        }
        System.out.println();
        System.out.println("  跨界缺陷：相邻但跨格的两点前缀可能完全不同 -> 需 9 格搜索（67-06）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 GeoHash 编解码，验证"编码 -> 解码 -> 再编码"幂等，并测量不同精度的格子尺寸。
 * 2. 实现"9 格搜索"：给定中心点与半径，生成中心格 + 8 个邻格的前缀，验证跨格的点不再漏查。
 * 3. 思考题：为什么 GeoHash 不能保证"前缀越长距离越近"？从"格子边界"与"相邻格共享边"分析。
 * ============================================================
 */
