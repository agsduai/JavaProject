### 41-04 制品管理与版本策略

**知识点**
- **制品（artifact）是流水线的"合同标的物"**：源码不是交付物，制品才是。制品库（Maven Central、Nexus、镜像仓库）存储不可变制品与元数据（版本、构建号、提交哈希、签名、漏洞扫描结果）。**"部署哪个版本"必须是一个可以准确回答的问题**。
- **版本号的语义（SemVer）**：`主.次.修订`（2.4.1）——主版本破坏兼容、次版本新增功能、修订只修 bug。加构建号与提交摘要形成完全可追溯版本：`2.4.1-142-a1b2c3d`。**" latest "不是版本**，是赌博。
- **制品晋升（promotion）不是重新构建**：同一个制品从 dev -> test -> staging -> prod，变的只有"部署在哪个环境"，制品本身一个字节不变。第 37 章 `digest 固定` 的原则在制品层同样成立。
- **不可变与防篡改**：已发布的制品禁止覆盖（`redeploy=false`）；只有 CI 机器账号有发布权限；制品签名 + 部署时验签。**同名同版本制品内容不同 = 供应链事故的温床**。
- **依赖治理**：每次构建拉最新 SNAPSHOT 依赖 = 构建不可重现。规范：依赖一律用 release 版本锁定；`mvn dependency:tree` 定期审计；传递依赖的漏洞靠 SBOM（软件物料清单）追踪。
- **保留策略**：制品库不是无限仓库——release 制品永久保留（回滚可能用到），SNAPSHOT 保留 30 天，过期清理。清理策略要写进运维文档，不然磁盘满在深夜。
- **回滚就是重新部署旧制品**：制品库保留的历史版本 + 不可变保证，让"回滚"只是部署 v-1 的制品，而不是"找到旧源码重新构建"（旧源码可能已经不在了）。
- 易错点：制品版本号用时间戳却没有提交摘要（无法对应源码）；本地 `mvn deploy` 手工发版（绕过 CI，制品来历不明）；同一版本号反复发布不同内容；把构建产物拷贝在跳板机目录当"制品库"（无签名无审计无保留策略）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArtifactRegistry {
    record Artifact(String version, String commit, String sha256, String stage) {
    }

    static Map<String, Artifact> registry = new LinkedHashMap<>();
    static final String KEY = "order-service";

    static void versionAnatomy() {
        System.out.println("【版本号的解剖：主.次.修订-构建-提交】");
        String v = "2.4.1-142-a1b2c3d";
        String[] p = v.split("-");
        System.out.printf("  %s%n", v);
        System.out.printf("  语义版本 %s（主=%s 次=%s 修订=%s）构建#%s 提交=%s%n",
                p[0], p[0].split("\\.")[0], p[0].split("\\.")[1], p[0].split("\\.")[2], p[1], p[2]);
        System.out.println("  反推：git checkout a1b2c3d + 构建#142 可复现该制品的构建过程");
        System.out.println("  latest 是指针不是版本：生产环境禁用");
        System.out.println();
    }

    static void promotion() {
        System.out.println("【制品晋升：同一制品，换个环境部署】");
        registry.put("2.4.1-142-a1b2c3d",
                new Artifact("2.4.1-142-a1b2c3d", "a1b2c3d", "sha256:9f2e...", "test"));
        Artifact a = registry.get("2.4.1-142-a1b2c3d");
        System.out.println("  test 环境验证通过：sha256=" + a.sha256());
        registry.put(a.version(), new Artifact(a.version(), a.commit(), a.sha256(), "staging"));
        System.out.println("  晋升 staging：版本不变、sha256 不变，只改 stage=" + a.version());
        registry.put(a.version(), new Artifact(a.version(), a.commit(), a.sha256(), "prod"));
        System.out.println("  部署 prod：部署脚本重新校验 sha256 -> 一致才放行");
        System.out.println("  对照组：重新构建 -> 依赖拉到新版 -> sha256 变了 -> 测试作废");
        System.out.println();
    }

    static void immutability() {
        System.out.println("【不可变与签名：同名同版本必须同内容】");
        record Upload(String version, String by, String result) {
        }
        List<Upload> uploads = List.of(
                new Upload("2.4.1-142", "ci-bot（流水线）", "接受：签名入库，redeploy=false"),
                new Upload("2.4.1-142", "developer-laptop", "拒绝：本地发版禁用，制品必须出自流水线"),
                new Upload("2.4.1-142", "ci-bot（重复上传）", "拒绝：已存在同名同版本，内容不可覆盖"));
        for (Upload u : uploads) {
            System.out.printf("  上传 %-12s by %-18s -> %s%n", u.version(), u.by(), u.result());
        }
        System.out.println("  部署前验签：签名不匹配 -> 拦截 + 告警（供应链防线）");
        System.out.println();
    }

    static void retention() {
        System.out.println("【保留策略与回滚的关系】");
        String[][] rows = {
                {"release 制品", "永久保留", "回滚 = 重新部署旧版本，依赖历史制品存在"},
                {"SNAPSHOT", "30 天", "开发期调试用，过期清理"},
                {"feature 分支产物", "7 天", "PR 验证制品，合入后无价值"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-8s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  清理是磁盘运维；保留是回滚保障——两条策略写进文档并自动化");
        System.out.println("  回滚演练：随机抽一个月前版本重新部署到 staging，验证仍可运行");
    }

    public static void main(String[] args) {
        versionAnatomy();
        promotion();
        immutability();
        retention();
    }
}
```

**课后练习**
1. 为你的制品设计完整版本号方案（含构建号与提交摘要），实现一个"提交哈希 -> 制品版本"的反查脚本。
2. 搭一个本地 Nexus/用目录模拟制品库，实现"上传签名、redeploy 拒绝、部署验签"三件事，演示覆盖上传被拦截。
3. 思考题：为什么制品晋升必须禁止重新构建？从依赖漂移与审计追溯两个角度说明后果。

---

### 41-05 环境晋升与配置管理

**知识点**
- **环境的本质是"不同信任级别的同一套系统"**：dev（开发者自由实验）-> test（自动化回归）-> staging（生产镜像，最后防线）-> prod（真实流量）。**环境数量按团队规模裁剪**，两环境（test+prod）是多数小团队的最优解，环境越多晋升越慢。
- **staging 的价值在于"像生产"**：同配置规模（或缩比）、同依赖版本、同数据结构（脱敏数据）、同拓扑。staging 与 prod 差异越大，通过 staging 的信心越低——"staging 测过了"不等于"生产没问题"，但差异可以管理。
- **配置与制品分离是环境晋升的前提**：同一个制品在 dev/test/prod 用不同配置（数据源、密钥、域名）。配置来源优先级：环境变量 > 配置中心 > 配置文件 > 代码默认值。**配置里绝不出现"环境 if-else"写在代码里**。
- **配置即资产，需要版本与审计**：配置中心（Nacos/Apollo）记录每次修改的人、时间、diff、灰度范围；敏感配置（密码/密钥）走 secret 管理（K8s Secret + KMS/Vault），**不进代码库不进聊天记录**。
- **晋升节奏与质量门槛**：环境晋升是流水线的阶段——test 绿灯才能进 staging，staging 冒烟绿灯才能进 prod。**每一跳都是自动的**，人工只出现在 prod 的最终放行（审批按钮）。
- **环境漂移（drift）是慢性病**：某人在 prod 机器上手工改了一个 nginx 参数，三个月后所有人都忘了——staging 复现不了、重建环境不对。对策：环境定义代码化（IaC）、变更一律走流水线、定期 diff 环境配置告警。
- 易错点：配置写在代码里用 profile 硬编码 if；密钥提交进 git（第 36 章的历史层教训同样适用）；staging 用生产真实数据（合规事故）；各环境版本不一致还互相拷贝配置文件。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnvironmentPromotion {
    record Env(String name, String trustLevel, String dataSource, String configSource, String gate) {
    }

    static void envMatrix() {
        System.out.println("【环境矩阵：同一制品，逐级晋升】");
        List<Env> envs = List.of(
                new Env("dev", "开发者自由实验", "内存/容器化DB", "本地默认值", "无（自测）"),
                new Env("test", "自动化回归", "脱敏子集", "配置中心 test 命名空间", "流水线全量绿灯"),
                new Env("staging", "生产镜像验证", "脱敏全量结构", "配置中心 staging", "冒烟 + 压测通过"),
                new Env("prod", "真实流量", "生产库", "配置中心 prod + Secret", "人工审批放行"));
        for (Env e : envs) {
            System.out.printf("  %-8s 数据源=%-14s 配置=%-24s 准入=%s%n",
                    e.name(), e.dataSource(), e.configSource(), e.gate());
        }
        System.out.println("  制品相同，配置按环境注入 —— 这是晋升语义的全部内容");
        System.out.println();
    }

    static void configPriority() {
        System.out.println("【配置优先级：环境变量 > 配置中心 > 文件 > 代码默认】");
        record Key(String key, String codeDefault, String center, String envVar, String actual) {
        }
        List<Key> keys = List.of(
                new Key("db.url", "jdbc:localhost", "jdbc:test-db:3306", "jdbc:prod-db:3306", "jdbc:prod-db:3306"),
                new Key("cache.ttl", "60", "300", null, "300"));
        for (Key k : keys) {
            String actual = k.envVar() != null ? k.envVar()
                    : k.center() != null ? k.center() : k.codeDefault();
            System.out.printf("  %-9s 代码=%-16s 中心=%-18s 环境变量=%-18s 生效=%s%n",
                    k.key(), k.codeDefault(), k.center(),
                    k.envVar() == null ? "-" : k.envVar(), actual);
        }
        System.out.println("  高优先级覆盖低优先级；优先级顺序全团队一致并有文档");
        System.out.println();
    }

    static void driftDetection() {
        System.out.println("【环境漂移：手工变更的三个月后】");
        record Drift(String where, String what, String found, String fix) {
        }
        List<Drift> drifts = List.of(
                new Drift("prod nginx", "手工加了 client_max_body_size 50m",
                        "staging 复现不了上传问题", "参数进 IaC，diff 告警"),
                new Drift("prod JVM", "手工改了 -Xmx 应急", "重启后配置回到镜像默认，问题复发",
                        "应急参数当天进配置中心"),
                new Drift("test DB", "手工改了表结构", "test 与 prod schema 校验失败",
                        "schema 变更走迁移脚本（41-09）"));
        for (Drift d : drifts) {
            System.out.printf("  %-10s %s%n    发现：%s%n    修复：%s%n",
                    d.where(), d.what(), d.found(), d.fix());
        }
        System.out.println("  原则：环境的每一次变更都走流水线，机器 diff 比人脑可靠");
    }

    public static void main(String[] args) {
        envMatrix();
        configPriority();
        driftDetection();
    }
}
```

**课后练习**
1. 为你的项目定义环境矩阵（各环境的数据/配置/准入门槛），指出当前缺失的一道门禁并落地。
2. 用配置中心（或环境变量 + 启动打印）验证配置优先级，故意在三处设置不同值，记录生效来源。
3. 思考题：为什么环境数量要克制？从晋升周期与"staging 信心"的平衡分析两环境方案的优势。

---

### 41-06 发布策略：滚动与蓝绿

**知识点**
- **发布策略回答的是"新旧版本怎么共存、怎么切流量、出问题怎么退"**。三种主流：滚动（逐批替换，资源省）、蓝绿（两套环境整体切换，切换最快）、金丝雀（小比例真实流量验证再放量，风险最小）。
- **滚动发布（rolling update）**：第 37 章 K8s 的 maxSurge/maxUnavailable 就是它。优点：资源需求少（不需要双倍容量）；缺点：新旧版本**共存期**要兼容——API 兼容、数据兼容、消息兼容。**共存期是滚动发布的全部难点**。
- **蓝绿发布（blue-green）**：绿集群部署新版、冒烟验证，负载均衡一次切流，蓝集群待命作为回滚。切换秒级、回滚秒级（切回去）。代价：**双倍资源**；数据层通常是共享的，所以**数据库变更必须在切换前完成且双向兼容**（41-09）。
- **共存期的兼容三件套**：① API 兼容（旧客户端能调用新服务：字段只增不改删）；② 数据兼容（新旧代码都能读写同一份数据：新字段可空、删列分三步）；③ 消息兼容（消费者能处理新消息格式：加字段容忍未知字段）。三者任一缺失，滚动/蓝绿都会在共存期出事故。
- **切换瞬间的会话与缓存**：会话状态不能存在本地内存（内存粘性会"粘"到旧版本）；缓存的序列化结构要兼容（旧版本缓存被新版本读到）。切换前清掉易变的本地状态。
- **回滚不是 re-deploy 旧包那么简单**：数据库变更已生效时，回滚代码不回滚数据（41-09 的双向兼容救你）；消息队列里积压的新格式消息回滚后还在消费。**回滚演练 = 代码回滚 + 数据兼容确认 + 缓存清理**。
- 易错点：滚动发布直接改字段语义（旧实例读到新语义数据）；蓝绿切换忘了清会话缓存（用户看到旧版页面数据）；蓝绿共享数据库却做了破坏性 DDL（切回蓝直接崩）；发布窗口选在高峰期（共存期负载翻倍）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class RollingBlueGreen {
    record Instance(String name, String version, boolean ready, int trafficPct) {
    }

    static void rolling() {
        System.out.println("【滚动发布：maxSurge=1, maxUnavailable=0，逐批替换】");
        List<Instance> fleet = new ArrayList<>(List.of(
                new Instance("pod-1", "v1", true, 25),
                new Instance("pod-2", "v1", true, 25),
                new Instance("pod-3", "v1", true, 25),
                new Instance("pod-4", "v1", true, 25)));
        for (int i = 0; i < 4; i++) {
            fleet.set(i, new Instance("pod-" + (i + 1), "v2", true, 25));
            System.out.printf("  step%d: 替换 %s -> v2，可用容量始终 4/4%n", i + 1, fleet.get(i).name());
        }
        System.out.println("  共存期：pod-1(v2) 与 pod-4(v1) 同时接流量 —— API/数据/消息必须兼容");
        System.out.println();
    }

    static void blueGreen() {
        System.out.println("【蓝绿发布：整套切换，回滚秒级】");
        List<String> steps = List.of(
                "step1 蓝集群(v1)承载 100% 流量，绿集群(v2)部署完成并冒烟通过",
                "step2 LB 切流：蓝 0% -> 绿 100%（秒级，切前清本地会话/缓存）",
                "step3 观察 30 分钟：错误率/SLO 达标",
                "step4 绿转正；蓝保留 24h 作回滚（切回 = LB 指回蓝）",
                "step5 确认稳定后回收蓝集群资源");
        for (String s : steps) {
            System.out.println("  " + s.replace("%", "%"));
        }
        System.out.println("  代价：双倍资源；数据库必须切换前完成双向兼容迁移");
        System.out.println();
    }

    static void compatibilityChecklist() {
        System.out.println("【共存期兼容三件套：缺一必出事故】");
        String[][] items = {
                {"API 兼容", "字段只增不改不删；删字段先停用再下线（两版发布）", "旧客户端调新服务"},
                {"数据兼容", "新字段可空；改语义分三步走（加列->双写->切读）", "新旧实例读写同一份数据"},
                {"消息兼容", "消费者容忍未知字段；格式变更加版本号", "队列里新旧消息混杂"},
        };
        for (String[] i : items) {
            System.out.printf("  %-8s %-42s 场景：%s%n", i[0], i[1], i[2]);
        }
        System.out.println();
    }

    static void rollbackRehearsal() {
        System.out.println("【回滚演练清单】");
        List<String> checks = List.of(
                "代码回滚：切回旧制品（蓝绿=切流，滚动=回退 Deployment）",
                "数据确认：确认没有破坏性 DDL；有则回滚被数据迁移卡住",
                "缓存清理：本地会话清空；Redis 序列化结构兼容确认",
                "消息确认：新格式消息回滚后消费不报错",
                "时间线：回滚全程 < 2 分钟（演练实测并记录）");
        for (String c : checks) {
            System.out.println("  [ ] " + c);
        }
    }

    public static void main(String[] args) {
        rolling();
        blueGreen();
        compatibilityChecklist();
        rollbackRehearsal();
    }
}
```

**课后练习**
1. 在你的 K8s 环境（或 minikube）真实执行一次滚动发布，观察 maxSurge/maxUnavailable 的容量曲线，记录共存期长度。
2. 设计一次蓝绿发布方案：容量规划、切流步骤、回滚预案、数据兼容确认，其中"切流后清理本地会话"的具体实现必须写清楚。
3. 思考题：为什么蓝绿发布对数据库变更的要求比滚动更严格？从"切换瞬间数据必须已被新旧两个版本共同理解"推导。

---

### 41-07 发布策略：金丝雀与灰度

**知识点**
- **金丝雀（canary）的本质是用真实流量做实验**：新版本先接 1%~5% 流量，观察核心指标，达标逐步放量 1% -> 5% -> 25% -> 50% -> 100%。它与蓝绿的区别：蓝绿是"整体切换+快速回退"，金丝雀是"渐进放量+统计验证"——**风险与流量比例成正比**。
- **灰度的维度选择**：按流量比例（随机）、按用户尾号（体验一致，同用户始终同版本）、按渠道（内部员工先试）、按地域（单机房先试）。**按用户维度灰度要求会话粘性**，否则同一用户在新旧版本间跳来跳去。
- **放量判据必须是统计指标**：错误率、P99 延迟、业务转化率的对比基线是**旧版本同期**，不是历史均值。判据写进自动化：错误率相对基线 +2% 持续 5 分钟 -> 自动暂停放量并告警；+10% -> 自动回退。**没有自动判据的金丝雀只是手工蓝绿**。
- **放量节奏与观察窗口**：每档至少观察一个完整业务周期（含早晚高峰），跳档太快=没观察到周期性差异。数据差异（如缓存命中率）需要更长窗口。
- **金丝雀与监控的联动**（第 42 章）：按版本维度拆分指标（`version=2.4.1`），错误率/延迟/业务指标全部带版本标签——没有版本维度指标的灰度是盲飞。
- **金丝雀的局限**：1% 流量暴露不了长尾问题（特定账号、特定数据形态）；缓存预热差异会让金丝雀"天然更慢"造成误判；有状态服务（本地缓存）的金丝雀要考虑数据迁移。
- 易错点：金丝雀只看"有没有报错"不看延迟与业务指标；放量节奏拍脑袋（高峰期猛放量）；灰度维度与产品预期不一致（运营想按渠道、技术按尾号，没人对齐）；自动回退阈值没设或太宽（出错 20 分钟才人工反应）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class CanaryRelease {
    record Step(int pct, String action, String evidence) {
    }

    static void rolloutPlan() {
        System.out.println("【金丝雀放量计划：1% -> 5% -> 25% -> 50% -> 100%】");
        List<Step> plan = List.of(
                new Step(1, "内部账号定向接入", "冒烟 + 主链路埋点正常"),
                new Step(5, "随机 5% 流量", "错误率 vs 基线 +0.3%，观察 2h"),
                new Step(25, "放量至 25%", "P99 与基线持平，观察 1 个完整高峰"),
                new Step(50, "放量至 50%", "缓存命中率回归正常"),
                new Step(100, "全量", "转正，金丝雀实例并入常驻"));
        for (Step s : plan) {
            System.out.printf("  %3d%%  %-16s %s%n", s.pct(), s.action(), s.evidence());
        }
        System.out.println();
    }

    static void autoJudge() {
        System.out.println("【自动判据：相对基线的阈值决策】");
        record Observation(int pct, double errRate, double baseline, String decision) {
        }
        List<Observation> obs = List.of(
                new Observation(5, 0.12, 0.10, "相对基线 +0.02（+2%）：暂停 5 分钟，观察"),
                new Observation(25, 0.19, 0.10, "相对基线 +0.09：超过 +5% -> 停止放量，告警"),
                new Observation(25, 0.35, 0.10, "相对基线 +0.25：超过 +10% -> 自动回退"));
        for (Observation o : obs) {
            double delta = o.errRate() - o.baseline();
            System.out.printf("  流量%d%% 错误率%.2f%%（基线%.2f%%，+%.2f%%）-> %s%n",
                    o.pct(), o.errRate(), o.baseline(), delta, o.decision());
        }
        System.out.println("  阈值必须相对基线（同期旧版本），绝对值没有意义");
        System.out.println();
    }

    static void grayDimension() {
        System.out.println("【灰度维度：会话粘性决定用户体验】");
        String[][] dims = {
                {"流量比例", "随机打散", "同用户可能新旧版本跳变"},
                {"用户尾号", "尾号%10 < 灰度值", "同用户稳定同版本（需粘性路由）"},
                {"渠道", "App 先灰、Web 后灰", "按业务影响面控制"},
                {"地域/机房", "单机房先灰", "基础设施风险隔离"},
        };
        for (String[] d : dims) {
            System.out.printf("  %-10s %-22s %s%n", d[0], d[1], d[2]);
        }
        System.out.println("  选型必须与产品/运营对齐（他们关心的是谁先看到新功能）");
        System.out.println();
    }

    static void limits() {
        System.out.println("【金丝雀暴露不了的问题】");
        List<String> items = List.of(
                "长尾账号：1% 流量可能不含某个特定数据形态的用户",
                "缓存预热差异：金丝雀冷缓存天然更慢 -> 看稳态指标不看冷启动",
                "低频功能：退款流程一天 20 次，1% 灰度可能一次都没触发 -> 低频功能用测试环境覆盖",
                "有状态实例：本地缓存的金丝雀要回答「数据在谁那里」");
        for (String s : items) {
            System.out.println("  - " + s);
        }
        System.out.println("  结论：金丝雀不是万能，它与测试环境、监控、回滚演练是组合拳");
    }

    public static void main(String[] args) {
        rolloutPlan();
        autoJudge();
        grayDimension();
        limits();
    }
}
```

**课后练习**
1. 为你的服务设计完整的金丝雀方案：灰度维度、放量档位、每档观察时长、自动判据（相对基线）与回退动作。
2. 实现自动判据模拟器：给定逐分钟错误率序列（新旧版本），按"暂停/告警/回退"三级阈值输出决策日志。
3. 思考题：为什么判据必须用"相对基线"而不是"绝对错误率阈值"？举一个业务高峰期绝对值天然升高导致误判的例子。
