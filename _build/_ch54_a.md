### 54-01 推荐系统全景

**知识点**
- **推荐系统回答"海量商品里给这个用户看什么"**：与搜索（38 章，用户主动找）相对，推荐是**无明确意图下的个性化排序**。工业级流水线四段：**召回**（万级 -> 千级，多路并行）-> **粗排**（千 -> 百，轻量模型）-> **精排**（百 -> 几十，复杂模型精算）-> **重排**（业务规则/多样性/打散）。**漏斗每级都是"性能 vs 效果"的折衷**。
- **为什么是漏斗而不是一个大模型打全场**：10 万商品 × 复杂模型逐个打分 = 延迟爆炸；粗排便宜筛掉大概率无关项，精排把算力花在幸存者上——**与搜索引擎的召回/排序同构（38 章），与推理服务的两阶段同理（48 章）**。
- **推荐的三类数据**：用户行为（曝光/点击/购买/停留——正负样本来源）、物品侧（类目/价格/文案）、上下文（时间/场景/设备）。**数据回路是推荐的生命线**：线上行为回流 -> 特征更新 -> 模型迭代（46 章数据平台的消费方）。
- **评估双轨**：**离线**（AUC/召回率，44-10 评测思想）与**在线**（CTR/转化/GMV 提升，A/B 实验 54-08）——离线好在线差是常态（分布漂移/位置偏差），**在线 A/B 才是最终裁决**。
- **Java 工程师的位置**：推荐系统大部分是工程——召回服务、特征服务、打分服务编排、AB 平台、曝光过滤——**模型是算法团队产出的 artifact，工程决定这些模型能不能正确、快速、稳定地服务**（48-01 服务分层的推荐版）。
- 易错点：把推荐当搜索做（无用户维度）；漏斗各级独立优化不看全局（粗排优化砍掉精排想要的候选）；只看离线指标上线；忽略位置偏差（排第一的 CTR 天然高）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class RecsysOverview {
    record Item(long id, double recallScore, double roughScore, double fineScore) {
    }

    static void funnel() {
        System.out.println("【四段漏斗：数量逐级收敛】");
        List<Item> all = new ArrayList<>();
        for (long i = 0; i < 100_000; i++) {
            all.add(new Item(i, 0, 0, 0));
        }
        int afterRecall = 800;         // 多路召回
        int afterRough = 100;          // 粗排
        int afterFine = 30;            // 精排
        int afterRerank = 20;          // 重排/混排
        System.out.printf("  全库 %6d -> 召回 %d -> 粗排 %d -> 精排 %d -> 重排 %d（最终展示）%n",
                all.size(), afterRecall, afterRough, afterFine, afterRerank);
        System.out.println("  各级算力预算：召回贵在路数，粗排贵在规模，精排贵在模型复杂度");
        System.out.println();
    }

    static void stagesDuty() {
        System.out.println("【四段的职责边界】");
        String[][] rows = {
                {"召回", "快 + 全（别漏掉可能喜欢的）", "多路：热门/协同/向量/运营"},
                {"粗排", "便宜地砍到精排吃得下的量", "轻量模型/双塔打分"},
                {"精排", "对幸存者精细打分（CTR/CVR 估分）", "复杂模型 + 全量特征"},
                {"重排", "业务规则兜底：打散/置顶/过滤", "规则引擎 + 多样性算法"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-4s %-28s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  Java 工程师主战场：召回服务、特征拼接、漏斗编排、AB 平台");
    }

    public static void main(String[] args) {
        funnel();
        stagesDuty();
    }
}
```

**课后练习**
1. 画出四段漏斗图并标注每级的输入输出量级与延迟预算（总预算 <200ms，自行分配）。
2. 列出你熟悉产品（或假想电商）的推荐数据回路：行为埋点 -> 数据平台（46 章）-> 特征/模型 -> 服务。
3. 思考题：为什么"粗排优化"必须与"精排"联合评估？构造一个粗排 AUC 升高但精排候选变差的场景。

---

### 54-02 召回：多路召回与融合

**知识点**
- **单路召回的死穴**：任何单一策略（热门/协同/向量）都有系统性盲区——热门路没个性化、协同有冷启动问题、向量召回同质化。**多路召回并行补盲区**：热门榜、协同过滤（看过又看）、向量近邻（40-07）、类目偏好、运营精选、新品池——**每路保底条数，融合去重**。
- **融合与配额**：各路返回后合并去重 -> 按路配额（如热门 ≤30%、协同 ≤40%、向量 ≤30%）——**配额防止强路垄断**（热门路永远跑赢，其他路没有曝光就无法积累数据——54-10 冷启动的联动）。
- **召回服务工程要点**：各路并行调用（50 章扇出）、单路超时降级（路级熔断 44-08）、合并去重（按 item id）、**路级可观测**（每路返回数/最终入选数/点击贡献——路的价值要可衡量，砍路加路有依据）。
- **与 ES 的关系**：向量召回可直接查 ES KNN（38 章集群）；关键词召回用 BM25——**ES 是召回路之一而不是全部**。
- 易错点：各路串行调用（延迟叠加，50 章扇出）；路无配额（热门垄断）；去重保留哪路的分数未定义；路级无监控（盲砍）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
```

**课后练习**
1. 实现完整多路召回融合（4 路 + 去重 + 配额 + 路级统计），并行调用模拟（50-05 虚拟线程）。
2. 加入"路级降级"：协同路超时 -> 其配额让渡给向量路，验证整体返回不空。
3. 思考题：为什么配额能缓解"热门垄断"？从"无曝光则无数据、无数据则更排不上"的冷启动循环分析。

---

### 54-03 协同过滤与相似度

**知识点**
- **协同过滤（CF）的核心假设：相似的人喜欢相似的东西**。**ItemCF**（工业主流）："看过 A 的人也看了 B"——物品相似度矩阵；**UserCF**："与你相似的用户喜欢什么"——用户相似度。**ItemCF 更稳**（物品关系比用户兴趣变化慢、可离线预计算、可解释"因为你看过 X"）。
- **相似度计算**：余弦相似（40-07 同款）/ Jaccard / 改进版（惩罚热门物品：热门共同出现不能说明相似—— IDF 加权）。**共现矩阵 + 归一化**是 ItemCF 的实现骨架。
- **工程实现**：离线计算物品相似度矩阵（46 章批处理）-> 存储 Top K 相似（每物品只存最相似的 K 个）-> 在线：用户历史 -> 查相似表 -> 候选聚合打分。**矩阵分片与稀疏存储**是规模化的关键。
- **衰减与时效**：行为权重随时间衰减（近期行为更能代表兴趣）；热门惩罚防止"人人看过 iPhone"把 iPhone 推到所有相似表首位。
- 易错点：不惩罚热门物品（推荐全是大爆款）；相似度没有下限截断（弱相关也进候选）；离线频率过高（算力浪费）或过低（兴趣漂移跟不上）；未去重已购物品。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ItemCFDemo {
    /** ItemCF：共现 -> 余弦 -> 热门惩罚（示意） */
    static Map<String, Map<String, Double>> sim = new LinkedHashMap<>();

    static void build(List<List<String>> userHistories) {
        Map<String, Integer> itemPop = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> co = new LinkedHashMap<>();
        for (List<String> history : userHistories) {
            for (String a : history) {
                itemPop.merge(a, 1, Integer::sum);
                for (String b : history) {
                    if (!a.equals(b)) {
                        co.computeIfAbsent(a, k -> new LinkedHashMap<>()).merge(b, 1, Integer::sum);
                    }
                }
            }
        }
        for (var e : co.entrySet()) {
            Map<String, Double> row = new LinkedHashMap<>();
            for (var inner : e.getValue().entrySet()) {
                // 余弦近似：共现 / sqrt(流行度积)，惩罚热门
                double denom = Math.sqrt(itemPop.get(e.getKey()) * (double) itemPop.get(inner.getKey()));
                row.put(inner.getKey(), inner.getValue() / denom);
            }
            sim.put(e.getKey(), row);
        }
    }

    public static void main(String[] args) {
        build(List.of(
                List.of("手机壳", "钢化膜", "充电器"),
                List.of("手机壳", "钢化膜"),
                List.of("充电器", "数据线"),
                List.of("iPhone", "手机壳")));
        System.out.println("  手机壳 的相似表：" + sim.get("手机壳"));
        System.out.println("  钢化膜 排前（强相关），iPhone 被热门惩罚压低");
    }
}
```

**课后练习**
1. 实现完整 ItemCF（共现/余弦/热门惩罚/TopK 截断），用 20 个用户行为验证"手机壳"的相似表合理。
2. 实现在线推荐：输入用户历史 -> 查相似表 -> 聚合去重 -> 输出 TopN；对比"惩罚热门"开关的推荐差异。
3. 思考题：为什么工业界 ItemCF 比 UserCF 更主流？从物品关系稳定性、计算规模、可解释性三方面论证。

---

### 54-04 特征工程与 Embedding 特征

**知识点**
- **特征是模型的燃料，特征工程决定模型上限**：三类特征——**用户特征**（画像/历史行为统计：近 7 天点击类目分布）、**物品特征**（类目/价格/热度统计）、**交叉特征**（用户×物品：该用户对该类目的历史 CTR）。**训练与推理特征必须同源同口径**（46-10 特征平台铁律）。
- **Embedding 特征**：把 ID 类稀疏特征（用户 ID/类目 ID）映射为稠密向量——**向量既作为模型输入，也可直接用于向量召回**（40-07 闭环：双塔模型的用户/物品向量余弦召回）。Embedding 的离线训练与在线服务版本要一致（40-07 换模型重建的教训）。
- **特征的时效分层**：实时特征（秒级：本次会话点击）、小时级特征（近 1 小时统计）、天级特征（离线 T+1）——**时效越高工程越贵**，按特征价值分层（43 章流处理的消费方）。
- **特征服务化**：46-10 特征平台的推荐版——在线低延迟取数（Redis 预灌）、离线批量回填、训练-推理一致性校验。
- 易错点：训练用离线特征、推理用实时口径（分布不一致模型失效）；特征穿越（用未来数据训练，离线指标虚高）；Embedding 版本混用；高基数 ID 直接 one-hot（维度爆炸，Embedding 是解）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class FeatureDemo {
    /** 交叉特征：用户 × 类目 历史点击率（训练/推理同源） */
    static Map<String, Double> userCategoryCtr = new LinkedHashMap<>();

    static double ctr(String userId, String category) {
        return userCategoryCtr.getOrDefault(userId + ":" + category, 0.03);  // 全局默认
    }

    public static void main(String[] args) {
        userCategoryCtr.put("u1:phone-case", 0.25);
        userCategoryCtr.put("u1:laptop", 0.01);
        System.out.printf("  u1 在 phone-case 的历史 CTR = %.2f（强偏好特征）%n", ctr("u1", "phone-case"));
        System.out.printf("  u1 在 laptop 的历史 CTR = %.2f%n", ctr("u1", "laptop"));
        System.out.println();
        System.out.println("  铁律：这条 CTR 的计算口径（窗口/分子分母）训练与在线必须同一份定义（46-10）");
        System.out.println("  穿越警示：训练时若用\"当天之后的数据\"算 CTR -> 离线虚高、线上翻车");
    }
}
```

**课后练习**
1. 实现三类特征生成器（用户统计/物品统计/交叉 CTR），口径定义写成契约文档（46-08）。
2. 构造一次"特征穿越"事故（窗口右移一位），对比离线 AUC 虚高与线上真实表现。
3. 思考题：为什么"训练-推理特征一致性"是推荐系统的生死线？从 46-10 特征平台与 44-10 评测的关系论证。

---
