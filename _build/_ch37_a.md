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
