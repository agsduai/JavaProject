/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-09
 * 课名：位置数据模型与存储
 * ------------------------------------------------------------
 * 知识点：
 *   - **位置数据的两种形态**：**轨迹流**（骑手/车辆每秒上报的时序点，高频写、按时间范围查）与**实体当前位置**（店铺/用户的最新坐标，覆盖写、按空间查）——**两者存储模型完全不同**。
 *   - **轨迹存储**：**时序表**（entityId + ts + lat + lng，按 (entityId, ts) 聚簇），**冷热分离**（近 7 天热存，更早压缩归档 39-10）；**写入要批量**（每秒百万点 -> 攒批 62-05）；**查询是"某实体某时间段的轨迹"**（范围扫描）。
 *   - **当前位置存储**：**Redis GEO**（`GEOADD`/`GEOSEARCH`，底层 GeoHash + 跳表）或**内存网格索引**（67-05）——**覆盖写 + TTL**（离线自动过期 65-09）。
 *   - **与 39 章的对应**：**分片键 = entityId（轨迹）或 geohash 前缀（当前位置）**；**热点格子倾斜**（市中心）要拆格；**归档**（历史轨迹进对象存储/湖表 46 章）。
 *   - **位置数据的体量估算**：1 万骑手 × 每秒 1 点 = 每天 8.6 亿点——**每点 (id+ts+lat+lng) 约 32 字节 = 每天 27GB**——**必须压缩与冷热分离**（52-01 的容量估算）。
 *   - 易错点：轨迹表按时间分片（跨实体查询要广播）；当前位置存 MySQL（高频覆盖写打爆 DB）；无 TTL（离线骑手永远"在线"）；不做冷热分离（存储成本失控）。
 * ============================================================
 */

package com.heima.ch67;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现轨迹存储：按 entityId 聚簇 + 时间段查询 + 批量写入，用 1 万点验证"单实体查询只扫一个分区"。
 * 2. 做一次容量估算：你业务的骑手/车辆数 × 上报频率，算出每日/每月存储量，并给出三条降本措施。
 * 3. 思考题：为什么"当前位置"适合 Redis 而不适合 MySQL？从"高频覆盖写 + TTL + 按空间查"三个特征分析。
 * ============================================================
 */
