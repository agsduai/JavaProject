/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-05
 * 课名：发布期的数据兼容：expand-contract
 * ------------------------------------------------------------
 * 知识点：
 *   - **滚动/灰度期间新旧版本同时在线**：**旧代码读新表结构会崩，新代码读旧数据会崩**——**所以数据库变更必须拆成「先兼容、再切换、后清理」三步**。
 *   - **Expand（扩展）**：**加新字段/新表，新旧代码都能跑**——**新字段必须可空或有默认值**，**旧代码不认识它就当不存在**。
 *   - **Migrate（双写/回填）**：**新代码同时写新旧两处，后台把历史数据回填**——**回填要能重跑、要限速**，**不能把库打挂**。
 *   - **Contract（收缩）**：**确认所有实例都是新版本、所有数据都回填完，才能删旧字段**——**删早了，还没升级的实例立刻 500**。
 *   - **三条铁律**：**加字段不删字段、改语义要加新字段、删字段要等两个发布周期**——**「两个周期」是给回滚留的余地**。
 *   - 易错点：一个发布里同时加字段又删字段；新字段设 NOT NULL 无默认值（旧代码插入失败）；回填不限速（打挂主库）；以为「代码回滚了数据也回来了」。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchemaCompat {
    enum Phase { EXPAND, MIGRATE, CONTRACT }

    // 一行数据：旧字段 price(分)，新字段 priceYuan(字符串，带两位小数)
    static class Row {
        Integer priceFen;          // 旧字段，可空
        String priceYuan;          // 新字段，可空

        Row(Integer priceFen, String priceYuan) {
            this.priceFen = priceFen;
            this.priceYuan = priceYuan;
        }
    }

    static class Table {
        final List<Row> rows = new ArrayList<>();
        Phase phase = Phase.EXPAND;
        int errors;

        // 旧代码：只认 priceFen
        void writeOld(int fen) {
            Row r = new Row(fen, null);
            if (phase == Phase.CONTRACT) {
                errors++;
                System.out.println("    [旧代码写] 失败：priceFen 字段已被删除（NPE / Unknown column）");
                return;
            }
            rows.add(r);
        }

        // 新代码：两个都写
        void writeNew(int fen) {
            String yuan = String.format("%d.%02d", fen / 100, fen % 100);
            rows.add(new Row(phase == Phase.CONTRACT ? null : fen, yuan));
        }

        // 旧代码读：优先旧字段
        String readOld(int idx) {
            Row r = rows.get(idx);
            if (r.priceFen == null) {
                errors++;
                return "<读不到 priceFen>";
            }
            return "RMB " + String.format("%.2f", r.priceFen / 100.0);
        }

        // 新代码读：优先新字段，回退旧字段（兼容未回填的数据）
        String readNew(int idx) {
            Row r = rows.get(idx);
            if (r.priceYuan != null) {
                return "RMB " + r.priceYuan;
            }
            if (r.priceFen != null) {
                return "RMB " + String.format("%.2f", r.priceFen / 100.0) + "(旧字段回退)";
            }
            return "<两个字段都空>";
        }

        int backfill() {
            int n = 0;
            for (Row r : rows) {
                if (r.priceYuan == null && r.priceFen != null) {
                    r.priceYuan = String.format("%d.%02d", r.priceFen / 100, r.priceFen % 100);
                    n++;
                }
            }
            return n;
        }
    }

    public static void main(String[] args) {
        Table t = new Table();

        System.out.println("【阶段1 EXPAND】加新字段 priceYuan（可空），旧代码照常跑");
        t.writeOld(1999);
        t.writeOld(2580);
        System.out.println("  旧代码写入 2 行，新代码读取：");
        System.out.println("    row0 -> " + t.readNew(0));
        System.out.println("    row1 -> " + t.readNew(1));

        System.out.println();
        System.out.println("【阶段2 MIGRATE】新代码双写 + 回填历史数据");
        t.phase = Phase.MIGRATE;
        t.writeNew(3999);
        int filled = t.backfill();
        System.out.println("  双写 1 行，回填历史 " + filled + " 行");
        for (int i = 0; i < t.rows.size(); i++) {
            System.out.printf("    row%d 旧代码读 %-10s 新代码读 %s%n",
                    i, t.readOld(i), t.readNew(i));
        }
        System.out.println("  -> 两个版本读出来的价格完全一致，可以开始收缩");

        System.out.println();
        System.out.println("【阶段3 CONTRACT】删除旧字段 priceFen");
        t.phase = Phase.CONTRACT;
        for (Row r : t.rows) {
            r.priceFen = null;                    // 列被 DROP，历史行的旧值也一起没了
        }
        System.out.println("  如果此时还有旧实例在线：");
        t.writeOld(1000);
        System.out.println("  旧代码读历史行：" + t.readOld(0));
        System.out.println("  新代码不受影响：" + t.readNew(0));
        System.out.printf("  -> 累计错误 %d 次；这就是为什么删字段要等所有实例都升级完%n", t.errors);

        System.out.println();
        System.out.println("【错误做法对照】一个发布里同时加新字段 + 删旧字段");
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("发布中", "旧实例还在跑，新表结构已经生效");
        bad.put("旧实例读", "SELECT priceFen -> Unknown column，接口 500");
        bad.put("回滚", "代码回滚了，但字段已经被 DROP，数据回不来");
        bad.put("正确做法", "expand -> migrate -> contract 三个发布，每步都可回滚");
        bad.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 CONTRACT 阶段改成「先停旧实例再删字段」，重跑，验证错误次数变成 0。
 * 2. 给 backfill 加限速（每批 100 行、批次间隔 50ms），统计 1 万行数据要回填多久。
 * 3. 思考题：为什么「改字段语义」也要加新字段，而不是直接改旧字段的含义？从「旧代码如何解释这个值」分析。
 * ============================================================
 */

