/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-04
 * 课名：时区与时间处理
 * ------------------------------------------------------------
 * 知识点：
 *   - **时间的三层概念**：**瞬时（Instant）**（UTC 时间点，唯一）、**本地时间（LocalDateTime）**（无时区的墙上时间）、**带时区的时间（ZonedDateTime）**——**存储用 Instant/UTC，展示用带时区**，这是铁律。
 *   - **时区的两大坑**：**夏令时（DST）**（某些时区一年切换两次，当天有 23 或 25 小时，`02:30` 可能不存在或出现两次）；**时区数据更新**（国家会改时区规则，JDK 的 tzdata 需要升级 35-08）。
 *   - **`java.time` 的正确用法**：`Instant.now()`（当前瞬时时）、`ZonedDateTime.ofInstant(instant, zone)`（转本地展示）、`Duration.between`（时长）、**禁止用 `new Date()` 做计算**（老 API 混乱）。
 *   - **跨时区的业务规则**：**"今天"的定义**（按用户时区还是服务器时区？）、**每日结算时间**（按哪个时区 00:00？）、**定时任务**（cron 按哪个时区？）——**必须显式声明**（61 章调度的时区问题）。
 *   - **与 43/61 章的关系**：流处理的"事件时间"（43-02）与时区无关（都是 UTC 毫秒）；调度的 cron（61-02）必须指定时区，否则跨时区出事故。
 *   - 易错点：用 `LocalDateTime` 存数据库（丢失时区信息）；用服务器默认时区格式化（不同机器不一致）；忽略 DST（定时任务在切换日跑错）；用 `new Date()` 加减天数（DST 下差一小时）。
 * ============================================================
 */

package com.heima.ch70;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class TimeZoneDemo {
    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    static final ZoneId UTC = ZoneId.of("UTC");

    static void threeConcepts() {
        System.out.println("【三层时间概念】");
        Instant instant = Instant.ofEpochMilli(1_763_000_000_000L);
        LocalDateTime local = LocalDateTime.ofInstant(instant, SHANGHAI);
        ZonedDateTime zoned = ZonedDateTime.ofInstant(instant, NEW_YORK);
        System.out.printf("  Instant（UTC 瞬时）：%s%n", instant);
        System.out.printf("  LocalDateTime（上海墙上时间）：%s%n", local);
        System.out.printf("  ZonedDateTime（纽约）：%s%n", zoned);
        System.out.println("  存储用 Instant（UTC 毫秒），展示用 ZonedDateTime（按用户时区）");
        System.out.println();
    }

    static void crossZoneDisplay() {
        System.out.println("【同一瞬时在不同时区展示】");
        Instant instant = Instant.ofEpochMilli(1_763_000_000_000L);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        Map<String, ZoneId> zones = new LinkedHashMap<>();
        zones.put("上海", SHANGHAI);
        zones.put("纽约", NEW_YORK);
        zones.put("伦敦", ZoneId.of("Europe/London"));
        zones.put("东京", ZoneId.of("Asia/Tokyo"));
        zones.forEach((name, zone) -> System.out.printf("  %-4s %s%n",
                name, ZonedDateTime.ofInstant(instant, zone).format(fmt)));
        System.out.println("  用户看到的「今天」取决于他的时区 -> 业务规则必须显式声明");
        System.out.println();
    }

    static void dst() {
        System.out.println("【夏令时（DST）：纽约 2026 年 3 月切换日】");
        ZonedDateTime before = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, NEW_YORK);
        ZonedDateTime after = before.plusHours(1);
        System.out.printf("  切换前：%s%n", before);
        System.out.printf("  加 1 小时后：%s（时间跳到了 3:30，2 点不存在）%n", after);
        System.out.printf("  实际经过：%s%n", Duration.between(before, after));
        System.out.println("  教训：不要用「加减小时」做时间计算，用 Instant/Duration");

        System.out.println();
        System.out.println("【「今天」的定义】");
        Instant now = Instant.ofEpochMilli(1_763_000_000_000L);
        for (Map.Entry<String, ZoneId> e : Map.of("上海", SHANGHAI).entrySet()) {
            System.out.printf("  %s 的今天：%s%n", e.getKey(),
                    LocalDateTime.ofInstant(now, e.getValue()).toLocalDate());
        }
        System.out.printf("  UTC 的今天：%s%n", LocalDateTime.ofInstant(now, UTC).toLocalDate());
        System.out.println("  两者可能差一天 -> 日切/结算/定时任务必须指定时区（61-02）");
    }

    public static void main(String[] args) {
        threeConcepts();
        crossZoneDisplay();
        dst();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现跨时区时间展示：同一 Instant 在 4 个时区格式化，验证日期可能不同。
 * 2. 复现 DST 事故：用 `LocalDateTime` 加减 24 小时，在 Nashville 切换日验证"实际不是 24 小时"。
 * 3. 思考题：为什么"每日 0 点结算"必须指定时区？用"用户分布多时区"的场景说明歧义。
 * ============================================================
 */
