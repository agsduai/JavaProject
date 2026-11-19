/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：68-11
 * 课名：综合案例：用户数据全生命周期治理
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一套完整治理方案**：**采集**（最小必要 + 分级 68-02）-> **传输**（TLS + 敏感字段加密 68-04）-> **存储**（加密 + 脱敏 68-03 + 保留期）-> **使用**（行列权限 68-05 + 审计 68-09）-> **共享**（出境评估 68-06 + 隐私计算 68-07/08）-> **销毁**（删除策略 68-10）。
 *   - **治理的三个载体**：**数据字典**（字段级级别与控制，68-02）、**访问矩阵**（角色 × 数据 × 操作，68-05）、**审计链**（每次访问留痕，68-09）——**三者缺一不可**。
 *   - **验收五条**：**可清点**（知道有哪些敏感数据）、**可控权**（知道谁能看）、**可脱敏**（非授权场景自动降级）、**可追溯**（每次访问有记录）、**可删除**（全链路清除 + 凭证）。
 *   - **与全书的关系**：36 章防外部攻击、55-10 多租户合规、67-10 位置隐私、66-10 Agent 采集边界——**本章把它们统一为"数据治理框架"**。
 *   - 易错点：只有制度没有技术控制（纸面合规）；只有技术没有制度（开发者不知道边界）；清单不定期更新（新系统绕过治理）；审计与删除各自为政（无统一证据链）。
 * ============================================================
 */

package com.heima.ch68;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DataGovernanceCase {
    enum Level {
        L1, L2, L3, L4
    }

    record FieldRule(String field, Level level, List<String> allowedRoles,
                     String mask, long retentionDays) {
    }

    static final List<FieldRule> rules = new ArrayList<>();
    static final List<String> audit = new ArrayList<>();

    static {
        rules.add(new FieldRule("phone", Level.L3, List.of("agent", "risk", "admin"),
                "138****1234", 365));
        rules.add(new FieldRule("id_card", Level.L4, List.of("risk", "admin"),
                "1101**********34", 180));
        rules.add(new FieldRule("address", Level.L3, List.of("agent", "admin"),
                "杭州市***", 365));
        rules.add(new FieldRule("amount", Level.L2, List.of("agent", "risk", "admin", "analyst"),
                "原值", 1095));
    }

    /** 访问：权限判定 + 脱敏 + 审计 */
    static String access(String role, String field, String rawValue, long nowMs) {
        FieldRule rule = rules.stream().filter(r -> r.field().equals(field))
                .findFirst().orElse(null);
        if (rule == null) {
            audit.add(role + " ACCESS " + field + " DENIED(未登记字段)");
            return "拒绝：字段未登记分级";
        }
        if (!rule.allowedRoles().contains(role)) {
            audit.add(role + " ACCESS " + field + " DENIED(无权限)");
            return "拒绝：" + role + " 无权访问 " + field;
        }
        audit.add(role + " ACCESS " + field + " ALLOWED");
        boolean privileged = role.equals("admin") || role.equals("risk");
        return privileged ? rawValue : rule.mask();
    }

    /** 保留期检查：超期需归档或删除 */
    static void retentionCheck(long nowMs) {
        System.out.println("【保留期检查】");
        rules.forEach(r -> {
            long expireAt = r.retentionDays() * 86_400_000L;
            String status = nowMs > expireAt ? "已超期 -> 归档/删除（68-10）" : "有效期内";
            System.out.printf("  %-10s 级别=%-3s 保留=%4d 天 -> %s%n",
                    r.field(), r.level(), r.retentionDays(), status);
        });
        System.out.println();
    }

    static void acceptance() {
        System.out.println();
        System.out.println("【治理验收五条】");
        Map<String, String> gates = new LinkedHashMap<>();
        gates.put("可清点", "数据字典覆盖全部字段（68-02）");
        gates.put("可控权", "角色×字段访问矩阵（68-05）");
        gates.put("可脱敏", "非特权角色自动降级（68-03）");
        gates.put("可追溯", "每次访问写入审计链（68-09）");
        gates.put("可删除", "全链路清除 + 凭证（68-10）");
        gates.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println();
        System.out.println("  三个载体：数据字典 + 访问矩阵 + 审计链 —— 缺一不可");
    }

    public static void main(String[] args) {
        System.out.println("【字段级规则】");
        rules.forEach(r -> System.out.printf("  %-10s 级别=%-3s 允许=%s%n",
                r.field(), r.level(), r.allowedRoles()));

        System.out.println();
        System.out.println("【访问模拟】");
        String phone = "13800001234";
        String idCard = "110101199001011234";
        System.out.println("  agent 读 phone  -> " + access("agent", "phone", phone, 0));
        System.out.println("  agent 读 id_card-> " + access("agent", "id_card", idCard, 0));
        System.out.println("  risk  读 id_card-> " + access("risk", "id_card", idCard, 0));
        System.out.println("  admin 读 phone  -> " + access("admin", "phone", phone, 0));
        System.out.println("  analyst 读未知  -> " + access("analyst", "salary", "10000", 0));

        System.out.println();
        System.out.println("【审计链】");
        audit.forEach(a -> System.out.println("  " + a));
        System.out.println("  （真实系统：append-only + 哈希链，68-09）");

        retentionCheck(200L * 86_400_000L);
        acceptance();
        System.out.println();
        System.out.println("  一句话：隐私工程 = 制度（清单/审批）+ 技术（分级/脱敏/权限/加密/审计）+ 运营（复核/演练）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现治理框架（字典 + 矩阵 + 脱敏 + 审计 + 保留期），用 5 个访问请求验证允许/拒绝/脱敏三类结果。
 * 2. 为你业务画"数据流转图"（采集到销毁六阶段），标注每阶段的控制点与现有缺口。
 * 3. 思考题：为什么"三个载体缺一不可"？分别去掉字典/矩阵/审计，说明各自的失效后果。
 * ============================================================
 */
