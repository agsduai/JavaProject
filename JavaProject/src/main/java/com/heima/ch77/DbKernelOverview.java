/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-01
 * 课名：为什么 Java 工程师要懂数据库内核
 * ------------------------------------------------------------
 * 知识点：
 *   - **一次查询的完整旅程**：**解析（SQL -> AST）-> 优化（选索引/定 join 顺序）-> 执行（迭代器火山模型）-> 存储引擎（读页/写页）**——**Java 层看到的 `jdbcTemplate.query` 只是这条链的入口**。
 *   - **懂内核才能解释"为什么慢"**：**逻辑读 vs 物理读**（缓冲池命中率）、**扫描行数 vs 返回行数**（索引选择度）、**锁等待 vs 执行耗时**（并发瓶颈）——**没有这些指标，调优只能靠猜**。
 *   - **数据库的"三级存储"**：**缓冲池（内存）-> 磁盘页（16KB）-> 行**——**所有 IO 以页为单位**，**读一行也会加载整页**，这是"索引覆盖"能省 IO 的根本原因（77-02/77-09）。
 *   - **Java 工程师最常见的三个盲区**：**以为索引一定走**（函数/隐式转换会让索引失效 77-08）、**以为事务隔离靠应用层**（MVCC 在数据库内 77-04）、**以为分库分表后 SQL 还能随便写**（跨分片查询要归并 77-10）。
 *   - 与第 20/54 章的关系：20 章讲"怎么用 MySQL/JDBC"，本章讲"MySQL 内部怎么执行你的 SQL"——**从会用到底层原理**。
 *   - 易错点：只看接口耗时（不看 DB 内部指标）；用 `SELECT *`（破坏覆盖索引）；把"加索引"当万能药（写放大/优化器不选）；不区分"慢在 DB"还是"慢在应用"。
 * ============================================================
 */

package com.heima.ch77;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class DbKernelOverview {
    record Stage(String name, long logicalReads, long physicalReads, double costMs) {
    }

    /** 模拟缓冲池：pageId -> 是否已缓存 */
    static final Set<Long> bufferPool = new LinkedHashSet<>();
    static long logical;
    static long physical;

    static void readPage(long pageId) {
        logical++;
        if (bufferPool.contains(pageId)) {
            return;
        }
        physical++;
        bufferPool.add(pageId);
    }

    /** 模拟一次带索引的查询：3 个索引页 + 5 个回表页 */
    static Stage executeIndexQuery() {
        long beforeLogical = logical;
        long beforePhysical = physical;
        for (long p = 100; p < 103; p++) {
            readPage(p);
        }
        for (long p = 500; p < 505; p++) {
            readPage(p);
        }
        long l = logical - beforeLogical;
        long ph = physical - beforePhysical;
        return new Stage("走二级索引（含回表）", l, ph, ph * 1.0 + l * 0.02);
    }

    /** 模拟全表扫描：20 个数据页 */
    static Stage executeFullScan() {
        long beforeLogical = logical;
        long beforePhysical = physical;
        for (long p = 500; p < 520; p++) {
            readPage(p);
        }
        long l = logical - beforeLogical;
        long ph = physical - beforePhysical;
        return new Stage("全表扫描", l, ph, ph * 1.0 + l * 0.02);
    }

    static void journey() {
        System.out.println("【一次查询的旅程】");
        String[][] steps = {
                {"1. 连接/协议", "JDBC 发 SQL，服务端解析", "词法/语法分析 -> AST"},
                {"2. 优化器", "选访问路径与 join 顺序", "基于成本（77-07）"},
                {"3. 执行器", "按计划调用存储引擎", "火山模型：next() 逐行"},
                {"4. 存储引擎", "读缓冲池/磁盘页", "16KB 页，LRU 淘汰（77-03）"},
                {"5. 返回结果", "结果集编码回传", "网络 + 反序列化"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-12s %-22s %s%n", s[0], s[1], s[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        journey();

        System.out.println("【第一次执行：缓冲池为空】");
        Stage first = executeIndexQuery();
        System.out.printf("  %s：逻辑读=%d 物理读=%d 预估耗时=%.2fms%n",
                first.name(), first.logicalReads(), first.physicalReads(), first.costMs());

        System.out.println();
        System.out.println("【第二次执行：页已在缓冲池】");
        Stage second = executeIndexQuery();
        System.out.printf("  %s：逻辑读=%d 物理读=%d 预估耗时=%.2fms%n",
                second.name(), second.logicalReads(), second.physicalReads(), second.costMs());

        System.out.println();
        System.out.println("【对比全表扫描（20 页）】");
        Stage scan = executeFullScan();
        System.out.printf("  %s：逻辑读=%d 物理读=%d 预估耗时=%.2fms%n",
                scan.name(), scan.logicalReads(), scan.physicalReads(), scan.costMs());

        System.out.println();
        System.out.println("【结论】");
        System.out.println("  1) 物理读才是真 IO，逻辑读是内存访问（快 50 倍）");
        System.out.println("  2) 命中率 = (逻辑读-物理读)/逻辑读；冷启动后才会稳定");
        System.out.println("  3) 扫描行数远大于返回行数 -> 索引选择度差（77-09）");
        System.out.println("  4) 调优先看指标：逻辑读/物理读/扫描行数/锁等待");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给一次真实查询统计"逻辑读/物理读/扫描行数/返回行数"，判断瓶颈在 IO 还是 CPU。
 * 2. 对比"走索引 + 回表"与"全表扫描"的页访问次数，说明何时全表扫描反而更快。
 * 3. 思考题：为什么"读一行也会加载整页"？从 16KB 页与局部性原理分析，并联系覆盖索引。
 * ============================================================
 */
