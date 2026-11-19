/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-08
 * 课名：大文件下载与断点下载
 * ------------------------------------------------------------
 * 知识点：
 *   - **大文件下载的两个关键**：**Range 请求**（HTTP 的 `Range: bytes=start-end`，客户端可分段下载/续传）与 **Content-Disposition**（`attachment; filename=...` 触发下载，文件名要按 RFC 5987 编码非 ASCII）。
 *   - **服务端要支持的三件事**：**`Accept-Ranges: bytes`** 声明支持分段、**`206 Partial Content`** 返回部分内容（带 `Content-Range`）、**`416 Range Not Satisfiable`** 处理非法区间——**缺一个客户端就无法续传**。
 *   - **下载链路的选择**：**小文件**直接签名 URL 给对象存储/CDN；**大文件**同样签名 URL（对象存储原生支持 Range）——**应用服务器不应中转文件流**（占用连接与带宽）。
 *   - **下载的内容安全**：**文件名注入**（`filename="../../x"` 或 CRLF 注入）要过滤；**下载文件的 MIME 不要用用户给的**（用白名单）；**压缩包下载要防 zip 炸弹**（73-05）。
 *   - **与 73-06 的关系**：Range 请求同样要签名（**签名要覆盖"整个资源"而非某个区间**，否则攻击者可只签一小段再拉全部）。
 *   - 易错点：不支持 Range（大文件只能从头下）；签名只覆盖部分请求（可绕过）；文件名直接来自数据库（历史脏数据带恶意字符）；应用服务器中转下载（带宽被打满）。
 * ============================================================
 */

package com.heima.ch73;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class RangeDownload {
    record RangeResult(int status, String contentRange, int length, String note) {
    }

    static final long TOTAL_SIZE = 100_000_000L;   // 100MB

    /** 解析并处理 Range 请求 */
    static RangeResult handleRange(String rangeHeader) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return new RangeResult(200, null, (int) Math.min(TOTAL_SIZE, Integer.MAX_VALUE),
                    "全量下载（Accept-Ranges: bytes）");
        }
        if (!rangeHeader.startsWith("bytes=")) {
            return new RangeResult(416, null, 0, "非法 Range 语法");
        }
        String spec = rangeHeader.substring("bytes=".length()).trim();
        String[] p = spec.split("-", -1);
        try {
            long start;
            long end;
            if (p[0].isEmpty()) {                       // bytes=-500 表示最后 500 字节
                long suffix = Long.parseLong(p[1]);
                start = Math.max(0, TOTAL_SIZE - suffix);
                end = TOTAL_SIZE - 1;
            } else {
                start = Long.parseLong(p[0]);
                end = p[1].isEmpty() ? TOTAL_SIZE - 1 : Long.parseLong(p[1]);
            }
            if (start < 0 || start >= TOTAL_SIZE || end < start) {
                return new RangeResult(416, "bytes */" + TOTAL_SIZE, 0,
                        "区间非法 -> 返回 416 + 完整长度");
            }
            end = Math.min(end, TOTAL_SIZE - 1);
            long len = end - start + 1;
            return new RangeResult(206,
                    "bytes " + start + "-" + end + "/" + TOTAL_SIZE,
                    (int) Math.min(len, Integer.MAX_VALUE),
                    "部分内容（可续传）");
        } catch (NumberFormatException e) {
            return new RangeResult(416, null, 0, "数字解析失败");
        }
    }

    /** Content-Disposition：非 ASCII 文件名用 RFC 5987 编码 */
    static String contentDisposition(String filename, boolean inline) {
        String safe = filename.replace("\"", "").replace("\r", "").replace("\n", "")
                .replace("/", "_").replace("\\", "_");
        boolean ascii = safe.chars().allMatch(c -> c < 128);
        String type = inline ? "inline" : "attachment";
        if (ascii) {
            return type + "; filename=\"" + safe + "\"";
        }
        StringBuilder encoded = new StringBuilder();
        for (byte b : safe.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            encoded.append(String.format("%%%02X", b));
        }
        return type + "; filename*=UTF-8''" + encoded;
    }

    /** 签名覆盖整个资源（不是某个区间） */
    static String signResource(String path, long expires) {
        return path + "?expires=" + expires + "&sign="
                + Integer.toHexString((path + expires).hashCode());
    }

    public static void main(String[] args) {
        System.out.println("【Range 请求处理】");
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("无 Range", "全量下载");
        cases.put("bytes=0-1023", "首 1KB");
        cases.put("bytes=50000000-", "从 50MB 到尾（续传）");
        cases.put("bytes=-1024", "最后 1KB");
        cases.put("bytes=999999999-", "越界");
        cases.put("bytes=100-50", "区间反了");
        cases.put("items=0-100", "语法错误");
        for (Map.Entry<String, String> e : cases.entrySet()) {
            RangeResult r = handleRange(e.getKey().equals("无 Range") ? null : e.getKey());
            System.out.printf("  %-18s -> %d %-40s %s%n", e.getKey(), r.status(),
                    r.contentRange() == null ? "-" : r.contentRange(), r.note());
        }

        System.out.println();
        System.out.println("【Content-Disposition】");
        for (String f : new String[]{"report.pdf", "月度报表.pdf", "../../etc/passwd"}) {
            System.out.printf("  %-18s -> %s%n", f, contentDisposition(f, false));
        }

        System.out.println();
        System.out.println("【签名覆盖整个资源】");
        System.out.println("  " + signResource("files/contract-9001.pdf", 1_000_300));
        System.out.println("  注意：签名针对 path，Range 只是请求头 -> 无法用局部签名放大下载范围");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Range 解析：覆盖 7 种请求（含后缀区间/越界/语法错），输出状态码与 Content-Range。
 * 2. 实现 Content-Disposition 安全化：过滤路径与 CRLF，非 ASCII 用 RFC 5987 编码，用 3 个文件名验证。
 * 3. 思考题：为什么"签名只覆盖路径而不含 Range"是安全的？从"Range 不改变资源身份"分析。
 * ============================================================
 */
