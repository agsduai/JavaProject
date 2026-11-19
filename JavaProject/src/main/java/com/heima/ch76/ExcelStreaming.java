/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-04
 * 课名：Excel 写入原理（SXSSF 思想）
 * ------------------------------------------------------------
 * 知识点：
 *   - **Excel 的两种写入模式**：**DOM 模式**（POI 的 `XSSF`，全建对象再写，10 万行即 OOM）与**流式模式**（`SXSSF`，**滑动窗口 + 临时文件**，只在内存保留 N 行）——**导出的唯一正确姿势是流式**。
 *   - **xlsx 本质是 ZIP + XML**：`[Content_Types].xml` + `xl/workbook.xml` + `xl/worksheets/sheet1.xml`（行/单元格）+ `xl/sharedStrings.xml`（共享字符串）——**"写 Excel"就是"按格式拼 XML 再压 ZIP"**。
 *   - **SXSSF 的滑动窗口**：`new SXSSFWorkbook(100)` 表示内存只保留 100 行，**超出的刷到磁盘临时文件**——**内存恒定，代价是临时磁盘 IO**（73-10 的存储治理也要管临时文件）。
 *   - **单元格类型**：**字符串用共享字符串表**（同值只存一份，省空间）、**数字直接写**、**日期用序列号 + 格式**——**类型错了 Excel 会显示成文本**（`"00123"` 变文本、`1.0` 变日期）。
 *   - 与 73-09 的关系：73-09 已手写过"最小 xlsx"（ZIP + XML），**本节把它用在"流式写入"上**——同一套原理，规模不同。
 *   - 易错点：用 XSSF 写大文件（OOM）；全部用字符串类型（Excel 无法求和）；不设列宽/格式（打开是一堆 `####`）；临时文件不清理（磁盘打满）。
 * ============================================================
 */

package com.heima.ch76;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// ==================== 示例代码 ====================

public class ExcelStreaming {
    /** 简化 xlsx 写入器：模拟 SXSSF 的滑动窗口（这里窗口只体现为"逐行写 XML"） */
    static class SheetWriter {
        final StringBuilder sheet = new StringBuilder();
        final Map<String, Integer> sharedStrings = new LinkedHashMap<>();
        int rows;

        SheetWriter() {
            sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            sheet.append("<sheetData>\n");
        }

        /** 写一行：字符串进共享表，数字直接写 */
        void writeRow(int rowNum, Object... cells) {
            sheet.append("<row r=\"").append(rowNum).append("\">");
            for (int i = 0; i < cells.length; i++) {
                String ref = colName(i) + rowNum;
                Object v = cells[i];
                if (v instanceof Number n) {
                    sheet.append("<c r=\"").append(ref).append("\"><v>")
                            .append(n).append("</v></c>");
                } else {
                    int idx = sharedStrings.computeIfAbsent(String.valueOf(v),
                            k -> sharedStrings.size());
                    sheet.append("<c r=\"").append(ref).append("\" t=\"s\"><v>")
                            .append(idx).append("</v></c>");
                }
            }
            sheet.append("</row>\n");
            rows++;
        }

        static String colName(int i) {
            return String.valueOf((char) ('A' + i));
        }

        String sheetXml() {
            return sheet + "</sheetData></worksheet>";
        }

        String sharedXml() {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"")
                    .append(sharedStrings.size()).append("\">");
            for (String s : sharedStrings.keySet()) {
                sb.append("<si><t>").append(escape(s)).append("</t></si>");
            }
            return sb.append("</sst>").toString();
        }

        static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    static byte[] buildXlsx(SheetWriter w) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                            + "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>"
                            + "</Types>");
            put(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<sheets><sheet name=\"订单\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/worksheets/sheet1.xml", w.sheetXml());
            put(zip, "xl/sharedStrings.xml", w.sharedXml());
        }
        return bos.toByteArray();
    }

    static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    static void windowExplain() {
        System.out.println("【SXSSF 滑动窗口】");
        System.out.println("  new SXSSFWorkbook(100)  -> 内存保留 100 行");
        System.out.println("  第 101 行写入 -> 最早的行刷到临时文件（/tmp/poi-sxssf-xxx）");
        System.out.println("  内存占用与总行数无关（约 100 行 * 行大小）");
        System.out.println("  结束：workbook.dispose() 删除临时文件（否则磁盘打满）");
        System.out.println();
        System.out.println("【XSSF vs SXSSF】");
        String[][] rows = {
                {"内存", "全部行在堆", "固定窗口（100~1000 行）"},
                {"10 万行", "OOM 风险", "正常"},
                {"读", "支持读", "只写"},
                {"代价", "-", "临时磁盘 IO"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-6s XSSF=%-16s SXSSF=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) throws IOException {
        windowExplain();

        SheetWriter w = new SheetWriter();
        w.writeRow(1, "订单号", "客户", "金额");
        for (int i = 1; i <= 5; i++) {
            w.writeRow(i + 1, 1000 + i, "客户" + i, 69.9 * i);
        }
        System.out.println("【生成 xlsx】");
        byte[] bytes = buildXlsx(w);
        System.out.printf("  行数 %d，共享字符串 %d 个，xlsx 大小 %d 字节%n",
                w.rows, w.sharedStrings.size(), bytes.length);
        System.out.println("  结构：ZIP 内 [Content_Types].xml / xl/workbook.xml / xl/worksheets/sheet1.xml / xl/sharedStrings.xml");
        System.out.println();
        System.out.println("【共享字符串收益】");
        System.out.println("  5 行「客户1..客户5」各存一次；若 5 万行只有 1000 个不同客户，字符串表只存 1000 份");
        System.out.println("  这是 xlsx 比 CSV 小的原因之一（但压缩后差距缩小）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用本章的最小写入器导出 1 万行，统计 xlsx 大小，验证"共享字符串去重"的效果。
 * 2. 增加列宽与数字格式（金额两位小数），说明"类型错误导致 Excel 显示异常"的场景。
 * 3. 思考题：为什么 SXSSF 能"内存恒定"？从"滑动窗口 + 临时文件"分析，并指出它的代价。
 * ============================================================
 */
