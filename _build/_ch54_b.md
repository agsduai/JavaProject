### 54-05 粗排与精排模型

**知识点**
- **粗排：轻量模型扛规模**——主流是**双塔**（用户塔 + 物品塔分别出向量，点积为分）：**物品塔可离线预计算**（物品向量入库），在线只算用户塔 + 点积——千级打分毫秒级。代价：无用户-物品交叉特征（双塔各自编码，交互靠点积）。
- **精排：复杂模型扛精度**——全量交叉特征（54-04）+ 复杂结构（深度网络/GBDT），输出 CTR/CVR 预估分；**精排分数是排序依据也是业务混排的货币**（eCPM = pCTR × 出价，广告与自然流混排）。
- **粗排与精排的模型一致性问题**：粗排与精排目标不一致会导致"粗排认为好的精排不认"——**对齐方式**：粗排用精排的蒸馏模型（知识蒸馏 44-06 的推荐应用）。
- **校准（calibration）**：pCTR 是概率估计，要与真实点击率对齐（否则业务用 pCTR 计算的 eCPM 系统性偏差）——**校准是精排上线前的必备步骤**（44-10 评测的延伸）。
- 易错点：粗排用全量交叉特征（算不动）；精排分数未校准直接当概率用；模型版本与特征版本错配；离线 AUC 与在线 CTR 脱节分析缺失。
- **工程要点**：粗排物品向量批量预灌 Redis/内存（离线更新）；精排特征在线拼接（特征服务 54-04）+ 模型服务（48 章推理容量同款）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class TwoTowerDemo {
    /** 双塔：用户向量 × 物品向量 点积（物品向量离线预计算） */
    static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    public static void main(String[] args) {
        double[] user = {0.9, 0.1, 0.2};          // 在线：用户塔实时算（一次）
        List<double[]> itemVecs = List.of(         // 离线：物品向量已入库
                new double[]{0.8, 0.2, 0.1},       // 商品A
                new double[]{0.1, 0.9, 0.3});      // 商品B
        for (int i = 0; i < itemVecs.size(); i++) {
            System.out.printf("  商品%d 点积得分 %.3f%n", i + 1, dot(user, itemVecs.get(i)));
        }
        System.out.println("  千级打分 = 一次用户塔 + N 次点积 -> 毫秒级");
        System.out.println("  代价：无交叉特征（用户×物品联合模式学不到）-> 精排补");
    }
}
```

**课后练习**
1. 实现双塔粗排模拟（用户塔 + 预计算物品向量 + 点积排序），1000 候选毫秒级验证。
2. 实现 pCTR 校准对比：校准前后的分数 vs 真实 CTR 的偏差（分组统计）。
3. 思考题：为什么双塔"学不到交叉模式"是结构性缺陷而非实现 bug？从两塔独立编码、仅点积交互的结构推导，并说明蒸馏如何缓解。

---

### 54-06 重排：多样性、打散与业务规则

**知识点**
- **精排分数最高 ≠ 最终展示顺序**：Top 结果全是同类目（10 个手机壳）体验崩坏——**重排解决"模型不知道的体验规则"**：类目打散（相邻不同类目）、同店打散、置顶插坑（运营/广告位）、负反馈过滤（已曝光未点击的降权）。
- **多样性算法**：**MMR**（最大边际相关：相关性与多样性加权轮选）——每一步选"与已选集合差异大且相关"的项；简单打散（滑动窗口内类目不重复）是工程常用版。
- **业务规则的优先级语义**：置顶 > 强插 > 打散 > 过滤——**规则要可配置可审计**（运营改规则不该改代码，44-08 配置化思想）；**每条规则的效果要可归因**（规则命中日志 + A/B）。
- **与精排分数的兼容**：打散会牺牲部分 pCTR——**打散强度是体验与收入的旋钮**（A/B 度量最优档位 54-08）。
- 易错点：规则硬编码（改规则=发版）；打散无上限（相关性崩）；置顶滥用（用户信任度下降，42 章点踩率上升）；曝光过滤内存无界（54-07）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class RerankDemo {
    record Item(long id, String category, double score) {
    }

    /** 滑动窗口打散：窗口 W 内同类目最多 1 个 */
    static List<Item> diversify(List<Item> ranked, int window) {
        List<Item> out = new ArrayList<>();
        List<Item> pool = new ArrayList<>(ranked);
        while (!pool.isEmpty()) {
            boolean placed = false;
            for (int i = 0; i < pool.size(); i++) {
                Item candidate = pool.get(i);
                boolean clash = out.stream()
                        .skip(Math.max(0, out.size() - window + 1))
                        .anyMatch(p -> p.category().equals(candidate.category()));
                if (!clash) {
                    out.add(candidate);
                    pool.remove(i);
                    placed = true;
                    break;
                }
            }
            if (!placed) {                  // 全部冲突 -> 放最优先的
                out.add(pool.remove(0));
            }
        }
        return out;
    }

    public static void main(String[] args) {
        List<Item> ranked = List.of(
                new Item(1, "case", 0.9), new Item(2, "case", 0.8),
                new Item(3, "charger", 0.7), new Item(4, "case", 0.6));
        List<Item> out = diversify(ranked, 2);
        for (Item i : out) {
            System.out.printf("  %d [%s] %.2f%n", i.id(), i.category(), i.score());
        }
    }
}
```

**课后练习**
1. 实现完整重排器（窗口打散 + 置顶插坑 + 曝光过滤），输出重排前后对比与牺牲的 pCTR 总量。
2. 实现 MMR 变版（相关性与多样性加权），对比窗口打散的多样性指标（类目熵）。
3. 思考题：为什么"打散强度"要 A/B 调优而不是拍脑袋？从短期 CTR 损失与长期留存收益的时间维度分析。

---

### 54-07 曝光过滤与去重

**知识点**
- **"看过不再推"是推荐的底线体验**——曝光记录（userId+itemId+时间）规模巨大（每天上亿），**存储与过滤是工程挑战**：布隆过滤器（40-10 同款：可能误判不存在必准——误判=少推，可接受）；分窗口保留（只过滤近 7 天曝光，更久的允许重推——重复曝光有时是必要的）。
- **存储选型**：Redis Set/Bitmap（按用户分 key，TTL 7 天）——**内存账要算**：人均曝光 200 × 1 亿用户 = 200 亿条，Bitmap 压缩是必需（46-03 成本思想）。
- **去重的时机**：召回后粗排前（尽早，省算力）；**曝光写入与过滤的竞态**：本次曝光写入后，同会话后续请求立即过滤（会话内去重）。
- **重复曝光的产品语义**：不是所有重复都该过滤——购物车商品、收藏商品、购买过的复购场景——**过滤清单可配置**（曝光/已购/不感兴趣 分维度）。
- 易错点：全量永久过滤（内存爆炸 + 复购场景错误）；布隆误判率没调（过高把新品全滤掉）；过滤在精排后（算力浪费）；负反馈（点踩/不感兴趣）没有独立于曝光的更强降权。

**示例代码**
```java
import java.util.BitSet;
import java.util.List;

public class BloomFilterDemo {
    static class SimpleBloom {
        final BitSet bits = new BitSet(1024);

        void add(String item) {
            bits.set(item.hashCode() & 1023);
            bits.set((item.hashCode() >>> 5) & 1023);
        }

        boolean mightContain(String item) {
            return bits.get(item.hashCode() & 1023) && bits.get((item.hashCode() >>> 5) & 1023);
        }
    }

    public static void main(String[] args) {
        SimpleBloom seen = new SimpleBloom();
        for (String id : List.of("item-1", "item-2")) {
            seen.add(id);
        }
        System.out.println("  已曝光 item-1 -> mightContain=" + seen.mightContain("item-1"));
        System.out.println("  未曝光 item-9 -> mightContain=" + seen.mightContain("item-9") + "（可能误判）");
    }
}
```

**课后练习**
1. 实现完整曝光过滤（布隆 + 窗口 TTL + 分维度清单），1 万曝光流验证过滤与误判率。
2. 实现"会话内去重"：同会话第二次请求过滤已返回项，验证体验一致性。
3. 思考题：为什么"误判不存在必准"的布隆过滤器适合曝光过滤？从"误判后果=少推一次"与"漏判后果=重复曝光"的不对称性分析。

---

### 54-08 A/B 实验与效果评估

**知识点**
- **推荐的效果必须用 A/B 验证**：分流（按用户尾号/哈希到实验组与对照组——**同用户恒定同组**，41-07 粘性思想）-> 指标收集（CTR/转化/人均时长/留存）-> 显著性检验（样本量与置信度）-> 结论与放量。**没有 A/B 的推荐优化是玄学**（离线指标与在线表现经常背离）。
- **实验平台三要素**：分流服务（稳定分流 + 分层实验正交——多个实验同时跑互不污染，哈希不同盐）、指标管道（46 章数据平台的行为回流）、置信判定（统计显著性，避免"看起来涨了"）。
- **常见陷阱**：**新奇效应**（用户对新样式先点后腻，观察期要够长）；**辛普森悖论**（分组看都涨、合并看反跌——分流不均）；**多重检验**（同时跑太多实验，假阳性升高）；**观察期截断**（大促期实验结果不可外推）。
- **推荐特有的评估维度**：多样性/新鲜度/长尾覆盖（不只 CTR）——**只优化 CTR 会把推荐变成"标题党聚合器"**（52-06 的目标权衡思想）。
- 易错点：分流键不稳定（用户在组间跳变）；实验组与对照组流量不均（置信失效）；看完一周数据就下结论（未达统计显著）；只报显著正向不报负向（选择性汇报）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class AbSplitDemo {
    /** 稳定分流：哈希(user, experiment) % buckets，同用户恒定同组 */
    static String bucket(String userId, String experiment, int buckets) {
        int h = Math.abs((userId + ":" + experiment).hashCode());
        int slot = h % buckets;
        return slot < buckets / 2 ? "control" : "treatment";
    }

    public static void main(String[] args) {
        Map<String, String> observed = new LinkedHashMap<>();
        for (String u : new String[]{"u1", "u2", "u3", "u1", "u2"}) {
            observed.put(u, bucket(u, "new-ranking", 100));
        }
        System.out.println("  分流结果：" + observed);
        System.out.println("  u1/u2 两次请求分组一致 —— 粘性分流（41-07 同源）");
    }
}
```

**课后练习**
1. 实现分流器（分层实验正交：两实验同用户分组独立），验证正交性（2×2 组合分布均匀）。
2. 实现"显著性判定"简化版：给定两组 CTR 与样本量，计算是否达到 95% 置信（正态近似）。
3. 思考题：为什么推荐实验要同时看 CTR 与多样性/留存？构造一个"CTR 升、留存降"的标题党策略用例。

---

### 54-09 实时特征与在线服务

**知识点**
- **实时特征让推荐"跟上用户"**：用户 5 分钟前看了手机壳 -> 本会话请求应立刻偏向手机配件——**实时特征的链路**：行为埋点 -> MQ（28 章）-> Flink 实时聚合（43 章）-> 特征存储（Redis，46-10）-> 推荐在线读取。**延迟目标：行为发生到影响推荐 < 1 分钟**。
- **在线特征读取的延迟纪律**：推荐接口总预算 200ms 中特征拼接占大头——**特征服务批量化**（一次取全特征，不全量逐个查）、本地缓存兜底、超时降级（特征缺失用默认值，44-08 阶梯）。
- **实时特征的新鲜度与正确性**：会话内去重联动（54-07）、特征抖动（短窗口统计噪声大，需平滑）、**特征回灌**（实时丢失后从离线兜底重建，46-10）。
- **与流处理的对接**：Flink 聚合窗口（43-03）输出实时统计 -> 写特征存储——**流处理的"状态 TTL"与特征窗口对齐**。
- 易错点：实时特征链路断了没人知道（新鲜度监控缺失 46-10）；特征拼接 N+1 查询（逐特征逐请求查 Redis）；实时特征覆盖离线特征口径（两套口径打架 54-04）。
- **延迟预算分配示例**：召回 60ms + 特征 40ms + 粗排 30ms + 精排 60ms + 重排 10ms = 200ms——**预算是设计出来的**（44-08）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class RealtimeFeatureDemo {
    /** 会话实时兴趣：最近 3 次点击的类目计数（Flink 输出 -> Redis） */
    static final Map<String, Map<String, Integer>> sessionInterest = new LinkedHashMap<>();

    static void onUserClick(String user, String category) {
        sessionInterest.computeIfAbsent(user, k -> new LinkedHashMap<>())
                .merge(category, 1, Integer::sum);
    }

    static Map<String, Integer> features(String user) {
        return sessionInterest.getOrDefault(user, Map.of());
    }

    public static void main(String[] args) {
        onUserClick("u1", "phone-case");
        onUserClick("u1", "phone-case");
        onUserClick("u1", "charger");
        System.out.println("  u1 实时兴趣：" + features("u1"));
        System.out.println("  推荐接口读取此特征 -> 本会话偏向手机配件（延迟 < 1min）");
        System.out.println();
        System.out.println("  工程要点：TTL 会话过期清理（43-05 状态治理）；断流兜底回退离线特征");
    }
}
```

**课后练习**
1. 实现实时特征链路模拟（埋点 -> 聚合 -> 特征读取），验证"点击后下一请求立即反映兴趣"。
2. 实现特征服务批量化：一次请求 5 个特征域的批量读取（对比 N+1 的耗时差异）。
3. 思考题：为什么实时特征断流要有"回退离线兜底"？从特征缺失时的模型行为（默认值质量）与新鲜度监控（46-10）分析。
