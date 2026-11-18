/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-07
 * 课名：文件上传
 * ------------------------------------------------------------
 * 知识点：
 *   - 前端表单必须是 `enctype="multipart/form-data"`，否则后端收不到文件；Spring MVC 用 `MultipartFile` 接收。
 *   - 单文件：`@RequestParam("file") MultipartFile file`；多文件：`MultipartFile[] files` 或 `List<MultipartFile>`。
 *   - 配置限制：`spring.servlet.multipart.max-file-size=10MB`、`max-request-size=100MB`，超出抛 `MaxUploadSizeExceededException`。
 *   - 安全校验（缺一不可）： 1. 文件大小（业务上限，别只靠框架配置）； 2. 扩展名白名单（`.jpg/.png/.pdf`）； 3. **MIME / 文件头魔数**校验（扩展名可以伪造，`Content-Type` 也可以伪造）； 4. 文件名重命名（UUID），绝不使用用户上传的原始文件名拼接路径——否则 `../../etc/passwd` 就是路径穿越漏洞； 5. 存储目录不在 Web 根目录下，或存储后由后端做权限校验再输出。
 *   - 常见魔数：JPEG `FF D8 FF`、PNG `89 50 4E 47`、GIF `47 49 46 38`、PDF `25 50 44 46`、ZIP/DOCX `50 4B 03 04`。
 *   - 存储位置：本地磁盘（单机）、对象存储 OSS/MinIO（推荐，天然支持 CDN 与水平扩展）。数据库只存 URL。
 *   - 易错：把文件存进数据库 BLOB（大文件拖垮数据库）；用原始文件名直接拼路径；上传目录可被直接访问导致脚本被解析执行。
 * ============================================================
 */

package com.heima.ch26;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ==================== 示例代码 ====================

public class FileUploadDemo {
    static final long MAX_SIZE = 2 * 1024 * 1024;          // 2MB
    static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "pdf");
    static final Map<String, byte[]> MAGIC = new LinkedHashMap<>();

    static {
        MAGIC.put("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MAGIC.put("png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        MAGIC.put("pdf", new byte[]{0x25, 0x50, 0x44, 0x46});
    }

    public static void main(String[] args) throws IOException {
        Path uploadDir = Files.createTempDirectory("upload-demo-");
        try {
            System.out.println("=== 正常上传 PNG ===");
            byte[] png = concat(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                    "fake-png-body".getBytes());
            System.out.println("  " + upload("头像.png", png, uploadDir));

            System.out.println();
            System.out.println("=== 伪造扩展名：exe 改名成 jpg ===");
            byte[] exe = concat("MZ".getBytes(), "fake-exe".getBytes());
            System.out.println("  " + upload("virus.jpg", exe, uploadDir));

            System.out.println();
            System.out.println("=== 路径穿越：文件名带 ../ ===");
            byte[] ok = concat(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "jpeg".getBytes());
            System.out.println("  " + upload("../../evil.jpg", ok, uploadDir));
            System.out.println();
            System.out.println("=== 超出大小限制 ===");
            byte[] big = new byte[(int) MAX_SIZE + 1];
            big[0] = (byte) 0xFF;
            big[1] = (byte) 0xD8;
            big[2] = (byte) 0xFF;
            System.out.println("  " + upload("big.jpg", big, uploadDir));

            System.out.println();
            System.out.println("=== 不在白名单的扩展名 ===");
            System.out.println("  " + upload("shell.jsp", ok, uploadDir));

            System.out.println();
            System.out.println("=== 上传目录内容 ===");
            try (var s = Files.list(uploadDir)) {
                s.forEach(p -> System.out.println("  " + p.getFileName()));
            }
        } finally {
            // 清理临时目录
            try (var s = Files.walk(uploadDir)) {
                s.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // 清理失败不影响演示
                            }
                        });
            }
        }

        System.out.println();
        System.out.println("=== 控制器写法 ===");
        System.out.println("""
                @PostMapping("/upload")
                public Result<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
                    if (file.isEmpty()) {
                        return Result.fail("文件为空");
                    }
                    String original = file.getOriginalFilename();
                    // 1. 原始文件名不许带路径，也不许含 ..
                    if (original == null || original.contains("/") || original.contains("\\\\")
                            || original.contains("..")) {
                        return Result.fail("非法文件名");
                    }
                    // 2. 扩展名白名单
                    String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
                    if (!ALLOWED_EXT.contains(ext)) {
                        return Result.fail("不支持的文件类型");
                    }
                    // 3. 落盘用 UUID 重命名，二次确认路径在目录内
                    String newName = UUID.randomUUID() + "." + ext;
                    Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
                    Path target = base.resolve(newName).normalize();
                    if (!target.startsWith(base)) {
                        return Result.fail("非法文件名");
                    }
                    file.transferTo(target.toFile());
                    return Result.ok("/files/" + newName);
                }
                """);
    }

    /** 返回校验结果描述；通过则落盘 */
    static String upload(String filename, byte[] content, Path dir) throws IOException {
        if (content == null || content.length == 0) {
            return "拒绝：文件为空";
        }
        if (content.length > MAX_SIZE) {
            return "拒绝：文件 " + content.length + " 字节，超过上限 " + MAX_SIZE;
        }
        // 第一道：原始文件名不能带路径分隔符，也不能是 ..
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return "拒绝：文件名含路径穿越字符（" + filename + "）";
        }
        String ext = extOf(filename);
        if (!ALLOWED_EXT.contains(ext)) {
            return "拒绝：扩展名 ." + ext + " 不在白名单 " + ALLOWED_EXT;
        }
        if (!magicMatches(ext, content)) {
            return "拒绝：文件头与扩展名不符（伪造的 ." + ext + "）";
        }
        // 第二道：即使校验被绕过，落盘也用 UUID 重命名，永不拼接原始文件名
        String safeName = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(safeName).normalize();
        if (!target.startsWith(dir)) {
            return "拒绝：路径穿越";
        }
        Files.write(target, content);
        return "成功：原始名 " + filename + " -> 存储为 " + safeName;
    }

    static String extOf(String filename) {
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1).toLowerCase();
    }

    static boolean magicMatches(String ext, byte[] content) {
        byte[] magic = MAGIC.get(ext);
        if (magic == null) {
            return true;      // pdf 之外的类型按扩展名放行
        }
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为什么"检查扩展名"不足以防止上传木马？写出文件头校验的思路和三种常见类型的魔数。
 * 2. 用户上传文件名是 `../../../webapps/ROOT/shell.jsp`，不做处理会发生什么？给出三层防御措施。
 * 3. 设计一个"头像上传"接口：限制 2MB、只允许 jpg/png、返回可访问 URL，说明文件存哪里、URL 怎么生成。
 * ============================================================
 */
