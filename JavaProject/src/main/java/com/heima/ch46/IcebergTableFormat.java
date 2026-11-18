/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：46-05
 * 课名：Iceberg 表格式
 * ------------------------------------------------------------
 * 知识点：
 *   - **表格式回答"怎么让一堆文件成为一张有事务的表"**：Iceberg 的答案是**元数据清单树**——snapshot（快照）指向 manifest list，manifest 记录数据文件清单与统计。**表状态 = 当前快照指针；提交 = 原子替换指针**——在无原子 rename 的对象存储上实现了事务（46-03 的解法落地）。
 *   - **ACID 能力**：多个写入方并发提交时通过**乐观并发 + 冲突检测**——两个写都基于同一快照，各自产出新快照，先提交者赢，后提交者若与已提交变更的文件冲突则重试。**"写冲突重试"与 45-06 的 txn 语义同源**。
 *   - **时间旅行（time travel）**：任意历史快照可查询——`SELECT ... VERSION AS OF snapshot-42`。**用途**：误操作回滚（指针回退即回滚，秒级）、审计重现（上周报表为什么是这个数）、实验复现。**快照保留策略**决定可回溯窗口与存储成本。
 *   - **隐藏分区（hidden partitioning）**：分区信息在元数据里（`days(ts)`），用户写 SQL 不需要知道分区列——**分区从用户负担变成引擎知识**，分区演进（天 -> 小时）不破坏旧数据。
 *   - **schema 演进**：加列/改列名/删列都不重写数据（旧文件 + 新 schema 元数据兼容映射）——**演进成本从"全表重写"降到"改元数据"**。
 *   - **增量消费**：快照间 diff 即变更流——Flink/Spark 结构化流直接订阅 Iceberg 增量（43-09 CDC 链路的湖端形态）。
 *   - 易错点：快照不过期（存储无限膨胀，保留 7 天 + 定期 expire）；并发写无冲突检测（两个引擎覆盖写同一文件）；把 Iceberg 当普通目录手工改文件（元数据与数据漂移，46-04 同款病）；小文件治理被忽略（写入频繁时 manifest 膨胀）。
 * ============================================================
 */

package com.heima.ch46;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class IcebergTableFormat {
    record DataFile(String path, long rows, String dateMin, String dateMax) {
    }

    record Snapshot(long id, String parentId, List<DataFile> files, String operation) {
    }

    /** 当前快照指针（原子替换的对象） */
    static long currentSnapshot = 0;
    static Map<Long, Snapshot> snapshots = new LinkedHashMap<>();

    static void commitFlow() {
        System.out.println("【一次提交：写文件 -> 原子替换快照指针】");
        Snapshot s0 = new Snapshot(0, null, List.of(), "init");
        snapshots.put(0L, s0);
        System.out.println("  当前快照 #0（空表）");
        List<DataFile> f1 = List.of(new DataFile("data/part-0001.parquet", 1_000_000,
                "2026-11-17", "2026-11-17"));
        Snapshot s1 = new Snapshot(1, "#0", f1, "append");
        snapshots.put(1L, s1);
        currentSnapshot = 1;
        System.out.println("  写 data/part-0001.parquet（幂等命名）-> 提交快照 #1 <- 原子替换指针");
        List<DataFile> f2 = new ArrayList<>(f1);
        f2.add(new DataFile("data/part-0002.parquet", 900_000, "2026-11-18", "2026-11-18"));
        snapshots.put(2L, new Snapshot(2, "#1", f2, "append"));
        currentSnapshot = 2;
        System.out.println("  第二次提交 -> 快照 #2（指针指向新清单，旧文件不重写）");
        System.out.println();
    }

    static void concurrentWrite() {
        System.out.println("【并发提交：乐观并发与冲突检测】");
        System.out.println("  写入方 A、B 都基于快照 #2：");
        System.out.println("  A 先提交 -> 快照 #3（当前指针 = 3）");
        System.out.println("  B 提交时发现自己基于的 #2 已不是当前快照：");
        boolean conflict = true;   // B 的文件与 A 的变更文件重叠
        System.out.println("    冲突检测：" + (conflict
                ? "文件重叠 -> B 重试：基于 #3 重算后提交 -> 快照 #4"
                : "文件不重叠 -> 直接提交（快照链线性化）"));
        System.out.println("  同一层语义：46-03 的“清单原子替换” + 乐观锁");
        System.out.println();
    }

    static void timeTravel() {
        System.out.println("【时间旅行：回滚与审计】");
        Snapshot s1 = snapshots.get(1L);
        System.out.printf("  VERSION AS OF #1 -> 只看 %d 个文件（11-17 的数据）%n", s1.files().size());
        System.out.println("  误操作回滚：把当前指针从 #4 回退到 #3（原子操作，秒级）");
        currentSnapshot = 3;
        System.out.println("  当前快照 = " + currentSnapshot + "（数据回到发布前状态）");
        System.out.println("  审计重现：“上周报表为什么是 860 万”-> 查当时快照即可复现");
        System.out.println("  保留策略：快照保留 7 天 + 每日一存（expire_snapshots 排期）");
        System.out.println();
    }

    static void evolution() {
        System.out.println("【隐藏分区与 schema 演进】");
        System.out.println("  分区：PARTITIONED BY days(ts) —— SQL 里 WHERE ts >= '...' 自动裁剪");
        System.out.println("  分区演进：天 -> 小时，旧数据保持天分区，新数据小时分区（元数据共存）");
        System.out.println("  schema：新增列 refund_status -> 只改元数据，旧文件缺列按 null 读取");
        System.out.println("  重命名：列改名通过映射完成，无需重写数据");
        System.out.println("  对比 Hive 分区：加列要重建表/重写数据 —— 演进成本数量级差异");
    }

    public static void main(String[] args) {
        commitFlow();
        concurrentWrite();
        timeTravel();
        evolution();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现迷你 Iceberg（快照指针 + 清单 + 乐观并发重试），模拟两个写入方并发提交，验证冲突检测与线性化。
 * 2. 用时间旅行修复一次"误删数据"：构造误操作快照，指针回退恢复，记录回滚耗时并与传统备份恢复对比。
 * 3. 思考题：为什么隐藏分区能把"分区演进"的代价从重写数据降到改元数据？从分区信息存储位置的变化推导。
 * ============================================================
 */
