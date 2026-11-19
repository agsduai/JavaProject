### 79-01 研发效能全景

**知识点**
- **DORA 四个指标**：**部署频率**（多久发一次）、**变更前置时间**（commit 到上线）、**变更失败率**（发布导致故障的比例）、**恢复时长 MTTR**——**前两个看速度，后两个看稳定性**，必须成对看。
- **价值流（Value Stream）**：**从需求到上线拆成若干阶段**，**每阶段记录"处理时间"与"等待时间"**——**真正拖慢交付的往往是等待（排队/评审/环境）而不是编码**。
- **效能不能只看"工作量"**：**代码行数、故事点、工时**都是"活动指标"，**容易造假且与价值无关**；**要看向"流动效率"**（处理时间/总时间）与"交付结果"。
- **瓶颈识别**：**价值流中等待时间最长的阶段就是瓶颈**——**优化非瓶颈阶段不会提升整体吞吐**（约束理论）。
- 与 41 章的关系：41 章 CI/CD 讲"流水线怎么建"，**本章讲"流水线建得好不好、瓶颈在哪"**——**从工具到度量**。
- 易错点：只追部署频率（忽视失败率）；用代码行数考核（鼓励臃肿）；优化编码（瓶颈常在评审/测试/环境）；把"忙"当"高效"。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DevOpsMetrics {
    record Deploy(int day, boolean success, double leadTimeHours, double restoreMinutes) {
    }

    record Stage(String name, double processHours, double waitHours) {
        double totalHours() {
            return processHours + waitHours;
        }
    }

    static void dora(List<Deploy> deploys) {
        int days = 30;
        long success = deploys.stream().filter(Deploy::success).count();
        double deployFreq = (double) deploys.size() / days;
        double avgLead = deploys.stream().mapToDouble(Deploy::leadTimeHours).average().orElse(0);
        double failureRate = deploys.isEmpty() ? 0
                : (double) (deploys.size() - success) / deploys.size();
        double mttr = deploys.stream().filter(d -> !d.success())
                .mapToDouble(Deploy::restoreMinutes).average().orElse(0);

        System.out.println("【DORA 四指标】");
        System.out.printf("  部署频率：%.2f 次/天（%.1f 次/月）%n", deployFreq, deployFreq * days);
        System.out.printf("  变更前置时间：%.1f 小时%n", avgLead);
        System.out.printf("  变更失败率：%.1f%%%n", failureRate * 100);
        System.out.printf("  平均恢复时长：%.0f 分钟%n", mttr);
        System.out.println();
    }

    static void valueStream(List<Stage> stages) {
        double total = 0;
        double process = 0;
        Stage bottleneck = stages.get(0);
        for (Stage s : stages) {
            total += s.totalHours();
            process += s.processHours();
            if (s.waitHours() > bottleneck.waitHours()) {
                bottleneck = s;
            }
        }
        System.out.println("【价值流】");
        for (Stage s : stages) {
            double efficiency = s.totalHours() == 0 ? 0 : s.processHours() / s.totalHours();
            System.out.printf("  %-10s 处理=%5.1fh 等待=%5.1fh 流动效率=%4.0f%%%n",
                    s.name(), s.processHours(), s.waitHours(), efficiency * 100);
        }
        System.out.printf("  总时长=%.1fh，处理=%.1fh，整体流动效率=%.1f%%%n",
                total, process, process / total * 100);
        System.out.printf("  瓶颈：%s（等待 %.1fh 最长）%n", bottleneck.name(), bottleneck.waitHours());
        System.out.println();
    }

    public static void main(String[] args) {
        List<Deploy> deploys = new ArrayList<>();
        for (int d = 1; d <= 30; d++) {
            boolean ok = d % 10 != 0;                // 每 10 次失败 1 次
            double lead = 18 + (d % 5) * 3;
            double restore = ok ? 0 : 40 + (d % 3) * 10;
            deploys.add(new Deploy(d, ok, lead, restore));
        }
        dora(deploys);

        List<Stage> stages = List.of(
                new Stage("需求评审", 4, 20),
                new Stage("编码", 24, 6),
                new Stage("代码评审", 2, 18),
                new Stage("测试", 8, 30),
                new Stage("发布", 1, 4));
        valueStream(stages);

        Map<String, String> actions = new LinkedHashMap<>();
        actions.put("瓶颈", "测试等待 30h -> 环境按需创建（79-06）+ 自动化回归");
        actions.put("次瓶颈", "需求评审等待 20h -> 需求模板 + 评审 SLA");
        actions.put("编码", "24h 处理时间不是瓶颈，加人收益有限（约束理论）");
        System.out.println("【改进优先级】");
        actions.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));

        System.out.println();
        System.out.println("【结论】优化瓶颈才提升吞吐；先度量流动效率，再谈工具");
    }
}
```

**课后练习**
1. 用你团队近 30 次发布的数据算 DORA 四指标，判断"快而不稳"还是"稳而不快"。
2. 画一条价值流（需求到上线），标出每阶段处理/等待时间，找出瓶颈并给出改进项。
3. 思考题：为什么"优化非瓶颈阶段"不提升整体吞吐？用约束理论解释。

---

### 79-02 脚手架与模板工程

**知识点**
- **脚手架的价值不是"省几行代码"，而是"把最佳实践固化"**：**目录结构、依赖版本、日志/配置/健康检查、CI 文件**一次生成，**新服务天然合规**（79-09 的 Golden Path）。
- **模板三要素**：**参数**（服务名/包名/owner）、**占位符**（`${serviceName}`）、**条件块**（是否需要数据库/缓存）——**参数校验在生成前完成**（缺参数直接报错，不生成半成品）。
- **模板的两种形态**：**文件模板**（整个工程目录 + 占位符替换）与**代码模板**（生成单个类/方法）——**脚手架用前者**，IDE 用后者。
- **生成即校验**：**生成后编译/单测必须通过**（否则模板腐烂没人用）——**模板要纳入 CI**（定期生成一个样例工程并构建）。
- 与 71 章的关系：71 章低代码是"元数据驱动运行时"，**脚手架是"元数据驱动代码生成"**——**一个生成配置，一个生成代码**。
- 易错点：模板里写死版本（升级要改所有生成物）；不校验参数（生成非法包名）；模板不维护（生成物编译不过）；生成后不注册到服务目录（找不到 owner）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Scaffolding {
    record TemplateFile(String path, String content) {
    }

    record Request(String serviceName, String packageName, String owner,
                   boolean withDatabase, boolean withCache) {
    }

    static List<String> validate(Request req) {
        List<String> errors = new ArrayList<>();
        if (!req.serviceName().matches("^[a-z][a-z0-9-]{2,30}$")) {
            errors.add("serviceName 必须是 3~31 位小写字母/数字/连字符");
        }
        if (!req.packageName().matches("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$")) {
            errors.add("packageName 不是合法 Java 包名");
        }
        if (req.owner() == null || req.owner().isBlank()) {
            errors.add("owner 必填（服务目录需要责任人）");
        }
        return errors;
    }

    static Map<String, String> render(Request req) {
        Map<String, String> files = new LinkedHashMap<>();

        files.put("pom.xml", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>${serviceName}</artifactId>
                  <version>0.1.0-SNAPSHOT</version>
                </project>
                """.replace("${serviceName}", req.serviceName()));

        String pkgPath = req.packageName().replace('.', '/');
        files.put("src/main/java/" + pkgPath + "/Application.java", """
                package ${packageName};

                public class Application {
                    public static void main(String[] args) {
                        System.out.println("${serviceName} started, owner=${owner}");
                    }
                }
                """.replace("${packageName}", req.packageName())
                .replace("${serviceName}", req.serviceName())
                .replace("${owner}", req.owner()));

        files.put("src/main/resources/application.yml", """
                app:
                  name: ${serviceName}
                  owner: ${owner}
                ${databaseBlock}${cacheBlock}""".replace("${serviceName}", req.serviceName())
                .replace("${owner}", req.owner())
                .replace("${databaseBlock}", req.withDatabase()
                        ? "  datasource:\n    url: jdbc:mysql://localhost:3306/" + req.serviceName() + "\n"
                        : "")
                .replace("${cacheBlock}", req.withCache()
                        ? "  cache:\n    type: redis\n"
                        : ""));

        files.put("README.md", """
                # ${serviceName}

                - owner: ${owner}
                - 由脚手架生成，遵循团队 Golden Path
                """.replace("${serviceName}", req.serviceName())
                .replace("${owner}", req.owner()));

        files.put(".github/workflows/ci.yml", """
                name: ci
                on: [push]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - run: mvn -q verify
                """);

        return files;
    }

    public static void main(String[] args) {
        System.out.println("【参数校验】");
        Request bad = new Request("X", "com.1bad", "", false, false);
        List<String> errors = validate(bad);
        System.out.println("  非法请求：" + errors);

        System.out.println();
        Request good = new Request("order-service", "com.example.order", "交易团队",
                true, true);
        List<String> ok = validate(good);
        System.out.println("  合法请求校验结果：" + (ok.isEmpty() ? "通过" : ok));

        System.out.println();
        System.out.println("【生成文件】");
        Map<String, String> files = render(good);
        files.keySet().forEach(p -> System.out.println("  " + p));

        System.out.println();
        System.out.println("【application.yml】");
        System.out.println(files.get("src/main/resources/application.yml"));

        System.out.println("【生成后动作】");
        String[][] steps = {
                {"编译", "mvn -q verify", "模板不腐烂的底线"},
                {"注册", "写入服务目录", "owner/分级/文档（79-09）"},
                {"建流水线", "套用流水线模板", "79-07"},
                {"建环境", "按需创建 dev", "79-06"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-8s %-22s %s%n", s[0], s[1], s[2]);
        }
    }
}
```

**课后练习**
1. 设计一个脚手架参数（服务名/包名/owner/是否用数据库），实现参数校验与三个文件的生成。
2. 给模板加"条件块"（有缓存才生成 Redis 配置），验证开关生效。
3. 思考题：为什么"模板必须纳入 CI 定期生成并编译"？从"模板腐烂"与"开发者信任"分析。

---

### 79-03 构建缓存与增量构建

**知识点**
- **构建缓存的本质：输入不变则输出可复用**——**缓存键 = 源码内容 + 依赖版本 + 编译参数 + 环境（JDK 版本）的哈希**；**任何输入变化都必须换 key**（否则拿到错误的旧产物）。
- **本地缓存 vs 远程缓存**：**本地**（单机 `.m2`/`build-cache`）命中快；**远程**（团队共享）让"别人构建过的"直接复用——**CI 的加速主要来自远程缓存**。
- **增量构建**：**只重建"受影响的模块"**（79-04 的依赖图），**未变化的模块直接取缓存**——**monorepo 的关键优化**。
- **缓存失效的三种原因**：**输入变了**（正常）、**key 设计漏了输入**（错误命中，最危险）、**缓存被清理**（性能回退）——**key 要"宁可多算，不可错用"**。
- 与 41 章的关系：41 章 CI 流水线讲"每次全量构建"，**本章讲"怎么让流水线从 20 分钟降到 2 分钟"**。
- 易错点：key 只哈希源码不哈希依赖（依赖升级拿到旧产物）；缓存不设过期（磁盘爆）；不同分支共享可变缓存（污染）；缓存命中率不监控。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BuildCache {
    record Task(String module, List<String> sources, List<String> deps,
                String compilerArgs, String jdkVersion) {
    }

    /** 缓存键：所有影响产物的输入一起哈希 */
    static String cacheKey(Task task) {
        StringBuilder sb = new StringBuilder();
        task.sources().forEach(s -> sb.append("src:").append(s).append('\n'));
        task.deps().forEach(d -> sb.append("dep:").append(d).append('\n'));
        sb.append("args:").append(task.compilerArgs()).append('\n');
        sb.append("jdk:").append(task.jdkVersion).append('\n');
        return Integer.toHexString(sb.toString().hashCode());
    }

    static class Cache {
        final Map<String, String> local = new LinkedHashMap<>();
        final Map<String, String> remote = new LinkedHashMap<>();
        long localHits;
        long remoteHits;
        long misses;
        long builds;

        String build(Task task) {
            String key = cacheKey(task);
            if (local.containsKey(key)) {
                localHits++;
                System.out.printf("  %-12s 本地命中 key=%s%n", task.module(), key);
                return local.get(key);
            }
            if (remote.containsKey(key)) {
                remoteHits++;
                local.put(key, remote.get(key));
                System.out.printf("  %-12s 远程命中 key=%s（下载到本地）%n", task.module(), key);
                return remote.get(key);
            }
            misses++;
            builds++;
            String artifact = task.module() + "-" + key + ".jar";
            System.out.printf("  %-12s 未命中，执行构建 key=%s -> %s%n",
                    task.module(), key, artifact);
            local.put(key, artifact);
            remote.put(key, artifact);
            return artifact;
        }

        void report() {
            long total = localHits + remoteHits + misses;
            System.out.printf("  总计 %d 次：本地命中 %d，远程命中 %d，构建 %d，命中率 %.0f%%%n",
                    total, localHits, remoteHits, builds,
                    (double) (localHits + remoteHits) / total * 100);
            System.out.printf("  实际构建次数 %d（省下 %d 次）%n", builds, total - builds);
        }
    }

    public static void main(String[] args) {
        Task common = new Task("common", List.of("A.java", "B.java"),
                List.of("guava:33.0"), "-parameters", "21");
        Task order = new Task("order", List.of("O.java"),
                List.of("common:1.0", "guava:33.0"), "-parameters", "21");
        Task user = new Task("user", List.of("U.java"),
                List.of("common:1.0", "guava:33.0"), "-parameters", "21");

        Cache cache = new Cache();
        System.out.println("【第一次构建（冷缓存）】");
        cache.build(common);
        cache.build(order);
        cache.build(user);
        cache.report();

        System.out.println();
        System.out.println("【第二次构建（无变更）】");
        cache.build(common);
        cache.build(order);
        cache.build(user);
        cache.report();

        System.out.println();
        System.out.println("【只改 order 的一个源文件】");
        Task order2 = new Task("order", List.of("O.java", "O2.java"),
                List.of("common:1.0", "guava:33.0"), "-parameters", "21");
        cache.build(common);                        // 未变 -> 命中
        cache.build(order2);                        // 变了 -> 重建
        cache.build(user);                          // 未变 -> 命中
        cache.report();

        System.out.println();
        System.out.println("【依赖升级：guava 33 -> 34】");
        Task order3 = new Task("order", List.of("O.java", "O2.java"),
                List.of("common:1.0", "guava:34.0"), "-parameters", "21");
        System.out.println("  key 变化 -> " + cacheKey(order2).equals(cacheKey(order3)));
        cache.build(order3);

        System.out.println();
        System.out.println("【要点】key 要覆盖源码/依赖/参数/JDK；远程缓存是 CI 加速主力");
        System.out.println("  最危险的 bug：key 漏了输入 -> 错误命中旧产物");
    }
}
```

**课后练习**
1. 实现缓存键（源码 + 依赖 + 编译参数 + JDK 版本），验证"只改一个源文件"只有该模块重建。
2. 故意从 key 中移除依赖版本，复现"依赖升级却命中旧产物"的错误。
3. 思考题：为什么"远程缓存"是 CI 加速的关键？从"同一提交被多人/多次构建"分析。

---

### 79-04 Monorepo 与依赖图

**知识点**
- **Monorepo 的核心能力是"影响分析"**：**改了模块 A，只构建/测试"依赖 A 的模块"**（affected），**而不是全仓构建**——**依赖图是这一切的基础**。
- **依赖图的两种边**：**编译依赖**（A 依赖 B 的 API）与**测试/运行依赖**——**影响分析要沿"反向边"传播**（谁依赖我，谁受影响）。
- **拓扑排序**：**构建顺序必须"被依赖者先构建"**；**有环说明模块耦合错了**（应该抽公共模块或改依赖方向）——**环检测是 CI 的必备门禁**。
- **与 79-03 的关系**：**affected 集合 + 构建缓存** = monorepo 增量构建的全部秘密——**先算影响面，再查缓存，最后只构建没命中的**。
- 与 31 章的关系：31 章讲"设计模式与重构"，**依赖方向（稳定依赖稳定）是架构原则**；monorepo 的依赖图是它的可视化。
- 易错点：全仓构建（慢）；影响分析只看直接依赖（漏传递依赖）；有环不报错（构建顺序随机）；不区分编译/测试依赖（过度构建）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MonorepoGraph {
    /** module -> 它依赖的模块 */
    static final Map<String, List<String>> deps = new LinkedHashMap<>();

    static {
        deps.put("common", List.of());
        deps.put("auth", List.of("common"));
        deps.put("order", List.of("common", "auth"));
        deps.put("payment", List.of("common", "order"));
        deps.put("web", List.of("order", "payment", "auth"));
        deps.put("report", List.of("common"));
    }

    /** 反向图：module -> 依赖它的模块 */
    static Map<String, List<String>> reverse() {
        Map<String, List<String>> rev = new LinkedHashMap<>();
        deps.keySet().forEach(m -> rev.put(m, new ArrayList<>()));
        for (Map.Entry<String, List<String>> e : deps.entrySet()) {
            for (String d : e.getValue()) {
                rev.get(d).add(e.getKey());
            }
        }
        return rev;
    }

    /** 拓扑排序：被依赖者在前 */
    static List<String> topoOrder() {
        List<String> order = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> done = new LinkedHashSet<>();
        for (String m : deps.keySet()) {
            dfs(m, visiting, done, order);
        }
        return order;
    }

    static void dfs(String m, Set<String> visiting, Set<String> done, List<String> order) {
        if (done.contains(m)) {
            return;
        }
        if (!visiting.add(m)) {
            throw new IllegalStateException("依赖环：" + visiting + " -> " + m);
        }
        for (String d : deps.getOrDefault(m, List.of())) {
            dfs(d, visiting, done, order);
        }
        visiting.remove(m);
        done.add(m);
        order.add(m);
    }

    /** 影响面：直接或间接依赖 changed 的模块 */
    static Set<String> affected(Set<String> changed) {
        Map<String, List<String>> rev = reverse();
        Set<String> out = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>(changed);
        while (!queue.isEmpty()) {
            String cur = queue.remove(0);
            for (String dependent : rev.getOrDefault(cur, List.of())) {
                if (out.add(dependent)) {
                    queue.add(dependent);
                }
            }
        }
        out.addAll(changed);
        return out;
    }

    public static void main(String[] args) {
        System.out.println("【依赖图】");
        deps.forEach((m, d) -> System.out.printf("  %-8s -> %s%n", m, d));

        System.out.println();
        System.out.println("【构建顺序（拓扑排序）】");
        System.out.println("  " + topoOrder());

        System.out.println();
        System.out.println("【影响分析】");
        for (String changed : List.of("common", "order", "report")) {
            Set<String> aff = affected(Set.of(changed));
            System.out.printf("  改 %-8s -> 需要构建 %d/%d 个模块：%s%n",
                    changed, aff.size(), deps.size(), aff);
        }

        System.out.println();
        System.out.println("【环检测】");
        deps.put("common", List.of("web"));          // 人为造环
        try {
            topoOrder();
        } catch (IllegalStateException e) {
            System.out.println("  " + e.getMessage() + "（CI 应直接失败）");
        }
        deps.put("common", List.of());

        System.out.println();
        System.out.println("【要点】拓扑排序定构建顺序；反向依赖算影响面；有环立即失败");
        System.out.println("  affected 集合再配合构建缓存（79-03），才是完整的增量构建");
    }
}
```

**课后练习**
1. 实现拓扑排序与环检测，验证有环时输出环路径并让 CI 失败。
2. 实现 affected 影响分析：改 common 时哪些模块需要重建，验证传递依赖不遗漏。
3. 思考题：为什么 monorepo 必须做影响分析？从"全仓构建时间随模块数线性增长"分析。

---

### 79-05 代码扫描与质量门禁

**知识点**
- **代码扫描的价值在"增量门禁"**：**存量问题不清零也先拦住"新增问题"**（基线机制）——**否则一上来几千条告警，团队直接忽略**。
- **规则分级**：**BLOCKER**（必须修：硬编码密钥、SQL 拼接）、**CRITICAL**（安全/正确性）、**MAJOR/MINOR**（可维护性）——**门禁只拦 BLOCKER/CRITICAL**，其余给建议。
- **规则实现三档**：**正则/文本匹配**（快但误报）、**AST 分析**（准但复杂）、**数据流分析**（污点追踪，最强）——**本章用正则演示门禁流程，生产用成熟工具**。
- **基线与豁免**：**基线按"规则 + 文件 + 代码片段"生成指纹**，**豁免要写原因与到期时间**（否则永久豁免）——**豁免也是技术债，要可见**。
- 与 36 章的关系：36 章讲"安全编码"，**本章讲"怎么用工具把安全规则自动化、拦住新增"**——**从知道到做到**。
- 易错点：全量扫描阻断（不敢用）；规则太多误报（被关闭）；豁免无期限（技术债堆积）；门禁只跑在 CI 不跑本地（反馈太晚）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class CodeScanGate {
    enum Severity { BLOCKER, CRITICAL, MAJOR, MINOR }

    record Rule(String id, String name, String regex, Severity severity) {
    }

    record Issue(String ruleId, String file, int line, String snippet, Severity severity) {
        String fingerprint() {
            return ruleId + ":" + file + ":" + snippet.trim().hashCode();
        }
    }

    static final List<Rule> rules = List.of(
            new Rule("SEC-01", "硬编码密码", "password\\s*=\\s*\"[^\"]+\"", Severity.BLOCKER),
            new Rule("SEC-02", "SQL 字符串拼接", "\"\\s*\\+\\s*\\w+\\s*\\+\\s*\"", Severity.CRITICAL),
            new Rule("LOG-01", "生产代码使用 System.out", "System\\.out\\.println", Severity.MINOR),
            new Rule("EXC-01", "捕获异常不处理", "catch\\s*\\(Exception\\s+\\w+\\)\\s*\\{\\s*\\}", Severity.MAJOR));

    static List<Issue> scan(Map<String, List<String>> files) {
        List<Issue> issues = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : files.entrySet()) {
            List<String> lines = e.getValue();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (Rule r : rules) {
                    if (Pattern.compile(r.regex()).matcher(line).find()) {
                        issues.add(new Issue(r.id(), e.getKey(), i + 1, line, r.severity()));
                    }
                }
            }
        }
        return issues;
    }

    /** 基线：历史已存在问题，门禁只拦"新增" */
    static Set<String> baseline(List<Issue> historical) {
        Set<String> out = new LinkedHashSet<>();
        historical.forEach(i -> out.add(i.fingerprint()));
        return out;
    }

    static boolean gate(List<Issue> current, Set<String> baseline) {
        List<Issue> newIssues = new ArrayList<>();
        for (Issue i : current) {
            if (!baseline.contains(i.fingerprint())) {
                newIssues.add(i);
            }
        }
        long blockers = newIssues.stream()
                .filter(i -> i.severity() == Severity.BLOCKER).count();
        long criticals = newIssues.stream()
                .filter(i -> i.severity() == Severity.CRITICAL).count();

        System.out.println("  当前问题 " + current.size() + " 条，基线豁免 "
                + (current.size() - newIssues.size()) + " 条，新增 " + newIssues.size() + " 条");
        for (Issue i : newIssues) {
            System.out.printf("    [%s] %s:%d %s -> %s%n",
                    i.severity(), i.file(), i.line(), i.ruleId(), i.snippet().trim());
        }
        System.out.printf("  门禁：BLOCKER=%d CRITICAL=%d -> %s%n",
                blockers, criticals, (blockers + criticals) > 0 ? "失败" : "通过");
        return blockers + criticals == 0;
    }

    public static void main(String[] args) {
        Map<String, List<String>> files = new LinkedHashMap<>();
        files.put("UserService.java", List.of(
                "public class UserService {",
                "  private String password = \"123456\";",
                "  public String find(String name) {",
                "    return jdbc.query(\"select * from user where name = '\" + name + \"'\");",
                "  }",
                "}"));
        files.put("OrderService.java", List.of(
                "public class OrderService {",
                "  public void run() {",
                "    try { work(); } catch (Exception e) { }",
                "    System.out.println(\"done\");",
                "  }",
                "}"));

        List<Issue> current = scan(files);
        System.out.println("【扫描结果】");
        current.forEach(i -> System.out.printf("  [%s] %s:%d %s%n",
                i.severity(), i.file(), i.line(), i.ruleId()));

        System.out.println();
        System.out.println("【第一次：无基线（存量全报）】");
        gate(current, Set.of());

        System.out.println();
        System.out.println("【建立基线：历史问题豁免】");
        Set<String> base = baseline(current);
        System.out.println("  基线条数：" + base.size());

        System.out.println();
        System.out.println("【第二次：无新增】");
        gate(current, base);

        System.out.println();
        System.out.println("【第三次：新增一个硬编码密码】");
        files.put("PayService.java", List.of(
                "public class PayService {",
                "  private String password = \"prod-secret\";",
                "}"));
        List<Issue> next = scan(files);
        gate(next, base);

        System.out.println();
        System.out.println("【要点】增量门禁 + 基线 + 豁免到期；BLOCKER/CRITICAL 必须拦住");
    }
}
```

**课后练习**
1. 实现四条扫描规则与增量门禁：存量豁免、新增 BLOCKER 直接失败。
2. 给豁免加"到期时间"字段，过期后重新变为新增问题。
3. 思考题：为什么"全量扫描阻断"会导致规则被关闭？从"存量债务 vs 增量门禁"分析。

---

### 79-06 环境即服务

**知识点**
- **环境是研发效能最大的等待源之一**：**测试环境排队、配置漂移、环境被占着不用**——**环境即服务（EaaS）把环境变成"按需申请、自动创建、到期回收"的资源**（79-01 的瓶颈）。
- **环境模板**：**一个环境 = 一组服务版本 + 配置 + 数据快照**——**模板化才能"一键复制"，避免手工搭环境**（79-02 的脚手架思想）。
- **状态机**：`REQUESTED -> PROVISIONING -> READY -> EXPIRING -> RECLAIMED`（失败 -> `FAILED`）——**每次状态迁移要可审计**；**TTL 到期自动回收**（防止资源泄漏）。
- **隔离级别**：**独占环境**（贵但干净）、**共享环境**（便宜但互相污染）、**按分支临时环境**（PR 环境）——**按测试类型选**。
- 与 37/41 章的关系：37 章 Docker 提供"环境一致性"，**本章讲"环境怎么按需供给与回收"**——**容器是砖，EaaS 是楼**。
- 易错点：环境手工创建（漂移）；不设 TTL（永远占用）；共享环境跑破坏性测试；环境与代码版本不对应（测了个旧版本）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnvironmentService {
    enum State { REQUESTED, PROVISIONING, READY, EXPIRING, RECLAIMED, FAILED }

    static class Environment {
        final String id;
        final String owner;
        final String template;
        final String version;
        State state = State.REQUESTED;
        long createdAt;
        long ttlMs;
        final List<String> services = new ArrayList<>();

        Environment(String id, String owner, String template, String version,
                    long createdAt, long ttlMs) {
            this.id = id;
            this.owner = owner;
            this.template = template;
            this.version = version;
            this.createdAt = createdAt;
            this.ttlMs = ttlMs;
        }

        long expiresAt() {
            return createdAt + ttlMs;
        }

        void report() {
            System.out.printf("  %-10s owner=%-6s template=%-10s version=%-8s state=%-12s 服务=%s%n",
                    id, owner, template, version, state, services);
        }
    }

    static final Map<String, Environment> envs = new LinkedHashMap<>();
    static long now = 1_000_000;
    static long nextId = 1;

    static Environment request(String owner, String template, String version, long ttlMs) {
        String id = "env-" + nextId++;
        Environment env = new Environment(id, owner, template, version, now, ttlMs);
        envs.put(id, env);
        System.out.printf("  申请 %s（%s/%s，TTL=%d 分钟）%n", id, template, version, ttlMs / 60_000);
        return env;
    }

    /** 推进：REQUESTED -> PROVISIONING -> READY（模拟异步创建） */
    static void tick() {
        for (Environment e : envs.values()) {
            if (e.state == State.REQUESTED) {
                e.state = State.PROVISIONING;
                e.services.add("mysql");
                e.services.add("redis");
                e.services.add("app:" + e.version);
                System.out.println("  " + e.id + " -> PROVISIONING（创建 " + e.services + "）");
            } else if (e.state == State.PROVISIONING) {
                e.state = State.READY;
                System.out.println("  " + e.id + " -> READY");
            }
        }
    }

    /** 回收：到期或手动释放 */
    static void reclaimExpired() {
        for (Environment e : envs.values()) {
            if (e.state == State.READY && now >= e.expiresAt()) {
                e.state = State.EXPIRING;
                System.out.println("  " + e.id + " 到期 -> EXPIRING");
                e.state = State.RECLAIMED;
                e.services.clear();
                System.out.println("  " + e.id + " -> RECLAIMED（资源已释放）");
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("【申请环境】");
        Environment dev = request("alice", "java-service", "1.4.2", 2 * 60 * 60 * 1000L);
        Environment pr = request("bob", "java-service", "1.5.0-rc1", 30 * 60 * 1000L);

        System.out.println();
        System.out.println("【异步创建】");
        tick();
        tick();

        System.out.println();
        System.out.println("【环境列表】");
        envs.values().forEach(Environment::report);

        System.out.println();
        System.out.println("【时间推进 40 分钟：PR 环境到期】");
        now += 40 * 60 * 1000L;
        reclaimExpired();
        envs.values().forEach(Environment::report);

        System.out.println();
        System.out.println("【时间推进 3 小时：dev 环境也到期】");
        now += 3 * 60 * 60 * 1000L;
        reclaimExpired();
        envs.values().forEach(Environment::report);

        System.out.println();
        System.out.println("【隔离级别】");
        String[][] levels = {
                {"独占", "整套服务独立部署", "贵、干净", "集成测试/压测"},
                {"共享", "多人共用一套", "便宜、会污染", "日常联调"},
                {"PR 环境", "每个 PR 一套，合并即回收", "中等", "代码评审验收"},
        };
        for (String[] l : levels) {
            System.out.printf("  %-8s %-26s %-14s %s%n", l[0], l[1], l[2], l[3]);
        }

        System.out.println();
        System.out.println("【要点】模板化 + 状态机 + TTL 回收 + 按测试类型选隔离级别");
    }
}
```

**课后练习**
1. 实现环境状态机（REQUESTED/PROVISIONING/READY/RECLAIMED），验证异步创建与到期回收。
2. 给环境加"续租"与"手动释放"操作，说明如何防止"占着不用"。
3. 思考题：为什么"PR 环境"能显著缩短评审等待？从"环境排队"与"验收前置"分析。

---

### 79-07 流水线编排与复用

**知识点**
- **流水线要"模板化 + 参数化"**：**同一套模板服务所有服务**（构建/测试/扫描/部署），**差异用参数表达**（语言/JDK/部署环境）——**避免每个服务复制一份 YAML**。
- **并行与矩阵**：**无依赖的 stage 并行**（lint/test/scan），**多环境用矩阵展开**（dev/staging/prod）——**总时长 = 关键路径**（最长链），不是所有 stage 之和。
- **stage 的依赖关系**：**DAG 而不是线性列表**——**构建 -> (单测 || 扫描) -> 镜像 -> (部署 dev || 部署 staging)**；**DAG 能表达"谁等谁"**（61-08 的工作流编排）。
- **复用机制**：**模板继承/组合**（include + 参数覆盖）、**共享步骤库**（build/test/deploy 原子步骤）——**复用程度决定维护成本**。
- 与 41 章的关系：41 章讲"CI/CD 的理念与发布策略"，**本章讲"流水线 DSL 与执行编排"**——**从流程到引擎**。
- 易错点：所有 stage 串行（慢）；并行无上限（打爆 runner）；矩阵爆炸（环境组合过多）；模板参数无校验（配错环境）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PipelineTemplate {
    record Stage(String name, long durationSec, String group) {
    }

    /** 模板展开：把 ${env} 替换成矩阵中的环境 */
    static List<Stage> expand(List<Stage> template, List<String> environments) {
        List<Stage> out = new ArrayList<>();
        for (Stage s : template) {
            if (s.name().contains("${env}")) {
                for (String env : environments) {
                    out.add(new Stage(s.name().replace("${env}", env), s.durationSec(), env));
                }
            } else {
                out.add(s);
            }
        }
        return out;
    }

    /** 计算总时长：同 group 并行取 max，其余串行相加 */
    static long totalDuration(List<Stage> stages) {
        long serial = 0;
        Map<String, Long> groups = new LinkedHashMap<>();
        for (Stage s : stages) {
            if (s.group() == null) {
                serial += s.durationSec();
            } else {
                groups.merge(s.group(), s.durationSec(), Math::max);
            }
        }
        long parallel = groups.values().stream().mapToLong(Long::longValue).sum();
        System.out.println("  串行部分=" + serial + "s，并行部分（按组取最大）=" + parallel + "s");
        groups.forEach((g, d) -> System.out.printf("    组 %-8s 最长 %ds%n", g, d));
        return serial + parallel;
    }

    static void execute(List<Stage> stages) {
        long serial = 0;
        Map<String, Long> groups = new LinkedHashMap<>();
        for (Stage s : stages) {
            if (s.group() == null) {
                serial += s.durationSec();
                System.out.printf("  [串行] %-22s %3ds%n", s.name(), s.durationSec());
            } else {
                groups.merge(s.group(), s.durationSec(), Math::max);
            }
        }
        groups.forEach((g, d) -> System.out.printf("  [并行] 组 %-8s 最长 %3ds%n", g, d));
        System.out.printf("  墙钟时间≈%ds（关键路径）%n", serial + groups.values().stream()
                .mapToLong(Long::longValue).sum());
    }

    public static void main(String[] args) {
        List<Stage> template = new ArrayList<>(List.of(
                new Stage("checkout", 5, null),
                new Stage("build", 60, null),
                new Stage("unit-test", 90, "test"),
                new Stage("code-scan", 40, "test"),
                new Stage("package", 30, null),
                new Stage("deploy-${env}", 45, null),
                new Stage("smoke-${env}", 20, null)));

        System.out.println("【模板】");
        template.forEach(s -> System.out.printf("  %-22s %3ds group=%s%n",
                s.name(), s.durationSec(), s.group()));

        System.out.println();
        System.out.println("【矩阵展开：dev / staging / prod】");
        List<Stage> stages = expand(template, List.of("dev", "staging", "prod"));
        stages.forEach(s -> System.out.printf("  %-22s %3ds group=%s%n",
                s.name(), s.durationSec(), s.group()));

        System.out.println();
        System.out.println("【执行编排】");
        execute(stages);
        System.out.println();
        System.out.println("【总时长】");
        long total = totalDuration(stages);

        System.out.println();
        System.out.println("【如果全部串行】");
        long allSerial = stages.stream().mapToLong(Stage::durationSec).sum();
        System.out.printf("  全部串行=%ds，编排后=%ds，节省 %.0f%%%n",
                allSerial, total, (1 - (double) total / allSerial) * 100);

        System.out.println();
        System.out.println("【要点】DAG 表达依赖；同组并行取最长；矩阵展开多环境；总时长看关键路径");
    }
}
```

**课后练习**
1. 实现模板展开（`${env}` 矩阵）与并行组时长计算，验证总时长等于关键路径。
2. 给 stage 加显式依赖（如 deploy 依赖 package），实现拓扑排序执行。
3. 思考题：为什么"并行度无上限"会拖慢整体？从 runner 资源竞争与关键路径分析。

---

### 79-08 制品与版本管理

**知识点**
- **制品（Artifact）是"可部署的不可变产物"**：**jar/镜像 + 版本号 + 构建元数据**（commit、构建号、时间）——**同一版本必须对应同一份内容**，**不可覆盖**。
- **语义化版本**：`MAJOR.MINOR.PATCH`——**MAJOR 不兼容、MINOR 向后兼容的新增、PATCH 修复**；**依赖范围**（`^1.2.0` 允许 1.x）——**版本比较是发布系统的基础能力**。
- **制品晋升（Promotion）**：**同一制品从 dev -> staging -> prod 逐级晋升**，**每级有质量门禁**（测试/扫描/审批）——**晋升的是"同一份制品"，不是"重新构建"**（重新构建会引入差异）。
- **可追溯性**：**从线上版本反查 commit/build/流水线/审批人**——**出问题能定位"这个包是谁、用什么代码、什么时候构建的"**。
- 与 41 章的关系：41 章讲"金丝雀/回滚"，**回滚的前提是"制品不可变 + 版本可追溯"**——**本章是发布的地基**。
- 易错点：每个环境重新构建（制品不一致）；版本覆盖（同一个 1.0.0 多次上传）；不做版本比较（`1.10` 被当成小于 `1.9`）；晋升无门禁（dev 直接上 prod）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArtifactPromotion {
    record Version(int major, int minor, int patch) implements Comparable<Version> {
        static Version parse(String s) {
            String[] parts = s.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("非法版本：" + s);
            }
            return new Version(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        }

        @Override
        public int compareTo(Version o) {
            if (major != o.major) {
                return Integer.compare(major, o.major);
            }
            if (minor != o.minor) {
                return Integer.compare(minor, o.minor);
            }
            return Integer.compare(patch, o.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    record Artifact(String name, Version version, String commit, String buildId,
                    String stage, boolean testsPassed, boolean scanPassed) {
        String coordinate() {
            return name + ":" + version;
        }
    }

    static final Map<String, Artifact> registry = new LinkedHashMap<>();

    static void publish(Artifact a) {
        String key = a.coordinate();
        if (registry.containsKey(key)) {
            throw new IllegalStateException("制品已存在，不可覆盖：" + key);
        }
        registry.put(key, a);
        System.out.printf("  发布 %-20s commit=%s build=%s stage=%s%n",
                key, a.commit(), a.buildId(), a.stage());
    }

    /** 晋升：同一制品换 stage，不重新构建 */
    static Artifact promote(Artifact a, String target) {
        List<String> order = List.of("dev", "staging", "prod");
        int cur = order.indexOf(a.stage());
        int next = order.indexOf(target);
        if (next != cur + 1) {
            System.out.printf("  非法晋升：%s -> %s（只能逐级）%n", a.stage(), target);
            return a;
        }
        if (!a.testsPassed() || !a.scanPassed()) {
            System.out.printf("  门禁失败：tests=%s scan=%s%n", a.testsPassed(), a.scanPassed());
            return a;
        }
        Artifact promoted = new Artifact(a.name(), a.version(), a.commit(), a.buildId(),
                target, a.testsPassed(), a.scanPassed());
        registry.put(promoted.coordinate() + "@" + target, promoted);
        System.out.printf("  晋升 %-20s %s -> %s（制品未重新构建）%n",
                a.coordinate(), a.stage(), target);
        return promoted;
    }

    public static void main(String[] args) {
        System.out.println("【版本比较】");
        List<Version> versions = new ArrayList<>(List.of(
                Version.parse("1.10.0"), Version.parse("1.9.0"), Version.parse("2.0.0"),
                Version.parse("1.9.1")));
        versions.sort(null);
        System.out.println("  排序：" + versions);
        System.out.println("  1.10.0 > 1.9.0 ? " + (Version.parse("1.10.0")
                .compareTo(Version.parse("1.9.0")) > 0));

        System.out.println();
        System.out.println("【发布】");
        Artifact a = new Artifact("order-service", Version.parse("1.5.0"),
                "a1b2c3d", "build-1001", "dev", true, true);
        publish(a);

        System.out.println();
        System.out.println("【重复发布同一版本】");
        try {
            publish(a);
        } catch (IllegalStateException e) {
            System.out.println("  " + e.getMessage());
        }

        System.out.println();
        System.out.println("【逐级晋升】");
        Artifact staging = promote(a, "staging");
        Artifact prod = promote(staging, "prod");
        System.out.println("  当前 prod：" + prod);

        System.out.println();
        System.out.println("【跳级晋升】");
        promote(a, "prod");

        System.out.println();
        System.out.println("【门禁失败】");
        Artifact bad = new Artifact("user-service", Version.parse("1.0.0"),
                "deadbee", "build-1002", "dev", true, false);
        publish(bad);
        promote(bad, "staging");

        System.out.println();
        System.out.println("【可追溯】");
        System.out.println("  prod 1.5.0 -> commit=a1b2c3d build=build-1001");
        System.out.println("  线上问题 -> 反查 commit -> 找到变更与审批人");

        System.out.println();
        System.out.println("【规律】同名同版本不可覆盖；晋升不重建；跳级/门禁失败被拒");
    }
}
```

**课后练习**
1. 实现语义化版本解析与比较，验证 `1.10.0 > 1.9.0`。
2. 实现"同一制品逐级晋升"，验证 dev 不能直接跳 prod，且门禁失败被拒。
3. 思考题：为什么"每个环境重新构建"是反模式？从"制品不一致 + 回滚不可信"分析。

---

### 79-09 开发者门户与 Golden Path

**知识点**
- **开发者门户（Developer Portal）是内部平台的"入口"**：**服务目录**（有哪些服务、谁负责、什么级别）、**自助文档**、**脚手架入口**、**环境申请**、**记分卡**——**把散落的工具收口到一个地方**。
- **Golden Path（黄金路径）**：**平台推荐的"默认正确做法"**——**脚手架 + 流水线模板 + 监控/日志默认接入 + 部署规范**；**走默认路径最省力，偏离要有理由**——**降低认知负担**。
- **服务记分卡（Scorecard）**：**用可自动检查的项给服务打分**（有 owner、有 runbook、有 SLO、有告警、有文档、依赖最新）——**分数不是考核，是改进引导**（79-10 的度量反模式）。
- **分级（Tier）**：**Tier1 核心**（高可用/严格 SLO）、**Tier2 重要**、**Tier3 一般**——**不同级别要求不同**，**避免"所有服务都按最高标准"**。
- 与 56 章的关系：56 章讲"稳定性工程"，**门户的记分卡是"稳定性要求的落地检查表"**——**从原则到可勾选项**。
- 易错点：门户只是"链接集合"（没有自助能力）；Golden Path 不维护（模板过期）；记分卡用来排名惩罚（团队抵触）；所有服务同一标准（资源浪费）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeveloperPortal {
    enum Tier { TIER1, TIER2, TIER3 }

    record Service(String name, String owner, Tier tier, boolean hasRunbook,
                   boolean hasSlo, boolean hasAlerts, boolean hasDocs, int outdatedDeps) {
    }

    record Score(Service service, int score, List<String> gaps) {
    }

    /** 记分卡：可自动检查的项，按 tier 加权 */
    static Score score(Service s) {
        int total = 0;
        int max = 0;
        List<String> gaps = new ArrayList<>();

        int ownerWeight = s.tier() == Tier.TIER1 ? 25 : 15;
        max += ownerWeight;
        if (s.owner() != null && !s.owner().isBlank()) {
            total += ownerWeight;
        } else {
            gaps.add("缺 owner");
        }

        int runbookWeight = s.tier() == Tier.TIER1 ? 25 : 15;
        max += runbookWeight;
        if (s.hasRunbook()) {
            total += runbookWeight;
        } else {
            gaps.add("缺 runbook");
        }

        int sloWeight = s.tier() == Tier.TIER1 ? 25 : 10;
        max += sloWeight;
        if (s.hasSlo()) {
            total += sloWeight;
        } else {
            gaps.add("缺 SLO");
        }

        int alertWeight = 15;
        max += alertWeight;
        if (s.hasAlerts()) {
            total += alertWeight;
        } else {
            gaps.add("缺告警");
        }

        int docWeight = 10;
        max += docWeight;
        if (s.hasDocs()) {
            total += docWeight;
        } else {
            gaps.add("缺文档");
        }

        if (s.outdatedDeps() > 5) {
            gaps.add("依赖过期 " + s.outdatedDeps() + " 个");
        } else {
            total += 10;
        }
        max += 10;

        return new Score(s, (int) Math.round(total * 100.0 / max), gaps);
    }

    public static void main(String[] args) {
        List<Service> services = new ArrayList<>(List.of(
                new Service("gateway", "架构组", Tier.TIER1, true, true, true, true, 0),
                new Service("order", "交易团队", Tier.TIER1, true, true, false, true, 2),
                new Service("report", "数据团队", Tier.TIER2, false, false, true, true, 8),
                new Service("demo", "个人", Tier.TIER3, false, false, false, false, 20)));

        System.out.println("【服务目录】");
        for (Service s : services) {
            System.out.printf("  %-8s owner=%-6s tier=%-6s runbook=%-5s slo=%-5s alerts=%-5s docs=%s%n",
                    s.name(), s.owner(), s.tier(), s.hasRunbook(), s.hasSlo(),
                    s.hasAlerts(), s.hasDocs());
        }

        System.out.println();
        System.out.println("【记分卡】");
        List<Score> scores = new ArrayList<>();
        services.forEach(s -> scores.add(score(s)));
        scores.sort(Comparator.comparingInt(Score::score).reversed());
        for (Score sc : scores) {
            System.out.printf("  %-8s %3d 分  %s%n", sc.service().name(), sc.score(),
                    sc.gaps().isEmpty() ? "无缺口" : "缺口：" + sc.gaps());
        }

        System.out.println();
        System.out.println("【Golden Path 检查】");
        String[][] path = {
                {"脚手架", "从模板生成，包名/目录/CI 就绪", "79-02"},
                {"流水线", "套用模板，含扫描与测试", "79-07"},
                {"可观测", "日志/指标/追踪默认接入", "42 章"},
                {"部署", "制品晋升 + 回滚预案", "79-08"},
                {"运维", "runbook + SLO + 告警", "56 章"},
        };
        for (String[] p : path) {
            System.out.printf("  %-8s %-30s %s%n", p[0], p[1], p[2]);
        }

        System.out.println();
        System.out.println("【原则】Golden Path 是默认最优解，不是强制；记分卡引导改进，不用于排名惩罚");
    }
}
```

**课后练习**
1. 实现服务记分卡（owner/runbook/SLO/告警/文档/依赖），按 Tier 加权并输出缺口。
2. 为 Tier1 服务设计 Golden Path 检查项，说明每项对应的稳定性要求（56 章）。
3. 思考题：为什么"记分卡用于排名惩罚"会失败？从 Goodhart 定律与团队行为分析。

---

### 79-10 效能度量与反模式

**知识点**
- **Goodhart 定律**：**"当一个指标成为目标，它就不再是好指标"**——**用代码行数考核 -> 代码膨胀；用故事点考核 -> 估算注水；用缺陷数考核 -> 不报缺陷**。
- **三个常见反模式**：**活动指标当产出**（工时/代码行/提交数）、**跨团队排名**（制造内卷与数据造假）、**只看速度不看质量**（部署频率上去了，故障率也上去了）。
- **好的度量**：**成对指标**（速度 + 稳定性）、**团队自比**（这个季度 vs 上季度）、**面向系统**（流动效率/瓶颈）而不是"给人打分"。
- **度量的目的**：**发现改进点**，不是**评判个人**——**一旦用于考核，数据就会失真**（这是组织问题，不是技术问题）。
- 与 79-01 的关系：DORA 四指标是"系统指标"，**本章讲"怎么用才不跑偏"**——**指标是方向盘，不是鞭子**。
- 易错点：用 LOC 排名；用"在线时长"衡量敬业；用缺陷数扣绩效；把团队对比公开化。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MetricAntiPatterns {
    record Team(String name, int features, int loc, int bugs, int deploys, int incidents) {
    }

    static void locRanking(List<Team> teams) {
        System.out.println("【反模式：按代码行数排名】");
        List<Team> sorted = new ArrayList<>(teams);
        sorted.sort(Comparator.comparingInt(Team::loc).reversed());
        for (Team t : sorted) {
            System.out.printf("  %-6s LOC=%-6d 功能数=%-3d -> 排名按 LOC%n",
                    t.name(), t.loc(), t.features());
        }
        System.out.println("  问题：写得多不等于价值高，反而奖励臃肿代码");
        System.out.println();
    }

    static void valueView(List<Team> teams) {
        System.out.println("【正确视角：单位功能成本 + 质量】");
        for (Team t : teams) {
            double locPerFeature = (double) t.loc() / t.features();
            double bugsPerFeature = (double) t.bugs() / t.features();
            System.out.printf("  %-6s 功能=%-3d LOC/功能=%6.1f 缺陷/功能=%.2f 发布=%d 事故=%d%n",
                    t.name(), t.features(), locPerFeature, bugsPerFeature,
                    t.deploys(), t.incidents());
        }
        System.out.println("  B 团队功能相同、代码更少、缺陷更少 -> 实际更高效");
        System.out.println();
    }

    static void goodhart() {
        System.out.println("【Goodhart 演示：用「缺陷数」考核】");
        int reported = 10;
        for (int round = 1; round <= 3; round++) {
            System.out.printf("  第 %d 月：考核压力上升，上报缺陷=%d（真实缺陷约 %d）%n",
                    round, reported, 10 + round * 2);
            reported -= 3;                            // 大家开始不报
        }
        System.out.println("  结果：指标「变好」，系统实际变差 —— 指标被博弈了");
        System.out.println();
    }

    static void principles() {
        System.out.println("【度量原则】");
        String[][] rows = {
                {"成对", "速度 + 稳定性", "部署频率 + 变更失败率"},
                {"自比", "团队 vs 自己的过去", "本季度 vs 上季度"},
                {"系统", "流动效率/瓶颈", "等待时间占比"},
                {"不可考核个人", "只用于改进", "一旦排名就失真"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-22s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        List<Team> teams = List.of(
                new Team("A", 10, 5000, 30, 20, 4),
                new Team("B", 10, 1200, 8, 40, 1),
                new Team("C", 6, 2000, 5, 12, 0));

        locRanking(teams);
        valueView(teams);
        goodhart();
        principles();

        System.out.println("【结论】度量要成对、自比、面向系统；用于考核个人必然失真");
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("LOC", "活动指标，不反映价值");
        summary.put("故事点", "估算单位，不能跨团队比较");
        summary.put("DORA", "系统指标，适合找瓶颈");
        summary.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 用两组"功能数/代码行/缺陷/发布"数据，演示 LOC 排名与单位功能成本的结论相反。
2. 设计一组"成对指标"（速度 + 稳定性），说明如何避免 Goodhart。
3. 思考题：为什么"指标用于个人考核"必然失真？从博弈行为与数据上报分析。

---

### 79-11 综合案例：内部开发者平台

**知识点**
- **把本章收口成一条"自助服务链"**：**服务目录（79-09）-> 脚手架生成（79-02）-> 流水线模板（79-07）-> 构建缓存/影响分析（79-03/79-04）-> 环境申请（79-06）-> 制品晋升（79-08）-> 记分卡**——**开发者自助完成，平台只提供默认正确路径**。
- **平台的价值指标**：**新服务上线时间**（从几天到几小时）、**环境等待时间**、**构建时长**、**Golden Path 覆盖率**——**用 79-01 的价值流度量平台自己的效果**。
- **平台不是"工具堆"**：**统一入口 + 统一模板 + 统一度量**，**否则只是把散落工具换了个首页**。
- **组织配套**：**平台团队做"产品"**（有用户、有反馈、有路线图），**不是"运维支持"**——**内部平台也要做用户研究**。
- 与 79 章各节的关系：本章是"组件的组装"，**和 69/70/71 章一样，是能力的收口**。
- 易错点：平台只做 UI 不做自动化（点完还要手工）；模板不维护（生成即过时）；不度量平台自身（不知道有没有用）；强制使用（团队绕过）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IdpCase {
    record Service(String name, String owner, String template, String pipeline,
                   String env, String version, int score) {
    }

    static final List<Service> catalog = new ArrayList<>();
    static long now = 0;

    static long step(String name, long costMinutes) {
        now += costMinutes;
        System.out.printf("  +%-4dmin %s%n", costMinutes, name);
        return now;
    }

    static Service onboard(String name, String owner) {
        System.out.println("【新服务上线：" + name + "】");
        step("门户登记（服务目录）", 2);
        step("脚手架生成（模板 + 参数校验）", 3);
        step("生成物编译 + 单测", 10);
        step("套用流水线模板（含扫描）", 2);
        step("申请 dev 环境（TTL 2h）", 1);
        step("首次构建（远程缓存未命中）", 8);
        step("部署 dev + 冒烟", 4);
        step("制品晋升 staging（门禁通过）", 2);
        step("记分卡初始化（owner/runbook/SLO）", 1);

        Service s = new Service(name, owner, "java-service", "java-ci-v3",
                "env-dev", "0.1.0", 60);
        catalog.add(s);
        System.out.printf("  总耗时：%d 分钟（传统流程通常 2~5 天）%n", now);
        return s;
    }

    static void change(String service, String file) {
        System.out.println();
        System.out.println("【变更：" + service + " 修改 " + file + "】");
        step("影响分析（只构建 " + service + "）", 1);
        step("构建缓存命中（依赖未变）", 1);
        step("单测 + 扫描（并行）", 6);
        step("部署 staging", 3);
        step("冒烟通过", 2);
        System.out.printf("  从提交到 staging：%d 分钟%n", 12);
    }

    static void report() {
        System.out.println();
        System.out.println("【平台看板】");
        System.out.println("  服务数：" + catalog.size());
        catalog.forEach(s -> System.out.printf("    %-8s owner=%-6s version=%-6s score=%d%n",
                s.name(), s.owner(), s.version(), s.score()));

        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("新服务上线", 33L);
        metrics.put("变更到 staging", 12L);
        metrics.put("环境申请等待", 1L);
        metrics.put("构建平均时长", 3L);
        metrics.put("GoldenPath 覆盖", 100L);
        System.out.println("  平台指标（分钟）：" + metrics);

        System.out.println();
        System.out.println("【平台自身也要度量】");
        System.out.println("  新服务上线时间 / 环境等待 / 构建时长 / 模板使用率 / 开发者满意度");
        System.out.println("  没有度量 -> 不知道平台有没有用 -> 无法争取投入");
    }

    public static void main(String[] args) {
        onboard("payment-service", "支付团队");
        change("payment-service", "PayService.java");
        onboard("risk-service", "风控团队");
        report();

        System.out.println();
        System.out.println("【验收】自助（无需工单）/ 默认正确（Golden Path）/ 可度量（价值流）");
    }
}
```

**课后练习**
1. 端到端模拟"新服务上线"：登记 -> 脚手架 -> 流水线 -> 环境 -> 晋升，统计总耗时。
2. 给平台加"变更路径"：影响分析 + 缓存命中 + 并行测试，对比全量构建耗时。
3. 思考题：为什么"平台只做 UI 不做自动化"没有价值？从"开发者仍要手工操作"分析。
