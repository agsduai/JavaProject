### 45-09 Quorum 读写与仲裁

**知识点**
- **Quorum（仲裁）是不走共识协议时保证一致性的简化机制**：N 副本，写入需 W 个确认，读取需 R 个确认，**W + R > N 保证读写集合必有交集**——读到至少一个最新副本。Dynamo/Cassandra 族的核心参数。
- **参数的物理含义**：N=3, W=2, R=2（标准配置）——容忍 1 副本故障仍可读写，读写各付一次多数派代价；W=1, R=3（写快读慢，有脏读窗口）；W=3, R=1（读快写慢）。**调节 W/R = 调节延迟与一致性曲线**（45-02 PACELC 的直接应用）。
- **Quorum 不等于线性一致**：W+R>N 只保证"读到至少一份最新数据"，**如果读到的恰好是旧副本**（交集里有最新也有旧），仍可能返回旧值——Dynamo 用向量时钟（45-07）+ 读修复解决：全部副本读回、比较版本、取最新并写回落后副本。
- **读修复与反熵**：读时发现副本版本不一致 -> 立即修复（read repair）；后台周期性同步（anti-entropy/merkle tree）。**最终一致的收敛机制**——"会收敛"不是靠等待，是靠这两个主动机制。
- ** sloppy quorum 与降级**：指定副本不可达时允许"附近的"副本代替（Dynamo 的 sloppy quorum）——提高分区时可用性，代价是一致性窗口扩大。**是否开启是业务决策**。
- **与共识的关系**：共识（Raft）= quorum + leader + 日志顺序 + 安全约束，是"管理型的过半"；纯 quorum 系统无 leader、允许并发冲突（交给向量时钟）。**需要严格顺序选共识，容忍冲突合并选 quorum**。
- 易错点：把 R+W>N 当线性一致（还要版本比较与修复）；只调大 N 不调 W/R（副本多但读写都是 1，一致性没变）；读修复缺失（最终一致永不收敛）；Sloppy quorum 默认开（一致性窗口悄悄变大）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
```

**课后练习**
1. 实现三副本 quorum 存储（W/R 参数化 + 版本比较 + 读修复），构造"读到旧副本"场景验证修复收敛。
2. 用模拟器统计不同 W/R 组合下的读写延迟与旧值概率（注入随机副本延迟），画出交换曲线。
3. 思考题：为什么写集合也要过半（W>N/2）？构造两个并发写各自拿 W=1 写不同副本导致分叉的场景。

---

### 45-10 共识的工程代价

**知识点**
- **共识不便宜，先算代价再上**：每次写至少一次多数派 RTT（同机房 1~2ms，跨机房 30ms+）+ 多数派 fsync（磁盘毫秒级）。**etcd 单集群写 QPS 上千是健康极限**——共识集群是"协调者"不是"数据主库"。
- **集群规模的收益递减**：3 节点容忍 1 故障，5 节点容忍 2——每加一台，写路径都要联系更多人，**延迟与故障概率同步上升**。生产 3 或 5 台是甜蜜点，7 台以上要有特殊理由。
- **跨机房部署的取舍**：三个机房各一台（任一机房挂仍有过半）但写延迟 = 最慢机房 RTT；同机房三台（快）但机房故障全挂。**PC-EC 判断：机房级容灾 > 毫秒级延迟时才跨机房**，或用"两层架构"（本地快集群 + 异步跨机房镜像）。
- **共识成员变更的危险性**：在线改集群成员（3->5）本身需要共识保证——配置错误瞬间可能没有过半（新旧配置各 3 台互不相认）。**joint consensus 两阶段变更**是安全做法；运维上更常用"停写 + 一次性替换"。
- **日志增长与快照**：日志无限增长 -> 快照压缩（状态机快照 + 截断日志）-> 恢复可以走快照 + 追日志。**快照频率与大小**影响恢复时长，也是 43-05 checkpoint 思想的一致。
- **监控与告警**（42 章方法论）：commit 耗时 P99、fsync 慢日志、换届频率、成员健康、磁盘水位——**共识集群挂了会拖垮依赖它的所有系统（K8s/配置/锁），必须最高优先级监控**。
- 易错点：把业务数据写进共识集群（容量与吞吐双爆炸）；跨机房配置了共识却不接受写延迟（用户感知到"慢"）；在线成员变更无两阶段（分区分裂风险）；无快照（重启恢复要重放亿级日志）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ConsensusCost {
    static void latencyMath() {
        System.out.println("【写延迟的物理构成】");
        double rttSameRack = 0.3, rttCrossAz = 1.8, fsync = 1.2;
        double sameRoom = 2 * rttSameRack + fsync;      // leader->2 follower 并行 + 落盘
        double crossAz = 2 * rttCrossAz + fsync;
        System.out.printf("  同机房 3 节点：2×%.1fms(RTT) + %.1fms(fsync) ≈ %.1fms%n",
                rttSameRack, fsync, sameRoom);
        System.out.printf("  跨可用区：2×%.1fms + %.1fms ≈ %.1fms（慢 %.1f 倍）%n",
                rttCrossAz, fsync, crossAz, crossAz / sameRoom);
        System.out.println("  结论：共识集群放同机房，跨机房用异步镜像（45-11 案例）");
        System.out.println();
    }

    static void clusterSize() {
        System.out.println("【集群规模：容忍度与代价】");
        String[][] rows = {
                {"1", "0 故障", "无共识可言（单点）"},
                {"3", "1 故障", "最小生产配置，写联系 2 台"},
                {"5", "2 故障", "滚动维护更从容，写联系 3 台"},
                {"7", "3 故障", "写延迟与故障概率上升，需特殊理由"},
        };
        for (String[] r : rows) {
            System.out.printf("  %s 台：容忍 %s 故障，%s%n", r[0], r[1], r[2]);
        }
        System.out.println("  甜蜜点：3 或 5。规模不是越多越可靠——写路径人数是代价");
        System.out.println();
    }

    static void membershipChange() {
        System.out.println("【成员变更：危险的在线操作】");
        List<String> steps = List.of(
                "危险做法：直接把配置从 {n1,n2,n3} 改成 {n1,n4,n5}",
                "  -> 旧配置 3 台与新配置 3 台同时存在，任一侧都可能“过半”",
                "  -> 两个 leader 并存的窗口（脑裂）",
                "安全做法一：joint consensus 两阶段（先联合配置 C_old,new，再切 C_new）",
                "安全做法二（运维常用）：停写 -> 一次性替换成员 -> 验证 -> 恢复");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  K8s etcd 扩容的“官方姿势”就是保守路线：能停就停，不能停走 joint");
        System.out.println();
    }

    static void usageDiscipline() {
        System.out.println("【使用纪律：什么该进共识，什么不该】");
        List<String> dos = List.of(
                "该进：选主结果、锁/租约、配置元数据、集群成员表、调度决策",
                "不该进：业务订单数据（走主库+复制）、高频计数（走 Redis+对账）、");
        System.out.println("  " + dos.get(0));
        System.out.println("  " + dos.get(1));
        System.out.println("  大 value 处理：日志只存引用（对象存 OSS，元数据进共识）");
        System.out.println("  判断式：这个数据需要“全局唯一顺序”吗？不需要就不进共识");
    }

    public static void main(String[] args) {
        latencyMath();
        clusterSize();
        membershipChange();
        usageDiscipline();
    }
}
```

**课后练习**
1. 用模拟器统计 3/5/7 节点在正常与单节点故障下的写延迟分布，量化"规模的代价"。
2. 演示"直接改成员配置"的脑裂窗口：模拟新旧配置各 3 台各自选举，验证双主可能；再实现 joint consensus 两阶段。
3. 思考题：为什么 etcd 官方推荐"专用节点"而不是与业务容器混部？从磁盘延迟、噪声邻居与换届的关系分析。

---

### 45-11 综合案例：配置中心的高可用设计

**知识点**
- **把本章共识知识收束成一个生产组件**：配置中心（29 章已用过 Nacos 视角）的底座设计——**Raft 集群存配置元数据（45-03/04）+ MVCC 与 watch 推送（45-06）+ 分级读（45-02）+ 变更审计（41 章思想）+ 客户端本地快照兜底**。
- **写路径**：控制台变更 -> 写请求 -> Raft 过半提交 -> 版本递增 -> 长轮询/gRPC 流推送变更到客户端 -> 客户端落地本地快照。**推送失败靠客户端定期全量拉齐兜底**（推拉结合）。
- **读路径分级**：业务进程启动时全量拉取（线性一致读一次）；运行中读本地缓存（微秒级，最终一致）；配置变更后热更新（watch 推送 + 版本号确认）。**业务热路径零共识开销**——共识只发生在配置变更瞬间。
- **容灾设计**：Raft 集群挂 -> 客户端用**本地快照继续跑**（配置不常变，旧配置可用性高于准确性——PACELC 的业务化选择）；恢复后按版本对齐。**"客户端自持快照"是配置中心与传统数据库的最大区别**。
- **脑裂防护**：客户端只接受**版本号单调递增**的配置（fencing 思想，45-08）——分区期旧实例的推送（旧版本）被拒；同版本冲突靠 hash 校验。
- **灰度与回滚**：配置变更按实例灰度（41-07 金丝雀思想）+ 一键回滚到任意历史版本（MVCC 天然支持）+ 变更审计（谁改的、改了什么、谁审批的）。
- 易错点：客户端本地快照缺失（配置中心挂 = 全公司进程起不来）；推送依赖长轮询无超时兜底（推送丢失后配置永久不更新）；热更新不校验 schema（一个 typo 全线崩）；配置变更无灰度直接全量（错误的连接串推到全部实例）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigCenterCase {
    /** 服务端：Raft 底座上的配置表 + 版本 */
    static class ConfigServer {
        final Map<String, String> data = new LinkedHashMap<>();
        long version = 0;
        final List<String> audit = new ArrayList<>();

        void put(String key, String value, String operator) {
            // 简化：假设已过半提交
            data.put(key, value);
            version++;
            audit.add(String.format("v%d %s 改 %s=%s", version, operator, key, value));
            System.out.printf("  [服务端] v%d 提交：%s=%s（操作人 %s）%n", version, key, value, operator);
        }

        String readLinear() {
            return "v" + version;
        }
    }

    /** 客户端：本地快照 + 版本单调 + 推拉结合 */
    static class ConfigClient {
        final Map<String, String> local = new LinkedHashMap<>();
        long localVersion = 0;

        void bootstrap(ConfigServer server) {
            local.putAll(server.data);
            localVersion = server.version;
            System.out.printf("  [客户端] 启动全量拉取：%s（线性一致读 %s）%n",
                    local, server.readLinear());
        }

        void onPush(String key, String value, long version) {
            if (version <= localVersion) {
                System.out.printf("  [客户端] 拒绝 v%d（本地已是 v%d）—— fencing 防旧实例推送%n",
                        version, localVersion);
                return;
            }
            local.put(key, value);
            localVersion = version;
            System.out.printf("  [客户端] 热更新 v%d：%s=%s + 落地本地快照%n", version, key, value);
        }

        void pullFallback(ConfigServer server) {
            if (server.version > localVersion) {
                onPush(null, null, server.version);
            }
        }
    }

    static void fullFlow() {
        System.out.println("【一次配置变更的完整链路】");
        ConfigServer server = new ConfigServer();
        server.put("db.url", "jdbc:mysql://db-a:3306/order", "ops-wang");
        ConfigClient client = new ConfigClient();
        client.bootstrap(server);
        System.out.println("  —— 10 分钟后修改连接串 ——");
        server.put("db.url", "jdbc:mysql://db-b:3306/order", "ops-wang");
        client.onPush("db.url", server.data.get("db.url"), server.version);
        System.out.println("  业务热路径全程未碰共识集群（本地缓存读取）");
        System.out.println();
    }

    static void disasterRecovery() {
        System.out.println("【容灾：Raft 集群挂了业务怎么办】");
        List<String> timeline = List.of(
                "T0 Raft 集群 3 台全挂（机房故障）",
                "T1 客户端推送失败 -> 静默重试，继续用本地快照服务",
                "T2 业务进程照常启动（新实例读本地快照文件）",
                "T3 恢复后按版本对齐：本地 v42 < 服务端 v43 -> 全量拉取",
                "T4 审计复盘：确认快照落盘策略（每版本落盘 + 保留 5 份）");
        for (String t : timeline) {
            System.out.println("  " + t);
        }
        System.out.println("  设计哲学：配置的可用性（旧配置能跑）优先于绝对最新——业务化 PACELC");
        System.out.println();
    }

    static void checklist() {
        System.out.println("【配置中心上线检查单】");
        List<String> items = List.of(
                "Raft 3~5 台专用部署，SSD，监控 commit P99 与换届",
                "客户端启动全量 + 运行热更新 + 定期兜底拉齐",
                "本地快照落盘（版本单调/fencing），防旧实例推送",
                "变更审计 + 按历史版本一键回滚（MVCC）",
                "schema 校验：变更先过格式与连接性检查再提交",
                "灰度：按实例比例生效，观察指标再全量",
                "演练：杀掉全部服务端节点，验证业务自持快照存活");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        fullFlow();
        disasterRecovery();
        checklist();
    }
}
```

**课后练习**
1. 实现配置中心迷你版（服务端 + 客户端）：版本单调校验、本地快照落盘、拉齐兜底，并完成"杀掉全部服务端"演练记录。
2. 给配置变更加 schema 校验与灰度（按实例尾号生效），用错误的 JDBC 串演示"校验拦截"与"灰度爆炸半径限制"。
3. 思考题：为什么配置中心客户端"自持快照"是它的可用性核心？对比 43-05 流作业 checkpoint 与 45-08 fencing，说明三者在"状态自持 + 版本防御"上的共同思想。
