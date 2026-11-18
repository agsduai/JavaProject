/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-09
 * 课名：Quorum 读写与仲裁
 * ------------------------------------------------------------
 * 知识点：
 *   - **Quorum（仲裁）是不走共识协议时保证一致性的简化机制**：N 副本，写入需 W 个确认，读取需 R 个确认，**W + R > N 保证读写集合必有交集**——读到至少一个最新副本。Dynamo/Cassandra 族的核心参数。
 *   - **参数的物理含义**：N=3, W=2, R=2（标准配置）——容忍 1 副本故障仍可读写，读写各付一次多数派代价；W=1, R=3（写快读慢，有脏读窗口）；W=3, R=1（读快写慢）。**调节 W/R = 调节延迟与一致性曲线**（45-02 PACELC 的直接应用）。
 *   - **Quorum 不等于线性一致**：W+R>N 只保证"读到至少一份最新数据"，**如果读到的恰好是旧副本**（交集里有最新也有旧），仍可能返回旧值——Dynamo 用向量时钟（45-07）+ 读修复解决：全部副本读回、比较版本、取最新并写回落后副本。
 *   - **读修复与反熵**：读时发现副本版本不一致 -> 立即修复（read repair）；后台周期性同步（anti-entropy/merkle tree）。**最终一致的收敛机制**——"会收敛"不是靠等待，是靠这两个主动机制。
 *   - ** sloppy quorum 与降级**：指定副本不可达时允许"附近的"副本代替（Dynamo 的 sloppy quorum）——提高分区时可用性，代价是一致性窗口扩大。**是否开启是业务决策**。
 *   - **与共识的关系**：共识（Raft）= quorum + leader + 日志顺序 + 安全约束，是"管理型的过半"；纯 quorum 系统无 leader、允许并发冲突（交给向量时钟）。**需要严格顺序选共识，容忍冲突合并选 quorum**。
 *   - 易错点：把 R+W>N 当线性一致（还要版本比较与修复）；只调大 N 不调 W/R（副本多但读写都是 1，一致性没变）；读修复缺失（最终一致永不收敛）；Sloppy quorum 默认开（一致性窗口悄悄变大）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class QuorumReplication {
    /** 副本：key -> 带版本号的值 */
    record Versioned(String value, long version) {
    }

    static Map<String, Versioned> r1 = new LinkedHashMap<>();
    static Map<String, Versioned> r2 = new LinkedHashMap<>();
    static Map<String, Versioned> r3 = new LinkedHashMap<>();
    static List<Map<String, Versioned>> replicas = List.of(r1, r2, r3);

    static void writeW(int w, String key, String value, long ver) {
        int acked = 0;
        for (Map<String, Versioned> r : replicas) {
            if (acked < w) {
                r.put(key, new Versioned(value, ver));
                acked++;
            }
        }
        System.out.printf("  写 %s=%s(v%d) W=%d -> %d 副本确认%n", key, value, ver, w, acked);
    }

    static void readWithRepair(int r) {
        System.out.printf("  读 %s（R=%d）-> 全量读 %d 个副本比较版本：%n", "x", r, replicas.size());
        Versioned newest = null;
        List<Integer> stale = new ArrayList<>();
        for (int i = 0; i < replicas.size(); i++) {
            Versioned v = replicas.get(i).get("x");
            if (v == null) {
                stale.add(i);
                continue;
            }
            if (newest == null || v.version() > newest.version()) {
                newest = v;
            }
        }
        System.out.printf("    最新值：%s(v%d)%n", newest.value(), newest.version());
        for (int i : stale) {
            replicas.get(i).put("x", newest);
            System.out.printf("    读修复：副本%d 版本落后 -> 回填 %s(v%d)%n", i, newest.value(), newest.version());
        }
        System.out.println("    返回给客户端：" + newest.value());
        System.out.println();
    }

    static void paramWalkthrough() {
        System.out.println("【W/R 参数的组合语义（N=3）】");
        String[][] rows = {
                {"W=2, R=2", "标准：容 1 故障，读写各付多数派代价", "大多数业务"},
                {"W=1, R=3", "写延迟最低，读全量对比", "写密集 + 可读修复"},
                {"W=3, R=1", "写最慢，读最快（可能脏读）", "读密集且容忍旧值"},
                {"W=1, R=1", "最快，故障时丢数据", "缓存类，非真实数据"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  铁律：W+R>N 且 W>N/2（写集合自己也过半，防止两个并发写都成功）");
        System.out.println();
    }

    static void notLinearizable() {
        System.out.println("【为什么 W+R>N 仍可能读到旧值】");
        System.out.println("  写 v2 到 {r1,r2}（W=2）成功，r3 还是 v1");
        System.out.println("  读时 R=2 读到 {r1(v2), r3(v1)}：交集成立但客户端拿到 r3 的 v1");
        System.out.println("  两个必要补充：");
        System.out.println("    1) 版本比较：全部副本读回取最高版本（本课 readWithRepair 的实现）");
        System.out.println("    2) 读修复：把落后副本回填，让收敛真实发生");
        System.out.println("  结论：quorum 是必要条件，一致性靠“比较+修复”补完");
    }

    public static void main(String[] args) {
        writeW(2, "x", "v2-value", 2);
        // 模拟 r3 未同步（W=2 只写前两个副本）
        r3.remove("x");
        r3.put("x", new Versioned("v1-value", 1));
        readWithRepair(2);
        paramWalkthrough();
        notLinearizable();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三副本 quorum 存储（W/R 参数化 + 版本比较 + 读修复），构造"读到旧副本"场景验证修复收敛。
 * 2. 用模拟器统计不同 W/R 组合下的读写延迟与旧值概率（注入随机副本延迟），画出交换曲线。
 * 3. 思考题：为什么写集合也要过半（W>N/2）？构造两个并发写各自拿 W=1 写不同副本导致分叉的场景。
 * ============================================================
 */
