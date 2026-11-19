/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-07
 * 课名：模板与动态报表
 * ------------------------------------------------------------
 * 知识点：
 *   - **模板化是报表工程化的关键**：**版式与数据分离**——模板定义"哪里放什么"（占位符 + 循环区 + 表格区），数据只负责填充——**改版式不改代码，改数据不动模板**。
 *   - **三类占位符**：**变量**（`${title}`）、**循环**（`{{#items}}...{{/items}}` 或表格行重复）、**条件**（`{{#if vip}}...{{/if}}`）——**与 37-04 的模板引擎同源**（Mustache/Thymeleaf 的最小模型）。
 *   - **模板的两种落点**：**文档模板**（Word/Excel 模板 + 占位符替换，适合"格式复杂、变动少"的合同/发票）与**代码模板**（HTML/字符串拼接，适合"结构简单、变化多"的报表）——**按"改版频率"选**。
 *   - **动态列是报表的难点**：列由数据决定（如"每月一列"）——**模板要支持"列集合"**，表头和数据行都按列集合渲染——**硬编码列名是动态报表的最大反模式**。
 *   - 易错点：模板里写业务逻辑（不可维护）；占位符不转义（HTML 注入 36-02）；动态列硬编码；模板与数据版本不匹配（字段改名导致空白）。
 * ============================================================
 */

package com.heima.ch76;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

public class ReportTemplate {
    /** 极简模板引擎：${var} 变量、{{#list}}...{{/list}} 循环、{{#if flag}}...{{/if}} 条件 */
    static class Template {
        final String src;

        Template(String src) {
            this.src = src;
        }

        String render(Map<String, Object> data) {
            return renderSection(src, data);
        }

        @SuppressWarnings("unchecked")
        String renderSection(String section, Map<String, Object> data) {
            // 1) 循环
            Pattern listPat = Pattern.compile("\\{\\{#([a-zA-Z_]+)}}(.*?)\\{\\{/\\1}}",
                    Pattern.DOTALL);
            Matcher lm = listPat.matcher(section);
            StringBuilder out = new StringBuilder();
            int last = 0;
            while (lm.find()) {
                out.append(section, last, lm.start());
                String key = lm.group(1);
                String body = lm.group(2);
                Object val = data.get(key);
                if (val instanceof List<?> list) {
                    for (Object item : list) {
                        Map<String, Object> scope = new LinkedHashMap<>(data);
                        if (item instanceof Map<?, ?> m) {
                            for (Map.Entry<?, ?> e : m.entrySet()) {
                                scope.put(String.valueOf(e.getKey()), e.getValue());
                            }
                        }
                        out.append(renderSection(body, scope));
                    }
                } else if (Boolean.TRUE.equals(val)) {
                    out.append(renderSection(body, data));       // if 语义
                }
                last = lm.end();
            }
            out.append(section.substring(last));

            // 2) 变量
            String result = out.toString();
            Matcher vm = Pattern.compile("\\$\\{([a-zA-Z_]+)}").matcher(result);
            StringBuilder sb = new StringBuilder();
            while (vm.find()) {
                Object v = data.get(vm.group(1));
                vm.appendReplacement(sb, Matcher.quoteReplacement(
                        v == null ? "" : escapeHtml(String.valueOf(v))));
            }
            vm.appendTail(sb);
            return sb.toString();
        }

        static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /** 动态列：列集合来自数据，表头与行按列渲染 */
    record DynamicTable(List<String> columns, List<Map<String, Object>> rows) {
        String toHtml(String title) {
            StringBuilder sb = new StringBuilder();
            sb.append("<h3>").append(title).append("</h3><table border=1><tr>");
            for (String c : columns) {
                sb.append("<th>").append(c).append("</th>");
            }
            sb.append("</tr>");
            for (Map<String, Object> row : rows) {
                sb.append("<tr>");
                for (String c : columns) {
                    sb.append("<td>").append(row.getOrDefault(c, "")).append("</td>");
                }
                sb.append("</tr>");
            }
            return sb.append("</table>").toString();
        }
    }

    public static void main(String[] args) {
        String tpl = """
                <h1>${title}</h1>
                <p>客户：${customer}</p>
                <table border=1>
                <tr><th>商品</th><th>数量</th><th>金额</th></tr>
                {{#items}}<tr><td>${name}</td><td>${qty}</td><td>${amount}</td></tr>
                {{/items}}</table>
                {{#vip}}<p>VIP 客户：享 9 折</p>{{/vip}}
                <p>备注：${note}</p>
                """;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "订单对账单");
        data.put("customer", "张三");
        data.put("vip", true);
        data.put("note", "<script>alert(1)</script>");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("name", "手机壳", "qty", 2, "amount", "138.00"));
        items.add(Map.of("name", "耳机", "qty", 1, "amount", "129.00"));
        data.put("items", items);

        System.out.println("【模板渲染】");
        String html = new Template(tpl).render(data);
        html.lines().filter(l -> !l.isBlank()).forEach(l -> System.out.println("  " + l));

        System.out.println();
        System.out.println("【动态列报表】");
        DynamicTable table = new DynamicTable(
                List.of("门店", "1月", "2月", "3月"),
                List.of(
                        Map.of("门店", "北京", "1月", 120, "2月", 135, "3月", 150),
                        Map.of("门店", "上海", "1月", 100, "2月", 98, "3月", 130)));
        System.out.println("  " + table.toHtml("季度销售").replace("<tr>", "\n  <tr>"));

        System.out.println();
        System.out.println("【要点】模板与数据分离 / 占位符转义（note 里的 script 已转义）/ 动态列不硬编码");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展模板引擎：支持嵌套循环（订单 -> 明细），并渲染一份"多订单"报表。
 * 2. 实现动态列报表：列由查询结果决定（如"每月一列"），表头与数据行都按列集合渲染。
 * 3. 思考题：为什么"模板里写业务逻辑"会不可维护？从"改版式 vs 改规则"的职责分离分析。
 * ============================================================
 */
