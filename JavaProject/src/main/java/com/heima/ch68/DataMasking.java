/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：68-03
 * 课名：静态脱敏与动态脱敏
 * ------------------------------------------------------------
 * 知识点：
 *   - **静态脱敏（SDM）**：**数据落地前/复制时脱敏**（生产 -> 测试环境的数据导出、数据共享给第三方）——脱敏后的数据**永久不可还原**，用于"非生产环境"与"对外共享"。
 *   - **动态脱敏（DDM）**：**查询时按访问者身份实时脱敏**（同一张表，客服看到 `138****1234`，风控看到全量）——**原数据不变，视图因人而异**，用于"生产环境内的差异化访问"。
 *   - **脱敏算法的六种**：**掩码**（保留首尾，中间 `*`）、**替换**（用假名/字典映射）、**截断**（只留前 N 位）、**泛化**（年龄 28 -> "20-30"）、**哈希**（不可逆，用于关联分析）、**加噪**（数值扰动，68-07 差分隐私的基础）——**按用途选算法**（要关联分析用哈希/假名，要展示用掩码）。
 *   - **脱敏不可逆与可关联的取舍**：**掩码/截断**不可关联（同一手机号每次掩码相同但无法 join）；**假名化**（`user_8f3a2b`）既可关联又不泄露——**跨表分析需要假名化而非掩码**。
 *   - **动态脱敏的实现位置**：**代理层**（数据库代理改写结果集）、**应用层**（DAO 拦截器 23 章插件）、**视图层**（数据库视图 + 权限）——**应用层最灵活**（能按业务上下文判断）。
 *   - 易错点：测试环境用生产数据未脱敏（最常见的泄露）；只脱敏手机号忘了地址/身份证；掩码规则可逆（保留位数太多可反推）；脱敏字段参与 join 失败（掩码后无法关联，应假名化）。
 * ============================================================
 */

package com.heima.ch68;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DataMasking {
    enum Algorithm {
        MASK, TRUNCATE, GENERALIZE, PSEUDONYMIZE, HASH
    }

    record Rule(String field, Algorithm algorithm, String note) {
    }

    static final List<Rule> rules = new ArrayList<>();

    static {
        rules.add(new Rule("phone", Algorithm.MASK, "138****1234"));
        rules.add(new Rule("idCard", Algorithm.MASK, "1101**********34"));
        rules.add(new Rule("bankCard", Algorithm.TRUNCATE, "只留后 4 位"));
        rules.add(new Rule("age", Algorithm.GENERALIZE, "28 -> 20-30"));
        rules.add(new Rule("userId", Algorithm.PSEUDONYMIZE, "user_8f3a2b（可关联）"));
        rules.add(new Rule("email", Algorithm.HASH, "sha256 前 8 位"));
    }

    /** 掩码：保留首尾 */
    static String mask(String v, int head, int tail) {
        if (v == null || v.length() <= head + tail) {
            return "***";
        }
        StringBuilder sb = new StringBuilder(v.substring(0, head));
        for (int i = head; i < v.length() - tail; i++) {
            sb.append('*');
        }
        return sb.append(v.substring(v.length() - tail)).toString();
    }

    /** 假名化：稳定映射（同一输入同一假名，可关联） */
    static String pseudonymize(String v) {
        return "user_" + Integer.toHexString(v.hashCode() & 0xFFFFFF);
    }

    /** 泛化：年龄分桶 */
    static String generalize(int age) {
        int low = age / 10 * 10;
        return low + "-" + (low + 9);
    }

    static String apply(Rule rule, String value) {
        return switch (rule.algorithm()) {
            case MASK -> mask(value, 3, 4);
            case TRUNCATE -> "****" + value.substring(Math.max(0, value.length() - 4));
            case GENERALIZE -> generalize(Integer.parseInt(value));
            case PSEUDONYMIZE -> pseudonymize(value);
            case HASH -> "h" + Integer.toHexString(value.hashCode()).substring(0, 8);
        };
    }

    /** 静态脱敏：导出前批量处理（不可还原） */
    static void staticMasking() {
        System.out.println("【静态脱敏：导出到测试环境】");
        String[][] rows = {
                {"phone", "13800001234"}, {"idCard", "110101199001011234"},
                {"bankCard", "6222021234567890"}, {"age", "28"},
                {"userId", "1001"}, {"email", "alice@example.com"}};
        for (String[] r : rows) {
            Rule rule = rules.stream().filter(x -> x.field().equals(r[0]))
                    .findFirst().orElse(null);
            if (rule == null) {
                continue;
            }
            System.out.printf("  %-10s %-24s -> %-20s（%s）%n", r[0], r[1],
                    apply(rule, r[1]), rule.note());
        }
        System.out.println();
    }

    /** 动态脱敏：同一字段按角色不同视图 */
    static void dynamicMasking() {
        System.out.println("【动态脱敏：按角色实时生效】");
        String phone = "13800001234";
        Map<String, String> viewByRole = new LinkedHashMap<>();
        viewByRole.put("客服", mask(phone, 3, 4));
        viewByRole.put("风控", phone);
        viewByRole.put("运营", mask(phone, 0, 0));
        viewByRole.forEach((role, v) -> System.out.printf("  %-4s 看到：%s%n", role, v));
        System.out.println();
    }

    static void choice() {
        System.out.println("【算法选型】");
        Map<String, String> choice = new LinkedHashMap<>();
        choice.put("展示给用户", "掩码（保留首尾，可识别）");
        choice.put("跨表分析", "假名化（稳定映射，可 join）");
        choice.put("统计报表", "泛化（分桶）");
        choice.put("去重比对", "哈希（不可逆）");
        choice.put("数值统计", "加噪（68-07）");
        choice.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }

    public static void main(String[] args) {
        staticMasking();
        dynamicMasking();
        choice();
        System.out.println("  铁律：测试环境永远不用未脱敏的生产数据");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现六种脱敏算法，对同一批数据分别应用，对比"可读性/可关联性/不可逆性"三个维度。
 * 2. 实现动态脱敏：同一查询按角色（客服/风控/运营）返回不同视图，验证原数据未被修改。
 * 3. 思考题：为什么"测试环境用生产数据"是最常见的隐私事故？从"开发/测试人员权限最宽"与"数据一旦落地难以回收"分析。
 * ============================================================
 */
