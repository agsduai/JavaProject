/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-08
 * 课名：活动时间窗口与时区
 * ------------------------------------------------------------
 * 知识点：
 *   - **活动时间必须用「绝对时间戳 + 明确时区」表达**：**「10 月 1 日 0 点」在代码里等于 `Instant`，不是 `LocalDateTime`**——**`LocalDateTime` 没有时区，落到不同机器上含义就变了**。
 *   - **服务器时区与用户时区可能不同**：**服务器跑 UTC、用户在东八区**，**用 `LocalDateTime.now()` 判活动开始，会提前 8 小时开抢或延后 8 小时结束**。
 *   - **夏令时会让「每天 0 点」这个表述本身失效**：**有的时区某天没有 0 点（时钟跳 2 点）**，**`atStartOfDay` 要处理「不存在的时间」与「重复的时间」两种情况**。
 *   - **活动开始/结束要「按区间判」而不是「按点判」**：**`[start, end)` 左闭右开**，**避免「结束那一刻还能下单、下一毫秒又不行」的边界争议**，**也避免同一秒被两个条件同时命中**。
 *   - **客户端时间不可信**：**用户改系统时间就能绕过「限时活动」**，**判定必须在服务端做，客户端只做展示**。
 *   - 易错点：用字符串比时间（`"2026-10-01" < "2026-10-1"` 为真）；活动结束时间写成 `23:59:59` 漏掉最后一秒；跨时区活动按本地时间各自开抢导致有人早 8 小时。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeWindow {
    record Window(String name, Instant start, Instant end) {
        // 左闭右开：start 可以下单，end 那一刻已经结束
        boolean contains(Instant t) {
            return !t.isBefore(start) && t.isBefore(end);
        }

        String show(ZoneId zone) {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return start.atZone(zone).format(f) + " ~ " + end.atZone(zone).format(f)
                    + " (" + zone + ")";
        }
    }

    public static void main(String[] args) {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        ZoneId utc = ZoneId.of("UTC");
        ZoneId newYork = ZoneId.of("America/New_York");

        System.out.println("【1. 同一个 Instant，在不同时区看到的「活动时间」】");
        Instant start = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, shanghai).toInstant();
        Instant end = ZonedDateTime.of(2026, 10, 8, 0, 0, 0, 0, shanghai).toInstant();
        Window w = new Window("国庆大促", start, end);
        System.out.println("  " + w.show(shanghai));
        System.out.println("  " + w.show(utc));
        System.out.println("  " + w.show(newYork));
        System.out.println("  时间戳是同一个（" + start.toEpochMilli() + "），只是显示不同。");

        System.out.println();
        System.out.println("【2. 用 LocalDateTime 判活动的坑：服务器跑 UTC】");
        LocalDateTime declared = LocalDateTime.of(2026, 10, 1, 0, 0);
        // 正确写法：明确按活动声明的时区解释
        Instant right = declared.atZone(shanghai).toInstant();
        // 错误写法：把「本地墙上时间」直接当 UTC 解释
        Instant wrong = declared.atZone(utc).toInstant();
        System.out.println("  运营声明：东八区 10-01 00:00 开始");
        System.out.printf("  正确解释（按东八区）：%s%n", right);
        System.out.printf("  错误解释（当成 UTC）  ：%s%n", wrong);
        long lateHours = Duration.between(right, wrong).toHours();
        System.out.printf("  两者相差 %+d 小时：按 UTC 解释会让活动晚开 %d 小时，用户到 08:00 才看到入口%n",
                lateHours, lateHours);

        // 反向的坑更常见：库里存的是 UTC 墙上时间，代码却按东八区解析
        LocalDateTime utcWall = LocalDateTime.of(2026, 9, 30, 16, 0);
        Instant misread = utcWall.atZone(shanghai).toInstant();
        System.out.printf("  反向坑：UTC 墙上时间 09-30 16:00 被当成东八区 -> %s%n", misread);
        System.out.printf("  比正确时刻早开 %d 小时，等于活动还没开始就放量了%n",
                Duration.between(misread, right).toHours());
        System.out.println("  结论：时间必须成对存「Instant + 活动时区」，绝不单独存 LocalDateTime。");

        System.out.println();
        System.out.println("【3. 左闭右开：结束那一刻到底算不算】");
        Instant justBefore = end.minusMillis(1);
        System.out.printf("  结束前 1ms  %s  -> %s%n", justBefore, w.contains(justBefore) ? "可下单" : "已结束");
        System.out.printf("  结束时刻    %s  -> %s%n", end, w.contains(end) ? "可下单" : "已结束");
        System.out.printf("  结束后 1ms  %s  -> %s%n", end.plusMillis(1), w.contains(end.plusMillis(1)) ? "可下单" : "已结束");
        System.out.println("  用 [start, end) 就没有「23:59:59 漏一秒」的问题。");

        System.out.println();
        System.out.println("【4. 夏令时：某些时区某天没有 00:00】");
        ZoneId santiago = ZoneId.of("America/Santiago");
        LocalDate d = LocalDate.of(2026, 9, 6);
        ZonedDateTime zdt = d.atStartOfDay(santiago);
        System.out.printf("  %s 在 %s 的「当天开始」= %s%n", d, santiago, zdt);
        System.out.println("  这一天时钟从 00:00 直接跳到 01:00，atStartOfDay 自动给出 01:00，不会抛异常。");
        ZonedDateTime naive = ZonedDateTime.of(d, java.time.LocalTime.MIDNIGHT, santiago);
        System.out.printf("  若强行写 00:00：%s（被自动调整为 %s）%n", naive, naive.toLocalTime());

        System.out.println();
        System.out.println("【5. 重复时间：秋季回拨那天的 01:30 出现两次】");
        ZoneId ny = ZoneId.of("America/New_York");
        LocalDateTime dup = LocalDateTime.of(2026, 11, 1, 1, 30);
        ZonedDateTime first = dup.atZone(ny).withEarlierOffsetAtOverlap();
        ZonedDateTime second = dup.atZone(ny).withLaterOffsetAtOverlap();
        System.out.printf("  第一次 01:30 -> %s%n", first);
        System.out.printf("  第二次 01:30 -> %s%n", second);
        System.out.printf("  两者相差 %d 分钟，是同一个本地时间的不同瞬间%n",
                Duration.between(first, second).toMinutes());

        System.out.println();
        System.out.println("【6. 客户端时间不可信】");
        // 用固定时刻模拟「服务端时钟」，保证输出可复现
        Instant serverNow = Instant.parse("2026-09-30T15:00:00Z");
        Instant clientSays = Instant.parse("2026-10-01T02:00:00Z");   // 客户端时钟被调快 3 小时
        System.out.printf("  服务端时钟 %s（东八区 %s）%n", serverNow,
                serverNow.atZone(shanghai).toLocalDateTime());
        System.out.printf("  客户端声称 %s，比服务端快 %d 小时%n", clientSays,
                Duration.between(serverNow, clientSays).toHours());
        System.out.printf("  若信客户端：%s（客户端以为已开始）%n",
                w.contains(clientSays) ? "允许下单" : "拒绝下单");
        System.out.printf("  若信服务端：%s（还差 %d 分钟）%n",
                w.contains(serverNow) ? "允许下单" : "拒绝下单",
                Duration.between(serverNow, start).toMinutes());
        System.out.println("  服务端只信自己的时钟；客户端时间只用来展示倒计时，绝不能参与「能不能买」的判定。");

        System.out.println();
        System.out.println("【7. 活动时长与「每天几点」的表达】");
        long days = Duration.between(start, end).toDays();
        System.out.printf("  活动跨度 %d 天（按绝对时间算，不受时区影响）%n", days);
        System.out.println("  如果需求是「每天 10 点开抢 1 小时」，就要按用户时区每天算窗口，");
        System.out.println("  不能写死一个绝对时间，否则跨时区用户会在半夜抢。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把活动改成「每天 20:00-22:00」，用 `ZonedDateTime` 生成 7 个窗口，验证每个窗口都是左闭右开。
 * 2. 给 Window 加一个 `remaining(Instant now)` 方法，返回剩余秒数，说明为什么不能用客户端时间算。
 * 3. 思考题：如果运营说「活动到 10 月 7 日为止」，你会把它解释成 10-07 00:00 还是 10-08 00:00？为什么必须问清楚？
 * ============================================================
 */

