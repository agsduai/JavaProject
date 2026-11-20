/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-07
 * 课名：实验分层与正交设计
 * ------------------------------------------------------------
 * 知识点：
 *   - **一个用户可能同时在多个实验里**：**推荐算法实验 + UI 改版实验 + 价格展示实验**——**如果都从同一份流量里随机切，实验之间会互相污染**。
 *   - **分层（Layer）**：**把流量按维度切成互不重叠的层**，**不同层用不同的 hash 种子**——**同一层的实验互斥，不同层的实验正交**。
 *   - **正交的含义**：**A 实验的对照组/实验组里，B 实验的分组比例都应该是 50/50**——**这样才能各自独立归因**，**用交叉表就能验证**。
 *   - **互斥（同层）**：**同一层内流量只能进一个实验**——**否则两个改版叠加，你不知道是谁的功劳**，**但会浪费流量（流量不够时要做流量复用）**。
 *   - **分层的代价**：**层数越多，每层能用的流量越少**——**层是「互斥换清晰」，正交是「复用换效率」**，**两者要按实验数量权衡**。
 *   - 易错点：hash 的 key 里不带层名（同一个用户在所有层分组完全一致）；同层放两个实验（互相污染）；分层后忘了验证交叉表（以为正交其实不正交）。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

import java.util.LinkedHashMap;
import java.util.Map;

public class ExperimentLayers {
    // 稳定分桶，种子不同 -> 不同层互相独立
    static int bucket(String userId, int seed, int mod) {
        int h = seed;
        for (int i = 0; i < userId.length(); i++) {
            h ^= userId.charAt(i);
            h *= 0x01000193;
        }
        return Math.floorMod(h, mod);
    }

    // 一层的分流：0 = 对照组，1 = 实验组。key 里带层名 -> 同一个人在不同层的分组互相独立
    static int assign(String userId, String layer, int seed) {
        return bucket(userId + ":" + layer, seed, 100) < 50 ? 1 : 0;
    }

    // 错误做法：key 里不带层名，同一个 seed 就会让两层完全相关
    static int assignSharedKey(String userId, int seed) {
        return bucket(userId, seed, 100) < 50 ? 1 : 0;
    }

    public static void main(String[] args) {
        String[] layers = {"推荐算法", "UI改版", "价格展示"};
        int[] seeds = {0x811C9DC5, 0x12345678, 0x9E3779B9};

        int n = 200_000;
        // 统计：每个用户在三层的分组组合
        Map<String, Integer> cross = new LinkedHashMap<>();
        int[] solo = new int[3];                 // 每层实验组人数
        for (int i = 0; i < n; i++) {
            String u = "u" + i;
            int a = assign(u, layers[0], seeds[0]);
            int b = assign(u, layers[1], seeds[1]);
            int c = assign(u, layers[2], seeds[2]);
            solo[0] += a;
            solo[1] += b;
            solo[2] += c;
            cross.merge("" + a + b + c, 1, Integer::sum);
        }

        System.out.println("【三层实验，各自 50/50】共 " + n + " 个用户");
        for (int i = 0; i < layers.length; i++) {
            System.out.printf("  %-10s 实验组 %6d (%.2f%%)%n",
                    layers[i], solo[i], solo[i] * 100.0 / n);
        }

        System.out.println();
        System.out.println("【正交验证：A 实验分组 × B 实验分组的交叉表】");
        System.out.println("  如果两层独立，每个格子的占比都应该接近 25%");
        Map<String, Integer> pair = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String u = "u" + i;
            int a = assign(u, layers[0], seeds[0]);
            int b = assign(u, layers[1], seeds[1]);
            pair.merge("" + a + b, 1, Integer::sum);
        }
        System.out.printf("%-12s %-18s %-18s%n", "", "B=对照", "B=实验");
        for (int a = 0; a <= 1; a++) {
            System.out.printf("%-12s", "A=" + (a == 0 ? "对照" : "实验"));
            for (int b = 0; b <= 1; b++) {
                int cnt = pair.getOrDefault("" + a + b, 0);
                System.out.printf("%-18s", cnt + String.format(" (%.2f%%)", cnt * 100.0 / n));
            }
            System.out.println();
        }
        System.out.println("  -> 四个格子都在 25% 附近，说明两层正交，可以各自独立归因");

        System.out.println();
        System.out.println("【对照：key 不带层名 + 同一个种子 -> 完全相关，不正交】");
        Map<String, Integer> badPair = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String u = "u" + i;
            int a = assignSharedKey(u, seeds[0]);
            int b = assignSharedKey(u, seeds[0]);     // 同一个 key + 同一个种子
            badPair.merge("" + a + b, 1, Integer::sum);
        }
        System.out.printf("%-12s %-18s %-18s%n", "", "B=对照", "B=实验");
        for (int a = 0; a <= 1; a++) {
            System.out.printf("%-12s", "A=" + (a == 0 ? "对照" : "实验"));
            for (int b = 0; b <= 1; b++) {
                int cnt = badPair.getOrDefault("" + a + b, 0);
                System.out.printf("%-18s", cnt + String.format(" (%.2f%%)", cnt * 100.0 / n));
            }
            System.out.println();
        }
        System.out.println("  -> 只有对角两个格子有数据：进了 A 实验的人必然也进 B 实验");
        System.out.println("     这时 B 的效果里混着 A 的效果，两个实验的结论都不可信");
        System.out.println("     修法：bucket 的 key 里带上层名（本课 assign 的写法），或每层换一个种子");

        System.out.println();
        System.out.println("【8 种组合的分布】三层全交叉，应该均匀分成 8 份（各 12.5%）");
        cross.forEach((k, v) -> System.out.printf("  A/B/C=%s : %6d (%.2f%%)%n", k, v, v * 100.0 / n));

        System.out.println();
        System.out.println("【结论】层内互斥保证「同一份流量不打架」，跨层换种子保证「互不相关」；");
        System.out.println("  验证手段就是交叉表——格子均匀才叫正交，不均匀说明种子或分组逻辑有问题。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 B 层的种子改成和 A 层相同，重跑交叉表，观察四个格子如何塌成两个。
 * 2. 加第四层（「新人引导」实验），验证 16 种组合是否各占 6.25%。
 * 3. 思考题：为什么「同层放两个实验」会让归因失效？举一个「两个改版都提升转化率」的具体例子。
 * ============================================================
 */

