/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：36-02
 * 课名：SQL 注入与参数化查询
 * ------------------------------------------------------------
 * 知识点：
 *   - **SQL 注入的本质是「数据和代码混在一起」**：把用户输入拼进 SQL 字符串，数据库就无法区分哪部分是结构、哪部分是值。输入里带一个引号，就能提前闭合字符串，把后面的内容变成 SQL 语法。
 *   - **参数化查询（PreparedStatement）解决的是语法层面的问题**：SQL 结构先编译定型，值后绑定，值里有什么字符都只是数据。**这是唯一可靠的解法**，不是「更安全的字符串拼接」。
 *   - **手写转义不可靠**：把 `'` 换成 `''` 只对「字符串字面量」这一种上下文有效。数字上下文（`age = ` + 输入）、`LIKE` 的 `%`、`ORDER BY` 的列名、表名，全都转义不了。
 *   - **占位符不能用在标识符位置**：`ORDER BY ?`、`FROM ?`、`LIMIT ?`（部分驱动可以）都不行。列名、表名、排序方向必须走**白名单**，这是注入的重灾区。
 *   - **`LIKE` 要单独处理**：`%` 和 `_` 是通配符，用户输入里带了会扩大匹配范围。要么转义 `%`/`_`/`\` 并声明 `ESCAPE '\'`，要么接受它只是「模糊匹配」而不影响安全。
 *   - **多租户系统必须把 `tenant_id` 一起绑进条件**：只按 `name` 查，注入一进来就能跨租户读数据。业务条件缺失和注入叠加，后果比单纯注入更严重。
 *   - **`IN` 子句要按数量生成占位符**：`IN (?, ?, ?)`，不能用 `IN ('a,b,c')`（那是一个字符串，不是三个值）。
 *   - **错误信息也是泄露面**：把 `SQLException` 原样返回给前端，等于把表名、列名、数据库版本送给攻击者。对外只回统一错误码，细节写进内部日志。
 *   - **纵深防御**：数据库账号只给 `SELECT/INSERT/UPDATE` 需要的表，不给 `DROP`、不给跨库；应用层加 WAF 只能挡常见 payload，不能当唯一防线；审计慢查询与异常 SQL。
 *   - 易错点：以为 ORM 就安全（`createNativeQuery`、字符串拼接的 `@Query` 一样能注入）；以为 `#{}` 和 `${}` 等价（MyBatis 里 `${}` 是直接拼接）；把「参数化」用在动态排序上（拼不出来就退化成拼接）。
 * ============================================================
 */

package com.heima.ch36;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// ==================== 示例代码 ====================

public class SqlInjectionDemo {
    record User(int id, String name, int age, String role, int tenant) {
    }

    static final List<User> USERS = List.of(
            new User(1, "张三", 28, "user", 7),
            new User(2, "李四", 34, "user", 7),
            new User(3, "王五", 41, "admin", 7),
            new User(4, "赵六", 22, "user", 9),
            new User(5, "孙七", 37, "admin", 9));

    /** 在引号外补空格，让 name='' OR '1'='1' 也能被切分 */
    static String normalize(String s) {
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') {
                inQuote = !inQuote;
                sb.append(ch);
                continue;
            }
            if (!inQuote && (ch == '=' || ch == '<' || ch == '>')) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                sb.append(ch);
                if (i + 1 < s.length() && (s.charAt(i + 1) == '=' || s.charAt(i + 1) == '>')) {
                    sb.append(s.charAt(++i));
                }
                sb.append(' ');
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    static boolean isNumeric(String s) {
        return s != null && s.matches("-?\\d+");
    }

    static String unquote(String s) {
        if (s.length() >= 2 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** 取列值；不是已知列名时，若为数字字面量就原样返回（为了能演示 1=1） */
    static String column(User u, String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "id" -> String.valueOf(u.id());
            case "name" -> u.name();
            case "age" -> String.valueOf(u.age());
            case "role" -> u.role();
            case "tenant_id", "tenant" -> String.valueOf(u.tenant());
            default -> isNumeric(name) ? name : null;
        };
    }

    static boolean compare(User u, String lhs, String op, String rhs) {
        String left = lhs.startsWith("'") ? unquote(lhs) : column(u, lhs);
        if (left == null) {
            return false;
        }
        String right = rhs.startsWith("'") ? unquote(rhs) : rhs;
        int cmp = isNumeric(left) && isNumeric(right)
                ? Long.compare(Long.parseLong(left), Long.parseLong(right))
                : left.compareTo(right);
        return switch (op) {
            case "=" -> cmp == 0;
            case "<>" -> cmp != 0;
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }

    static boolean evalCondition(User u, String cond) {
        String c = normalize(cond).trim();
        for (String op : new String[]{"<>", ">=", "<=", "=", ">", "<"}) {
            int i = c.indexOf(" " + op + " ");
            if (i < 0) {
                continue;
            }
            String lhs = c.substring(0, i).trim();
            String rhs = c.substring(i + op.length() + 2).trim();
            return compare(u, lhs, op, rhs);
        }
        return false;
    }

    /** 极简 WHERE 求值：OR 分组，组内 AND。只为演示，真实解析器必须做词法分析 */
    static boolean evalWhere(User u, String where) {
        String w = normalize(where);
        for (String orPart : w.split("(?i) OR ")) {
            boolean all = true;
            for (String andPart : orPart.split("(?i) AND ")) {
                if (!evalCondition(u, andPart)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    static List<User> run(String where) {
        List<User> out = new ArrayList<>();
        for (User u : USERS) {
            if (evalWhere(u, where)) {
                out.add(u);
            }
        }
        return out;
    }

    static String show(List<User> rows) {
        StringBuilder sb = new StringBuilder();
        for (User u : rows) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(u.name()).append("(t").append(u.tenant()).append(')');
        }
        return rows.isEmpty() ? "0 行" : rows.size() + " 行：" + sb;
    }

    static void whatHappens() {
        System.out.println("【同一句查询，两种写法】");
        String normal = "张三";
        String evil = "' OR '1'='1";

        System.out.println("  正常输入 " + normal + "：");
        System.out.println("    拼接  WHERE name = '" + normal + "'");
        System.out.println("    结果  " + show(run("name = '" + normal + "'")));

        System.out.println("  恶意输入 " + evil + "：");
        String concat = "name = '" + evil + "'";
        System.out.println("    拼接  WHERE " + concat);
        System.out.println("    结果  " + show(run(concat)));

        System.out.println("  参数化 WHERE name = ?  绑定 \"" + evil + "\"");
        List<User> bound = new ArrayList<>();
        for (User u : USERS) {
            if (u.name().equals(evil)) {
                bound.add(u);
            }
        }
        System.out.println("    结果  " + show(bound) + "  <- 整段被当成一个字符串值");
        System.out.println();
    }

    static void multiTenantBreach() {
        System.out.println("【多租户隔离被一条注入打穿】");
        String evil = "' OR tenant_id=9 OR name='";
        String concat = "name = '" + evil + "' AND tenant_id = 7";
        System.out.println("  业务意图：只看本租户 7 的数据");
        System.out.println("  拼接后  WHERE " + concat);
        System.out.println("  结果    " + show(run(concat)) + "  <- 租户 9 的数据也出来了");
        System.out.println("  正确写法：WHERE name = ? AND tenant_id = ?  （两个都绑，租户号不接受前端传）");
        System.out.println();
    }

    static void quoteEscapingIsNotEnough() {
        System.out.println("【只转义引号救不了数字上下文】");
        String evil = "1 OR 1=1";
        System.out.println("  输入 " + evil + "（一个引号都没有）");
        System.out.println("  拼进 age = " + evil + "  ->  WHERE age = " + evil);
        System.out.println("  结果 " + show(run("age = " + evil)));
        System.out.println("  把 ' 换成 '' 对这段输入毫无作用，因为它根本不靠引号闭合");
        System.out.println("  同理：ORDER BY、LIMIT、表名、列名，全都没有「值」这个上下文");
        System.out.println();
    }

    static void injectionThatNarrows() {
        System.out.println("【注入不只是「拉全表」，还能精确捞管理员】");
        String evil = "' OR role = 'admin";
        String concat = "name = '" + evil + "'";
        System.out.println("  WHERE " + concat);
        System.out.println("  结果 " + show(run(concat)) + "  <- 只要 admin，越精准越难被发现");
        System.out.println("  这类慢速、低频的定向查询，靠 WAF 拦不住，只能靠参数化");
        System.out.println();
    }

    static void parameterizedSemantics() {
        System.out.println("【参数化的本质：结构先编译，值后绑定】");
        String sql = "SELECT id, name FROM users WHERE name = ? AND tenant_id = ?";
        int marks = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                marks++;
            }
        }
        System.out.println("  语句 " + sql);
        System.out.println("  占位符 " + marks + " 个 -> 必须绑定 " + marks + " 个参数，少一个就报错");
        System.out.println("  setString(1, \"张三\")、setInt(2, 7)：类型由调用方声明，数据库按值处理");
        System.out.println("  绑定 \"" + "' OR '1'='1" + "\" 时，数据库收到的就是一个含引号的普通字符串");
        System.out.println("  即使值里出现 ; DROP TABLE -- 也只是这个字符串的一部分");
        System.out.println();
    }

    static final List<String> SORTABLE = List.of("id", "age", "name");

    static String safeOrderBy(String input) {
        String col = input == null ? "id" : input.trim().toLowerCase(Locale.ROOT);
        return SORTABLE.contains(col) ? col : "id";
    }

    static void nonParameterizableParts() {
        System.out.println("【拼不进去的部分：白名单，不是转义】");
        String[] inputs = {"age", "name", "id; DROP TABLE users", "(SELECT 1)", "price desc"};
        for (String in : inputs) {
            String col = safeOrderBy(in);
            System.out.printf("  ORDER BY 输入 %-24s -> 实际使用 %-6s %s%n",
                    "\"" + in + "\"", col, col.equals(in.trim()) ? "命中白名单" : "拒绝，回落默认");
        }
        System.out.println("  排序方向也一样：只接受 ASC/DESC 两个常量");
        System.out.println();
    }

    static void likeTrap() {
        System.out.println("【LIKE 的 % 与 _ 是通配符】");
        String[] inputs = {"张", "%", "_", "%三%"};
        for (String in : inputs) {
            String escaped = in.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            System.out.printf("  用户输入 %-6s -> 绑定 %-8s （SQL 里写 LIKE ? ESCAPE '\\'）%n",
                    "\"" + in + "\"", "\"" + escaped + "\"");
        }
        System.out.println("  不转义时，输入一个 % 就等于「匹配所有」，可能被用来刷全表");
        System.out.println();
    }

    static void layeredDefense() {
        System.out.println("【参数化之外还要做的四件事】");
        String[][] items = {
                {"最小权限", "应用账号只给 SELECT/INSERT/UPDATE，不给 DROP、不给跨库"},
                {"错误处理", "对外统一错误码，堆栈与 SQL 只进内部日志"},
                {"输入约束", "长度、类型、枚举值先校验，减少奇怪输入的面"},
                {"审计告警", "监控返回行数异常放大的查询，比如一次返回 10 万行"},
        };
        for (String[] i : items) {
            System.out.printf("  %-10s %s%n", i[0], i[1]);
        }
        System.out.println("  WAF 只能挡已知 payload，参数化才是根治");
        System.out.println();
    }

    public static void main(String[] args) {
        whatHappens();
        multiTenantBreach();
        quoteEscapingIsNotEnough();
        injectionThatNarrows();
        parameterizedSemantics();
        nonParameterizableParts();
        likeTrap();
        layeredDefense();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `evalWhere` 加上括号支持（`(a OR b) AND c`），体会为什么「自己写 SQL 解析器」是个坏主意，以及真实数据库为什么必须用预编译。
 * 2. 实现 `inClause(int n)` 生成 `IN (?, ?, ...)`，再写一个方法按列表长度动态绑定，比较它和 `IN ('a,b,c')` 的行为差异。
 * 3. 写一个静态检查脚本：扫描 Java 源码里 `+ " WHERE`、`"SELECT` 与变量拼接的行，输出可疑位置清单。
 * 4. 思考题：为什么 `ORDER BY ?` 在多数驱动里不生效？如果框架允许你把列名当参数传，风险在哪里？
 * ============================================================
 */
