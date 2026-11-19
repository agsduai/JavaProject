/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-05
 * 课名：代码扫描与质量门禁
 * ------------------------------------------------------------
 * 知识点：
 *   - **代码扫描的价值在"增量门禁"**：**存量问题不清零也先拦住"新增问题"**（基线机制）——**否则一上来几千条告警，团队直接忽略**。
 *   - **规则分级**：**BLOCKER**（必须修：硬编码密钥、SQL 拼接）、**CRITICAL**（安全/正确性）、**MAJOR/MINOR**（可维护性）——**门禁只拦 BLOCKER/CRITICAL**，其余给建议。
 *   - **规则实现三档**：**正则/文本匹配**（快但误报）、**AST 分析**（准但复杂）、**数据流分析**（污点追踪，最强）——**本章用正则演示门禁流程，生产用成熟工具**。
 *   - **基线与豁免**：**基线按"规则 + 文件 + 代码片段"生成指纹**，**豁免要写原因与到期时间**（否则永久豁免）——**豁免也是技术债，要可见**。
 *   - 与 36 章的关系：36 章讲"安全编码"，**本章讲"怎么用工具把安全规则自动化、拦住新增"**——**从知道到做到**。
 *   - 易错点：全量扫描阻断（不敢用）；规则太多误报（被关闭）；豁免无期限（技术债堆积）；门禁只跑在 CI 不跑本地（反馈太晚）。
 * ============================================================
 */

package com.heima.ch79;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现四条扫描规则与增量门禁：存量豁免、新增 BLOCKER 直接失败。
 * 2. 给豁免加"到期时间"字段，过期后重新变为新增问题。
 * 3. 思考题：为什么"全量扫描阻断"会导致规则被关闭？从"存量债务 vs 增量门禁"分析。
 * ============================================================
 */
