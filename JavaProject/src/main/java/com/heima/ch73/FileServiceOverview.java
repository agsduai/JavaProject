/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-01
 * 课名：文件服务全景：上传、存储、下载
 * ------------------------------------------------------------
 * 知识点：
 *   - **文件服务的三件事**：**上传**（怎么把字节可靠地送上来）、**存储**（放哪里、怎么组织）、**下载**（怎么快速安全地取走）——**看似简单，实际是企业系统最容易出事故的模块**（大文件超时、磁盘打满、越权下载、图片被拖垮带宽）。
 *   - **文件的三层数据**：**元数据**（文件名/大小/类型/hash/归属人/存储路径——存数据库）、**二进制内容**（存对象存储/文件系统）、**衍生文件**（缩略图/转码结果/水印版——异步生成）——**元数据与内容分离**是标准架构（46-03 对象存储思想）。
 *   - **上传方式的演进**：**表单直传**（小文件，一次请求）-> **分片上传**（大文件，切片并发）-> **直传对象存储**（客户端拿签名直传到 OSS，不经过应用服务器——省带宽与内存）。
 *   - **与 37/46 章的关系**：37-06 讲容器的数据卷（存储位置）、46-03 讲对象存储的分层与成本——**本章讲业务侧怎么用它们**。
 *   - **容量与成本**：文件是"只增不减"的存储大户——**生命周期策略（73-10）与去重（73-03）是降本两大手段**。
 *   - 易错点：把文件存数据库 blob（数据库膨胀、备份慢）；应用服务器中转所有上传（带宽与内存双爆）；文件名直接用用户上传的名字（路径穿越 36 章）；不校验类型与大小（上传可执行文件）。
 * ============================================================
 */

package com.heima.ch73;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FileServiceOverview {
    record FileMeta(String fileId, String originalName, String contentType, long sizeBytes,
                    String hash, String storageKey, String ownerId, long createdAtMs) {
    }

    record StorageLayer(String layer, String what, String where, String note) {
    }

    static void threeLayers() {
        System.out.println("【文件的三层数据】");
        List<StorageLayer> layers = List.of(
                new StorageLayer("元数据", "名称/大小/类型/hash/归属", "MySQL", "可检索、可审计"),
                new StorageLayer("二进制内容", "文件本体", "对象存储 / 本地盘", "按 storageKey 寻址"),
                new StorageLayer("衍生文件", "缩略图/水印/转码", "对象存储（同桶不同前缀）", "异步生成，可重建"));
        for (StorageLayer l : layers) {
            System.out.printf("  %-8s %-24s %-24s %s%n", l.layer(), l.what(), l.where(), l.note());
        }
        System.out.println();
    }

    static void uploadEvolution() {
        System.out.println("【上传方式演进】");
        String[][] rows = {
                {"表单直传", "< 5MB 小文件", "一次请求，应用服务器中转"},
                {"分片上传", "大文件（100MB~数 GB）", "切片并发 + 断点续传（73-02）"},
                {"直传对象存储", "任意大小", "应用只发签名，客户端直传 OSS（省带宽）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-22s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void metadataDesign() {
        System.out.println("【元数据表设计要点】");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("file_id", "业务主键（雪花 39-07），不暴露路径");
        fields.put("original_name", "仅展示用，绝不用于拼路径");
        fields.put("content_type", "MIME 类型（校验 + 下载头）");
        fields.put("size_bytes", "大小（配额统计 + 校验）");
        fields.put("hash", "内容摘要（秒传 73-03 + 完整性校验）");
        fields.put("storage_key", "存储层寻址键（如 yyyy/MM/dd/uuid）");
        fields.put("owner_id", "归属（越权下载防护 73-06）");
        fields.put("status", "UPLOADING/READY/DELETED（生命周期）");
        fields.forEach((k, v) -> System.out.printf("  %-14s %s%n", k, v));
        System.out.println();
        System.out.println("  反模式：用 original_name 拼存储路径 -> 路径穿越与重名覆盖");
    }

    public static void main(String[] args) {
        threeLayers();
        uploadEvolution();
        metadataDesign();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的文件服务设计元数据表（≥8 字段），标注每字段的用途与索引需求。
 * 2. 画出三种上传方式的时序（表单直传/分片/直传 OSS），标注应用服务器承担的职责变化。
 * 3. 思考题：为什么"元数据与二进制分离"是标准架构？从"检索需求"与"存储成本"两个角度分析。
 * ============================================================
 */
