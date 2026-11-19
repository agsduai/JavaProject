/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：71-07
 * 课名：版本与发布管理
 * ------------------------------------------------------------
 * 知识点：
 *   - **低代码平台的"发版"是元数据发版**：表单/实体/流程/权限/规则都是版本化对象——**每次发布生成一个不可变版本**，运行中的应用引用某个版本（与 41 章制品不可变同源）。
 *   - **元数据变更的兼容性**：**加字段**（兼容）、**改字段类型**（不兼容，需迁移）、**删字段**（危险，历史数据）——**与 41-09 的 expand-contract 完全一致**（低代码把它自动化）。
 *   - **灰度与回滚**：新版本元数据先给部分租户/用户（41-07 金丝雀）——**低代码的灰度粒度通常是"租户"或"表单"**；**回滚 = 切回旧版本元数据**（秒级，因为数据仍在）。
 *   - **在途数据的处理**（最大难点）：改表单定义后，**已提交的单据要按"提交时的定义"展示**——**表单定义必须带版本，单据记录 version**（否则历史单据字段错位）。
 *   - 与 55/61 章的关系：多租户按租户发布（55-08）、流程实例按版本隔离（61-07）——**同一套版本化思想**。
 *   - 易错点：改定义不升版本（历史单据展示错乱）；直接删字段（数据丢失）；全量发布无灰度（一个租户的配置错影响全部）；回滚只回代码不回元数据。
 * ============================================================
 */

package com.heima.ch71;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class VersionManagement {
    record FormVersion(String version, List<String> fields, String status) {
    }

    record FormRecord(String id, String formId, String version, Map<String, String> data) {
    }

    static final Map<String, List<FormVersion>> versions = new LinkedHashMap<>();
    static final List<FormRecord> records = new ArrayList<>();

    static void publish(String formId, List<String> fields, boolean gray) {
        List<FormVersion> list = versions.computeIfAbsent(formId, k -> new ArrayList<>());
        String ver = "v" + (list.size() + 1);
        String status = gray ? "GRAY" : "ACTIVE";
        if (!gray) {
            for (int i = 0; i < list.size(); i++) {
                FormVersion old = list.get(i);
                list.set(i, new FormVersion(old.version(), old.fields(), "DEPRECATED"));
            }
        }
        list.add(new FormVersion(ver, fields, status));
        System.out.printf("  发布 %s %s（%s）：字段 %s%n", formId, ver, status, fields);
    }

    static void submit(String id, String formId, Map<String, String> data) {
        List<FormVersion> list = versions.get(formId);
        FormVersion active = list.stream()
                .filter(v -> v.status().equals("ACTIVE")).findFirst().orElse(null);
        if (active == null) {
            System.out.println("  提交失败：无生效版本");
            return;
        }
        for (String f : data.keySet()) {
            if (!active.fields().contains(f)) {
                System.out.printf("  提交失败：字段 %s 不在 %s 中%n", f, active.version());
                return;
            }
        }
        records.add(new FormRecord(id, formId, active.version(), new LinkedHashMap<>(data)));
        System.out.printf("  提交 %s 使用 %s：%s%n", id, active.version(), data);
    }

    /** 展示：按记录自己的版本渲染（历史单据不受新版本影响） */
    static void render(String recordId) {
        FormRecord r = records.stream().filter(x -> x.id().equals(recordId))
                .findFirst().orElse(null);
        if (r == null) {
            System.out.println("  记录不存在：" + recordId);
            return;
        }
        FormVersion v = versions.get(r.formId()).stream()
                .filter(x -> x.version().equals(r.version())).findFirst().orElse(null);
        System.out.printf("  记录 %s（%s 版本 %s）:%n", r.id(), r.formId(), r.version());
        if (v != null) {
            v.fields().forEach(f -> System.out.printf("    %-10s = %s%n", f,
                    r.data().getOrDefault(f, "（该版本无此字段）")));
        }
        System.out.println("    （渲染字段来自记录自身的版本定义，不受新版本影响）");
    }

    public static void main(String[] args) {
        System.out.println("【发布流程】");
        publish("expense", List.of("title", "amount"), true);
        publish("expense", List.of("title", "amount"), false);

        Map<String, String> d1 = new LinkedHashMap<>();
        d1.put("title", "差旅");
        d1.put("amount", "1200");
        submit("r-1", "expense", d1);

        System.out.println();
        System.out.println("【v3 新增字段（兼容变更）】");
        publish("expense", List.of("title", "amount", "cost_center"), false);
        Map<String, String> d2 = new LinkedHashMap<>();
        d2.put("title", "餐补");
        d2.put("amount", "300");
        d2.put("cost_center", "CC-01");
        submit("r-2", "expense", d2);

        System.out.println();
        System.out.println("【历史记录按各自版本渲染】");
        render("r-1");
        render("r-2");

        System.out.println();
        System.out.println("【不兼容变更：v4 删除 amount 字段】");
        publish("expense", List.of("title", "cost_center"), false);
        Map<String, String> d3 = new LinkedHashMap<>();
        d3.put("title", "打车");
        d3.put("amount", "80");
        submit("r-3", "expense", d3);
        System.out.println("  结论：删字段是破坏性变更，必须 expand-contract（41-09）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现表单版本管理：发布/灰度/回滚，用"v1 提交 -> v2 加字段 -> 历史单据渲染"验证版本隔离。
 * 2. 实现"破坏性变更检测"：改类型/删字段时拒绝发布并提示迁移方案（expand-contract）。
 * 3. 思考题：为什么"历史单据必须按提交时版本渲染"？从"字段错位/数据丢失"与"审计要求"分析。
 * ============================================================
 */
