/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-11
 * 课名：综合案例：订单系统从单表到分库分表
 * ------------------------------------------------------------
 * 知识点：
 *   - **完整演进路线回顾**：慢查询治理（39-02）→ 深分页优化（39-03）→ 读写分离（39-04）→ 缓存挡读 → 历史归档（39-10）→ 仍然撑不住 → 水平分库分表（39-05/06）→ 分布式 ID（39-07）→ 跨分片查询治理（39-08）→ 跨库一致性（39-09）。**每一步都有前置条件与回退方案**，不是一步跳到分库分表。
 *   - **最终架构**：4 库 × 8 表（32 分片），分片键 user_id（基因法嵌入 order_id），一套订单库承载 C 端主路径；商家维度走冗余索引表；后台统计走异构（binlog 同步到数仓）；历史订单按月归档到归档库。**每个查询维度都有明确归宿，没有广播查询**。
 *   - **ID 设计落地**：雪花 64 位改造——41 位时间戳 + 4 位库号 + 6 位表号基因（取 user_id 哈希低 10 位）+ 13 位序列，既全局唯一又能反解路由。**ID 即路由信息**，任何分片只要拿到 ID 就知道数据在哪。
 *   - **迁移方案（双写三阶段）**：① 存量迁移：按 id 范围分批搬历史数据到 32 分片，校验和比对；② 增量双写：新旧库同写（旧为准），对账任务持续比对差异；③ 切读：灰度按用户尾号切读流量到新库，观察一周全量切换，旧库降级为只读备份一个月后下线。**每阶段都有回退开关**。
 *   - **验收指标**：单用户订单查询 P99 < 50ms（单分片）；下单链路 P99 < 300ms（含跨库扣库存）；无广播查询（后台报表全部走数仓）；对账差异为零；主从延迟稳定 < 2s。
 *   - **回归测试清单**：所有高频 SQL 逐条确认路由（单分片/基因/索引表/异构）；深分页全部改游标；事务边界重新梳理（跨库的改本地消息表）；连接池重新评估（32 分片 × 应用实例数的乘积效应）。
 *   - 易错点：迁移期旧库新库时间戳不一致导致对账误报（用业务版本号不用时间）；灰度切读时忘记切写（读新写旧，数据分裂）；连接数爆炸（每应用实例连 32 库，应用实例 20 台就是 640 连接，必须用连接池上限 + Proxy 收敛）；迁移完成立即下线旧库（至少保留一个月只读）。
 * ============================================================
 */

package com.heima.ch39;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class OrderShardingCase {
    static final int DB_COUNT = 4, TABLE_COUNT = 8, TOTAL = DB_COUNT * TABLE_COUNT;
    static final long GENE_BITS = 10, SEQ_BITS = 12;

    /** 基因路由：user_id 哈希的低 10 位作为基因嵌入 order_id */
    static long geneOf(long userId) {
        return Math.abs(userId * 2654435761L) & 1023;
    }

    record OrderId(long value) {
        /** 41 位毫秒时间戳 | 10 位基因 | 12 位序列 = 63 位 */
        static OrderId of(long userId, long seq) {
            long ts = System.currentTimeMillis() & ((1L << 41) - 1);
            long s = seq & ((1L << SEQ_BITS) - 1);
            return new OrderId((ts << (GENE_BITS + SEQ_BITS)) | (geneOf(userId) << SEQ_BITS) | s);
        }

        long gene() {
            return (value >> SEQ_BITS) & 1023;
        }

        int db() {
            return (int) (gene() % DB_COUNT);
        }

        int table() {
            return (int) (gene() % TABLE_COUNT);
        }
    }

    record Order(OrderId id, long userId, double amount) {
    }

    static void routingMatrix() {
        System.out.println("【32 分片的最终路由矩阵】");
        long userId = 8123;
        long gene = geneOf(userId);
        int db = (int) (gene % DB_COUNT), table = (int) (gene % TABLE_COUNT);
        System.out.printf("  user_id=%d 的基因=%d -> 列表查询路由：库 %d 表 %d（单分片）%n",
                userId, gene, db, table);
        OrderId oid = OrderId.of(userId, 42);
        System.out.printf("  该用户的 orderId=%d -> 反解基因 %d -> 库 %d 表 %d（同一个分片）%n",
                oid.value(), oid.gene(), oid.db(), oid.table());
        System.out.println("  按 orderId 查详情与按 user_id 查列表命中同一分片，两个维度都免广播");
        System.out.println("  商家侧查询 -> 冗余索引表 t_order_by_shop（shop_id 分片）");
        System.out.println("  后台统计 -> binlog -> 数仓（绝不广播生产库）");
        System.out.println("  历史订单 -> 归档库（按月，路由按时间）");
        System.out.println();
    }

    static void migrationPhases() {
        System.out.println("【双写迁移三阶段与回退开关】");
        String[][] phases = {
                {"阶段1 存量搬迁", "按 id 范围分批搬到 32 分片，校验和比对", "失败：清空重来（旧库仍为准）"},
                {"阶段2 增量双写", "新写流量同时进新旧库（旧为准），对账任务跑差", "失败：关双写开关回到单库"},
                {"阶段3 切读灰度", "按用户尾号 1% -> 10% -> 100% 切读新库", "失败：读开关回退旧库，秒级"},
        };
        for (String[] p : phases) {
            System.out.printf("  %-12s %-38s 回退：%s%n", p[0], p[1], p[2]);
        }
        System.out.println("  铁律：任何阶段旧库保持只读前一律是唯一真源；切换后旧库只读保留 30 天");
        System.out.println();
    }

    static void sqlAudit() {
        System.out.println("【SQL 审计：每条查询必须有明确路由】");
        String[][] audits = {
                {"用户查自己的订单列表", "user_id 哈希 -> 单分片", "PASS"},
                {"订单详情页", "orderId 基因反解 -> 单分片", "PASS"},
                {"商家查今日订单", "索引表按 shop_id -> 单分片 + 回主表", "PASS"},
                {"运营按状态导出全量", "binlog 异构数仓", "PASS（禁广播）"},
                {"客服查三天前订单", "时间路由归档库", "PASS"},
        };
        for (String[] a : audits) {
            System.out.printf("  %-18s %-34s %s%n", a[0], a[1], a[2]);
        }
        System.out.println("  审计原则：出现“广播到 32 片”的日常 SQL，就是架构缺陷，不是优化项");
        System.out.println();
    }

    static void acceptance() {
        System.out.println("【验收指标与回归清单】");
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("单用户订单列表 P99", "< 50ms（单分片点查 + 游标分页）");
        metrics.put("下单链路 P99", "< 300ms（含跨库扣库存走本地消息表）");
        metrics.put("主从延迟", "稳定 < 2s，无周期性尖刺");
        metrics.put("对账差异", "连续 7 天为零");
        metrics.put("连接数", "应用实例 × 分片数在连接池预算内");
        for (Map.Entry<String, String> e : metrics.entrySet()) {
            System.out.printf("  %-16s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  回归：高频 SQL 全量过路由审计；深分页全改游标；跨库事务全部消息化");
    }

    public static void main(String[] args) {
        routingMatrix();
        migrationPhases();
        sqlAudit();
        acceptance();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的业务画出"从单表到分库分表"的完整演进图（含每一步的触发条件与回退方案），标注哪些步骤在你的场景可以跳过。
 * 2. 实现三阶段迁移模拟：内存双写 + 对账任务 + 灰度切读，故意制造一次对账差异并修复，记录全流程日志。
 * 3. 做一次连接数测算：应用 20 实例 × 32 分片 × 连接池 5，得出总连接数与数据库上限的对比，给出 Proxy 收敛方案。
 * 4. 思考题：为什么"ID 即路由"（基因法）是分库分表后最重要的架构决策之一？从故障排查、缓存键设计、跨系统对账三个场景说明它带来的便利。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 订单表从 100 万涨到 10 亿：先做索引与深分页优化，再做读写分离，最后按用户 id 分片。要解决分片路由、分布式 ID、跨分片查询与聚合、跨库事务、冷热分离。
 * 【任务】
 * 1. 用 EXPLAIN 分析一条慢查询，补索引或改写 SQL，把全表扫描变成索引扫描，给出前后耗时对比。
 * 2. 优化深分页：limit 1000000, 20 改成基于上次最大 id 的游标查询，说明为什么 offset 越大越慢。
 * 3. 实现分片路由：按 userId 取模分 4 片，写一个路由方法把 SQL 定位到具体分片，并说明为什么不用订单 id 做分片键。
 * 4. 实现雪花算法生成全局唯一 id，再用「先查用户维度分片 + 异步写宽表」解决按商家维度查询的问题，说明跨分片聚合的两种方案。
 * 【验收清单】
 * [ ] 慢查询优化后 type 从 ALL 变为 ref 或 range，扫描行数下降一个量级以上。
 * [ ] 游标分页结果与 offset 分页在相同数据下一致，但耗时基本恒定。
 * [ ] 分片路由在 4 个分片上数据分布均匀（偏差不超过 10%）。
 * [ ] 雪花 id 在多线程下无重复、趋势递增，能说明时钟回拨的处理方式。
 * ============================================================
 */
