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
