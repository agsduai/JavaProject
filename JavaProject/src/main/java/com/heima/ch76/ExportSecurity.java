/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-10
 * 课名：导出安全与审计
 * ------------------------------------------------------------
 * 知识点：
 *   - **导出的四类风险**：**越权导出**（导出全量而非自己可见的数据 74-05）、**敏感字段泄露**（手机号/身份证未脱敏 68-04）、**大批量拉取**（内部人员一次导 100 万条，44-08 的配额）、**文件外泄**（下载链接被转发 73-06）——**导出是数据泄露的高发口**，必须专项治理。
 *   - **导出前必须做的三件事**：**行级权限**（只能导出"你能看到的"）、**列级脱敏**（敏感列按角色遮蔽）、**数量配额**（每人每日导出上限，超限拦截）——**与 68 章隐私工程同源**。
 *   - **审计要记全**：**谁**（userId）、**何时**（时间）、**导了什么**（报表类型 + 筛选条件）、**多少行**（数量）、**结果**（成功/失败/被拒）、**文件 ID**（可追溯）——**审计日志不可篡改**（68-09）。
 *   - **水印与追溯**：导出文件加**隐形水印**（含 userId/时间），泄露后可溯源——**"防不住就追得到"**是数据安全的基本策略。
 *   - 与 36/68 章的关系：导出安全 = **输入校验（条件注入）+ 权限（行/列）+ 脱敏 + 配额 + 审计**——**是全书安全能力的汇总演练**。
 *   - 易错点：导出不分权限（全量导出）；敏感列不脱敏（手机号明文）；无配额（内部人批量拉取）；审计只记"导出成功"（不记条件和行数）。
 * ============================================================
 */

package com.heima.ch76;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class ExportSecurity {
    record Order(long id, String customer, String phone, String idCard, double amount,
                 String deptId) {
    }

    record Operator(String userId, String role, Set<String> deptScope, int dailyQuota) {
    }

    static final List<Order> allOrders = new ArrayList<>();

    static {
        allOrders.add(new Order(1, "张三", "13800001111", "110101199001011234", 69.0, "d-1"));
        allOrders.add(new Order(2, "李四", "13900002222", "310101199202022345", 129.0, "d-1"));
        allOrders.add(new Order(3, "王五", "13700003333", "440101199303033456", 39.0, "d-2"));
        allOrders.add(new Order(4, "赵六", "13600004444", "510101199404044567", 199.0, "d-2"));
        allOrders.add(new Order(5, "钱七", "13500005555", "330101199505055678", 88.0, "d-3"));
    }

    static String maskPhone(String p) {
        return p.substring(0, 3) + "****" + p.substring(7);
    }

    static String maskIdCard(String id) {
        return id.substring(0, 6) + "********" + id.substring(14);
    }

    /** 列级脱敏：按角色决定敏感列是否可见 */
    static Map<String, Object> project(Order o, String role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", o.id());
        row.put("customer", o.customer());
        row.put("amount", o.amount());
        if (role.equals("admin") || role.equals("auditor")) {
            row.put("phone", o.phone());
            row.put("idCard", o.idCard());
        } else {
            row.put("phone", maskPhone(o.phone()));
            row.put("idCard", maskIdCard(o.idCard()));
        }
        return row;
    }

    /** 行级权限：只能导出 deptScope 内的数据 */
    static List<Order> filterRows(Operator op) {
        List<Order> out = new ArrayList<>();
        for (Order o : allOrders) {
            if (op.role().equals("admin") || op.deptScope().contains(o.deptId())) {
                out.add(o);
            }
        }
        return out;
    }

    /** 配额：每日导出上限（生产用 Redis 计数） */
    static final Map<String, Integer> usedToday = new LinkedHashMap<>();

    static String checkQuota(Operator op, int requested) {
        int used = usedToday.getOrDefault(op.userId(), 0);
        if (used + requested > op.dailyQuota()) {
            return "拒绝：超出每日配额（已用 " + used + " / 上限 " + op.dailyQuota() + "）";
        }
        usedToday.put(op.userId(), used + requested);
        return "通过：已用 " + (used + requested) + " / " + op.dailyQuota();
    }

    record Audit(String userId, String report, int rows, String result, String fileId) {
    }

    static final List<Audit> audits = new ArrayList<>();

    public static void main(String[] args) {
        Operator staff = new Operator("u-1", "staff", Set.of("d-1"), 100);
        Operator manager = new Operator("u-2", "admin", Set.of(), 10_000);

        System.out.println("【行级权限】");
        System.out.println("  staff（d-1）可见行：" + filterRows(staff).size() + " / " + allOrders.size());
        System.out.println("  admin 可见行：" + filterRows(manager).size() + " / " + allOrders.size());

        System.out.println();
        System.out.println("【列级脱敏】");
        System.out.println("  staff 视角：" + project(allOrders.get(0), "staff"));
        System.out.println("  admin 视角：" + project(allOrders.get(0), "admin"));

        System.out.println();
        System.out.println("【配额】");
        System.out.println("  staff 申请 50 行：" + checkQuota(staff, 50));
        System.out.println("  staff 再申请 60 行：" + checkQuota(staff, 60));
        System.out.println("  admin 申请 5000 行：" + checkQuota(manager, 5000));

        System.out.println();
        System.out.println("【审计】");
        audits.add(new Audit("u-1", "订单明细", 2, "SUCCESS", "f-100"));
        audits.add(new Audit("u-1", "订单明细", 0, "DENY(quota)", "-"));
        audits.add(new Audit("u-2", "全量订单", 5, "SUCCESS", "f-101"));
        audits.forEach(a -> System.out.printf(
                "  user=%-4s report=%-8s rows=%-3d result=%-12s file=%s%n",
                a.userId(), a.report(), a.rows(), a.result(), a.fileId()));

        System.out.println();
        System.out.println("【水印】文件内嵌 userId+时间（不可见），泄露可溯源");
        String watermark = "u-1|2026-11-18T10:00|f-100";
        System.out.println("  watermark=" + watermark.hashCode() + "（写入文档属性/隐形图层）");

        System.out.println();
        String[][] defenses = {
                {"行级权限", "只能导出可见数据", "74-05 数据权限"},
                {"列级脱敏", "手机号/身份证遮蔽", "68-04"},
                {"配额", "每人每日上限", "44-08 限流"},
                {"审计", "谁/何时/条件/行数/结果", "68-09"},
                {"水印", "泄露可溯源", "68 隐私工程"},
        };
        for (String[] d : defenses) {
            System.out.printf("  %-8s %-24s %s%n", d[0], d[1], d[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现导出的"行级权限 + 列级脱敏 + 配额"三件套，用 staff/admin 两种角色验证差异。
 * 2. 设计审计日志字段（谁/何时/条件/行数/结果/文件），并说明"审计不可篡改"的实现思路（68-09）。
 * 3. 思考题：为什么"导出是数据泄露的高发口"？从"批量拉取/文件外泄/内部威胁"三个角度分析。
 * ============================================================
 */
