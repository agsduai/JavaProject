/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：54-02
 * 课名：召回：多路召回与融合
 * ------------------------------------------------------------
 * 知识点：
 *   - **单路召回的死穴**：任何单一策略（热门/协同/向量）都有系统性盲区——热门路没个性化、协同有冷启动问题、向量召回同质化。**多路召回并行补盲区**：热门榜、协同过滤（看过又看）、向量近邻（40-07）、类目偏好、运营精选、新品池——**每路保底条数，融合去重**。
 *   - **融合与配额**：各路返回后合并去重 -> 按路配额（如热门 ≤30%、协同 ≤40%、向量 ≤30%）——**配额防止强路垄断**（热门路永远跑赢，其他路没有曝光就无法积累数据——54-10 冷启动的联动）。
 *   - **召回服务工程要点**：各路并行调用（50 章扇出）、单路超时降级（路级熔断 44-08）、合并去重（按 item id）、**路级可观测**（每路返回数/最终入选数/点击贡献——路的价值要可衡量，砍路加路有依据）。
 *   - **与 ES 的关系**：向量召回可直接查 ES KNN（38 章集群）；关键词召回用 BM25——**ES 是召回路之一而不是全部**。
 *   - 易错点：各路串行调用（延迟叠加，50 章扇出）；路无配额（热门垄断）；去重保留哪路的分数未定义；路级无监控（盲砍）。
 * ============================================================
 */

package com.heima.ch54;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class RecallFusion {
    /** 多路召回融合：去重 + 路配额 */
    record Candidate(long itemId, String source, double score) {
    }

    static List<Long> fuse(List<Candidate> all, Map<String, Integer> quotas) {
        List<Long> result = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();          // 去重（保先到路）
        Map<String, Integer> perSource = new LinkedHashMap<>();
        for (Candidate c : all) {
            int quota = quotas.getOrDefault(c.source(), 0);
            int used = perSource.getOrDefault(c.source(), 0);
            if (used >= quota || !seen.add(c.itemId())) {
                continue;
            }
            perSource.merge(c.source(), 1, Integer::sum);
            result.add(c.itemId());
        }
        return result;
    }

    public static void main(String[] args) {
        List<Candidate> all = List.of(
                new Candidate(1, "hot", 9.9), new Candidate(2, "hot", 9.8),
                new Candidate(2, "cf", 9.7), new Candidate(3, "vector", 9.5),
                new Candidate(4, "cf", 9.0));
        Map<String, Integer> quotas = Map.of("hot", 1, "cf", 2, "vector", 1);
        System.out.println("  融合配额后：" + fuse(all, quotas) + "（去重 2 + 配额约束）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整多路召回融合（4 路 + 去重 + 配额 + 路级统计），并行调用模拟（50-05 虚拟线程）。
 * 2. 加入"路级降级"：协同路超时 -> 其配额让渡给向量路，验证整体返回不空。
 * 3. 思考题：为什么配额能缓解"热门垄断"？从"无曝光则无数据、无数据则更排不上"的冷启动循环分析。
 * ============================================================
 */
