### 74-01 认证与授权全景

**知识点**
- **认证（Authentication）回答"你是谁"，授权（Authorization）回答"你能做什么"**——两者必须分开设计：认证由登录/令牌完成，授权由权限模型完成；**混在一起会导致"登录即全权"的经典漏洞**（36-05 越权）。
- **认证的四种方式**：**会话 Cookie**（服务端状态，适合 Web）、**Token（JWT）**（无状态，适合 API/跨域）、**OAuth2 授权**（第三方代授权，64-02）、**SSO 单点登录**（一次登录访问多系统，74-02）——**选型看场景**（B 端多系统用 SSO，开放平台用 OAuth2）。
- **授权的三层模型**：**功能权限**（能不能调这个接口/看这个菜单）、**数据权限**（能看哪些行 74-05）、**字段权限**（能看哪些字段 68-05）——**企业系统三层都要**。
- **与 68 章的关系**：68 章讲"数据隐私的访问控制"（数据视角），本章讲"企业权限体系的组织方式"（系统视角）——**同一套控制，不同抽象层**。
- **权限系统的演进路径**：硬编码角色 -> 角色表 + 权限表（RBAC）-> 支持数据范围（ABAC 雏形）-> 统一权限中台（跨系统统一）——**不要一步到位，按痛点走**（49-10 演进纪律）。
- 易错点：认证成功就认为有权限（缺少授权检查）；只做菜单权限不做接口权限（直接调 API）；权限判断散落各处（无统一入口）；管理员权限无限大（无审计 74-10）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuthOverview {
    record AuthMethod(String name, String state, String fit, String risk) {
    }

    static void methods() {
        System.out.println("【四种认证方式】");
        List<AuthMethod> methods = List.of(
                new AuthMethod("Session Cookie", "服务端存会话", "传统 Web 后台", "集群需共享会话"),
                new AuthMethod("JWT Token", "无状态", "API / 移动端 / 跨域", "无法主动失效（需黑名单）"),
                new AuthMethod("OAuth2", "授权码/客户端凭证", "第三方接入", "scope 配置不当（64-02）"),
                new AuthMethod("SSO", "集中认证", "企业多系统", "中心化单点风险（74-02）"));
        for (AuthMethod m : methods) {
            System.out.printf("  %-16s 状态=%-12s 适合=%-20s 风险=%s%n",
                    m.name(), m.state(), m.fit(), m.risk());
        }
        System.out.println();
    }

    static void authnVsAuthz() {
        System.out.println("【认证 vs 授权】");
        String[][] rows = {
                {"回答", "你是谁", "你能做什么"},
                {"载体", "账号/令牌/证书", "角色/权限/数据范围"},
                {"失败", "401 未登录", "403 无权限"},
                {"常见漏洞", "弱密码/令牌泄露", "越权/IDOR（36-05）"},
                {"本课程", "74-02 SSO、64-02 OAuth2", "74-04 RBAC、74-05 ABAC、68-05 数据权限"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s 认证=%-28s 授权=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void threeLayers() {
        System.out.println("【授权的三层】");
        Map<String, String> layers = new LinkedHashMap<>();
        layers.put("功能权限", "接口/菜单/按钮：能不能调这个 API");
        layers.put("数据权限", "行级：能看哪些数据（本部门/本人）");
        layers.put("字段权限", "列级：能看哪些字段（脱敏 68-03）");
        layers.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.println();
        System.out.println("  反模式：只做菜单权限 -> 用户直接调 API 绕过（前端隐藏不可信）");
    }

    public static void main(String[] args) {
        methods();
        authnVsAuthz();
        threeLayers();
    }
}
```

**课后练习**
1. 为你的系统列出"认证方式 + 授权层数"，标注现有缺口（如只有菜单无接口权限）。
2. 对比 Session 与 JWT 在"集群部署/主动失效/跨域"三个场景下的取舍。
3. 思考题：为什么"认证成功不代表有权限"？举一个"登录普通用户直接调管理接口"的漏洞路径。

---

### 74-02 SSO 与 OIDC 流程

**知识点**
- **SSO 的价值是"一次登录，多系统通行"**：企业有 20 个内部系统，用户不该登录 20 次——**认证集中在 IdP（身份提供方）**，业务系统（SP）信任 IdP 的断言。
- **OIDC 是 OAuth2 之上的身份层**：OAuth2 只给"访问令牌"（能干什么），**OIDC 加了 `id_token`（JWT，声明"你是谁"）**——**OIDC = OAuth2 + 身份声明**，是现代 SSO 的标准协议（64-02 的延伸）。
- **授权码 + PKCE 的 SSO 流程**：用户访问系统 A -> 未登录跳 IdP -> IdP 认证后带 code 回 A -> A 用 code 换 id_token/access_token -> A 建立本地会话——**code 一次性 + 短有效期**（64-02 同款防护）。
- **单点登出（SLO）更难**：登出 IdP 后，各系统本地会话要失效——**做法**：IdP 通知各 SP（back-channel logout）或 **短期 token + 定期校验**——**跨域会话同步是 SSO 的工程难点**。
- **与 74-08 的关系**：SSO 解决"认证"，但**每个系统仍要自己做授权**（IdP 不知道业务权限）——**别把 SSO 当权限系统**。
- 易错点：id_token 不验签（伪造身份）；不校验 `iss`/`aud`/`exp`（令牌给错系统用）；code 可重复使用；单点登出不实现（登出 IdP 后各系统还能用）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SsoOidc {
    record IdToken(String iss, String aud, String sub, long exp, String nonce,
                   String signature) {
        String describe() {
            return "iss=" + iss + " aud=" + aud + " sub=" + sub + " exp=" + exp;
        }
    }

    static final Map<String, Boolean> usedCodes = new LinkedHashMap<>();

    /** IdP：认证后签发 code */
    static String authorize(String clientId, String redirectUri, String user, long now) {
        String code = UUID.randomUUID().toString().substring(0, 8);
        usedCodes.put(code, false);
        System.out.printf("  IdP 认证 %s -> 签发 code=%s（一次性，60s）%n", user, code);
        return code;
    }

    /** SP：用 code 换 token（含 PKCE 校验，简化） */
    static String exchange(String code, String clientId, String expectedAud) {
        Boolean used = usedCodes.get(code);
        if (used == null) {
            return "失败：code 不存在";
        }
        if (used) {
            return "失败：code 已被使用（重放）";
        }
        usedCodes.put(code, true);
        IdToken token = new IdToken("https://idp.example.com", expectedAud, "user-1001",
                9_999_999, "n-abc", "sig-valid");
        System.out.printf("  SP 换 token 成功：%s%n", token.describe());
        return "成功：建立本地会话（sub=user-1001）";
    }

    /** SP 验签 + 声明校验（生产必须做） */
    static String validate(IdToken t, String expectedIss, String expectedAud, long now) {
        if (!"sig-valid".equals(t.signature())) {
            return "拒绝：签名无效（伪造身份）";
        }
        if (!expectedIss.equals(t.iss())) {
            return "拒绝：iss 不符（令牌来自别的 IdP）";
        }
        if (!expectedAud.equals(t.aud())) {
            return "拒绝：aud 不符（令牌给别的系统用的）";
        }
        if (now > t.exp()) {
            return "拒绝：已过期";
        }
        return "通过：可建立会话";
    }

    static void singleLogout() {
        System.out.println("【单点登出（SLO）】");
        Map<String, String> approaches = new LinkedHashMap<>();
        approaches.put("back-channel 通知", "IdP 主动调各 SP 的登出接口（需注册 SP 回调）");
        approaches.put("短期 token", "access_token 15 分钟 + 定期校验 IdP（最多滞后 15 分钟）");
        approaches.put("前端轮询", "SP 定时查 IdP 会话状态（成本高，兜底用）");
        approaches.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));
        System.out.println("  工程取舍：安全要求高用 back-channel；一般系统用短期 token 可接受");
    }

    public static void main(String[] args) {
        System.out.println("【SSO 登录流程】");
        String code = authorize("app-order", "https://order.example.com/callback", "alice", 1000);
        System.out.println("  " + exchange(code, "app-order", "app-order"));
        System.out.println("  " + exchange(code, "app-order", "app-order"));

        System.out.println();
        System.out.println("【令牌校验】");
        IdToken good = new IdToken("https://idp.example.com", "app-order", "user-1001",
                9_999_999, "n", "sig-valid");
        System.out.println("  " + validate(good, "https://idp.example.com", "app-order", 1000));
        System.out.println("  " + validate(good, "https://idp.example.com", "app-other", 1000));
        System.out.println("  " + validate(new IdToken("https://idp.example.com", "app-order",
                "u", 500, "n", "sig-valid"), "https://idp.example.com", "app-order", 1000));

        System.out.println();
        singleLogout();
        System.out.println();
        System.out.println("  边界：SSO 只解决认证，业务权限仍由各系统自己管（74-04）");
    }
}
```

**课后练习**
1. 实现 SSO 流程：authorize/exchange/validate，验证 code 一次性与令牌三项校验（iss/aud/exp）。
2. 对比 back-channel 登出与短期 token 的"安全性与实现成本"，给出你系统的选择。
3. 思考题：为什么 SSO"不能替代授权"？从 IdP 不知道业务权限模型分析。

---

### 74-03 组织架构与用户体系

**知识点**
- **权限系统的地基是组织架构**：**租户 -> 公司 -> 部门（树）-> 用户**，用户可**兼职多部门**、部门可**层级嵌套**——**数据权限（74-05）依赖这棵树**（"本部门及下级"）、**审批流依赖这棵树**（61-07 的审批人规则）。
- **用户的三种身份来源**：**内部员工**（HR 系统同步）、**外部客户**（注册）、**系统账号**（服务间调用）——**同一套权限模型要能容纳三类**（用"账号类型"区分）。
- **组织架构的变更很频繁**：部门合并/拆分/人员调动——**权限要"跟着组织走"而不是"跟着人走"**（角色绑在岗位/部门上，人调岗自动获得新权限、失去旧权限）。
- **与 55 章的关系**：多租户下"组织架构也是租户隔离的"（`tenantId + deptId` 双键）；**跨租户的组织数据绝不能混**（55-04）。
- 易错点：部门树用单表 parentId 但查询"所有下级"用递归查询（性能差，应用闭包表/物化路径 34 章）；兼职多部门只支持一个（权限缺失）；人员离职只禁用账号不回收权限（僵尸权限）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrgStructure {
    record Dept(long id, String name, Long parentId, String path) {
        /** 物化路径：/1/10/100/ 便于查"所有下级" */
        String display() {
            return id + ":" + name + " path=" + path;
        }
    }

    record User(String id, String name, List<Long> deptIds, String accountType,
                boolean active) {
    }

    static final Map<Long, Dept> depts = new LinkedHashMap<>();
    static final List<User> users = new ArrayList<>();

    static void seed() {
        depts.put(1L, new Dept(1, "集团", null, "/1/"));
        depts.put(10L, new Dept(10, "电商事业部", 1L, "/1/10/"));
        depts.put(100L, new Dept(100, "订单组", 10L, "/1/10/100/"));
        depts.put(101L, new Dept(101, "支付组", 10L, "/1/10/101/"));
        depts.put(20L, new Dept(20, "技术中心", 1L, "/1/20/"));
        users.add(new User("u-1", "张三", List.of(100L), "EMPLOYEE", true));
        users.add(new User("u-2", "李四", List.of(100L, 101L), "EMPLOYEE", true));  // 兼职
        users.add(new User("u-3", "王五", List.of(20L), "EMPLOYEE", false));         // 离职
        users.add(new User("svc-1", "订单服务", List.of(), "SYSTEM", true));
    }

    /** 查所有下级部门（用物化路径前缀，避免递归） */
    static List<Long> descendantsOf(long deptId) {
        Dept root = depts.get(deptId);
        List<Long> out = new ArrayList<>();
        for (Dept d : depts.values()) {
            if (d.id() == deptId || d.path().startsWith(root.path())) {
                out.add(d.id());
            }
        }
        return out;
    }

    /** 用户可见部门范围（含兼职） */
    static Set<Long> visibleDepts(String userId, boolean includeChildren) {
        Set<Long> out = new LinkedHashSet<>();
        User u = users.stream().filter(x -> x.id().equals(userId)).findFirst().orElse(null);
        if (u == null || !u.active()) {
            return out;
        }
        for (Long d : u.deptIds()) {
            out.add(d);
            if (includeChildren) {
                out.addAll(descendantsOf(d));
            }
        }
        return out;
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【部门树（物化路径）】");
        depts.values().forEach(d -> System.out.println("  " + d.display()));

        System.out.println();
        System.out.println("【查所有下级（路径前缀）】");
        System.out.println("  事业部 10 的下级：" + descendantsOf(10));
        System.out.println("  技术中心 20 的下级：" + descendantsOf(20));

        System.out.println();
        System.out.println("【用户可见范围】");
        System.out.println("  u-1（订单组，含下级）：" + visibleDepts("u-1", true));
        System.out.println("  u-2（订单组+支付组，兼职）：" + visibleDepts("u-2", true));
        System.out.println("  u-3（离职）：" + visibleDepts("u-3", true));
        System.out.println("  svc-1（系统账号，无部门）：" + visibleDepts("svc-1", true));

        System.out.println();
        System.out.println("【设计要点】");
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("物化路径", "path 存 /1/10/100/，查下级用 LIKE '/1/10/%'，避免递归");
        notes.put("兼职", "用户与部门多对多；权限取并集");
        notes.put("离职", "active=false + 权限回收任务（防僵尸权限 74-10）");
        notes.put("租户", "所有表带 tenantId（55-04）");
        notes.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 实现部门树（物化路径）与"所有下级"查询，用 5 个部门验证；对比递归查询的复杂度。
2. 实现兼职用户的多部门可见范围（取并集），验证 u-2 能同时看到两个组的数据。
3. 思考题：为什么"权限要跟着组织走"？从人员调岗后"权限残留/权限缺失"两个问题分析。

---

### 74-04 RBAC：用户-角色-权限模型

**知识点**
- **RBAC 的三张表**：**用户**（user）、**角色**（role）、**权限**（permission）+ 两张关联表（user_role、role_permission）——**用户不直接绑权限，通过角色间接获得**，这是可维护性的关键（1000 个用户不用配 1000 次权限）。
- **权限的最小粒度是"资源 + 操作"**：`order:read`、`order:refund`、`user:delete`——**用"资源:动作"命名**，与 64-02 的 scope 同构；**不可用"接口路径"直接当权限**（路径变了权限就失效）。
- **角色的两种设计**：**业务角色**（运营/客服/财务——贴近组织）与**功能角色**（订单管理员/审核员——贴近功能）——**推荐"业务角色 + 少量功能角色"混合**，避免角色爆炸。
- **权限的继承与分组**：角色可继承（"财务主管"继承"财务专员"再追加权限）、权限可分组（菜单树 74-06）——**继承要谨慎**（多层继承难排查）。
- 与 68-05 的关系：RBAC 管"功能权限"，数据权限（行/列）要额外建模（74-05）——**RBAC 不自带数据范围**。
- 易错点：用户直接绑权限（绕过角色，无法批量调整）；角色数量爆炸（每个用户一个角色）；权限命名用中文/路径（不可维护）；不校验后端（只前端隐藏按钮）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RbacModel {
    record Permission(String code, String name, String group) {
    }

    record Role(String code, String name, Set<String> permissions,
                List<String> inherits) {
    }

    record UserAccount(String id, String name, Set<String> roles) {
    }

    static final Map<String, Permission> permissions = new LinkedHashMap<>();
    static final Map<String, Role> roles = new LinkedHashMap<>();
    static final Map<String, UserAccount> users = new LinkedHashMap<>();

    static void seed() {
        permissions.put("order:read", new Permission("order:read", "查看订单", "订单"));
        permissions.put("order:refund", new Permission("order:refund", "订单退款", "订单"));
        permissions.put("order:export", new Permission("order:export", "导出订单", "订单"));
        permissions.put("user:read", new Permission("user:read", "查看用户", "用户"));
        permissions.put("user:delete", new Permission("user:delete", "删除用户", "用户"));

        roles.put("agent", new Role("agent", "客服", Set.of("order:read", "user:read"),
                List.of()));
        roles.put("agent-lead", new Role("agent-lead", "客服主管",
                Set.of("order:refund"), List.of("agent")));
        roles.put("finance", new Role("finance", "财务", Set.of("order:export"), List.of()));
        roles.put("admin", new Role("admin", "管理员",
                Set.of("order:read", "order:refund", "order:export",
                        "user:read", "user:delete"), List.of()));

        users.put("u-1", new UserAccount("u-1", "小客服", Set.of("agent")));
        users.put("u-2", new UserAccount("u-2", "客服主管", Set.of("agent-lead")));
        users.put("u-3", new UserAccount("u-3", "财务", Set.of("finance", "agent")));
        users.put("u-4", new UserAccount("u-4", "管理员", Set.of("admin")));
    }

    /** 解析用户的有效权限：角色 + 继承链（防环用 visited） */
    static Set<String> effectivePermissions(String userId) {
        UserAccount u = users.get(userId);
        if (u == null) {
            return Set.of();
        }
        Set<String> perms = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String roleCode : u.roles()) {
            collect(roleCode, perms, visited);
        }
        return perms;
    }

    static void collect(String roleCode, Set<String> perms, Set<String> visited) {
        if (!visited.add(roleCode)) {
            return;
        }
        Role r = roles.get(roleCode);
        if (r == null) {
            return;
        }
        perms.addAll(r.permissions());
        for (String parent : r.inherits()) {
            collect(parent, perms, visited);
        }
    }

    static boolean can(String userId, String permissionCode) {
        return effectivePermissions(userId).contains(permissionCode);
    }

    /** 权限校验入口（统一收口，避免散落） */
    static String check(String userId, String permissionCode) {
        boolean ok = can(userId, permissionCode);
        System.out.printf("  %-4s 请求 %-13s -> %s%n", userId, permissionCode,
                ok ? "允许" : "拒绝（403）");
        return ok ? "ALLOW" : "DENY";
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【角色与权限】");
        roles.forEach((code, r) -> System.out.printf("  %-10s %-6s 权限=%s 继承=%s%n",
                code, r.name(), r.permissions(), r.inherits()));

        System.out.println();
        System.out.println("【有效权限（含继承）】");
        users.forEach((id, u) -> System.out.printf("  %-4s %-6s roles=%-24s effective=%s%n",
                id, u.name(), u.roles(), effectivePermissions(id)));

        System.out.println();
        System.out.println("【权限校验】");
        check("u-2", "order:refund");      // 继承 agent-lead -> 有
        check("u-2", "order:export");      // 无
        check("u-3", "order:export");      // finance -> 有
        check("u-1", "user:delete");       // 无

        System.out.println();
        System.out.println("【设计要点】");
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("权限命名", "资源:动作（order:refund），不用路径/中文");
        notes.put("角色来源", "业务角色为主（客服/财务），少量功能角色");
        notes.put("继承深度", "≤2 层（再深难排查）");
        notes.put("校验收口", "统一 can() 入口，禁止散落 if");
        notes.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 实现 RBAC：角色继承 + 有效权限解析（防环）+ 统一校验入口，用 4 个用户验证。
2. 对比"用户直连权限"与"用户-角色-权限"在"100 人调岗"场景下的维护成本。
3. 思考题：为什么权限命名用"资源:动作"而不是接口路径？从"接口重构"与"跨端一致"分析。

---

### 74-05 权限模型进阶：ABAC 与数据权限

**知识点**
- **RBAC 不够用的场景**：**"客服只能看自己负责的订单"**（数据维度依赖用户属性）、**"工作时间外禁止导出"**（环境属性）、**"金额超 1 万需主管审批"**（资源属性）——**RBAC 只回答"能不能做这个动作"，不回答"能对哪些数据做"**。
- **ABAC（基于属性的访问控制）**：**主体属性**（用户等级/部门）、**资源属性**（订单金额/归属）、**环境属性**（时间/IP）、**动作**——用**策略表达式**描述（如 `subject.dept == resource.dept && env.hour in 9..18`）。
- **数据权限的四种范围**（企业标配）：**本人**（OWNER）、**本部门**（DEPT）、**本部门及下级**（DEPT_AND_CHILD 74-03）、**全部**（ALL）——**按角色配置范围，查询时注入条件**（68-05 的落地）。
- **数据权限的实现位置**：**SQL 注入**（查询条件自动加 `dept_id IN (...)`，55-03 同款）、**数据权限中间件**、**数据库行级安全**——**应用层注入最通用**。
- 与 68-05 的关系：68-05 讲"数据权限的实现"，本章讲"权限模型怎么组织"（RBAC + ABAC + 数据范围三层叠加）——**模型与实现分开看**。
- 易错点：数据权限只在前端过滤（接口返回全量）；范围计算不递归（主管看不到下级部门）；ABAC 策略太复杂（无法排查）；环境属性可伪造（IP 头/时间客户端传）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AbacAndDataScope {
    enum Scope {
        OWNER, DEPT, DEPT_AND_CHILD, ALL
    }

    record Subject(String userId, String deptId, String level, Set<Long> deptTree) {
    }

    record Order(String orderId, String ownerId, String deptId, long amountCents) {
    }

    record ResourceAttr(String ownerId, String deptId, long amountCents) {
    }

    /** 数据范围 -> 可见的 dept 集合（ALL 返回 null 表示不过滤） */
    static Set<String> scopeDepts(Scope scope, Subject s) {
        return switch (scope) {
            case OWNER -> Set.of();
            case DEPT -> Set.of(s.deptId());
            case DEPT_AND_CHILD -> {
                Set<String> out = new LinkedHashSet<>();
                for (Long d : s.deptTree()) {
                    out.add(String.valueOf(d));
                }
                yield out;
            }
            case ALL -> null;
        };
    }

    /** 行级过滤 */
    static List<Order> filter(List<Order> orders, Scope scope, Subject s) {
        if (scope == Scope.ALL) {
            return orders;
        }
        List<Order> out = new ArrayList<>();
        Set<String> depts = scopeDepts(scope, s);
        for (Order o : orders) {
            if (scope == Scope.OWNER && o.ownerId().equals(s.userId())) {
                out.add(o);
            } else if (scope == Scope.DEPT && o.deptId().equals(s.deptId())) {
                out.add(o);
            } else if (scope == Scope.DEPT_AND_CHILD && depts.contains(o.deptId())) {
                out.add(o);
            }
        }
        return out;
    }

    /** ABAC 策略：主体属性 + 资源属性 + 环境属性 */
    record Policy(String name, String expr) {
    }

    static final List<Policy> policies = List.of(
            new Policy("工作时间外禁止导出",
                    "env.hour < 9 || env.hour > 18 -> DENY export"),
            new Policy("大额订单仅主管可退",
                    "resource.amount > 1000000 && subject.level != LEAD -> DENY refund"),
            new Policy("跨部门订单只读",
                    "resource.dept != subject.dept -> READ_ONLY"));

    static String evaluate(String action, Subject s, ResourceAttr r, int hour) {
        if (action.equals("export") && (hour < 9 || hour > 18)) {
            return "DENY（工作时间外禁止导出：" + hour + " 点）";
        }
        if (action.equals("refund") && r.amountCents() > 1_000_000
                && !s.level().equals("LEAD")) {
            return "DENY（大额订单需主管）";
        }
        if (!r.deptId().equals(s.deptId())) {
            return "READ_ONLY（跨部门）";
        }
        return "ALLOW";
    }

    public static void main(String[] args) {
        Subject agent = new Subject("u-1", "100", "STAFF",
                Set.of(100L, 1001L, 1002L));
        Subject lead = new Subject("u-2", "100", "LEAD", Set.of(100L, 1001L, 1002L));

        List<Order> orders = List.of(
                new Order("o-1", "u-1", "100", 69_00),
                new Order("o-2", "u-9", "100", 199_00),
                new Order("o-3", "u-8", "1001", 39_00),
                new Order("o-4", "u-7", "200", 129_00));

        System.out.println("【数据范围对比】");
        for (Scope scope : Scope.values()) {
            List<String> ids = filter(orders, scope, agent).stream().map(Order::orderId).toList();
            System.out.printf("  %-16s -> %s%n", scope, ids);
        }

        System.out.println();
        System.out.println("【ABAC 策略】");
        policies.forEach(p -> System.out.printf("  %-18s %s%n", p.name(), p.expr()));

        System.out.println();
        System.out.println("【策略求值】");
        ResourceAttr big = new ResourceAttr("u-9", "100", 5_000_000);
        ResourceAttr crossDept = new ResourceAttr("u-7", "200", 129_00);
        System.out.println("  agent(10 点) export 本部门：" + evaluate("export", agent, big, 10));
        System.out.println("  agent(22 点) export 本部门：" + evaluate("export", agent, big, 22));
        System.out.println("  agent 退款大额：" + evaluate("refund", agent, big, 10));
        System.out.println("  lead  退款大额：" + evaluate("refund", lead, big, 10));
        System.out.println("  agent 操作跨部门：" + evaluate("export", agent, crossDept, 10));

        System.out.println();
        System.out.println("【模型分层】RBAC 管功能（74-04）-> 数据范围管行（本章）-> 字段脱敏管列（68-03）");
    }
}
```

**课后练习**
1. 实现四种数据范围的行级过滤，用 4 条订单验证 OWNER/DEPT/DEPT_AND_CHILD/ALL 的差异。
2. 实现 3 条 ABAC 策略（时间/金额/跨部门），用 5 个请求验证求值结果。
3. 思考题：为什么"环境属性（时间/IP）"不能信任客户端传值？构造伪造 IP 头绕过策略的例子。

---

### 74-06 权限树与菜单渲染

**知识点**
- **菜单/按钮是权限的"展示形态"**：前端需要一棵**权限树**（目录 -> 菜单 -> 按钮）来渲染侧边栏与按钮显隐——**后端返回"用户可见的权限树"**，前端按树渲染。
- **权限树的两种构建方式**：**后端构树**（返回嵌套 JSON，前端直接渲染，推荐）、**前端构树**（返回扁平列表，前端按 parentId 组装）——**后端构树减少前端逻辑**。
- **菜单与权限码的绑定**：菜单节点带 `permissionCode`（如 `order` 目录、`order:read` 菜单、`order:refund` 按钮）——**渲染时按权限码过滤**（无权限的节点不返回或标记不可见）。
- **与 71-04 的关系**：低代码平台的"元数据权限"也是同一套（字段/操作/菜单）——**权限树是通用形态**。
- **性能**：权限树每次登录算一次（缓存 74-07）、**节点数控制在几百以内**（菜单不是无限嵌套）、**前端路由与权限树对齐**（避免"有菜单无路由"）。
- 易错点：返回全量菜单前端隐藏（用户能看到无权菜单的结构 36-05）；权限树与路由不同步（点菜单 404）；树无排序（菜单乱序）；递归构树有环（数据错误导致栈溢出）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PermissionTree {
    record Node(long id, Long parentId, String name, String type, String permissionCode,
                int sort) {
    }

    static final List<Node> allNodes = new ArrayList<>();

    static void seed() {
        allNodes.add(new Node(1, null, "订单中心", "DIR", null, 1));
        allNodes.add(new Node(2, 1L, "订单列表", "MENU", "order:read", 1));
        allNodes.add(new Node(3, 1L, "退款管理", "MENU", "order:refund", 2));
        allNodes.add(new Node(4, 2L, "导出按钮", "BUTTON", "order:export", 1));
        allNodes.add(new Node(5, null, "用户中心", "DIR", null, 2));
        allNodes.add(new Node(6, 5L, "用户列表", "MENU", "user:read", 1));
        allNodes.add(new Node(7, 6L, "删除按钮", "BUTTON", "user:delete", 1));
    }

    /** 按权限过滤：无权限码的目录保留（若其下有可见子节点） */
    static List<Node> filterByPerms(Set<String> perms) {
        List<Node> visible = new ArrayList<>();
        for (Node n : allNodes) {
            boolean ok = n.permissionCode() == null || perms.contains(n.permissionCode());
            if (ok) {
                visible.add(n);
            }
        }
        // 自底向上：若目录下没有可见子节点，目录也去掉
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Node> next = new ArrayList<>();
            for (Node n : visible) {
                boolean hasChild = visible.stream().anyMatch(c ->
                        c.parentId() != null && c.parentId() == n.id());
                boolean isLeaf = !visible.stream().anyMatch(c ->
                        c.parentId() != null && c.parentId() == n.id());
                if (n.type().equals("DIR") && isLeaf) {
                    changed = true;
                    continue;
                }
                next.add(n);
            }
            visible = next;
        }
        return visible;
    }

    /** 构树：递归 + 排序 */
    static List<String> buildTree(List<Node> nodes, Long parentId, int depth) {
        List<String> lines = new ArrayList<>();
        List<Node> children = new ArrayList<>();
        for (Node n : nodes) {
            if (parentId == null ? n.parentId() == null : n.parentId() != null
                    && n.parentId().equals(parentId)) {
                children.add(n);
            }
        }
        children.sort((a, b) -> Integer.compare(a.sort(), b.sort()));
        for (Node n : children) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                sb.append("  ");
            }
            sb.append(n.type().equals("BUTTON") ? "- " : "▸ ")
                    .append(n.name()).append(" [").append(n.type()).append("]");
            if (n.permissionCode() != null) {
                sb.append(" (").append(n.permissionCode()).append(")");
            }
            lines.add(sb.toString());
            lines.addAll(buildTree(nodes, n.id(), depth + 1));
        }
        return lines;
    }

    public static void main(String[] args) {
        seed();
        Map<String, Set<String>> rolePerms = new LinkedHashMap<>();
        rolePerms.put("客服（order:read + user:read）", Set.of("order:read", "user:read"));
        rolePerms.put("客服主管（+refund/export）",
                Set.of("order:read", "order:refund", "order:export", "user:read"));
        rolePerms.put("管理员（全部）",
                Set.of("order:read", "order:refund", "order:export",
                        "user:read", "user:delete"));

        rolePerms.forEach((role, perms) -> {
            System.out.println("【" + role + "】");
            List<Node> visible = filterByPerms(perms);
            buildTree(visible, null, 1).forEach(l -> System.out.println("    " + l));
            System.out.println();
        });

        System.out.println("【要点】");
        System.out.println("  1) 返回「过滤后的树」，而不是全量菜单 + 前端隐藏（36-05）");
        System.out.println("  2) 空的目录节点要自底向上剪掉（否则侧边栏有空洞）");
        System.out.println("  3) 菜单节点与前端路由表对齐（避免点开 404）");
        System.out.println("  4) 权限树每次登录算一次 + 缓存（74-07）");
    }
}
```

**课后练习**
1. 实现权限树过滤与构树：3 个角色各输出可见树，验证"空目录被剪掉"。
2. 实现"前端路由对齐检查"：比对权限树的菜单码与前端路由表，输出"有菜单无路由"的清单。
3. 思考题：为什么"返回全量菜单 + 前端隐藏"是安全问题？从"菜单结构泄露"与"直接调 API"分析。

---

### 74-07 权限缓存与失效

**知识点**
- **权限校验是高频操作**：每个请求都要查权限（74-04 的 `can()`）——**每次都查库会把权限表打爆**，必须缓存。
- **缓存的三层**：**本地缓存**（Caffeine/ConcurrentHashMap，微秒级，但多实例不一致）、**分布式缓存**（Redis，毫秒级，一致性好 25 章）、**会话内缓存**（登录时算好塞进 token/session，零查询但失效难）——**常用：本地 + Redis 两级**。
- **失效的三种触发**：**权限变更**（改角色权限）、**用户角色变更**（调岗）、**用户登出/被禁用**——**失效要广播**（多实例本地缓存要清，55-08 同款问题）。
- **失效策略**：**主动失效**（变更时删缓存 + 广播，实时性好）、**TTL 过期**（简单但有滞后窗口）、**版本号**（权限数据带版本，缓存 key 含版本，变更即换 key——最优雅）——**推荐"版本号 + 短期 TTL 兜底"**。
- 与 55/68 章的关系：多租户缓存 key 要含 tenantId（55-04）、权限数据本身是敏感数据（68-02）。
- 易错点：缓存永不过期（改权限不生效，用户投诉）；只清本地缓存不清其他实例（部分节点权限错乱）；缓存 key 不含租户（跨租户串权限）；改了权限不通知用户（需重新登录）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class PermissionCache {
    /** 权限版本：每次变更 +1，缓存 key 含版本 -> 旧缓存自然失效 */
    static long permVersion = 1;

    /** 模拟两级缓存：本地 + 分布式 */
    static final Map<String, Set<String>> localCache = new LinkedHashMap<>();
    static final Map<String, Set<String>> redisCache = new LinkedHashMap<>();

    /** 权限源（数据库） */
    static final Map<String, Set<String>> db = new LinkedHashMap<>();

    static void seed() {
        db.put("u-1", Set.of("order:read"));
        db.put("u-2", Set.of("order:read", "order:refund"));
    }

    static String key(String tenantId, String userId) {
        return tenantId + ":" + userId + ":v" + permVersion;
    }

    static Set<String> getPermissions(String tenantId, String userId) {
        String k = key(tenantId, userId);
        Set<String> hit = localCache.get(k);
        if (hit != null) {
            System.out.printf("    本地命中 %s%n", k);
            return hit;
        }
        hit = redisCache.get(k);
        if (hit != null) {
            System.out.printf("    Redis 命中 %s%n", k);
            localCache.put(k, hit);
            return hit;
        }
        hit = db.getOrDefault(userId, Set.of());
        System.out.printf("    回源 DB %s -> %s%n", userId, hit);
        redisCache.put(k, hit);
        localCache.put(k, hit);
        return hit;
    }

    static void changePermission(String userId, Set<String> newPerms) {
        db.put(userId, newPerms);
        permVersion++;
        System.out.printf("  权限变更：%s -> %s，版本升到 v%d%n", userId, newPerms, permVersion);
        System.out.println("  旧版本缓存（v" + (permVersion - 1) + "）不再被查询到 -> 等价于失效");
    }

    /** 主动失效：广播到所有实例（示意） */
    static void broadcastInvalidate(String userId) {
        System.out.printf("  广播失效：清本地缓存中 %s 的所有版本%n", userId);
        localCache.keySet().removeIf(k -> k.contains(":" + userId + ":"));
    }

    static void ttlFallback() {
        System.out.println("【TTL 兜底】");
        System.out.println("  即使广播丢失，缓存也有 5 分钟 TTL -> 最多滞后 5 分钟");
        System.out.println("  高敏感权限（删除/审批）：TTL 缩短到 30 秒或直接不缓存");
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【首次查询（回源）】");
        System.out.println("  u-1 -> " + getPermissions("t-100", "u-1"));
        System.out.println("  u-1 再查（本地命中）：" + getPermissions("t-100", "u-1"));

        System.out.println();
        System.out.println("【权限变更 + 版本号】");
        changePermission("u-1", Set.of("order:read", "order:export", "order:refund"));
        System.out.println("  u-1 查询（新版本 key，未命中 -> 回源）："
                + getPermissions("t-100", "u-1"));

        System.out.println();
        System.out.println("【多实例本地缓存失效】");
        System.out.println("  实例 A / B 各有本地缓存 -> 变更时需广播");
        broadcastInvalidate("u-1");

        System.out.println();
        System.out.println("【缓存 key 设计】");
        System.out.println("  " + key("t-100", "u-1") + "（含租户 + 用户 + 版本）");
        System.out.println("  缺租户 -> 跨租户串权限（55-04）");
        System.out.println("  缺版本 -> 变更后仍读旧缓存");

        System.out.println();
        ttlFallback();
        System.out.println();
        System.out.println("  推荐组合：版本号（主）+ 广播（快）+ 短期 TTL（兜底）");
    }
}
```

**课后练习**
1. 实现两级缓存 + 版本号失效：验证权限变更后"新 key 未命中回源"，旧缓存自然废弃。
2. 实现"广播失效"：模拟 3 个实例的本地缓存，变更后全部清除（含广播丢失时的 TTL 兜底）。
3. 思考题：为什么"缓存 key 不含租户"会导致跨租户串权限？构造两个租户同 userId 的场景说明。

---

### 74-08 越权防护与权限测试

**知识点**
- **两类越权**：**水平越权**（同角色用户互相越界：用户 A 看 B 的订单）、**垂直越权**（低权限做高权限的事：普通用户调管理接口）——**水平越权更常见**（36-05 的 IDOR）。
- **防护三原则**：**每个接口都鉴权**（不能依赖"这个接口没人知道"）、**资源归属双键校验**（`tenantId + ownerId`，55-04）、**拒绝返回 404 而非 403**（不泄露存在性 68-05）。
- **权限测试的四个层次**：**功能点覆盖**（每个接口 × 每个角色）、**参数篡改**（改 id/租户/角色）、**边界权限**（有 read 无 write）、**越权路径**（绕过前端直接调 API）——**自动化程度决定测试覆盖率**。
- **测试矩阵**：`角色 × 接口 × 预期结果`——**用数据驱动测试**（33 章的方法）；**重点覆盖"敏感接口"**（删除/退款/导出）。
- 与 36-05 的关系：36-05 讲"IDOR 的原理与防护"，本章讲"怎么系统性地测出来"——**测试是防护的验证**。
- 易错点：只测"有权限能通过"不测"无权限被拒"（漏掉 60% 的用例）；测试环境用管理员跑（全通过无意义）；不测参数篡改（改 id 就绕过）；越权返回 403（泄露存在性）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuthzTesting {
    enum Expect {
        ALLOW, DENY, NOT_FOUND
    }

    record Case(String role, String userId, String action, String resourceOwner,
                String resourceTenant, String userTenant, Set<String> perms,
                Expect expect, String note) {
    }

    /** 模拟接口：功能权限 + 归属 + 租户 */
    static String invoke(Case c) {
        // 1) 功能权限
        if (!c.perms().contains(c.action())) {
            return "403";
        }
        // 2) 租户隔离
        if (!c.userTenant().equals(c.resourceTenant())) {
            return "404";
        }
        // 3) 归属（水平越权）
        if (!c.userId().equals(c.resourceOwner())) {
            return "404";
        }
        return "200";
    }

    static void runMatrix(List<Case> cases) {
        int pass = 0;
        int fail = 0;
        for (Case c : cases) {
            String actual = invoke(c);
            boolean ok = switch (c.expect()) {
                case ALLOW -> actual.equals("200");
                case DENY -> actual.equals("403");
                case NOT_FOUND -> actual.equals("404");
            };
            if (ok) {
                pass++;
            } else {
                fail++;
            }
            System.out.printf("  %-6s %-6s %-16s 期望=%-10s 实际=%-4s %s%n",
                    c.role, c.userId(), c.action(), c.expect(), actual,
                    ok ? "PASS" : "FAIL <<< " + c.note());
        }
        System.out.printf("  结果：pass=%d fail=%d%n", pass, fail);
        System.out.println();
    }

    public static void main(String[] args) {
        Set<String> agentPerms = Set.of("order:read");
        Set<String> adminPerms = Set.of("order:read", "order:delete", "user:delete");

        List<Case> cases = new ArrayList<>(List.of(
                // 有权限 + 自己的资源 -> 允许
                new Case("客服", "u-1", "order:read", "u-1", "t-100", "t-100",
                        agentPerms, Expect.ALLOW, "基线"),
                // 水平越权：改 ownerId
                new Case("客服", "u-1", "order:read", "u-2", "t-100", "t-100",
                        agentPerms, Expect.NOT_FOUND, "水平越权应 404"),
                // 垂直越权：客服调删除
                new Case("客服", "u-1", "order:delete", "u-1", "t-100", "t-100",
                        agentPerms, Expect.DENY, "垂直越权应 403"),
                // 跨租户
                new Case("客服", "u-1", "order:read", "u-1", "t-200", "t-100",
                        agentPerms, Expect.NOT_FOUND, "跨租户应 404"),
                // 管理员自己的资源
                new Case("管理员", "u-9", "order:delete", "u-9", "t-100", "t-100",
                        adminPerms, Expect.ALLOW, "管理员删除自己租户"),
                // 管理员也不能跨租户
                new Case("管理员", "u-9", "order:delete", "u-9", "t-200", "t-100",
                        adminPerms, Expect.NOT_FOUND, "管理员也受租户隔离")
        ));

        System.out.println("【权限测试矩阵】");
        runMatrix(cases);

        System.out.println("【补一个失败用例（演示 FAIL 输出）】");
        List<Case> bad = List.of(new Case("客服", "u-1", "order:read", "u-2",
                "t-100", "t-100", agentPerms, Expect.ALLOW, "误把越权写成允许"));
        runMatrix(bad);

        System.out.println("【测试设计要点】");
        Map<String, String> points = new LinkedHashMap<>();
        points.put("覆盖", "角色 × 接口 × 预期（ALLOW/DENY/NOT_FOUND）");
        points.put("必测", "无权限被拒（不能只测有权限通过）");
        points.put("参数篡改", "改 id / ownerId / tenantId / 角色字段");
        points.put("数据驱动", "用例表进代码库，随接口变更维护（33 章）");
        points.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 为你的 3 个敏感接口（删除/退款/导出）× 3 个角色写权限测试矩阵（27 条），标注预期结果。
2. 实现参数篡改用例：改 ownerId/tenantId 各一条，验证返回 404 而非 403。
3. 思考题：为什么"越权返回 404"比 403 更安全？从"存在性枚举"与"攻击者侦察"分析。

---

### 74-09 多租户与权限隔离

**知识点**
- **多租户权限的两个层次**：**租户内权限**（RBAC/数据范围，74-04/05）与**租户间隔离**（A 租户的用户绝不可能看到 B 租户的任何数据）——**隔离是硬约束，权限是软约束**。
- **隔离的三道防线**：**数据层**（所有表带 `tenant_id` + 查询强制注入 55-03）、**缓存层**（key 含 tenantId 74-07）、**令牌层**（token 里带 tenantId，服务端从 token 取而非请求头 55-03）。
- **平台运营角色的特殊权限**：平台管理员要跨租户（运维/客服支持），但**必须审计 + 租户授权**（55-10 的授权代理模式）——**"跨租户能力"本身是最高危权限**。
- **租户配置对权限的影响**：租户可自定义角色（55-06 的配置分层）——**平台内置角色 + 租户自定义角色**并存，**租户不能修改平台内置角色**（防越权）。
- 与 55 章的关系：55 章讲多租户架构（隔离三方案），本章讲"权限在多租户下怎么组织"——**同一套 RBAC，多了租户维度**。
- 易错点：权限表不带 tenantId（A 租户配的角色 B 租户能用）；平台管理员无审计（内部作恶 51-07）；租户自建角色可提权（拿到平台级权限）；缓存 key 缺租户（74-07）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiTenantAuthz {
    record RoleDef(String tenantId, String code, String name, Set<String> perms,
                   boolean builtin) {
    }

    static final Map<String, RoleDef> roleTable = new LinkedHashMap<>();

    static void seed() {
        // 平台内置角色（tenantId = PLATFORM）
        roleTable.put("PLATFORM:admin", new RoleDef("PLATFORM", "admin", "平台管理员",
                Set.of("tenant:read", "tenant:manage", "order:read"), true));
        // 租户角色
        roleTable.put("t-100:agent", new RoleDef("t-100", "agent", "客服",
                Set.of("order:read"), false));
        roleTable.put("t-100:finance", new RoleDef("t-100", "finance", "财务",
                Set.of("order:read", "order:export"), false));
        roleTable.put("t-200:agent", new RoleDef("t-200", "agent", "客服",
                Set.of("order:read", "user:read"), false));
    }

    /** 解析权限：必须带租户，内置角色仅平台可用 */
    static Set<String> permsOf(String tenantId, String roleCode) {
        RoleDef def = roleTable.get(tenantId + ":" + roleCode);
        if (def == null) {
            // 找不到本租户角色 -> 尝试平台内置（仅平台租户）
            def = roleTable.get("PLATFORM:" + roleCode);
            if (def == null || !tenantId.equals("PLATFORM")) {
                return Set.of();
            }
        }
        return def.perms();
    }

    /** 租户自建角色不能包含平台级权限 */
    static String createRole(String tenantId, String code, Set<String> perms) {
        if (tenantId.equals("PLATFORM")) {
            return "拒绝：平台角色需走变更流程";
        }
        for (String p : perms) {
            if (p.startsWith("tenant:") || p.startsWith("platform:")) {
                return "拒绝：租户角色不可含平台权限 " + p;
            }
        }
        roleTable.put(tenantId + ":" + code, new RoleDef(tenantId, code, code, perms, false));
        return "创建成功：" + tenantId + ":" + code + " -> " + perms;
    }

    /** 跨租户访问：平台管理员 + 审计 */
    static String crossTenantAccess(String actorTenant, Set<String> perms,
                                    String targetTenant, String action) {
        if (!actorTenant.equals("PLATFORM")) {
            return "拒绝：非平台账号（跨租户需 404）";
        }
        if (!perms.contains("tenant:manage")) {
            return "拒绝：缺少 tenant:manage";
        }
        System.out.printf("  [审计] 平台管理员访问 %s 的 %s（记录操作人与原因）%n",
                targetTenant, action);
        return "允许（已审计）";
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【角色表（含租户与内置标记）】");
        roleTable.forEach((k, v) -> System.out.printf("  %-18s 权限=%-36s 内置=%b%n",
                k, v.perms(), v.builtin()));

        System.out.println();
        System.out.println("【按租户解析权限】");
        System.out.println("  t-100 + agent：" + permsOf("t-100", "agent"));
        System.out.println("  t-200 + agent：" + permsOf("t-200", "agent")
                + "（同名角色，权限不同）");
        System.out.println("  t-100 + admin：" + permsOf("t-100", "admin")
                + "（租户不能拿平台角色）");
        System.out.println("  PLATFORM + admin：" + permsOf("PLATFORM", "admin"));

        System.out.println();
        System.out.println("【租户自建角色】");
        System.out.println("  " + createRole("t-100", "auditor", Set.of("order:read")));
        System.out.println("  " + createRole("t-100", "sneaky", Set.of("order:read", "tenant:manage")));

        System.out.println();
        System.out.println("【跨租户访问】");
        System.out.println("  t-100 客服查 t-200："
                + crossTenantAccess("t-100", permsOf("t-100", "agent"), "t-200", "order:read"));
        System.out.println("  平台管理员查 t-200："
                + crossTenantAccess("PLATFORM", permsOf("PLATFORM", "admin"), "t-200", "order:read"));

        System.out.println();
        System.out.println("【三道防线】数据层 tenant_id（55-03）+ 缓存 key（74-07）+ 令牌携带（55-03）");
    }
}
```

**课后练习**
1. 实现多租户角色解析：同名角色在不同租户权限不同、租户不能拿平台角色，用 4 组验证。
2. 实现"租户自建角色禁止平台权限"的校验，构造一次提权尝试验证拒绝。
3. 思考题：为什么"跨租户能力"是最高危权限？从"单点作恶影响全租户"与"审计必要性"分析。

---

### 74-10 权限变更审计

**知识点**
- **权限变更必须留痕**（68-09 的权限域）：**谁**在**何时**给**谁**授了**什么权限**、审批人是谁、原因是什么——**这是内部作恶的第一道防线**（51-07 审计）。
- **三类需要审计的变更**：**授权/收权**（用户-角色、角色-权限）、**角色定义变更**（新增/删除权限）、**敏感操作**（平台管理员跨租户访问）——**变更比访问更危险**（提权一次就能持续作恶）。
- **审计的四要素**：**变更前后快照**（diff 可读）、**操作人 + 审批人**（双人原则）、**生效时间**（立即/定时）、**关联工单**（为什么给）——**不可篡改**（append-only + 哈希链 68-09）。
- **异常检测**：**批量授权**（一次给 100 人加权限）、**高危权限授予**（tenant:manage/user:delete）、**非工作时间变更**、**自授**（自己给自己加权限）——**命中即告警**。
- 与 51-10 的关系：资金操作审计与权限操作审计同一套（append-only + 审计链）——**权限是"能做什么"，比资金更底层**。
- 易错点：审计只记"谁改的"不记"改了什么"（无法复盘）；审计可被删（等于没有）；高危变更无审批（单人就能提权）；不检测异常（批量提权无人发现）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuthzAudit {
    record AuditEntry(long seq, long atMs, String operator, String approver,
                      String targetUser, String change, String before, String after,
                      String ticket, boolean sensitive) {
    }

    static final List<AuditEntry> ledger = new ArrayList<>();
    static long seq = 0;

    static void grant(String operator, String approver, String targetUser,
                      String change, String before, String after, String ticket,
                      long atMs) {
        boolean sensitive = change.contains("tenant:manage") || change.contains("user:delete");
        seq++;
        ledger.add(new AuditEntry(seq, atMs, operator, approver, targetUser, change,
                before, after, ticket, sensitive));
        System.out.printf("  #%d %s 给 %s %s：%s -> %s（审批=%s，工单=%s）%s%n",
                seq, operator, targetUser, change, before, after, approver, ticket,
                sensitive ? " [高危]" : "");
    }

    /** 异常检测 */
    static void detectAnomalies(long workStart, long workEnd) {
        System.out.println("【异常检测】");
        Map<String, Integer> perOperator = new LinkedHashMap<>();
        for (AuditEntry e : ledger) {
            perOperator.merge(e.operator(), 1, Integer::sum);
            if (e.sensitive()) {
                System.out.printf("  高危授权：%s 给 %s 授 %s%n",
                        e.operator(), e.targetUser(), e.change());
            }
            if (e.atMs() < workStart || e.atMs() > workEnd) {
                System.out.printf("  非工作时间变更：#%d（%d 点）%n", e.seq(),
                        e.atMs() / 3_600_000);
            }
            if (e.operator().equals(e.targetUser())) {
                System.out.printf("  自授权限：#%d %s 给自己授 %s%n",
                        e.seq(), e.operator(), e.change());
            }
            if (e.approver() == null || e.approver().isBlank()) {
                System.out.printf("  无审批：#%d %s 授 %s%n", e.seq(), e.operator(), e.change());
            }
        }
        perOperator.forEach((op, count) -> {
            if (count >= 3) {
                System.out.printf("  批量授权：%s 共 %d 次 -> 需复核%n", op, count);
            }
        });
        System.out.println();
    }

    static void replay(String targetUser) {
        System.out.println("【权限变更历史（按目标用户）】");
        ledger.stream().filter(e -> e.targetUser().equals(targetUser)).forEach(e ->
                System.out.printf("  #%d %s %s：%s -> %s（操作人 %s，审批 %s）%n",
                        e.seq(), e.change(), e.before(), e.before(), e.after(),
                        e.operator(), e.approver()));
    }

    public static void main(String[] args) {
        long workStart = 9 * 3_600_000L;
        long workEnd = 18 * 3_600_000L;
        System.out.println("【授权记录】");
        grant("admin-a", "lead-b", "u-1", "role", "agent", "agent-lead", "T-1001", 10 * 3_600_000L);
        grant("admin-a", "lead-b", "u-2", "perm", "order:read", "order:read,order:refund",
                "T-1002", 11 * 3_600_000L);
        grant("admin-a", "", "u-3", "perm", "none", "tenant:manage", "T-1003", 23 * 3_600_000L);
        grant("admin-c", "lead-b", "admin-c", "role", "agent", "admin", "T-1004",
                14 * 3_600_000L);
        grant("admin-a", "lead-b", "u-4", "perm", "none", "user:delete", "T-1005",
                15 * 3_600_000L);

        System.out.println();
        detectAnomalies(workStart, workEnd);
        replay("u-1");

        System.out.println();
        System.out.println("【审计要求】");
        Map<String, String> req = new LinkedHashMap<>();
        req.put("四要素", "前后快照 + 操作人/审批人 + 生效时间 + 关联工单");
        req.put("不可篡改", "append-only + 哈希链（68-09）");
        req.put("高危双人", "tenant:manage / user:delete 必须审批");
        req.put("保留期", "≥6 个月（合规通常 3 年）");
        req.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 实现权限变更审计：记录四要素 + 前后快照，用 5 次授权验证可回溯。
2. 实现异常检测四项（高危/非工作时间/自授/无审批）+ 批量授权告警，验证全部命中。
3. 思考题：为什么"权限变更"比"权限访问"更需要审计？从"一次提权可长期作恶"分析。

---

### 74-11 综合案例：统一权限中台

**知识点**
- **把本章收口成一个可运行的权限中台**：**认证**（SSO/OIDC 74-02）-> **组织**（部门树 74-03）-> **RBAC**（74-04）-> **数据范围**（74-05）-> **权限树**（74-06）-> **缓存**（74-07）-> **越权测试**（74-08）-> **租户隔离**（74-09）-> **审计**（74-10）。
- **中台对外的三个能力**：**认证服务**（登录/令牌/SSO）、**授权服务**（`can(user, action, resource)`——统一入口）、**权限管理**（角色/授权/审计）。
- **验收清单**：单点登录可用、角色继承正确、数据范围生效、权限树正确渲染、缓存变更即失效、越权全被拒、跨租户隔离、变更可审计。
- 与全书的关系：权限中台把 36（越权）、55（租户）、68（数据权限）串成一个平台组件——**它是"安全与合规"的工程实现**。
- 易错点：认证授权混在一起（登录即全权）；无统一校验入口（散落 if）；缓存不失效；越权测试缺失；无审计。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuthPlatformCase {
    record User(String id, String tenantId, String deptId, String level, Set<String> roles) {
    }

    record RolePerm(String role, Set<String> perms) {
    }

    static final Map<String, User> users = new LinkedHashMap<>();
    static final Map<String, RolePerm> roles = new LinkedHashMap<>();
    static final Map<String, String> deptOfOrder = new LinkedHashMap<>();

    static void seed() {
        // 订单 -> 部门（数据归属）
        deptOfOrder.put("o-1", "100");
        deptOfOrder.put("o-2", "1001");
        deptOfOrder.put("o-3", "200");

        roles.put("agent", new RolePerm("agent", Set.of("order:read")));
        roles.put("agent-lead", new RolePerm("agent-lead",
                Set.of("order:read", "order:refund")));

        users.put("u-1", new User("u-1", "t-100", "100", "STAFF", Set.of("agent")));
        users.put("u-2", new User("u-2", "t-100", "100", "LEAD", Set.of("agent-lead")));
        users.put("u-9", new User("u-9", "t-200", "200", "LEAD", Set.of("agent-lead")));
    }

    /** 统一授权入口：功能 + 数据范围 + 租户 */
    static String can(String userId, String action, String orderId) {
        User u = users.get(userId);
        if (u == null) {
            return "401 未认证";
        }
        // 1) 功能权限（角色并集）
        Set<String> perms = new LinkedHashSet<>();
        for (String r : u.roles()) {
            RolePerm rp = roles.get(r);
            if (rp != null) {
                perms.addAll(rp.perms());
            }
        }
        if (!perms.contains(action)) {
            return "403 无功能权限";
        }
        // 2) 订单归属租户（简化：订单前缀带租户，真实实现查库）
        String orderTenant = orderId.startsWith("x-") ? "t-200" : u.tenantId();
        if (!orderTenant.equals(u.tenantId())) {
            return "404 跨租户（不泄露存在性）";
        }
        // 3) 数据范围：STAFF 只看本部门，LEAD 看本部门及下级
        String orderDept = deptOfOrder.getOrDefault(orderId, "-");
        boolean inScope = u.level().equals("LEAD")
                ? orderDept.startsWith(u.deptId())
                : orderDept.equals(u.deptId());
        if (!inScope) {
            return "404 超出数据范围";
        }
        return "200 允许";
    }

    static void acceptance() {
        System.out.println();
        System.out.println("【验收清单】");
        List<String> gates = List.of(
                "SSO 登录 + 令牌校验（74-02）",
                "角色继承解析（74-04）",
                "数据范围四种（74-05）",
                "权限树过滤渲染（74-06）",
                "缓存版本失效（74-07）",
                "越权测试矩阵通过（74-08）",
                "跨租户隔离（74-09）",
                "变更可审计（74-10）");
        gates.forEach(g -> System.out.println("  [ ] " + g));
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【用户与角色】");
        users.forEach((id, u) -> System.out.printf("  %-4s tenant=%-6s dept=%-5s level=%-5s roles=%s%n",
                id, u.tenantId(), u.deptId(), u.level(), u.roles()));

        System.out.println();
        System.out.println("【统一授权入口 can(user, action, orderId)】");
        String[][] cases = {
                {"u-1", "order:read", "o-1"},
                {"u-1", "order:read", "o-2"},
                {"u-1", "order:refund", "o-1"},
                {"u-2", "order:refund", "o-2"},
                {"u-2", "order:read", "o-3"},
                {"u-9", "order:read", "o-1"},
                {"u-1", "order:read", "x-o-9"}
        };
        for (String[] c : cases) {
            System.out.printf("  %-4s %-13s %-6s -> %s%n", c[0], c[1], c[2],
                    can(c[0], c[1], c[2]));
        }

        System.out.println();
        System.out.println("【对照】");
        System.out.println("  u-1 看 o-1（本部门）：允许");
        System.out.println("  u-1 看 o-2（下级部门）：拒绝（STAFF 只看本部门）");
        System.out.println("  u-1 退款：403（无功能权限）");
        System.out.println("  u-2 退款 o-2（下级）：允许（LEAD + 下级范围）");
        System.out.println("  u-2 看 o-3（其他部门）：404（数据范围外）");
        System.out.println("  u-9 看 o-1（跨租户）：404（不泄露存在性）");

        acceptance();
        System.out.println();
        System.out.println("  一句话：权限中台 = 认证 + 组织 + RBAC + 数据范围 + 缓存 + 测试 + 审计");
    }
}
```

**课后练习**
1. 端到端实现权限中台（认证/角色/数据范围/统一入口），用 7 个请求验证允许/403/404 三类结果。
2. 加入"权限缓存"（74-07）：验证同一用户多次鉴权只回源一次，变更后立即回源。
3. 思考题：为什么"统一授权入口"是权限中台的核心？从"散落 if 的三个后果（漏判/不一致/难审计）"分析。
