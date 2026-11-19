/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：68-02
 * 课名：数据分类分级
 * ------------------------------------------------------------
 * 知识点：
 *   - **分类分级是一切隐私工作的起点**：不知道"哪些数据敏感"，脱敏/权限/审计都无从下手——**分级结果决定后续所有控制强度**。
 *   - **分类（按业务属性）与分级（按敏感程度）是两件事**：分类如"用户身份/交易/行为/设备"；分级通常四级：**公开（L1）/内部（L2）/敏感（L3）/高敏（L4）**——**《个人信息保护法》的"敏感个人信息"对应 L3/L4**（身份证、生物识别、行踪、健康、金融账户）。
 *   - **分级的影响字段**：**保留期**（L4 最短）、**加密要求**（L3/L4 必加密）、**访问审批**（L4 需二次审批）、**脱敏策略**（L3 展示脱敏、L4 默认不可见）、**出境**（L4 禁止）、**审计粒度**（L4 每次访问留痕）。
 *   - **分级的落地形态**：**数据字典标注**（字段级 `level` 元数据）+ **自动识别**（正则识别手机号/身份证，扫描存量表）+ **动态标注**（新建字段必须声明级别，CI 校验 46-08 契约思想）。
 *   - **与 46/55 章的衔接**：分级结果进元数据目录（46-04）；多租户场景下分级与租户隔离叠加（55-04）。
 *   - 易错点：只分"敏感/不敏感"两级（控制粒度不够）；分级只做一次（新字段不标注）；分级结果不落元数据（无法自动化）；把"脱敏后"的数据重新定为低级别（脱敏可逆则不可降级）。
 * ============================================================
 */

package com.heima.ch68;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

public class DataClassification {
    enum Level {
        L1_PUBLIC, L2_INTERNAL, L3_SENSITIVE, L4_HIGH
    }

    record Field(String table, String column, String category, Level level) {
        String control() {
            return switch (level) {
                case L1_PUBLIC -> "无特殊控制";
                case L2_INTERNAL -> "登录可见 + 常规日志";
                case L3_SENSITIVE -> "加密存储 + 展示脱敏 + 访问审计";
                case L4_HIGH -> "加密 + 默认不可见 + 双人审批 + 禁止出境";
            };
        }
    }

    static final List<Field> dictionary = new ArrayList<>();

    static void register() {
        dictionary.add(new Field("user", "phone", "用户身份", Level.L3_SENSITIVE));
        dictionary.add(new Field("user", "id_card", "用户身份", Level.L4_HIGH));
        dictionary.add(new Field("user", "nickname", "用户身份", Level.L1_PUBLIC));
        dictionary.add(new Field("order", "amount", "交易", Level.L2_INTERNAL));
        dictionary.add(new Field("order", "address", "用户身份", Level.L3_SENSITIVE));
        dictionary.add(new Field("location", "lat_lng", "行踪轨迹", Level.L4_HIGH));
        System.out.println("【数据字典（字段级分级）】");
        dictionary.forEach(f -> System.out.printf("  %-8s.%-10s %-8s %-14s %s%n",
                f.table(), f.column(), f.category(), f.level(), f.control()));
        System.out.println();
    }

    /** 自动识别：正则扫描未标注字段（存量治理） */
    static void autoDetect(List<String[]> samples) {
        Pattern phone = Pattern.compile("^1[3-9]\\d{9}$");
        Pattern idCard = Pattern.compile("^\\d{17}[\\dXx]$");
        System.out.println("【自动识别敏感字段】");
        for (String[] s : samples) {
            String level = "未识别";
            if (phone.matcher(s[1]).matches()) {
                level = "L3（手机号）";
            } else if (idCard.matcher(s[1]).matches()) {
                level = "L4（身份证）";
            }
            System.out.printf("  %-20s 样例=%-12s -> %s%n", s[0], mask(s[1]), level);
        }
        System.out.println();
    }

    static String mask(String v) {
        if (v == null || v.length() <= 4) {
            return "***";
        }
        return v.substring(0, 2) + "***" + v.substring(v.length() - 2);
    }

    static void governance() {
        System.out.println("【分级治理机制】");
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("新建字段必声明", "DDL 评审时必须在字典登记级别（CI 校验）");
        rules.put("存量扫描", "正则识别 + 人工复核，补齐历史字段");
        rules.put("脱敏不可逆原则", "脱敏后的数据不能降级（可逆脱敏等同明文）");
        rules.put("定期复核", "季度复审分级，业务变化可能改变敏感度");
        rules.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
    }

    public static void main(String[] args) {
        register();
        autoDetect(List.of(
                new String[]{"user.phone", "13800001234"},
                new String[]{"user.id_card", "110101199001011234"},
                new String[]{"user.nickname", "小明"},
                new String[]{"order.remark", "随便写"}));
        governance();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你业务建数据字典：15 个字段 × 分类 × 分级 × 控制措施，标注哪些是"敏感个人信息"。
 * 2. 实现敏感字段自动识别（手机号/身份证/银行卡/邮箱四类正则），对 20 个样例验证识别率。
 * 3. 思考题：为什么"可逆脱敏"的数据不能降级？从"脱敏可还原 = 明文等价"分析。
 * ============================================================
 */
