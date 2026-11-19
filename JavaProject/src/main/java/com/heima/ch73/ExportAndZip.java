/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-09
 * 课名：大文件导出与压缩
 * ------------------------------------------------------------
 * 知识点：
 *   - **导出与下载的区别**：下载是"取已存在的文件"，导出是"**现场生成文件**"——**导出的痛点是内存**（百万行数据一次读进内存必 OOM），解法是**流式生成 + 分批查询**（76 章细讲）。
 *   - **压缩的两个时机**：**生成时压缩**（导出直接写成 zip，边写边压缩，内存友好）与 **传输时压缩**（HTTP `Content-Encoding: gzip`，适合文本）——**大文件优先"生成时压缩"**。
 *   - **office 格式的本质**：**docx/xlsx/pptx 都是 zip 包**（内含 XML）——**可以用 `ZipOutputStream` 手工生成最小的 xlsx**（无需 POI），这是理解"导出库在做什么"的最佳路径（76-04）。
 *   - **导出的异步化**：超过 N 行的导出**不阻塞请求**——提交任务 -> 后台生成 -> 通知下载（76-09 的下载中心），**避免 HTTP 超时**。
 *   - **与 76 章的关系**：本章讲"文件工程"的通用面，76 章讲"报表内容"的生成细节——**两者互补**。
 *   - 易错点：`ByteArrayOutputStream` 攒整个文件（OOM）；同步导出百万行（请求超时）；不设导出上限（一个请求跑了 10 分钟）；压缩率没预期的格式（图片再压缩几乎无效）。
 * ============================================================
 */

package com.heima.ch73;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// ==================== 示例代码 ====================

public class ExportAndZip {
    /** 流式写 zip：逐条写，内存只保留当前条目 */
    static byte[] buildZip(List<String[]> rows) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("data.csv"));
            zos.write("id,name,amount\n".getBytes(StandardCharsets.UTF_8));
            for (String[] r : rows) {
                zos.write((r[0] + "," + r[1] + "," + r[2] + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("README.txt"));
            zos.write("导出于 2026-11-18\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    /** 最小 xlsx：zip 里放 3 个 XML（[Content_Types].xml / _rels / sheet1.xml） */
    static byte[] minimalXlsx(String sheetName, List<String[]> rows) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "</Types>";
            put(zos, "[Content_Types].xml", contentTypes);

            String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>";
            put(zos, "_rels/.rels", rels);

            String workbook = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<sheets><sheet name=\"" + sheetName + "\" sheetId=\"1\" r:id=\"rId1\""
                    + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>"
                    + "</sheets></workbook>";
            put(zos, "xl/workbook.xml", workbook);

            StringBuilder sheet = new StringBuilder(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                            + "<sheetData>");
            for (int i = 0; i < rows.size(); i++) {
                sheet.append("<row r=\"").append(i + 1).append("\">");
                String[] row = rows.get(i);
                for (int j = 0; j < row.length; j++) {
                    sheet.append("<c r=\"").append((char) ('A' + j)).append(i + 1)
                            .append("\" t=\"inlineStr\"><is><t>").append(row[j])
                            .append("</t></is></c>");
                }
                sheet.append("</row>");
            }
            sheet.append("</sheetData></worksheet>");
            put(zos, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return bos.toByteArray();
    }

    private static void put(ZipOutputStream zos, String name, String content)
            throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    public static void main(String[] args) throws Exception {
        List<String[]> rows = List.of(
                new String[]{"1", "手机壳", "6900"},
                new String[]{"2", "蓝牙耳机", "12900"},
                new String[]{"3", "充电器", "3900"});

        byte[] zip = buildZip(rows);
        System.out.println("【zip 导出】大小=" + zip.length + " 字节（含 data.csv + README.txt）");

        byte[] xlsx = minimalXlsx("订单", rows);
        System.out.println("【最小 xlsx】大小=" + xlsx.length + " 字节");
        System.out.println("  内容：zip 包 + 4 个 XML 部件（xlsx 本质就是 zip）");

        System.out.println();
        System.out.println("【压缩效果对比（模拟）】");
        Map<String, String> compress = new LinkedHashMap<>();
        compress.put("文本 CSV 100MB", "gzip 后约 10~20MB（压缩率 80~90%）");
        compress.put("已压缩图片 100MB", "zip 后约 95MB（几乎无效，别白费 CPU）");
        compress.put("Excel 100MB", "zip 后约 20~30MB（XML 压缩率高）");
        compress.forEach((k, v) -> System.out.printf("  %-18s %s%n", k, v));

        System.out.println();
        System.out.println("【导出的两条硬规则】");
        System.out.println("  1) 流式写（不要 ByteArrayOutputStream 攒全量）");
        System.out.println("  2) 超阈值异步（提交任务 -> 后台生成 -> 通知下载，76-09）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现流式 zip 导出（两个条目），验证不需要把全部内容放内存。
 * 2. 生成一个最小 xlsx（4 个 XML 部件），说明 xlsx = zip + XML 的结构。
 * 3. 思考题：为什么"对已压缩图片再 zip"是浪费？从"熵已接近上限"与"CPU 成本"分析。
 * ============================================================
 */
