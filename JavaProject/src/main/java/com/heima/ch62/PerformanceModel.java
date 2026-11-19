/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-09
 * 课名：性能模型：顺序写、零拷贝、批量
 * ------------------------------------------------------------
 * 知识点：
 *   - **Kafka 高吞吐的五个来源**：① **顺序追加写**（磁盘顺序写接近内存速度，随机写慢几个数量级）；② **页缓存（Page Cache）**（写入先进 OS 页缓存，由内核刷盘，避免 JVM 堆 GC 压力）；③ **零拷贝（sendfile）**（读消息时数据从页缓存直接到网卡，跳过用户态拷贝——60-07 的零拷贝在 Kafka 的应用）；④ **批量 + 压缩**（生产者凑批、Broker 整批存储、消费者整批拉取）；⑤ **分区并行**（多分区多 Broker 并行）。
 *   - **"利用页缓存而非 JVM 堆"是关键决策**：消息不进 JVM 堆（避免 GC 与对象开销），由 OS 管理缓存——**代价是"重启后首次读会走磁盘"**（缓存冷）；**Kafka 不应与其他重 IO 进程混部**（页缓存被抢）。
 *   - **批量的三个参数**：`batch.size`（批次字节）、`linger.ms`（等待凑批时间）、`compression.type`（压缩算法）——**吞吐与延迟的权衡**（48-03 的批处理同构）；**压缩在生产者做、Broker 原样存、消费者解压**（节省网络与磁盘）。
 *   - **消费者吞吐**：`fetch.min.bytes`/`fetch.max.wait.ms`（攒够再返回）、`max.poll.records`（每次 poll 条数）——**小批量高频 poll 增加往返**，大批量增加处理延迟。
 *   - **磁盘与网络是最终瓶颈**：单 Broker 吞吐经验值（顺序写 100~200MB/s 每盘）；**副本复制占用网络**（`replication.factor` 翻倍网络流量）——**扩容靠加 Broker 与分区**（62-02）。
 *   - 易错点：linger=0（不凑批，吞吐低）；压缩算法选 gzip（CPU 高，推荐 lz4/zstd）；把 Kafka 与 ES/数据库混部（页缓存争抢）；单分区追求高吞吐（分区才并行）。
 * ============================================================
 */

package com.heima.ch62;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class PerformanceModel {
    /** 顺序写 vs 随机写的量级对比（经验值） */
    static void diskIo() {
        System.out.println("【磁盘 IO 的量级差异】");
        String[][] rows = {
                {"机械盘顺序写", "100~200 MB/s", "追加日志"},
                {"机械盘随机写", "0.1~1 MB/s（约 100 IOPS）", "B 树随机更新"},
                {"SSD 顺序写", "500~3000 MB/s", "日志/大文件"},
                {"SSD 随机写", "50~500 MB/s", "数据库"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-26s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  顺序写比随机写快 2~3 个数量级 -> 这是「日志式」存储的根基");
        System.out.println();
    }

    /** 零拷贝：传统 4 次拷贝 vs sendfile 的 2 次 */
    static void zeroCopy() {
        System.out.println("【读消息的两条路径】");
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("传统 read+write", "磁盘->页缓存->用户态缓冲->socket 缓冲->网卡（4 次拷贝 + 2 次上下文切换）");
        paths.put("sendfile 零拷贝", "磁盘->页缓存->网卡（2 次拷贝，不经用户态）");
        paths.put("实际效果", "大消息批量消费时 CPU 占用与延迟显著下降");
        paths.forEach((k, v) -> System.out.printf("  %-14s %s%n", k, v));
        System.out.println();
    }

    /** 批量参数对吞吐与延迟的影响（简化模型） */
    static void batching(int batchSize, int lingerMs, int messages, int messageBytes) {
        int batches = (int) Math.ceil((double) messages / batchSize);
        double networkBytes = (double) messages * messageBytes;
        double latencyMs = lingerMs + batches * 0.5;          // 简化：每批一次往返
        System.out.printf("  batch=%d linger=%dms -> 批次数=%d 传输=%.1fMB 估算延迟=%.1fms%n",
                batchSize, lingerMs, batches, networkBytes / 1024 / 1024, latencyMs);
    }

    public static void main(String[] args) {
        diskIo();
        zeroCopy();
        System.out.println("【批量参数权衡（10 万条 × 512B）】");
        batching(1, 0, 100_000, 512);
        batching(1000, 5, 100_000, 512);
        batching(5000, 20, 100_000, 512);
        System.out.println("  结论：批量越大吞吐越高、延迟越高；按业务 SLA 选 linger");
        System.out.println();
        System.out.println("  五要素：顺序写 + 页缓存 + 零拷贝 + 批量压缩 + 分区并行");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用压测（或模拟参数）对比 `linger.ms=0/5/20` 与 `batch.size` 三档组合的吞吐与 P99 延迟，画出权衡曲线。
 * 2. 计算"3 副本 + 1 倍网络复制"下写入 100MB/s 时 Broker 的实际出口带宽需求，判断集群网络是否是瓶颈。
 * 3. 思考题：为什么 Kafka 把消息放页缓存而不是 JVM 堆？从 GC 压力、对象开销、重启恢复三个角度分析。
 * ============================================================
 */
