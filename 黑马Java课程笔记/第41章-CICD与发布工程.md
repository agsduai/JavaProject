# 第41章 CI/CD与发布工程

第40章把 AI 应用做进了系统，本章回到一个更古老的问题："代码写完之后，怎么安全地跑到生产"。手工发布的时代，发布是一次仪式：申请窗口、手工执行、全员盯守、出事回滚靠手速；发布工程把它变成一条流水线：提交进规范、验证进闸门、制品不可变、环境逐级晋升、金丝雀自动判据、回滚一条命令——**发布从"事件"变成"日常"**。本章按「分支策略 → 提交规范 → 流水线设计 → 制品管理 → 环境与配置 → 滚动与蓝绿 → 金丝雀灰度 → 特性开关 → 数据库变更协同 → 发布审计 → 完整流水线案例」的顺序讲。所有示例仍是纯 JDK：分支指针与合并语义用提交图真实推演，提交校验与 CHANGELOG 生成真跑，流水线闸门与金丝雀自动回退用状态机真实执行，不需要 Jenkins 或 GitLab。

### 41-01 Git 协作模型与分支策略

**知识点**
- **Git 的核心模型只有四层**：工作区（你正在编辑的文件）、暂存区（`git add` 后的快照）、本地仓库（`git commit` 后的历史）、远程仓库（`git push` 后的共享历史）。所有令新手困惑的命令，都是在问"把什么从一个区搬到另一个区"。
- **分支只是一个 41 字节的指针**：`.git/refs/heads/main` 文件里存着一个 commit 哈希，创建分支≈写一个文件，切换分支才真正动文件树。这解释了为什么 Git 分支"免费"而 SVN 分支昂贵——**分支的便宜催生了分支策略**。
- **团队分支策略的主流选择**：`GitHub Flow`（main + 短命特性分支，适合持续部署）；`GitFlow`（develop/release/hotfix 多长命分支，适合按版本发布）；` trunk-based `（所有人向 main 高频合入，配合特性开关，适合大团队快节奏）。**选择依据是发布节奏，不是公司大小**。
- **短命分支是卫生问题**：特性分支存活超过 2~3 天，与 main 的分歧就会累积到难以合并。大 PR = 大风险 + 长评审。**小步提交、当天合并**是所有策略的共同纪律。
- **合并方式的三种语义**：`merge`（保留分支拓扑，历史真实但杂乱）、`squash merge`（压成一个提交进 main，main 干净）、`rebase`（把提交搬到新基底，线性历史但**改写历史**，只适用于未推送的私有分支）。团队必须统一约定，混用 = 历史混乱。
- **保护分支（branch protection）是策略的强制执行**：main 禁止直接 push、必须 PR + 至少 1 人审批 + CI 绿灯才能合并。没有保护分支的"策略"只是文档。
- **冲突预防优于冲突解决**：频繁 rebase main 到自己分支（每天一次）、拆小 PR、模块所有权清晰化。冲突的本质是**两个人同时改同一处代码没有及时同步**。
- 易错点：在 main 上直接开发（无保护分支时最容易发生）；`git push -f` 到共享分支（同事的提交被覆盖）；长命 release 分支与 develop 双线演化导致合并地狱；把 `git pull`（fetch+merge）当默认习惯产生无意义 merge 提交（改用 `pull --rebase`）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GitBranchModel {
    /** commit 图：每个提交记录父提交，分支只是指向某个提交的名字 */
    static Map<String, String[]> commits = new LinkedHashMap<>();

    static void addCommit(String id, String parent) {
        commits.put(id, parent == null ? new String[0] : new String[]{parent});
    }

    static void model() {
        System.out.println("【分支 = 指向提交的指针】");
        addCommit("c1", null);
        addCommit("c2", "c1");
        addCommit("c3", "c2");
        addCommit("c4", "c3");      // 特性分支上的提交
        Map<String, String> refs = Map.of("main", "c3", "feat-pay", "c4", "HEAD", "feat-pay");
        System.out.println("  提交图：c1 <- c2 <- c3 <- c4（c4 只在 feat-pay 上）");
        for (Map.Entry<String, String> e : refs.entrySet()) {
            System.out.printf("  ref %-10s -> %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  创建分支 = 写一行指针；合并 = 移动指针 + 记录双亲");
        System.out.println();
    }

    static void strategyCompare() {
        System.out.println("【三种分支策略：按发布节奏选】");
        String[][] rows = {
                {"GitHub Flow", "main + 短命特性分支", "持续部署，一天多次发布", "小团队、SaaS"},
                {"GitFlow", "develop/release/hotfix 长命分支", "按版本发布、多版本并行维护", "发布节奏慢、有交付期"},
                {"trunk-based", "高频直入 main + 特性开关", "极快节奏、大团队同主干", "需要强 CI 与开关基础设施"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-13s %-32s %-26s %s%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println();
    }

    static void branchLife() {
        System.out.println("【分支寿命：分歧越大合并越痛】");
        record Branch(String name, int days, int divergence, String mergeRisk) {
        }
        List<Branch> branches = List.of(
                new Branch("feat-icon", 1, 1, "低：一次 rebase 即可"),
                new Branch("feat-refactor", 3, 6, "中：需要与提交人当面协调"),
                new Branch("feat-big", 12, 40, "高：重复合并地狱，应拆分重做"));
        for (Branch b : branches) {
            System.out.printf("  %-14s 存活%2d天 与main分歧%2d处 -> %s%n",
                    b.name(), b.days(), b.divergence(), b.mergeRisk());
        }
        System.out.println("  纪律：特性分支不超过 2~3 天；PR 变更行数控制在 400 行以内");
        System.out.println();
    }

    static void mergeSemantics() {
        System.out.println("【三种合并的历史形状】");
        List<String> modes = List.of(
                "merge         ：main 历史保留分支拓扑（真实但杂乱），产生 merge 提交",
                "squash merge  ：分支 5 个提交压成 main 上 1 个提交（main 干净，细节丢失）",
                "rebase        ：提交搬到 main 最新节点之后（线性历史，改写了分支提交哈希）");
        for (String m : modes) {
            System.out.println("  " + m);
        }
        System.out.println("  铁律：rebase 只用于自己未推送的分支；公共分支历史不可改写");
        System.out.println();
    }

    static void protection() {
        System.out.println("【分支保护：把纪律变成机器规则】");
        List<String> rules = List.of(
                "main 禁止直接 push，一切变更走 PR",
                "PR 需要 CI 全绿（编译/测试/扫描）+ 1~2 人审批",
                "禁止 force push 与删除 main",
                "要求分支与 main 最新（up to date）才能合并",
                "CODEOWNERS：核心模块必须模块负责人审批");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        model();
        strategyCompare();
        branchLife();
        mergeSemantics();
        protection();
    }
}
```

**课后练习**
1. 为你的团队写一份分支策略决策书：对比 GitHub Flow 与 GitFlow，依据发布节奏选定一种，并给出分支保护的 5 条机器规则。
2. 本机演练三种合并：创建分支提交 3 次，分别用 merge/squash/rebase 合回 main，用 `git log --graph` 截图对比历史形状。
3. 制造一次冲突并解决：两人分支改同一行，合并后说明冲突的产生条件与你的预防措施。
4. 思考题：为什么"分支很便宜"反而要求"分支很短命"？从分歧累积成本的角度推导小 PR 的价值。

---

### 41-02 提交规范与变更追溯

**知识点**
- **提交信息是变更的元数据，不是日记**：半年后排查"这行代码为什么这么写"，唯一入口是提交历史。规范的提交信息让 `git log` 可以机读、可检索、可生成变更日志。
- **Conventional Commits 是事实标准**：`type(scope): subject`，如 `feat(order): 支持批量取消`。type 固定枚举：feat/fix/refactor/perf/test/docs/chore/ci。**机读的价值**：脚本可以按 type 自动生成 CHANGELOG、自动算版本号（feat → minor，fix → patch，breaking → major）。
- **一个提交做一件事（原子提交）**：提交是回滚与 review 的最小单位。"重构 + 修 bug + 改格式"混在一个提交里，出问题时无法单独回滚那 20 行修复。**提交粒度 = 事后追溯粒度**。
- **subject 写"什么变了"而不是"我做了什么"**：`fix: 修复订单金额计算` 差，`fix(order): 金额计算未考虑优惠券叠加导致多扣` 好——后者包含影响范围与根因方向。首行 50 字内，正文空一行后写动机与副作用。
- **提交与需求/事故的关联**：正文或脚注引用工单号（`Refs: JIRA-1234`）与事故编号。事故复盘时 `git log --grep=INC-456` 直接拉出全部相关变更——这是发布审计（41-10）的数据基础。
- **坏味道提交的自动拦截**：commit-msg 钩子或 CI 检查提交格式；`git commit -m "fix"` / `"update"` / `"改好了" 直接拒绝。规范靠工具强制，不靠自觉。
- **git blame 的正确用法**：`blame` 只告诉你谁最后改了这行，**配合提交信息才知道为什么**。没有规范提交信息的 blame 是没有线索的现场。
- 易错点：一次提交改 30 个文件混 5 个意图；用 `git commit -am` 顺手提交了调试代码与临时文件；提交信息用"修复bug"（半年后等于没有）；rebase 后 force push 到共享分支丢失他人提交。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class CommitConvention {
    record Commit(String id, String message, String refs, int files, int lines) {
        String type() {
            return message.split("[(:]")[0];
        }

        boolean atomic() {
            return files <= 5;
        }
    }

    static final List<Commit> LOG = List.of(
            new Commit("a1b2", "feat(order): 支持批量取消", "Refs: JIRA-1234", 4, 120),
            new Commit("c3d4", "fix(order): 金额未考虑优惠券叠加导致多扣", "Refs: JIRA-1237 INC-456", 2, 15),
            new Commit("e5f6", "update", "", 31, 900),
            new Commit("g7h8", "chore(deps): 升级 fastjson 到安全版本", "Refs: SEC-89", 1, 3));

    static void validate() {
        System.out.println("【提交校验：机读格式与原子性】");
        for (Commit c : LOG) {
            String type = c.type();
            boolean typeOk = List.of("feat", "fix", "refactor", "perf", "test",
                    "docs", "chore", "ci").contains(type);
            String verdict;
            if (!typeOk) {
                verdict = "REJECT：type 非法（" + type + "），必须是规范枚举";
            } else if (!c.atomic()) {
                verdict = "REJECT：改动 " + c.files() + " 个文件，疑似混入多个意图，请拆分";
            } else if (c.refs().isEmpty()) {
                verdict = "WARN：未关联工单/事故编号";
            } else {
                verdict = "PASS";
            }
            System.out.printf("  %-4s %-42s %s%n", c.id(), c.message(), verdict);
        }
        System.out.println();
    }

    static void changelogGen() {
        System.out.println("【机读价值：按 type 自动生成 CHANGELOG】");
        StringBuilder feat = new StringBuilder(), fix = new StringBuilder();
        for (Commit c : LOG) {
            if (c.type().equals("feat")) {
                feat.append(c.message()).append(" (").append(c.id()).append(")\n");
            } else if (c.type().equals("fix")) {
                fix.append(c.message()).append(" (").append(c.id()).append(")\n");
            }
        }
        System.out.println("  ## 2.4.0");
        System.out.println("  ### Features\n  " + feat);
        System.out.println("  ### Fixes\n  " + fix);
        System.out.println("  版本号规则：feat -> minor，fix -> patch，含 breaking -> major");
        System.out.println();
    }

    static void traceIncident() {
        System.out.println("【事故追溯：git log --grep 是复盘的第一工具】");
        List<Commit> related = new ArrayList<>();
        for (Commit c : LOG) {
            if (c.refs().contains("INC-456")) {
                related.add(c);
            }
        }
        System.out.println("  检索 INC-456 相关提交：");
        for (Commit c : related) {
            System.out.printf("  %s %s（%d 行）%n", c.id(), c.message(), c.lines());
        }
        System.out.println("  配合 blame：定位到 c3d4 引入的叠加逻辑，直接对齐事故时间线");
        System.out.println();
    }

    static void hookGuard() {
        System.out.println("【commit-msg 钩子：规范靠工具强制】");
        List<String> hook = List.of(
                "读取 $1（暂存的提交信息文件）",
                "正则校验 ^(feat|fix|refactor|perf|test|docs|chore|ci)(\\(.+\\))?: .+",
                "首行超过 72 字 -> 拒绝",
                "CI 层再校验一次（防止本地 --no-verify 绕过）");
        for (String s : hook) {
            System.out.println("  " + s);
        }
    }

    public static void main(String[] args) {
        validate();
        changelogGen();
        traceIncident();
        hookGuard();
    }
}
```

**课后练习**
1. 给你的项目写 commit-msg 钩子（bash 或 Java 都可），实现格式校验与 72 字限制，并验证 3 个合法与 3 个非法提交。
2. 用脚本把过去一个月的提交按 Conventional Commits 生成 CHANGELOG，统计非法格式提交的比例，作为团队改进基线。
3. 思考题：为什么"原子提交"是回滚与 review 的基础？举一个混合提交导致无法精准回滚的真实场景。

---

### 41-03 流水线的阶段设计

**知识点**
- **流水线是一条有准入与准出的质量闸门链**：每个阶段（stage）只有"通过/失败"两种结果，失败即终止——**流水线的价值不在跑得快，而在"绿灯可信"**：绿灯意味着合并的代码满足全部质量约定。
- **经典五阶段**：`编译构建 → 静态检查 → 单元测试 → 打包制品 → 部署验证`。顺序的原则是**便宜的放前面**：编译 30 秒先跑，集成测试 10 分钟放后——失败要尽早暴露，别让开发者等 10 分钟才知道少了个分号。
- **并行与扇出**：互不依赖的阶段并行（静态检查 ∥ 单元测试 ∥ 安全扫描），多模块工程按模块扇出（矩阵构建）。流水线耗时 = 最长路径耗时，优化先砍关键路径。
- **阶段产物必须显式传递**：构建产出的 jar、测试报告、覆盖率、扫描结果都是"产物（artifact）"，下游阶段引用而不是重算。**产物不可变**：测试通过的那个 jar 必须就是部署的那个 jar——重新构建意味着"测的不是发的"。
- **验证环境（smoke test）自动化**：部署到测试环境后自动跑健康检查与核心接口回归，绿灯才能进下一环境。人工点开页面看一眼不是验证。
- **流水线的两种触发语义**：PR 触发（验证合并后的结果，用 merge commit）与 main 触发（构建制品并晋升）。**PR 流水线跑测试但不出制品**，避免未合入代码产生"幽灵版本"。
- **失败即停 + 快速失败**：任何阶段失败流水线终止、通知提交人、门禁拦截合并。红灯超过 30 分钟没人处理，团队会开始"无视红灯"——**红灯响应时间是流水线可信度的生命线**。
- 易错点：流水线里手工点击审批太多（自动化变成人肉流水线）；测试失败后"重跑碰运气"变成习惯（flaky 测试是绿灯不可信的头号原因）；构建产物不固定版本（部署的是什么没人说得清）；在流水线里直接 build 镜像又 push 又部署一步做完（阶段不可回退）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PipelineStages {
    record Stage(String name, int costSec, boolean parallel, boolean blocking) {
    }

    static final List<Stage> STAGES = List.of(
            new Stage("compile", 30, false, true),
            new Stage("lint+scan", 60, true, true),
            new Stage("unit-test", 180, true, true),
            new Stage("package-jar", 45, false, true),
            new Stage("deploy-test", 60, false, true),
            new Stage("smoke-test", 90, false, true),
            new Stage("deploy-prod", 120, false, true));

    static void criticalPath() {
        System.out.println("【流水线耗时 = 关键路径】");
        System.out.println("  compile(30s) -> [lint(60s) ∥ unit-test(180s)] -> package(45s)");
        System.out.println("              -> deploy-test(60s) -> smoke(90s) -> deploy-prod(120s)");
        int path1 = 30 + Math.max(60, 180) + 45 + 60 + 90 + 120;
        int path2 = 30 + 60 + 180 + 45 + 60 + 90 + 120;
        System.out.printf("  并行后总耗时 %d s；若不并行 %d s（省 %d s）%n",
                path1, path2, path2 - path1);
        System.out.println("  优化顺序：先砍关键路径上最贵的阶段（unit-test 提速/分片）");
        System.out.println();
    }

    static void gateWalkthrough() {
        System.out.println("【闸门推演：一次带故障的运行】");
        record Run(String stage, String result, String note) {
        }
        List<Run> runs = List.of(
                new Run("compile", "PASS", "30s"),
                new Run("unit-test", "FAIL", "OrderServiceTest 第 42 行断言失败"),
                new Run("deploy-test", "SKIP", "上游失败，自动跳过"),
                new Run("deploy-prod", "SKIP", "同上"));
        for (Run r : runs) {
            System.out.printf("  %-12s %-5s %s%n", r.stage(), r.result(), r.note());
        }
        System.out.println("  规则：blocking 阶段失败 -> 后续全部 SKIP -> 门禁拦截合并");
        System.out.println("  失败通知 10 分钟内响应；红灯超 30 分钟视为团队事故");
        System.out.println();
    }

    static void artifactImmutability() {
        System.out.println("【制品不可变：测的就是发的】");
        Map<String, String> correct = Map.of(
                "PR 流水线", "merge commit 构建 -> 测试通过 -> 记录制品 sha256",
                "main 流水线", "引用 PR 验证过的制品（不再重新构建）-> 打正式版本号 -> 晋升部署",
                "生产部署", "下载制品库中的版本，校验 sha256 一致");
        for (Map.Entry<String, String> e : correct.entrySet()) {
            System.out.printf("  %-10s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  反例：测试完重新 build 一次再部署 -> 依赖可能拉到新版，测的不是发的");
        System.out.println();
    }

    static void triggerSemantics() {
        System.out.println("【两种触发：PR 验证，main 出制品】");
        List<String> rows = List.of(
                "PR 触发：跑 编译/检查/测试，不打包不部署，验证的是「合并后 main 会是什么样」",
                "main 触发：合并后跑 打包+制品入库+部署测试环境，main 永远处于可发布状态",
                "tag 触发：打版本 tag -> 从制品库取已验证制品 -> 生产发布流程");
        for (String r : rows) {
            System.out.println("  " + r);
        }
    }

    public static void main(String[] args) {
        criticalPath();
        gateWalkthrough();
        artifactImmutability();
        triggerSemantics();
    }
}
```

**课后练习**
1. 为你的项目画出流水线阶段图（含并行与耗时），找出关键路径上最贵的阶段并给出一条优化方案。
2. 复现"测的不是发的"事故：测试通过后改一行代码重新构建再部署，解释制品不可变原则如何阻断它。
3. 思考题：为什么 flaky 测试会摧毁流水线的全部价值？给出 flaky 的定义、检测方法与处理流程（隔离/修复/删除的决策树）。

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

### 41-08 特性开关与暗发布

**知识点**
- **特性开关把"部署"与"发布"解耦**：代码随时合入 main 部署上线，新功能由开关控制是否对用户可见。**部署 100 次，功能发布 1 次**——这是 trunk-based 开发的基础设施，也是发布风险从"一次性大爆炸"变成"随时小步释放"的支点。
- **开关的四类生命周期**：发布开关（release toggle，功能上线后几天内删除）、运维开关（ops toggle，如降级到大促写保护）、实验开关（experiment，A/B 用完删除）、权限开关（permission，长期存在如付费功能）。**前三类是技术债，必须设过期时间**——堆积的"僵尸开关"会把代码分支逻辑变成迷宫。
- **暗发布（dark launch）**：新代码在真实流量下运行但用户无感知——把结果写日志不返回用户，用真实负载验证性能与正确性（影子流量）。"新推荐算法算了但不展示，对比效果一周"是暗发布的典型用法。
- **开关的实现层次**：最简单是配置中心布尔值；进阶带灰度规则（按用户/渠道/百分比）；再进阶与实验平台打通（分流+效果归因）。**开关读取必须在热路径上便宜**（本地缓存 + 秒级同步），否则开关本身就是故障源。
- **开关的测试矩阵爆炸**：N 个开关 = 2^N 种组合。对策：开关声明默认值、组合测试只测核心路径、CI 里跑"全开/全关"两组回归。
- **删除开关也是发布**：移除旧分支代码时走正常 PR + 测试，**"功能已全量、代码可删"要写进发布清单**。永远不删的开关 = 永远的死代码分支。
- 易错点：开关默认值是"开"（忘了关闭就直接全量）；开关逻辑散落在几十个 if 里没有统一收口；开关名不可读（`flag1`、`test2`，半年后没人知道它控制什么）；用开关做长期权限管理（应上权限系统，开关系统承担不了审计职责）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeatureFlags {
    record Flag(String name, String kind, boolean defaultValue, String expire, String owner) {
    }

    static Map<String, Flag> flags = new LinkedHashMap<>();

    static void flagRegistry() {
        System.out.println("【开关注册表：名字可读、默认值、过期时间、责任人】");
        flags.put("order.batch-cancel", new Flag("order.batch-cancel", "release", false,
                "2026-12-31 全量后删除", "订单组-小王"));
        flags.put("search.vector-rank", new Flag("search.vector-rank", "experiment", false,
                "实验结束（2026-12-15）", "搜索组-小李"));
        flags.put("pay.write-protect", new Flag("pay.write-protect", "ops", false,
                "大促结束删除", "支付组"));
        flags.put("vip.discount", new Flag("vip.discount", "permission", true,
                "长期保留（转权限系统候选）", "产品"));
        for (Flag f : flags.values()) {
            System.out.printf("  %-20s %-11s 默认=%-5s 过期=%-24s 责任=%s%n",
                    f.name(), f.kind(), f.defaultValue(), f.expire(), f.owner());
        }
        System.out.println("  release/experiment 开关必须有删除计划，否则注册表拒绝新增");
        System.out.println();
    }

    static void decoupleDeployRelease() {
        System.out.println("【部署与发布解耦：代码先行，功能后放】");
        List<String> timeline = List.of(
                "11-01 合入批量取消代码（flag=false），部署 3 次，用户无感",
                "11-10 内部账号 flag=true（权限开关），收集反馈",
                "11-15 5% 用户灰度，错误率与转化观察",
                "11-20 全量 flag=true",
                "12-01 确认稳定 -> 删除开关与旧分支代码（一次正常 PR）");
        for (String t : timeline) {
            System.out.println("  " + t);
        }
        System.out.println("  对照：不开开关 = 11-20 一次性大爆炸，出问题只能整体回滚");
        System.out.println();
    }

    static void darkLaunch() {
        System.out.println("【暗发布：真跑但用户无感】");
        record Req(String query, String oldResult, String newResult, String action) {
        }
        List<Req> reqs = List.of(
                new Req("手机壳", "商品A", "商品C", "日志记录 diff，返回旧结果"),
                new Req("退款 政策", "FAQ-3", "FAQ-3", "一致，标记为正常"));
        for (Req r : reqs) {
            System.out.printf("  查询「%s」 旧=%-8s 新=%-8s -> %s%n",
                    r.query(), r.oldResult(), r.newResult(), r.action());
        }
        System.out.println("  一周后统计：新算法 CTR 提升 +2.1% 且 P99 持平 -> 开放灰度");
        System.out.println("  影子流量的三种用途：性能压测、正确性对比、效果实验");
        System.out.println();
    }

    static void testMatrix() {
        System.out.println("【开关组合的测试策略】");
        List<String> rules = List.of(
                "核心链路跑「全关」与「全开」两组回归（CI 固定两档）",
                "开关间声明依赖：B 依赖 A 开启时才生效，注册表校验",
                "组合爆炸（2^N）不可测 -> 限制同模块活跃开关 <= 3",
                "默认值即兜底：开关系统不可用时按默认值执行（fail safe）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        flagRegistry();
        decoupleDeployRelease();
        darkLaunch();
        testMatrix();
    }
}
```

**课后练习**
1. 为你的项目实现一个极简开关服务（配置中心布尔值 + 本地缓存秒级同步），验证热路径读取开销 < 0.1ms。
2. 给一个新功能设计完整的"部署-暗发布-灰度-全量-删开关"时间线，其中每一步的验证动作与责任人写清。
3. 思考题：为什么"僵尸开关"是技术债？从代码分支组合数与认知负担两个角度量化 N 个僵尸开关的维护成本。

---

### 41-09 数据库变更与发布协同

**知识点**
- **发布事故的最大来源不是代码，是代码与数据库变更的时序错配**：代码新版本需要新列，DDL 还没跑 -> 启动即报错；DDL 先跑删了旧列 -> 旧版本还在读 -> 崩。**协同的核心是"每一步状态下，正在运行的版本集合都能正常工作"**。
- **schema 变更必须走迁移工具与版本化脚本**（Flyway/Liquibase）：`V17__add_refund_status.sql` 进代码库，流水线在部署前自动执行，执行记录入库可审计。**禁止手工在库上改结构**（41-05 的漂移话题在数据库上的最严重形态）。
- **兼容性迁移的扩容三步法（expand-contract）**：① expand：加列/加表，新旧代码都兼容；② 部署新版本双读写；③ contract：确认旧版本下线后，删除旧列/旧表。**每一步独立发布，全程新旧可共存**。破坏性操作（DROP/改类型）只出现在 contract 步。
- **反向兼容（rollback-safe）检验**：每个迁移脚本要回答"如果发布失败回滚代码，旧代码在这个 schema 上能不能跑？"——加列可空可以，改列类型不行。**不可反向兼容的变更必须在发布计划里显式声明并准备数据回滚方案**。
- **大表的 DDL 是高危操作**：MySQL 8.0 的 Online DDL 大多 INSTANT/INPLACE，但改列类型、加全文本地索引仍可能长时间锁；DDL 工具（gh-ost/pt-online-schema-change）用影子表+触发器/binlog 同步。**DDL 执行前估算行数与锁表时长，避开高峰，主从延迟告警盯紧**。
- **数据回填（backfill）与 DDL 分离**：加列（秒级）与回填千万行（小时级）分开跑，回填用分批脚本 + 限速，避免一个 UPDATE 锁全表拖垮主从。
- 易错点：迁移脚本没有 down（回滚）路径；expand 和 contract 合并成一次发布（新旧版本无法共存）；DDL 在高峰期执行拖垮主从；回填脚本不分批（undo log 爆炸 + 复制延迟）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class DbMigration {
    record Migration(String version, String sql, String kind, String rollbackSafe) {
    }

    static final List<Migration> PLAN = List.of(
            new Migration("V17", "ALTER TABLE orders ADD COLUMN refund_status VARCHAR(16) NULL", "expand",
                    "回滚安全：新列可空，旧代码不读"),
            new Migration("V18", "回填脚本：UPDATE orders SET refund_status='NONE' 批量分批", "backfill",
                    "与 DDL 分离，限速执行"),
            new Migration("V19", "部署新版本（读+写 refund_status）", "deploy", "双版本共存期开始"),
            new Migration("V20", "ALTER TABLE orders DROP COLUMN old_flag", "contract",
                    "不可回滚！必须确认旧版本全部下线"));

    static void expandContract() {
        System.out.println("【expand-contract 三步法：每一步新旧共存都安全】");
        for (Migration m : PLAN) {
            System.out.printf("  %-4s %-6s %-52s %s%n",
                    m.version(), m.kind(), m.sql(), m.rollbackSafe());
        }
        System.out.println("  contract 步骤是单向门：执行前必须确认旧版本实例数为 0");
        System.out.println();
    }

    static void versionTimeline() {
        System.out.println("【时序推演：任何时刻任何版本都能跑】");
        record Phase(String when, String schema, String versions, String verdict) {
        }
        List<Phase> phases = List.of(
                new Phase("T1 expand 后", "+refund_status(NULL)", "v1, v2 均未部署", "v1 正常：不认识新列也无所谓"),
                new Phase("T2 v2 上线", "+refund_status", "v1 与 v2 共存", "v1 读写忽略新列；v2 正常读写"),
                new Phase("T3 v1 下线", "+refund_status(有值)", "仅 v2", "双读写稳定"),
                new Phase("T4 contract", "-old_flag", "仅 v2", "删除列，v2 不依赖旧列"));
        for (Phase p : phases) {
            System.out.printf("  %-14s schema=%-22s 版本=%-12s %s%n",
                    p.when(), p.schema(), p.versions(), p.verdict());
        }
        System.out.println();
    }

    static void ddlRisk() {
        System.out.println("【大表 DDL 的风险分级】");
        String[][] rows = {
                {"加可空列", "MySQL8 INSTANT 秒级", "低风险，流水线直接跑"},
                {"加索引", "INPLACE，不锁写但耗 IO", "评估行数，避开高峰"},
                {"改列类型", "多数重建全表", "用 gh-ost/pt-osc，或拆两列过渡"},
                {"DROP 列", "INSTANT 但不可逆", "contract 步，人工确认"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-24s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  执行清单：估行数 -> 预估时长 -> 主从延迟告警就位 -> 错峰执行");
        System.out.println();
    }

    static void backfill() {
        System.out.println("【回填：分批限速，不与 DDL 混跑】");
        long total = 50_000_000, batch = 5_000;
        long batches = total / batch;
        System.out.printf("  5000 万行按每批 %d 行 -> %d 批，每批间隔 100ms 限速%n", batch, batches);
        System.out.println("  批间 sleep 让主从延迟收敛；脚本可断点续跑（记录最大已回填 id）");
        System.out.println("  完成 marker：回填进度表 100% 后才能进入依赖该列的部署");
    }

    public static void main(String[] args) {
        expandContract();
        versionTimeline();
        ddlRisk();
        backfill();
    }
}
```

**课后练习**
1. 为一个真实需求（新增退款状态）写出完整迁移计划：V17~V20 四步 + 每步的回滚方案，标注"单向门"位置。
2. 用 gh-ost（或模拟脚本）体验大表加索引的影子表流程，记录主从延迟变化与限速参数。
3. 思考题：为什么"迁移脚本必须回答反向兼容问题"？举一个发布失败回滚后旧代码在新 schema 上崩溃的例子。

---

### 41-10 发布自动化与审计

**知识点**
- **发布的理想形态是"按钮工程"**：触发 = 打版本 tag；执行 = 流水线全自动（测试 -> 制品 -> 晋升 -> 部署 -> 验证）；人工只出现在两处——prod 放行审批与异常时的回滚决定。**自动化不是减少步骤，是把步骤变成可重复、可审计的机器动作**。
- **发布单（change record）是审计单元**：谁、何时、发布了什么（制品版本）、到哪些环境、变更类型（常规/紧急/回滚）、审批人、验证结果、工单关联。**每一条生产变更都应能从发布单回溯到提交与验证证据**。
- **紧急发布的绿色通道与代价**：紧急修复也走流水线但允许并行审批、跳过部分低风险门禁（如 staging 观察 24h）。**"紧急"是路径更快，不是没有记录**——事故后的"谁改了什么"如果答不出来，紧急发布就成了二次事故源。
- **审批的最小化原则**：审批人要有上下文（变更内容 + 影响面 + 验证证据），自动汇总进审批卡片；高频低风险变更（纯配置、文案）降级为自动放行 + 事后抽查。**审批膨胀 = 团队学会绕过流程**。
- **发布窗口与冻结期**：大促/月末结算冻结发布（只允许 hotfix）；常规发布窗口避开周五下午与节前。冻结期是业务节奏，写进日历并自动拦截。
- **度量发布工程**：部署频率、变更前置时间（commit->prod）、变更失败率、MTTR——DORA 四指标。**度量驱动改进，而不是 KPI 表演**。
- 易错点：跳板机上手工 `kubectl set image`（无记录无审计）；发布单事后补填（等于没有）；紧急发布习惯化（每次都说紧急，流程形同虚设）；审批链条 5 层（变更前置时间被审批拖到一周）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReleaseAudit {
    record ReleaseRecord(String id, String artifact, String operator, String approver,
                         String type, String result, String refs) {
    }

    static final List<ReleaseRecord> LEDGER = List.of(
            new ReleaseRecord("R-3001", "order:2.4.1-142", "ci-bot", "lead-wang",
                    "常规", "成功：金丝雀 1h 全量", "JIRA-1234"),
            new ReleaseRecord("R-3002", "order:2.4.2-143", "ci-bot", "lead-wang",
                    "紧急", "成功：跳过 staging 观察，事故修复", "INC-456"),
            new ReleaseRecord("R-3003", "order:2.4.1-141", "ci-bot", "auto(回滚)",
                    "回滚", "成功：金丝雀错误率触发自动回退", "R-3001"));

    static void ledger() {
        System.out.println("【发布台账：每条生产变更一行】");
        for (ReleaseRecord r : LEDGER) {
            System.out.printf("  %-7s %-18s %-9s %-10s %-4s %s%n        %s%n",
                    r.id(), r.artifact(), r.operator(), r.approver(), r.type(), r.result(), r.refs());
        }
        System.out.println("  可回答：生产现在跑什么？谁批的？出事时改了什么？");
        System.out.println();
    }

    static void emergencyLane() {
        System.out.println("【紧急通道：更快，而不是更少记录】");
        Map<String, String> normal = new LinkedHashMap<>();
        normal.put("门禁", "全量测试 + staging 观察 24h + 金丝雀");
        normal.put("审批", "双人（变更人 + 负责人）");
        Map<String, String> emergency = new LinkedHashMap<>();
        emergency.put("门禁", "全量测试 + 金丝雀（跳过 staging 观察期）");
        emergency.put("审批", "单人 + 事后 24h 内补复盘");
        System.out.println("  常规：" + normal);
        System.out.println("  紧急：" + emergency);
        System.out.println("  共同点：发布单、制品版本、审计记录一样不少");
        System.out.println("  约束：紧急发布占比 > 10% -> 流程有问题，触发复盘");
        System.out.println();
    }

    static void dora() {
        System.out.println("【DORA 四指标：发布工程的体检表】");
        record Metric(String name, String def, String elite, String ours) {
        }
        List<Metric> ms = List.of(
                new Metric("部署频率", "prod 发布次数/天", "按需（日多次）", "2 次/周"),
                new Metric("变更前置时间", "commit -> prod", "< 1 天", "3 天"),
                new Metric("变更失败率", "引发降级/回滚的比例", "< 15%", "8%"),
                new Metric("MTTR", "故障恢复时长", "< 1 小时", "40 分钟"));
        for (Metric m : ms) {
            System.out.printf("  %-12s %-18s 精英=%-14s 我们=%s%n",
                    m.name(), m.def(), m.elite(), m.ours());
        }
        System.out.println("  短板定位：前置时间 3 天主要卡在人工审批 -> 审批最小化");
    }

    public static void main(String[] args) {
        ledger();
        emergencyLane();
        dora();
    }
}
```

**课后练习**
1. 为你的团队设计发布单模板与台账存储（表格或数据库），实现"发布单 -> 制品 -> 提交"三层链接可点击跳转。
2. 统计你团队过去一个月的 DORA 四指标，找出最差一项并给出改进动作与目标值。
3. 思考题：为什么"紧急发布跳过审批但不能跳过记录"？从事后追责与流程信任两个角度分析。

---

### 41-11 综合案例：一条完整的发布流水线

**知识点**
- **把本章串成一条端到端流水线**：提交（Conventional Commits）-> PR 流水线（编译/检查/测试）-> 合入 main -> 构建制品（版本号+签名）-> 自动部署 test -> 冒烟 -> 人工放行 prod -> 金丝雀 1%~100%（自动判据）-> 发布单归档 -> 观察与复盘。**第 37 章的容器、第 41 章的流程在此汇合**。
- **每一步的"通过标准"都要机器可判**：测试通过率 100%、扫描无 Critical、冒烟接口 200、金丝雀错误率相对基线 +2% 以内。**写不进判据的验证就是装饰**。
- **故障演练贯穿**：流水线红灯演练（挂掉一个测试看拦截）、金丝雀自动回退演练（制造错误率飙升）、回滚演练（切回旧制品的实测时长）。**演练过的故障才叫预案，没演练过的叫想象**。
- **一次真实发布的时间预算**：PR 验证 8 分钟、构建 3 分钟、test 冒烟 5 分钟、金丝雀全程 90 分钟——**从 commit 到全量约 2 小时，全程无需盯守**（金丝雀自动判据接管）。
- **发布文化**：发布是日常动作不是仪式——高频小步、失败自动回退、台账自动记录。**发布越频繁，单次风险越小**（变更小、演练多、回滚熟练）。
- 易错点：流水线全绿但没人看过部署结果（验证缺失）；金丝雀判据写了没接自动回退（指标只是装饰）；发布单与制品脱节（审计断链）；把本章当作一次性建设（发布工程是持续运营的）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class PipelineCase {
    enum StepResult {
        PASS, FAIL, ROLLBACK
    }

    record Phase(int no, String name, String action, String gate, StepResult result) {
    }

    static void fullPipeline() {
        System.out.println("【order 服务 2.4.1-142 的完整发布时间线】");
        List<Phase> phases = List.of(
                new Phase(1, "提交", "feat(order): 批量取消 + 单测", "Conventional 格式校验", StepResult.PASS),
                new Phase(2, "PR 流水线", "编译30s + lint/scan60s + 单测180s", "全绿 + 1 人审批", StepResult.PASS),
                new Phase(3, "构建制品", "jar + 镜像，签名入库", "版本号含提交摘要", StepResult.PASS),
                new Phase(4, "test 环境", "自动部署 + 冒烟 5 项接口", "接口全部 200", StepResult.PASS),
                new Phase(5, "staging", "脱敏数据回归 + 压测 P99 达标", "无 Critical 漏洞", StepResult.PASS),
                new Phase(6, "prod 金丝雀", "1% -> 5% -> 25% -> 50% -> 100%",
                        "错误率相对基线 +2% 内", StepResult.PASS),
                new Phase(7, "归档", "发布单 R-3001 自动生成", "制品/审批/证据齐全", StepResult.PASS));
        int minute = 0;
        for (Phase p : phases) {
            System.out.printf("  T+%dmin 步骤%d %-12s %s%n        闸门：%s -> %s%n",
                    minute, p.no(), p.name(), p.action(), p.gate(), p.result());
            minute += switch (p.no()) {
                case 1, 2 -> 8;
                case 3 -> 3;
                case 4, 5 -> 5;
                default -> 90;
            };
        }
        System.out.printf("  commit 到全量约 %d 分钟，全程无需盯守%n", minute);
        System.out.println();
    }

    static void drills() {
        System.out.println("【三项常态化演练】");
        record Drill(String name, String action, String expect, String measured) {
        }
        List<Drill> drills = List.of(
                new Drill("红灯拦截", "在 PR 分支故意提交失败测试", "流水线 FAIL，PR 无法合并",
                        "实测拦截生效"),
                new Drill("金丝雀回退", "向金丝雀实例注入 5xx", "错误率超阈值自动回退并告警",
                        "实测 2 分 10 秒完成回退"),
                new Drill("回滚", "部署上一版本制品", "回滚到稳定版，时长 < 2 分钟",
                        "实测 1 分 30 秒"));
        for (Drill d : drills) {
            System.out.printf("  %-8s %s%n    期望：%s%n    实测：%s%n",
                    d.name(), d.action(), d.expect(), d.measured());
        }
        System.out.println("  演练频率：每迭代一次，结果进发布复盘");
        System.out.println();
    }

    static void timeBudget() {
        System.out.println("【发布时间预算与人工介入点】");
        List<String> rows = List.of(
                "自动段（约 50 分钟）：PR 验证 -> 构建 -> test/staging 部署与验证",
                "人工点 1：prod 放行审批（1 分钟，看到的是自动汇总的变更内容+验证证据）",
                "自动段（约 90 分钟）：金丝雀放量，自动判据接管（暂停/告警/回退）",
                "人工点 2（仅异常）：回退决定或紧急通道");
        for (String r : rows) {
            System.out.println("  " + r);
        }
        System.out.println("  原则：人工只出现在「决策」，一切「执行」都自动化");
    }

    public static void main(String[] args) {
        fullPipeline();
        drills();
        timeBudget();
    }
}
```

**课后练习**
1. 把本章时间线在你的项目真实跑一遍（可以缩小规模），记录每步实际耗时与"通过证据"，形成你的发布手册第一版。
2. 实现金丝雀自动判据与自动回退的闭环（模拟指标流即可），并做一次"注入 5xx"演练记录回退时长。
3. 思考题：为什么说"发布越频繁，单次风险越小"？用本章 DORA 指标与演练机制解释高频发布的风险控制闭环。
