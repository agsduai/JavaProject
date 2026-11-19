/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：63-05
 * 课名：拉链表与历史追溯
 * ------------------------------------------------------------
 * 知识点：
 *   - **拉链表是 SCD2 在数仓中的典型落地形式**：每行带 `start_date`/`end_date`（`end_date` 用极大值表示"至今有效"），**同一业务主键的多行时间区间不重叠、连续覆盖**——查询任意一天的状态就是"`start_date <= 某天 < end_date`"。
 *   - **拉链表的更新是"关旧开新"两步**：① 把"有变化的旧记录"的 `end_date` 改为变更日；② 插入新记录（`start_date` = 变更日，`end_date` = 极大值）——**两步在同一批次完成，保证区间连续无空洞**。
 *   - **拉链表的查询模式**：**查最新**（`end_date = 极大值`）、**查历史某天**（区间包含）、**查变化区间**（按 `start_date` 排序看版本链）——**分析场景"按当时状态统计"直接 join 区间条件**。
 *   - **拉链表与全量快照的取舍**：**全量快照**（每天一份全表）简单但存储爆炸（100 天 = 100 倍）；**拉链表**只存变化，存储小但要处理区间逻辑——**维度用拉链、事实用快照**是常见组合。
 *   - **拉链表的坑在"边界"**：变更日当天的归属（`start_date` 当天生效还是次日）、同日多次变更（合并为一次）、区间重叠检测（数据质量规则 46-08）。
 *   - 易错点：`end_date` 用 `NULL`（区间比较失效，应用极大值）；关旧开新不在同批（中间出现空洞/重叠）；同日多次变更产生多条（区间重叠）；拉链表的变更没有生效时间（历史错位）。
 * ============================================================
 */

package com.heima.ch63;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ZipperTable {
    static final long OPEN = Long.MAX_VALUE;

    record Row(String userId, String level, long startDate, long endDate) {
        boolean validAt(long day) {
            return startDate <= day && day < endDate;
        }

        boolean isOpen() {
            return endDate == OPEN;
        }
    }

    /** 关旧开新：同一批次完成 */
    static void upsert(List<Row> table, String userId, String newLevel, long changeDay) {
        for (int i = 0; i < table.size(); i++) {
            Row r = table.get(i);
            if (r.userId().equals(userId) && r.isOpen()) {
                if (r.level().equals(newLevel)) {
                    System.out.printf("  %s 等级未变（%s）-> 不产生新版本%n", userId, newLevel);
                    return;
                }
                table.set(i, new Row(r.userId(), r.level(), r.startDate(), changeDay));
            }
        }
        table.add(new Row(userId, newLevel, changeDay, OPEN));
        System.out.printf("  %s 变更为 %s（生效日 %d）：旧版本关闭 + 新版本开启%n",
                userId, newLevel, changeDay);
    }

    static Row stateAt(List<Row> table, String userId, long day) {
        for (Row r : table) {
            if (r.userId().equals(userId) && r.validAt(day)) {
                return r;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        List<Row> table = new ArrayList<>();
        table.add(new Row("u-1", "NORMAL", 0, OPEN));

        System.out.println("【拉链表变更轨迹】");
        upsert(table, "u-1", "VIP", 100);
        upsert(table, "u-1", "VIP", 150);          // 无变化 -> 无新版本
        upsert(table, "u-1", "SVIP", 200);

        table.sort(Comparator.comparingLong(Row::startDate));
        System.out.println();
        System.out.println("【拉链表内容】");
        for (Row r : table) {
            System.out.printf("  用户=%s 等级=%-7s [%d, %s)%n", r.userId(), r.level(),
                    r.startDate(), r.isOpen() ? "now" : String.valueOf(r.endDate()));
        }

        System.out.println();
        System.out.println("【按天查询历史状态】");
        for (long day : new long[]{50, 120, 250}) {
            Row r = stateAt(table, "u-1", day);
            System.out.printf("  第 %3d 天 -> %s%n", day, r == null ? "无记录" : r.level());
        }

        System.out.println();
        System.out.println("【区间完整性校验：不重叠、无空洞】");
        boolean ok = true;
        for (int i = 0; i < table.size() - 1; i++) {
            if (table.get(i).endDate() != table.get(i + 1).startDate()) {
                ok = false;
            }
        }
        System.out.println("  校验结果：" + (ok ? "连续覆盖" : "存在空洞或重叠（数据质量问题）"));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现拉链表完整更新：支持"同日多次变更合并"与"变更检测"，输出区间连续性校验结果。
 * 2. 实现查询：给定任意日期，按"当时的用户等级"统计订单量（事实表 join 拉链维度 + 区间条件）。
 * 3. 思考题：为什么维度用拉链表、事实用快照？从"维度变化慢、事实不可变"的数据特征分析存储与查询的取舍。
 * ============================================================
 */
