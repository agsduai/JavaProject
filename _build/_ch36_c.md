### 36-05 越权访问与 IDOR

**知识点**
- **越权分两种**：**水平越权**（同一角色之间互相看数据，A 用户看 B 用户的订单）和**垂直越权**（低权限干高权限的事，普通用户调管理员接口）。前者更常见，后者危害更大。
- **IDOR（Insecure Direct Object Reference）** 是水平越权的典型形态：接口直接拿前端传的 `id` 去查，不校验「这条数据是不是你的」。`GET /api/order/1001` 改成 `1002` 就能看别人的单。
- **根本原因不是「id 可枚举」，而是「授权缺失」**：就算 id 换成 UUID 不可猜，只要别人把链接分享出去、或者从日志/Referer 里拿到，依然越权。UUID 只是「提高门槛」，不是防护。
- **正确姿势是「数据级授权」**：查询条件里必须带上当前用户的归属（`WHERE id = ? AND user_id = ?`），或者在查到后立刻判断 `if (!order.ownerId().equals(currentUser)) deny()`。**先鉴权再返回，不能先返回再判断**。
- **授权要集中，不要分散**：每个接口手写一遍 `if` 必然漏。用拦截器 + 注解（如 `@RequirePermission("order:read")`）、或在 Service 层统一入口做校验。**新增接口时最容易忘**，所以要让它「默认拒绝」。
- **`userId` 绝不能让前端传**：从会话/token 里取当前用户，而不是从请求参数里取。`POST /api/order?userId=1002` 这种设计等于把授权交给攻击者。
- **对象归属要贯穿到底**：列表查询、详情、修改、删除、导出、下载附件 —— 每一个能接触到该对象的入口都要校验。常见漏洞是「详情校验了，导出没校验」。
- **批量接口是高危点**：`POST /api/order/batch-delete` 传一个 id 数组，如果只校验第一个，后面全放过。必须逐个校验或一次性用 `IN` + `user_id` 条件过滤。
- **管理员接口要独立鉴权**：不能靠「前端不显示入口」。管理端点应放在独立路径 + 独立鉴权中间件 + 可选 IP 白名单。
- **间接引用（indirect reference）能降低风险**：对外暴露的 id 用「映射表」或「带签名的短期 token」，服务端反查真实 id。适合下载、分享链接等场景。
- 易错点：只校验「是否登录」不校验「是否有权」；用 `@PreAuthorize` 但 SpEL 写错（`#id == authentication.principal.id` 拿不到值）；前端隐藏按钮就以为安全；测试只用自己的账号测，从不用两个账号互测。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class IdorDemo {
    record User(int id, String name, String role, int tenant) {
    }

    record Order(int id, int ownerId, int tenant, String item, int amount) {
    }

    static final List<User> USERS = List.of(
            new User(1, "张三", "user", 7),
            new User(2, "李四", "user", 7),
            new User(3, "王五", "admin", 7),
            new User(4, "赵六", "user", 9));

    static final List<Order> ORDERS = List.of(
            new Order(1001, 1, 7, "机械键盘", 399),
            new Order(1002, 2, 7, "显示器", 1299),
            new Order(1003, 1, 7, "鼠标垫", 29),
            new Order(1004, 4, 9, "耳机", 599));

    record Result(int status, String body) {
        @Override
        public String toString() {
            return status + " " + body;
        }
    }

    /** 四种实现，逐层加防护 */
    static class OrderApi {
        /** 错误示范一：完全不校验 */
        Result naive(int orderId) {
            for (Order o : ORDERS) {
                if (o.id() == orderId) {
                    return new Result(200, o.item() + " / " + o.amount() + " 元 / owner=" + o.ownerId());
                }
            }
            return new Result(404, "not found");
        }

        /** 错误示范二：只校验登录 */
        Result loginOnly(int orderId, User current) {
            if (current == null) {
                return new Result(401, "未登录");
            }
            return naive(orderId);
        }

        /** 正确：数据级授权，查询条件带归属 */
        Result scoped(int orderId, User current) {
            if (current == null) {
                return new Result(401, "未登录");
            }
            for (Order o : ORDERS) {
                if (o.id() == orderId && o.ownerId() == current.id() && o.tenant() == current.tenant()) {
                    return new Result(200, o.item() + " / " + o.amount() + " 元");
                }
            }
            return new Result(404, "not found");
        }

        /** 管理员可跨用户，但仍限制在本租户 */
        Result scopedAdmin(int orderId, User current) {
            if (current == null) {
                return new Result(401, "未登录");
            }
            for (Order o : ORDERS) {
                if (o.id() != orderId) {
                    continue;
                }
                boolean admin = current.role().equals("admin") && o.tenant() == current.tenant();
                boolean owner = o.ownerId() == current.id();
                if (admin || owner) {
                    return new Result(200, o.item() + " / " + o.amount() + " 元");
                }
            }
            return new Result(404, "not found");
        }

        /** 错误示范：userId 由前端传 */
        Result trustingParam(int orderId, int claimedUserId) {
            for (Order o : ORDERS) {
                if (o.id() == orderId && o.ownerId() == claimedUserId) {
                    return new Result(200, o.item() + " / " + o.amount() + " 元");
                }
            }
            return new Result(404, "not found");
        }
    }

    static void idorBasics() {
        System.out.println("【IDOR：把 id 改一下就看到别人的数据】");
        OrderApi api = new OrderApi();
        User zhangsan = USERS.get(0);
        User lisi = USERS.get(1);

        System.out.println("  张三（id=1）查自己的 1001：" + api.naive(1001));
        System.out.println("  张三（id=1）查李四的 1002：" + api.naive(1002) + "  <- 越权成功");
        System.out.println("  李四（id=2）查自己的 1002：" + api.naive(1002));
        System.out.println();
        System.out.println("  只校验登录也挡不住：");
        System.out.println("    张三已登录，查 1002 -> " + api.loginOnly(1002, zhangsan));
        System.out.println("  「登录了」不等于「这条数据归你」");
        System.out.println();
    }

    static void scopedQuery() {
        System.out.println("【正确写法：把归属写进查询条件】");
        OrderApi api = new OrderApi();
        User zhangsan = USERS.get(0);
        User lisi = USERS.get(1);
        System.out.println("  SQL: SELECT * FROM orders WHERE id = ? AND owner_id = ? AND tenant_id = ?");
        System.out.println("  张三查 1001 -> " + api.scoped(1001, zhangsan));
        System.out.println("  张三查 1002 -> " + api.scoped(1002, zhangsan) + "  <- 查不到，和不存在一样");
        System.out.println("  李四查 1002 -> " + api.scoped(1002, lisi));
        System.out.println("  关键点：越权时返回 404 而不是 403，避免泄露「这条数据存在」");
        System.out.println();
    }

    static void verticalPrivilege() {
        System.out.println("【垂直越权：普通用户调管理员接口】");
        OrderApi api = new OrderApi();
        User zhangsan = USERS.get(0);
        User wangwu = USERS.get(2);
        User zhaoliu = USERS.get(3);
        System.out.println("  张三(user) 查 1002 -> " + api.scopedAdmin(1002, zhangsan));
        System.out.println("  王五(admin, 租户7) 查 1002 -> " + api.scopedAdmin(1002, wangwu) + "  <- 同租户可管");
        System.out.println("  王五(admin, 租户7) 查 1004 -> " + api.scopedAdmin(1004, wangwu) + "  <- 跨租户也不行");
        System.out.println("  赵六(user, 租户9) 查 1004 -> " + api.scopedAdmin(1004, zhaoliu));
        System.out.println("  管理员身份也要受租户边界约束，否则一个租户的管理员能看全平台");
        System.out.println();
    }

    static void trustingClientId() {
        System.out.println("【userId 从参数里取 = 把授权交给攻击者】");
        OrderApi api = new OrderApi();
        System.out.println("  请求 POST /api/order/detail  body: {orderId:1002, userId:2}");
        System.out.println("  张三把 userId 改成 2 -> " + api.trustingParam(1002, 2));
        System.out.println("  正确：userId 从会话/token 里取，请求体里的 userId 一律忽略");
        System.out.println();
    }

    static void uuidIsNotAuth() {
        System.out.println("【换 UUID 只是提高门槛，不是防护】");
        String[] ids = {"1001", "550e8400-e29b-41d4-a716-446655440000"};
        System.out.println("  自增 id：" + ids[0] + " -> 改一个数字就能遍历");
        System.out.println("  UUID：" + ids[1]);
        System.out.println("    猜不出来，但会出现在：分享链接、Referer、日志、截图、客服工单");
        System.out.println("    一旦泄露，照样越权 —— 所以授权校验不能省");
        System.out.println();
    }

    static void batchAndExport() {
        System.out.println("【批量接口与导出：最容易漏的地方】");
        OrderApi api = new OrderApi();
        User zhangsan = USERS.get(0);
        int[] ids = {1001, 1002, 1003};
        System.out.println("  POST /api/order/batch  ids=" + java.util.Arrays.toString(ids));
        System.out.println("    错误实现（只校验第一个 id，其余直接放行）：");
        for (int i = 0; i < ids.length; i++) {
            boolean first = i == 0;
            Result r = first ? api.scoped(ids[i], zhangsan) : api.naive(ids[i]);
            System.out.printf("      id=%d -> %s%s%n", ids[i], r, first ? "" : "  <- 未校验");
        }
        System.out.println("    正确实现（每个 id 都过一遍 scoped）：");
        for (int id : ids) {
            System.out.printf("      id=%d -> %s%n", id, api.scoped(id, zhangsan));
        }
        System.out.println("    或一条 SQL 搞定：WHERE id IN (?,?,?) AND owner_id = ?");
        System.out.println();
        System.out.println("  同类高危入口：导出 CSV、下载附件、打印、消息推送、分享链接");
        System.out.println("  规律：凡是「按 id 取数据」的地方，都要问一句「这条归谁」");
        System.out.println();
    }

    static void centralize() {
        System.out.println("【授权要集中：默认拒绝，新增接口自动受保护】");
        Map<String, String> endpointPolicy = new LinkedHashMap<>();
        endpointPolicy.put("GET /api/order/{id}", "owner-or-admin");
        endpointPolicy.put("GET /api/admin/users", "admin-only");
        endpointPolicy.put("GET /api/report/export", "owner-scoped");
        endpointPolicy.put("GET /api/health", "public");
        endpointPolicy.put("GET /api/order/{id}/invoice", "MISSING");
        endpointPolicy.forEach((k, v) -> {
            String mark = v.equals("MISSING") ? "  <- 未登记，必须默认拒绝" : "";
            System.out.printf("  %-30s %s%s%n", k, v, mark);
        });
        System.out.println("  做法：拦截器读「策略表」，没登记的一律 403");
        System.out.println("  这样新接口上线时忘了配策略，是「不能用」而不是「裸奔」");
        System.out.println();
    }

    static void testingMethod() {
        System.out.println("【怎么测越权：永远用两个账号】");
        String[] steps = {
                "准备两个同角色账号 A、B，各自造一条数据",
                "用 A 的 token 请求 B 的数据 id，期望 404/403",
                "用 A 的 token 请求管理员接口，期望 403",
                "把请求体里的 userId/tenantId 改成别人的，期望被忽略",
                "批量接口里混入别人的 id，期望整批拒绝或只返回自己的",
                "导出/下载/分享链接逐个试一遍",
        };
        int i = 1;
        for (String s : steps) {
            System.out.printf("  %d. %s%n", i++, s);
        }
        System.out.println("  只用自己账号测，是越权漏洞最常见的漏测原因");
        System.out.println();
    }

    static void indirectReference() {
        System.out.println("【间接引用：对外不暴露真实 id】");
        Map<String, Integer> shareTokens = new LinkedHashMap<>();
        shareTokens.put("tok-a1b2c3", 1001);
        shareTokens.put("tok-d4e5f6", 1003);
        System.out.println("  分享链接：https://shop.example.com/share/tok-a1b2c3");
        shareTokens.forEach((tok, orderId) ->
                System.out.printf("  %s -> 内部订单 %d%n", tok, orderId));
        System.out.println("  好处：token 可撤销、可设过期、可绑定查看者");
        System.out.println("  但服务端拿到 token 反查后，仍要校验当前用户是否有权看这条");
        System.out.println();
    }

    public static void main(String[] args) {
        idorBasics();
        scopedQuery();
        verticalPrivilege();
        trustingClientId();
        uuidIsNotAuth();
        batchAndExport();
        centralize();
        testingMethod();
        indirectReference();
    }
}
```

**课后练习**
1. 给 `OrderApi` 加一个 `listOrders(User current, int page, int size)`：只返回当前用户可见的订单，并说明为什么「查出来再过滤」不如「查询条件里带归属」。
2. 实现一个「策略表 + 拦截器」骨架：未登记的端点默认 403，登记为 `owner-scoped` 的端点自动注入归属条件。
3. 写一组越权测试用例（伪代码即可）：覆盖详情、修改、删除、批量、导出、管理员接口六个入口。
4. 思考题：为什么越权时推荐返回 404 而不是 403？在什么场景下 403 反而更合适？

---

### 36-06 密码存储与加盐哈希

**知识点**
- **密码绝不能明文存，也不能可逆加密存**。加密意味着「有密钥就能还原」，一旦密钥泄露（配置、备份、内存转储）等于全量泄露。密码只需要**验证**，不需要还原，所以要用**单向哈希**。
- **MD5/SHA-1/SHA-256 都不行**：它们是为「快」设计的，GPU 每秒能算几十亿次。8 位密码的哈希空间，用现成字典 + 规则，几小时就能跑完。加盐只能防「彩虹表」，防不住「针对单个用户的暴力破解」。
- **必须用「慢哈希」**：bcrypt、scrypt、Argon2、PBKDF2。它们通过可调的计算成本（迭代次数、内存占用）让每次尝试变慢，把暴力破解的成本拉高几个数量级。
- **推荐 Argon2id**（内存硬，抗 GPU/ASIC），其次 bcrypt（成熟、库多）。bcrypt 的 cost 建议 ≥ 12（约 250 ms），Argon2id 建议内存 19 MiB、迭代 2 次、并行度 1（OWASP 2024 基线）。
- **盐必须每个用户独立随机**：长度 ≥ 16 字节，用 `SecureRandom` 生成，和哈希一起存。共用盐等于没加盐 —— 两个相同密码还是同样的哈希，一次破解就批量命中。
- **参数要存进哈希串**：bcrypt 的 `$2a$12$...` 自带 cost 和 salt，Argon2 的 `$argon2id$v=19$m=19456,t=2,p=1$...` 自带全部参数。这样以后调高强度，老密码还能验证，登录成功后再悄悄升级。
- **不要自己拼哈希**：`sha256(salt + password)` 这种自己设计的方案几乎都错。用库，`BCryptPasswordEncoder`、`Argon2PasswordEncoder`、`Pbkdf2PasswordEncoder`。
- **比较要用常量时间**：普通 `String.equals` 会在第一个不同字节就返回，理论上可被计时攻击。哈希比较用库提供的 `matches()`（内部已做常量时间比较）。
- **密码策略要现实**：长度 ≥ 12 比「必须含大小写+数字+符号」更有效；检查是否在已泄露密码库（Have I Been Pwned 的 k-anonymity API）；不要强制定期改密码（会导致 `Password1!` → `Password2!`）；不要设置荒谬的最大长度（bcrypt 只取前 72 字节，超长要预哈希）。
- **其他凭据也一样**：API Key、重置 token、邀请码，都要「随机生成 + 哈希存储 + 有效期 + 一次性」。
- 易错点：把盐也当秘密（盐不需要保密，保密的是哈希算法强度）；用 `Random` 而不是 `SecureRandom` 生成盐；把密码写进日志；把密码放进 URL 参数；忘记限制登录失败次数（慢哈希挡不住在线爆破，要靠限流和锁定）。

**示例代码**
```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;


public class PasswordStorage {
    /** 演示用的慢哈希：真实项目请用 bcrypt / Argon2 库，这里用 PBKDF2 手写以保持零依赖 */
    static final int ITERATIONS = 210_000;
    static final int SALT_BYTES = 16;
    static final int KEY_BITS = 256;

    static final SecureRandom RANDOM = new SecureRandom();

    static byte[] randomSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return salt;
    }

    /** PBKDF2-HMAC-SHA256，纯 JDK 实现 */
    static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        try {
            javax.crypto.spec.PBEKeySpec spec =
                    new javax.crypto.spec.PBEKeySpec(password, salt, iterations, keyBits);
            return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 存储格式：算法$迭代次数$盐$哈希，参数随密码一起存 */
    record StoredPassword(String algorithm, int iterations, byte[] salt, byte[] hash) {
        String encode() {
            return algorithm + "$" + iterations + "$"
                    + HexFormat.of().formatHex(salt) + "$"
                    + HexFormat.of().formatHex(hash);
        }

        static StoredPassword decode(String s) {
            String[] p = s.split("\\$");
            return new StoredPassword(p[0], Integer.parseInt(p[1]),
                    HexFormat.of().parseHex(p[2]), HexFormat.of().parseHex(p[3]));
        }
    }

    static StoredPassword hash(char[] password) {
        byte[] salt = randomSalt();
        return new StoredPassword("pbkdf2-sha256", ITERATIONS, salt,
                pbkdf2(password, salt, ITERATIONS, KEY_BITS));
    }

    /** 常量时间比较，避免计时侧信道 */
    static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    static boolean verify(char[] password, StoredPassword stored) {
        byte[] candidate = pbkdf2(password, stored.salt(), stored.iterations(), stored.hash().length * 8);
        return constantTimeEquals(candidate, stored.hash());
    }

    static String md5(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void whyFastHashFails() {
        System.out.println("【为什么 MD5/SHA-256 不能用】");
        System.out.println("  同一个密码 \"123456\"：");
        System.out.println("    MD5    " + md5("123456"));
        System.out.println("    SHA256 " + sha256("123456"));
        System.out.println("  这两个值在网上的「彩虹表」里直接能查到");
        System.out.println();
        System.out.println("  加盐后彩虹表失效，但暴力破解依然快：");
        String salt = "s1";
        System.out.println("    sha256(\"s1\" + \"123456\") = " + sha256("s1" + "123456"));
        System.out.println("    GPU 每秒可算几十亿次，8 位密码几小时穷举完");
        System.out.println("  关键差异不是「有没有盐」，而是「一次计算要多久」");
        System.out.println();
        System.out.printf("  PBKDF2 迭代 %d 次：单次约 100~300 ms%n", ITERATIONS);
        System.out.println("    同样穷举，成本被放大几十万倍，攻击在经济上不可行");
        System.out.println();
    }

    static void saltPerUser() {
        System.out.println("【盐必须每人一份，不能共用】");
        String password = "same-password";
        StoredPassword a = hash(password.toCharArray());
        StoredPassword b = hash(password.toCharArray());
        System.out.println("  用户A：" + a.encode().substring(0, 60) + "...");
        System.out.println("  用户B：" + b.encode().substring(0, 60) + "...");
        System.out.println("  同样的密码，哈希完全不同 -> 破解一个不等于破解全部");
        System.out.println();

        System.out.println("  反例：共用固定盐");
        System.out.println("    用户A sha256(\"fixed\" + pwd) = " + sha256("fixed" + password));
        System.out.println("    用户B sha256(\"fixed\" + pwd) = " + sha256("fixed" + password));
        System.out.println("    两个值一样，一次破解批量命中，盐等于白加");
        System.out.println();
    }

    static void storeAndVerify() {
        System.out.println("【完整流程：注册存哈希，登录验哈希】");
        Map<String, String> db = new HashMap<>();
        String[] users = {"zhangsan", "lisi"};
        String[] pwds = {"Tr0ub4dor&3-horse", "correct horse battery staple"};
        for (int i = 0; i < users.length; i++) {
            StoredPassword sp = hash(pwds[i].toCharArray());
            db.put(users[i], sp.encode());
        }
        System.out.println("  数据库里存的样子：");
        db.forEach((u, v) -> System.out.printf("    %-10s %s%n", u, v.substring(0, 70) + "..."));
        System.out.println();

        String[][] attempts = {
                {"zhangsan", "Tr0ub4dor&3-horse"},
                {"zhangsan", "wrong-password"},
                {"lisi", "correct horse battery staple"},
        };
        for (String[] a : attempts) {
            StoredPassword sp = StoredPassword.decode(db.get(a[0]));
            boolean ok = verify(a[1].toCharArray(), sp);
            System.out.printf("  登录 %-10s 密码 %-28s -> %s%n", a[0], "\"" + a[1] + "\"", ok ? "成功" : "失败");
        }
        System.out.println();
        System.out.println("  注意：库里没有明文，也没有可逆的密文；泄露了也只是拿到一堆慢哈希");
        System.out.println();
    }

    static void upgradeOnLogin() {
        System.out.println("【参数升级：老密码也能平滑迁移】");
        StoredPassword real = hash("my-password".toCharArray());
        StoredPassword legacy = new StoredPassword("pbkdf2-sha256", 100_000, real.salt(),
                pbkdf2("my-password".toCharArray(), real.salt(), 100_000, 256));

        System.out.println("  老记录迭代次数：" + legacy.iterations() + "（低于当前标准 " + ITERATIONS + "）");
        boolean ok = verify("my-password".toCharArray(), legacy);
        System.out.println("  验证通过：" + ok);
        if (ok && legacy.iterations() < ITERATIONS) {
            StoredPassword upgraded = hash("my-password".toCharArray());
            System.out.println("  登录成功后立刻用新参数重算并覆盖：" + upgraded.encode().substring(0, 40) + "...");
        }
        System.out.println("  这样不用强制所有人改密码，也能逐步把强度提上来");
        System.out.println();
    }

    /** 按终端显示宽度补空格：CJK 字符占 2 列 */
    static String pad(String s, int width) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) > 0x2E80 ? 2 : 1;
        }
        return s + " ".repeat(Math.max(width - w, 0));
    }

    static void algorithmComparison() {
        System.out.println("【慢哈希方案对比】");
        String[][] rows = {
                {"Argon2id", "内存硬，抗 GPU/ASIC", "m=19MiB, t=2, p=1", "首选，OWASP 推荐"},
                {"bcrypt", "成熟稳定，库最多", "cost >= 12", "注意只取前 72 字节"},
                {"scrypt", "内存硬，参数较难调", "N=2^17, r=8, p=1", "可用，生态略少"},
                {"PBKDF2", "标准、FIPS 合规", ">= 600000 次(SHA256)", "合规场景，抗 GPU 较弱"},
                {"SHA-256 + 盐", "快", "-", "不可用于密码"},
                {"MD5 / SHA-1", "更快", "-", "绝对不可用"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 14) + " | " + pad(r[1], 26)
                    + " | " + pad(r[2], 22) + " | " + r[3]);
        }
        System.out.println();
    }

    static void otherSecrets() {
        System.out.println("【其他凭据也走同一套逻辑】");
        System.out.println("  重置密码 token：SecureRandom 生成 32 字节 -> 哈希后入库 -> 30 分钟过期 -> 用一次即失效");
        System.out.println("  API Key：只显示一次，库里存哈希 + 前缀（便于识别是哪个 key）");
        System.out.println("  邀请码：随机 + 有效期 + 使用次数上限");
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        System.out.println("  示例 token：" + HexFormat.of().formatHex(token));
        System.out.println("  常见错误：token 用时间戳/自增 id，可预测 -> 直接接管别人账号");
        System.out.println();
    }

    static void operationalHygiene() {
        System.out.println("【慢哈希之外还必须做的】");
        String[][] items = {
                {"登录限流", "同 IP / 同账号失败 5 次后加验证码或锁定，慢哈希挡不住在线爆破"},
                {"不打印密码", "日志、异常、APM 里出现明文密码是重大事故"},
                {"不放进 URL", "会进访问日志、Referer、浏览器历史"},
                {"长度下限 12", "比复杂度规则更有效；上限别低于 64"},
                {"查泄露库", "注册/登录时比对已泄露密码（k-anonymity，不上传明文）"},
                {"别强制轮换", "强制 90 天改密码会导致弱密码规律化"},
                {"支持 MFA", "密码泄露后还有第二道门"},
        };
        for (String[] i : items) {
            System.out.printf("  %-14s %s%n", i[0], i[1]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        whyFastHashFails();
        saltPerUser();
        storeAndVerify();
        upgradeOnLogin();
        algorithmComparison();
        otherSecrets();
        operationalHygiene();
    }
}
```

**课后练习**
1. 把 `hash` 换成 bcrypt 风格的「可配置 cost」：写一个 `hash(char[] pwd, int cost)`，测出 cost=10/12/14 各自的耗时，画出「安全强度 vs 登录延迟」的取舍曲线。
2. 实现「密码强度评估」：长度、是否在常见弱密码表、是否含用户名字符串，输出评分与改进建议（不要求强制复杂度）。
3. 实现重置密码 token 的完整生命周期：生成 → 哈希入库 → 校验 → 一次性失效 → 过期清理，并说明每一环的失败会带来什么后果。
4. 思考题：为什么 bcrypt 只取前 72 字节？超长密码该怎么处理才既安全又不损失熵？

---
