/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：48-04
 * 课名：PagedAttention 与显存管理
 * ------------------------------------------------------------
 * 知识点：
 *   - **传统 KV 管理的浪费**：每个请求预分配“最大长度”的连续 KV 空间（如按 2048 预留）——**实际输出长短不一，平均浪费 60%~80%**；连续内存还有外部碎片。vLLM 的 PagedAttention 把操作系统分页思想搬进 KV 管理。
 *   - **分页机制**：KV 空间切成固定大小的 block（如 16 token/块），按需分配、逻辑连续物理分散——**显存利用率从 ~40% 提到 90%+**，同卡并发数翻倍以上。
 *   - **Copy-on-Write 与前缀共享的配合**：多个请求共享相同前缀的 block（只读），各自 fork 出私有 block——**48-02 的前缀共享在 block 层面的实现**；beam search 等分支解码也由此变便宜。
 *   - **显存压力的运行时表现**：block 池耗尽 -> 新请求排队/抢占（低优先级请求的 KV 被换出重算）——**抢占是延迟尖刺的来源之一**，监控 block 池水位（42 章饱和度信号）。
 *   - **与量化的联动**：KV 也可量化（FP8），block 粒度下按需应用——**48-02 的省显存技术与 48-04 的管理技术叠加使用**。
 *   - **Java 侧的关联**：网关看到的“引擎容量”= block 池水位决定的实际并发——**把 block 水位暴露成网关路由依据**（48-08 多副本路由的权重输入）。
 *   - 易错点：显存“看起来还多”却并发上不去（block 碎片/预留策略）；抢占频繁（长请求被换出重算，延迟翻倍）；block 大小配置不当（太小管理开销大，太大浪费尾块）；忽略 max_model_len 与 KV 池的比例关系。
 * ============================================================
 */

package com.heima.ch48;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class PagedAttention {
    static void wasteVsPaged() {
        System.out.println("【预留式 vs 分页式：显存利用率对比】");
        int maxLen = 2048, blockTokens = 16;
        int[] actual = {300, 1500, 60, 900};
        int reserved = actual.length * maxLen;
        int paged = 0;
        for (int len : actual) {
            paged += (int) Math.ceil((double) len / blockTokens) * blockTokens;
        }
        System.out.printf("  预留式：%d 请求 × %d = %d token 槽位%n", actual.length, maxLen, reserved);
        System.out.printf("  分页式：实际占用 %d token 槽位（利用率 %.0f%% vs %.0f%%）%n",
                paged, paged * 100.0 / reserved, 100.0);
        System.out.println("  同卡并发能力因此提升 2~3 倍 —— vLLM 吞吐优势的物理来源");
        System.out.println();
    }

    static void blockLifecycle() {
        System.out.println("【block 生命周期：分配/共享/回收】");
        List<String> flow = List.of(
                "1) 请求到达：prefill 后按实际长度分 block（16 token/块）",
                "2) 多请求共享 system prompt block（引用计数，只读）",
                "3) decode 每生成 16 token 追加一个新 block（按需）",
                "4) 请求完成 -> 私有 block 回收；共享 block 引用-1，归零才回收",
                "5) 池水位低 -> 抢占策略：换出低优先级请求的 KV（可重算恢复）");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void preemptionSignal() {
        System.out.println("【抢占：延迟尖刺的隐形来源】");
        record Event(String clock, String what, String impact) {
        }
        List<Event> events = List.of(
                new Event("10:00:05", "block 池 92%（水位告警阈值 85%）", "新请求开始排队"),
                new Event("10:00:06", "抢占 1 个低优先级长请求", "该请求延迟翻倍（重算）"),
                new Event("10:00:08", "池回落到 70%", "恢复正常"));
        for (Event e : events) {
            System.out.printf("  %s  %-32s %s%n", e.clock(), e.what(), e.impact());
        }
        System.out.println("  监控：block 水位、抢占次数（42 章饱和度 -> 事故前兆）");
        System.out.println("  处置：降低批上限 / 缩短 max_model_len / 扩容副本");
        System.out.println();
    }

    static void tuning() {
        System.out.println("【block 相关参数的调优】");
        String[][] rows = {
                {"block 大小", "16 token 常规；小=碎片少管理开销大", "默认值起步，压测微调"},
                {"gpu_memory_utilization", "0.85~0.92（留激活与碎片余量）", "过高触发 OOM，过低浪费"},
                {"max_model_len", "按业务真实需要设（不要默认最大）", "直接决定单请求 KV 上限"},
                {"swap/重算策略", "优先重算（decode 便宜）或换出（显存紧）", "按显存带宽与容量权衡"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-22s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  原则：参数组合靠压测确定，一次只动一个");
    }

    public static void main(String[] args) {
        wasteVsPaged();
        blockLifecycle();
        preemptionSignal();
        tuning();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现一个简化 block 管理器（分页分配/引用计数共享/回收），对比预留式与分页式在不同长度分布下的利用率。
 * 2. 构造显存压力场景（并发逼近池上限），观察抢占事件与受影响请求的延迟，记录水位与尖刺的对应关系。
 * 3. 思考题：为什么“重算被抢占请求”通常优于“换出到 CPU 内存”？从 decode 的计算成本与 PCIe 带宽的成本对比分析。
 * ============================================================
 */
