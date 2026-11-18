/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：36-06
 * 课名：密码存储与加盐哈希
 * ------------------------------------------------------------
 * 知识点：
 *   - **密码绝不能明文存，也不能可逆加密存**。加密意味着「有密钥就能还原」，一旦密钥泄露（配置、备份、内存转储）等于全量泄露。密码只需要**验证**，不需要还原，所以要用**单向哈希**。
 *   - **MD5/SHA-1/SHA-256 都不行**：它们是为「快」设计的，GPU 每秒能算几十亿次。8 位密码的哈希空间，用现成字典 + 规则，几小时就能跑完。加盐只能防「彩虹表」，防不住「针对单个用户的暴力破解」。
 *   - **必须用「慢哈希」**：bcrypt、scrypt、Argon2、PBKDF2。它们通过可调的计算成本（迭代次数、内存占用）让每次尝试变慢，把暴力破解的成本拉高几个数量级。
 *   - **推荐 Argon2id**（内存硬，抗 GPU/ASIC），其次 bcrypt（成熟、库多）。bcrypt 的 cost 建议 ≥ 12（约 250 ms），Argon2id 建议内存 19 MiB、迭代 2 次、并行度 1（OWASP 2024 基线）。
 *   - **盐必须每个用户独立随机**：长度 ≥ 16 字节，用 `SecureRandom` 生成，和哈希一起存。共用盐等于没加盐 —— 两个相同密码还是同样的哈希，一次破解就批量命中。
 *   - **参数要存进哈希串**：bcrypt 的 `$2a$12$...` 自带 cost 和 salt，Argon2 的 `$argon2id$v=19$m=19456,t=2,p=1$...` 自带全部参数。这样以后调高强度，老密码还能验证，登录成功后再悄悄升级。
 *   - **不要自己拼哈希**：`sha256(salt + password)` 这种自己设计的方案几乎都错。用库，`BCryptPasswordEncoder`、`Argon2PasswordEncoder`、`Pbkdf2PasswordEncoder`。
 *   - **比较要用常量时间**：普通 `String.equals` 会在第一个不同字节就返回，理论上可被计时攻击。哈希比较用库提供的 `matches()`（内部已做常量时间比较）。
 *   - **密码策略要现实**：长度 ≥ 12 比「必须含大小写+数字+符号」更有效；检查是否在已泄露密码库（Have I Been Pwned 的 k-anonymity API）；不要强制定期改密码（会导致 `Password1!` → `Password2!`）；不要设置荒谬的最大长度（bcrypt 只取前 72 字节，超长要预哈希）。
 *   - **其他凭据也一样**：API Key、重置 token、邀请码，都要「随机生成 + 哈希存储 + 有效期 + 一次性」。
 *   - 易错点：把盐也当秘密（盐不需要保密，保密的是哈希算法强度）；用 `Random` 而不是 `SecureRandom` 生成盐；把密码写进日志；把密码放进 URL 参数；忘记限制登录失败次数（慢哈希挡不住在线爆破，要靠限流和锁定）。
 * ============================================================
 */

package com.heima.ch36;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `hash` 换成 bcrypt 风格的「可配置 cost」：写一个 `hash(char[] pwd, int cost)`，测出 cost=10/12/14 各自的耗时，画出「安全强度 vs 登录延迟」的取舍曲线。
 * 2. 实现「密码强度评估」：长度、是否在常见弱密码表、是否含用户名字符串，输出评分与改进建议（不要求强制复杂度）。
 * 3. 实现重置密码 token 的完整生命周期：生成 → 哈希入库 → 校验 → 一次性失效 → 过期清理，并说明每一环的失败会带来什么后果。
 * 4. 思考题：为什么 bcrypt 只取前 72 字节？超长密码该怎么处理才既安全又不损失熵？
 * ============================================================
 */
