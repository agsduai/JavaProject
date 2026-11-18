/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：41-09
 * 课名：数据库变更与发布协同
 * ------------------------------------------------------------
 * 知识点：
 *   - **发布事故的最大来源不是代码，是代码与数据库变更的时序错配**：代码新版本需要新列，DDL 还没跑 -> 启动即报错；DDL 先跑删了旧列 -> 旧版本还在读 -> 崩。**协同的核心是"每一步状态下，正在运行的版本集合都能正常工作"**。
 *   - **schema 变更必须走迁移工具与版本化脚本**（Flyway/Liquibase）：`V17__add_refund_status.sql` 进代码库，流水线在部署前自动执行，执行记录入库可审计。**禁止手工在库上改结构**（41-05 的漂移话题在数据库上的最严重形态）。
 *   - **兼容性迁移的扩容三步法（expand-contract）**：① expand：加列/加表，新旧代码都兼容；② 部署新版本双读写；③ contract：确认旧版本下线后，删除旧列/旧表。**每一步独立发布，全程新旧可共存**。破坏性操作（DROP/改类型）只出现在 contract 步。
 *   - **反向兼容（rollback-safe）检验**：每个迁移脚本要回答"如果发布失败回滚代码，旧代码在这个 schema 上能不能跑？"——加列可空可以，改列类型不行。**不可反向兼容的变更必须在发布计划里显式声明并准备数据回滚方案**。
 *   - **大表的 DDL 是高危操作**：MySQL 8.0 的 Online DDL 大多 INSTANT/INPLACE，但改列类型、加全文本地索引仍可能长时间锁；DDL 工具（gh-ost/pt-online-schema-change）用影子表+触发器/binlog 同步。**DDL 执行前估算行数与锁表时长，避开高峰，主从延迟告警盯紧**。
 *   - **数据回填（backfill）与 DDL 分离**：加列（秒级）与回填千万行（小时级）分开跑，回填用分批脚本 + 限速，避免一个 UPDATE 锁全表拖垮主从。
 *   - 易错点：迁移脚本没有 down（回滚）路径；expand 和 contract 合并成一次发布（新旧版本无法共存）；DDL 在高峰期执行拖垮主从；回填脚本不分批（undo log 爆炸 + 复制延迟）。
 * ============================================================
 */

package com.heima.ch41;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class DbMigration {
    record Migration(String version, String sql, String kind, String rollbackSafe) {
    }

    static final List<Migration> PLAN = List.of(
            new Migration("V17", "ALTER TABLE orders ADD COLUMN refund_status VARCHAR(16) NULL", "expand",
                    "回滚安全：新列可空，旧代码不读"),
            new Migration("V18", "回填脚本：UPDATE orders SET refund_status='NONE' 批量分批", "backfill",
                    "与 DDL 分离，限速执行"),
            new Migration("V19", "部署新版本（读+写 refund_status）", "deploy", "双版本共存期开始"),
            new Migration("V20", "ALTER TABLE orders DROP COLUMN old_flag", "contract",
                    "不可回滚！必须确认旧版本全部下线"));

    static void expandContract() {
        System.out.println("【expand-contract 三步法：每一步新旧共存都安全】");
        for (Migration m : PLAN) {
            System.out.printf("  %-4s %-6s %-52s %s%n",
                    m.version(), m.kind(), m.sql(), m.rollbackSafe());
        }
        System.out.println("  contract 步骤是单向门：执行前必须确认旧版本实例数为 0");
        System.out.println();
    }

    static void versionTimeline() {
        System.out.println("【时序推演：任何时刻任何版本都能跑】");
        record Phase(String when, String schema, String versions, String verdict) {
        }
        List<Phase> phases = List.of(
                new Phase("T1 expand 后", "+refund_status(NULL)", "v1, v2 均未部署", "v1 正常：不认识新列也无所谓"),
                new Phase("T2 v2 上线", "+refund_status", "v1 与 v2 共存", "v1 读写忽略新列；v2 正常读写"),
                new Phase("T3 v1 下线", "+refund_status(有值)", "仅 v2", "双读写稳定"),
                new Phase("T4 contract", "-old_flag", "仅 v2", "删除列，v2 不依赖旧列"));
        for (Phase p : phases) {
            System.out.printf("  %-14s schema=%-22s 版本=%-12s %s%n",
                    p.when(), p.schema(), p.versions(), p.verdict());
        }
        System.out.println();
    }

    static void ddlRisk() {
        System.out.println("【大表 DDL 的风险分级】");
        String[][] rows = {
                {"加可空列", "MySQL8 INSTANT 秒级", "低风险，流水线直接跑"},
                {"加索引", "INPLACE，不锁写但耗 IO", "评估行数，避开高峰"},
                {"改列类型", "多数重建全表", "用 gh-ost/pt-osc，或拆两列过渡"},
                {"DROP 列", "INSTANT 但不可逆", "contract 步，人工确认"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-24s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  执行清单：估行数 -> 预估时长 -> 主从延迟告警就位 -> 错峰执行");
        System.out.println();
    }

    static void backfill() {
        System.out.println("【回填：分批限速，不与 DDL 混跑】");
        long total = 50_000_000, batch = 5_000;
        long batches = total / batch;
        System.out.printf("  5000 万行按每批 %d 行 -> %d 批，每批间隔 100ms 限速%n", batch, batches);
        System.out.println("  批间 sleep 让主从延迟收敛；脚本可断点续跑（记录最大已回填 id）");
        System.out.println("  完成 marker：回填进度表 100% 后才能进入依赖该列的部署");
    }

    public static void main(String[] args) {
        expandContract();
        versionTimeline();
        ddlRisk();
        backfill();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为一个真实需求（新增退款状态）写出完整迁移计划：V17~V20 四步 + 每步的回滚方案，标注"单向门"位置。
 * 2. 用 gh-ost（或模拟脚本）体验大表加索引的影子表流程，记录主从延迟变化与限速参数。
 * 3. 思考题：为什么"迁移脚本必须回答反向兼容问题"？举一个发布失败回滚后旧代码在新 schema 上崩溃的例子。
 * ============================================================
 */
