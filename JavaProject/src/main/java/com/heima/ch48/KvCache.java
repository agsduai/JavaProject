/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：48-02
 * 课名：KV Cache 原理
 * ------------------------------------------------------------
 * 知识点：
 *   - **为什么需要 KV Cache**：decode 每生成一个 token 都要对“整段上文”做注意力计算——不缓存的话每步都重算全部历史的 K/V 投影。**KV Cache 把已算过的 Key/Value 存下来**，每步只算新 token 的 Q/K/V：生成从 O(n²) 重复计算变成 O(n) 线性追加。
 *   - **显存账是推理的容量核心**：KV Cache 大小 = 2(K和V) × 层数 × KV头数 × 头维度 × 精度字节数 × 序列长度 × 并发数。**7B 模型 8K 上下文单请求的 KV 可达 GB 级**——显存大头不是权重而是 KV（长上下文时）。
 *   - **上下文长度与并发是显存的两面**：单请求超长上下文（128K）会吃光 KV 空间；高并发会让 KV 争抢显存触发驱逐/重算。**容量规划 = 权重 + KV（并发×长度）+ 激活余量**（44-05 的显存预算在推理服务的精确版）。
 *   - **省 KV 的技术线**：**GQA/MQA**（KV 头数比注意力头少，KV 直接除以组数）、**量化 KV**（FP8/INT8 存储）、**滑动窗口/稀疏注意力**（只留局部窗口）、**前缀共享**（相同 system prompt 只存一份，多请求共享——44-03 记忆与 40-08 RAG 的系统提示前缀是天然受益者）。
 *   - **KV 与会话管理的关系**：多轮对话把历史 KV 保留可跳过 prefill（缓存命中，TTFT 直降）——**会话黏住同一实例**才能复用（48-08 路由的亲和性需求）。
 *   - 易错点：显存预算只算权重（长上下文必 OOM）；并发压测不带上真实上下文长度（KV 差几个数量级）；会话不粘实例（每轮全量 prefill，TTFT 与成本双爆炸）；长前缀重复计算（未用前缀共享）。
 * ============================================================
 */

package com.heima.ch48;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class KvCache {
    /** KV 显存估算（字节）：2×layers×kvHeads×headDim×bytes×seqLen */
    static double kvBytes(int layers, int kvHeads, int headDim, int bytesPer, int seqLen) {
        return 2.0 * layers * kvHeads * headDim * (double) bytesPer * seqLen;
    }

    static void memoryMath() {
        System.out.println("【KV 显存账：7B 模型（32 层，GQA 8 头，headDim=128）】");
        int layers = 32, kvHeads = 8, headDim = 128;
        List<int[]> cases = List.of(
                new int[]{2, 2048},     // FP16, 2K 上下文
                new int[]{2, 32768},    // FP16, 32K
                new int[]{1, 32768});   // FP8, 32K
        String[] labels = {"FP16 2K", "FP16 32K", "FP8 32K"};
        for (int i = 0; i < cases.size(); i++) {
            int[] c = cases.get(i);
            double per = kvBytes(layers, kvHeads, headDim, c[0], c[1]);
            System.out.printf("  %-9s 单请求 KV ≈ %.2f GB；32 并发 ≈ %.0f GB%n",
                    labels[i], per / 1e9, per * 32 / 1e9);
        }
        System.out.println("  对照：7B 权重 FP16 ≈ 14GB —— 长上下文时 KV 反超权重");
        System.out.println("  结论：并发 × 上下文长度 是显存的两条乘数腿");
        System.out.println();
    }

    static void gqaBenefit() {
        System.out.println("【GQA：KV 头分组，缓存直接除以组数】");
        int heads = 32, kvHeadsMha = 32, kvHeadsGqa = 8;
        System.out.printf("  MHA：KV 头 = %d（与注意力头相同）%n", kvHeadsMha);
        System.out.printf("  GQA：KV 头 = %d（32 头共享 8 组 KV）-> KV 显存 /%.0f%n",
                kvHeadsGqa, (double) kvHeadsMha / kvHeadsGqa);
        System.out.println("  质量损失极小（现代主流模型标配），容量收益 4 倍");
        System.out.println();
    }

    static void prefixSharing() {
        System.out.println("【前缀共享：相同 system prompt 只算一次】");
        String sysPrompt = "system: 你是客服助手…（2200 token 的长系统提示）";
        int sessions = 50;
        System.out.println("  场景：" + sessions + " 个并发会话共用同一 system prompt");
        double perPrefill = 2200 * 30 / 1000.0;   // 粗略 ms，演示用
        System.out.printf("  不共享：每会话 prefill %.0fms × %d = %.0fms 总算力%n",
                perPrefill, sessions, perPrefill * sessions);
        System.out.println("  共享：首个会话 prefill 后，其余命中 KV 复用 -> TTFT 降到毫秒级");
        System.out.println("  受益场景：RAG 固定指令段（40-08）、固定角色 prompt（40-03）");
        System.out.println();
    }

    static void sessionAffinity() {
        System.out.println("【多轮对话的 KV 复用需要会话黏性】");
        List<String> rounds = List.of(
                "轮1：输入 2000 token -> prefill 全量，KV 保留在本实例",
                "轮2（同实例）：只 prefill 新增 300 token -> TTFT 低",
                "轮2（异实例）：重新 prefill 全部 2300 token -> TTFT 高 + 成本高");
        for (String r : rounds) {
            System.out.println("  " + r);
        }
        System.out.println("  路由需求：sessionId 粘住同一副本（48-08），KV 淘汰要有 TTL");
    }

    public static void main(String[] args) {
        memoryMath();
        gqaBenefit();
        prefixSharing();
        sessionAffinity();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用你的模型参数（层数/头数/精度）计算 8K/32K 上下文的 KV 显存账，并得出最大并发数。
 * 2. 实测前缀共享收益：固定长 system prompt + 50 个并发短问题，对比开启/关闭 prefix caching 的 TTFT。
 * 3. 思考题：为什么多轮对话“换实例重算”比“黏实例复用”贵得多？用 prefill 成本与 TTFT 两个角度量化。
 * ============================================================
 */
