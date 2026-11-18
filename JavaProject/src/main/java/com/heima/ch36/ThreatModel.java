/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：36-01
 * 课名：安全编码原则与威胁建模
 * ------------------------------------------------------------
 * 知识点：
 *   - **安全不是一个功能，而是一条约束**：功能是「登录能成功」，安全是「只有本人能登录成功」。写代码时每加一个入口，都要问一句「谁能调用它、他能拿到什么」。
 *   - **STRIDE 是最好用的威胁清单**：仿冒（Spoofing）、篡改（Tampering）、抵赖（Repudiation）、信息泄露（Information Disclosure）、拒绝服务（Denial of Service）、权限提升（Elevation of Privilege）。对着这六项逐条问，比凭空想「有什么漏洞」有效得多。
 *   - **先列资产再谈威胁**：不知道要保护什么，就没法判断什么算「泄露」。资产按影响打分（资金、合规、声誉），威胁按「可能性 × 影响」算风险，优先修高风险的。
 *   - **信任边界是安全设计的骨架**：数据每跨过一次边界（浏览器→网关、网关→服务、服务→数据库、服务→第三方），就必须重新校验一次。**内网不等于可信**，这是大量事故的共同前提。
 *   - **纵深防御（defense in depth）**：输入校验、输出编码、认证、授权、数据层约束、检测告警，每一层都可能被绕过，所以不能只靠一层。只做输入校验的系统，漏一个字段就全线失守。
 *   - **默认拒绝（fail closed）**：权限判断里「拿不准」时应当拒绝，而不是放行。`if (user.isAdmin()) allow();` 后面忘了写 `else deny()`，就是默认放行。
 *   - **最小权限**：数据库账号只给需要的表和操作；服务账号只给需要的接口；容器不以 root 运行；密钥只在需要的进程可见。
 *   - **不要自己发明加密**：密码哈希用 bcrypt/Argon2/PBKDF2，对称加密用 AES-GCM，随机数用 `SecureRandom`。自己拼的 XOR、自己设计的「加盐规则」基本都是错的。
 *   - **攻击面要主动收缩**：删掉不用的接口、关掉不用的端口、下线调试端点（`/actuator/env`、`/druid`）、生产环境关掉堆栈回显。
 *   - **滥用用例（abuse case）比正常用例更能发现问题**：把每个正常流程反着写一遍 —— 改 ID、改价格、改数量为负、重复提交、换租户、超大分页。
 *   - 易错点：把「前端校验过」当成安全（前端可绕过）；把「HTTPS」当成万能（只保护传输）；把「加了权限注解」当成授权完整（注解漏在某个新接口上就失守）；日志里打印了完整请求体（密码、token 全进日志）。
 * ============================================================
 */

package com.heima.ch36;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给一个「优惠券领取」功能写威胁建模：列出资产、画出信任边界、按 STRIDE 各写至少一条威胁并打风险分。
 * 2. 实现 `failClosed` 示例：写一个 `canViewOrder(User u, Order o)`，故意漏掉一个分支，用单元测试证明它会放行不该放行的请求，再改成默认拒绝。
 * 3. 为一个已有项目列出「攻击面清单」：所有对外端点、管理端点、调试端点、上传入口，并标出哪些应该下线。
 * 4. 思考题：为什么「内网调用不校验身份」在微服务架构里特别危险？一旦某台机器被攻破，会发生什么？
 * ============================================================
 */
