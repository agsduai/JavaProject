/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：71-06
 * 课名：代码生成 vs 解释执行
 * ------------------------------------------------------------
 * 知识点：
 *   - **低代码的执行有两条路线**：**解释执行**（运行时解析元数据 -> 动态执行，改配置即时生效）与**代码生成**（把元数据生成 Java/前端代码 -> 编译部署，运行快但改配置要重新生成）。
 *   - **解释执行的代价**：每次请求都要解析元数据（可缓存）、动态分支难以优化、调试链路长——**适合配置频繁变化的场景**（后台管理）。
 *   - **代码生成的代价**：构建链路长（生成 -> 编译 -> 发布 41 章）、配置与代码不同步（改了元数据忘重新生成）——**适合配置稳定、性能敏感的场景**。
 *   - **混合方案（主流）**：**元数据解释 + 生成"类型安全的访问层"**——UI 与校验走解释（灵活），数据访问走生成的 DAO（性能与类型安全）——**各取所长**。
 *   - **与 24/60 章的关系**：代码生成就是"自己写一个注解处理器/模板引擎"（24-01 自动配置、60-02 IOC 都是"生成/装配"思想）——**低代码平台是这类思想的工程化**。
 *   - 易错点：全量解释导致性能差（每个字段都反射）；代码生成后元数据变更不同步（线上数据错乱）；生成物被手工修改（下次生成被覆盖）；无生成版本记录（回滚困难）。
 * ============================================================
 */

package com.heima.ch71;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class CodegenVsInterpretation {
    record Field(String name, String type) {
    }

    record Entity(String name, List<Field> fields) {
    }

    /** 解释执行：运行时按元数据取值/赋值（反射式，简化用 Map） */
    static Object getInterpreted(Map<String, Object> row, String field) {
        return row.get(field);
    }

    static void setInterpreted(Map<String, Object> row, String field, Object value) {
        row.put(field, value);
    }

    /** 代码生成：产出 Java 源码（DAO + DTO），编译后类型安全 */
    static String generateDao(Entity e) {
        StringBuilder sb = new StringBuilder();
        String cls = toPascal(e.name());
        sb.append("// 由元数据生成，请勿手工修改\n");
        sb.append("public class ").append(cls).append("Dao {\n");
        for (Field f : e.fields()) {
            sb.append("    public ").append(javaType(f.type())).append(" get")
                    .append(toPascal(f.name())).append("() { ... }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    static String javaType(String type) {
        return switch (type) {
            case "string", "text" -> "String";
            case "int" -> "Long";
            case "decimal" -> "BigDecimal";
            case "date" -> "LocalDate";
            case "bool" -> "Boolean";
            default -> "Object";
        };
    }

    static String toPascal(String s) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : s.toCharArray()) {
            if (c == '_') {
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    static void compare() {
        System.out.println("【两条路线对比】");
        String[][] rows = {
                {"解释执行", "运行时解析元数据", "改配置即时生效", "每次请求有解析开销"},
                {"代码生成", "元数据 -> 源码 -> 编译", "运行快、类型安全", "改配置要重新生成+发布"},
                {"混合（推荐）", "UI/校验解释 + DAO 生成", "兼顾灵活与性能", "需治理生成物与元数据同步"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-26s %-18s %s%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        Entity expense = new Entity("expense", List.of(
                new Field("title", "string"),
                new Field("amount", "decimal"),
                new Field("apply_date", "date"),
                new Field("approved", "bool")));

        System.out.println("【解释执行：动态取值】");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", "差旅报销");
        row.put("amount", "1200.50");
        row.put("apply_date", "2026-11-18");
        expense.fields().forEach(f -> System.out.printf("  %-12s -> %s%n",
                f.name(), getInterpreted(row, f.name())));

        System.out.println();
        System.out.println("【代码生成：产出源码】");
        System.out.println(generateDao(expense).replace("\n", "\n  "));

        System.out.println();
        compare();
        System.out.println("  生成物治理：标记「请勿手工修改」 + 生成版本号 + 与元数据一致性校验");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现元数据到 Java DAO 的代码生成器（含类型映射），生成一个实体的完整源码。
 * 2. 对比解释与生成两种方式在"10 万次字段读取"上的耗时差异（用 Map 与生成代码各模拟），给出选用建议。
 * 3. 思考题：为什么"生成物被手工修改"是低代码平台的常见事故？从"下次生成覆盖"与"漂移难排查"分析。
 * ============================================================
 */
