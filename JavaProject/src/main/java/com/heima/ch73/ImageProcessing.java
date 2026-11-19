/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-07
 * 课名：图片处理与动态缩放
 * ------------------------------------------------------------
 * 知识点：
 *   - **图片服务的两个关键设计**：**原图只存一份**（保留最高质量）+ **衍生图按需生成并缓存**（缩略图/水印/裁剪）——**不要把每种尺寸都预先全量生成**（尺寸组合爆炸），而是"首次请求生成 + 缓存"。
 *   - **动态缩放的 URL 规范**：`/img/{fileId}_{w}_{h}.webp`——**尺寸进 URL**，CDN/对象存储按 URL 缓存；**同一个 fileId 不同尺寸是不同缓存条目**。
 *   - **WebP/AVIF 的自适应**：按 `Accept` 头返回不同格式（现代浏览器 WebP 体积小 30%）——**格式协商 + 缓存键要含格式**（70-08 的 Vary 思想）。
 *   - **图片处理的成本**：CPU 密集（解码 + 缩放 + 编码）——**异步生成 + 队列**（63 章），**生成结果落对象存储**（后续直接命中缓存）；**超大原图的保护**（限制最大像素，防解压炸弹）。
 *   - **与 73-05 的关系**：图片同样是上传入口——**校验格式与尺寸、剥离 EXIF**（防隐私泄露，68 章：照片里可能带 GPS 坐标）。
 *   - 易错点：全量预生成所有尺寸（存储爆炸）；图片处理同步阻塞上传响应；不限制原图像素（解压炸弹）；不剥离 EXIF（泄露拍摄位置）。
 * ============================================================
 */

package com.heima.ch73;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ImageProcessing {
    record Variant(String key, int width, int height, String format, long sizeBytes) {
    }

    /** 衍生图缓存：key = fileId + 尺寸 + 格式 */
    static final Map<String, Variant> variantCache = new LinkedHashMap<>();
    static int generateCount = 0;

    static String variantKey(String fileId, int w, int h, String format) {
        return fileId + "_" + w + "x" + h + "." + format;
    }

    /** 按需生成：命中缓存直接返回，否则生成 */
    static Variant getVariant(String fileId, int w, int h, String format,
                              long originalBytes) {
        String key = variantKey(fileId, w, h, format);
        Variant cached = variantCache.get(key);
        if (cached != null) {
            System.out.printf("  命中缓存 %s（%d 字节）%n", key, cached.sizeBytes());
            return cached;
        }
        generateCount++;
        // 简化：面积比例估算体积 + 格式压缩系数
        double areaRatio = (w * (double) h) / (4000.0 * 3000.0);
        double formatFactor = format.equals("webp") ? 0.6 : (format.equals("avif") ? 0.5 : 1.0);
        long size = Math.max(2048, (long) (originalBytes * areaRatio * formatFactor));
        Variant v = new Variant(key, w, h, format, size);
        variantCache.put(key, v);
        System.out.printf("  生成 %s（%d 字节）-> 落对象存储%n", key, size);
        return v;
    }

    /** 格式协商：按 Accept 选最优格式 */
    static String negotiateFormat(String accept) {
        if (accept == null) {
            return "jpeg";
        }
        if (accept.contains("image/avif")) {
            return "avif";
        }
        if (accept.contains("image/webp")) {
            return "webp";
        }
        return "jpeg";
    }

    /** 上传保护：像素上限 + EXIF 剥离 */
    static List<String> validateImage(int width, int height, Map<String, String> exif) {
        List<String> issues = new ArrayList<>();
        long pixels = (long) width * height;
        if (pixels > 50_000_000L) {
            issues.add("像素超限（" + pixels + "），可能是解压炸弹");
        }
        if (exif.containsKey("GPS")) {
            issues.add("检测到 GPS 信息（" + exif.get("GPS") + "）-> 需剥离（隐私）");
        }
        if (exif.containsKey("CameraModel")) {
            issues.add("检测到相机型号 -> 建议剥离");
        }
        return issues;
    }

    public static void main(String[] args) {
        long originalBytes = 5L * 1024 * 1024;
        System.out.println("【按需生成 + 缓存】");
        getVariant("f-1", 200, 200, "webp", originalBytes);
        getVariant("f-1", 200, 200, "webp", originalBytes);
        getVariant("f-1", 800, 600, "webp", originalBytes);
        getVariant("f-1", 200, 200, "jpeg", originalBytes);
        System.out.printf("  实际生成次数：%d（其余命中缓存）%n", generateCount);

        System.out.println();
        System.out.println("【格式协商】");
        String[] accepts = {
                "image/avif,image/webp,*/*",
                "image/webp,image/*",
                "image/png,image/jpeg",
                null
        };
        for (String a : accepts) {
            System.out.printf("  Accept=%-32s -> %s%n", "[" + a + "]", negotiateFormat(a));
        }

        System.out.println();
        System.out.println("【上传保护】");
        Map<String, String> exif = new LinkedHashMap<>();
        exif.put("GPS", "30.2741N,120.1551E");
        exif.put("CameraModel", "Phone X");
        validateImage(6000, 4000, exif).forEach(i -> System.out.println("  " + i));
        System.out.println("  正常图（无 EXIF、像素合规）：" + validateImage(1200, 800, Map.of()));

        System.out.println();
        System.out.println("【架构要点】");
        System.out.println("  原图一份 + 衍生图按需 + 结果落对象存储（CDN 缓存）");
        System.out.println("  处理 CPU 密集 -> 异步队列（63 章）；超大图限制像素（防炸弹）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现按需生成 + 缓存：3 种尺寸 × 2 种格式，验证"每种组合只生成一次"。
 * 2. 实现格式协商与缓存键：验证同一 fileId 的 webp 与 jpeg 是不同缓存条目（Vary 思想）。
 * 3. 思考题：为什么"剥离 EXIF"是隐私要求？举出照片 EXIF 可能泄露的三类信息。
 * ============================================================
 */
