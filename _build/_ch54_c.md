### 54-10 冷启动问题

**知识点**
- **冷启动三类**：**新用户**（无历史——用人口属性/设备/地域 + 热门/运营精选兜底）、**新物品**（无交互——内容特征/相似物品扩散 + 流量扶持配额 54-02）、**新系统**（全无数据——运营策略先行，逐步引入个性化）。
- **新物品的流量扶持是"投资"**：新品无曝光就永远无数据——**扶持配额**（新品池固定占比 + 快速升降级：点击率达标升级流量池、不达标降级退出）——**探索与利用（explore/exploit）的平衡**是推荐系统的基础张力（54-02 配额的深化）。
- **新用户的快速收敛**：首次会话的行为价值极高（尽快收集兴趣信号）——**首屏策略**：热门 + 多样化类目试探（信息增益最大化：多样曝光比单类目曝光更快定位兴趣）。
- **Embedding 冷启动**：新物品无训练数据——**内容特征生成向量**（标题/图片文本 Embedding，40-08 RAG 同源技术）替代行为向量。
- 易错点：冷启动推荐全上热门（新用户看到的全是爆款，个性化无从谈起）；新品扶持无退出机制（垃圾新品长期占坑）；内容 Embedding 与行为 Embedding 混用同一空间（40-07 不同空间教训）。
- **与 54-02 配额的统一**：扶持配额、路配额、打散窗口——**本质都是"曝光资源的分配策略"**，全部需要 A/B 验证（54-08）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class ColdStartDemo {
    /** 新品扶持池：流量配额 + 快速升降级 */
    static class NewItemPool {
        final Map<String, Integer> impressions = new LinkedHashMap<>();
        final Map<String, Integer> clicks = new LinkedHashMap<>();

        boolean promote(String itemId) {
            int imp = impressions.getOrDefault(itemId, 0);
            int click = clicks.getOrDefault(itemId, 0);
            return imp >= 100 && (double) click / imp >= 0.05;
        }
    }

    public static void main(String[] args) {
        NewItemPool pool = new NewItemPool();
        pool.impressions.put("new-1", 150);
        pool.clicks.put("new-1", 12);
        System.out.printf("  新品 new-1（曝光150 点击12 CTR=%.0f%%）-> %s%n",
                100.0 * 12 / 150, pool.promote("new-1") ? "升级流量池" : "继续扶持观察");
    }
}
```

**课后练习**
1. 实现新品扶持池完整版（配额 + 升降级 + 退出），用 10 个新品模拟 7 天流量分配演化。
2. 实现新用户首屏策略：热门 + 类目多样化试探，模拟 5 次会话后兴趣收敛的推荐变化。
3. 思考题：为什么新物品扶持要设"退出机制"？从扶持资源的机会成本与垃圾新品挤占分析（54-02 配额的对称面）。

---

### 54-11 综合案例：商品推荐流水线

**知识点**
- **把本章收束成一条完整推荐流水线**：请求 -> 特征拼接（54-04）-> 多路召回（54-02：热门/ItemCF/向量）-> 曝光过滤（54-07）-> 粗排双塔（54-05）-> 精排 pCTR（54-05）-> 重排打散（54-06）-> 冷启动注入（54-10）-> 返回 + 曝光日志回写（数据回路）。**38 章搜索、40 章向量、43 章流、46 章数据平台在此合流**。
- **延迟预算**：特征 40ms + 召回 60ms + 过滤/粗排 30ms + 精排 60ms + 重排 10ms = 200ms——**每段可降级**（精排超时回退粗排分 44-08）。
- **正确性验收**：单测（各段逻辑）+ 回归（黄金请求集 44-10）+ 压测（目标 QPS 下 P99）+ A/B（54-08）——**推荐功能上线四门**。
- **可观测**：段级耗时/各路召回数/过滤量/最终 CTR 分实验组——**段级指标定位"效果劣化在哪一段"**（42 章分解思想）。
- 易错点：流水线各段无超时降级（一段卡全链）；曝光日志缺失（数据回路断，越跑越差）；无 A/B 直接过全量；精排分数与重排规则冲突无仲裁记录。
- **Java 工程师的验收清单**：召回并行化、特征批量读、过滤内存可控、降级链路完整、指标分实验组——**模型之外的工程能力决定推荐系统的下限**。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductRecCase {
    record RecItem(long id, double score, String source) {
    }

    static List<RecItem> pipeline(String userId, long deadlineMs) {
        long start = System.currentTimeMillis();
        Map<String, Long> budget = new LinkedHashMap<>();
        budget.put("feature", 40L);
        budget.put("recall", 60L);
        budget.put("rough+fine", 90L);
        budget.put("rerank", 10L);
        // 模拟各段（真实实现为服务调用）
        List<RecItem> result = new ArrayList<>(List.of(
                new RecItem(101, 0.92, "itemcf"),
                new RecItem(102, 0.88, "vector"),
                new RecItem(103, 0.81, "hot"),
                new RecItem(104, 0.75, "itemcf")));
        long used = System.currentTimeMillis() - start;
        System.out.println("  预算表：" + budget);
        System.out.printf("  本次实际耗时 %dms（预算 200ms）-> %s%n", used,
                used <= deadlineMs ? "达标" : "触发降级（精排回退粗排分）");
        return result;
    }

    public static void main(String[] args) {
        List<RecItem> items = pipeline("u1", 200);
        System.out.println("  最终推荐：" + items.stream().map(r -> r.id()).toList());
        System.out.println("  曝光日志回写 -> 数据回路闭合（54-01）");
    }
}
```

**课后练习**
1. 端到端实现迷你推荐流水线（8 段全部模拟真实逻辑），注入"精排超时"验证降级链。
2. 实现段级可观测：每段耗时/召回数/过滤量按实验组输出，定位一次"效果劣化在哪段"的模拟事故。
3. 思考题：为什么推荐系统的"工程下限"决定"模型上限"？从特征时效、服务延迟、数据回路三方面的模型依赖论证。
