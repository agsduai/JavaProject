/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：46-04
 * 课名：Hive 与元数据管理
 * ------------------------------------------------------------
 * 知识点：
 *   - **Hive 的历史贡献是把 SQL 带到大数据**：表 = 目录 + schema + 分区的映射（Metastore 记录），SQL 编译成分布式作业。今天 Hive 引擎本身渐渐淡出，**Hive Metastore（ HMS ）仍是元数据事实标准**——Spark/Trino/Flink 都从它读"表在哪、什么格式"。
 *   - **Metastore 记录什么**：库/表/列 schema、分区列表、存储位置与格式、表属性。**它是"数据目录"的雏形**（46-08 治理的载体）：表注册即入 HMS，无注册不可查询。
 *   - **分区管理的运维成本**：每天加分区（`ADD PARTITION`）在高表数场景成为管理负担——Iceberg 类表格式把分区做成表数据的一部分（隐藏分区），**分区演进不再破坏数据**。
 *   - **schema on read 的双刃剑**：ODS 层原样存储靠读时解释，灵活但**列类型错误在读时才爆**——规范：ODS 保留原始 payload + 注册层给出推断 schema 与校验报告。
 *   - **权限与审计**：HMS 层做库表权限（Ranger 类），配合桶策略（46-03）与行级权限——**数据权限的落点在元数据层而不是散在各引擎**。
 *   - **小表大表的管理差异**：维表小表直接全量缓存（43-07）；事实大表分区 + 增量。**元数据里记录表的规模与更新频率**，供引擎做执行计划与调度决策。
 *   - 易错点：元数据与实际文件漂移（手工删了文件，HMS 还指向它——查询报错）；分区手工 ADD 漏掉（数据在目录里但查不到）；权限只配在引擎层（换引擎权限失效）；表无注释无责任人（治理无从下手）。
 * ============================================================
 */

package com.heima.ch46;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class HiveMetastore {
    record TableMeta(String name, String location, String format, String partitionCol,
                     String owner, long rowsEstimate) {
    }

    static Map<String, TableMeta> metastore = new LinkedHashMap<>();

    static void register() {
        metastore.put("ods.orders", new TableMeta("ods.orders",
                "s3://lake/ods/orders", "JSON", "dt", "sync-team", 0));
        metastore.put("dwd.orders", new TableMeta("dwd.orders",
                "s3://lake/dwd/orders", "Parquet", "dt", "data-lee", 8_600_000_000L));
        metastore.put("dim.product", new TableMeta("dim.product",
                "s3://lake/dim/product", "Parquet", null, "data-lee", 500_000L));
        System.out.println("【HMS 注册表：表在哪、谁负责、多大】");
        for (TableMeta t : metastore.values()) {
            System.out.printf("  %-12s 格式=%-8s 分区=%-4s 责任人=%-9s 行数≈%,d%n",
                    t.name(), t.format(), t.partitionCol() == null ? "-" : t.partitionCol(),
                    t.owner(), t.rowsEstimate());
        }
        System.out.println("  治理基线：无注册的表不可查询；每表必有 owner");
        System.out.println();
    }

    static void driftProblem() {
        System.out.println("【元数据漂移：手工操作的代价】");
        System.out.println("  有人直接在对象存储删了 dt=2026-11-15 目录：");
        TableMeta dwd = metastore.get("dwd.orders");
        System.out.printf("  HMS 仍记录分区 dt=2026-11-15 -> %s%n", dwd.location() + "/dt=2026-11-15");
        System.out.println("  查询报错：文件不存在；报表失败；排查 2 小时发现是手工删桶");
        List<String> cures = List.of(
                "删除必须走表格式/SQL（msck repair 或 drop partition）",
                "每日校验：HMS 分区清单 vs 实际目录 diff 告警",
                "桶策略：生产数据目录禁止人工写删权限");
        for (String c : cures) {
            System.out.println("  处方：" + c);
        }
        System.out.println();
    }

    static void schemaOnRead() {
        System.out.println("【ODS 的 schema-on-read：保真与风险的平衡】");
        String rawJson = "{\"id\":9001,\"amt\":\"69.0\",\"ts\":\"2026-11-17T10:00:00Z\"}";
        System.out.println("  原始 payload：" + rawJson);
        System.out.println("  风险点：amt 是字符串（类型漂移）；ts 是 ISO 格式（与 DWD 统一口径）");
        List<String> rules = List.of(
                "ODS 层保留原样 payload（审计可回放）",
                "注册层提供推断 schema + 每日校验报告（类型漂移检测）",
                "DWD 层做类型归一（on-write），下游永远拿干净列");
        for (String r : rules) {
            System.out.println("  " + r);
        }
        System.out.println();
    }

    static void permissionLayer() {
        System.out.println("【权限落点：元数据层统一，而不是散在引擎】");
        Map<String, String> grants = new LinkedHashMap<>();
        grants.put("analyst-1", "dwd.orders(只读) + dim.*(只读)");
        grants.put("etl-bot", "dwd.*(读写) + ods.*(读)");
        grants.put("external-bi", "ads.*(只读，行级：仅本部门)");
        for (Map.Entry<String, String> e : grants.entrySet()) {
            System.out.printf("  %-12s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  换引擎（Spark->Trino）权限不变 —— 因为权限跟着表不跟着引擎");
    }

    public static void main(String[] args) {
        register();
        driftProblem();
        schemaOnRead();
        permissionLayer();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的数据资产建一份元数据登记表（表/位置/格式/分区/责任人/规模），并实现"分区清单 vs 实际目录"的每日 diff 校验脚本。
 * 2. 演示 schema-on-read 的类型漂移事故：ODS 中金额列从数字变字符串，观察下游 DWD 的反应，落地校验报告方案。
 * 3. 思考题：为什么数据权限应该落在元数据层而不是各引擎？从"换引擎权限不失效"与审计一致性两个角度分析。
 * ============================================================
 */
