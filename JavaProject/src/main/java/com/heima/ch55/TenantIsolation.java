/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：55-02
 * 课名：租户隔离三方案
 * ------------------------------------------------------------
 * 知识点：
 *   - **共享表方案（默认）的工程要点**：**所有业务表首列 tenant_id + 联合索引以 tenant_id 打头**（`(tenant_id, ...)`，否则跨租户扫描）；唯一约束要含 tenant_id（不同租户允许同名）；所有查询强制带租户条件（55-03 的拦截器自动化，不能靠自觉）。
 *   - **独立库方案的管理成本**：连接池 × 租户数（39/51 章连接数爆炸问题的 SaaS 版——**连接池按租户懒加载 + 上限治理**）；schema 变更 × N（迁移脚本循环执行，进度可观测 46-09）；**跨租户聚合查询**（运营报表）需要单独的数据汇聚通道（CDC 到数仓 46 章）。
 *   - **独立部署方案的应用治理**：每租户独立发布流水线（41 章）或统一镜像分实例配置；**配置与代码版本漂移治理**（47-09 跨集群漂移的同款问题）；成本核算天然清晰（租户自付）。
 *   - **方案的迁移路径**：共享表 -> 独立库（大租户搬出）：按 tenant_id 迁移数据（39-11 双写三阶段复用）——**隔离方案不是终身绑定**，但迁移要趁早规划。
 *   - **数据层选型矩阵**：共享表适合 MySQL 单体或分片（tenant_id 进分片键考量）；独立库适合每个库再分片（大租户 39 章方案）。
 *   - 易错点：索引不带 tenant_id（查询退化全表）；唯一约束漏租户（租户 A 阻止租户 B 建同名）；共享表的 tenant_id 可空（空=跨租户数据，必须 NOT NULL）；独立库连接池无上限（租户数增长拖垮应用）。
 * ============================================================
 */

package com.heima.ch55;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TenantIsolation {
    /** 共享表：租户条件强制注入（拦截器思想） */
    record Query(String table, String tenantId, Map<String, String> conditions) {
    }

    static String buildSql(Query q) {
        if (q.tenantId() == null || q.tenantId().isBlank()) {
            throw new IllegalStateException("拒绝无租户条件的查询（防跨租户泄露）");
        }
        StringBuilder sb = new StringBuilder("SELECT * FROM ").append(q.table())
                .append(" WHERE tenant_id = '").append(q.tenantId()).append("'");
        q.conditions().forEach((k, v) ->
                sb.append(" AND ").append(k).append(" = '").append(v).append("'"));
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("  " + buildSql(new Query("orders", "t-100",
                Map.of("status", "PAID"))));
        try {
            buildSql(new Query("orders", "", Map.of()));
        } catch (IllegalStateException e) {
            System.out.println("  无租户查询被拦截：" + e.getMessage());
        }
        System.out.println();
        System.out.println("  索引设计：INDEX (tenant_id, status) —— 租户列必须打头（39-02）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三方案的内存模拟：共享表（带租户查询构建）、独立库（按租户路由 Map）、独立部署（配置模板），各演示一个查询。
 * 2. 设计独立库方案的连接池治理（懒加载 + 上限 + LRU 淘汰），模拟 100 租户场景的资源曲线。
 * 3. 思考题：为什么共享表的唯一约束必须含 tenant_id？构造两个租户同名数据的冲突用例说明。
 * ============================================================
 */
