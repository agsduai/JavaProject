### 36-01 安全编码原则与威胁建模

**知识点**
- **安全不是一个功能，而是一条约束**：功能是「登录能成功」，安全是「只有本人能登录成功」。写代码时每加一个入口，都要问一句「谁能调用它、他能拿到什么」。
- **STRIDE 是最好用的威胁清单**：仿冒（Spoofing）、篡改（Tampering）、抵赖（Repudiation）、信息泄露（Information Disclosure）、拒绝服务（Denial of Service）、权限提升（Elevation of Privilege）。对着这六项逐条问，比凭空想「有什么漏洞」有效得多。
- **先列资产再谈威胁**：不知道要保护什么，就没法判断什么算「泄露」。资产按影响打分（资金、合规、声誉），威胁按「可能性 × 影响」算风险，优先修高风险的。
- **信任边界是安全设计的骨架**：数据每跨过一次边界（浏览器→网关、网关→服务、服务→数据库、服务→第三方），就必须重新校验一次。**内网不等于可信**，这是大量事故的共同前提。
- **纵深防御（defense in depth）**：输入校验、输出编码、认证、授权、数据层约束、检测告警，每一层都可能被绕过，所以不能只靠一层。只做输入校验的系统，漏一个字段就全线失守。
- **默认拒绝（fail closed）**：权限判断里「拿不准」时应当拒绝，而不是放行。`if (user.isAdmin()) allow();` 后面忘了写 `else deny()`，就是默认放行。
- **最小权限**：数据库账号只给需要的表和操作；服务账号只给需要的接口；容器不以 root 运行；密钥只在需要的进程可见。
- **不要自己发明加密**：密码哈希用 bcrypt/Argon2/PBKDF2，对称加密用 AES-GCM，随机数用 `SecureRandom`。自己拼的 XOR、自己设计的「加盐规则」基本都是错的。
- **攻击面要主动收缩**：删掉不用的接口、关掉不用的端口、下线调试端点（`/actuator/env`、`/druid`）、生产环境关掉堆栈回显。
- **滥用用例（abuse case）比正常用例更能发现问题**：把每个正常流程反着写一遍 —— 改 ID、改价格、改数量为负、重复提交、换租户、超大分页。
- 易错点：把「前端校验过」当成安全（前端可绕过）；把「HTTPS」当成万能（只保护传输）；把「加了权限注解」当成授权完整（注解漏在某个新接口上就失守）；日志里打印了完整请求体（密码、token 全进日志）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class ThreatModel {
    enum Stride {
        SPOOFING("仿冒", "能证明「你是你」吗"),
        TAMPERING("篡改", "数据在传输或存储中被改过吗"),
        REPUDIATION("抵赖", "关键操作有不可否认的凭据吗"),
        INFO_DISCLOSURE("信息泄露", "不该看的人能看到吗"),
        DENIAL("拒绝服务", "资源会被打光吗"),
        ELEVATION("权限提升", "普通用户能变成管理员吗");

        final String cn;
        final String question;

        Stride(String cn, String question) {
            this.cn = cn;
            this.question = question;
        }
    }

    record Asset(String name, String kind, int impact) {
    }

    record Threat(Stride stride, String desc, String asset, int likelihood, int impact) {
        int risk() {
            return likelihood * impact;
        }

        String level() {
            int r = risk();
            if (r >= 16) {
                return "高";
            }
            if (r >= 9) {
                return "中";
            }
            return "低";
        }
    }

    static void strideTable() {
        System.out.println("【STRIDE：六个提问把威胁问全】");
        for (Stride s : Stride.values()) {
            System.out.printf("  %-16s %-10s %s%n", s, s.cn, s.question);
        }
        System.out.println("  对应的安全属性：机密性、完整性、可用性、认证、授权、不可否认");
        System.out.println();
    }

    static void assets() {
        System.out.println("【先列资产：不知道要保护什么，就谈不上安全】");
        List<Asset> assets = List.of(
                new Asset("用户密码", "凭据", 5),
                new Asset("订单与支付记录", "业务数据", 5),
                new Asset("身份证/手机号", "个人敏感信息", 4),
                new Asset("管理员后台", "功能入口", 5),
                new Asset("日志与监控", "运维数据", 3),
                new Asset("数据库连接串", "配置密钥", 5));
        for (Asset a : assets) {
            System.out.printf("  %-18s %-14s 影响=%d%n", a.name(), a.kind(), a.impact());
        }
        System.out.println("  影响 5 = 泄露或篡改会导致直接资金损失或合规处罚");
        System.out.println();
    }

    static void trustBoundaries() {
        System.out.println("【信任边界：数据每跨一次边界，就必须重新校验】");
        String[][] boundaries = {
                {"浏览器", "网关", "所有参数、Header、Cookie 一律不可信"},
                {"网关", "业务服务", "网关注入的身份头也要校验，内网不等于可信"},
                {"业务服务", "数据库", "SQL 必须参数化，账号只给最小权限"},
                {"业务服务", "第三方 API", "回包不可信，要校验结构与大小"},
                {"服务", "日志/消息队列", "写入前脱敏，避免二次泄露"},
        };
        for (String[] b : boundaries) {
            System.out.printf("  %-10s -> %-16s %s%n", b[0], b[1], b[2]);
        }
        System.out.println();
    }

    static void riskRanking() {
        System.out.println("【威胁清单按风险排序：先修高危】");
        List<Threat> threats = new ArrayList<>(List.of(
                new Threat(Stride.ELEVATION, "普通用户直接访问 /admin/order/list", "管理员后台", 4, 5),
                new Threat(Stride.INFO_DISCLOSURE, "订单详情只校验登录不校验归属", "订单与支付记录", 5, 4),
                new Threat(Stride.SPOOFING, "密码用 MD5 存储，撞库即可登录", "用户密码", 3, 5),
                new Threat(Stride.TAMPERING, "金额由前端传入，服务端不重算", "订单与支付记录", 3, 5),
                new Threat(Stride.DENIAL, "导出接口无频率限制", "日志与监控", 3, 3),
                new Threat(Stride.REPUDIATION, "退款操作不留操作人", "订单与支付记录", 2, 3)));
        threats.sort(Comparator.comparingInt(Threat::risk).reversed());
        for (Threat t : threats) {
            System.out.printf("  [%s] 风险=%2d  %-12s %-22s %s%n",
                    t.level(), t.risk(), t.stride().cn, t.asset(), t.desc());
        }
        System.out.println("  风险 = 可能性 × 影响；两个 3 分组合起来往往比一个 5 分更该先修");
        System.out.println();
    }

    static void abuseCases() {
        System.out.println("【滥用用例：把「正常流程」反着写一遍】");
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("登录", "换别人的手机号收验证码；爆破；用已泄露密码撞库；改返回体里的 userId");
        cases.put("下单", "改 price 字段；数量填负数；重复提交；绕过库存校验");
        cases.put("查订单", "把 orderId 逐个 +1 遍历；换 tenantId 看别人公司的数据");
        cases.put("上传", "传 .jsp/.php；改 Content-Type；超大文件；路径穿越 ../../");
        cases.put("导出", "把 pageSize 改成 100000 拉全库；并发开 100 个导出");
        cases.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println("  每写一个正常用例，就问一句「如果参数是我随便填的呢」");
        System.out.println();
    }

    static void defenseInDepth() {
        System.out.println("【纵深防御：任何一层都可能失效，所以要有下一层】");
        String[][] layers = {
                {"输入校验", "白名单、长度、类型、范围", "会被绕过：编码变形、非常规字段"},
                {"输出编码", "按上下文做 HTML/JS/URL 编码", "会被绕过：上下文判断错"},
                {"认证", "密码强度、MFA、会话管理", "会被绕过：钓鱼、撞库"},
                {"授权", "每次都校验「这个用户能不能碰这条数据」", "会被绕过：漏了某个新接口"},
                {"数据层", "参数化查询、最小权限账号、加密存储", "会被绕过：配置错误"},
                {"检测", "审计日志、告警、异常行为分析", "作用：前几层都失效时还能发现"},
        };
        for (String[] l : layers) {
            System.out.printf("  %-8s %-38s %s%n", l[0], l[1], l[2]);
        }
        System.out.println("  只做输入校验是不够的：漏一个字段就全线失守");
        System.out.println();
    }

    static void reviewQuestions() {
        System.out.println("【提交代码前自问的五个问题】");
        String[] qs = {
                "这个接口有没有鉴权？换个 userId 能不能读到别人的数据？",
                "我拼字符串进 SQL / 命令 / 模板了吗？",
                "这个字段会原样输出到页面、日志、CSV 吗？",
                "失败时返回的信息会不会泄露内部结构（表名、堆栈、版本）？",
                "这个操作重复执行两次会怎样？金额会多扣吗？",
        };
        int i = 1;
        for (String q : qs) {
            System.out.printf("  %d. %s%n", i++, q);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        strideTable();
        assets();
        trustBoundaries();
        riskRanking();
        abuseCases();
        defenseInDepth();
        reviewQuestions();
    }
}
```

**课后练习**
1. 给一个「优惠券领取」功能写威胁建模：列出资产、画出信任边界、按 STRIDE 各写至少一条威胁并打风险分。
2. 实现 `failClosed` 示例：写一个 `canViewOrder(User u, Order o)`，故意漏掉一个分支，用单元测试证明它会放行不该放行的请求，再改成默认拒绝。
3. 为一个已有项目列出「攻击面清单」：所有对外端点、管理端点、调试端点、上传入口，并标出哪些应该下线。
4. 思考题：为什么「内网调用不校验身份」在微服务架构里特别危险？一旦某台机器被攻破，会发生什么？

---

### 36-02 SQL 注入与参数化查询

**知识点**
- **SQL 注入的本质是「数据和代码混在一起」**：把用户输入拼进 SQL 字符串，数据库就无法区分哪部分是结构、哪部分是值。输入里带一个引号，就能提前闭合字符串，把后面的内容变成 SQL 语法。
- **参数化查询（PreparedStatement）解决的是语法层面的问题**：SQL 结构先编译定型，值后绑定，值里有什么字符都只是数据。**这是唯一可靠的解法**，不是「更安全的字符串拼接」。
- **手写转义不可靠**：把 `'` 换成 `''` 只对「字符串字面量」这一种上下文有效。数字上下文（`age = ` + 输入）、`LIKE` 的 `%`、`ORDER BY` 的列名、表名，全都转义不了。
- **占位符不能用在标识符位置**：`ORDER BY ?`、`FROM ?`、`LIMIT ?`（部分驱动可以）都不行。列名、表名、排序方向必须走**白名单**，这是注入的重灾区。
- **`LIKE` 要单独处理**：`%` 和 `_` 是通配符，用户输入里带了会扩大匹配范围。要么转义 `%`/`_`/`\` 并声明 `ESCAPE '\'`，要么接受它只是「模糊匹配」而不影响安全。
- **多租户系统必须把 `tenant_id` 一起绑进条件**：只按 `name` 查，注入一进来就能跨租户读数据。业务条件缺失和注入叠加，后果比单纯注入更严重。
- **`IN` 子句要按数量生成占位符**：`IN (?, ?, ?)`，不能用 `IN ('a,b,c')`（那是一个字符串，不是三个值）。
- **错误信息也是泄露面**：把 `SQLException` 原样返回给前端，等于把表名、列名、数据库版本送给攻击者。对外只回统一错误码，细节写进内部日志。
- **纵深防御**：数据库账号只给 `SELECT/INSERT/UPDATE` 需要的表，不给 `DROP`、不给跨库；应用层加 WAF 只能挡常见 payload，不能当唯一防线；审计慢查询与异常 SQL。
- 易错点：以为 ORM 就安全（`createNativeQuery`、字符串拼接的 `@Query` 一样能注入）；以为 `#{}` 和 `${}` 等价（MyBatis 里 `${}` 是直接拼接）；把「参数化」用在动态排序上（拼不出来就退化成拼接）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


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
```

**课后练习**
1. 给 `evalWhere` 加上括号支持（`(a OR b) AND c`），体会为什么「自己写 SQL 解析器」是个坏主意，以及真实数据库为什么必须用预编译。
2. 实现 `inClause(int n)` 生成 `IN (?, ?, ...)`，再写一个方法按列表长度动态绑定，比较它和 `IN ('a,b,c')` 的行为差异。
3. 写一个静态检查脚本：扫描 Java 源码里 `+ " WHERE`、`"SELECT` 与变量拼接的行，输出可疑位置清单。
4. 思考题：为什么 `ORDER BY ?` 在多数驱动里不生效？如果框架允许你把列名当参数传，风险在哪里？

---
