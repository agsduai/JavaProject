### 44-01 从工具调用到 Agent

**知识点**
- **工具调用（40-06）是一次决策，Agent 是一组自主决策的循环**：工具调用是"用户说一句话 -> 模型决定调一次工具 -> 返回"；Agent 是"给一个目标 -> 模型反复【思考 -> 调工具 -> 观察结果 -> 再思考】直到目标完成"。**Agent = LLM 循环 + 工具 + 终止条件**，没有更神秘的东西。
- **Agent 的最小组成五要素**：目标（用户任务）、工具集（能做什么）、循环控制器（最多几轮、何时停）、记忆（上下文与状态，44-03）、护栏（权限与预算，44-09）。**五要素里四个是工程，只有一个是模型**——Java 工程师的领地在此。
- **自主性是双刃剑**：Agent 可以把"查库存 -> 补货 -> 通知采购"自己编排完（人不在环）；代价是**错误也自主传播**——第一步查错，后面全错且更难追溯。因此 Agent 的自主边界（哪些操作允许自主、哪些要人确认）是架构决策不是产品细节。
- **Agent 与工作流（workflow）的分界**：工作流是**人写死的步骤图**（确定性，模型只是节点里的生成器）；Agent 是**模型自己决定下一步**（开放性，行为不可穷举）。**生产系统的主流是"工作流为主 + 局部 Agent"**：结构确定的部分写死，不确定的分支交给 Agent 循环。
- **什么时候值得用 Agent**：任务步骤无法预先枚举（排障、研究型任务）、需要多轮工具交互（先查再算再验证）、单次工具调用循环（44-02 的 ReAct）比手写编排便宜。**步骤固定的任务写成工作流更可靠**。
- 易错点：把 Agent 当万能药（能用普通函数解决的也套 Agent，成本与不可控性双升）；没有终止条件的循环（模型反复调工具直到上下文爆炸）；自主权限过大（Agent 直接执行退款）；混淆工作流与 Agent（把确定性流程写成 Agent，回归测试都做不了）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentIntro {
    record Tool(String name, String desc, String effect) {
    }

    static final List<Tool> TOOLS = List.of(
            new Tool("query_stock", "查询商品库存", "只读"),
            new Tool("create_po", "创建补货单", "写操作，需审批"),
            new Tool("notify", "发通知", "写操作"));

    /** Agent 循环：思考 -> 工具 -> 观察 -> 继续/终止 */
    static void agentLoop() {
        System.out.println("【目标：库存低于 20 的商品自动补货】");
        String goal = "检查商品 A100 的库存，低于 20 则补货 100 件并通知采购";
        List<String> trace = new ArrayList<>();
        // 轮 1：思考 -> 工具
        trace.add("思考1：需要先查库存 -> 调用 query_stock(A100)");
        int stock = 12;                                    // 工具执行结果
        trace.add("观察1：库存=" + stock + " -> 低于 20");
        // 轮 2：判断 -> 写操作走审批（护栏）
        trace.add("思考2：需补货 -> create_po 是写操作 -> 记录待审批而非直接执行");
        trace.add("观察2：补货单 PO-1 已创建（待审批）");
        // 轮 3：通知 -> 终止
        trace.add("思考3：通知采购 -> notify");
        trace.add("观察3：已通知 -> 目标完成，终止");
        for (String t : trace) {
            System.out.println("  " + t);
        }
        System.out.println("  循环三要素：终止条件（目标完成/达最大轮数）+ 工具结果注入 + 每轮重审权限");
        System.out.println();
    }

    static void agentVsWorkflow() {
        System.out.println("【Agent 与工作流的分界：步骤是否可预先枚举】");
        String[][] rows = {
                {"退费审批流", "工作流", "步骤固定：校验->计算->落库，确定性优先"},
                {"客服问题诊断", "Agent", "先问什么、查什么都取决于用户输入"},
                {"报表生成", "工作流", "数据源与格式固定"},
                {"线上事故排查", "Agent", "排查路径无法预先穷举"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-6s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  生产主流：工作流为主 + 局部 Agent —— 可测试性优先");
        System.out.println();
    }

    static void autonomyBoundary() {
        System.out.println("【自主边界：哪些操作允许 Agent 自主执行】");
        Map<String, String> boundary = new LinkedHashMap<>();
        boundary.put("只读查询", "自主执行（读不会造成不可逆损失）");
        boundary.put("内部通知", "自主执行，但记录审计");
        boundary.put("创建待审单据", "允许创建，执行需人审批");
        boundary.put("资金操作/删除/外发", "禁止自主，只能建议 + 人工执行");
        for (Map.Entry<String, String> e : boundary.entrySet()) {
            System.out.printf("  %-14s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  判据：操作是否不可逆 + 影响是否可估算");
        System.out.println("  循环控制器每轮重查权限：上一轮允许不代表这一轮允许");
    }

    public static void main(String[] args) {
        agentLoop();
        agentVsWorkflow();
        autonomyBoundary();
    }
}
```

**课后练习**
1. 把 40-06 的单次工具调用改造成最小 Agent 循环（终止条件 + 最大轮数 + 每轮权限检查），实现"查订单 -> 判断是否超时 -> 超时则建议补偿"三步自主任务。
2. 为你的业务划分"工作流 vs Agent"边界：列 6 个任务，标注归属与理由，找出一个"误用 Agent"的候选并改成工作流。
3. 思考题：为什么说 Agent 的五要素里"四个是工程"？从循环控制、记忆、护栏的实现复杂度与模型能力的边界分析。

---

### 44-02 ReAct 循环与多步规划

**知识点**
- **ReAct 是 Agent 循环的经典范式**：每轮输出 **Thought（我该干什么）-> Action（调什么工具+参数）-> Observation（工具返回）**，循环直到 Thought 判定目标完成。它的价值是**把推理过程显式化**——每个决策可审计、出错可定位到具体轮次。
- **显式规划 vs 隐式规划**：隐式（每轮只看当前状态决定下一步，ReAct 默认）简单灵活但可能绕远路；显式（先让模型输出完整计划再逐步执行）可控性强但计划可能失效需要重规划。**生产常用混合：先出计划，执行中允许修订**。
- **上下文膨胀是循环的成本曲线**：每轮把"历史 Thought/Action/Observation"全部回传——5 轮 × 每轮 800 token = 4000 token 仅历史。**Observation 必须精炼**（40-06 的工具结果精炼原则在 Agent 里更关键），必要时对历史做摘要压缩（44-03）。
- **失败处理是循环的健壮性核心**：工具报错（返回结构化错误让模型重试或换路）、死循环检测（同一 Action 连续 N 次 -> 强制终止）、计划偏离检测（执行 3 轮后对照原计划，偏离过大回到重规划）。
- **多步任务的检查点（checkpoint）**：长任务在第 N 步崩溃，从检查点恢复而不是从头再来——每轮结束把（目标、已完成步骤、中间产物）持久化。**Agent 恢复语义与流处理 checkpoint（43-05）同构**。
- **可观测性（42 章思想在此复用）**：每次循环记录 trace：轮次、Thought、Action、耗时、token 消耗。**Agent 调试的唯一入口就是这份 trace**，没有 trace 的 Agent 出错等于盲修。
- 易错点：Observation 原样塞 JSON 大对象（上下文爆炸）；没有死循环检测（同参数调用刷屏）；Thought 不输出（黑盒循环，无法审计）；恢复语义缺失（长任务崩溃全部重跑）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReActLoop {
    record Round(int no, String thought, String action, String observation, int tokens) {
    }

    /** 模拟的执行轨迹：查库存 -> 计算补货量 -> 校验预算 -> 完成 */
    static void reactTrace() {
        System.out.println("【目标：为爆款 A100 制定补货方案（预算上限 5000 元）】");
        List<Round> rounds = List.of(
                new Round(1, "先查当前库存与销量", "query_stock(A100)", "库存 12，日均销量 30", 180),
                new Round(2, "按 7 天销量估算需求：30×7-12=198 件", "calc(30*7-12)", "建议补货 198 件", 220),
                new Round(3, "198 件单价 25 元 = 4950 元，需校验预算", "check_budget(4950)", "预算内（上限 5000）", 200),
                new Round(4, "预算通过，输出方案", "finish", "补货 198 件，总价 4950 元", 150));
        int totalTokens = 0;
        for (Round r : rounds) {
            totalTokens += r.tokens();
            System.out.printf("  轮%d Thought: %s%n        Action: %s%n        Observation: %s%n",
                    r.no(), r.thought(), r.action(), r.observation());
        }
        System.out.printf("  终止：finish；总消耗 %d token（历史每轮全量回传）%n", totalTokens);
        System.out.println();
    }

    static void guards() {
        System.out.println("【循环护栏：健壮性的四道闸】");
        String[][] guards = {
                {"最大轮数", "8 轮到顶 -> 强制终止并输出已有结论", "防无限循环"},
                {"重复检测", "同一 Action+参数 连续 2 次 -> 终止", "防模型绕圈"},
                {"失败重试", "工具错误返回 {error:...} -> 模型可换路", "失败也是信息"},
                {"偏离检测", "第 3 轮对照原计划，偏差过大 -> 重规划", "防跑题"},
        };
        for (String[] g : guards) {
            System.out.printf("  %-8s %-40s %s%n", g[0], g[1], g[2]);
        }
        System.out.println();
    }

    static void checkpointRecovery() {
        System.out.println("【长任务的检查点与恢复】");
        record Checkpoint(int round, String done, String next) {
        }
        List<Checkpoint> cps = List.of(
                new Checkpoint(1, "库存=12 已查", "计算补货量"),
                new Checkpoint(2, "补货量 198 已算", "校验预算"),
                new Checkpoint(3, "预算通过", "输出方案"));
        Checkpoint crash = cps.get(1);
        System.out.printf("  崩溃于第 3 轮前 -> 从检查点恢复：%s（已完成）%n        继续：%s%n",
                crash.done(), crash.next());
        System.out.println("  持久化内容：目标 + 已完成步骤 + 中间产物 + 权限状态");
        System.out.println("  与 43-05 流处理 checkpoint 同构：可恢复优于可重跑");
    }

    public static void main(String[] args) {
        reactTrace();
        guards();
        checkpointRecovery();
    }
}
```

**课后练习**
1. 实现带全部四道护栏的 ReAct 循环（模拟模型决策），构造"工具反复失败"与"同参数绕圈"两种场景验证护栏。
2. 给循环加检查点：在第 2 轮后注入崩溃，验证恢复后从第 3 步继续且最终结果正确。
3. 思考题：为什么 Observation 必须精炼而 Thought 可以保留？从上下文成本与可审计性的不同需求分析两者的处理差异。

---

### 44-03 Agent 的记忆体系

**知识点**
- **Agent 的记忆分三层，各管不同时效**：**工作记忆**（当前循环的上下文：Thought/Action/ Observation，随任务存亡）、**会话记忆**（跨轮次对话历史，40-05 的会话管理）、**长期记忆**（跨会话沉淀的用户偏好、历史结论，存向量库/DB）。**三层混用是 Agent 记忆混乱的根源**。
- **工作记忆的裁剪**：长任务的 Observation 累积最凶——策略：完成步骤的 Observation 压缩成一行摘要（"已查库存=12"），只保留最近 2 轮原文。**裁剪不丢关键结论**（库存数字要留，查询过程可丢）。
- **长期记忆的写入要"事件驱动 + 主动沉淀"**：任务结束时用小模型总结"本次学到了什么"（用户偏好 A 品牌下单快、该项目部署前要清缓存），**有价值的才写入**——什么都存等于没有记忆（检索噪声淹没有效信息）。
- **记忆的检索与注入**：长期记忆按当前任务语义检索 top K（40-07 向量检索），以"已知信息"块注入 system。**检索质量决定记忆价值**：向量库的切块与 40-08 RAG 同一套方法论。
- **记忆的时效与失效**：偏好会变（用户换了手机品牌）、事实会过期（项目下线了）——记忆条目带时间戳与 TTL，**定期清理与置信度衰减**。永不失效的记忆最终变成误导。
- **跨 Agent 共享记忆**：多智能体（44-04）协作时共享一块"黑板"（blackboard）：中间结论、当前状态——避免每个 Agent 重复查询。**黑板要分角色可写**，否则互相覆盖。
- 易错点：三层记忆不分（会话历史当长期记忆用，越滚越大）；记忆全量保存（检索噪声）；无 TTL（过期偏好误导决策）；跨线程/Agent 记忆不共享（重复劳动与结论打架）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentMemory {
    record Memory(String content, String kind, long ttlDays, double score) {
    }

    /** 三层记忆：各自的生命周期 */
    static Map<String, List<Memory>> layers = new LinkedHashMap<>();

    static void threeLayers() {
        System.out.println("【三层记忆的生命周期】");
        layers.put("工作记忆", List.of(
                new Memory("已查库存=12", "本轮结论", 0, 1.0),
                new Memory("补货量建议 198", "本轮结论", 0, 1.0)));
        layers.put("会话记忆", List.of(
                new Memory("用户要求“只补爆款”", "本会话约束", 1, 0.9),
                new Memory("上一轮补货方案被否（超预算）", "本会话历史", 1, 0.8)));
        layers.put("长期记忆", List.of(
                new Memory("用户偏好：预算敏感，先报总价", "偏好", 180, 0.7),
                new Memory("项目 A 已下线", "事实", 30, 0.5)));
        for (Map.Entry<String, List<Memory>> e : layers.entrySet()) {
            System.out.println("  " + e.getKey() + "：");
            for (Memory m : e.getValue()) {
                System.out.printf("    [%s ttl=%dd 置信%.1f] %s%n",
                        m.kind(), m.ttlDays(), m.score(), m.content());
            }
        }
        System.out.println("  工作记忆随任务消亡；会话记忆随会话；长期记忆带 TTL 与置信度");
        System.out.println();
    }

    static void contextTrim() {
        System.out.println("【工作记忆裁剪：只留结论，丢弃过程】");
        List<String> fullObs = List.of(
                "查询返回 JSON：{\"sku\":\"A100\",\"stock\":12,\"daily_sales\":30,\"warehouses\":3...}",
                "calc(30*7-12) = 198，计算过程...", "check_budget(4950) 返回 {...}");
        List<String> trimmed = List.of(
                "库存=12，日均销量=30", "建议补货 198 件", "预算通过（4950/5000）");
        System.out.printf("  原始 Observation 合计约 %d token，裁剪后约 %d token%n",
                fullObs.stream().mapToInt(String::length).sum() * 2,
                trimmed.stream().mapToInt(String::length).sum() * 2);
        System.out.println("  原则：数字与结论保留，JSON 过程丢弃（需要时重查）");
        System.out.println();
    }

    static void retrieval() {
        System.out.println("【长期记忆检索注入：按任务相关性取 top K】");
        String task = "为 A100 制定补货方案";
        record Candidate(String content, double rel, boolean expired) {
        }
        List<Candidate> cands = List.of(
                new Candidate("用户偏好：预算敏感，先报总价", 0.82, false),
                new Candidate("爆款补货需走审批流", 0.78, false),
                new Candidate("去年双11补货记录", 0.41, false),
                new Candidate("项目 A 部署规范（已下线）", 0.33, true));
        List<String> injected = new ArrayList<>();
        for (Candidate c : cands) {
            if (c.rel() > 0.6 && !c.expired()) {
                injected.add(c.content());
            }
        }
        System.out.println("  任务：" + task);
        System.out.println("  注入 system 的已知信息：");
        for (String s : injected) {
            System.out.println("    - " + s);
        }
        System.out.println("  过期记忆被 TTL 拦截：项目 A 已下线，注入只会误导");
        System.out.println();
    }

    public static void main(String[] args) {
        threeLayers();
        contextTrim();
        retrieval();
    }
}
```

**课后练习**
1. 实现三层记忆的最小框架（工作/会话/长期），完成"任务结束 -> 总结沉淀 -> 下次任务检索注入"闭环，验证记忆价值。
2. 给长期记忆加 TTL 与置信度衰减（每过 30 天 score×0.9，低于 0.3 清理），用模拟时间轴验证失效行为。
3. 思考题：为什么"什么都存的记忆等于没有记忆"？从检索噪声与上下文成本两个角度量化记忆的负价值。

---

### 44-04 多智能体协作

**知识点**
- **多智能体的价值是"分工与制衡"**：单一 Agent 上下文有限、角色混乱（又写又审）；拆成多个专职 Agent——规划者、执行者、审查者——每个上下文干净、职责单一。**制衡（审查者否决执行者）是质量保障的核心**。
- **三种协作拓扑**：**流水线**（A -> B -> C 顺序传递，确定性最高）；**协调者 + 工人**（一个 orchestrator 分发任务收结果，最常用）；**辩论制**（两个 Agent 独立给答案再互相评审，用于高风险决策）。**拓扑越自由越难测试**。
- **通信即消息，不是共享大脑**：Agent 之间传递的是**结构化消息**（任务、结果、质疑），各自维护自己的上下文。共享的是"黑板"（44-03）而非全部记忆——**全量共享 = 上下文爆炸 + 互相污染**。
- **协调者的职责**：拆解任务、选择工人、校验结果、处理失败重试、聚合输出。**协调者本身也是 Agent，也要护栏**（最大轮数、预算）。协调者的失败模式：死循环派工（工人互相踢皮球）。
- **成本与延迟的乘积**：多智能体 = 多次模型调用（串行则延迟线性涨）。**能单 Agent 解决就别多 Agent**——多智能体的合理触发：任务可并行、需要独立视角制衡、单上下文装不下。
- **可测试性**：多智能体的确定性靠"固定拓扑 + mock 工人"——测试协调者时工人返回桩数据（40-09 的框架思想）。**拓扑不固定的多智能体无法回归测试**，等于上线赌博。
- 易错点：为炫技上多智能体（单 Agent 就够，成本×N）；Agent 间传自然语言长文本（信息丢失，应传结构化数据）；没有失败处理（一个工人挂了全链路卡死）；评审 Agent 与执行 Agent 用同一个模型同一提示（制衡失效，互相点头）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class MultiAgent {
    /** 协调者 + 工人 + 审查者 */
    record Msg(String from, String to, String type, String payload) {
    }

    static void orchestratorFlow() {
        System.out.println("【任务：生成周报（协调者 + 2 工人 + 1 审查者）】");
        List<Msg> msgs = List.of(
                new Msg("user", "orchestrator", "task", "生成订单系统周报"),
                new Msg("orchestrator", "worker-data", "subtask", "统计本周单量/GMV/错误率"),
                new Msg("worker-data", "orchestrator", "result", "单量 12.4 万，GMV 860 万，错误率 0.3%"),
                new Msg("orchestrator", "worker-analyst", "subtask", "对比上周，给出 3 条趋势解读"),
                new Msg("worker-analyst", "orchestrator", "result", "GMV +12%（大促预热）；错误率环比持平..."),
                new Msg("orchestrator", "reviewer", "review", "请校验数字与结论一致性"),
                new Msg("reviewer", "orchestrator", "verdict", "GMV 数字与明细一致；解读 2 与数据矛盾 -> 打回"),
                new Msg("orchestrator", "worker-analyst", "rework", "解读2 需重写（数据不支持）"),
                new Msg("worker-analyst", "orchestrator", "result", "解读2 修正：错误率持平但 P99 +8%..."),
                new Msg("orchestrator", "user", "final", "周报完成（经审查者确认）"));
        for (Msg m : msgs) {
            System.out.printf("  %-14s -> %-14s %-8s %s%n",
                    m.from(), m.to(), m.type(), m.payload());
        }
        System.out.println("  制衡生效：审查者打回 1 条与数据矛盾的解读");
        System.out.println();
    }

    static void topologyChoice() {
        System.out.println("【三种拓扑的确定性与适用】");
        String[][] rows = {
                {"流水线", "A->B->C 固定顺序", "确定性高，最易测试", "文档处理流水线"},
                {"协调者+工人", "动态分发，结构可控", "常用，需护栏", "多源调研、周报生成"},
                {"辩论制", "独立答案 + 互评", "成本高，适合高风险", "资金相关决策复核"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-22s %-16s %s%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println();
    }

    static void testability() {
        System.out.println("【多智能体的回归测试：mock 工人】");
        List<String> steps = List.of(
                "固定拓扑：协调者的分发规则写死（哪些子任务给谁）",
                "工人桩：测试协调者时工人返回预录结果（含失败样本）",
                "评审断言：协调者对“审查否决”必须重派而非直接放行",
                "边界样本：工人超时/返回空/返回畸形 JSON 的处理");
        for (String s : steps) {
            System.out.println("  - " + s);
        }
        System.out.println("  与 40-09 AI Services 的 mock 思想一致：拓扑固定才能测试");
    }

    public static void main(String[] args) {
        orchestratorFlow();
        topologyChoice();
        testability();
    }
}
```

**课后练习**
1. 实现协调者+工人的最小多智能体（模拟模型）：完成"周报生成"任务，验证审查者否决后的重派流程。
2. 用 mock 工人为协调者写 4 个测试用例（正常/工人超时/空结果/审查否决），全部通过后说明为什么拓扑必须固定。
3. 思考题：为什么"执行者与审查者用同一个模型同一提示"会让制衡失效？从偏差相关性的角度分析，并给出至少两个降低相关性的手段。
