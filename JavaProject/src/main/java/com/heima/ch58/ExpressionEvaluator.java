/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：58-03
 * 课名：规则表达式与求值器
 * ------------------------------------------------------------
 * 知识点：
 *   - **手写表达式求值器是理解规则引擎的最佳路径**：词法分析（数字/标识符/运算符）-> 递归下降解析（或调度场算法）-> 语法树 -> 求值（携带特征上下文）。**掌握后 Drools/QLExpress 只是"加强版"**。
 *   - **运算符优先级与结合性**：`!` > 比较（`> >= < <= == !=`）> `&&` > `||`——**优先级错了规则行为就错了**，必须与文档一致（表达式语言要写语法手册）。
 *   - **短路求值是语义而非优化**：`a != null && a.x > 0` 依赖短路避免 NPE——**求值器必须实现短路**（`&&` 左边 false 时不求右边），否则规则里无法写空值保护。
 *   - **类型处理**：数值比较（整数/小数）、字符串比较、布尔值；**类型不匹配要报明确错误**（而不是静默 false）——规则写错的成本远高于代码写错（业务方直接改）。
 *   - **安全边界**：**绝不用脚本引擎执行用户输入**（36-10 注入的规则版）——自研求值器只支持白名单语法（无方法调用/无反射/无 IO），这是规则引擎与脚本引擎的根本区别。
 *   - 易错点：不支持短路的求值器（空值保护写不了）；运算符优先级错（`a || b && c` 结果不符直觉）；数字用 double 比较（精度问题，金额要整数分）；支持任意方法调用（注入风险）。
 * ============================================================
 */

package com.heima.ch58;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ExpressionEvaluator {
    /** 词法单元 */
    enum Kind { NUM, IDENT, OP, LPAREN, RPAREN }

    record Token(Kind kind, String text) {
    }

    /** 词法分析：支持数字、标识符、比较与逻辑运算符、括号 */
    static List<Token> lex(String src) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isDigit(c)) {
                int j = i;
                while (j < src.length() && Character.isDigit(src.charAt(j))) {
                    j++;
                }
                out.add(new Token(Kind.NUM, src.substring(i, j)));
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < src.length()
                        && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_')) {
                    j++;
                }
                out.add(new Token(Kind.IDENT, src.substring(i, j)));
                i = j;
            } else if (c == '(') {
                out.add(new Token(Kind.LPAREN, "("));
                i++;
            } else if (c == ')') {
                out.add(new Token(Kind.RPAREN, ")"));
                i++;
            } else {
                String two = i + 1 < src.length() ? src.substring(i, i + 2) : "";
                if (two.equals(">=") || two.equals("<=") || two.equals("==") || two.equals("!=")
                        || two.equals("&&") || two.equals("||")) {
                    out.add(new Token(Kind.OP, two));
                    i += 2;
                } else {
                    out.add(new Token(Kind.OP, String.valueOf(c)));
                    i++;
                }
            }
        }
        return out;
    }

    /** 求值（递归下降 + 短路）：ctx 提供特征值 */
    static class Parser {
        final List<Token> tokens;
        int pos = 0;
        final Map<String, Object> ctx;

        Parser(List<Token> tokens, Map<String, Object> ctx) {
            this.tokens = tokens;
            this.ctx = ctx;
        }

        boolean parse() {
            boolean v = or();
            if (pos != tokens.size()) {
                throw new IllegalArgumentException("表达式未消费完：" + tokens.get(pos).text());
            }
            return v;
        }

        boolean or() {
            boolean left = and();
            while (peekOp("||")) {
                pos++;
                boolean right = and();       // 注意：此处已求值右侧，真实短路见下
                left = left || right;
            }
            return left;
        }

        boolean and() {
            boolean left = cmp();
            while (peekOp("&&")) {
                pos++;
                if (!left) {
                    skipExpr();              // 短路：跳过右侧求值
                    return false;
                }
                left = cmp();
            }
            return left;
        }

        /** 跳过表达式直到同层的 || 或结尾（简化实现） */
        void skipExpr() {
            int depth = 0;
            while (pos < tokens.size()) {
                Token t = tokens.get(pos);
                if (t.kind() == Kind.LPAREN) {
                    depth++;
                } else if (t.kind() == Kind.RPAREN) {
                    depth--;
                } else if (depth == 0 && t.kind() == Kind.OP && t.text().equals("||")) {
                    return;
                }
                pos++;
            }
        }

        boolean cmp() {
            if (pos < tokens.size() && tokens.get(pos).kind() == Kind.LPAREN) {
                pos++;                            // 进入括号
                boolean inner = or();
                if (pos >= tokens.size() || tokens.get(pos).kind() != Kind.RPAREN) {
                    throw new IllegalArgumentException("缺少右括号");
                }
                pos++;
                return inner;
            }
            long a = num();
            if (pos < tokens.size() && tokens.get(pos).kind() == Kind.OP) {
                String op = tokens.get(pos).text();
                if (List.of(">", ">=", "<", "<=", "==", "!=").contains(op)) {
                    pos++;
                    long b = num();
                    return switch (op) {
                        case ">" -> a > b;
                        case ">=" -> a >= b;
                        case "<" -> a < b;
                        case "<=" -> a <= b;
                        case "==" -> a == b;
                        default -> a != b;
                    };
                }
            }
            return a != 0;
        }

        long num() {
            Token t = tokens.get(pos++);
            if (t.kind() == Kind.NUM) {
                return Long.parseLong(t.text());
            }
            if (t.kind() == Kind.IDENT) {
                Object v = ctx.get(t.text());
                if (v instanceof Number n) {
                    return n.longValue();
                }
                if (v instanceof Boolean b) {
                    return b ? 1 : 0;
                }
                return 0;
            }
            throw new IllegalArgumentException("意外符号：" + t.text());
        }

        boolean peekOp(String op) {
            return pos < tokens.size() && tokens.get(pos).kind() == Kind.OP
                    && tokens.get(pos).text().equals(op);
        }
    }

    static boolean evaluate(String expr, Map<String, Object> ctx) {
        return new Parser(lex(expr), ctx).parse();
    }

    public static void main(String[] args) {
        var ctx = Map.<String, Object>of("amount", 8000L, "hour", 3L, "newDevice", true);
        System.out.println("  amount > 5000 && hour < 6          -> "
                + evaluate("amount > 5000 && hour < 6", ctx));
        System.out.println("  amount >= 10000 || newDevice != 0  -> "
                + evaluate("amount >= 10000 || newDevice != 0", ctx));
        System.out.println("  (amount < 100) && hour > 10        -> "
                + evaluate("(amount < 100) && hour > 10", ctx));
        System.out.println();
        System.out.println("  安全边界：只支持白名单语法（无方法调用/无反射/无 IO）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整实现求值器：补全 `||` 的短路（当前 `or()` 会先求值右侧再判断，请改正）、`!` 取反、括号优先级，并写 15 条表达式测试。
 * 2. 增加类型检查：字符串特征与数字比较时报明确错误（而不是静默 false），用测试验证。
 * 3. 思考题：为什么规则引擎"绝不能执行用户输入"？构造一个"合法表达式白名单之外的注入"尝试并说明防御点。
 * ============================================================
 */
