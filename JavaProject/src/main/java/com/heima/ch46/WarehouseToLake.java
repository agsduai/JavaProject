/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：46-01
 * 课名：从数仓到数据湖
 * ------------------------------------------------------------
 * 知识点：
 *   - **数仓与数据湖的分野是 schema 何时确定**：数仓（schema-on-write）——数据写入前先定义表结构，质量高但灵活差；数据湖（schema-on-read）——原始数据原样存（JSON/日志/CSV），用到时再解释，灵活但容易变"数据沼泽"（无目录、无格式、无质量）。**湖仓一体（lakehouse）= 湖的灵活 + 仓的治理**。
 *   - **数据平台的典型分层**（43-09 实时数仓分层的离线对应）：ODS 原始层（原样备份）、DWD 明细层（清洗/拆列/关联）、DWS 汇总层（预聚合）、ADS 应用层（报表/接口）。**层与层之间的 transform 用 SQL/DAG 调度（Airflow 类）编排**。
 *   - **为什么"直接把数据丢 HDFS"会变沼泽**：无 schema 契约（上游改列下游炸）、无元数据（没人知道这张表是谁的）、无小文件治理（千万个小文件拖垮文件系统）。**治理先行：表注册、责任人、格式规范、生命周期**（46-08 细讲）。
 *   - **湖仓一体的技术三角**：**表格式**（Iceberg/Hudi/Delta：给"一堆文件"加事务与版本）、**引擎**（Spark/Trino/Flink 统一读写）、**目录/元数据**（Hive Metastore 或 Glue 类）。**表格式是湖仓一体的核心创新**——它让"目录里的文件"拥有了数据库表的能力（46-05）。
 *   - **批流一体的数据侧形态**：同一份湖表既被离线批任务读（T+1 报表）、也被流任务写（CDC 持续写入，43-09）——**存储统一后"Lambda 双跑"（43-01）在数据侧真正消失**。
 *   - **Java 工程师的位置**：数据平台的服务化层（46-10）——特征 API、指标 API、数据导出服务，多为 Java 实现；Flink 作业本身也是 Java/SQL。**数据平台不是"数据团队专属"，业务 Java 团队是主要生产者与消费者**。
 *   - 易错点：把数据湖当备份盘（丢进去不管，三个月后没人认识）；每张表都没有责任人（坏了没人修）；不分层全在 ADS 直接改（口径失控）；以为湖仓一体是买一个产品（核心是治理流程 + 表格式选型）。
 * ============================================================
 */

package com.heima.ch46;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WarehouseToLake {
    record Zone(String name, String schema, String format, String owner, String sla) {
    }

    static void zones() {
        System.out.println("【订单主题的四层分区】");
        List<Zone> zones = List.of(
                new Zone("ODS", "原样（schema-on-read）", "JSON/Parquet 混合", "同步管道组", "与源库延迟<10min"),
                new Zone("DWD", "写入前定义（on-write）", "Parquet", "数据组-小李", "每日 T+1 或小时级"),
                new Zone("DWS", "写入前定义", "Parquet", "数据组-小李", "汇总口径审计"),
                new Zone("ADS", "写入前定义", "Parquet/接口", "报表组", "大屏与导出"));
        for (Zone z : zones) {
            System.out.printf("  %-4s schema=%-22s 格式=%-18s 责任人=%-10s SLA=%s%n",
                    z.name(), z.schema(), z.format(), z.owner(), z.sla());
        }
        System.out.println("  ODS 用 on-read 保真，往上层逐级 on-write 收紧质量");
        System.out.println();
    }

    static void swampSymptoms() {
        System.out.println("【数据沼泽的四个症状与处方】");
        record Symptom(String sign, String cause, String cure) {
        }
        List<Symptom> symptoms = List.of(
                new Symptom("无人认领的表 3000 张", "建表无注册", "目录 + 责任人强制"),
                new Symptom("上游加列下游全红", "无 schema 契约", "契约测试进 CI（46-08）"),
                new Symptom("查询越来越慢", "小文件百万级", "compaction 排期（46-06）"),
                new Symptom("两个口径打架", "同一指标多处实现", "指标平台统一（46-10）"));
        for (Symptom s : symptoms) {
            System.out.printf("  %-18s 原因：%-12s 处方：%s%n", s.sign(), s.cause(), s.cure());
        }
        System.out.println();
    }

    static void lakehouseTriangle() {
        System.out.println("【湖仓一体的技术三角】");
        Map<String, String> triangle = new LinkedHashMap<>();
        triangle.put("表格式", "Iceberg/Hudi/Delta：文件之上加事务、版本、演进");
        triangle.put("计算引擎", "Spark（批）/Trino（即席）/Flink（流）读同一张表");
        triangle.put("元数据目录", "Hive Metastore/Glue：表在哪、schema 是什么");
        for (Map.Entry<String, String> e : triangle.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  判断成熟度：有没有“多引擎写同一张表不出乱子”的验证");
        System.out.println();
    }

    static void batchStreamMerge() {
        System.out.println("【数据侧的批流一体：Lambda 双跑消失】");
        List<String> rows = List.of(
                "过去：离线表（Hive T+1）+ 实时链路（自建）双份代码双份数据",
                "现在：同一张 Iceberg 表，Flink CDC 持续写入 + Spark 离线读",
                "收益：口径单一（43-09 的 diff 监控失去必要性）、修复可回放（46-05 时间旅行）",
                "代价：表格式运维（compaction/快照清理）成为新工作");
        for (String r : rows) {
            System.out.println("  " + r);
        }
    }

    public static void main(String[] args) {
        zones();
        swampSymptoms();
        lakehouseTriangle();
        batchStreamMerge();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的业务画四层分区图（ODS/DWD/DWS/ADS），标注每层的 schema 策略、格式与责任人，检查是否存在沼泽症状。
 * 2. 列出你团队"两张表口径打架"的实例（或虚构），设计统一的指标定义文档并指出放置位置（46-10 指标平台）。
 * 3. 思考题：为什么说"表格式是湖仓一体的核心创新"？对比"目录里的文件"与"数据库表"在事务与版本上的能力差距。
 * ============================================================
 */
