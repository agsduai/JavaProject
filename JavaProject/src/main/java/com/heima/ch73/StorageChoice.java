/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-04
 * 课名：存储选型：本地盘、对象存储与 CDN
 * ------------------------------------------------------------
 * 知识点：
 *   - **三种存储的定位**：**本地盘/文件系统**（低延迟、需自己管容量与备份、适合临时文件与热数据）、**对象存储**（无限容量、按量计费、HTTP 直连、适合几乎全部持久文件）、**CDN**（边缘缓存、加速下载、抗带宽峰值、适合公开静态资源）。
 *   - **选型的三个判据**：**访问模式**（谁访问、频率、地域分布）、**一致性要求**（写完能否立即读）、**成本结构**（存储费 vs 流量费——出网流量常是隐形大头 46-03）。
 *   - **典型组合**：**上传直传对象存储** -> **私有文件走签名 URL**（73-06）-> **公开资源挂 CDN** -> **缩略图异步生成后也放对象存储**——**本地盘只用于临时中转**（如上传时的分片暂存）。
 *   - **对象存储的一致性**：现代对象存储通常"写后强一致读"，但**跨区域复制有延迟**——**CDN 缓存有 TTL**（更新后要刷新缓存，73-07）。
 *   - **与 37-06 的关系**：容器的数据卷是"本地盘"的容器化形态——**容器内的本地盘不适合持久化业务文件**（容器一删就没），应挂卷或直接对象存储。
 *   - 易错点：业务文件存应用服务器本地盘（扩容/迁移丢数据）；公开资源直连对象存储（贵且慢，该走 CDN）；CDN 缓存更新不刷新（用户看到旧图）；忽略出网流量成本。
 * ============================================================
 */

package com.heima.ch73;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class StorageChoice {
    record Option(String name, String pros, String cons, String fit) {
    }

    static void compare() {
        System.out.println("【三种存储对比】");
        List<Option> options = List.of(
                new Option("本地盘", "延迟最低、无需网络", "要自己管容量/备份/迁移",
                        "临时文件、构建缓存"),
                new Option("对象存储", "无限容量、按量、HTTP 直连", "出网流量费、延迟略高",
                        "几乎所有持久文件"),
                new Option("CDN", "边缘加速、抗峰值", "缓存有 TTL、刷新有成本",
                        "公开静态资源（图片/视频/JS）"));
        for (Option o : options) {
            System.out.printf("  %-8s 优势=%-26s 劣势=%-26s 适合=%s%n",
                    o.name(), o.pros(), o.cons(), o.fit());
        }
        System.out.println();
    }

    /** 成本模型：存储费 + 流量费 */
    static void costModel() {
        System.out.println("【成本模型（模拟参数）】");
        long storageGb = 2000;
        double storagePricePerGbMonth = 0.12;
        long monthlyDownloadGb = 5000;
        double egressPricePerGb = 0.5;
        double cdnPricePerGb = 0.2;

        double originCost = storageGb * storagePricePerGbMonth;
        double egressDirect = monthlyDownloadGb * egressPricePerGb;
        double egressCdn = monthlyDownloadGb * cdnPricePerGb;
        System.out.printf("  存储 %dGB -> %.0f 元/月%n", storageGb, originCost);
        System.out.printf("  直连下载 %dGB -> %.0f 元/月（出网费）%n",
                monthlyDownloadGb, egressDirect);
        System.out.printf("  走 CDN %dGB -> %.0f 元/月（省 %.0f%%）%n",
                monthlyDownloadGb, egressCdn, (1 - egressCdn / egressDirect) * 100);
        System.out.printf("  总成本：直连 %.0f 元 vs CDN %.0f 元%n",
                originCost + egressDirect, originCost + egressCdn);
        System.out.println("  结论：下载量大时，CDN 省的钱远超缓存管理的复杂度");
        System.out.println();
    }

    static void typicalCombination() {
        System.out.println("【典型组合】");
        Map<String, String> flow = new LinkedHashMap<>();
        flow.put("上传", "客户端分片直传对象存储（应用只发签名 73-07）");
        flow.put("私有文件下载", "应用签发临时 URL（73-06）");
        flow.put("公开图片", "对象存储 -> CDN 回源，边缘缓存");
        flow.put("缩略图", "异步生成后写对象存储（同桶不同前缀）");
        flow.put("临时分片", "本地盘（上传完成后清理）");
        flow.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
        System.out.println();
        System.out.println("  反模式：业务文件写容器本地盘 -> 重启即丢（37-06）");
    }

    public static void main(String[] args) {
        compare();
        costModel();
        typicalCombination();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的业务列出 5 类文件（用户头像/合同 PDF/导出报表/临时分片/公开图片），各选存储并说明理由。
 * 2. 用真实/模拟参数算一次成本：存储 + 直连出网 vs 存储 + CDN，找出"切换 CDN 的盈亏平衡点"。
 * 3. 思考题：为什么"业务文件存容器本地盘"是事故？从"容器重建"与"多副本调度"分析。
 * ============================================================
 */
