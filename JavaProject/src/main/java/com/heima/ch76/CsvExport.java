/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-05
 * 课名：CSV 与编码
 * ------------------------------------------------------------
 * 知识点：
 *   - **CSV 是"最朴素也最容易出错"的格式**：**没有标准**（RFC 4180 只是参考），**转义规则**（字段含逗号/引号/换行要加双引号，内部双引号翻倍）、**编码**（UTF-8 vs GBK）、**BOM**（Excel 打开 UTF-8 CSV 乱码的根源）——**导出 CSV 的坑比 Excel 多**。
 *   - **转义三规则**：**含 `,` `"` `\n` 的字段用双引号包裹**；**字段内的 `"` 写成 `""`**；**行尾用 `\r\n`**（Excel 兼容）——**少一条就会错列**。
 *   - **编码选择**：**内部/接口用 UTF-8**；**给 Excel 用户的 CSV 加 UTF-8 BOM**（`\uFEFF`）——**没有 BOM 时 Excel 按系统编码（中文 Windows 是 GBK）解析，必乱码**。
 *   - **CSV 注入（公式注入）**：字段以 `=` `+` `-` `@` 开头时，Excel 会当公式执行（`=cmd|...`）——**导出前转义/加单引号前缀**，这是**真实的安全漏洞**（36 章输入校验的变体）。
 *   - 与 73-09 的关系：73-09 的"导出压缩"里有 CSV 生成，**本节把转义/编码/注入讲透**。
 *   - 易错点：不转义逗号（错列）；不用 BOM（乱码）；`\n` 换行（Windows 打开错行）；不防公式注入（安全漏洞）。
 * ============================================================
 */

package com.heima.ch76;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class CsvExport {
    /** 规则一：含特殊字符加引号；规则二：内部引号翻倍；规则三：防公式注入 */
    static String escape(String field) {
        if (field == null) {
            return "";
        }
        String v = field;
        // 公式注入防护：以 = + - @ 开头时加单引号前缀
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        boolean needQuote = v.contains(",") || v.contains("\"")
                || v.contains("\n") || v.contains("\r");
        if (!needQuote) {
            return v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    static String toCsv(List<String[]> rows, boolean withBom) {
        StringBuilder sb = new StringBuilder();
        if (withBom) {
            sb.append('\uFEFF');
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escape(row[i]));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    static void escaping() {
        System.out.println("【转义演示】");
        String[] inputs = {
                "普通文本",
                "含,逗号",
                "含\"引号",
                "含\n换行",
                "=SUM(A1:A9)",
                "-1+1",
        };
        for (String in : inputs) {
            System.out.printf("  %-14s -> [%s]%n",
                    in.replace("\n", "\\n"), escape(in).replace("\n", "\\n"));
        }
        System.out.println();
    }

    static void roundTrip() {
        System.out.println("【生成 CSV（带 BOM）】");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"订单号", "客户", "备注", "金额"});
        rows.add(new String[]{"1001", "张三", "含,逗号的备注", "69.00"});
        rows.add(new String[]{"1002", "李四", "说\"你好\"", "129.00"});
        rows.add(new String[]{"1003", "王五", "两行\n备注", "39.00"});
        rows.add(new String[]{"1004", "=cmd|calc", "正常", "0.00"});

        String csv = toCsv(rows, true);
        csv.lines().forEach(l -> System.out.println("  " + l.replace("\uFEFF", "<BOM>")));

        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        System.out.printf("  字节数 %d（BOM 占 3 字节），首字节 %02X %02X %02X%n",
                bytes.length, bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF);
        System.out.println();
        System.out.println("  若去掉 BOM：Excel（中文 Windows）按 GBK 解码 -> 「订单号」变乱码");
        System.out.println();
    }

    static void bomTable() {
        System.out.println("【编码与 BOM 决策】");
        String[][] rows = {
                {"UTF-8 + BOM", "Excel 双击打开正常", "给业务人员的 CSV（推荐）"},
                {"UTF-8 无 BOM", "Excel 可能乱码", "程序消费 / 上传给系统"},
                {"GBK", "老系统兼容", "仅对接遗留系统"},
                {"UTF-16", "少见", "特定财务软件"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-22s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  原则：内部一律 UTF-8；面向 Excel 的下载加 BOM；转码只在边界做");
    }

    public static void main(String[] args) {
        escaping();
        roundTrip();
        bomTable();
        System.out.println("【CSV 四坑】逗号/引号/换行未转义、无 BOM 乱码、\\n 换行错位、公式注入");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 CSV 转义（逗号/引号/换行）并做"生成 -> 解析"往返验证，确保字段原样还原。
 * 2. 生成带 BOM 与不带 BOM 的两份 CSV，用 Excel/WPS 打开对比乱码现象。
 * 3. 思考题：为什么"公式注入"是安全问题而不是格式问题？从"Excel 执行公式"与"导出给外部"分析（36 章）。
 * ============================================================
 */
