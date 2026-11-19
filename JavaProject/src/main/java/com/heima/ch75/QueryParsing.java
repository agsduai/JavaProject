/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-03
 * 课名：查询解析
 * ------------------------------------------------------------
 * 知识点：
 *   - **GraphQL 查询是一棵树**：`{ product(id:1) { name price } }` 解析成 AST（SelectionSet：字段 + 参数 + 子选择集）——**解析器（parser）产出 AST，执行器（executor）按 AST 取数**。
 *   - **解析的三类节点**：**Operation**（query/mutation/subscription + 变量声明）、**Field**（字段名 + 别名 + 参数 + 子选择集）、**Fragment**（可复用的字段集合，`...Frag`）——**Fragment 是客户端复用查询片段的关键**。
 *   - **别名（alias）解决"同字段多次取不同参数"**：`a: product(id:1) { name } b: product(id:2) { name }`——**响应里用别名作键**，这是 GraphQL 灵活性的来源。
 *   - **变量（variables）是参数化的关键**：查询里写 `product(id: $id)`，变量单独传——**避免字符串拼接（注入风险 75-09）**，也便于服务端做持久化查询。
 *   - 与 64/70 章的关系：变量等同于 REST 的参数化（64-03），**查询字符串本身不应含用户数据**。
 *   - 易错点：用字符串拼接把用户输入塞进 query（注入）；不用变量（每个请求生成新查询，缓存失效）；不处理 alias（同字段多参数写不出来）；不支持 Fragment（客户端查询冗长）。
 * ============================================================
 */

package com.heima.ch75;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class QueryParsing {
    sealed interface Node permits Field, FragmentSpread {
    }

    record Field(String alias, String name, Map<String, String> args, List<Node> children)
            implements Node {
        String key() {
            return alias != null ? alias : name;
        }
    }

    record FragmentSpread(String name) implements Node {
    }

    /** 极简解析：支持 { a: field(arg: "v") { sub } ...Frag } */
    static class Parser {
        final String src;
        int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        String readName() {
            skipWs();
            int start = pos;
            while (pos < src.length()
                    && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
                pos++;
            }
            return src.substring(start, pos);
        }

        String peek() {
            skipWs();
            return pos < src.length() ? String.valueOf(src.charAt(pos)) : "";
        }

        void expect(String c) {
            skipWs();
            if (pos >= src.length() || !src.startsWith(c, pos)) {
                throw new IllegalArgumentException("期望 " + c + " 于 " + pos);
            }
            pos += c.length();
        }

        List<Node> parseSelectionSet() {
            expect("{");
            List<Node> nodes = new ArrayList<>();
            while (true) {
                skipWs();
                if (pos >= src.length() || src.charAt(pos) == '}') {
                    break;
                }
                if (src.startsWith("...", pos)) {
                    pos += 3;
                    nodes.add(new FragmentSpread(readName()));
                    continue;
                }
                String first = readName();
                skipWs();
                String alias = null;
                String name = first;
                if (pos < src.length() && src.charAt(pos) == ':') {
                    pos++;
                    alias = first;
                    name = readName();
                    skipWs();
                }
                Map<String, String> args = new LinkedHashMap<>();
                if (pos < src.length() && src.charAt(pos) == '(') {
                    pos++;
                    while (pos < src.length() && src.charAt(pos) != ')') {
                        String k = readName();
                        expect(":");
                        skipWs();
                        String v;
                        if (pos < src.length() && src.charAt(pos) == '"') {
                            pos++;
                            int end = src.indexOf('"', pos);
                            v = src.substring(pos, end);
                            pos = end + 1;
                        } else {
                            v = readName();
                        }
                        args.put(k, v);
                        skipWs();
                        if (pos < src.length() && src.charAt(pos) == ',') {
                            pos++;
                        }
                    }
                    expect(")");
                }
                skipWs();
                List<Node> children = new ArrayList<>();
                if (pos < src.length() && src.charAt(pos) == '{') {
                    children = parseSelectionSet();
                }
                nodes.add(new Field(alias, name, args, children));
            }
            expect("}");
            return nodes;
        }
    }

    static void printTree(List<Node> nodes, int depth) {
        for (Node n : nodes) {
            String pad = "  ".repeat(depth);
            if (n instanceof Field f) {
                System.out.printf("%s%s%s%s%n", pad,
                        f.alias() == null ? "" : f.alias() + ": ",
                        f.name(), f.args().isEmpty() ? "" : " " + f.args());
                printTree(f.children(), depth + 1);
            } else if (n instanceof FragmentSpread fs) {
                System.out.printf("%s...%s%n", pad, fs.name());
            }
        }
    }

    public static void main(String[] args) {
        String query = """
                {
                  a: product(id: "1") {
                    name
                    price
                    brand { name }
                  }
                  b: product(id: "2") { name }
                  ...Common
                }
                """;
        System.out.println("【原始查询】");
        query.lines().forEach(l -> System.out.println("  " + l));
        List<Node> ast = new Parser(query).parseSelectionSet();
        System.out.println("【AST】");
        printTree(ast, 1);

        System.out.println();
        System.out.println("【要点】");
        System.out.println("  1) 别名字段：a/b 作为响应键（同字段不同参数）");
        System.out.println("  2) 子选择集：brand { name } 只取品牌名字");
        System.out.println("  3) Fragment：...Common 复用字段集合");
        System.out.println("  4) 参数应通过变量传递（$id），而不是字符串拼接（75-09）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展解析器：支持变量声明（`query Q($id: ID!)`）与变量引用（`id: $id`），用别名查询验证。
 * 2. 实现 Fragment 展开：把 `...Common` 替换为对应字段集，输出完全展开后的 AST。
 * 3. 思考题：为什么"用户输入拼进查询字符串"是注入漏洞？从"查询语法即代码"分析。
 * ============================================================
 */
