/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-07
 * 课名：Docker Compose 与多容器编排
 * ------------------------------------------------------------
 * 知识点：
 *   - **Compose 解决的问题是"本地/单机环境的一致性"**：一个 `compose.yaml` 声明全部服务、网络、卷、环境变量，`docker compose up -d` 一条命令把整个应用栈拉起来，`down` 整体拆除。它把"README 里 15 条启动命令"变成一份可版本化的文件。
 *   - **服务 = 同一镜像的运行模板**：`services:` 下每一项可指定 `image` 或 `build:`（有 build 时 compose 会替你执行 docker build），`ports`、`volumes`、`environment`、`depends_on` 都写在服务条目里。
 *   - **depends_on 只管启动顺序，不管就绪**：`depends_on: [db]` 保证 db 容器**先启动**，但数据库"能接受连接"要靠 `healthcheck` + `condition: service_healthy`。应用比数据库先就绪是"本地第一次 up 必失败"的经典原因。
 *   - **项目网络与名字解析**：compose 默认创建 `<project>_default` 自定义网络，服务名即 DNS 名，应用配置里写 `jdbc:mysql://db:3306` 即可。**配置里的主机名必须等于服务名**，这是 compose 化改造最常见的断点。
 *   - **改动即增量**：修改单个服务后 `docker compose up -d app` 只重建该服务，其他服务不动；`--build` 强制重新构建镜像。`down -v` 会连卷一起删（危险命令，本地才用）。
 *   - **环境变量注入**：`${VAR}` 从 `.env` 文件或 shell 读入，区分开发/测试配置；敏感信息不进 yaml，用 secrets 或外部配置中心。
 *   - **Compose 不是生产编排**：它没有自愈、没有滚动更新、没有跨机调度。单机小系统可以用，微服务集群要交给 K8s —— 但 compose 的服务定义迁移成本很低。
 *   - 易错点：把 `ports` 和 `expose` 混用错（ports 才发布到宿主机）；depends_on 后应用还是连不上（缺 healthcheck 条件）；`.env` 提交进仓库带出真实密码；用 `restart: always` 掩盖应用启动失败（容器在 crash loop，日志里全是堆栈）。
 * ============================================================
 */

package com.heima.ch37;

import java.util.List;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为「Spring Boot 应用 + MySQL + Redis」写出完整 compose.yaml，要求 db 带健康检查、app 依赖 healthy，本地真实跑通并截图启动时序。
 * 2. 故意去掉 `condition: service_healthy`，观察 app 的启动失败与重启行为，然后用 `docker compose logs` 找到连接拒绝的证据。
 * 3. 把数据库密码移入 `.env` 并确认 `.env` 不进 git；解释为什么密码写死在 compose.yaml 与写死在 Dockerfile 同样危险。
 * 4. 思考题：`docker compose down` 与 `down -v` 在"第二天来继续开发"场景下分别意味着什么？写出你的本地环境保护策略。
 * ============================================================
 */
