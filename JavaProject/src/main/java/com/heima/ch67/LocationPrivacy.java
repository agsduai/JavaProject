/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-10
 * 课名：位置数据的隐私与合规
 * ------------------------------------------------------------
 * 知识点：
 *   - **位置是"最敏感的个人信息"之一**：它能推断住址、工作地、作息、社交关系、健康状况——**《个人信息保护法》把"行踪轨迹"列为敏感个人信息**（需单独同意 + 最小必要）。
 *   - **四类技术防护**：**精度降级**（返回模糊距离/低精度坐标 67-06）、**位置模糊化**（加随机噪声/网格化到 500m 格）、**k-匿名**（同一位置至少 k 个用户，无法区分个体——68-05）、**差分隐私**（发布统计时加拉普拉斯噪声——68-07）。
 *   - **防三角定位**（trilateration）：攻击者用**多个"我的位置"查询同一目标**，用距离圆求交点反推精确坐标——**对策**：限制查询频率、距离分档（<1km 不细分）、限制同一目标的可查询次数、**只在"双方互相可见"时返回位置**（社交产品的核心策略）。
 *   - **存储与传输的合规**：**加密存储**（68-04）、**最小保留期**（轨迹通常 30~90 天，68-10 遗忘权）、**访问审计**（谁查了谁的位置 68-09）、**不出境**（68-06）。
 *   - **与 36/55 章的关系**：36 章讲"漏洞"（越权可查位置 = IDOR 的 LBS 版，67-06 的双键校验）；55-10 讲"SaaS 合规"——**位置隐私是合规最严格的子集**。
 *   - 易错点：返回精确坐标（该模糊却给原值）；不限制查询频率（三角定位可行）；轨迹无限保留（合规风险）；"附近的人"默认展示（应双向授权）。
 * ============================================================
 */

package com.heima.ch67;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现位置隐私四件套（网格化/加噪/距离分档/频率限制），用 10 次查询验证三角定位被阻断。
 * 2. 计算"三角定位"所需的最少查询次数与精度要求，说明"距离分档"为什么能破坏三角定位。
 * 3. 思考题：为什么"附近的人"必须双向授权？从"单方可见"可能带来的骚扰与跟踪风险分析。
 * ============================================================
 */
