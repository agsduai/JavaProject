/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-06
 * 课名：PDF 排版基础
 * ------------------------------------------------------------
 * 知识点：
 *   - **PDF 的本质**：**页面 + 内容流**（`BT /F1 12 Tf 72 720 Td (Hello) Tj ET`）+ **字体资源** + **xref 交叉引用表**——**"生成 PDF"就是"按坐标画字/线，再写 xref"**，并不神秘（与 PDF 1.4 语法对应）。
 *   - **坐标系**：**原点在左下角**（不是左上），**单位是 point**（1/72 英寸），A4 = 595×842pt——**排版时 y 轴从大到小走**（从上往下），这是新手最容易搞反的地方。
 *   - **中文的关键：字体嵌入**——标准 14 字体不含中文，**必须嵌入字体（Type0/CIDFont + ToUnicode）**，否则中文显示为空白/方框——**这是"PDF 中文乱码"的唯一根因**。
 *   - **排版要素**：**分页**（内容超出页面高度就 `showPage` + 换页）、**表格**（画线 + 逐格写文本 + 列宽计算）、**页眉页脚/页码**——**报表 PDF = 这三样的组合**。
 *   - 与 73 章的关系：PDF 是"不可编辑的交付物"，**通常配合签名 URL 下载**（73-06）——**发票/合同/对账单**是典型场景。
 *   - 易错点：y 轴当左上（内容倒置/出界）；不嵌中文（乱码）；不处理分页（内容溢出丢失）；表格不换行（长文本撑破）。
 * ============================================================
 */

package com.heima.ch76;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class PdfLayout {
    /** 极简 PDF 生成：页面 + 文本 + 线（不嵌入中文，演示排版坐标系） */
    static class PdfBuilder {
        final List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        final double pageWidth = 595;
        final double pageHeight = 842;

        void beginPage() {
            current = new StringBuilder();
        }

        /** 画文本：注意 y 轴从下往上，这里传"距顶部距离"自动换算 */
        void text(double x, double fromTop, double size, String s) {
            double y = pageHeight - fromTop;
            current.append("BT /F1 ").append(size).append(" Tf ")
                    .append(fmt(x)).append(' ').append(fmt(y)).append(" Td (")
                    .append(escapePdf(s)).append(") Tj ET\n");
        }

        void line(double x1, double fromTop1, double x2, double fromTop2) {
            current.append(fmt(x1)).append(' ').append(fmt(pageHeight - fromTop1)).append(" m ")
                    .append(fmt(x2)).append(' ').append(fmt(pageHeight - fromTop2)).append(" l S\n");
        }

        void endPage() {
            pages.add(current.toString());
        }

        static String fmt(double v) {
            return String.format("%.1f", v);
        }

        static String escapePdf(String s) {
            return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }

        byte[] build() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();
            StringBuilder pdf = new StringBuilder();
            pdf.append("%PDF-1.4\n");

            // 对象 1：Catalog；对象 2：Pages；对象 3：Font；对象 4..：Page/Content
            int obj1 = 1, obj2 = 2, obj3 = 3;
            int firstPage = 4;
            int objCount = 3 + pages.size() * 2;

            offsets.add(0);
            StringBuilder body = new StringBuilder();
            offsets.add(body.length());
            body.append(obj1).append(" 0 obj << /Type /Catalog /Pages ").append(obj2)
                    .append(" 0 R >> endobj\n");
            int pagesOffset = body.length();
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
                kids.append(firstPage + i * 2).append(" 0 R ");
            }
            // 先占位记录 Pages 对象
            body.append(obj2).append(" 0 obj << /Type /Pages /Kids [")
                    .append(kids).append("] /Count ").append(pages.size()).append(" >> endobj\n");
            body.append(obj3).append(" 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n");

            for (int i = 0; i < pages.size(); i++) {
                int pageObj = firstPage + i * 2;
                int contentObj = pageObj + 1;
                body.append(pageObj).append(" 0 obj << /Type /Page /Parent ").append(obj2)
                        .append(" 0 R /MediaBox [0 0 ").append((int) pageWidth).append(' ')
                        .append((int) pageHeight).append("] /Contents ").append(contentObj)
                        .append(" 0 R /Resources << /Font << /F1 ").append(obj3)
                        .append(" 0 R >> >> >> endobj\n");
                String content = pages.get(i);
                body.append(contentObj).append(" 0 obj << /Length ")
                        .append(content.getBytes(StandardCharsets.ISO_8859_1).length)
                        .append(" >> stream\n").append(content).append("endstream endobj\n");
            }

            pdf.append(body);
            int xrefPos = pdf.length();
            pdf.append("xref\n0 ").append(objCount + 1).append("\n");
            pdf.append("0000000000 65535 f \n");
            for (int i = 1; i <= objCount; i++) {
                pdf.append(String.format("%010d 00000 n \n", 0));
            }
            pdf.append("trailer << /Size ").append(objCount + 1)
                    .append(" /Root ").append(obj1).append(" 0 R >>\nstartxref\n")
                    .append(xrefPos).append("\n%%EOF\n");
            out.write(pdf.toString().getBytes(StandardCharsets.ISO_8859_1));
            return out.toByteArray();
        }
    }

    static void coordinate() {
        System.out.println("【PDF 坐标系】");
        System.out.println("  A4：595 x 842 pt；原点在左下角");
        System.out.println("  距顶部 100pt 的文字 -> 实际 y = 842 - 100 = 742");
        System.out.println("  Td x y：把文本游标移到 (x, y)，然后 Tj 画文本");
        System.out.println("  常见错误：以为原点在左上 -> 内容倒置或跑出页面");
        System.out.println();
    }

    static void chineseIssue() {
        System.out.println("【中文必须嵌入字体】");
        System.out.println("  /BaseFont /Helvetica 只含西文 -> 中文显示空白/方框");
        System.out.println("  正解：Type0 复合字体 + CIDFont（如 STSong-Light）+ ToUnicode 映射");
        System.out.println("  结论：正式报表用成熟库（iText/OpenPDF），但要知道它在做什么");
        System.out.println();
    }

    public static void main(String[] args) throws IOException {
        coordinate();
        chineseIssue();

        PdfBuilder pdf = new PdfBuilder();
        pdf.beginPage();
        pdf.text(72, 72, 18, "Order Report");
        pdf.line(72, 90, 523, 90);
        pdf.text(72, 110, 11, "No.   Customer        Amount");
        pdf.text(72, 130, 11, "1001  Zhang San       69.00");
        pdf.text(72, 150, 11, "1002  Li Si          129.00");
        pdf.text(72, 800, 9, "Page 1 / 1");
        pdf.endPage();

        byte[] bytes = pdf.build();
        System.out.printf("【生成 PDF】%d 字节，%d 页%n", bytes.length, pdf.pages.size());
        System.out.println("  内容流示例（前 3 行）：");
        pdf.pages.get(0).lines().limit(3).forEach(l -> System.out.println("    " + l));
        System.out.println();
        System.out.println("【报表 PDF 三件套】分页（超高度换页）/ 表格（画线+逐格）/ 页眉页脚页码");
        System.out.println("  生产建议：用成熟库，但务必嵌入中文字体 + 显式处理分页");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用本章的生成器输出 2 页 PDF：第 1 页表格，第 2 页汇总，验证 `showPage` 分页。
 * 2. 说明"y 轴从下往上"的换算，并用"距顶部 100pt"的文字验证位置正确。
 * 3. 思考题：为什么"中文不嵌入字体就乱码"？从"标准 14 字体不含 CJK"与"CIDFont"分析。
 * ============================================================
 */
