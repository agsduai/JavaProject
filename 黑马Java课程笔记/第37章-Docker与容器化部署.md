# 第37章 Docker与容器化部署

第36章保证了"应用写得安全"，本章解决"应用怎么稳定地跑起来"。开发环境能跑、测试环境配置不一样、生产环境依赖缺失——这些问题的共同答案是**把应用连同它的运行时环境一起打包交付**，这就是容器化。容器不是轻量虚拟机，而是一个被 namespace（视图隔离）与 cgroup（资源限额）圈起来的普通进程；镜像不是压缩包，而是分层叠加、可共享可缓存的文件系统。本章按「容器原理 → 镜像分层 → Dockerfile → 生命周期 → 网络 → 数据卷 → Compose → Java 容器化 → 镜像瘦身与安全 → K8s 入门 → 发布全流程」的顺序讲，每一步都给出真实命令与真实退出码。所有示例仍是纯 JDK：namespace 与 cgroup 用视图表与记账模拟，镜像分层与写时复制用层栈真实推演，容器状态机、端口映射表、滚动更新全部真跑逻辑，不需要装 Docker 或 K8s 也能理解每个动作的后果。

### 37-01 容器与虚拟机的区别

**知识点**
- **容器不是"轻量虚拟机"**：虚拟机虚拟的是一整套硬件外加自己的操作系统内核，容器共享宿主机内核，只隔离进程视图与资源额度。启动一个 Ubuntu 容器是秒级的，启动一台 Ubuntu 虚拟机是分钟级的，差别不在磁盘大小，而在**有没有自己的内核**。
- **隔离 = namespace（视图）+ cgroup（额度）**：namespace 让进程"看不见"别人 —— PID namespace（容器里的进程从 1 开始编号）、NET namespace（独立网卡、独立端口空间）、MNT namespace（独立文件系统视图）；cgroup 限制"能用多少" —— CPU 配额、内存上限、块设备 IO 速率。**容器本质就是一个被这两样东西圈起来的普通 Linux 进程**。
- **容器里没有完整的 init**：1 号进程就是 ENTRYPOINT 指定的那个进程，它一退出容器就退出。所以容器里没有 systemd，也不该有 —— 一个容器只做一件事。
- **镜像是模板，容器是运行实例**：同一个镜像可以起 N 个容器，互相完全隔离，关系类似类与对象。
- **容器的优势**：秒级启动、密度高（一台宿主机跑几十个）、交付物一致（镜像自带运行时与依赖，"在我机器上是好的"被根治）。
- **容器的代价**：共享内核，只能跑与宿主机内核兼容的系统（Windows 宿主机不能直接跑 Linux 容器）；隔离强度弱于虚拟机，内核漏洞可能导致容器逃逸；默认无持久状态，删容器即丢数据，必须显式挂卷。
- **Java 与容器的关系**：JDK 8u191+ / JDK 10+ 默认开启 `UseContainerSupport`，JVM 会读取 cgroup 配额，按容器限额而不是宿主机内存来计算堆大小；老 JDK 看到的是宿主机的 64G 内存，按默认比例开堆，一进小容器就被内核 OOM 杀掉（退出码 137）。
- 易错点：把容器当虚拟机用（装 systemd、SSH、在里面改内核参数）；以为容器里的数据是持久的（没挂卷，删容器全丢）；内存限额 1G 却让 JVM 堆开 4G（必然 OOMKilled）；把"容器秒级启动"理解成"应用秒级就绪"（进程起了不代表能接流量，要看健康检查）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DockerVsVm {
    record Dim(String name, String vm, String container) {
    }

    record ProcView(int hostPid, int containerPid, String cmd) {
    }

    static void compareTable() {
        System.out.println("【虚拟机 vs 容器：六个维度】");
        List<Dim> dims = List.of(
                new Dim("启动时间", "分钟级（引导内核+init）", "秒级（起一个进程）"),
                new Dim("内核", "每个 VM 自己的内核", "共享宿主机内核"),
                new Dim("隔离强度", "硬件级，最强", "进程级，强但可逃逸"),
                new Dim("单机密度", "几个到几十个", "几十到几百个"),
                new Dim("镜像大小", "GB 级（整个系统盘）", "MB 级（只含运行时）"),
                new Dim("典型用途", "异构 OS、强隔离多租户", "应用交付、CI/CD、微服务"));
        for (Dim d : dims) {
            System.out.printf("  %-8s | 虚拟机：%-22s | 容器：%s%n", d.name(), d.vm(), d.container());
        }
        System.out.println();
    }

    static void pidNamespace() {
        System.out.println("【PID namespace：每个容器都以为自己是 1 号】");
        List<ProcView> javaApp = List.of(new ProcView(4721, 1, "java -jar app.jar"));
        List<ProcView> nginx = List.of(
                new ProcView(5310, 1, "nginx: master"),
                new ProcView(5377, 5, "nginx: worker"));
        printView("容器 java-app", javaApp);
        printView("容器 nginx", nginx);
        System.out.println("  宿主机视角：这些只是普通进程，PID 连续、共享同一内核");
        System.out.println("  教训：容器内没有 init，1 号进程退出 = 容器退出");
        System.out.println();
    }

    private static void printView(String label, List<ProcView> views) {
        System.out.println("  " + label + "（容器内视角）");
        for (ProcView p : views) {
            System.out.printf("    容器PID=%-3d <- 宿主机PID=%-5d %s%n",
                    p.containerPid(), p.hostPid(), p.cmd());
        }
    }

    static void networkNamespace() {
        System.out.println("【NET namespace：两个容器可以同时“监听 8080”】");
        Map<String, String> javaApp = Map.of("eth0", "172.17.0.2", "内部监听", "8080");
        Map<String, String> redis = Map.of("eth0", "172.17.0.3", "内部监听", "6379");
        System.out.println("  java-app : " + javaApp);
        System.out.println("  redis    : " + redis);
        System.out.println("  宿主机通过 docker0 网桥(172.17.0.1)与 veth pair 连到每个容器");
        System.out.println("  端口映射：-p 8081:8080 表示 宿主机8081 -> 容器8080");
        System.out.println();
    }

    static void cgroupQuota() {
        System.out.println("【cgroup：CPU 配额与内存上限的真实记账】");
        // CPU：period=100ms，quota=50ms，即限 0.5 核
        int period = 100, quota = 50;
        int wantCpu = 80;
        int throttled = Math.max(0, wantCpu - quota);
        System.out.printf("  CPU 限额 0.5 核（每 %dms 周期最多用 %dms）%n", period, quota);
        System.out.printf("  本周期想用 %dms -> 放行 %dms，节流 %dms（表现为接口变慢而非报错）%n",
                wantCpu, Math.min(wantCpu, quota), throttled);
        // 内存：limit=256MB
        int limit = 256;
        int used1 = 200, used2 = 300;
        System.out.printf("  内存限额 %dMB：用到 %dMB 正常；用到 %dMB -> 内核 OOM killer 杀掉主进程%n",
                limit, used1, used2);
        System.out.printf("  docker ps 看到 Exited (137)，137 = 128 + SIGKILL(9)%n");
        System.out.println("  推论：JVM 堆必须按容器限额算，不能按宿主机内存算");
        System.out.println();
    }

    static void sameImageManyContainers() {
        System.out.println("【同一镜像起 3 个容器：类与对象的关系】");
        List<String> names = List.of("order-1", "order-2", "order-3");
        for (String n : names) {
            System.out.printf("  %-8s 镜像=app:1.0  IP=172.17.0.%d  独立文件系统(读写层)  独立进程空间%n",
                    n, 2 + names.indexOf(n));
        }
        System.out.println("  镜像只存一份，容器各自叠加一个可写层");
        System.out.println();
    }

    public static void main(String[] args) {
        compareTable();
        pidNamespace();
        networkNamespace();
        cgroupQuota();
        sameImageManyContainers();
    }
}
```

**课后练习**
1. 用自己的话解释：为什么"容器里 ps 看不到宿主机的其他进程"，而"在容器里 top 看到的内存却可能是宿主机的"？分别对应哪种隔离机制？
2. 写一个函数 `jvmHeapSuggestion(long containerLimitMb, boolean legacyJdk)`：容器限额 1024MB 时，新 JDK 与老 JDK 分别建议堆多大？返回并解释理由。
3. 在本机（或虚拟机）安装 Docker，运行 `docker run -m 256m --cpus=0.5 openjdk:21` 并在里面压测，观察 137 退出与 CPU 节流现象，记录 `docker inspect` 里的关键证据。
4. 思考题：一台宿主机上同时跑 100 个基于同一镜像的容器，磁盘上的镜像数据存了几份？容器各自独有的是哪部分？

---

### 37-02 镜像与分层存储

**知识点**
- **镜像 = 只读层的叠加**：每一层记录"相对上一层的文件变化"，从基础层的发行版文件，到 JDK 层，再到应用层。运行容器时，在最上面再叠一个**可写层**。
- **分层带来两个核心收益**：**共享**（多个镜像共用同一个基础层，磁盘和网络只传一份）与**缓存**（构建时父层没变、指令没变，直接复用缓存层，秒级完成）。
- **写时复制（copy-up）**：容器要改一个属于只读层的文件时，先把该文件从下层完整复制到可写层再改；要删除下层文件时，在可写层放一个"白障（whiteout）标记"遮住它。所以**改动越多，可写层越胖，启动越慢**。
- **层里永远只增不删**：在后面一层 `RUN rm big.zip` 并不会让镜像变小，只是加了"它不存在"的记录，前一层的文件还在。想真正变小必须把删除动作合并进产生文件的**同一层**（例如同一条 `RUN` 里下载、解压、删除）。
- **Dockerfile 每条指令大致对应一层**：FROM、RUN、COPY、ADD 产生层；ENV、EXPOSE、WORKDIR、CMD、ENTRYPOINT 只改元数据。
- **镜像 tag 不是版本保证**：`latest` 是可变指针，今天的 latest 和明天的不一样；生产环境应固定 digest（`app@sha256:...`）或自建带版本约定的 tag 规则。
- **pull 增量传输**：本地已有的层不会重新下载，`docker pull` 输出里的 "Layer already exists" 就是分层共享的直接体现。
- 易错点：`RUN apt-get update && apt-get install -y xxx && rm -rf /var/lib/apt/lists/*` 拆成三条 RUN，删除那层根本不省空间；把日志写进容器可写层越跑越大（应输出到 stdout 或挂卷）；用 `latest` 部署导致两台机器跑的不是同一个版本。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImageLayers {
    record Layer(String id, String desc, int sizeMb, String createdBy) {
    }

    record FileEntry(String path, String fromLayer, boolean whiteout) {
    }

    /** 模拟一个镜像的层栈：底层是系统，往上是运行时和应用 */
    static final List<Layer> IMAGE_APP = List.of(
            new Layer("a1f3", "基础发行版 /bin /lib /etc", 72, "FROM debian:12-slim"),
            new Layer("b7c2", "JRE 与字体、时区", 180, "RUN apt-get install jre"),
            new Layer("c9d4", "应用 jar 与启动脚本", 65, "COPY target/app.jar"));

    static void layerStack() {
        System.out.println("【镜像 app:1.0 的三层结构】");
        int total = 0;
        for (Layer l : IMAGE_APP) {
            total += l.sizeMb();
            System.out.printf("  层 %-4s %4dMB  %-24s <- %s%n",
                    l.id(), l.sizeMb(), l.desc(), l.createdBy());
        }
        System.out.printf("  镜像总大小 %dMB = 各层之和；每层只记录相对变化%n%n", total);
    }

    static void shareLayers() {
        System.out.println("【两个镜像共享基础层：pull 只传缺失的层】");
        List<Layer> imageV2 = List.of(
                IMAGE_APP.get(0), IMAGE_APP.get(1),
                new Layer("e2f8", "应用 jar（新版本）", 68, "COPY target/app.jar"));
        Map<String, Integer> localLayers = new LinkedHashMap<>();
        for (Layer l : IMAGE_APP) {
            localLayers.put(l.id(), l.sizeMb());
        }
        int downloaded = 0;
        System.out.println("  pull app:2.0（本地已有 app:1.0）");
        for (Layer l : imageV2) {
            if (localLayers.containsKey(l.id())) {
                System.out.printf("  层 %-4s already exists（跳过）%n", l.id());
            } else {
                downloaded += l.sizeMb();
                System.out.printf("  层 %-4s 下载 %dMB%n", l.id(), l.sizeMb());
            }
        }
        System.out.printf("  实际下载 %dMB，而不是整个镜像的 %dMB%n%n",
                downloaded, imageV2.stream().mapToInt(Layer::sizeMb).sum());
    }

    static void copyOnWrite() {
        System.out.println("【写时复制：容器改文件、删文件时发生了什么】");
        // 可写层初始为空
        List<FileEntry> writable = new ArrayList<>();
        List<FileEntry> lower = List.of(
                new FileEntry("/app/config.yml", "c9d4", false),
                new FileEntry("/app/old-lib.jar", "b7c2", false),
                new FileEntry("/tmp/cache.dat", "c9d4", false));
        System.out.println("  1) 容器 echo 修改 /app/config.yml：");
        writable.add(new FileEntry("/app/config.yml", "rw层(copy-up 自 c9d4)", false));
        showView(lower, writable);
        System.out.println("  2) 容器 rm /app/old-lib.jar：下层文件还在，只是被白障遮住");
        writable.add(new FileEntry("/app/old-lib.jar", "rw层", true));
        showView(lower, writable);
        System.out.println("  3) 结论：删文件不释放镜像空间；频繁写入让可写层变厚");
        System.out.println();
    }

    private static void showView(List<FileEntry> lower, List<FileEntry> writable) {
        System.out.println("     联合挂载后的最终视图：");
        List<String> seen = new ArrayList<>();
        for (FileEntry w : writable) {
            seen.add(w.path());
            System.out.printf("       %-18s 来自 %s%s%n", w.path(), w.fromLayer(),
                    w.whiteout() ? "（已被删除）" : "（容器修改版）");
        }
        for (FileEntry f : lower) {
            if (seen.contains(f.path())) {
                continue;
            }
            System.out.printf("       %-18s 来自 %s%n", f.path(), f.fromLayer());
        }
    }

    static void layersNeverShrink() {
        System.out.println("【rm 放进独立一层，镜像反而不会变小】");
        int base = 72, download = 120, jre = 180;
        int wrong = base + download + jre + 0;
        System.out.printf("  写法A（错误）：RUN curl 下载%dMB -> 新层；再 RUN rm -> 又一层%n", download);
        System.out.printf("    实际占用 %dMB：删除层只是加了白障，下载层仍在%n", wrong);
        int right = base + jre;
        System.out.printf("  写法B（正确）：RUN curl ... && rm -f tmp.zip 合并成一条指令%n");
        System.out.printf("    实际占用 %dMB：中间文件从未进入任何层%n", right);
        System.out.println("  口诀：产生临时文件的指令，必须与清理它的指令写在同一条 RUN 里");
        System.out.println();
    }

    public static void main(String[] args) {
        layerStack();
        shareLayers();
        copyOnWrite();
        layersNeverShrink();
    }
}
```

**课后练习**
1. 在本机构建一个镜像，然后用 `docker history` 查看每一层的大小与产生指令，找出最大的一层并解释它为什么大。
2. 写一个"错误 Dockerfile"（update/install/rm 拆三条 RUN）和"正确 Dockerfile"（合并成一条），分别构建，对比 `docker images` 里的体积差。
3. 思考题：为什么把 `COPY . .` 放在 Dockerfile 第一行是缓存杀手？结合构建缓存的判定规则说明。
4. 生产环境为什么应固定 `镜像@digest` 而不是用 `latest`？列出两个可被 latest 咬到的具体场景。

---

### 37-03 Dockerfile 与构建过程

**知识点**
- **Dockerfile 是一段"从头构建环境的脚本"，每条指令在前一层之上执行**：FROM 选基座，RUN 执行命令产生新层，COPY/ADD 把构建上下文里的文件放进镜像，ENV/WORKDIR 改环境，EXPOSE 只是文档声明，CMD/ENTRYPOINT 定启动命令。
- **构建上下文不是 Dockerfile 目录本身**：`docker build .` 会把 `.` 整个目录打包发给守护进程，`.dockerignore` 排除的文件不进上下文 —— 上下文里有 1GB 日志，构建就会先传 1GB。
- **COPY 与 ADD 的分工**：COPY 只做复制，语义清晰；ADD 额外支持远程 URL 与本地 tar 自动解压。**规范要求优先 COPY**，ADD 只在"必须自动解压 tar"时使用。
- **CMD 与 ENTRYPOINT 的区别**：ENTRYPOINT 是"这个容器天生要跑的命令"，CMD 是它的默认参数；两者都写时，CMD 的内容作为参数追加到 ENTRYPOINT 后面。`docker run 镜像 参数` 会覆盖 CMD 而不覆盖 ENTRYPOINT，这正是"镜像默认跑主程序、又能临时换个命令调试"的实现基础。
- **构建缓存的判定**：从前往后逐条比对，父层 ID 相同且指令文本未变才命中；COPY/ADD 还要求文件内容的校验和未变。任何一条失配，**其后所有层全部重算** —— 这就是"指令顺序 = 缓存策略"的原因。
- **Java 项目标准顺序**：先 COPY `pom.xml`（或分层 jar 的依赖层）→ 下载依赖 → 再 COPY 源码 → 编译。源码天天变，依赖月月变，把易变的放后面才能吃到缓存。
- **多阶段构建**：第一阶段用带编译器的镜像构建产物，第二阶段只 COPY 产物进最小运行镜像，编译器、源码、构建缓存都不进最终镜像。
- 易错点：在 Dockerfile 里硬编码密码（会永远留在层里，`docker history` 可见，应传 ARG/secret 或挂载）；用 `RUN cd /app && ...` 而不是 WORKDIR（cd 不跨指令生效）；把 ENTRYPOINT 写成 `["sh", "-c", "java ..."]` 导致 SIGTERM 发给了 sh 而不是 java，容器优雅停机失效。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class DockerfileDemo {
    record Instruction(String keyword, String arg, String memo) {
    }

    record StageResult(String instruction, boolean cached, int layerSizeMb, String note) {
    }

    static final String DOCKERFILE = """
            FROM eclipse-temurin:21-jre-alpine
            WORKDIR /app
            COPY target/app.jar app.jar
            EXPOSE 8080
            ENTRYPOINT ["java", "-jar", "app.jar"]
            CMD ["--spring.profiles.active=prod"]""";

    static void walkThrough() {
        System.out.println("【逐条执行一个真实 Dockerfile】");
        List<Instruction> plan = List.of(
                new Instruction("FROM", "eclipse-temurin:21-jre-alpine", "拉基座镜像，只含 JRE，约 80MB"),
                new Instruction("WORKDIR", "/app", "后续指令的当前目录，目录不存在则创建"),
                new Instruction("COPY", "target/app.jar app.jar", "把 jar 从构建上下文复制进镜像，产生新层"),
                new Instruction("EXPOSE", "8080", "文档声明端口，不做映射，运行时还要 -p"),
                new Instruction("ENTRYPOINT", "[\"java\", \"-jar\", \"app.jar\"]", "固定主命令，exec 形式保证 java 是 1 号进程"),
                new Instruction("CMD", "[\"--spring.profiles.active=prod\"]", "默认参数，可被 docker run 后面的参数覆盖"));
        for (Instruction ins : plan) {
            System.out.printf("  %-10s %-32s %s%n", ins.keyword(), ins.arg(), ins.memo());
        }
        System.out.println();
    }

    static void cmdVsEntrypoint() {
        System.out.println("【CMD / ENTRYPOINT 组合行为推演】");
        String entry = "java -jar app.jar";
        String cmd = "--profile=prod";
        System.out.printf("  只有 CMD      ：docker run img        -> %s%n", cmd);
        System.out.printf("  只有 CMD      ：docker run img --test -> %s（CMD 被覆盖）%n", "--test");
        System.out.printf("  ENTRYPOINT+CMD：docker run img        -> %s %s%n", entry, cmd);
        System.out.printf("  ENTRYPOINT+CMD：docker run img --test -> %s --test（追加参数）%n", entry);
        System.out.printf("  调试覆盖      ：docker run --entrypoint sh img -> 进入 shell 排查%n");
        System.out.println();
    }

    static void buildWithCache() {
        System.out.println("【第二次构建：缓存命中推演】");
        List<StageResult> run2 = List.of(
                new StageResult("FROM temurin:21-jre-alpine", true, 0, "基础层本地已有"),
                new StageResult("WORKDIR /app", true, 0, "指令未变且父层相同"),
                new StageResult("COPY target/app.jar", false, 68, "jar 重新编译过，校验和变了，失效"),
                new StageResult("EXPOSE 8080", false, 0, "父层已变，只能重算（虽然指令没变）"),
                new StageResult("ENTRYPOINT ...", false, 0, "同上"));
        for (StageResult s : run2) {
            System.out.printf("  %-28s %s  %s%n", s.instruction(),
                    s.cached() ? "CACHED" : "重算  ", s.note());
        }
        System.out.println("  规则：一条失配，其后全部重算；所以易变的指令必须放在最后");
        System.out.println();
    }

    static void goodVsBadOrder() {
        System.out.println("【同样的文件，指令顺序决定改一行源码重建多久】");
        System.out.println("  写法A（坏）：COPY . . -> RUN mvn package");
        System.out.println("    改一行代码：COPY 失效 -> 依赖重新下载 -> 编译重跑，约 3~5 分钟");
        System.out.println("  写法B（好）：COPY pom.xml . -> RUN mvn dependency:go-offline -> COPY src . -> RUN mvn package");
        System.out.println("    改一行代码：pom 未变，依赖层 CACHED，只重跑编译，约 30 秒");
        System.out.println("  口诀：把变化频率低的放前面，变化频率高的放后面");
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("---- Dockerfile 原文 ----");
        System.out.println(DOCKERFILE);
        System.out.println();
        walkThrough();
        cmdVsEntrypoint();
        buildWithCache();
        goodVsBadOrder();
    }
}
```

**课后练习**
1. 为一个 Maven 项目写出"缓存友好"的完整 Dockerfile，并解释每条指令为什么放在那个位置。
2. 用 `--entrypoint sh` 进入一个正在运行的镜像排查环境问题，退出后解释 ENTRYPOINT 与 CMD 的覆盖规则各是什么。
3. 把 `ENTRYPOINT ["sh", "-c", "java -jar app.jar"]` 改成 exec 形式，解释为什么原写法会让 `docker stop` 等满 10 秒后才 SIGKILL。
4. 思考题：`.dockerignore` 里应该排除哪些文件？（提示：`.git`、`target`、日志、密钥文件——各说出一个后果）

### 37-04 容器的生命周期

**知识点**
- **容器的状态是一条状态机**：`created → running → paused → exited → removed`，其中 running 时 Docker 还维护一个 health 状态（starting/healthy/unhealthy）用于编排决策。`docker start` 是从 created/exited 回 running，`docker rm` 只能删已停止的容器（强制加 `-f`）。
- **退出码是最重要的排障入口**：0 表示进程正常退出；137 = 128+9，SIGKILL，通常是内存超限被内核 OOM killer 杀掉；143 = 128+15，SIGTERM，是 `docker stop` 的默认动作；126/127 是命令不可执行/不存在；125 是 Docker 自身错误。看到 137 先查内存限额，看到 143 先确认应用是否优雅停机。
- **`docker stop` 的 10 秒宽限期**：先发 SIGTERM 给 1 号进程，超过 `--time`（默认 10s）才 SIGKILL。应用必须捕获 SIGTERM 并触发优雅停机（处理完在途请求、关闭连接池），否则要么被硬杀丢请求，要么白白等 10 秒。
- **重启策略决定"挂了怎么办"**：`no`（默认）、`on-failure[:N]`（非零退出才重启，最多 N 次）、`always`（只要不是手工 stop 就拉起）、`unless-stopped`（同 always，但手工 stop 后重启宿主机也不拉起）。策略是容器属性，改策略要重建容器。
- **崩溃循环（crash loop）**：配置错误的应用每次启动即退出，always 策略会让它无限拉起。要有启动次数/退避机制，并靠健康检查+告警发现"容器在 running 但一直不就绪"。
- **健康检查**：HEALTHCHECK 定期执行探针命令，连续 `--retries` 次失败转 unhealthy。健康检查失败**不会**自动重启容器（Docker 单机如此；K8s 的 liveness 才会重启），它只是把状态暴露给 `docker ps` 与编排系统。
- **paused 状态**：`docker pause` 冻结进程（cgroup freezer），用于 CPU 满载时临时让路，不是停机，内存还占着。
- 易错点：把 `docker restart` 当修复手段（不查退出码，容器带病循环重启）；用 `docker kill` 代替 `stop`（跳过优雅停机）；健康检查命令写得不对导致永远 healthy（例如用 curl 检查但没检查返回码）；1 号进程不转发信号，stop 永远超时。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ContainerLifecycle {
    enum State {
        CREATED("已创建，未运行"),
        RUNNING("运行中"),
        PAUSED("进程被冻结"),
        EXITED("已退出"),
        REMOVED("已删除");

        final String cn;

        State(String cn) {
            this.cn = cn;
        }
    }

    record Event(State from, String action, State to, int exitCode, String note) {
    }

    static void stateMachine() {
        System.out.println("【容器状态机：一次完整生命】");
        List<Event> events = List.of(
                new Event(State.CREATED, "docker start", State.RUNNING, -1, "1号进程启动"),
                new Event(State.RUNNING, "docker pause", State.PAUSED, -1, "cgroup freezer 冻结"),
                new Event(State.PAUSED, "docker unpause", State.RUNNING, -1, "恢复调度"),
                new Event(State.RUNNING, "docker stop(10s)", State.EXITED, 143, "SIGTERM 优雅退出"),
                new Event(State.EXITED, "docker rm", State.REMOVED, -1, "可写层一并删除"));
        for (Event e : events) {
            String exit = e.exitCode() < 0 ? "-" : String.valueOf(e.exitCode());
            System.out.printf("  %-7s --(%-16s)--> %-7s exit=%-4s %s%n",
                    e.from(), e.action(), e.to(), exit, e.note());
        }
        System.out.println();
    }

    static void exitCodes() {
        System.out.println("【退出码速查：docker ps 一眼定位事故类别】");
        String[][] codes = {
                {"0", "进程正常退出", "检查是否被配置成跑完就退（如一次性任务）"},
                {"125", "docker 自身错误", "参数、权限、运行时问题"},
                {"126", "命令不可执行", "ENTRYPOINT 指向的文件没有执行权限"},
                {"127", "命令不存在", "镜像里没有这个命令，PATH 不对"},
                {"137", "SIGKILL(128+9)", "内存超限 OOMKilled 或 kill -9，先查内存限额"},
                {"143", "SIGTERM(128+15)", "docker stop 的正常动作，应优雅退出"},
        };
        for (String[] c : codes) {
            System.out.printf("  %-4s %-16s %s%n", c[0], c[1], c[2]);
        }
        System.out.println();
    }

    static void gracefulShutdown() {
        System.out.println("【docker stop 的 10 秒宽限期：为什么应用要处理 SIGTERM】");
        long termTime = 800, killTime = 0;
        System.out.println("  写法A：应用监听 SIGTERM，关闭 HTTP 服务、等待在途请求");
        System.out.printf("    收到信号 %dms 内退出，用户请求无一丢失%n", termTime);
        System.out.println("  写法B：应用不处理信号（默认 sh 包一层也常出此问题）");
        System.out.println("    SIGTERM 发给了 sh 或被忽略 -> 等 10s -> SIGKILL");
        System.out.println("    在途请求被硬杀，连接池脏，上游开始报 502");
        System.out.printf("    停机耗时 = 10000ms，有效处理 %dms%n", killTime);
        System.out.println("  正确姿势：exec 形式启动 + 运行时钩子关闭资源");
        System.out.println();
    }

    static void restartPolicy() {
        System.out.println("【重启策略推演：配置错误应用的崩溃循环】");
        int[] exits = {127, 127, 127, 127, 127};
        int maxRetry = 5, started = 0;
        System.out.println("  策略 on-failure:5，应用配置写错，每次启动即 exit 127：");
        for (int i = 0; i < exits.length; i++) {
            started++;
            System.out.printf("    第 %d 次启动 -> exit %d%n", i + 1, exits[i]);
        }
        System.out.printf("    达到上限 %d 次，Docker 放弃重启，容器停在 Exited(127)%n", maxRetry);
        System.out.println("  策略 always 则会无限拉起：表象是“容器在跑”，实际在 crash loop");
        System.out.println("  对策：启动快速失败要有告警；单机 Docker 无健康检查自愈，交给 K8s");
        System.out.println();
    }

    static void healthCheck() {
        System.out.println("【健康检查三态：starting -> healthy / unhealthy】");
        String[] probes = {"ok", "ok", "timeout", "timeout", "timeout"};
        int intervalMs = 3000, retries = 3;
        int fails = 0;
        String state = "starting";
        for (int i = 0; i < probes.length; i++) {
            boolean ok = probes[i].equals("ok");
            fails = ok ? 0 : fails + 1;
            state = fails >= retries ? "unhealthy" : (fails > 0 ? "starting" : "healthy");
            System.out.printf("  探针#%d %-7s 连续失败=%d -> health=%s%n",
                    i + 1, probes[i], fails, state);
        }
        System.out.printf("  （interval=%dms retries=%d）单机 Docker 不自动重启 unhealthy 容器%n",
                intervalMs, retries);
        System.out.println("  但 compose/K8s 会依据 health/readiness 决定是否接入流量");
        System.out.println();
    }

    public static void main(String[] args) {
        stateMachine();
        exitCodes();
        gracefulShutdown();
        restartPolicy();
        healthCheck();
    }
}
```

**课后练习**
1. 制造三种真实退出：容器内存限额 64M 后分配大数组（137）、ENTRYPOINT 指向不存在的脚本（127）、应用主动 System.exit(0)（0），记录 `docker inspect` 的 `State.OOMKilled` 字段。
2. 写一个带 SIGTERM 钩子的 Java 程序（`Runtime.getRuntime().addShutdownHook`），打包进镜像，`docker stop` 验证能在 1 秒内优雅退出，并解释 exec 形式与 shell 形式的差别。
3. 分别用 `no`、`on-failure:3`、`always` 跑同一个秒退镜像，观察 `docker ps -a` 的差异，写出三种策略各自适用的场景。
4. 思考题：为什么单机 Docker 的健康检查失败不会重启容器？这与 K8s 的 liveness/readiness 探针的行为差异意味着什么？

---

### 37-05 网络模式与端口映射

**知识点**
- **四种网络模式**：`bridge`（默认，容器接在 docker0 网桥上，有独立 IP）、`host`（直接用宿主机网络栈，无独立 IP、无端口映射，性能最好）、`none`（只有 lo，完全离线）、`container:xxx`（与指定容器共享网络栈，K8s Pod 内多容器就是这个模式）。
- **bridge 的真实链路**：每个容器有一对 veth（一端在容器里叫 eth0，一端插在 docker0 网桥），容器出网走 NAT（源地址被替换成宿主机 IP），外部访问容器走端口映射 DNAT。所以**容器 IP 外部不可直连，-p 映射才是对外入口**。
- **端口映射 `-p 宿主端口:容器端口` 是一张 DNAT 表**：宿主机收到 8081 的包 → 查表改目标地址为容器 IP:8080 → 转发进容器。同一宿主机端口只能被一个映射占用，冲突直接报 "port is already allocated"。
- **容器互访用服务名，不用 IP**：compose/自定义网络内置 DNS，按容器名/服务名解析到当前 IP。容器 IP 每次重建都会变，**写死容器 IP 是最常见的联调事故**。
- **默认 bridge 与自定义 bridge 的区别**：默认 bridge（docker0）没有内置 DNS，只能用古老(link)或 IP 互访；`docker network create` 出来的自定义网络才有按名字解析。**compose 项目默认创建自定义网络**，所以服务名互访"开箱即用"。
- **host 模式的取舍**：免去 NAT 与网桥开销、延迟最低，代价是失去网络隔离、端口自己管、不同宿主机之间没有统一抽象 —— 一般只在性能敏感的边缘场景用。
- **发布 vs 暴露**：EXPOSE 只是文档；`-p/--publish` 才真正把端口映射到宿主机；compose 里 `ports:` 是发布，`expose:` 只是容器网络内可见。
- 易错点：本机能 curl 容器 IP 就以为外部也能（NAT 只在宿主机上生效）；用默认 bridge 又想按名字互访（不支持，建自定义网络）；映射 0.0.0.0 却以为只有内网能访问（云主机的安全组才是最后一道门）；一个容器里写 `-p 8080:8080 -p 8080:8080`（第二个必冲突）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DockerNetwork {
    record Mapping(String hostPort, String container, int containerPort, String proto) {
    }

    record Route(String from, String to, String path, String note) {
    }

    static void modes() {
        System.out.println("【四种网络模式】");
        String[][] modes = {
                {"bridge", "docker0 网桥+独立 IP+可映射端口", "默认模式，90% 场景"},
                {"host", "与宿主机共享网络栈，无 NAT", "极致性能，牺牲隔离"},
                {"none", "只有 lo 回环", "离线任务、安全敏感"},
                {"container:xx", "复用另一容器的网络栈", "K8s Pod 内多容器协作"},
        };
        for (String[] m : modes) {
            System.out.printf("  %-12s %-32s %s%n", m[0], m[1], m[2]);
        }
        System.out.println();
    }

    static void bridgeTopology() {
        System.out.println("【bridge 拓扑与一进一出两条链路】");
        List<Route> routes = List.of(
                new Route("容器 app -> 数据库容器", "容器网段直连", "app(172.17.0.2) -> docker0 -> db(172.17.0.3)",
                        "同网桥二层转发，不经 NAT"),
                new Route("外部用户 -> 容器 app", "入站 DNAT", "client -> host:8081 -> DNAT -> app:8080",
                        "由 -p 8081:8080 的映射表驱动"),
                new Route("容器 app -> 外网 API", "出站 SNAT", "app -> docker0 -> SNAT(宿主机IP) -> internet",
                        "回包按连接跟踪还原"));
        for (Route r : routes) {
            System.out.printf("  %-16s %-8s %s%n", r.from(), r.to(), r.path());
            System.out.printf("    %s%n", r.note());
        }
        System.out.println();
    }

    static void portMapping() {
        System.out.println("【-p 映射表：宿主机端口是稀缺资源】");
        List<Mapping> table = List.of(
                new Mapping("8081", "app(172.17.0.2)", 8080, "tcp"),
                new Mapping("8082", "app-2(172.17.0.4)", 8080, "tcp"),
                new Mapping("6379", "redis(172.17.0.3)", 6379, "tcp"));
        for (Mapping m : table) {
            System.out.printf("  宿主:%-5s -> %-20s 容器:%-5d %s%n",
                    m.hostPort(), m.container(), m.containerPort(), m.proto());
        }
        System.out.println("  两个 app 容器内部都用 8080 —— 端口空间是每个容器独立的");
        System.out.println("  冲突示例：再映射 -p 8081:80 会报 port is already allocated");
        System.out.println("  安全建议：redis 这类内部服务只映射 127.0.0.1:6379:6379");
        System.out.println();
    }

    static void dnsBetweenContainers() {
        System.out.println("【容器互访：按名字解析，不要写 IP】");
        Map<String, String> dns = Map.of(
                "db", "172.20.0.2",
                "redis", "172.20.0.3",
                "app", "172.20.0.4");
        for (Map.Entry<String, String> e : dns.entrySet()) {
            System.out.printf("  app 调用 %s -> 内置 DNS 解析为 %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  容器重建后 IP 会变：db 重启换成 172.20.0.9");
        System.out.println("  app 配置写 jdbc:mysql://db:3306 仍然连通，写死 IP 直接断联");
        System.out.println("  注意：默认 bridge(docker0) 没有这个 DNS；自定义网络/compose 才有");
        System.out.println();
    }

    static void commonMistakes() {
        System.out.println("【四个真实事故场景】");
        List<String> mistakes = List.of(
                "① 本机 curl 172.17.0.2:8080 通了，外部用户访问不通 —— 容器 IP 只在宿主机可达，必须 -p 发布",
                "② -p 6379:6379 忘了限定 127.0.0.1，redis 裸奔公网，被扫到无密码直接写入恶意 key",
                "③ compose 里服务名写错（db_1），DNS 解析失败，一直 connection refused",
                "④ host 模式两个服务都监听 8080，第二个起不来 —— 共享宿主机端口空间");
        for (String m : mistakes) {
            System.out.println("  " + m);
        }
        System.out.println("  共同根因：把“容器网络”当成“宿主机网络”来理解");
    }

    public static void main(String[] args) {
        modes();
        bridgeTopology();
        portMapping();
        dnsBetweenContainers();
        commonMistakes();
    }
}
```

**课后练习**
1. 起两个 nginx 容器分别映射 8081/8082，宿主机 curl 验证；再尝试把第二个映射到同一个 8081，记录报错原文。
2. 用 `docker network create` 建自定义网络，跑一个 redis 和一个 `redis-cli` 客户端容器，让客户端用服务名连接；再换到默认 bridge 重试，解释差异。
3. 用 `-p 127.0.0.1:6379:6379` 与 `-p 6379:6379` 分别启动 redis，用外部机器（或公网 IP）探测，写出两种写法的安全差异。
4. 思考题：K8s Pod 里的两个容器可以直接用 localhost 互访，它们用了本章的哪种网络模式？这个设计给 Sidecar 模式带来什么便利？

---

### 37-06 数据卷与持久化

**知识点**
- **容器可写层是临时的**：`docker rm` 即销毁，进程写入的可写层数据全部丢失。凡是要"留下来"的数据（数据库文件、上传文件、日志归档），必须放在**卷（volume）**上。
- **三种挂载方式**：`volume`（`-v mydata:/var/lib/mysql`，由 Docker 统一管理，位置与驱动与宿主机无关，**首选**）；`bind mount`（`-v /data/app:/app/data`，宿主机目录直接映射，适合配置文件与本地开发热更新）；`tmpfs`（内存挂载，容器停止即消失，适合临时敏感文件）。
- **卷的生命周期独立于容器**：删容器卷还在，`docker volume ls` 可见；卷直到被 `docker volume rm` / `prune` 才删除。备份卷的标准姿势是起一个临时容器挂同一卷做 tar。
- **具名卷 vs 匿名卷**：`-v mydata:/path` 是具名卷（可管理可复用）；`-v /path` 是匿名卷（一串随机 ID，容易堆积），规范要求永远给卷命名。
- **bind mount 会"遮住"镜像内容**：挂载一个空宿主机目录到 `/app/conf`，镜像里该目录的默认配置文件在容器里就"消失"了。首次挂空目录想要"以镜像内容初始化"，用具名卷而不是 bind mount。
- **挂载方向与权限**：容器内进程通常以非 root 跑，bind mount 的宿主机目录属主要对齐 UID，否则读写失败；Linux 上还会遇到 SELinux/AppArmor 标签问题。
- **数据库容器的黄金法则**：数据目录必须挂卷，且卷随数据库版本一起升级（不要两个版本共用一个卷直接升级）；有主从时每副本一个独立卷。
- 易错点：测试环境跑得好好的，删容器重建数据全丢（没挂卷）；挂了卷却挂在错误路径（数据库实际写 `/var/lib/postgresql/data` 而你挂的是 `/data`）；bind mount 空目录盖住镜像配置，应用"莫名"用默认配置跑起来；卷堆积几百个匿名卷从不清理，磁盘悄悄爆满。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DockerVolume {
    record Mount(String type, String spec, String persists, String useCase) {
    }

    static void mountTypes() {
        System.out.println("【三种挂载方式对比】");
        List<Mount> mounts = List.of(
                new Mount("volume", "-v pgdata:/var/lib/postgresql/data",
                        "Docker 管理，删容器仍在", "数据库等有状态服务（首选）"),
                new Mount("bind", "-v /home/me/app/conf:/app/conf",
                        "宿主机目录，人可直接编辑", "配置文件、本地开发热更新"),
                new Mount("tmpfs", "--mount type=tmpfs,dst=/tmp/secret",
                        "内存，容器停止即消失", "临时令牌、加密密钥"));
        for (Mount m : mounts) {
            System.out.printf("  %-6s %-40s%n      持久性：%-22s 用途：%s%n",
                    m.type(), m.spec(), m.persists(), m.useCase());
        }
        System.out.println();
    }

    static void containerLifecycleVsVolume() {
        System.out.println("【容器会死，卷不会：一次删除重建实验】");
        Map<String, String> volume = new LinkedHashMap<>();
        volume.put("pgdata/orders.tbl", "10000 行");
        System.out.println("  容器 app-a（挂卷 pgdata）写入：" + volume);
        System.out.println("  docker rm -f app-a          -> 可写层销毁，卷 pgdata 保留");
        System.out.println("  docker run -v pgdata:... app-b -> 重新挂上同一个卷");
        System.out.println("  app-b 读到：" + volume + "（数据完好）");
        System.out.println("  对照组：没挂卷的容器 rm 后，写入的 10000 行全部丢失");
        System.out.println();
    }

    static void bindMountShadow() {
        System.out.println("【bind mount 遮蔽效应：配置文件“消失”事故】");
        System.out.println("  镜像内 /app/conf/application.yml（内置默认配置）");
        System.out.println("  宿主机 /home/me/conf/ 是空目录");
        System.out.println("  运行：-v /home/me/conf:/app/conf");
        System.out.println("  容器内视图：/app/conf 为空 —— 镜像里的默认配置被宿主机空目录盖住");
        System.out.println("  现象：应用“随机”回到默认端口/默认密码，其实是读不到配置");
        System.out.println("  对策：配置目录挂载前先放好文件，或用具名卷做首次初始化");
        System.out.println();
    }

    static void volumeOps() {
        System.out.println("【卷的日常运维：备份与清理】");
        System.out.println("  备份 pgdata 卷（标准三行）：");
        System.out.println("    docker run --rm -v pgdata:/data -v $(pwd):/backup alpine \\");
        System.out.println("      tar czf /backup/pgdata.tgz -C /data .");
        System.out.println("  查看堆积：docker volume ls | wc -l");
        System.out.println("  清理无主卷：docker volume prune（先确认没有要保留的匿名卷！）");
        System.out.println("  磁盘排查：docker system df 看卷/镜像/容器分别占了多少");
        System.out.println();
    }

    static void statefulChecklist() {
        System.out.println("【有状态服务上线清单】");
        List<String> checks = List.of(
                "数据目录确认：镜像文档里的真实数据路径，而不是想当然的 /data",
                "具名卷命名：服务名-用途，如 mysql-data、es-data，禁止匿名卷",
                "UID/GID 对齐：bind mount 时容器进程与宿主机目录属主一致",
                "备份演练：卷备份脚本真实跑一次并验证可恢复",
                "容量规划：docker system df + 宿主机磁盘告警双保险");
        for (String c : checks) {
            System.out.println("  - " + c);
        }
    }

    public static void main(String[] args) {
        mountTypes();
        containerLifecycleVsVolume();
        bindMountShadow();
        volumeOps();
        statefulChecklist();
    }
}
```

**课后练习**
1. 跑一个 MySQL/Postgres 容器写入测试表，`docker rm -f` 后用同一个具名卷重建容器，验证数据还在；再跑一个不挂卷的对照组，记录差异。
2. 复现"bind mount 空目录遮蔽配置"事故：挂一个空目录到 nginx 的 `/usr/share/nginx/html`，观察默认首页消失，然后给出两种修复方案。
3. 写一个卷备份脚本（临时容器 + tar），并在另一台目录恢复测试，输出恢复后文件列表作为证据。
4. 思考题：数据库容器的数据目录为什么推荐 volume 而不是 bind mount？至少给出两个可维护性理由。

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

### 37-10 Kubernetes 核心概念入门

**知识点**
- **K8s 管的是"期望状态"而不是"过程"**：你声明"order 服务要有 3 个副本、用 2.3.1 镜像"，控制器循环（Reconcile Loop）持续比对期望与实际，少了就补、多了就删、不健康就重建。这是它与 compose 最本质的区别：**自愈与收敛**。
- **Pod 是最小调度单元**：一个 Pod = 一个或多个共享网络/存储的容器（共用 localhost 与卷），要么整体一起调度。**不要在 Pod 里放互不相关的容器**，Pod 通常"一个主容器 + 可选 Sidecar"。
- **Deployment 管 ReplicaSet 管 Pod**：Deployment 声明副本数与滚动更新策略，ReplicaSet 负责维持副本数量，Pod 才是真正跑的。回滚 = 回退到上一个 ReplicaSet，秒级完成。
- **滚动更新参数**：`maxSurge`（最多多起几个新副本）与 `maxUnavailable`（最多少几个旧副本），两者决定更新过程中容量曲线。想"零损失发布"就 maxUnavailable=0 + maxSurge=1：先起新的，就绪后再杀旧的。
- **探针三件套**：`startupProbe`（应用启动慢的保护，启动成功前不跑其他探针）、`readinessProbe`（不就绪就摘出 Service，不接流量但**不重启**）、`livenessProbe`（失败即重启容器，阈值太敏感会造成重启风暴）。探针失败是"接口 502 但容器还在跑"的最常见解释。
- **Service 是稳定的虚拟 IP + 负载均衡**：Pod IP 随时生灭，Service 用 label selector 圈住一组 Pod，提供稳定 ClusterIP 与 DNS 名（`svc.namespace.svc.cluster.local`）。`NodePort/LoadBalancer` 是对外暴露的进阶形式。
- **ConfigMap 与 Secret**：配置与镜像分离 —— ConfigMap 放普通配置，Secret 放敏感信息（base64 编码不是加密，权限控制才是关键），都可作为环境变量或文件挂载进 Pod。
- **资源请求与限额**：`requests` 是调度依据（节点至少留这么多），`limits` 是运行上限；CPU 超 limit 被节流，内存超 limit 被 OOMKilled。**不配 requests 的 Pod 调度是赌博**，节点超卖会让所有服务一起抖动。
- 易错点：liveness 探针指向依赖数据库的接口（数据库抖一下全集群重启）；镜像 tag 用 latest 且 imagePullPolicy 默认（K8s 不重新拉取，更新"不生效"）；Pod 里跑多个不相干进程（无法独立扩缩容与重启）；把配置写死在镜像里换环境就重新构建（应用 ConfigMap 分离）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class K8sIntro {
    record Pod(String name, String image, String phase, boolean ready) {
    }

    static void hierarchy() {
        System.out.println("【三层结构：Deployment -> ReplicaSet -> Pod】");
        List<Pod> pods = List.of(
                new Pod("order-7d9f4-x2k1p", "order:2.3.1", "Running", true),
                new Pod("order-7d9f4-k8w2q", "order:2.3.1", "Running", true),
                new Pod("order-7d9f4-m3v8s", "order:2.3.1", "Running", true));
        System.out.println("  Deployment order（期望副本=3, 镜像 order:2.3.1）");
        System.out.println("    └─ ReplicaSet order-7d9f4（当前持有 3 个 Pod）");
        for (Pod p : pods) {
            System.out.printf("        ├─ %-22s %-8s ready=%s%n", p.name(), p.phase(), p.ready());
        }
        System.out.println("  杀掉一个 Pod -> ReplicaSet 检测到 2/3 -> 自动补一个新 Pod");
        System.out.println();
    }

    static void reconcileLoop() {
        System.out.println("【控制器循环：期望状态 vs 实际状态】");
        int desired = 3;
        int actual = 1;
        List<String> actions = List.of(
                "diff: 期望3 实际1 -> 创建 2 个 Pod（镜像 order:2.3.1）",
                "diff: 期望3 实际3 -> 无操作",
                "diff: 期望3 实际4（有人手动扩了）-> 删除 1 个多余 Pod",
                "diff: 期望3 实际3 -> 无操作（收敛完成，继续轮询）");
        for (String a : actions) {
            System.out.println("  " + a);
        }
        System.out.printf("  本轮收敛结果：actual=%d -> desired=%d%n", actual, desired);
        System.out.println("  你永远在改“期望”，K8s 负责把现实拉回期望");
        System.out.println();
    }

    static void rollingUpdate() {
        System.out.println("【滚动更新：maxSurge=1, maxUnavailable=0，v1 -> v2】");
        List<String> steps = List.of(
                "初始      v1×3（全部 ready，承载流量）",
                "step1     v1×3 + v2×1（surge 多起 1 个，v2 未就绪不接流量）",
                "step2     v2 就绪 -> v1×2 + v2×2（杀 1 个 v1，始终保持 ≥3 可用）",
                "step3     v1×1 + v2×3",
                "step4     v2×3 完成，旧 ReplicaSet 保留（用于秒级回滚）");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  探针失败时更新会卡住（progressDeadline），超时自动回滚");
        System.out.println();
    }

    static void probes() {
        System.out.println("【三种探针的分工】");
        String[][] probes = {
                {"startupProbe", "应用启动慢？启动成功前不执行其他探针", "失败超过阈值 -> 重启"},
                {"readinessProbe", "能接流量吗？失败只摘流量，不重启", "更新/扩容期间的关键闸门"},
                {"livenessProbe", "还活着吗？失败即重启容器", "阈值放宽，避免重启风暴"},
        };
        for (String[] p : probes) {
            System.out.printf("  %-14s %-32s %s%n", p[0], p[1], p[2]);
        }
        System.out.println("  反例：liveness 指向 /dbCheck -> DB 抖动 -> 全体重启 -> 雪崩");
        System.out.println("  正解：liveness 只测进程自身，外部依赖放 readiness");
        System.out.println();
    }

    static void serviceAndConfig() {
        System.out.println("【Service 与配置分离】");
        System.out.println("  Service order：selector app=order -> 稳定 DNS order.prod.svc:8080");
        System.out.println("  Pod 生灭不影响调用方；Service 自动挑 ready 的 Pod");
        System.out.println("  ConfigMap order-cfg：application-prod.yml 挂载到 /app/config");
        System.out.println("  Secret order-sec：DB 密码以 env 注入；权限用 RBAC 控制");
        System.out.println("  requests: cpu 250m / memory 512Mi；limits: cpu 1 / memory 1Gi");
        System.out.println("  没写 requests 的 Pod 在节点超卖时会与邻居互相挤压");
    }

    public static void main(String[] args) {
        hierarchy();
        reconcileLoop();
        rollingUpdate();
        probes();
        serviceAndConfig();
    }
}
```

**课后练习**
1. 用 minikube/kind 起一个本地集群，部署 3 副本 Deployment，手动删除一个 Pod 观察 ReplicaSet 自动补齐，记录事件输出。
2. 修改 liveness 探针为一个必然失败的路径，观察重启风暴与退避间隔，然后按"探针分工"改成合理配置。
3. 做一次真实滚动更新（v1 -> v2），期间用循环脚本请求 Service，验证 maxUnavailable=0 时无请求失败；再制造一个就绪失败的 v3，观察更新卡住与自动回滚。
4. 思考题：compose 的 depends_on 与 K8s 的"不保证启动顺序" philosophic 冲突在哪？在 K8s 里应用应如何实现"等数据库就绪"？（提示：重试 + readiness）

---

### 37-11 综合案例：一次完整的容器化发布

**知识点**
- **发布全流程九步**：本地验证 → 写 Dockerfile（多阶段）→ 构建镜像并打版本 tag → 推送仓库 → 单容器冒烟 → compose 起全栈联调 → 推到 K8s（改 Deployment 镜像）→ 观察滚动更新与监控 → 验证成功或秒级回滚。每一步都有明确的"通过标准"，失败就停，不带病前进。
- **版本 tag 在构建时定型**：`docker build -t registry/app:2.3.1-142 .`，tag 里带构建号与提交摘要；**同一份产物推同一个 digest**，多环境部署的是同一个 digest，杜绝"测试环境和生产跑的不一样"。
- **冒烟测试先于编排**：单容器 `docker run` 后先 curl 健康检查与核心接口，再进 compose 全栈；编排失败时先分清"镜像坏了"还是"编排配置坏了"，两回事排障路径完全不同。
- **发布窗口的观测三件套**：Pod 就绪数、错误率（5xx 比例）、核心业务指标（下单成功率）。滚动更新期间任何一项劣化，立即回滚 —— 回滚是零成本操作（回到旧 ReplicaSet），而"带病坚持排查"的代价不可控。
- **回滚演练是发布能力的一部分**：`kubectl rollout undo deployment/order` 一条命令回到上一版本；但没有提前演练过回滚的团队，第一次回滚往往发生在深夜事故里。
- **失败案例的三类根因**：镜像问题（依赖缺失、配置硬编码）、编排问题（探针路径错、资源限额太小）、环境问题（数据库未迁移、DNS 不通）。发布 checklist 的意义是把三类问题的检查项前置。
- 易错点：直接在 K8s 里改 Pod（Pod 是临时对象，会被 ReplicaSet 按期望状态拉回，必须改 Deployment）；更新镜像 tag 没变导致"不生效"（tag 相同则不重新拉取，要换 tag 或强制 imagePullPolicy: Always）；发布后不看指标就宣布成功（错误率可能 10 分钟后才起来）；回滚后不查根因（旧版本也在劣化，只是慢）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ContainerizeCase {
    enum StepResult {
        PASS, FAIL
    }

    record Step(int no, String name, String command, StepResult result, String evidence) {
    }

    static void pipeline() {
        System.out.println("【发布流水线：order 服务 v2.3.1-142】");
        List<Step> steps = List.of(
                new Step(1, "本地测试", "mvn verify", StepResult.PASS, "142 个测试全绿"),
                new Step(2, "构建镜像", "docker build -t registry/order:2.3.1-142 .", StepResult.PASS, "多阶段，135MB"),
                new Step(3, "推送仓库", "docker push registry/order:2.3.1-142", StepResult.PASS, "digest sha256:a1b2..."),
                new Step(4, "单容器冒烟", "docker run -p 8080:8080 ... + curl /health", StepResult.PASS, "HTTP 200，耗时 45ms"),
                new Step(5, "compose 联调", "docker compose up -d", StepResult.PASS, "app/db/redis 三服务 healthy"),
                new Step(6, "更新 Deployment", "kubectl set image deploy/order order=...:2.3.1-142", StepResult.PASS, "触发滚动更新"),
                new Step(7, "观察指标", "kubectl rollout status + 监控面板", StepResult.PASS, "5xx 0.1%，就绪 6/6"),
                new Step(8, "回滚预案确认", "kubectl rollout undo --dry-run", StepResult.PASS, "上一版 2.3.0-139 可回"));
        for (Step s : steps) {
            System.out.printf("  步骤%d %-12s %s%n        命令：%s%n        结果：%s 证据：%s%n",
                    s.no(), s.name(), s.result(), s.command(), s.result(), s.evidence());
        }
        System.out.println();
    }

    static void failedRelease() {
        System.out.println("【一次失败发布的排障记录：v2.4.0-150】");
        record Finding(int step, String symptom, String diagnosis, String rootCause) {
        }
        List<Finding> findings = List.of(
                new Finding(7, "Pod Ready 1/6，一直卡在滚动更新", "kubectl describe -> Readiness probe failed: 404",
                        "探针路径写错（/health 写成 /heath），镜像本身没问题"),
                new Finding(7, "修正探针后仍然 OOMKilled 循环", "kubectl logs -> OutOfMemoryError",
                        "新版本加了缓存但 limits 还是 1Gi，编排问题而非镜像问题"),
                new Finding(7, "调大 limits 后 5xx 飙升", "监控 -> 下单接口 500 激增",
                        "环境问题：新功能依赖的 order_refund 表没在生产执行迁移脚本"));
        for (Finding f : findings) {
            System.out.printf("  症状：%s%n    诊断：%s%n    根因：%s%n",
                    f.symptom(), f.diagnosis(), f.rootCause());
        }
        System.out.println("  最终处置：回滚到 2.3.1-142，三类问题（镜像/编排/环境）分账清楚");
        System.out.println();
    }

    static void rollbackDrill() {
        System.out.println("【回滚演练：把最坏情况变成一条命令】");
        List<String> drill = List.of(
                "kubectl rollout history deploy/order       查看可回滚的版本列表",
                "kubectl rollout undo deploy/order          回到上一版本",
                "kubectl rollout status deploy/order        等待回滚完成（约 20s）",
                "验证：5xx 回落正常，Pod 全部 Ready");
        for (String d : drill) {
            System.out.println("  " + d);
        }
        System.out.println("  演练周期：每个迭代至少演练一次回滚，并记录实际耗时");
        System.out.println("  原则：发布窗口内“回滚优先于排查”，先恢复服务再找根因");
        System.out.println();
    }

    static void checklist() {
        System.out.println("【发布 checklist（贴在流水线模板里）】");
        List<String> items = List.of(
                "镜像 tag 含构建号+提交摘要，可反查源码",
                "多阶段构建，扫描无 CRITICAL 漏洞",
                "冒烟：健康检查 + 核心接口 200",
                "编排：探针路径/端口与代码一致，requests/limits 已评估",
                "环境：数据库迁移脚本已执行，配置中心值已确认",
                "观测：错误率与核心指标面板已打开，阈值告警生效",
                "回滚：undo 命令演练过，上一版本镜像仍在仓库");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        pipeline();
        failedRelease();
        rollbackDrill();
        checklist();
    }
}
```

**课后练习**
1. 按本章九步流程完整发布一个自己的小项目，记录每一步的命令与"通过证据"，形成你自己的发布 checklist。
2. 刻意制造一次发布事故（探针路径错或迁移脚本没跑），走完"观察 -> 诊断 -> 回滚"全流程，把时间线写成一篇复盘。
3. 用 `kubectl set image` 更新时故意复用旧 tag，观察滚动更新"无变化"的现象，解释 imagePullPolicy 的默认行为并给出两种规避方案。
4. 思考题：为什么说"没有演练过回滚的发布不是完整的发布"？从本章失败案例的三类根因出发，说明哪些事故只能靠回滚解决。

## 本章综合练习

### 综合题：一次完整的容器化发布

**场景**

把一个 Java 服务容器化并完成发布：写 Dockerfile、分层缓存优化、多阶段构建瘦身、Compose 编排应用与依赖、端口映射与数据卷、滚动更新与回滚。没有 Docker 环境时用命令序列与状态推演验证。

**任务**
1. 写 Dockerfile：基础镜像选择、依赖层与代码层分离以利用缓存、非 root 用户运行、暴露端口、健康检查。
2. 用多阶段构建把镜像从完整 JDK 瘦身到 JRE 或分层镜像，对比前后体积并说明每一层删掉了什么。
3. 写 docker-compose.yml 编排应用 + 数据库，配置端口映射、数据卷持久化、依赖启动顺序与健康检查。
4. 设计滚动更新方案：新旧版本共存、健康检查通过才切流量、失败自动回滚，写出每一步命令与预期结果。

**验收清单**
- [ ] Dockerfile 改动代码后重建只重跑代码层，依赖层命中缓存。
- [ ] 镜像体积相比单阶段构建下降明显，且运行时不使用 root。
- [ ] Compose 一条命令拉起全部服务，数据卷重建容器后数据不丢。
- [ ] 滚动更新过程中服务不中断（连续请求无 5xx），回滚能在分钟级完成。
