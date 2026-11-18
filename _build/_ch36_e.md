### 36-10 依赖供应链与安全响应头

**知识点**
- **供应链攻击的入口比你想象的多**：直接依赖、传递依赖、Maven/Gradle 插件、基础镜像、CI 里的 action、甚至 IDE 插件。你只写了 10 行依赖，实际拉进来 300 个 jar。
- **传递依赖是盲区**：`spring-core` 会带 `spring-jcl`，`jackson-databind` 会带 `jackson-core`/`jackson-annotations`。漏洞经常出在你「没直接写过」的那个包上（Log4Shell 就是这样扫出来的）。
- **CVSS 高分不等于必须立刻修**：CVSS 衡量的是「漏洞本身的严重程度」，但还要看**可达性**（你的代码有没有走到那条路径）、**是否暴露在公网**、**是否有缓解措施**。EPSS 给的是「未来 30 天被利用的概率」，两者结合才能排优先级。
- **依赖混淆（dependency confusion）**：内部包叫 `com.heima:common-utils`，如果构建配置允许「私有仓库找不到就去中央仓库找」，攻击者抢先在中央仓库发布同名包（版本号更高），构建时就会被拉进来。
- **仿冒包（typosquatting）**：`spring-core` → `springcore`、`log4j-core` → `log4jcore`、`requests` → `request`。名字差一个字符，下载量可能差几个数量级。
- **恶意包常见手法**：`postinstall` 脚本执行命令、构建插件在编译期跑代码、读取环境变量里的 token/密钥、把代码混淆后藏在小文件里。
- **防护是一套组合**：
  - 私有仓库做代理 + `<mirrorOf>*</mirrorOf>` 把中央仓库也走代理，**禁止 fallback 到公网**；
  - 依赖白名单（只允许清单内的 group/artifact）；
  - 锁定版本（`dependencyManagement`、BOM、Gradle lockfile），禁用 `LATEST`/`RELEASE`/`[1.0,2.0)` 这类动态版本；
  - 校验 checksum 与签名；
  - 每次构建跑 SCA（软件成分分析），产出 SBOM（CycloneDX/SPDX）；
  - 最小依赖：能不引就不引，能引小的就不引大的。
- **安全响应头是浏览器侧的最后一道防线**：应用层漏了，响应头还能兜住一部分。
  - `Content-Security-Policy`：限制页面能加载/执行什么，是防 XSS 的最后一道。
  - `Strict-Transport-Security`：告诉浏览器「以后只用 HTTPS 访问我」，防降级与中间人。
  - `X-Content-Type-Options: nosniff`：禁止浏览器猜 MIME，防止把 `.txt` 当脚本执行。
  - `X-Frame-Options` / CSP 的 `frame-ancestors`：防点击劫持。
  - `Referrer-Policy`：控制 Referer 带多少信息，防止 URL 里的 token 泄露给第三方。
  - `Permissions-Policy`：关掉不需要的摄像头/麦克风/定位。
  - `Cross-Origin-Opener-Policy` / `Cross-Origin-Resource-Policy`：隔离跨窗口与跨源资源读取。
- **CSP 的三个阶段**：`'unsafe-inline'`（等于没防）→ `nonce-xxx`（每次响应随机，内联脚本要带这个 nonce）→ `'strict-dynamic'`（信任链传递，配合 nonce 是当前最佳实践）。先用 `Content-Security-Policy-Report-Only` 观察，再切正式。
- **CORS 和 CSP 不是一回事**：CORS 管「别的源能不能**读**我的响应」，CSP 管「我的页面能**加载/执行**什么」。CORS 配错等于把数据敞开。
- **CORS 四个典型错误**：把请求的 `Origin` 原样反射回去（等于允许所有人）、信任 `Origin: null`（sandbox iframe 与 `data:` URL 能发）、`Access-Control-Allow-Origin: *` 配 `Allow-Credentials: true`（浏览器会拒绝，但有人用反射绕过）、允许 `*.example.com`（任一子域被拿下就能打主站）。
- 易错点：只扫直接依赖；只看 CVSS 不看可达性；响应头在应用里配了但被 CDN/网关覆盖；HSTS 没加 `includeSubDomains`；CSP 一直停在 Report-Only 就当已生效；把 `X-Frame-Options: ALLOW-FROM` 当有效（现代浏览器已不支持）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class SupplyChainHeaders {
    record Dep(String group, String artifact, String version, String scope) {
        String ga() {
            return group + ":" + artifact;
        }

        String gav() {
            return ga() + ":" + version;
        }
    }

    record Vuln(String ga, String fixedIn, double cvss, String id, String desc) {
    }

    static final List<Dep> DIRECT = List.of(
            new Dep("com.heima", "order-service", "2.4.1", "compile"),
            new Dep("org.springframework", "spring-core", "5.3.20", "compile"),
            new Dep("com.fasterxml.jackson.core", "jackson-databind", "2.13.2", "compile"),
            new Dep("org.apache.logging.log4j", "log4j-core", "2.14.1", "compile"),
            new Dep("com.google.guava", "guava", "31.1-jre", "compile"),
            new Dep("org.yaml", "snakeyaml", "1.30", "compile"),
            new Dep("org.junit.jupiter", "junit-jupiter", "5.8.2", "test"));

    static final Map<String, List<Dep>> TRANSITIVE = new LinkedHashMap<>();

    static {
        TRANSITIVE.put("org.springframework:spring-core", List.of(
                new Dep("org.springframework", "spring-jcl", "5.3.20", "compile")));
        TRANSITIVE.put("com.fasterxml.jackson.core:jackson-databind", List.of(
                new Dep("com.fasterxml.jackson.core", "jackson-core", "2.13.2", "compile"),
                new Dep("com.fasterxml.jackson.core", "jackson-annotations", "2.13.2", "compile")));
        TRANSITIVE.put("org.apache.logging.log4j:log4j-core", List.of(
                new Dep("org.apache.logging.log4j", "log4j-api", "2.14.1", "compile")));
        TRANSITIVE.put("com.heima:order-service", List.of(
                new Dep("com.heima", "common-utils", "1.0.3", "compile")));
        TRANSITIVE.put("com.google.guava:guava", List.of(
                new Dep("com.google.code.findbugs", "jsr305", "3.0.2", "provided")));
    }

    static final List<Vuln> ADVISORIES = List.of(
            new Vuln("org.apache.logging.log4j:log4j-core", "2.17.1", 10.0,
                    "CVE-2021-44228", "Log4Shell：JNDI lookup 导致远程代码执行"),
            new Vuln("com.fasterxml.jackson.core:jackson-databind", "2.13.4", 7.5,
                    "CVE-2022-42003", "深层嵌套导致栈溢出（拒绝服务）"),
            new Vuln("org.yaml:snakeyaml", "1.31", 7.5,
                    "CVE-2022-1471", "构造器可被调用，存在反序列化风险"),
            new Vuln("com.google.guava:guava", "32.0.0", 5.3,
                    "CVE-2023-2976", "临时目录文件权限过宽"),
            new Vuln("org.apache.commons:commons-text", "1.10.0", 9.8,
                    "CVE-2022-42889", "Text4Shell：字符串插值导致命令执行"));

    static int[] ver(String v) {
        List<Integer> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            } else if (c == '.' && sb.length() > 0) {
                parts.add(Integer.parseInt(sb.toString()));
                sb.setLength(0);
            } else if (c != '.' && c != '-') {
                break;
            }
        }
        parts.add(sb.length() == 0 ? 0 : Integer.parseInt(sb.toString()));
        int[] a = new int[parts.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = parts.get(i);
        }
        return a;
    }

    static int compareVer(String a, String b) {
        int[] x = ver(a);
        int[] y = ver(b);
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int xi = i < x.length ? x[i] : 0;
            int yi = i < y.length ? y[i] : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        return 0;
    }

    static String severity(double cvss) {
        if (cvss >= 9.0) {
            return "严重";
        }
        if (cvss >= 7.0) {
            return "高危";
        }
        if (cvss >= 4.0) {
            return "中危";
        }
        if (cvss > 0) {
            return "低危";
        }
        return "无";
    }

    /** 建立完整依赖闭包：直接依赖 + 逐层传递 */
    static Map<String, Dep> closure() {
        Map<String, Dep> all = new LinkedHashMap<>();
        List<Dep> queue = new ArrayList<>(DIRECT);
        while (!queue.isEmpty()) {
            Dep d = queue.remove(0);
            if (all.containsKey(d.ga())) {
                continue;
            }
            all.put(d.ga(), d);
            List<Dep> children = TRANSITIVE.get(d.ga());
            if (children != null) {
                queue.addAll(children);
            }
        }
        return all;
    }

    static void transitiveBlindSpot() {
        System.out.println("【直接写 7 个，实际拉进来多少个】");
        System.out.println("  直接依赖 " + DIRECT.size() + " 个：");
        for (Dep d : DIRECT) {
            System.out.println("    " + pad(d.gav(), 52) + " " + d.scope());
        }
        Map<String, Dep> all = closure();
        System.out.println();
        System.out.println("  完整闭包 " + all.size() + " 个，多出来的都是传递依赖：");
        for (Dep d : all.values()) {
            boolean direct = DIRECT.stream().anyMatch(x -> x.ga().equals(d.ga()));
            if (!direct) {
                System.out.println("    " + pad(d.gav(), 56) + " <- 间接");
            }
        }
        System.out.println("  漏洞常常出在这些「你没写过」的包上");
        System.out.println();
    }

    static void vulnScan() {
        System.out.println("【SCA 扫描：把闭包和漏洞库比对】");
        Map<String, Dep> all = closure();
        int hits = 0;
        System.out.println("  " + pad("组件", 46) + " | " + pad("当前", 10) + " | "
                + pad("修复版本", 10) + " | " + pad("CVSS", 6) + " | 级别");
        for (Dep d : all.values()) {
            for (Vuln v : ADVISORIES) {
                if (!v.ga().equals(d.ga())) {
                    continue;
                }
                if (compareVer(d.version(), v.fixedIn()) < 0) {
                    hits++;
                    System.out.println("  " + pad(d.ga(), 46) + " | " + pad(d.version(), 10) + " | "
                            + pad(v.fixedIn(), 10) + " | "
                            + pad(String.format("%.1f", v.cvss()), 6) + " | " + severity(v.cvss()));
                }
            }
        }
        System.out.println("  命中 " + hits + " 个漏洞，全部集中在 4 个组件上");
        System.out.println();
        System.out.println("  其中 log4j-core 2.14.1 的 " + ADVISORIES.get(0).id()
                + " 是 CVSS 10.0：只要日志里出现 ${jndi:...} 就可能被 RCE");
        System.out.println();
        System.out.println("  反向核对：漏洞库里还有 " + (ADVISORIES.size() - hits)
                + " 条没报出来，要确认原因是「组件不在闭包里」而不是「漏扫」");
        for (Vuln v : ADVISORIES) {
            if (!all.containsKey(v.ga())) {
                System.out.println("    " + pad(v.ga(), 46) + " 不在闭包内 -> 正确忽略");
            }
        }
        System.out.println("  这一步很重要：不核对的话，分不清「真没有」和「扫漏了」");
        System.out.println();
    }

    static void reachability() {
        System.out.println("【CVSS 高 ≠ 必须今晚加班：还要看可达性】");
        String[][] rows = {
                {"log4j-core 2.14.1", "10.0", "是，用户输入进了日志", "立即修，或先关 lookup"},
                {"snakeyaml 1.30", "7.5", "是，解析用户上传的 YAML", "立即修"},
                {"jackson-databind 2.13.2", "7.5", "否，不反序列化不可信输入", "本次迭代内修"},
                {"guava 31.1-jre", "5.3", "否，未用受影响的临时文件 API", "排期修"},
        };
        System.out.println("  " + pad("组件", 26) + " | " + pad("CVSS", 6) + " | "
                + pad("受影响路径是否可达", 30) + " | 处理");
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 26) + " | " + pad(r[1], 6) + " | "
                    + pad(r[2], 30) + " | " + r[3]);
        }
        System.out.println();
        System.out.println("  排序公式：可达性 × 影响 × 可利用性，而不是单看 CVSS");
        System.out.println();
    }

    /** 模拟依赖解析：私有仓库找不到时会不会 fallback 到公网 */
    static void dependencyConfusion() {
        System.out.println("【依赖混淆：内部包名被抢注】");
        String target = "com.heima:common-utils";
        System.out.println("  你的 pom 里：<dependency> " + target + ":1.0.3 </dependency>");
        System.out.println();

        System.out.println("  情况一：私有仓库有，且禁止 fallback");
        System.out.println("    私有仓库命中 1.0.3 -> 从私有仓库下载，安全");
        System.out.println();
        System.out.println("  情况二：私有仓库没有，配置允许 fallback 到中央仓库");
        System.out.println("    私有仓库未命中 -> 转中央仓库 -> 攻击者已发布 9.9.9");
        System.out.println("    构建成功，日志里只有一行 " + target + ":9.9.9 下载完成");
        System.out.println("    jar 里的 build 插件在编译期执行，读走了 CI 的环境变量");
        System.out.println();
        System.out.println("  防护清单：");
        String[] fixes = {
                "<mirrorOf>*</mirrorOf>：所有仓库请求都走私有代理，代理里显式屏蔽未获批准的 group",
                "私有仓库设为唯一源，禁止 fallback（Nexus/Artifactory 的 routing rule）",
                "内部包用不可能被注册的 group（如 com.heima.internal），或在公共仓库预留命名空间",
                "构建时校验 checksum 与签名，checksum 变了就失败",
                "CI 里对依赖做白名单比对，出现新 GA 就告警",
        };
        int i = 1;
        for (String f : fixes) {
            System.out.printf("    %d. %s%n", i++, f);
        }
        System.out.println();
    }

    static void typosquatting() {
        System.out.println("【仿冒包：差一个字符】");
        String[][] rows = {
                {"org.springframework:spring-core", "org.springframework:springcore", "少一个连字符"},
                {"org.apache.logging.log4j:log4j-core", "org.apache.logging.log4j:log4jcore", "少一个连字符"},
                {"com.google.guava:guava", "com.google.guava:guavaa", "多一个字母"},
                {"com.fasterxml.jackson.core:jackson-databind", "com.fasterxml.jackson:jackson-databind", "少了 .core"},
                {"org.yaml:snakeyaml", "org.yamls:snakeyaml", "group 多一个 s"},
        };
        for (String[] r : rows) {
            System.out.println("  正确  " + r[0]);
            System.out.println("  仿冒  " + r[1] + "   <- " + r[2]);
        }
        System.out.println("  这类包通常下载量为 0、无源码仓库、release 时间集中 -> 都是信号");
        System.out.println();
    }

    static void lockAndVerify() {
        System.out.println("【锁定与可验证】");
        String[][] rows = {
                {"锁定版本", "dependencyManagement / BOM / Gradle lockfile", "避免每次构建拉到不同版本"},
                {"禁动态版本", "去掉 LATEST / RELEASE / [1.0,2.0)", "动态版本 = 把选择权交给仓库"},
                {"校验和", "sha256 比对，不匹配即失败", "防篡改与中间人"},
                {"签名", "GPG 签名验证", "防仓库被投毒"},
                {"SBOM", "CycloneDX / SPDX，随版本归档", "出事后 10 分钟内查清影响面"},
                {"SCA 卡点", "CI 里严重/高危直接 fail", "不依赖人的自觉"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 10) + " | " + pad(r[1], 40) + " | " + r[2]);
        }
        System.out.println();
        System.out.println("  最小依赖原则：能不加就不加，能加小的就不加大的");
        System.out.println("  一个 200 KB 的「工具库」可能带进来 15 个传递依赖");
        System.out.println();
    }

    static void securityHeaders() {
        System.out.println("【安全响应头：一张对照表】");
        String[][] rows = {
                {"Content-Security-Policy",
                        "default-src 'self'; script-src 'self' 'nonce-r4nd0m'; object-src 'none'; "
                                + "base-uri 'none'; frame-ancestors 'none'",
                        "防 XSS、防点击劫持、防 base 标签劫持"},
                {"Strict-Transport-Security",
                        "max-age=31536000; includeSubDomains; preload",
                        "强制 HTTPS，防降级"},
                {"X-Content-Type-Options", "nosniff", "禁止 MIME 嗅探"},
                {"X-Frame-Options", "DENY", "老浏览器兜底（现代用 frame-ancestors）"},
                {"Referrer-Policy", "strict-origin-when-cross-origin", "跨站只带域名，不带路径"},
                {"Permissions-Policy", "camera=(), microphone=(), geolocation=()", "关掉不需要的能力"},
                {"Cross-Origin-Opener-Policy", "same-origin", "隔离跨窗口引用"},
                {"Cross-Origin-Resource-Policy", "same-origin", "禁止别的源直接加载我的资源"},
        };
        for (String[] r : rows) {
            System.out.println("  " + r[0]);
            System.out.println("    " + r[1]);
            System.out.println("    -> " + r[2]);
        }
        System.out.println();
    }

    static void cspStages() {
        System.out.println("【CSP 的三代写法】");
        String[][] rows = {
                {"第一代", "script-src 'self' 'unsafe-inline'", "等于没防：任意内联脚本都能跑"},
                {"第二代", "script-src 'self' 'nonce-abc123'", "内联脚本必须带 nonce，XSS 注入的脚本没有"},
                {"第三代", "script-src 'nonce-abc123' 'strict-dynamic'", "信任链传递，兼容动态加载的框架"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 6) + " | " + pad(r[1], 42) + " | " + r[2]);
        }
        System.out.println();
        System.out.println("  上线节奏：先 Content-Security-Policy-Report-Only 观察违规上报");
        System.out.println("            确认没有误伤后，再换成正式头");
        System.out.println("  千万别：加了 Report-Only 就当已生效 —— 它不拦任何东西");
        System.out.println();
    }

    static void corsMistakes() {
        System.out.println("【CORS 配错 = 数据敞开】");
        String[][] rows = {
                {"反射 Origin", "Access-Control-Allow-Origin: <请求里的 Origin>", "任意站点都能读响应"},
                {"信任 null", "Origin: null 也放行", "sandbox iframe / data: URL 可发请求"},
                {"* + 凭证", "Allow-Origin: * 且 Allow-Credentials: true", "浏览器会拒绝，但有人改反射绕过"},
                {"通配子域", "允许 *.example.com", "任一子域被拿下即可打主站"},
                {"预检放行所有头", "Allow-Headers: *", "等于允许任意自定义头"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 14) + " | " + pad(r[1], 44) + " | " + r[2]);
        }
        System.out.println();
        System.out.println("  正确做法：显式白名单（完整 origin，含协议和端口），逐条比对");
        System.out.println("  CORS 管「能不能读」，CSP 管「能不能加载执行」，两者互补不能互替");
        System.out.println();
    }

    /** 审计一组响应头，报告缺失项 */
    static void headerAudit() {
        System.out.println("【响应头审计：拿真实响应体比对】");
        Map<String, String> actual = new LinkedHashMap<>();
        actual.put("Content-Type", "text/html; charset=utf-8");
        actual.put("X-Frame-Options", "DENY");
        actual.put("Referrer-Policy", "strict-origin-when-cross-origin");
        actual.put("Server", "nginx/1.18.0");

        System.out.println("  当前响应头：");
        actual.forEach((k, v) -> System.out.println("    " + k + ": " + v));
        System.out.println();

        String[] required = {
                "Content-Security-Policy",
                "Strict-Transport-Security",
                "X-Content-Type-Options",
                "Referrer-Policy",
                "Permissions-Policy",
                "Cross-Origin-Opener-Policy",
        };
        System.out.println("  审计结果：");
        int missing = 0;
        for (String h : required) {
            boolean has = actual.containsKey(h);
            if (!has) {
                missing++;
            }
            System.out.println("    " + pad(h, 32) + " -> " + (has ? "存在" : "缺失"));
        }
        System.out.println("    缺失 " + missing + " 项");
        System.out.println();
        System.out.println("  另外发现：Server: nginx/1.18.0 泄露了中间件版本，应去掉或改写");
        System.out.println();
    }

    static String pad(String s, int width) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) > 0x2E80 ? 2 : 1;
        }
        return s + " ".repeat(Math.max(width - w, 0));
    }

    public static void main(String[] args) {
        transitiveBlindSpot();
        vulnScan();
        reachability();
        dependencyConfusion();
        typosquatting();
        lockAndVerify();
        securityHeaders();
        cspStages();
        corsMistakes();
        headerAudit();
    }
}
```

**课后练习**
1. 给 `closure()` 加上「版本冲突检测」：同一个 GA 在闭包里出现两个不同版本时，打印冲突并说明 Maven 的「最近优先」会选哪个。
2. 实现一个简化的 SCA 报告：按 CVSS 分档统计，输出「严重 N 个、高危 M 个」以及每个组件的升级建议版本。
3. 写一个「响应头生成器」：根据配置（是否启用 CSP nonce、是否 preload HSTS）拼出完整的头集合，并列出每条头的理由。
4. 思考题：为什么 `Access-Control-Allow-Origin: *` 配 `Access-Control-Allow-Credentials: true` 会被浏览器拒绝？如果服务端改成反射 Origin，会打开什么口子？

---

### 36-11 综合案例：一次接口安全评审

**知识点**
- **安全评审不是「跑个扫描器」，而是按攻击面逐项过**。工具只能发现已知模式，业务逻辑漏洞（越权、重复提交、金额篡改）只能靠人看。
- **九个必查维度**：认证与会话、授权与数据归属、输入校验、输出编码、数据保护（存储/传输/脱敏）、配置与响应头、依赖与供应链、日志与监控、业务逻辑。每个维度都要有「查什么」和「怎么验」。前八个是技术面，最后一个是业务面——「重复退款」「金额篡改」这类问题都出在这里，扫描器一个都发现不了。
- **一条合格的发现要写清五件事**：位置（哪个接口哪一行）、证据（怎么复现）、影响（能造成什么后果）、修复（具体怎么改）、验证（改完怎么确认）。只写「存在越权风险」等于没写。
- **分级与放行门禁**：严重（可导致数据泄露/资金损失/远程执行）与高危必须在**上线前**修完；中危可以带条件上线（有缓解措施 + 排期）；低危进迭代。门禁要写进 CI，不能靠开会决定。
- **修复优先级不等于严重级别**：还要乘上「利用难度」和「暴露面」。一个公网可达、无需登录的严重漏洞，优先级远高于需要内网权限的高危漏洞。
- **每个发现都要映射一个回归测试**：否则修完过两个月重构又回来了。测试要写成「用 A 的 token 请求 B 的数据，期望 404」这种可执行的断言。
- **评审要覆盖「异常路径」**：正常流程大家都测，漏洞往往在「参数为空」「id 不存在」「重复提交」「并发」里。
- **复盘比清单更重要**：这些问题是「需求阶段没写安全要求」「评审只看功能」「测试只测正常路径」还是「赶上线跳过了卡点」？找到流程原因，才能避免下一批同样的漏洞。
- **安全左移**：在设计阶段做威胁建模（36-01），编码阶段用安全基线（36-02~36-09），提交阶段用 SCA 与 SAST 卡点（36-10），上线前做这一课的评审。
- 易错点：评审只做一次，之后新接口不再过；只评审「新增代码」不看被改动的老接口；发现写了但没有责任人和截止时间；把「已加 WAF 规则」当作代码修复。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class SecurityReviewCase {
    enum Severity {
        CRITICAL("严重", 4),
        HIGH("高危", 3),
        MEDIUM("中危", 2),
        LOW("低危", 1);

        final String cn;
        final int weight;

        Severity(String cn, int weight) {
            this.cn = cn;
            this.weight = weight;
        }
    }

    /** 评审对象：POST /api/order/{id}/refund */
    record Finding(String area, Severity sev, String issue, String fix, String test) {
    }

    static final List<Finding> FINDINGS = List.of(
            new Finding("授权", Severity.CRITICAL,
                    "退款接口只按 orderId 查订单，未校验归属，任意用户可退别人的单",
                    "查询条件加 owner_id = 当前用户；管理员跨用户时也限制在本租户",
                    "用 A 的 token 退 B 的订单，期望 404"),
            new Finding("依赖与供应链", Severity.CRITICAL,
                    "log4j-core 2.14.1，存在 Log4Shell（CVSS 10.0），且用户输入进了日志",
                    "升级到 2.17.1；升级前先关闭 JNDI lookup 作为临时缓解",
                    "构造 ${jndi:ldap://...} 作为订单备注，确认不再发起外连"),
            new Finding("业务逻辑", Severity.HIGH,
                    "退款无幂等控制，重复提交会退多次",
                    "引入幂等键 + 数据库唯一约束（order_id, idempotency_key）",
                    "同一请求并发提交 10 次，确认只产生 1 条退款记录"),
            new Finding("认证与会话", Severity.HIGH,
                    "refresh token 永不过期、不轮换，泄露后可长期换取 access token",
                    "7 天过期 + 一次性轮换；检测旧 token 复用则注销整条会话",
                    "用已轮换的旧 refresh 换 token，期望 401 且会话失效"),
            new Finding("数据保护", Severity.HIGH,
                    "退款日志打印完整银行卡号与持卡人姓名",
                    "日志层脱敏（保留后四位），审计日志同样脱敏",
                    "grep 日志文件，确认无完整卡号"),
            new Finding("输入校验", Severity.MEDIUM,
                    "退款金额未与订单金额比对，可提交超过订单金额的值",
                    "服务端按订单重算可退金额，客户端传值仅作参考",
                    "提交金额 99999 退款 29 元的订单，期望 400"),
            new Finding("配置与响应头", Severity.MEDIUM,
                    "缺少 CSP 与 HSTS，响应头暴露 Server: nginx/1.18.0",
                    "补齐安全响应头；去掉版本信息；CSP 先 Report-Only 观察",
                    "用 curl -I 检查头是否齐全"),
            new Finding("日志与监控", Severity.MEDIUM,
                    "退款操作无审计日志，事后无法追溯谁退的",
                    "记录 谁/何时/哪笔单/金额/来源IP/请求ID，独立存储只追加",
                    "执行一次退款，确认审计表有对应记录"),
            new Finding("业务逻辑", Severity.LOW,
                    "退款接口无速率限制，可被脚本高频调用",
                    "按用户 + 订单维度限流（如 5 次/分钟）",
                    "连续调用 20 次，期望后 15 次返回 429"));

    static final String[] AREAS = {
            "认证与会话", "授权", "输入校验", "输出编码",
            "数据保护", "配置与响应头", "依赖与供应链", "日志与监控", "业务逻辑",
    };

    static void walkthrough() {
        System.out.println("【评审对象】POST /api/order/{id}/refund  退款接口");
        System.out.println("【九个维度逐项过】");
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("认证与会话", "token 有效期、refresh 轮换、注销是否即时生效");
        checks.put("授权", "数据归属、管理员边界、批量与导出入口");
        checks.put("输入校验", "金额/数量边界、类型、长度、白名单");
        checks.put("输出编码", "错误信息是否回显、JSON 是否直接拼 HTML");
        checks.put("数据保护", "卡号/手机号是否落日志、传输是否强制 HTTPS");
        checks.put("配置与响应头", "CSP/HSTS/nosniff、版本信息、CORS 白名单");
        checks.put("依赖与供应链", "SCA 扫描、传递依赖、动态版本");
        checks.put("日志与监控", "审计日志、异常告警、是否有敏感字段");
        checks.put("业务逻辑", "幂等、限额、状态机、并发");
        int i = 1;
        for (Map.Entry<String, String> e : checks.entrySet()) {
            System.out.printf("  %d. %s：%s%n", i++, pad(e.getKey(), 14), e.getValue());
        }
        System.out.println();
    }

    static void findingTable() {
        System.out.println("【发现清单（按严重级别排序）】");
        List<Finding> sorted = new ArrayList<>(FINDINGS);
        sorted.sort(Comparator.comparingInt((Finding f) -> f.sev().weight).reversed());
        System.out.println("  " + pad("级别", 6) + " | " + pad("维度", 16) + " | 问题");
        for (Finding f : sorted) {
            System.out.println("  " + pad(f.sev().cn, 6) + " | " + pad(f.area(), 16) + " | " + f.issue());
        }
        System.out.println();
        System.out.println("  共 " + FINDINGS.size() + " 条，全部可复现、可验证");
        System.out.println();
    }

    static void detail() {
        System.out.println("【单条发现要写清五件事】");
        Finding f = FINDINGS.get(0);
        System.out.println("  位置：" + f.area() + " -> 退款接口 OrderRefundController.refund()");
        System.out.println("  问题：" + f.issue());
        System.out.println("  影响：任意登录用户可对他人的订单发起退款，直接造成资金损失");
        System.out.println("  修复：" + f.fix());
        System.out.println("  验证：" + f.test());
        System.out.println();
        System.out.println("  只写「存在越权风险」等于没写 —— 开发不知道怎么改，测试不知道怎么验");
        System.out.println();
    }

    static void gate() {
        System.out.println("【放行门禁：能不能上线】");
        long critical = FINDINGS.stream().filter(f -> f.sev() == Severity.CRITICAL).count();
        long high = FINDINGS.stream().filter(f -> f.sev() == Severity.HIGH).count();
        long medium = FINDINGS.stream().filter(f -> f.sev() == Severity.MEDIUM).count();
        long low = FINDINGS.stream().filter(f -> f.sev() == Severity.LOW).count();
        System.out.printf("  严重 %d 个，高危 %d 个，中危 %d 个，低危 %d 个%n",
                critical, high, medium, low);
        System.out.println();
        System.out.println("  规则：");
        System.out.println("    严重 > 0        -> 禁止上线，修完重审");
        System.out.println("    高危 > 0        -> 禁止上线，或在有等效缓解时由安全负责人签字特批");
        System.out.println("    中危 > 0        -> 可带条件上线：必须有缓解措施 + 明确排期");
        System.out.println("    仅低危          -> 可上线，进迭代");
        System.out.println();
        String decision = critical > 0 ? "禁止上线" : (high > 0 ? "禁止上线" : "可上线");
        System.out.println("  本次结论：" + decision + "（严重 " + critical + " 个必须清零）");
        System.out.println();
    }

    static void fixPlan() {
        System.out.println("【修复计划：按批次排】");
        Map<String, List<Finding>> buckets = new LinkedHashMap<>();
        buckets.put("上线前必须完成", new ArrayList<>());
        buckets.put("上线后一周内", new ArrayList<>());
        buckets.put("本迭代内", new ArrayList<>());
        for (Finding f : FINDINGS) {
            switch (f.sev()) {
                case CRITICAL, HIGH -> buckets.get("上线前必须完成").add(f);
                case MEDIUM -> buckets.get("上线后一周内").add(f);
                case LOW -> buckets.get("本迭代内").add(f);
            }
        }
        int i = 1;
        for (Map.Entry<String, List<Finding>> e : buckets.entrySet()) {
            System.out.printf("  %s（%d 条）%n", e.getKey(), e.getValue().size());
            for (Finding f : e.getValue()) {
                System.out.printf("    %d) [%s] %s%n", i++, f.area(), f.issue());
            }
        }
        System.out.println();
        System.out.println("  每条都要有责任人和截止时间，否则等于没排");
        System.out.println();
    }

    static void regression() {
        System.out.println("【回归测试：一条发现一个用例】");
        int i = 1;
        for (Finding f : FINDINGS) {
            System.out.printf("  %d. [%s] %s%n", i++, f.sev().cn, f.test());
        }
        System.out.println();
        System.out.println("  这些用例要进 CI，不能只放在评审报告里");
        System.out.println();
    }

    static void coverage() {
        System.out.println("【维度覆盖度：有没有漏审】");
        for (String area : AREAS) {
            long n = FINDINGS.stream().filter(f -> f.area().equals(area)).count();
            String status;
            if (n == 0) {
                status = "本次无发现";
            } else if (n == 1) {
                status = "1 条";
            } else {
                status = n + " 条";
            }
            System.out.println("  " + pad(area, 18) + " -> " + status);
        }
        System.out.println();
        System.out.println("  「无发现」不等于「没问题」，只等于「这次没看出来」");
        System.out.println("  输出编码本次无发现，是因为该接口只返回 JSON，但前端的渲染页面仍需单独审");
        System.out.println();
    }

    static void whyMissed() {
        System.out.println("【复盘：这些为什么会溜进来】");
        String[][] rows = {
                {"需求阶段", "需求文档没写安全要求", "加「安全验收条件」到 DoD"},
                {"设计阶段", "没做威胁建模，没想到越权", "对涉及资金/个人数据的接口强制建模"},
                {"编码阶段", "照抄了老代码的错误写法", "提供安全基线代码模板与工具类"},
                {"测试阶段", "只测正常路径，没测双账号", "把越权/幂等/并发写进用例模板"},
                {"上线阶段", "赶时间跳过了安全卡点", "CI 卡点不可绕过，特批要留痕"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 10) + " | " + pad(r[1], 26) + " | " + r[2]);
        }
        System.out.println();
        System.out.println("  修 9 个漏洞只是治标；改掉上面 5 条流程才是治本");
        System.out.println();
    }

    static void finalReport() {
        System.out.println("【一页纸结论】");
        long blocking = FINDINGS.stream()
                .filter(f -> f.sev() == Severity.CRITICAL || f.sev() == Severity.HIGH)
                .count();
        System.out.println("  评审范围：退款接口 POST /api/order/{id}/refund");
        System.out.println("  评审维度：9 个，覆盖认证/授权/输入/输出/数据/配置/依赖/日志/业务");
        System.out.println("  发现问题：" + FINDINGS.size() + " 条，其中阻塞项 " + blocking + " 条");
        System.out.println("  结论：禁止上线，阻塞项清零后重审");
        System.out.println();
        System.out.println("  建议的次序：");
        System.out.println("    1) 升级 log4j（不改代码，最快见效）");
        System.out.println("    2) 退款接口加数据级授权（改动小，风险最高）");
        System.out.println("    3) 加幂等与金额校验（涉及表结构，需要联调）");
        System.out.println("    4) 日志脱敏 + 审计日志（可并行）");
        System.out.println("    5) 补响应头与限流（最后做，影响面小）");
        System.out.println();
        System.out.println("  安全评审的价值不在于「找出几个漏洞」，");
        System.out.println("  而在于让下一个接口在写的时候就避开这些坑。");
        System.out.println();
    }

    static String pad(String s, int width) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) > 0x2E80 ? 2 : 1;
        }
        return s + " ".repeat(Math.max(width - w, 0));
    }

    public static void main(String[] args) {
        walkthrough();
        findingTable();
        detail();
        gate();
        fixPlan();
        regression();
        coverage();
        whyMissed();
        finalReport();
    }
}
```

**课后练习**
1. 给 `Finding` 增加「利用难度」与「暴露面」两个字段，重写排序逻辑，让「公网可达 + 易利用」的项自动排到最前。
2. 把 `gate()` 改成一个可配置的门禁：阈值（严重/高危数量）从参数传入，输出是否放行及理由。
3. 为「重复提交」这条发现设计完整的测试方案：并发数、断言、如何构造幂等键、如何验证唯一约束生效。
4. 思考题：为什么「已加 WAF 规则」不能算作代码层漏洞的修复？举一个 WAF 被绕过的例子。

---
