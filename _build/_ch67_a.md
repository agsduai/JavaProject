### 67-01 LBS 全景：坐标、距离与索引

**知识点**
- **LBS（基于位置的服务）的三个基本问题**：**"我在哪"**（定位与坐标系，67-02）、**"离我多远"**（距离计算，67-03）、**"附近有什么"**（空间查询与索引，67-04~67-06）——**外卖、打车、到店、社交都建立在这三件事上**。
- **坐标的两种表示**：**经纬度（球面）**与**平面坐标（投影）**——**距离计算在球面上做（Haversine），范围/索引在平面上做（GeoHash/网格/R 树）**；混用的后果是"算出的距离与查出的范围对不上"。
- **空间数据的特殊挑战**：**二维范围查询**（普通 B+ 树只能一维）——**解法是把二维降成一维**（GeoHash 67-04）或**专门的二维索引**（R 树/四叉树 67-05）。
- **LBS 的典型查询**：**附近 N 公里**（范围查询）、**最近的 K 个**（KNN）、**是否在区域内**（点在多边形 67-07）、**路径规划**（图最短路 67-08）。
- **与 39/46 章的关系**：位置数据同样是"海量 + 按范围查"——**分片键可以是 GeoHash 前缀**（同一格子的数据物理相邻），**索引可以是空间索引**（46-07 的 MPP 引擎也支持空间函数）。
- 易错点：用平面距离公式算经纬度（在纬度 40 度误差可达 20%以上）；把经纬度写反（lat/lng 顺序）；用矩形近似圆形查询（边角误差，需二次过滤 67-06）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
```

**课后练习**
1. 实现 Haversine 距离计算，用 5 组坐标（跨城市、同城、相邻楼宇）验证结果合理性。
2. 对比"平面近似"与"Haversine"在 1km/10km/100km/1000km 四档距离下的误差，给出可用阈值。
3. 思考题：为什么位置数据需要"二维索引"而普通 B+ 树不够？从"经纬度联合查询"的索引失效分析。

---

### 67-02 坐标系与坐标转换

**知识点**
- **国内三套坐标系**是 LBS 最常见的坑：**WGS84**（GPS 原始坐标，国际标准）、**GCJ-02**（国测局加密坐标，高德/腾讯用）、**BD-09**（百度在 GCJ-02 上二次加密）——**同一地点在三套坐标系下的数值差异可达数百米**。
- **转换的方向与不可逆性**：WGS84 -> GCJ-02 是**偏移算法**（公开但非官方，俗称"火星坐标"）；GCJ-02 -> BD-09 是另一层偏移；**GCJ-02 -> WGS84 只能近似反算**（迭代逼近，精度有损）。
- **GPS 漂移**：定位本身有误差（城市峡谷 50~100 米），**坐标转换的误差要与定位误差一起评估**——**转换错坐标系比漂移更致命**（系统性偏移）。
- **工程做法**：**统一存储 WGS84**（真值），**展示/导航时按地图 SDK 转换**；或者"对接哪家地图就统一用它的坐标系"（简单但绑定）——**关键是把"当前坐标是哪个坐标系"作为字段显式声明**。
- **与 67-01 的呼应**：距离计算出错，可能是"公式错"，也可能是"坐标系错"——**排查顺序：先看坐标系一致性，再看公式**。
- 易错点：混用三套坐标（导航偏几百米）；以为转换可逆（BD-09 -> WGS84 误差累积）；不声明坐标系统（数据无法追溯）；海外数据用国内坐标系（偏移算法只在中国境内有效）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

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
```

**课后练习**
1. 实现三套坐标系的双向转换（含 GCJ-02 -> WGS84 的迭代反算），并量化"反算误差"（米级）。
2. 构造一个"高德坐标直接给百度 SDK 用"的错误场景，计算偏移距离并说明用户感知。
3. 思考题：为什么统一存 WGS84 更好？从"数据可追溯"与"避免二次转换误差"两个角度分析。

---

### 67-03 距离计算：Haversine 与近似

**知识点**
- **Haversine 是球面距离的标准公式**：用经纬度差与两点的纬度计算大圆距离，**地球按半径 6371km 的球近似**（实际是椭球，误差 0.3% 左右，LBS 场景可接受）。
- **公式的直觉**：`haversine(θ) = sin²(θ/2)`，把两点投影到单位球面上算中心角的弦长，再乘半径——**比"平面勾股 + 余弦修正"更稳**（后者在高纬度和长距离误差大）。
- **平面近似的适用边界**：**短距离（<50km）且不跨极地**时，`Δlat×111km`、`Δlng×111km×cos(lat)` 的误差 <1%——**附近的人/店常用它做粗筛**（快），**精确距离再用 Haversine**（准）。
- **性能优化：边界框预筛**（67-06）：先用 `lat±Δ、lng±Δ` 的矩形把候选缩小（走索引），再对候选算 Haversine——**避免对全表算距离**（39-02 的"先裁剪后精算"同款思想）。
- **与 67-01/67-02 的联动**：**距离算错的三层原因**——坐标系不一致（最大误差）、纬度未修正（中纬误差）、公式本身（最小误差）。**排查从大到小**。
- 易错点：用平面公式跨省算（误差几十公里）；经度差忘记乘 cos(lat)（高纬误差翻倍）；半径单位混用（米/公里）；对 1 亿条数据逐条算 Haversine（不预筛）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class DistanceCalc {
    static final double EARTH_RADIUS_KM = 6371.0;

    /** Haversine 球面距离 */
    static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /** 平面近似（短距离可用） */
    static double planar(double lat1, double lng1, double lat2, double lng2) {
        double dLatKm = (lat2 - lat1) * 111.0;
        double dLngKm = (lng2 - lng1) * 111.0 * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        return Math.sqrt(dLatKm * dLatKm + dLngKm * dLngKm);
    }

    /** 边界框：给定圆心与半径，算出经纬度范围（用于索引预筛） */
    static double[] boundingBox(double lat, double lng, double radiusKm) {
        double dLat = radiusKm / 111.0;
        double dLng = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return new double[]{lat - dLat, lat + dLat, lng - dLng, lng + dLng};
    }

    record Shop(String name, double lat, double lng) {
    }

    static void nearBy(List<Shop> shops, double lat, double lng, double radiusKm) {
        System.out.printf("【附近 %.0fkm：先边界框预筛，再 Haversine 精算】%n", radiusKm);
        double[] box = boundingBox(lat, lng, radiusKm);
        System.out.printf("  边界框：lat[%.4f, %.4f] lng[%.4f, %.4f]%n",
                box[0], box[1], box[2], box[3]);
        List<String> hits = new ArrayList<>();
        int prefiltered = 0;
        for (Shop s : shops) {
            boolean inBox = s.lat() >= box[0] && s.lat() <= box[1]
                    && s.lng() >= box[2] && s.lng() <= box[3];
            if (!inBox) {
                continue;
            }
            prefiltered++;
            double d = haversine(lat, lng, s.lat(), s.lng());
            if (d <= radiusKm) {
                hits.add(String.format("%s(%.1fkm)", s.name(), d));
            }
        }
        System.out.printf("  候选 %d 个 -> 命中 %s%n", prefiltered, hits);
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【两种公式的误差对比】");
        double[][] cases = {
                {30.2741, 120.1551, 30.2800, 120.1600},       // 约 0.7km
                {30.2741, 120.1551, 30.5000, 120.4000},       // 约 35km
                {30.2741, 120.1551, 31.2304, 121.4737},       // 杭州-上海 约 165km
                {30.2741, 120.1551, 39.9042, 116.4074}        // 杭州-北京 约 1150km
        };
        for (double[] c : cases) {
            double h = haversine(c[0], c[1], c[2], c[3]);
            double p = planar(c[0], c[1], c[2], c[3]);
            System.out.printf("  Haversine=%8.3f km  平面=%8.3f km  误差=%5.2f%%%n",
                    h, p, Math.abs(p - h) / h * 100);
        }
        System.out.println("  短距离可平面近似；长距离必须球面");
        System.out.println();

        List<Shop> shops = List.of(
                new Shop("A 店", 30.2750, 120.1560),
                new Shop("B 店", 30.2800, 120.1600),
                new Shop("C 店", 30.3000, 120.1800),
                new Shop("D 店", 31.2304, 121.4737));
        nearBy(shops, 30.2741, 120.1551, 3.0);
        System.out.println("  预筛价值：边界框内的才精算，避免对全表算 Haversine");
    }
}
```

**课后练习**
1. 实现 Haversine 与平面近似，用 4 档距离量化误差，给出"可平面近似"的距离阈值。
2. 实现"边界框预筛 + 精算"：用 1000 个随机点验证结果与"全量 Haversine"一致，但计算次数下降一个数量级。
3. 思考题：为什么边界框预筛是"矩形"而不是"圆形"？从索引可用性（矩形可走 B+ 树/GeoHash）分析。

---

### 67-04 GeoHash：把二维降成一维

**知识点**
- **GeoHash 的核心思路：交替二分经纬度区间，把二维坐标编码成一维字符串**——每编码一位就把范围减半，**编码前缀越长的字符串，代表的地理格子越小**（1 位≈5000km，6 位≈1.2km，8 位≈38m）。
- **前缀匹配即"附近"**：同一前缀的坐标落在同一格子——**"找附近"变成"前缀 LIKE 查询"**（39-02 的前缀索引可用）——**这是把空间查询塞进普通索引的关键技巧**。
- **边界问题是 GeoHash 的固有缺陷**：两点在格子的两侧（相邻但跨格），前缀可能完全不同——**解法：查询时取"中心格 + 周围 8 个格"**（9 格搜索，67-06），或同时用多个精度。
- **与 39-05 分片的关系**：**GeoHash 可作为分片键前缀**（同格子数据物理相邻），但**热点格子**（市中心）会倾斜——需要加盐或拆格（59-08 的倾斜治理）。
- **Base32 编码**：GeoHash 用 32 个字符（0-9 + b-z 去掉 a/i/l/o）——**字母表要统一**（大小写、是否含 a/i/l/o），否则"同一位置不同编码"。
- 易错点：只用前缀匹配不加邻格（跨界的点漏查）；精度选错（6 位 1.2km 用来找 100m 内的店）；经纬度顺序搞反（编码完全不同）；Base32 字母表不一致。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
```

**课后练习**
1. 实现 GeoHash 编解码，验证"编码 -> 解码 -> 再编码"幂等，并测量不同精度的格子尺寸。
2. 实现"9 格搜索"：给定中心点与半径，生成中心格 + 8 个邻格的前缀，验证跨格的点不再漏查。
3. 思考题：为什么 GeoHash 不能保证"前缀越长距离越近"？从"格子边界"与"相邻格共享边"分析。

---

### 67-05 空间索引：R 树与网格

**知识点**
- **三种空间索引的取舍**：**网格索引**（把地图切格子，每格存点列表——简单、查询快，但**点分布不均时热点格爆炸**）；**R 树**（用最小外接矩形（MBR）组织空间对象，支持任意形状——数据库空间索引的主流，46-07 的 MPP 也支持）；**四叉树**（递归四分，适合点集——地图瓦片服务的常用结构）。
- **R 树的核心思想**：用**包围盒层级**（根节点框住全部，子节点框住局部）做"先粗后细"的过滤——**查询时先判断"这个框是否与查询范围相交"，不相交整棵子树剪掉**（与 46-02 的 Row Group 裁剪同源）。
- **网格索引的工程要点**：**格子大小与查询半径匹配**（半径 3km 用 1km 格，查 9 格）；**热点格拆分**（市中心一个格塞 10 万点 -> 用二级网格/GeoHash 细分）。
- **数据库与 ES 的空间索引**：MySQL 的 `SPATIAL INDEX`（R 树，需 MyISAM 或 5.7+ InnoDB 的 GIS 支持）、PostGIS（最完整的空间能力）、ES 的 `geo_point`（底层用 BKD 树/GeoHash 前缀）——**与 38/46 章的技术栈衔接**。
- 易错点：网格索引热点格不拆（单格百万点）；R 树误以为能"点查询"（它是范围查询优化）；空间索引与坐标系不匹配（投影坐标系才能建空间索引，经纬度要转换或用 GIS 类型）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
```

**课后练习**
1. 实现网格索引：支持热点格二级细分（格子点数超阈值就再切 10×10），验证热点格查询仍快。
2. 实现简化 R 树：插入 100 个矩形并做范围查询，输出"访问节点数"与"暴力遍历"的对比。
3. 思考题：为什么"经纬度"不能直接建 MySQL SPATIAL 索引？从坐标系与索引的空间语义分析。

---

### 67-06 附近的人：查询与分页

**知识点**
- **"附近的人"的完整查询链路**：**GeoHash 前缀（或网格）粗筛** -> **边界框过滤**（67-03） -> **Haversine 精算距离** -> **按距离排序** -> **分页**——**每一步都在缩小候选集**（39-02 的裁剪思想）。
- **分页的特殊性**：**距离排序无法用 offset 稳定分页**（用户移动后排序会变）——**用"距离游标"**（`WHERE distance > lastDistance`，类似 39-03 的游标分页）；或**限定返回数量**（近 20 个）。
- **候选集大小的控制**：GeoHash 精度决定格子大小——**查询半径 3km 选 6 位（1.2km）格 + 9 格搜索**，候选约几千个；**若候选太多**（市中心），先按格子内热门度/在线状态过滤，再精算。
- **"人"与"店"的差异**：**店的集合小且稳定**（适合预计算距离/缓存）；**人的位置高频变化**（骑手每秒上报，适合内存/GeoHash 索引 + TTL 过期 65-09 的 Presence 同源）。
- **隐私红线**（67-10）：返回"附近的人"时**距离模糊化**（1km 内显示"<1km"）、**不返回精确坐标**、**防三角定位**（多个查询可以反推目标位置）——**LBS 的隐私问题在"附近的人"最突出**。
- 易错点：全表算距离（不预筛）；用 offset 分页（移动后重复/漏项）；返回精确坐标（隐私泄露）；不限制返回数量（大范围查询拖死）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
```

**课后练习**
1. 实现"附近的人"完整查询：GeoHash 粗筛 + 边界框 + 精算 + 距离游标分页，用 1000 人验证性能与正确性。
2. 实现距离模糊化与频率限制：同一查询方 1 分钟内只能查 3 次，验证防三角定位。
3. 思考题：为什么"距离排序 + offset 分页"会重复/漏项？构造"用户移动导致排序变化"的用例说明。

---

### 67-07 电子围栏：点在多边形内

**知识点**
- **电子围栏（Geo-fencing）是"判断一个点是否在指定区域内"**：配送范围、禁停区、商圈、行政区域——**区域用多边形表示**（顶点数组），**判断用射线法**（Ray Casting）。
- **射线法的原理**：从点向任意方向发一条射线，**数它与多边形边的交点个数**——奇数在内部，偶数在外部；**实现要注意边界情况**（顶点在射线上、水平边、射线过顶点）。**标准做法是取"半开区间"**（`(y1 > y) != (y2 > y)`）避免重复计数。
- **圆形的围栏更简单**：`Haversine(点, 圆心) <= 半径`——**配送范围常用圆形**（简单、快），多边形用于**不规则区域**（行政区、禁停区）。
- **围栏的工程用法**：**进/出围栏事件**（骑手进入配送区 -> 开始计时）、**多点判断**（一个点是否在"任一"区域内 -> 遍历；在"全部"区域内 -> 原子判定）、**围栏的索引**（先按外接矩形粗筛，再精算 67-03/67-05）。
- **与 67-05 的关系**：多边形是"任意形状"，R 树用 MBR 组织它——**查询时先用 MBR 相交粗筛，再射线法精算**。
- 易错点：射线法边界处理错（顶点上重复计数）；多边形顶点顺序影响（顺时针/逆时针不影响奇偶判断，但影响面积计算）；用矩形近似不规则区域（边界外误判）；围栏判定不用外接矩形预筛（多边形边数多时慢）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

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
```

**课后练习**
1. 实现多边形围栏判定：用 20 个测试点（含顶点上、边中点、凹多边形内部缺口）验证射线法正确。
2. 实现"多点围栏"：判断一个点是否在 100 个多边形中的任意一个（用外接矩形预筛 + 精确判定），对比暴力遍历耗时。
3. 思考题：为什么射线法要把 Y 比较写成半开区间？构造"射线正好穿过顶点"的用例说明重复计数。

---

### 67-08 路径规划基础：图与最短路

**知识点**
- **路网就是一张图**：交叉口是顶点、道路是带权边（权重=距离/时间）——**路径规划 = 图上的最短路问题**（34 章的图论在 LBS 的应用）。
- **三种最短路算法**：**Dijkstra**（单源最短路，非负权，用优先队列 O((V+E)logV)）；**A\***（Dijkstra + 启发式（直线距离下界），目标明确时更快）；**Floyd**（全源，O(V³)，小图或预处理用）。
- **A\* 的启发函数是关键**：**必须"可采纳"（不高估真实距离）**——欧氏/直线距离是合法启发；**高估会导致次优路径**（如用 0.5×直线距离）。
- **工程化的四个问题**：**图太大**（全国路网千万节点 -> 分层图/预处理（Contraction Hierarchies））、**动态权重**（实时路况 -> 定期重算/分层缓存）、**多目标**（最短/最快/少收费 -> 多权重）、**批量查询**（算路缓存）。
- **与 34 章的关系**：34 章讲 Dijkstra 的原理，本章讲"路网建模 + 启发式 + 工程约束"——**从算法到服务**。
- 易错点：A\* 启发函数高估（次优解）；权重为负（Dijkstra 失效，需 Bellman-Ford）；图未考虑单向道路（导航逆行）；每次查询都跑全图（无预处理/缓存）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

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
```

**课后练习**
1. 实现 Dijkstra 与 A\*，用 20 节点路网对比"访问节点数"与"最终距离"，验证启发函数可采纳时结果一致。
2. 构造一个"启发函数高估"（用 1.5× 直线距离）的 A\*，验证得到次优路径，说明可采纳性要求。
3. 思考题：为什么全国路网不能每次查询都跑 Dijkstra？从"节点规模"与"实时性要求"两个角度分析，并给出分层预处理的思路。

---

### 67-09 位置数据模型与存储

**知识点**
- **位置数据的两种形态**：**轨迹流**（骑手/车辆每秒上报的时序点，高频写、按时间范围查）与**实体当前位置**（店铺/用户的最新坐标，覆盖写、按空间查）——**两者存储模型完全不同**。
- **轨迹存储**：**时序表**（entityId + ts + lat + lng，按 (entityId, ts) 聚簇），**冷热分离**（近 7 天热存，更早压缩归档 39-10）；**写入要批量**（每秒百万点 -> 攒批 62-05）；**查询是"某实体某时间段的轨迹"**（范围扫描）。
- **当前位置存储**：**Redis GEO**（`GEOADD`/`GEOSEARCH`，底层 GeoHash + 跳表）或**内存网格索引**（67-05）——**覆盖写 + TTL**（离线自动过期 65-09）。
- **与 39 章的对应**：**分片键 = entityId（轨迹）或 geohash 前缀（当前位置）**；**热点格子倾斜**（市中心）要拆格；**归档**（历史轨迹进对象存储/湖表 46 章）。
- **位置数据的体量估算**：1 万骑手 × 每秒 1 点 = 每天 8.6 亿点——**每点 (id+ts+lat+lng) 约 32 字节 = 每天 27GB**——**必须压缩与冷热分离**（52-01 的容量估算）。
- 易错点：轨迹表按时间分片（跨实体查询要广播）；当前位置存 MySQL（高频覆盖写打爆 DB）；无 TTL（离线骑手永远"在线"）；不做冷热分离（存储成本失控）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocationStorage {
    record TrackPoint(String entityId, long tsMs, double lat, double lng) {
        int sizeBytes() {
            return entityId.length() + 8 + 8 + 8;
        }
    }

    /** 轨迹：按 entityId 聚簇，按时间排序 */
    static final Map<String, List<TrackPoint>> trackStore = new LinkedHashMap<>();

    static void appendTrack(TrackPoint p) {
        trackStore.computeIfAbsent(p.entityId(), k -> new ArrayList<>()).add(p);
    }

    /** 查某实体某时间段的轨迹（单实体 -> 单分区扫描） */
    static List<TrackPoint> queryTrack(String entityId, long fromMs, long toMs) {
        List<TrackPoint> out = new ArrayList<>();
        for (TrackPoint p : trackStore.getOrDefault(entityId, List.of())) {
            if (p.tsMs() >= fromMs && p.tsMs() <= toMs) {
                out.add(p);
            }
        }
        return out;
    }

    /** 当前位置：覆盖写 + TTL（Redis GEO 的语义） */
    static final Map<String, TrackPoint> currentPos = new LinkedHashMap<>();
    static final Map<String, Long> ttl = new LinkedHashMap<>();

    static void updatePosition(TrackPoint p, long nowMs) {
        currentPos.put(p.entityId(), p);
        ttl.put(p.entityId(), nowMs + 60_000);
    }

    static TrackPoint queryCurrent(String entityId, long nowMs) {
        Long expireAt = ttl.get(entityId);
        if (expireAt == null || expireAt < nowMs) {
            return null;
        }
        return currentPos.get(entityId);
    }

    static void capacityEstimate() {
        System.out.println("【容量估算：1 万骑手每秒 1 点】");
        long riders = 10_000;
        long pointsPerDay = riders * 86_400;
        TrackPoint sample = new TrackPoint("r-10000", 1L, 30.0, 120.0);
        long dailyBytes = pointsPerDay * sample.sizeBytes();
        System.out.printf("  每日点量 = %,d（%.1f 亿）%n", pointsPerDay, pointsPerDay / 1e8);
        System.out.printf("  单点约 %d 字节 -> 每日 %.1f GB，每月 %.0f GB%n",
                sample.sizeBytes(), dailyBytes / 1024.0 / 1024 / 1024,
                dailyBytes * 30 / 1024.0 / 1024 / 1024);
        System.out.println("  对策：批量写 + 压缩（列存/编码）+ 冷热分离（39-10）+ 归档到湖（46 章）");
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【轨迹写入与查询】");
        long base = 1_000_000L;
        for (int i = 0; i < 10; i++) {
            appendTrack(new TrackPoint("rider-1", base + i * 1000L,
                    30.27 + i * 0.0001, 120.15 + i * 0.0001));
        }
        appendTrack(new TrackPoint("rider-2", base + 5000L, 30.28, 120.16));
        List<TrackPoint> seg = queryTrack("rider-1", base + 3000, base + 6000);
        System.out.printf("  rider-1 在 [%d,%d] 的轨迹：%d 个点%n", base + 3000, base + 6000, seg.size());
        seg.forEach(p -> System.out.printf("    ts=%d (%.4f, %.4f)%n", p.tsMs(), p.lat(), p.lng()));

        System.out.println();
        System.out.println("【当前位置：覆盖写 + TTL】");
        long now = 2_000_000L;
        updatePosition(new TrackPoint("rider-1", now, 30.2799, 120.1599), now);
        System.out.println("  查询（TTL 内）：" + queryCurrent("rider-1", now + 10_000));
        System.out.println("  查询（TTL 过期）：" + queryCurrent("rider-1", now + 70_000));

        System.out.println();
        capacityEstimate();
        System.out.println("【存储选型】");
        Map<String, String> choice = new LinkedHashMap<>();
        choice.put("轨迹", "时序表（entityId, ts 聚簇）+ 冷热分离 + 归档湖表");
        choice.put("当前位置", "Redis GEO 或内存网格（覆盖写 + TTL）");
        choice.put("历史查询", "按实体+时间范围扫单分区（分片键 entityId）");
        choice.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 实现轨迹存储：按 entityId 聚簇 + 时间段查询 + 批量写入，用 1 万点验证"单实体查询只扫一个分区"。
2. 做一次容量估算：你业务的骑手/车辆数 × 上报频率，算出每日/每月存储量，并给出三条降本措施。
3. 思考题：为什么"当前位置"适合 Redis 而不适合 MySQL？从"高频覆盖写 + TTL + 按空间查"三个特征分析。

---

### 67-10 位置数据的隐私与合规

**知识点**
- **位置是"最敏感的个人信息"之一**：它能推断住址、工作地、作息、社交关系、健康状况——**《个人信息保护法》把"行踪轨迹"列为敏感个人信息**（需单独同意 + 最小必要）。
- **四类技术防护**：**精度降级**（返回模糊距离/低精度坐标 67-06）、**位置模糊化**（加随机噪声/网格化到 500m 格）、**k-匿名**（同一位置至少 k 个用户，无法区分个体——68-05）、**差分隐私**（发布统计时加拉普拉斯噪声——68-07）。
- **防三角定位**（trilateration）：攻击者用**多个"我的位置"查询同一目标**，用距离圆求交点反推精确坐标——**对策**：限制查询频率、距离分档（<1km 不细分）、限制同一目标的可查询次数、**只在"双方互相可见"时返回位置**（社交产品的核心策略）。
- **存储与传输的合规**：**加密存储**（68-04）、**最小保留期**（轨迹通常 30~90 天，68-10 遗忘权）、**访问审计**（谁查了谁的位置 68-09）、**不出境**（68-06）。
- **与 36/55 章的关系**：36 章讲"漏洞"（越权可查位置 = IDOR 的 LBS 版，67-06 的双键校验）；55-10 讲"SaaS 合规"——**位置隐私是合规最严格的子集**。
- 易错点：返回精确坐标（该模糊却给原值）；不限制查询频率（三角定位可行）；轨迹无限保留（合规风险）；"附近的人"默认展示（应双向授权）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocationPrivacy {
    /** 位置模糊化：网格化到指定精度 */
    static double[] gridSnap(double lat, double lng, double gridDeg) {
        return new double[]{Math.round(lat / gridDeg) * gridDeg,
                Math.round(lng / gridDeg) * gridDeg};
    }

    /** 加噪声（简化版差分隐私直觉：均匀噪声） */
    static double[] addNoise(double lat, double lng, double maxOffsetDeg) {
        double noiseLat = (Math.random() * 2 - 1) * maxOffsetDeg;
        double noiseLng = (Math.random() * 2 - 1) * maxOffsetDeg;
        return new double[]{lat + noiseLat, lng + noiseLng};
    }

    /** 距离开关：分档不细分 */
    static String distanceBucket(double km) {
        if (km < 1) {
            return "<1km";
        }
        if (km < 3) {
            return "1~3km";
        }
        if (km < 10) {
            return "3~10km";
        }
        return ">10km";
    }

    /** 查询频率限制（防三角定位） */
    static class QueryLimiter {
        final Map<String, List<Long>> history = new LinkedHashMap<>();
        final int maxPerMinute;
        final long windowMs = 60_000;

        QueryLimiter(int maxPerMinute) {
            this.maxPerMinute = maxPerMinute;
        }

        boolean allow(String querier, String target, long now) {
            String key = querier + "->" + target;
            List<Long> times = history.computeIfAbsent(key, k -> new ArrayList<>());
            times.removeIf(t -> now - t > windowMs);
            if (times.size() >= maxPerMinute) {
                return false;
            }
            times.add(now);
            return true;
        }
    }

    /** 双向授权：只有互相可见才返回位置（社交产品标准策略） */
    static String queryPosition(String querier, String target,
                                boolean targetAuthorized, long now,
                                QueryLimiter limiter) {
        if (!targetAuthorized) {
            return "拒绝：对方未授权向你展示位置";
        }
        if (!limiter.allow(querier, target, now)) {
            return "拒绝：查询过于频繁（防三角定位）";
        }
        double[] noisy = addNoise(30.2741, 120.1551, 0.002);   // 约 ±200m
        return String.format("允许：距离档位=%s（坐标已加噪，不返回精确值）",
                distanceBucket(1.2));
    }

    public static void main(String[] args) {
        System.out.println("【位置模糊化】");
        double[] snapped = gridSnap(30.27413, 120.15512, 0.005);
        System.out.printf("  原始 (30.27413, 120.15512)%n");
        System.out.printf("  网格化(0.005°) -> (%.4f, %.4f)（约 500m 精度）%n", snapped[0], snapped[1]);
        double[] noisy = addNoise(30.27413, 120.15512, 0.002);
        System.out.printf("  加噪(±200m) -> (%.4f, %.4f)%n", noisy[0], noisy[1]);

        System.out.println();
        System.out.println("【距离分档（不细分）】");
        for (double d : new double[]{0.3, 0.9, 2.5, 8, 15}) {
            System.out.printf("  实际 %.1fkm -> 展示 %s%n", d, distanceBucket(d));
        }

        System.out.println();
        System.out.println("【双向授权 + 频率限制】");
        QueryLimiter limiter = new QueryLimiter(3);
        long now = 1_000_000;
        System.out.println("  未授权：" + queryPosition("u-1", "u-2", false, now, limiter));
        for (int i = 1; i <= 4; i++) {
            System.out.printf("  第%d次：%s%n", i,
                    queryPosition("u-1", "u-2", true, now + i * 1000L, limiter));
        }

        System.out.println();
        System.out.println("【合规清单】");
        Map<String, String> compliance = new LinkedHashMap<>();
        compliance.put("告知同意", "位置属敏感个人信息，需单独同意");
        compliance.put("最小必要", "只采集业务必需精度与频率");
        compliance.put("存储", "加密 + 保留期（30~90 天）+ 到期删除（68-10）");
        compliance.put("访问", "审计每次查询（谁查谁，68-09）+ 双向授权");
        compliance.put("出境", "位置不跨境传输（68-06）");
        compliance.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}
```

**课后练习**
1. 实现位置隐私四件套（网格化/加噪/距离分档/频率限制），用 10 次查询验证三角定位被阻断。
2. 计算"三角定位"所需的最少查询次数与精度要求，说明"距离分档"为什么能破坏三角定位。
3. 思考题：为什么"附近的人"必须双向授权？从"单方可见"可能带来的骚扰与跟踪风险分析。

---

### 67-11 综合案例：外卖配送范围服务

**知识点**
- **把本章收口成一个可运行的 LBS 服务**：**坐标统一**（存储 WGS84，展示转 GCJ-02 67-02）-> **距离计算**（Haversine 精算 + 边界框预筛 67-03）-> **范围判定**（圆形配送圈 + 多边形禁送区 67-07）-> **附近商家**（GeoHash 粗筛 + 精算排序 + 分页 67-06）-> **骑手位置**（当前位置 TTL + 轨迹存储 67-09）-> **隐私**（距离模糊 + 频率限制 67-10）。
- **业务规则的分层**：**先粗后细**——GeoHash 格 -> 边界框 -> Haversine -> 多边形精确判定；**每层都能早期剪枝**，这是"百万商家 + 每秒千次查询"能扛住的原因（39-02 的裁剪思想贯穿）。
- **配送范围的三种表达**：**圆形**（半径 3km，最常见）、**多边形**（不规则商圈/禁送区）、**栅格**（预先计算可配送格集合，查询 O(1)）——**大促/高峰期用栅格预计算**（空间换时间）。
- **服务能力验收**：**正确性**（边界点判定一致、坐标系一致）、**性能**（P99 < 50ms）、**隐私**（不返回精确坐标、防三角定位）、**可运维**（范围可配置、变更可审计）。
- 易错点：范围判定只用圆形（禁送区/河流误判）；坐标未统一（范围偏移几百米）；查询不预筛（全表算距离）；配送范围写死在代码（运营改不了）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
```

**课后练习**
1. 端到端实现配送范围服务（坐标统一 + 禁送区 + 半径 + 模糊展示），用 10 个测试点验证判定正确。
2. 实现"栅格预计算"：把 3km 范围离散成 500m 格，预先算好每格是否可配送，对比"实时判定"的耗时。
3. 思考题：为什么大促期要用"栅格预计算"而不是每次实时算多边形？从查询量与计算复杂度的权衡分析。
