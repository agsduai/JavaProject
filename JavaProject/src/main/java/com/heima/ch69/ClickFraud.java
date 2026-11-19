/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-08
 * 课名：反作弊：点击欺诈识别
 * ------------------------------------------------------------
 * 知识点：
 *   - **广告作弊的动机很直接：点击能收钱**——**CPC/CPA 模式下，每一次无效点击都是平台与广告主的资损**（51 章资金安全的广告版）。
 *   - **四类作弊**：**机器刷量**（脚本/模拟器批量点击）、**点击农场**（真人众包点击）、**竞争对手恶意点击**（消耗对方预算）、**归因作弊**（伪造 click_id 抢归因）。
 *   - **检测的四类特征**：**设备指纹**（同一设备/IP/IMEI 高频点击）、**行为特征**（点击间隔极短、无滚动/停留、转化率为 0）、**时序特征**（集中在某些 IP 段/时间段）、**归因特征**（点击到转化的时间分布异常）。
 *   - **处置分级**（58-10 的动作分级）：**观察**（记录不扣费）-> **过滤**（不计费不归因）-> **封禁**（设备/IP/账号）——**误伤要可申诉**（58-09 的申诉闭环）。
 *   - **与 58 章的关系**：反作弊就是"广告域的风控"——**规则引擎 + 特征 + 决策链完全同构**，只是特征与动作不同。
 *   - 易错点：只看点击量不看转化（刷点击但无转化）；阈值太严误伤真实用户（正常用户也可能快速点击）；只封设备不封账号（换设备继续刷）；作弊检测同步阻塞（拖慢请求，应异步 + 事后扣回）。
 * ============================================================
 */

package com.heima.ch69;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ClickFraud {
    record Click(String clickId, String deviceId, String ip, long atMs,
                 boolean converted, String adId) {
    }

    enum Verdict {
        NORMAL, SUSPICIOUS, FRAUD
    }

    /** 多维特征打分 */
    static Map<String, Integer> features(List<Click> clicks, String deviceId) {
        Map<String, Integer> score = new LinkedHashMap<>();
        List<Click> mine = clicks.stream()
                .filter(c -> c.deviceId().equals(deviceId)).toList();
        // 1) 点击频次：10 分钟内超过 20 次
        long windowMs = 600_000;
        long recent = mine.stream().filter(c -> c.atMs() >= mine.get(mine.size() - 1).atMs()
                - windowMs).count();
        score.put("高频点击", recent > 20 ? 40 : 0);
        // 2) 转化率：点击超过 10 次且 0 转化
        score.put("零转化", mine.size() > 10 && mine.stream().noneMatch(Click::converted) ? 30 : 0);
        // 3) 点击间隔：连续两次间隔 < 1 秒
        int rapid = 0;
        for (int i = 1; i < mine.size(); i++) {
            if (mine.get(i).atMs() - mine.get(i - 1).atMs() < 1000) {
                rapid++;
            }
        }
        score.put("秒级连点", rapid > 3 ? 25 : 0);
        // 4) IP 聚集：同 IP 设备数
        String ip = mine.get(mine.size() - 1).ip();
        long devicesOnIp = clicks.stream().filter(c -> c.ip().equals(ip))
                .map(Click::deviceId).distinct().count();
        score.put("IP 聚集", devicesOnIp > 20 ? 30 : 0);
        return score;
    }

    static Verdict judge(Map<String, Integer> score) {
        int total = score.values().stream().mapToInt(Integer::intValue).sum();
        if (total >= 70) {
            return Verdict.FRAUD;
        }
        if (total >= 40) {
            return Verdict.SUSPICIOUS;
        }
        return Verdict.NORMAL;
    }

    public static void main(String[] args) {
        List<Click> clicks = new ArrayList<>();
        // 正常设备：3 次点击，1 次转化，间隔正常
        for (int i = 0; i < 3; i++) {
            clicks.add(new Click("n-" + i, "device-normal", "10.0.0.1",
                    1_000_000L + i * 300_000L, i == 2, "ad-A"));
        }
        // 作弊设备：25 次秒级点击，0 转化
        for (int i = 0; i < 25; i++) {
            clicks.add(new Click("f-" + i, "device-fraud", "192.168.1.9",
                    2_000_000L + i * 500L, false, "ad-B"));
        }
        // 同 IP 大量设备（点击农场）
        for (int d = 0; d < 25; d++) {
            clicks.add(new Click("farm-" + d, "device-farm-" + d, "172.16.0.9",
                    3_000_000L + d * 2000L, false, "ad-C"));
        }

        System.out.println("【逐设备评估】");
        for (String device : new String[]{"device-normal", "device-fraud", "device-farm-0"}) {
            Map<String, Integer> f = features(clicks, device);
            Verdict v = judge(f);
            System.out.printf("  %-16s 特征=%s -> %s%n", device, f, v);
        }

        System.out.println();
        System.out.println("【处置动作】");
        Map<String, String> actions = new LinkedHashMap<>();
        actions.put("NORMAL", "正常计费与归因");
        actions.put("SUSPICIOUS", "记为观察态：不计费，样本留存待人工复核");
        actions.put("FRAUD", "过滤点击（不计费、不归因）+ 设备/IP 进黑名单 + 异步扣回已计费");
        actions.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));

        System.out.println();
        System.out.println("  原则：检测异步化（不阻塞竞价），处置可申诉（58-09），黑名单有 TTL（58-07）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现四特征打分器（频次/转化率/间隔/IP 聚集），用 3 类设备验证判定结果，并调整阈值观察误伤。
 * 2. 实现"异步扣回"：识别为作弊后，把已计费的点击金额从广告主账单中扣回，输出对账差异。
 * 3. 思考题：为什么反作弊要"异步检测 + 事后扣回"而不是"同步拦截"？从竞价延迟与误伤成本分析。
 * ============================================================
 */
