/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：46-03
 * 课名：对象存储与数据分层
 * ------------------------------------------------------------
 * 知识点：
 *   - **对象存储（S3/OSS/MinIO）已是数据湖的默认底座**：无限容量、按量付费、高持久性（11 个 9）；代价是**没有目录语义与原子 rename**（列出桶要分页、改名 = 复制+删除）。**HDFS 正在被对象存储替代**（存算分离）。
 *   - **存算分离的架构意义**：计算集群（Spark/Trino）按需起停，数据常驻对象存储——**计算与数据各自弹性**（HDFS 时代计算与存储绑死在一批机器上）。代价：数据不在本地（网络读），靠缓存与列裁剪补。
 *   - **对象存储的语义陷阱**：**rename 不原子**——"先把数据写到 tmp 再 rename 到正式目录"的提交模式在对象存储上不安全；**这就是表格式（46-05）存在的理由之一**：用元数据清单（meta.json 指向文件列表）实现原子提交，而不是依赖文件系统 rename。
 *   - **数据分层布局**：ODS 原样桶（低频访问、生命周期 90 天转归档）、加工层桶（频繁读）、归档层（合规保留，最低成本）。**生命周期策略**让冷数据自动降级——数据平台的"冷热分离"（39-10 思想的数据版）。
 *   - **一致性模型**：对象存储普遍提供强一致读（写后立即可见），但**列举与 rename 的语义与本地文件系统不同**——依赖"目录列表"的程序要按前缀分页设计。
 *   - **成本结构**：存储费 + 请求费 + 出网流量费——**出网（跨云/下载）是隐形大头**，数据不出域（内网分析）同时省钱与合规。
 *   - 易错点：把对象存储当文件系统用（大量小文件 + rename 依赖，性能与正确性双崩）；生命周期没配（十年日志躺在热存储烧钱）；跨云出网无预算（账单惊吓）；安全上桶策略过宽（匿名可读 = 数据泄露，36 章教训）。
 * ============================================================
 */

package com.heima.ch46;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ObjectStorage {
    static void hdfsToS3() {
        System.out.println("【存算分离：HDFS 时代 vs 对象存储时代】");
        String[][] rows = {
                {"容量扩展", "加机器（存储计算绑定）", "桶容量无限，独立扩展"},
                {"弹性", "计算不能单独缩", "计算集群按需起停"},
                {"文件语义", "目录+原子 rename", "扁平 key，rename=复制+删除"},
                {"一致性", "rename 原子提交", "需表格式补原子提交"},
                {"成本", "整机持有", "按量（存储/请求/流量）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-26s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void renameTrap() {
        System.out.println("【rename 陷阱：提交模式的安全实现】");
        List<String> unsafe = List.of(
                "1) 写数据到 /data/tmp/part-1.parquet",
                "2) rename /data/tmp/* -> /data/official/",
                "3) 对象存储 rename 非原子：部分文件已移动时进程挂 -> 半提交状态");
        for (String s : unsafe) {
            System.out.println("  " + s);
        }
        List<String> safe = List.of(
                "表格式做法：先写数据文件（任意位置，幂等命名）",
                "          再原子替换“元数据清单”（指向新文件列表）",
                "          清单替换是单对象覆盖 = 原子；读端按清单找文件");
        for (String s : safe) {
            System.out.println("  " + s);
        }
        System.out.println("  这正是 Iceberg/Delta/Hudi 的提交原理（46-05）");
        System.out.println();
    }

    static void tiering() {
        System.out.println("【数据分层与生命周期】");
        record Tier(String layer, String storage, String retention, String cost) {
        }
        List<Tier> tiers = List.of(
                new Tier("热：DWD/DWS", "标准存储", "在线 90 天", "基准价"),
                new Tier("温：ODS 近期", "低频存储", "90~365 天", "约 40% 价"),
                new Tier("冷：归档", "归档/深冷", "合规期 1~3 年", "约 10% 价，取回贵"));
        for (Tier t : tiers) {
            System.out.printf("  %-12s %-10s %-12s %s%n", t.layer(), t.storage(), t.retention(), t.cost());
        }
        System.out.println("  生命周期规则自动降级：超 90 天未访问 -> 低频；超 1 年 -> 归档");
        System.out.println("  39-10 冷热分离思想在对象存储上的原生支持");
        System.out.println();
    }

    static void costTrap() {
        System.out.println("【成本三要素：存储/请求/出网】");
        List<String> notes = List.of(
                "出网流量是隐形大头：跨云导出 10TB 可能比存储一年还贵",
                "请求费：百万小文件的 LIST/GET 请求费超过存储费",
                "治理：数据不出域（内网分析）、小文件治理、生命周期三层兜底",
                "安全：桶策略最小化（36 章），禁止匿名读与宽泛 IAM");
        for (String n : notes) {
            System.out.println("  - " + n);
        }
    }

    public static void main(String[] args) {
        hdfsToS3();
        renameTrap();
        tiering();
        costTrap();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的数据平台设计桶布局与生命周期策略（分层/保留期/降级规则），估算当前与一年后的存储成本。
 * 2. 复现 rename 陷阱：模拟"rename 中途失败"，观察半提交状态对下游的影响，然后实现清单式原子提交修复。
 * 3. 思考题：为什么对象存储"没有原子 rename"催生了表格式？从提交原子性与并发写两个角度分析。
 * ============================================================
 */
