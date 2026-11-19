/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-05
 * 课名：上传安全：类型、大小与内容校验
 * ------------------------------------------------------------
 * 知识点：
 *   - **文件上传是最高危的入口之一**：**可执行文件上传**（webshell）、**路径穿越**（`../../etc/passwd`）、**超大文件**（磁盘打满）、**zip 炸弹**（解压后爆盘）、**图片隐写/恶意宏**（36 章的攻击面）。
 *   - **四层校验**：**大小限制**（请求级 + 用户级配额）、**类型校验**（MIME + 扩展名白名单——**不能只看扩展名**，要读文件头魔数 66-03 同款）、**文件名安全化**（丢弃原文件名，用 UUID + 白名单扩展名）、**内容扫描**（病毒/恶意宏，异步）。
 *   - **存储隔离**：**用户文件与程序文件不同域**（上传目录禁止执行脚本）、**下载走独立域名**（防 XSS 通过 SVG/HTML 上传执行——36-03 的存储型 XSS）。
 *   - **访问控制**：**私有文件必须签名下载**（73-06）；**归属校验**（不是文件主人不能下——55-04 双键）。
 *   - 与 36 章的关系：上传是"输入校验"的典型场景——**所有校验必须在服务端**（前端 accept 属性只是体验）。
 *   - 易错点：只校验扩展名（改后缀即绕过）；用原文件名拼路径（穿越）；上传目录可执行（webshell）；不限制大小（磁盘打满）；SVG/HTML 直接回显（XSS）。
 * ============================================================
 */

package com.heima.ch73;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class UploadSecurity {
    /** 允许的类型：扩展名 -> 文件头魔数（十六进制前缀） */
    static final Map<String, String> ALLOWED = new LinkedHashMap<>();

    static {
        ALLOWED.put("jpg", "FFD8FF");
        ALLOWED.put("png", "89504E47");
        ALLOWED.put("pdf", "25504446");
        ALLOWED.put("docx", "504B0304");       // zip 容器
    }

    static final long MAX_SIZE = 50L * 1024 * 1024;

    record Check(String name, boolean pass, String detail) {
    }

    static List<Check> validate(String originalName, long sizeBytes, String headHex,
                                String contentType) {
        List<Check> checks = new ArrayList<>();
        // 1) 大小
        checks.add(new Check("大小限制", sizeBytes <= MAX_SIZE,
                sizeBytes + " 字节（上限 " + MAX_SIZE + "）"));
        // 2) 扩展名白名单
        int dot = originalName.lastIndexOf('.');
        String ext = dot < 0 ? "" : originalName.substring(dot + 1).toLowerCase();
        checks.add(new Check("扩展名白名单", ALLOWED.containsKey(ext),
                "扩展名=" + ext));
        // 3) 文件头魔数与扩展名一致（防改后缀）
        String expected = ALLOWED.get(ext);
        boolean magicOk = expected != null && headHex.toUpperCase().startsWith(expected);
        checks.add(new Check("文件头校验", magicOk,
                "头=" + headHex + " 期望前缀=" + expected));
        // 4) MIME 一致性（粗校验）
        boolean mimeOk = contentType != null && !contentType.isBlank();
        checks.add(new Check("Content-Type 存在", mimeOk, "type=" + contentType));
        return checks;
    }

    /** 安全化文件名：永不使用原文件名拼路径 */
    static String safeStorageKey(String originalName, String uuid) {
        int dot = originalName.lastIndexOf('.');
        String ext = dot < 0 ? "" : originalName.substring(dot + 1).toLowerCase();
        return "upload/" + uuid + (ext.isEmpty() ? "" : "." + ext);
    }

    /** 路径穿越检测（展示为什么不能用原文件名） */
    static boolean pathTraversalRisk(String name) {
        return name.contains("..") || name.contains("/") || name.contains("\\")
                || name.contains(":") || name.startsWith(".");
    }

    public static void main(String[] args) {
        System.out.println("【校验用例】");
        Object[][] cases = {
                {"photo.jpg", 1024 * 1024L, "FFD8FFE0", "image/jpeg"},
                {"evil.jpg", 1024 * 1024L, "4D5A9000", "image/jpeg"},          // 改后缀的 exe
                {"invoice.pdf", 2 * 1024 * 1024L, "25504446", "application/pdf"},
                {"huge.zip", 200L * 1024 * 1024, "504B0304", "application/zip"},
                {"shell.php", 1024L, "3C3F7068", "text/plain"}
        };
        for (Object[] c : cases) {
            List<Check> checks = validate((String) c[0], (Long) c[1],
                    (String) c[2], (String) c[3]);
            boolean allPass = checks.stream().allMatch(Check::pass);
            System.out.printf("  %-12s -> %s%n", c[0], allPass ? "通过" : "拒绝");
            checks.stream().filter(x -> !x.pass())
                    .forEach(x -> System.out.printf("      拦截：%s（%s）%n",
                            x.name(), x.detail()));
        }

        System.out.println();
        System.out.println("【文件名安全化】");
        String[] names = {"../../etc/passwd", "normal.jpg", "a b c.png", "C:\\windows\\x.exe"};
        for (String n : names) {
            System.out.printf("  原文件名=%-22s 穿越风险=%-5s -> 存储键=%s%n",
                    n, pathTraversalRisk(n), safeStorageKey(n, "uuid-1234"));
        }

        System.out.println();
        System.out.println("【其他防线】");
        Map<String, String> defenses = new LinkedHashMap<>();
        defenses.put("存储隔离", "上传目录不可执行；下载走独立域名（防 SVG/HTML XSS）");
        defenses.put("异步扫描", "病毒/恶意宏扫描，命中则隔离（不阻塞上传）");
        defenses.put("配额", "用户/租户级总容量限制（55-05）");
        defenses.put("归属校验", "下载时校验 owner（55-04 双键）");
        defenses.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现四层上传校验（大小/扩展名/文件头/MIME），用 5 个用例（含改后缀的木马）验证拦截。
 * 2. 实现文件名安全化与路径穿越检测，用 4 个恶意文件名验证。
 * 3. 思考题：为什么"上传目录可执行"会导致 webshell？从"上传 + 访问触发"的攻击链分析。
 * ============================================================
 */
