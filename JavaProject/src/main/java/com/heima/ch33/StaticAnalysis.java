/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-09
 * 课名：静态检查：规范、坏味道与自定义规则引擎
 * ------------------------------------------------------------
 * 知识点：
 *   - **静态检查**在**不运行代码**的前提下发现问题，能在提交前拦住一大类缺陷：空指针风险、资源未关闭、`equals`/`hashCode` 不配对、可变静态字段、`catch` 空块、魔法值、命名不规范。
 *   - 工具分工：
 *   - **Checkstyle**：纯风格（缩进、命名、行宽、import 顺序、JavaDoc）。快，但只能管"形"。
 *   - **SpotBugs**：字节码级缺陷检测（NPE 风险、`equals` 未覆盖 `hashCode`、`Stream` 未关闭、锁未释放）。能管"实"。
 *   - **PMD**：源码级坏味道（圈复杂度、重复代码、过长方法、未使用变量）。
 *   - **ErrorProne / SonarQube**：编译期/平台级，规则最全，Sonar 还能做质量门禁。
 *   - **圈复杂度**（Cyclomatic Complexity）= 判定点数量 + 1。经验阈值：方法 > 10 就该拆，> 15 几乎必然难测。它是"可测性"的量化指标。
 *   - **重复代码**检测思路：滑动窗口 + 归一化（去空白/去注释/变量名归一）+ 哈希比对。重复率高的地方，改一处漏一处。
 *   - 规则引擎的最小实现：**解析**（按行/按 token）→ **匹配**（正则或 AST 访问）→ **报告**（文件:行:规则:说明）→ **分级**（ERROR 阻断构建，WARN 仅提示）。
 *   - **质量门禁**（quality gate）：把静态检查结果变成"能不能合并"的硬条件——`ERROR = 0`、圈复杂度超标的文件数不增加、新增代码覆盖率 ≥ 80%。
 *   - 规则要**可执行、可解释、可豁免**。豁免必须有理由和责任人（`@SuppressWarnings("rule")` + 注释说明），否则规则会被集体绕过。
 *   - 渐进式落地：先只开 ERROR 级别的少数规则（空 `catch`、资源未关闭、`equals/hashCode`），全绿后再逐步加 WARN，最后接入 CI 阻断。一次性开 200 条规则必然被抵制。
 *   - 易错：规则太多导致 `// CHECKSTYLE:OFF` 满天飞；只看报告不修（技术债累积）；把风格问题和缺陷问题混在一个等级；自研规则正则写得太宽（误报率高）或太窄（漏报）。
 * ============================================================
 */

package com.heima.ch33;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// ==================== 示例代码 ====================

public class StaticAnalysis {
    enum Severity {
        ERROR, WARN, INFO
    }

    /** 一条规则 */
    static class Rule {
        final String id;
        final String desc;
        final Severity severity;
        final Pattern pattern;

        Rule(String id, String desc, Severity severity, String regex) {
            this.id = id;
            this.desc = desc;
            this.severity = severity;
            this.pattern = Pattern.compile(regex);
        }
    }

    /** 一条发现 */
    static class Finding {
        final String file;
        final int line;
        final Rule rule;
        final String snippet;

        Finding(String file, int line, Rule rule, String snippet) {
            this.file = file;
            this.line = line;
            this.rule = rule;
            this.snippet = snippet;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s:%d %s | %s", rule.severity, file, line,
                    rule.id, snippet.trim());
        }
    }

    static final List<Rule> RULES = List.of(
            new Rule("EMPTY_CATCH", "空的 catch 块会吞掉异常", Severity.ERROR,
                    "^\\s*catch\\s*\\([^)]*\\)\\s*\\{\\s*$"),
            new Rule("SYSTEM_OUT", "生产代码不应直接 System.out", Severity.WARN,
                    "System\\.(out|err)\\.print"),
            new Rule("MAGIC_NUMBER", "出现魔法数字，应提取常量", Severity.INFO,
                    "[^\\w.]\\d{3,}[^\\w.]"),
            new Rule("TODO_FIXME", "遗留 TODO/FIXME", Severity.INFO,
                    "//\\s*(TODO|FIXME)"),
            new Rule("THREAD_SLEEP", "测试/生产代码里的 sleep 是 flaky 源头", Severity.WARN,
                    "Thread\\.sleep\\s*\\("),
            new Rule("CATCH_GENERIC", "catch Exception 过宽", Severity.WARN,
                    "catch\\s*\\(\\s*Exception\\s+\\w+\\s*\\)")
    );

    /** 迷你规则引擎 */
    static class Analyzer {
        final List<Finding> findings = new ArrayList<>();
        final Map<String, Integer> perRule = new LinkedHashMap<>();

        void analyze(Path file) throws IOException {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String name = file.getFileName().toString();
            for (int i = 0; i < lines.size(); i++) {
                String text = lines.get(i);
                String stripped = stripComment(text);
                for (Rule rule : RULES) {
                    if (rule.pattern.matcher(stripped).find()) {
                        if ("EMPTY_CATCH".equals(rule.id) && !isEmptyCatchBlock(lines, i)) {
                            continue;
                        }
                        findings.add(new Finding(name, i + 1, rule, text));
                        perRule.merge(rule.id, 1, Integer::sum);
                    }
                }
            }
        }

        /** 判断 catch 行之后是否紧跟空块 */
        static boolean isEmptyCatchBlock(List<String> lines, int catchLine) {
            for (int j = catchLine + 1; j < Math.min(lines.size(), catchLine + 3); j++) {
                String t = lines.get(j).trim();
                if (t.isEmpty()) {
                    continue;
                }
                return t.startsWith("}") || t.startsWith("//") || t.startsWith("/*");
            }
            return false;
        }

        static String stripComment(String s) {
            int idx = s.indexOf("//");
            return idx >= 0 ? s.substring(0, idx) : s;
        }

        /** 圈复杂度：判定点 + 1 */
        static int cyclomatic(String source) {
            String[] keywords = {"if ", "else if", "for ", "while ", "case ", "catch ", "&&", "||", "?"};
            int score = 1;
            for (String k : keywords) {
                int from = 0;
                while (true) {
                    int idx = source.indexOf(k, from);
                    if (idx < 0) {
                        break;
                    }
                    score++;
                    from = idx + k.length();
                }
            }
            return score;
        }

        /** 重复代码：按 6 行滑动窗口做归一化哈希 */
        static Map<String, List<Integer>> duplicateBlocks(List<String> lines) {
            Map<String, List<Integer>> map = new HashMap<>();
            int window = 6;
            for (int i = 0; i + window <= lines.size(); i++) {
                StringBuilder sb = new StringBuilder();
                boolean meaningful = false;
                for (int j = i; j < i + window; j++) {
                    String t = lines.get(j).trim().replaceAll("\\s+", " ");
                    if (t.isEmpty() || t.startsWith("//") || t.equals("{") || t.equals("}")) {
                        continue;
                    }
                    meaningful = true;
                    sb.append(t).append(';');
                }
                if (meaningful) {
                    map.computeIfAbsent(sb.toString(), k -> new ArrayList<>()).add(i + 1);
                }
            }
            map.entrySet().removeIf(e -> e.getValue().size() < 2);
            return map;
        }
    }

    static final String SAMPLE = """
            public class BadSample {
                private static int total;

                public int calc(int a) {
                    try {
                        System.out.println("计算中");
                        Thread.sleep(100);
                    } catch (Exception e) {
                    }
                    if (a > 1000) {
                        total = a * 2;
                    }
                    // TODO 这里要改成配置
                    return total;
                }

                public int calc2(int a) {
                    try {
                        System.out.println("计算中");
                        Thread.sleep(100);
                    } catch (Exception e) {
                    }
                    if (a > 1000) {
                        total = a * 2;
                    }
                    return total;
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("static-");
        Path file = dir.resolve("BadSample.java");
        Files.writeString(file, SAMPLE, StandardCharsets.UTF_8);

        try {
            System.out.println("=== 1. 扫描示例文件 ===");
            Analyzer analyzer = new Analyzer();
            analyzer.analyze(file);

            for (Finding f : analyzer.findings) {
                System.out.println("  " + f);
            }
            System.out.println("  共 " + analyzer.findings.size() + " 条发现");

            System.out.println();
            System.out.println("=== 2. 按规则汇总 ===");
            analyzer.perRule.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey()))
                    .forEach(e -> System.out.printf("  %-14s %d 次%n", e.getKey(), e.getValue()));

            System.out.println();
            System.out.println("=== 3. 质量门禁判定 ===");
            long errors = analyzer.findings.stream()
                    .filter(f -> f.rule.severity == Severity.ERROR).count();
            long warns = analyzer.findings.stream()
                    .filter(f -> f.rule.severity == Severity.WARN).count();
            long infos = analyzer.findings.stream()
                    .filter(f -> f.rule.severity == Severity.INFO).count();
            System.out.printf("  ERROR=%d WARN=%d INFO=%d%n", errors, warns, infos);
            boolean gatePass = errors == 0;
            System.out.println("  门禁（ERROR 必须为 0）: " + (gatePass ? "PASS" : "BLOCKED"));
            if (!gatePass) {
                System.out.println("  阻断原因：存在空 catch 块，异常被吞掉，测试无法发现问题");
            }

            System.out.println();
            System.out.println("=== 4. 圈复杂度 ===");
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String whole = String.join("\n", lines);
            int cc = Analyzer.cyclomatic(whole);
            System.out.println("  整个文件圈复杂度 = " + cc);
            System.out.println("  单方法阈值参考：<=10 良好，11~15 需关注，>15 应拆分");

            System.out.println();
            System.out.println("=== 5. 重复代码检测（6 行窗口）===");
            Map<String, List<Integer>> dup = Analyzer.duplicateBlocks(lines);
            if (dup.isEmpty()) {
                System.out.println("  未发现重复块");
            } else {
                for (Map.Entry<String, List<Integer>> e : dup.entrySet()) {
                    System.out.println("  重复块出现在行 " + e.getValue()
                            + "，片段: " + e.getKey().substring(0, Math.min(60, e.getKey().length())) + "...");
                }
                System.out.println("  calc 与 calc2 的 try/catch 逻辑完全重复 -> 应抽成方法");
            }

            System.out.println();
            System.out.println("=== 6. 工具分工 ===");
            String[][] t = {
                    {"Checkstyle", "源码风格", "命名、缩进、行宽、import", "快，只管形"},
                    {"SpotBugs", "字节码缺陷", "NPE、equals/hashCode、资源泄漏", "能管实"},
                    {"PMD", "源码坏味道", "圈复杂度、重复代码、未用变量", "规则可自定义"},
                    {"ErrorProne", "编译期", "编译即报，接入零成本", "需改构建"},
                    {"SonarQube", "平台", "规则最全 + 质量门禁 + 趋势", "需部署"}
            };
            System.out.printf("  %-12s %-12s %-34s %s%n", "工具", "层次", "擅长", "特点");
            for (String[] r : t) {
                System.out.printf("  %-12s %-12s %-34s %s%n", r[0], r[1], r[2], r[3]);
            }

            System.out.println();
            System.out.println("=== 7. 渐进式落地建议 ===");
            String[] steps = {
                    "第 1 周：只开 ERROR 三条（空 catch、资源未关闭、equals/hashCode）",
                    "第 2 周：全绿后接入 CI，PR 时阻断",
                    "第 3 周：加 WARN（System.out、sleep、catch Exception）",
                    "第 4 周：加圈复杂度与重复率阈值，只对新增代码生效",
                    "持续：豁免必须写理由，每季度清理一次豁免清单"
            };
            for (String s : steps) {
                System.out.println("  - " + s);
            }
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 新增一条规则"公开方法缺少 JavaDoc"，用"上一行是否为 `* /`"判断，注意排除 `@Override` 方法。
 * 2. 把 `cyclomatic` 改成按方法统计（用大括号配对找方法边界），输出每个方法的复杂度排名。
 * 3. 说明为什么"一次性开启 200 条规则"会导致规则被集体绕过，给出你所在团队的两阶段落地计划。
 * ============================================================
 */
