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
