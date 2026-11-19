/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：71-02
 * 课名：元数据建模：实体、字段、关系
 * ------------------------------------------------------------
 * 知识点：
 *   - **元数据是低代码的"数据库 schema 的 schema"（元-元模型）**：`EntityMeta`（实体名/表名/字段列表）、`FieldMeta`（字段名/类型/必填/默认值/校验规则）、`RelationMeta`（1:1、1:N、N:M）——**用数据描述数据结构**。
 *   - **字段类型的封闭枚举**：`string/int/decimal/date/datetime/bool/enum/text/ref`——**类型封闭是可控性的前提**（开放类型 = 无法生成 UI/无法校验/无法索引）。
 *   - **元数据的版本与演进**（71-07 的前置）：加字段（兼容）、改类型（不兼容，需迁移）、删字段（危险）——**与 41-09 expand-contract 同款纪律**。
 *   - **物理存储的两种策略**：**宽表**（每个实体一张物理表，字段一一对应——查询快、改结构要 DDL）与**EAV/JSON**（一行一属性或整条 JSON——灵活、查询慢）——**主流是"宽表 + JSON 扩展列"的混合**（46-05 的半结构化思想）。
 *   - **与 63 章维度建模的关系**：低代码的实体建模是"业务对象的建模"，数仓维度建模是"分析视角的建模"——**同为建模，目标不同**（前者支撑 CRUD，后者支撑分析）。
 *   - 易错点：字段类型开放（允许任意类型 -> 引擎失控）；元数据不带版本（改字段破坏历史数据）；关系只存 ID 不做级联策略（删除留孤儿）；物理表与元数据不一致（漂移）。
 * ============================================================
 */

package com.heima.ch71;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MetadataModeling {
    enum FieldType {
        STRING, INT, DECIMAL, DATE, DATETIME, BOOL, ENUM, TEXT, REF
    }

    record FieldMeta(String name, String label, FieldType type, boolean required,
                     String defaultValue, List<String> enumValues, String refEntity) {
        String toDdl() {
            String sqlType = switch (type) {
                case STRING -> "VARCHAR(255)";
                case INT -> "BIGINT";
                case DECIMAL -> "DECIMAL(18,2)";
                case DATE -> "DATE";
                case DATETIME -> "DATETIME";
                case BOOL -> "TINYINT(1)";
                case ENUM -> "VARCHAR(64)";
                case TEXT -> "TEXT";
                case REF -> "BIGINT";
            };
            return name + " " + sqlType + (required ? " NOT NULL" : " NULL");
        }
    }

    record EntityMeta(String name, String table, List<FieldMeta> fields,
                      Map<String, String> relations) {
        String toDdl() {
            StringBuilder sb = new StringBuilder("CREATE TABLE " + table + " (\n  id BIGINT PRIMARY KEY,\n");
            for (FieldMeta f : fields) {
                sb.append("  ").append(f.toDdl()).append(",\n");
            }
            relations.forEach((field, target) ->
                    sb.append("  INDEX idx_").append(field).append(" (").append(field).append("),\n"));
            return sb.append("  created_at DATETIME NOT NULL\n)").toString();
        }
    }

    static void buildDemo() {
        List<FieldMeta> fields = new ArrayList<>(List.of(
                new FieldMeta("title", "标题", FieldType.STRING, true, null, List.of(), null),
                new FieldMeta("amount", "金额", FieldType.DECIMAL, true, "0.00", List.of(), null),
                new FieldMeta("category", "类别", FieldType.ENUM, true, "TRAVEL",
                        List.of("TRAVEL", "MEAL", "OFFICE"), null),
                new FieldMeta("apply_date", "申请日期", FieldType.DATE, true, null, List.of(), null),
                new FieldMeta("applicant_id", "申请人", FieldType.REF, true, null, List.of(), "user"),
                new FieldMeta("remark", "备注", FieldType.TEXT, false, null, List.of(), null)));
        Map<String, String> relations = new LinkedHashMap<>();
        relations.put("applicant_id", "user");
        EntityMeta expense = new EntityMeta("报销单", "lc_expense", fields, relations);

        System.out.println("【实体元数据】" + expense.name() + " -> 表 " + expense.table());
        expense.fields().forEach(f -> System.out.printf(
                "  %-14s %-8s 类型=%-9s 必填=%-5s 默认=%s%n",
                f.name(), f.label(), f.type(), f.required(),
                f.defaultValue() == null ? "-" : f.defaultValue()));
        System.out.println("  关系：" + expense.relations());
        System.out.println();
        System.out.println("【由元数据生成 DDL】");
        System.out.println(expense.toDdl());
        System.out.println();
    }

    static void storageStrategy() {
        System.out.println("【物理存储两种策略】");
        String[][] rows = {
                {"宽表", "每实体一张表，字段一一对应", "查询快、可索引；改结构要 DDL"},
                {"JSON 列", "主键 + 整条 JSON", "灵活；查询/索引受限"},
                {"混合（推荐）", "常用字段宽表 + 扩展字段 JSON", "兼顾性能与灵活（46-05 半结构化）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  演进纪律：加字段兼容；改类型走 expand-contract（41-09）");
    }

    public static void main(String[] args) {
        buildDemo();
        storageStrategy();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 设计一个"请假单"实体的元数据（≥8 个字段，含枚举与引用），从元数据生成 DDL。
 * 2. 对比宽表与 JSON 列两种存储：设计"按金额范围查询"与"新增自定义字段"两个场景，说明各自代价。
 * 3. 思考题：为什么字段类型必须"封闭枚举"？从"UI 渲染、校验、索引、导出"四个下游能力分析。
 * ============================================================
 */
