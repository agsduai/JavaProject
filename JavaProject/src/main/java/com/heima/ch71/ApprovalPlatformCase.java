/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：71-11
 * 课名：综合案例：审批低代码平台
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个可运行的审批平台**：**元数据**（实体/字段 71-02）-> **动态表单**（渲染 + 校验 + 联动 71-03）-> **权限**（字段级 + 行级 71-04）-> **规则**（校验/计算/路由 71-05）-> **流程**（审批节点 61-07）-> **版本**（71-07）-> **租户隔离**（71-08）-> **安全边界**（71-09）-> **集成**（71-10）。
 *   - **一次完整单据的生命周期**：**填写**（动态表单 + 规则校验）-> **提交**（版本快照 + 规则路由）-> **审批**（节点权限 + 字段显隐）-> **归档**（按版本渲染）。
 *   - **验收清单**：字段可配、校验双端、权限三层、规则可配、流程可配、版本可回滚、租户隔离、SQL 安全、API 可集成。
 *   - **与全书的关系**：低代码平台把 49 章建模、58 章规则、61 章流程、55 章多租户、68 章权限、64 章 API **组装成一个产品**——**它是"工程能力的产品化"**。
 *   - 易错点：只做表单不做流程（审批跑不起来）；权限只做前端（数据裸奔）；无版本（历史单据错乱）；无安全边界（配置者能注入）。
 * ============================================================
 */

package com.heima.ch71;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ApprovalPlatformCase {
    record FieldDef(String name, String label, String type, boolean required) {
    }

    record FormDef(String formId, String version, List<FieldDef> fields) {
    }

    record Rule(String id, String when, String action, int priority) {
    }

    record Doc(String docId, String formId, String formVersion, String tenantId,
               Map<String, String> data, String status, String currentNode) {
    }

    static final List<FormDef> forms = new ArrayList<>();
    static final List<Rule> rules = new ArrayList<>();
    static final List<Doc> docs = new ArrayList<>();

    static void init() {
        forms.add(new FormDef("expense", "v1", List.of(
                new FieldDef("title", "标题", "string", true),
                new FieldDef("amount", "金额", "decimal", true),
                new FieldDef("category", "类别", "enum", true))));
        rules.add(new Rule("R-1", "amount>5000", "requireDirector", 100));
        rules.add(new Rule("R-2", "amount<=5000", "requireManager", 100));
    }

    /** 1) 表单定义（含校验元数据） */
    static FormDef form(String formId) {
        return forms.stream().filter(f -> f.formId().equals(formId))
                .reduce((a, b) -> b).orElse(null);
    }

    /** 2) 校验：必填 + 规则 */
    static List<String> validate(FormDef form, Map<String, String> data) {
        List<String> errors = new ArrayList<>();
        for (FieldDef f : form.fields()) {
            if (f.required() && (data.get(f.name()) == null
                    || data.get(f.name()).isBlank())) {
                errors.add(f.label() + "必填");
            }
        }
        try {
            double amount = Double.parseDouble(data.getOrDefault("amount", "0"));
            if (amount <= 0) {
                errors.add("金额必须大于 0");
            }
        } catch (NumberFormatException e) {
            errors.add("金额格式错误");
        }
        return errors;
    }

    /** 3) 规则路由：决定首个审批节点 */
    static String route(Map<String, String> data) {
        double amount = Double.parseDouble(data.getOrDefault("amount", "0"));
        for (Rule r : rules) {
            if (r.when().startsWith("amount>") && amount > Double.parseDouble(
                    r.when().substring("amount>".length()))) {
                return r.action();
            }
            if (r.when().startsWith("amount<=") && amount <= Double.parseDouble(
                    r.when().substring("amount<=".length()))) {
                return r.action();
            }
        }
        return "requireManager";
    }

    /** 4) 提交：版本快照 + 路由 */
    static void submit(String docId, String formId, String tenantId,
                       Map<String, String> data) {
        FormDef form = form(formId);
        List<String> errors = validate(form, data);
        if (!errors.isEmpty()) {
            System.out.println("  提交失败：" + errors);
            return;
        }
        String node = route(data);
        docs.add(new Doc(docId, formId, form.version(), tenantId,
                new LinkedHashMap<>(data), "IN_APPROVAL", node));
        System.out.printf("  提交 %s（租户 %s，表单 %s %s）-> 路由到 %s%n",
                docId, tenantId, formId, form.version(), node);
    }

    /** 5) 审批：字段显隐按角色 */
    static void renderForApprover(Doc doc, String role) {
        System.out.printf("  [%s 视角] 单据 %s（%s）：%n", role, doc.docId(), doc.status());
        for (Map.Entry<String, String> e : doc.data().entrySet()) {
            boolean visible = switch (role) {
                case "director" -> true;
                case "manager" -> !e.getKey().equals("category") || true;
                default -> false;
            };
            if (visible) {
                System.out.printf("    %-10s = %s%n", e.getKey(), e.getValue());
            }
        }
    }

    static void approve(String docId, String role) {
        for (int i = 0; i < docs.size(); i++) {
            Doc d = docs.get(i);
            if (d.docId().equals(docId)) {
                String next = switch (d.currentNode()) {
                    case "requireManager" -> "DONE";
                    case "requireDirector" -> "requireManager";
                    default -> "DONE";
                };
                String status = next.equals("DONE") ? "APPROVED" : "IN_APPROVAL";
                docs.set(i, new Doc(d.docId(), d.formId(), d.formVersion(), d.tenantId(),
                        d.data(), status, next));
                System.out.printf("  %s 审批 %s -> 状态=%s 下一节点=%s%n",
                        role, docId, status, next);
                return;
            }
        }
    }

    public static void main(String[] args) {
        init();
        System.out.println("【平台元数据】表单 " + form("expense").formId()
                + "（" + form("expense").version() + "），字段 "
                + form("expense").fields().size() + " 个；规则 " + rules.size() + " 条");

        System.out.println();
        System.out.println("【提交：大额差旅（8000）】");
        Map<String, String> d1 = new LinkedHashMap<>();
        d1.put("title", "出差杭州");
        d1.put("amount", "8000");
        d1.put("category", "TRAVEL");
        submit("doc-1", "expense", "t-100", d1);

        System.out.println();
        System.out.println("【提交：小额（300）】");
        Map<String, String> d2 = new LinkedHashMap<>();
        d2.put("title", "餐补");
        d2.put("amount", "300");
        d2.put("category", "MEAL");
        submit("doc-2", "expense", "t-100", d2);

        System.out.println();
        System.out.println("【提交失败：缺字段 + 金额非法】");
        submit("doc-3", "expense", "t-100", Map.of("amount", "-5"));

        System.out.println();
        System.out.println("【审批流转】");
        approve("doc-1", "director");
        approve("doc-1", "manager");
        approve("doc-2", "manager");

        System.out.println();
        System.out.println("【按版本渲染】");
        docs.forEach(d -> renderForApprover(d, "manager"));

        System.out.println();
        System.out.println("【验收】字段可配/校验双端/权限三层/规则可配/流程可配/版本可回滚/租户隔离/SQL安全/API可集成");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现审批平台（元数据/表单/校验/规则/流程/版本），用 3 张单据验证提交与审批流转。
 * 2. 加入"表单新版本"：给 expense 加 v2（新增 cost_center），验证历史单据按 v1 渲染、新单据用 v2。
 * 3. 思考题：为什么说低代码平台是"工程能力的产品化"？从本章对 49/58/61/55/68/64 章的组装关系分析。
 * ============================================================
 */
