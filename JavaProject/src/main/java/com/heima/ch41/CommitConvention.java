/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：41-02
 * 课名：提交规范与变更追溯
 * ------------------------------------------------------------
 * 知识点：
 *   - **提交信息是变更的元数据，不是日记**：半年后排查"这行代码为什么这么写"，唯一入口是提交历史。规范的提交信息让 `git log` 可以机读、可检索、可生成变更日志。
 *   - **Conventional Commits 是事实标准**：`type(scope): subject`，如 `feat(order): 支持批量取消`。type 固定枚举：feat/fix/refactor/perf/test/docs/chore/ci。**机读的价值**：脚本可以按 type 自动生成 CHANGELOG、自动算版本号（feat → minor，fix → patch，breaking → major）。
 *   - **一个提交做一件事（原子提交）**：提交是回滚与 review 的最小单位。"重构 + 修 bug + 改格式"混在一个提交里，出问题时无法单独回滚那 20 行修复。**提交粒度 = 事后追溯粒度**。
 *   - **subject 写"什么变了"而不是"我做了什么"**：`fix: 修复订单金额计算` 差，`fix(order): 金额计算未考虑优惠券叠加导致多扣` 好——后者包含影响范围与根因方向。首行 50 字内，正文空一行后写动机与副作用。
 *   - **提交与需求/事故的关联**：正文或脚注引用工单号（`Refs: JIRA-1234`）与事故编号。事故复盘时 `git log --grep=INC-456` 直接拉出全部相关变更——这是发布审计（41-10）的数据基础。
 *   - **坏味道提交的自动拦截**：commit-msg 钩子或 CI 检查提交格式；`git commit -m "fix"` / `"update"` / `"改好了" 直接拒绝。规范靠工具强制，不靠自觉。
 *   - **git blame 的正确用法**：`blame` 只告诉你谁最后改了这行，**配合提交信息才知道为什么**。没有规范提交信息的 blame 是没有线索的现场。
 *   - 易错点：一次提交改 30 个文件混 5 个意图；用 `git commit -am` 顺手提交了调试代码与临时文件；提交信息用"修复bug"（半年后等于没有）；rebase 后 force push 到共享分支丢失他人提交。
 * ============================================================
 */

package com.heima.ch41;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给你的项目写 commit-msg 钩子（bash 或 Java 都可），实现格式校验与 72 字限制，并验证 3 个合法与 3 个非法提交。
 * 2. 用脚本把过去一个月的提交按 Conventional Commits 生成 CHANGELOG，统计非法格式提交的比例，作为团队改进基线。
 * 3. 思考题：为什么"原子提交"是回滚与 review 的基础？举一个混合提交导致无法精准回滚的真实场景。
 * ============================================================
 */
