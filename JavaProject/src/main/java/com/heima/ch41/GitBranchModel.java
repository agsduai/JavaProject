/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：41-01
 * 课名：Git 协作模型与分支策略
 * ------------------------------------------------------------
 * 知识点：
 *   - **Git 的核心模型只有四层**：工作区（你正在编辑的文件）、暂存区（`git add` 后的快照）、本地仓库（`git commit` 后的历史）、远程仓库（`git push` 后的共享历史）。所有令新手困惑的命令，都是在问"把什么从一个区搬到另一个区"。
 *   - **分支只是一个 41 字节的指针**：`.git/refs/heads/main` 文件里存着一个 commit 哈希，创建分支≈写一个文件，切换分支才真正动文件树。这解释了为什么 Git 分支"免费"而 SVN 分支昂贵——**分支的便宜催生了分支策略**。
 *   - **团队分支策略的主流选择**：`GitHub Flow`（main + 短命特性分支，适合持续部署）；`GitFlow`（develop/release/hotfix 多长命分支，适合按版本发布）；` trunk-based `（所有人向 main 高频合入，配合特性开关，适合大团队快节奏）。**选择依据是发布节奏，不是公司大小**。
 *   - **短命分支是卫生问题**：特性分支存活超过 2~3 天，与 main 的分歧就会累积到难以合并。大 PR = 大风险 + 长评审。**小步提交、当天合并**是所有策略的共同纪律。
 *   - **合并方式的三种语义**：`merge`（保留分支拓扑，历史真实但杂乱）、`squash merge`（压成一个提交进 main，main 干净）、`rebase`（把提交搬到新基底，线性历史但**改写历史**，只适用于未推送的私有分支）。团队必须统一约定，混用 = 历史混乱。
 *   - **保护分支（branch protection）是策略的强制执行**：main 禁止直接 push、必须 PR + 至少 1 人审批 + CI 绿灯才能合并。没有保护分支的"策略"只是文档。
 *   - **冲突预防优于冲突解决**：频繁 rebase main 到自己分支（每天一次）、拆小 PR、模块所有权清晰化。冲突的本质是**两个人同时改同一处代码没有及时同步**。
 *   - 易错点：在 main 上直接开发（无保护分支时最容易发生）；`git push -f` 到共享分支（同事的提交被覆盖）；长命 release 分支与 develop 双线演化导致合并地狱；把 `git pull`（fetch+merge）当默认习惯产生无意义 merge 提交（改用 `pull --rebase`）。
 * ============================================================
 */

package com.heima.ch41;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的团队写一份分支策略决策书：对比 GitHub Flow 与 GitFlow，依据发布节奏选定一种，并给出分支保护的 5 条机器规则。
 * 2. 本机演练三种合并：创建分支提交 3 次，分别用 merge/squash/rebase 合回 main，用 `git log --graph` 截图对比历史形状。
 * 3. 制造一次冲突并解决：两人分支改同一行，合并后说明冲突的产生条件与你的预防措施。
 * 4. 思考题：为什么"分支很便宜"反而要求"分支很短命"？从分歧累积成本的角度推导小 PR 的价值。
 * ============================================================
 */
