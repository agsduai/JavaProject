/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-01
 * 课名：数据量大了会发生什么
 * ------------------------------------------------------------
 * 知识点：
 *   - **单表性能拐点是经验值不是铁律**：千万级通常还能撑（看索引与查询模式），五千万到一亿级开始明显劣化。决定拐点的不是行数本身，而是 **B+ 树的深度**：主键索引树高从 3 层变 4 层，每次查找多一次磁盘/页读取，所有查询同时变慢。
 *   - **量变引起质变的四个信号**：① 通过索引查单行仍快，但**无索引条件查询**从毫秒变秒；② 缓冲池（innodb_buffer_pool）命中率下降，数据页换进换出；③ 备份/DDL 时间从分钟变小时（`ALTER TABLE` 锁表风险飙升）；④ 主从延迟开始周期性飙高。
 *   - **先优化索引与查询，再考虑拆**：绝大多数"数据量大了很慢"的真实原因是一个没走索引的 SQL。**分库分表是最后手段**，做之前必须先做：索引优化、SQL 治理、缓存挡读、历史归档。直接上分库分表是把架构复杂度当成特效药。
 *   - **数据量的另一个代价是"写"**：B+ 树页分裂、redo/undo 膨胀、长事务持锁更久。读可以通过缓存与从库扛，**写瓶颈才是分库的真实动因**。
 *   - **容量规划要按"三年后"算**：日增 50 万行，一年 1.8 亿行。规划分片数不是按今天的量，而是按三年后的量 ÷ 单分片合理上限（经验值 1000~2000 万行/表），一次规划到位，避免二次扩容。
 *   - **判断该走哪条路**：读慢 → 缓存 + 读写分离 + 归档；写慢 → 批量化 + 削峰 + 分库；单表大但增长停止 → 归档即可；持续增长 → 分库分表。
 *   - 易错点：用行数判断要不要拆（要看 QPS、索引命中、增长曲线）；把分库分表当成"性能优化第一步"；忽略归档——很多系统 80% 的数据是两年前的冷数据，归档完单表直接回到安全区。
 * ============================================================
 */

package com.heima.ch39;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class BigTableProblems {
    /** 简化的 B+ 树深度估算：单页存 M 个键，树高 = ceil(logM(行数)) */
    static int btreeHeight(long rows, int keysPerPage) {
        long nodes = 1;
        int height = 1;
        while (nodes * (long) keysPerPage < rows) {
            nodes *= keysPerPage;
            height++;
        }
        return height;
    }

    static void btreeDepth() {
        System.out.println("【B+ 树深度：为什么行数增加让所有查询都变慢】");
        long[][] cases = {{1_000_000L, 3}, {20_000_000L, 3}, {200_000_000L, 3}};
        for (long[] c : cases) {
            long rows = c[0];
            int h = btreeHeight(rows, (int) c[1]);
            System.out.printf("  单表 %2d,000,000 行 -> 主键树高 %d 层（每次定位多一跳）%n",
                    rows / 1_000_000, h);
        }
        System.out.println("  树高 3->4：全表所有点查的 IO 基础成本同步上升 33%");
        System.out.println();
    }

    static void fourSignals() {
        System.out.println("【数据量撑不住的四个信号】");
        String[][] signals = {
                {"无索引查询变慢", "EXISTS 子查询/模糊条件 从 50ms -> 5s"},
                {"缓冲池命中率下降", "buffer pool hit 99% -> 85%，磁盘 IO 翻倍"},
                {"DDL 时间失控", "加一列从 2s -> 2h，期间锁表风险"},
                {"主从延迟周期性飙高", "大查询拖住 SQL 线程，Seconds_Behind_Master 抖动"},
        };
        for (String[] s : signals) {
            System.out.printf("  - %-18s %s%n", s[0], s[1]);
        }
        System.out.println();
    }

    static void decisionTree() {
        System.out.println("【拆不拆：先走便宜的路】");
        List<String> steps = List.of(
                "1) 慢查询治理：EXPLAIN 逐条过，补索引、改写法（通常解决 80% 的慢）",
                "2) 缓存挡读：热点读走 Redis，数据库只承担写与冷读",
                "3) 读写分离：报表与后台查询下放到从库",
                "4) 历史归档：两年前数据进归档库/表，主表回到安全区",
                "5) 以上都做完了写瓶颈仍在 -> 才进入分库分表设计");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void capacityPlan() {
        System.out.println("【容量规划：按三年后的量算分片数】");
        long dailyGrowth = 500_000;
        long threeYears = dailyGrowth * 365 * 3;
        long perTableLimit = 20_000_000;
        int shards = (int) Math.ceil((double) threeYears / perTableLimit);
        System.out.printf("  日增 %d 行 -> 三年 %d 行%n", dailyGrowth, threeYears);
        System.out.printf("  单表上限 %d 行 -> 至少 %d 个分片，取 2 的幂 -> 8 分片%n",
                perTableLimit, shards);
        System.out.println("  分片数选 2 的幂（8/16/32）：扩容翻倍时路由迁移最规整");
        System.out.println("  反例：按当前量 2000 万就 2 分片，一年后被迫二次扩容");
    }

    public static void main(String[] args) {
        btreeDepth();
        fourSignals();
        decisionTree();
        capacityPlan();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用示例的 btreeHeight 公式估算你项目主表三年后的树高，写出"何时必须做归档/分表"的量化判断。
 * 2. 给你的数据库跑一次慢查询审计（slow log 或手动 EXPLAIN 全量接口），列出前 5 条慢 SQL 与修复方案。
 * 3. 思考题：为什么说"写瓶颈才是分库的真实动因，读慢一般不用分库"？读慢的三板斧分别解决哪类问题？
 * ============================================================
 */
