/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：46-06
 * 课名：批流一体存储
 * ------------------------------------------------------------
 * 知识点：
 *   - **批流写同一张湖表是湖仓一体的试金石**：Flink CDC 持续写入（秒级一批小文件）+ Spark 每日批读——**写入频繁产生小文件**，读取要求文件足够大——矛盾由 **compaction（合并）机制**解决。
 *   - **两种读模式**：**copy-on-write（COW）**——写时重写受影响文件，读永远干净（读快写贵）；**merge-on-read（MOR）**——写入追加 delta 文件，读时合并 base+delta（写快读贵）。**选型看读写比例**：读多写少选 COW，写多读少或延迟敏感选 MOR。
 *   - **小文件的治理节奏**：流写入每分钟一个小文件 × 1440 分钟 = 每天 8.6 万文件/分区——**异步 compaction**（每 10 分钟合并一次 delta，每天把整分区重整为目标大小）是流式湖表的标配运维。
 *   - **快照与 compaction 的关系**：compaction 本身也是一次提交（产生新快照），**读端不受影响**（快照一致性保证读到的始终是完整状态）；合并后旧文件进"待回收"（快照过期后物理删除）。
 *   - **Flink 写湖的一致性**：checkpoint（43-05）成功后才提交 Iceberg 快照——**流作业的 exactly-once 与湖表事务对齐**：checkpoint 间隔 = 数据可见延迟。
 *   - **下游感知**：46-05 的增量消费让下游订阅"compaction 后的新快照"，或者直接消费数据变更（append 流）。**消费模式选择**决定下游时效（分钟级 vs 天级）。
 *   - 易错点：流写入不做 compaction（小文件雪崩，读取越来越慢）；compaction 与写入抢资源（错峰排期）；MOR 的读放大没预估（查询变慢 3~5 倍才发现）；checkpoint 间隔设太长（下游数据可见延迟跟随变长）。
 * ============================================================
 */

package com.heima.ch46;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class LakeStreaming {
    record File(String name, String kind, long sizeMb) {
    }

    static void cowVsMor() {
        System.out.println("【COW vs MOR：读写代价互换】");
        record Write(long baseFiles, long deltaFiles, String mode) {
        }
        List<String> rows = List.of(
                "COW：更新 5% 的行 -> 重写受影响 base 文件（如 30 个 300MB） -> 写贵，读零合并",
                "MOR：更新 5% 的行 -> 只追加 2 个 delta 文件 -> 写便宜，读时合并 base+delta",
                "选型：报表读多写少 -> COW；实时写入延迟敏感 -> MOR + 异步 compaction");
        for (String r : rows) {
            System.out.println("  " + r);
        }
        System.out.println();
    }

    static void compactionSchedule() {
        System.out.println("【流写入的小文件治理：两级合并】");
        List<File> files = new ArrayList<>();
        for (int i = 0; i < 1440; i++) {
            files.add(new File("part-" + i, "delta", 12));
        }
        System.out.printf("  流写入一天：%d 个 delta 文件（每个 %dMB）%n", files.size(), 12);
        long deltaTotal = 1440L * 12;
        System.out.printf("  每 10 分钟异步合并 delta -> 144 个中型文件；每日全量重整 -> 目标 %dMB×N 个%n", 300);
        System.out.printf("  合并后存储不变（%dMB 级），文件数从 %d 降到数十个%n", deltaTotal, files.size());
        System.out.println("  排期原则：compaction 错开业务高峰，限速执行（39-10 分批思想）");
        System.out.println();
    }

    static void checkpointAligned() {
        System.out.println("【Flink 写湖：checkpoint 与快照对齐】");
        List<String> flow = List.of(
                "1) Flink 处理 CDC 事件，数据缓存在算子状态（43-05）",
                "2) checkpoint barrier 到达 -> 状态快照成功",
                "3) 成功后才向 Iceberg 提交本轮数据（产生新快照）",
                "4) checkpoint 失败 -> 本轮不提交，恢复后重放（幂等：按文件名去重）",
                "5) 数据可见延迟 ≈ checkpoint 间隔（43-06 的端到端一致性落地）");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println("  实践：大屏要 1 分钟时效 -> checkpoint 60s；报表 T+1 -> 5 分钟即可");
        System.out.println();
    }

    static void downstreamModes() {
        System.out.println("【下游消费模式选择】");
        String[][] rows = {
                {"订阅数据增量", "分钟级时效", "准实时指标、特征回填"},
                {"订阅 compaction 后快照", "小时级", "对账与报表（读性能优先）"},
                {"每日批读最新快照", "T+1", "传统报表（兼容旧调度）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-22s %-10s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  同一张表三种消费方式并存 —— 存储统一后各取所需");
    }

    public static void main(String[] args) {
        cowVsMor();
        compactionSchedule();
        checkpointAligned();
        downstreamModes();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 模拟 MOR 读放大：base 10 个文件 + delta 200 个文件的分区，统计一次全表查询的合并成本，对比 compaction 前后。
 * 2. 实现流写入 + 两级 compaction 的调度模拟，验证文件数曲线（写入上升/合并回落）与存储总量稳定。
 * 3. 思考题：为什么"checkpoint 成功才提交快照"能对齐流作业与湖表的事务？从 43-06 幂等与 46-05 乐观并发两个机制推导端到端 exactly-once。
 * ============================================================
 */
