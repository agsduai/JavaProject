### 37-07 Docker Compose 与多容器编排

**知识点**
- **Compose 解决的问题是"本地/单机环境的一致性"**：一个 `compose.yaml` 声明全部服务、网络、卷、环境变量，`docker compose up -d` 一条命令把整个应用栈拉起来，`down` 整体拆除。它把"README 里 15 条启动命令"变成一份可版本化的文件。
- **服务 = 同一镜像的运行模板**：`services:` 下每一项可指定 `image` 或 `build:`（有 build 时 compose 会替你执行 docker build），`ports`、`volumes`、`environment`、`depends_on` 都写在服务条目里。
- **depends_on 只管启动顺序，不管就绪**：`depends_on: [db]` 保证 db 容器**先启动**，但数据库"能接受连接"要靠 `healthcheck` + `condition: service_healthy`。应用比数据库先就绪是"本地第一次 up 必失败"的经典原因。
- **项目网络与名字解析**：compose 默认创建 `<project>_default` 自定义网络，服务名即 DNS 名，应用配置里写 `jdbc:mysql://db:3306` 即可。**配置里的主机名必须等于服务名**，这是 compose 化改造最常见的断点。
- **改动即增量**：修改单个服务后 `docker compose up -d app` 只重建该服务，其他服务不动；`--build` 强制重新构建镜像。`down -v` 会连卷一起删（危险命令，本地才用）。
- **环境变量注入**：`${VAR}` 从 `.env` 文件或 shell 读入，区分开发/测试配置；敏感信息不进 yaml，用 secrets 或外部配置中心。
- **Compose 不是生产编排**：它没有自愈、没有滚动更新、没有跨机调度。单机小系统可以用，微服务集群要交给 K8s —— 但 compose 的服务定义迁移成本很低。
- 易错点：把 `ports` 和 `expose` 混用错（ports 才发布到宿主机）；depends_on 后应用还是连不上（缺 healthcheck 条件）；`.env` 提交进仓库带出真实密码；用 `restart: always` 掩盖应用启动失败（容器在 crash loop，日志里全是堆栈）。

**示例代码**
```java
import java.util.List;

public class ComposeDemo {
    record Service(String name, String image, String ports, String dependsOn,
                   String healthcheck, String env) {
    }

    static final String COMPOSE_YAML = """
            services:
              db:
                image: mysql:8.4
                environment: [MYSQL_ROOT_PASSWORD=devpass]
                volumes: [db-data:/var/lib/mysql]
                healthcheck:
                  test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
                  interval: 5s
                  retries: 10
              redis:
                image: redis:7-alpine
              app:
                build: .
                ports: ["8080:8080"]
                depends_on:
                  db: {condition: service_healthy}
                  redis: {condition: service_started}
            volumes:
              db-data:""";

    static void parseYaml() {
        System.out.println("【compose.yaml 三个服务的关系】");
        List<Service> services = List.of(
                new Service("db", "mysql:8.4", "不发布（仅网络内）", "-",
                        "mysqladmin ping，10 次×5s", "root 密码走 .env"),
                new Service("redis", "redis:7-alpine", "不发布", "-", "不需要", "-"),
                new Service("app", "build .（本地 Dockerfile）", "8080:8080", "db(healthy) + redis(started)",
                        "自身 /actuator/health", "SPRING_PROFILES_ACTIVE=dev"));
        for (Service s : services) {
            System.out.printf("  %-6s %-24s 端口=%-14s 依赖=%s%n",
                    s.name(), s.image(), s.ports(), s.dependsOn());
        }
        System.out.println("  网络：<project>_default，服务名互访：jdbc:mysql://db:3306");
        System.out.println();
    }

    static void startupOrder() {
        System.out.println("【up 的启动时序：healthcheck 才是真·就绪信号】");
        String[] timeline = {
                "t+0s   redis     started（无依赖，立即起）",
                "t+0s   db        started（进程启动，但 MySQL 还在初始化数据目录）",
                "t+6s   db        healthcheck 第 2 次失败（starting）",
                "t+25s  db        healthy（初始化完成，能接受连接）",
                "t+25s  app       才开始启动（condition: service_healthy 满足）",
                "t+27s  app       Spring Boot 起来，连 db 一次成功"};
        for (String t : timeline) {
            System.out.println("  " + t);
        }
        System.out.println("  反例：只写 depends_on: [db] -> app 在 t+1s 启动 -> 连接拒绝 -> crash loop");
        System.out.println();
    }

    static void incrementalOps() {
        System.out.println("【日常操作与影响范围】");
        List<String> ops = List.of(
                "docker compose up -d            全栈拉起（已有容器不重建）",
                "docker compose up -d --build app 只重建 app（重新构建+替换），db/redis 不动",
                "docker compose logs -f app      跟踪单个服务日志",
                "docker compose restart app      重启（配置改动不生效，要 up）",
                "docker compose down             停止并删除容器与网络，保留卷",
                "docker compose down -v          连卷一起删（危险：数据库全没，仅限本地）");
        for (String o : ops) {
            System.out.println("  " + o);
        }
        System.out.println();
    }

    static void composeToK8s() {
        System.out.println("【compose 与 K8s 的概念映射】");
        String[][] map = {
                {"services 条目", "Deployment + Service"},
                {"healthcheck", "liveness/readiness 探针"},
                {"volumes 具名卷", "PersistentVolumeClaim"},
                {"depends_on", "Init 容器/启动顺序编排（语义不同）"},
                {"restart: always", "副本自愈（控制器循环）"},
        };
        for (String[] m : map) {
            System.out.printf("  %-18s -> %s%n", m[0], m[1]);
        }
        System.out.println("  结论：compose 管单机环境一致性，K8s 管集群期望状态");
    }

    public static void main(String[] args) {
        System.out.println("---- compose.yaml 原文 ----");
        System.out.println(COMPOSE_YAML);
        System.out.println();
        parseYaml();
        startupOrder();
        incrementalOps();
        composeToK8s();
    }
}
```

**课后练习**
1. 为「Spring Boot 应用 + MySQL + Redis」写出完整 compose.yaml，要求 db 带健康检查、app 依赖 healthy，本地真实跑通并截图启动时序。
2. 故意去掉 `condition: service_healthy`，观察 app 的启动失败与重启行为，然后用 `docker compose logs` 找到连接拒绝的证据。
3. 把数据库密码移入 `.env` 并确认 `.env` 不进 git；解释为什么密码写死在 compose.yaml 与写死在 Dockerfile 同样危险。
4. 思考题：`docker compose down` 与 `down -v` 在"第二天来继续开发"场景下分别意味着什么？写出你的本地环境保护策略。

---

### 37-08 Java 应用的容器化实践

**知识点**
- **JVM 必须按容器限额来配置内存**：现代 JDK（8u191+/10+）默认 `UseContainerSupport`，能读 cgroup 限额。但**显式参数仍然优先**：`-Xmx` 写死了就不再感知容器。推荐用百分比方式：`-XX:MaxRAMPercentage=75.0`，给元空间、线程栈、直接内存留出余量。限额 1G 的容器，堆最多 768M，再叠 Netty 堆外缓冲与 Metaspace，实际也到顶了。
- **CPU 配额同样影响 JVM**：容器限 0.5 核时，JVM 默认按配额算 `ActiveProcessorCount`，进而决定 GC 线程数与 ForkJoinPool 大小；老 JDK 会按宿主机 16 核建 15 条 GC 线程，在小容器里互相踩踏。
- **exec 形式启动是优雅停机的前提**：`ENTRYPOINT ["java", "-jar", "app.jar"]` 让 java 直接成为 1 号进程，SIGTERM 直达 JVM；`ENTRYPOINT java -jar app.jar`（shell 形式）实际是 `sh -c ...`，SIGTERM 给了 sh，java 收不到，`docker stop` 必等满 10 秒。
- **分层 jar 让依赖层稳定**：Spring Boot 的 layered jar 把依赖、loader、应用类拆成多层，依赖不变时 `COPY` 依赖层缓存命中，只有几 MB 的应用层重建 —— 改一行代码的镜像构建从分钟级降到秒级。
- **基础镜像的选择是运行时契约**：`temurin:21-jre`（完整，几百 MB）、`21-jre-alpine`（小但 musl libc，字体/时区/JNI 库可能踩坑）、`distroless/java`（只有运行时，最安全最小，无 shell 可调试性差）。选型标准是"需要什么能力"而不是"越小越好"。
- **镜像里常被遗忘的系统级配置**：时区（`TZ=Asia/Shanghai`）、字符集（日志乱码多因基础镜像无 locale）、DNS 缓存（JVM 默认缓存 30s，K8s 环境常调成 `networkaddress.cache.ttl=10`）、容器内 `ulimit`。
- **可观测性从镜像开始**：把 `/actuator/health` 作为 HEALTHCHECK；日志一律 stdout/stderr（容器日志驱动收集），不要写文件进容器；开启 `-XX:+ExitOnOutOfMemoryError` 让 OOM 时快速失败，交给编排层重启。
- 易错点：`-Xmx` 按宿主机内存写死（进小容器必被 137 杀）；shell 形式 ENTRYPOINT（优雅停机失效）；镜像里写日志文件路径（磁盘涨满）；alpine 上跑带字体渲染/JNI 的应用（缺 glibc 依赖崩溃）。

**示例代码**
```java
import java.util.List;

public class JavaContainerize {
    record Cgroup(long memoryLimitMb, double cpuQuota) {
    }

    static void memoryBudget() {
        System.out.println("【容器限额下的 JVM 内存预算】");
        Cgroup c = new Cgroup(1024, 1.0);
        long heap = Math.round(c.memoryLimitMb() * 0.75);
        long metaspace = 96, direct = 64, stackAndOther = 64;
        long sum = heap + metaspace + direct + stackAndOther;
        System.out.printf("  容器限额 %dMB / %.1f 核%n", c.memoryLimitMb(), c.cpuQuota());
        System.out.printf("  堆          MaxRAMPercentage=75%% -> %dMB%n", heap);
        System.out.printf("  Metaspace   约 %dMB%n", metaspace);
        System.out.printf("  直接内存/Netty 约 %dMB%n", direct);
        System.out.printf("  线程栈与其他 约 %dMB%n", stackAndOther);
        System.out.printf("  合计 %dMB <= 限额 %dMB，余量 %dMB%n", sum, c.memoryLimitMb(),
                c.memoryLimitMb() - sum);
        System.out.println("  反例：-Xmx4g 写死 + 限额 1G -> 分配即 OOMKilled(137)");
        System.out.println();
    }

    static void cpuAwareness() {
        System.out.println("【CPU 配额对 JVM 内部线程数的影响】");
        long hostCores = Runtime.getRuntime().availableProcessors();
        double quota = 0.5;
        int containerAware = Math.max(1, (int) Math.round(quota));
        long legacy = hostCores;
        System.out.printf("  宿主机核数=%d，容器配额=%.1f 核%n", hostCores, quota);
        System.out.printf("  新 JDK：ActiveProcessorCount=%d -> GC 线程≈%d，FJP 并行度=%d%n",
                containerAware, containerAware, containerAware);
        System.out.printf("  老 JDK：按宿主机算 %d 核 -> GC 线程≈%d，小容器里线程互相争抢%n",
                legacy, legacy);
        System.out.println("  显式指定：-XX:ActiveProcessorCount=2 可人工定档");
        System.out.println();
    }

    static void entrypointTrap() {
        System.out.println("【ENTRYPOINT 两种写法与信号送达】");
        System.out.println("  exec 形式：ENTRYPOINT [\"java\",\"-jar\",\"app.jar\"]");
        System.out.println("    进程树：java(PID 1) -> SIGTERM 直达 JVM -> 优雅停机 ~1s");
        System.out.println("  shell 形式：ENTRYPOINT java -jar app.jar");
        System.out.println("    进程树：sh(PID 1) -> java(PID 7) -> SIGTERM 给了 sh");
        System.out.println("    java 不退出 -> 10s 宽限期满 -> SIGKILL，在途请求全丢");
        System.out.println("  兜底方案：sh -c 'exec java ...' 用 exec 替换 shell 自身");
        System.out.println();
    }

    static void layeredJar() {
        System.out.println("【分层 jar：改一行代码只重建哪层？】");
        record JarLayer(String name, int sizeMb, String rule) {
        }
        List<JarLayer> layers = List.of(
                new JarLayer("dependencies", 45, "第三方依赖，月级变化"),
                new JarLayer("spring-boot-loader", 1, "启动器，几乎不变"),
                new JarLayer("snapshot-dependencies", 3, "SNAPSHOT 依赖"),
                new JarLayer("application", 2, "业务类，日级变化"));
        for (JarLayer l : layers) {
            System.out.printf("  %-22s %3dMB  %s%n", l.name(), l.sizeMb(), l.rule());
        }
        System.out.println("  改业务代码：前三层 CACHED，只重传 application 层 ~2MB");
        System.out.println("  Dockerfile 顺序：COPY 依赖层 -> COPY 应用层");
        System.out.println();
    }

    static void imageChecklist() {
        System.out.println("【Java 镜像上线检查单】");
        List<String> items = List.of(
                "TZ=Asia/Shanghai + 字体/locale，验证日志时间戳与中文",
                "ENTRYPOINT 用 exec 形式，SIGTERM 实测 1s 内退出",
                "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError",
                "HEALTHCHECK 用 /actuator/health 并检查 HTTP 200",
                "日志到 stdout，容器内不写业务日志文件",
                "基础镜像选型留档：jre / alpine / distroless 的取舍理由");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        memoryBudget();
        cpuAwareness();
        entrypointTrap();
        layeredJar();
        imageChecklist();
    }
}
```

**课后练习**
1. 实测内存预算：起 `docker run -m 1g` 的 JDK 21 容器，分别用 `-Xmx900m` 与 `MaxRAMPercentage=75` 跑压测，用 `docker stats` 对比峰值内存。
2. 复现 shell 形式 ENTRYPOINT 的停机问题：两种写法各做一次 `docker stop`，用 `docker events` 或时间戳记录耗时差异，解释原因。
3. 为 Spring Boot 项目开启分层 jar，构建两次（第二次只改业务类），用 `docker build` 日志证明依赖层 CACHED，记录两次构建耗时。
4. 思考题：为什么 OOM 时应选择 `ExitOnOutOfMemoryError` 快速失败而不是"带病坚持"？结合 37-04 的重启策略说明完整自愈链路。

---

### 37-09 镜像瘦身与镜像安全

**知识点**
- **瘦身的三板斧**：小基础镜像（alpine/distroless）、多阶段构建（编译器与构建产物不进运行镜像）、层内容最小化（同层内下载+清理、.dockerignore 减少上下文与意外文件）。
- **多阶段构建是 Java 镜像的标配**：第一阶段 maven 镜像（~500MB）编译出 jar，第二阶段只 COPY jar 进 JRE 镜像。源码、`.m2` 仓库、maven 本体都不进最终镜像，体积与攻击面同时下降。
- **镜像安全清单**：非 root 用户（`USER 10001`）、只读根文件系统（`--read-only`，可写目录单独挂卷）、最小能力集（`--cap-drop=ALL`）、禁用特权（`--privileged` 绝不上生产）、固定 tag/digest、定期重建镜像吃安全更新（"基础镜像有 CVE 就全量重构建"）。
- **镜像扫描**：`docker scout` / trivy 按 CVE 库扫描镜像层，输出高危漏洞与修复版本。扫描要进 CI 门禁：高危漏洞阻断发布，而不是"扫完存档"。
- **`docker history` 是审计工具**：每层对应一条指令，密码、密钥一旦被 COPY/RUN 进层里，删除文件也还在历史层里 —— 泄露后唯一正确的处置是**换密钥**，而不是"删镜像重传"。
- **瘦身不是目的，交付一致性才是**：镜像从 900MB 到 90MB，pull 时间从 1 分钟到 6 秒，发布速度与带宽成本同步下降；但为省 10MB 换来调试能力全无（distroless 无 shell）需要评估团队排查手段。
- **镜像标签规范**：`应用名:语义化版本-构建号-短摘要`（如 `order:2.3.1-142-a1b2c3d`），可追溯到源码提交；`latest` 只在开发环境用。
- 易错点：把 `.git`、密钥、`node_modules` 全量 COPY 进镜像（体积大 + 密钥泄露）；alpine 里装 glibc 才能跑的 JVM（temurin 已提供 alpine 版，不要自己拼）；扫描工具只扫一次就归档（CVE 库天天更新）；用 root 跑应用（逃逸后直接拥有宿主机 root）。

**示例代码**
```java
import java.util.List;

public class ImageSlim {
    record SizeStep(String item, int sizeMb, String note) {
    }

    static void sizeJourney() {
        System.out.println("【同一个 Java 应用：镜像体积优化路径】");
        List<SizeStep> steps = List.of(
                new SizeStep("ubuntu + openjdk 全量 + 应用", 890, "从零自装，什么都带"),
                new SizeStep("换成 eclipse-temurin:21-jre", 320, "只留运行时"),
                new SizeStep("多阶段构建（构建产物 COPY）", 240, "源码与 .m2 不再进镜像"),
                new SizeStep("temurin:21-jre-alpine", 135, "musl 基座，注意 libc 差异"),
                new SizeStep("jre-alpine + jlink 裁剪运行时", 92, "按需模块，需验证反射配置"));
        int prev = 0;
        for (SizeStep s : steps) {
            String delta = prev == 0 ? "" : String.format("（-%dMB）", prev - s.sizeMb());
            System.out.printf("  %-36s %4dMB %s%n", s.item(), s.sizeMb(), delta);
            prev = s.sizeMb();
        }
        System.out.println("  每一步的取舍都要写进团队文档，而不是只留下一个更小的 tag");
        System.out.println();
    }

    static void multistage() {
        System.out.println("【多阶段构建：两阶段各自装什么】");
        String[][] stages = {
                {"阶段1 build", "maven:3.9-eclipse-temurin-21", "COPY 源码 -> mvn package，~600MB",
                        "构建结束即丢弃"},
                {"阶段2 runtime", "eclipse-temurin:21-jre-alpine", "COPY --from=build target/app.jar",
                        "最终镜像，~135MB"},
        };
        for (String[] s : stages) {
            System.out.printf("  %-12s 基座=%-32s 内容=%s%n      %s%n",
                    s[0], s[1], s[2], s[3]);
        }
        System.out.println("  关键：只有阶段2 被 tag；阶段1 的层不进发布产物");
        System.out.println();
    }

    static void securityHardening() {
        System.out.println("【镜像加固：Dockerfile 与运行时双管齐下】");
        String[][] items = {
                {"USER 10001", "非 root 运行，逃逸后权限受限"},
                {"--read-only + 挂卷", "根文件系统只读，篡改无处落笔"},
                {"--cap-drop=ALL", "丢弃全部 Linux 能力，按需加回"},
                {"固定 digest", "app@sha256:... 防止 tag 漂移"},
                {"每周重建", "吃掉基础镜像的安全更新"},
        };
        for (String[] i : items) {
            System.out.printf("  %-22s %s%n", i[0], i[1]);
        }
        System.out.println();
    }

    static void scanGate() {
        System.out.println("【扫描门禁：CVE 分级与处理策略】");
        record Vuln(String id, String pkg, String severity, String action) {
        }
        List<Vuln> vulns = List.of(
                new Vuln("CVE-2025-1234", "openssl", "HIGH", "基础镜像已出新版 -> 重建即修复"),
                new Vuln("CVE-2025-5678", "log4j-api", "CRITICAL", "无修复版 -> 依赖降级/加缓解，阻断发布"),
                new Vuln("CVE-2024-0233", "zlib", "LOW", "记录到 backlog，随下次基线升级"));
        for (Vuln v : vulns) {
            System.out.printf("  %-14s %-10s %-9s %s%n", v.id(), v.pkg(), v.severity(), v.action());
        }
        System.out.println("  门禁规则：CRITICAL/HIGH 无缓解 -> 流水线失败；扫描进 CI 每次构建执行");
        System.out.println();
    }

    static void historyAudit() {
        System.out.println("【docker history 审计：密钥泄露的真相】");
        List<String> layers = List.of(
                "COPY app.jar app.jar            65MB",
                "COPY .env /app/.env             1KB   ← 密钥进了层！",
                "RUN rm /app/.env                0B    ← 只是白障，上一层文件仍在",
                "ENTRYPOINT [\"java\",\"-jar\",...]  0B");
        for (String l : layers) {
            System.out.println("  " + l);
        }
        System.out.println("  docker run img cat /app/.env 依然能读出密钥");
        System.out.println("  正确处置：立即换密钥 + 构建时用 BuildKit secret 挂载（不进层）");
    }

    public static void main(String[] args) {
        sizeJourney();
        multistage();
        securityHardening();
        scanGate();
        historyAudit();
    }
}
```

**课后练习**
1. 用多阶段构建重构你现有的 Dockerfile，记录优化前后的镜像大小与 `docker history` 层数对比。
2. 安装 trivy（或使用 `docker scout`）扫描自己的应用镜像，把 CRITICAL 漏洞的处理过程写成一段记录（修复/缓解/豁免，各附理由）。
3. 让应用以 `USER 10001 + --read-only` 运行，找到所有需要单独挂可写卷的路径（日志、临时文件），并记录调试过程。
4. 思考题：为什么"镜像里有密钥"的正确处置是换密钥而不是删层？从层的不可变性与镜像分发机制推导结论。
