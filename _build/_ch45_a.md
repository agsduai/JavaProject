### 45-01 为什么需要共识

**知识点**
- **复制的根本分歧问题是"谁说了算"**：三副本集群收到写请求时，如果两个副本各自认为自己是主，同一个键就会被写两次不同的值。**共识（consensus）就是让多个节点对"一个值"达成唯一决定**——它是复制、选主、分布式锁、配置中心的共同底座。
- **故障模型决定协议难度**：崩溃故障（节点停了就不再回来作恶）与拜占庭故障（节点可能撒谎）是两个世界。**数据中心内部只需要处理崩溃故障**——Raft/Paxos 的适用域；区块链处理拜占庭。选协议先问"我要防什么样的坏节点"。
- **分布式系统的敌人是异步网络**：消息可以丢失、延迟、乱序，节点时钟不可信，你无法区分"节点挂了"和"网络很慢"。**共识协议的全部复杂性都来自这个不确定性**——单机程序没有这个问题。
- **安全性与活性的取舍**：安全性（决不返回两个不同的值）是底线，任何网络分区下都不能违反；活性（最终能做出决定）在分区时可能暂停。**Raft 选择"分区时少数派停服，多数派继续"**——宁可不服务，不给错误答案。
- **共识在 Java 系统里的身影**：ZooKeeper/etcd（配置与协调）、Redis Cluster/ES master 选举（41/38 章的选主）、Kafka Controller、K8s 的 etcd 底座——**你已经天天在用共识，只是被封装了**。本章把它拆开看。
- **不要自己实现共识**："看起来简单的协议"（两阶段提交+超时切换）在分区下几乎必然产生脑裂。**正确姿势是用经过验证的库**（etcd/raft 库、ZooKeeper），自己写只存在于学习场景。
- 易错点：把"过半数确认"理解成"过半机器在线就行"（是过半的**投票**，不是机器活着）；在少数派分区里继续写（脑裂）；用共享存储当共识（存储挂了就没有共识）；以为 Raft 保证"所有请求都成功"（少数派请求会失败，客户端要重试到新主）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhyConsensus {
    /** 没有共识协议的"主备切换"：分区下的脑裂现场 */
    static void splitBrainDemo() {
        System.out.println("【朴素主备切换的脑裂：两台都认为自己是主】");
        record Node(String name, String role, String reason) {
        }
        // 网络分区：master 与 slave-1 一组，slave-2 单独一组
        List<Node> partition = List.of(
                new Node("master", "主", "心跳超时前仍是主"),
                new Node("slave-1", "主？", "探测到 master 失联，抢主"),
                new Node("slave-2", "主？", "同样探测到失联，也抢主"));
        for (Node n : partition) {
            System.out.printf("  %-8s %s（%s）%n", n.name(), n.role(), n.reason());
        }
        System.out.println("  结果：slave-1 与 slave-2 各自成为主，客户端写到两个不同的值");
        System.out.println("  根因：每个节点只依赖自己的观察（失联≠死亡），没有全局决定机制");
        System.out.println("  解法：用共识协议选主——只有获得过半投票的节点才能当主");
        System.out.println();
    }

    static void majorityRule() {
        System.out.println("【多数派投票：3 节点集群的分区表现】");
        record Scenario(String partition, String majoritySide, String outcome) {
        }
        List<Scenario> cases = List.of(
                new Scenario("A={n1,n2} | B={n3}", "A 侧 2/3", "A 侧继续选主并服务；B 侧 n3 拿不到过半，停服"),
                new Scenario("A={n1} | B={n2,n3}", "B 侧 2/3", "B 侧服务；n1 停服"),
                new Scenario("三边各 1 台", "无过半", "全部停服——宁可不服务，不给错误答案"));
        for (Scenario c : cases) {
            System.out.printf("  分区 %-16s 多数派=%s%n    %s%n", c.partition(), c.majoritySide(), c.outcome());
        }
        System.out.println("  安全性优先于可用性：这是共识协议与“脑裂备份”方案的本质区别");
        System.out.println();
    }

    static void consensusUses() {
        System.out.println("【Java 系统里的共识：你天天在用】");
        Map<String, String> uses = new LinkedHashMap<>();
        uses.put("ZooKeeper/etcd", "配置中心、选主协调（29 章）、K8s 元数据");
        uses.put("Redis Cluster", "slot 归属的主节点仲裁");
        uses.put("ES master 选举", "38-10 的 quorum 选举");
        uses.put("Kafka Controller", "分区 leader 的全局决定");
        uses.put("分布式锁", "39 章本地消息表的协调补充（45-08 细讲）");
        for (Map.Entry<String, String> e : uses.entrySet()) {
            System.out.printf("  %-14s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  共同点：都需要“多个节点对一个值达成唯一决定”");
        System.out.println();
    }

    static void failureModels() {
        System.out.println("【故障模型：决定协议选型】");
        String[][] rows = {
                {"崩溃故障", "节点宕机后不再恢复作恶", "Raft/Paxos/ZAB（数据中心标准）"},
                {"拜占庭故障", "节点可能返回任意错误结果", "PBFT 类（区块链、航天），开销大 1~2 个数量级"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-24s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  内网服务按崩溃故障设计即可；公网多方系统才考虑拜占庭");
    }

    public static void main(String[] args) {
        splitBrainDemo();
        majorityRule();
        consensusUses();
        failureModels();
    }
}
```

**课后练习**
1. 用两台（或两进程）模拟"朴素主备切换"，制造网络分区（不互相 ping），复现双主写冲突，记录数据分叉证据。
2. 把三节点集群的六种分区组合全部推演一遍（本章只列了三种），标注每侧的服务状态与原因。
3. 思考题：为什么"共享存储当共识"不可靠？从存储自身的可用性与的一致性要求推导。

---

### 45-02 CAP 与一致性谱系

**知识点**
- **CAP 的准确读法**：网络分区（P）发生时，**一致性（C）与可用性（A）只能二选一**——这不是"三选二"的选择题，而是"分区时你选哪边"的承诺。无分区时 C 和 A 可以兼得。**CAP 是分区预案的设计约束，不是架构选型的口号**。
- **一致性是一个谱系而非开关**：**线性一致**（读到的值与所有进程的操作顺序一致，像单机）> **顺序一致**（同客户端有序，跨客户端可乱）> **因果一致**（有因果关系的操作有序）> **最终一致**（停止写入后收敛）。**不同接口可以承诺不同级别**——同一系统里"配置读取"线性一致、"计数"最终一致很常见。
- **线性一致的代价**：每次读都要与多数派交互（或读主），延迟 = 一次 RTT 往上，吞吐受限。**只对真正需要的接口承诺线性一致**（选主、锁、配置变更确认），其余用本地读。
- **最终一致的业务后果要先想清楚**：余额查询最终一致 = 用户刚充 100 元查不到（客诉）；库存扣减最终一致 = 超卖。**"读自己的写"（read-your-writes）是最常见的最低要求**：用户提交后必须看到自己刚写的数据（39-04 sticky routing 的理论根基）。
- **BASE 与 ACID 的适用边界**：跨服务的业务流程用 BASE（最终一致 + 补偿，39-09 的本地消息表），单库事务仍用 ACID。**"放弃强一致"不是放弃正确性**，而是把正确性从"数据库保证"变成"架构保证"（幂等、对账、补偿）。
- **PACELC 补充了无分区时的取舍**：分区时选 C 或 A；**无分区时选延迟（L）或一致性（C）**——线性一致每次多一次 RTT，这就是"为什么默认读本地副本"的理论解释。
- 易错点：把 CAP 当"三选二"选型表（P 是必选项不是选项）；以为最终一致 = 数据可以随便错（收敛性与收敛时间是有契约的）；所有接口都上线性一致（延迟与吞吐双输）；把"读写主库"当成唯一解（读自己的写只需要会话粘性）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class CapSpectrum {
    record Read(String scenario, String level, String cost, String verdict) {
    }

    static void consistencyLadder() {
        System.out.println("【一致性谱系：同一系统允许分级承诺】");
        List<Read> reads = List.of(
                new Read("选主结果查询", "线性一致", "每次读多数派（+1 RTT）", "必须：脑裂的防线"),
                new Read("配置读取", "顺序一致", "读主或版本号校验", "变更后短暂窗口可接受"),
                new Read("商品详情页浏览数", "最终一致", "读本地副本（无额外代价）", "偏差分钟级，业务无感"),
                new Read("库存扣减后查询", "读自己的写", "会话粘性（39-04）", "用户感知的底线"));
        for (Read r : reads) {
            System.out.printf("  %-12s %-8s %-20s %s%n", r.scenario(), r.level(), r.cost(), r.verdict());
        }
        System.out.println("  原则：一致性级别按接口的业务后果定价，不整体一刀切");
        System.out.println();
    }

    static void partitionChoice() {
        System.out.println("【分区时的选择：CP 与 AP 的真实含义】");
        record SystemChoice(String name, String choice, String behavior, String scene) {
        }
        List<SystemChoice> systems = List.of(
                new SystemChoice("ZooKeeper/etcd", "CP", "少数派分区时拒绝服务", "配置与协调，错比停更糟"),
                new SystemChoice("Cassandra 类", "AP", "分区时两侧都可写，恢复后收敛", "社交流、计数类"),
                new SystemChoice("ES", "可调", "38-10：write 块与水位", "按索引用途权衡"));
        for (SystemChoice s : systems) {
            System.out.printf("  %-14s 选%s  %s%n      %s%n", s.name(), s.choice(), s.behavior(), s.scene());
        }
        System.out.println("  同一个公司里 CP 与 AP 系统并存：按数据类型选，不是全局站队");
        System.out.println();
    }

    static void pacelc() {
        System.out.println("【PACELC：无分区时延迟与一致性的交换】");
        double rtt = 1.5;    // ms，同机房一次往返
        List<String> rows = List.of(
                "线性一致读：+1 次多数派 RTT ≈ +" + rtt + "ms（P99 受最慢副本拖累）",
                "读主：0 额外 RTT，但主所在机房抖动时全量读受影响",
                "读本地副本：0 RTT，可能读到旧值（需要业务容忍）",
                "写路径：同步写多数派 = 1 RTT；异步复制 = 立即返回但可能丢");
        for (String r : rows) {
            System.out.println("  " + r);
        }
        System.out.println("  实践：把“哪些接口愿意多付 1 RTT”写进设计文档");
        System.out.println();
    }

    static void readYourWrites() {
        System.out.println("【读自己的写：最低一致性契约】");
        List<String> steps = List.of(
                "1) 用户提交订单 -> 写主库成功（版本 v17）",
                "2) 30ms 后用户刷新订单页 -> 若路由到从库（可能还在 v16）",
                "3) 会话粘性：写操作后 N 秒内该用户请求全走主库（39-04）",
                "4) 或版本校验：读到 v<17 就回主库重读");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  反例：充值 100 元后余额未变 —— 客诉率最高的“最终一致”误用");
    }

    public static void main(String[] args) {
        consistencyLadder();
        partitionChoice();
        pacelc();
        readYourWrites();
    }
}
```

**课后练习**
1. 为你的系统列出接口一致性分级表（≥6 个接口），标注级别、代价与业务理由，找出一个"可以降级"与一个"必须升级"的接口。
2. 模拟读自己的写：写主库后立刻读从库（复现旧值），实现会话粘性修复，记录客诉场景的消除过程。
3. 思考题：为什么"分区时 CP"的系统在大促高峰反而更稳？从"拒绝服务 vs 返回错误数据"的下游影响链分析。

---

### 45-03 Raft 领导者选举

**知识点**
- **Raft 把共识拆成三个可理解的问题**：**选主**（谁当 leader）、**日志复制**（leader 怎么让全员对操作顺序达成一致）、**安全**（新 leader 不能丢已提交的数据）。每个问题独立可测——这是 Raft 相比 Paxos 的最大工程价值。
- **三种角色与任期（term）**：Follower / Candidate / Leader。逻辑时钟是**任期号**（单调递增），一轮选举 = 一个任期。**任期的单调性是全局顺序的基石**：旧任期的消息一律拒绝——它替代了不可信的物理时钟（45-07 的伏笔）。
- **选举流程**：Follower 超时没听到 Leader 心跳 -> 自增任期、转 Candidate、给自己投票、向所有节点拉票 -> **获过半选票**则当选，立刻广播心跳确立权威 -> 未过半则等待随机超时后新一轮。**随机化选举超时**（150~300ms 抖动）避免两个节点同时发起导致选票分裂。
- **投票的安全约束**：每个节点**一个任期只投一票**（先到先得）；**只投给日志不落后于自己的候选者**（比较最后一条日志的 term+index）——这两条保证"当选者拥有全部已提交日志"。
- **脑裂不可能产生两个主**：过半票只有一个（3 节点需 2 票），两个 Candidate 都拿不到 2 票时谁也不当选，等下一轮。**45-01 的朴素方案缺的正是这个全局约束**。
- **选举风暴与心跳**：Leader 每隔心跳间隔（如 100ms）广播心跳抑制选举；网络抖动导致的频繁换届要监控（换届率是集群健康信号，42 章方法论）。
- 易错点：选举超时不随机（两节点同刻拉票死循环）；任期号用了物理时间（时钟回拨毁掉顺序，39-07/45-07 同款问题）；少数派分区里不断重试选举刷任期号（应静默等待恢复）；投完票还接受旧 term 的消息（安全漏洞）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RaftElection {
    enum Role {
        FOLLOWER, CANDIDATE, LEADER
    }

    /** 单节点状态：任期、投票、角色 */
    static class RaftNode {
        final String name;
        Role role = Role.FOLLOWER;
        int term = 0;
        Integer votedFor = null;       // 本任期投给谁（null 未投）
        int logLastTerm = 0;           // 本地最后一条日志的任期（安全比较用）

        RaftNode(String name, int logLastTerm) {
            this.name = name;
            this.logLastTerm = logLastTerm;
        }

        boolean canVoteFor(int candidateLogTerm) {
            // 只投给“日志不落后于我”的候选者（简化：只比 term）
            return votedFor == null && candidateLogTerm >= logLastTerm;
        }
    }

    static void electionRound() {
        System.out.println("【一轮选举：3 节点，n2 先超时】");
        List<RaftNode> cluster = List.of(
                new RaftNode("n1", 3), new RaftNode("n2", 4), new RaftNode("n3", 4));
        RaftNode n2 = cluster.get(1);
        n2.role = Role.CANDIDATE;
        n2.term = 5;
        n2.votedFor = 2;                       // 自己投自己（节点编号示意）
        Map<Integer, Integer> votes = new LinkedHashMap<>();
        votes.put(2, 1);
        System.out.println("  n2 超时 -> term=5 转 Candidate，投自己");
        for (RaftNode n : cluster) {
            if (n != n2) {
                boolean grant = n.canVoteFor(n2.logLastTerm);
                if (grant) {
                    n.term = 5;
                    n.votedFor = 2;
                }
                votes.put(cluster.indexOf(n), grant ? 1 : 0);
                System.out.printf("  n2 -> %s 拉票(term=5, logTerm=%d) -> %s%n",
                        n.name, n2.logLastTerm, grant ? "同意（votedFor=2）" : "拒绝（日志落后/已投）");
            }
        }
        int total = votes.values().stream().mapToInt(Integer::intValue).sum();
        boolean won = total >= 2;
        System.out.printf("  得票 %d/2 -> %s%n", total, won ? "n2 当选 LEADER（term=5），广播心跳" : "选举失败，随机超时后重选");
        System.out.println();
    }

    static void splitVote() {
        System.out.println("【选票分裂与随机超时的化解】");
        System.out.println("  n1、n3 同刻发起选举（term=6），各拿 1 票（自己 + 互投失败）");
        System.out.println("  双方都未过半 -> 各自等待随机超时（150~300ms 抖动）");
        int n1Delay = 180, n3Delay = 260;
        System.out.printf("  n1 先超时（%dms）-> term=7 重新拉票 -> 获得 2 票当选%n", n1Delay);
        System.out.printf("  n3 晚 %dms 收到 n1 心跳 -> 退回 Follower%n", n3Delay - n1Delay);
        System.out.println("  随机化让“同时发起”大概率自然化解——分布式去重的经典手法");
        System.out.println();
    }

    static void partitionBehavior() {
        System.out.println("【分区行为：旧主与新主】");
        System.out.println("  集群 {n1(旧leader,term=4), n2, n3}，n1 与 n2/n3 分区");
        List<String> lines = List.of(
                "n2/n3 侧：选举出新 leader（term=5），继续接收写入",
                "n1 侧：仍在 term=4 自认为是 leader，接收客户端写",
                "客户端写 n1：leader 只复制给自己，拿不到过半 -> 不提交（安全）",
                "n1 收到 n2 心跳（term=5 > 4）-> 立刻退位为 Follower，未提交数据回滚",
                "关键：过半机制保证任何时刻最多一个有效 leader");
        for (String s : lines) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void healthSignals() {
        System.out.println("【选举健康度监控（42 章方法论）】");
        List<String> signals = List.of(
                "换届频率：稳定集群应接近 0（>1 次/小时需排查）",
                "选举耗时：正常 < 500ms，P99 过高说明网络抖动",
                "拒绝票原因分布：日志落后（数据问题）vs 已投他人（时序问题）",
                "任期号跳变：突增说明反复选举");
        for (String s : signals) {
            System.out.println("  - " + s);
        }
    }

    public static void main(String[] args) {
        electionRound();
        splitVote();
        partitionBehavior();
        healthSignals();
    }
}
```

**课后练习**
1. 实现三节点 Raft 选举模拟器（线程 + 消息队列）：含随机超时、投票约束、心跳抑制，统计 1000 轮中的选举耗时分布。
2. 构造"两个节点同时发起选举"的场景，验证随机超时如何化解，并统计平均化解轮数。
3. 思考题：为什么"只投给日志不落后的候选者"是安全的？构造一个违反该约束导致已提交数据丢失的场景。

---

### 45-04 Raft 日志复制与安全

**知识点**
- **一切操作都先变成日志**：客户端请求到达 Leader -> 追加为日志条目（term+index）-> 并行复制给 Follower -> **过半节点落盘** -> Leader 提交并应用 -> 通知客户端成功 -> Follower 跟随提交。**日志就是全集群的唯一操作顺序**，状态机按序回放日志得到一致状态。
- **提交规则是安全的核心**：只有**当前任期**的条目被过半复制才能直接提交；提交当前条目会**间接提交**之前所有已复制条目（Raft 安全性论文的经典补丁——旧任期条目不能靠计数直接提交，否则已提交数据可能被覆盖）。
- **日志匹配性质**：两节点日志在相同 index 上 term 相同，则该位置之前的所有日志完全相同（归纳保证）。AppendEntries 自带一致性检查：Follower 缺失/冲突时 Leader 回退 nextIndex 重发，**冲突的 Follower 条目被 Leader 覆盖**。
- **新 Leader 的安全保证**：当选者日志不落后于任何过半节点（45-03 投票约束）=> **已提交的条目必然在新 Leader 日志里**——这就是"提交过的数据永不丢"的推导链。
- **读请求的处理**：写走日志，读也要保证线性一致时不能只读本地——**ReadIndex**（leader 确认自己在当前 term 内仍是 leader：与多数派交换一次心跳）或 **Lease Read**（租约期内信任本地，45-08）。**"读不走日志所以没有一致性问题"是常见误解**。
- **性能工程**：日志条目合并（batch）、流水线化（不等上一条确认就发下一条）、follower 读（ReadIndex 下放）、快照压缩日志（快照点之前的日志可丢弃）。**etcd 的实践：写 QPS 上千就很可观**——共识不便宜，能不用就不用（缓存、无状态计算不进共识）。
- 易错点：commit 后才回复客户端（应过半落盘即可回复，apply 异步）；忽略"旧任期提交规则补丁"（自己实现时最易犯的安全 bug）；Follower 冲突日志直接追加不回退（日志分叉）；把大 value 塞进日志（建议日志只存命令引用，数据走外部存储）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RaftLogReplication {
    record Entry(int term, int index, String cmd, boolean committed) {
    }

    /** 三节点的日志状态 */
    static Map<String, List<Entry>> logs = new LinkedHashMap<>();

    static void replicate() {
        System.out.println("【一条写请求的复制与提交】");
        logs.put("leader", new ArrayList<>(List.of(new Entry(5, 1, "SET x=1", false))));
        logs.put("f1", new ArrayList<>());
        logs.put("f2", new ArrayList<>());
        Entry e = logs.get("leader").get(0);
        System.out.println("  客户端 -> leader：SET x=1 -> 追加日志 " + e.term() + "/" + e.index());
        logs.get("f1").add(new Entry(5, 1, "SET x=1", false));
        System.out.println("  f1 复制成功（1/2）");
        logs.get("f2").add(new Entry(5, 1, "SET x=1", false));
        System.out.println("  f2 复制成功（2/2 过半）-> 提交！");
        for (Map.Entry<String, List<Entry>> node : logs.entrySet()) {
            List<Entry> list = node.getValue();
            list.set(0, new Entry(e.term(), e.index(), e.cmd(), true));
            System.out.printf("  %s 日志：5/1 SET x=1 committed=%b%n", node.getKey(), list.get(0).committed());
        }
        System.out.println("  回复客户端成功；Follower 随后收到 commitIndex 同步");
        System.out.println();
    }

    static void commitRulePatch() {
        System.out.println("【提交规则补丁：为什么旧任期条目不能直接提交】");
        System.out.println("  场景：leader(旧term=4) 复制条目 idx=2 到 f1 后宕机");
        System.out.println("  f1 有 idx=2，但只有 1/3 -> 未提交；新 leader(term=5) 追加 idx=2 新值");
        System.out.println("  若旧条目可以按“已复制到 1 台”提交 -> 与新 leader 的 idx=2 冲突");
        List<String> rules = List.of(
                "规则1：只直接提交当前任期的条目（过半复制时）",
                "规则2：提交当前条目时，其之前的所有条目一并提交（间接提交）",
                "规则3：新 leader 必须先提交一条自己任期的 no-op，才能安全读");
        for (String r : rules) {
            System.out.println("  " + r);
        }
        System.out.println("  这是自实现 Raft 最常犯的安全错误（etcd/consul 论文都强调）");
        System.out.println();
    }

    static void conflictFix() {
        System.out.println("【日志冲突修复：一致性检查与回退】");
        List<Entry> leaderLog = List.of(
                new Entry(5, 1, "SET x=1", true),
                new Entry(5, 2, "SET x=2", false));
        List<Entry> staleLog = new ArrayList<>(List.of(
                new Entry(4, 1, "SET x=1", true),
                new Entry(3, 2, "SET y=9", false)));    // 分区期间旧 leader 写入的孤儿日志
        System.out.println("  leader 日志： " + leaderLog);
        System.out.println("  f2 旧日志：  " + staleLog + "（term=3 的 idx=2 是孤儿条目）");
        System.out.println("  AppendEntries(idx=2) 一致性检查失败（term 5≠3）");
        System.out.println("  leader 递减 nextIndex 重试 -> 发现 f2 的 idx=1 匹配");
        staleLog.remove(1);
        staleLog.add(new Entry(5, 2, "SET x=2", false));
        System.out.println("  覆盖 f2 的冲突条目 -> " + staleLog);
        System.out.println("  结论：以 leader 当前任期日志为准，分区侧孤儿数据被安全覆盖");
        System.out.println();
    }

    static void readPaths() {
        System.out.println("【读的三条路径与代价】");
        String[][] rows = {
                {"日志读", "读操作也写日志过半提交", "线性一致最强，写入翻倍，极少用"},
                {"ReadIndex", "leader 与多数派交换心跳确认权威后本地读", "线性一致，+1 RTT，etcd 默认串行读"},
                {"Lease Read", "租约期内信任本地时钟读", "0 RTT，依赖时钟有界偏移（45-08）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  “读不走日志就没问题”是误解：读也要确认 leader 权威");
    }

    public static void main(String[] args) {
        replicate();
        commitRulePatch();
        conflictFix();
        readPaths();
    }
}
```

**课后练习**
1. 扩展 45-03 的模拟器实现日志复制：三节点 + leader 挂掉 + 重新选举，验证"已提交条目在新 leader 日志中保留、未提交孤儿条目被覆盖"。
2. 实现 AppendEntries 一致性检查与 nextIndex 回退逻辑，用本章冲突场景跑通修复过程。
3. 思考题：为什么读路径也要确认 leader 权威？构造一个"陈旧 leader 直接读本地返回旧值"的时序并说明后果。
