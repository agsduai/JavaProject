/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-03
 * 课名：Dockerfile 与构建过程
 * ------------------------------------------------------------
 * 知识点：
 *   - **Dockerfile 是一段"从头构建环境的脚本"，每条指令在前一层之上执行**：FROM 选基座，RUN 执行命令产生新层，COPY/ADD 把构建上下文里的文件放进镜像，ENV/WORKDIR 改环境，EXPOSE 只是文档声明，CMD/ENTRYPOINT 定启动命令。
 *   - **构建上下文不是 Dockerfile 目录本身**：`docker build .` 会把 `.` 整个目录打包发给守护进程，`.dockerignore` 排除的文件不进上下文 —— 上下文里有 1GB 日志，构建就会先传 1GB。
 *   - **COPY 与 ADD 的分工**：COPY 只做复制，语义清晰；ADD 额外支持远程 URL 与本地 tar 自动解压。**规范要求优先 COPY**，ADD 只在"必须自动解压 tar"时使用。
 *   - **CMD 与 ENTRYPOINT 的区别**：ENTRYPOINT 是"这个容器天生要跑的命令"，CMD 是它的默认参数；两者都写时，CMD 的内容作为参数追加到 ENTRYPOINT 后面。`docker run 镜像 参数` 会覆盖 CMD 而不覆盖 ENTRYPOINT，这正是"镜像默认跑主程序、又能临时换个命令调试"的实现基础。
 *   - **构建缓存的判定**：从前往后逐条比对，父层 ID 相同且指令文本未变才命中；COPY/ADD 还要求文件内容的校验和未变。任何一条失配，**其后所有层全部重算** —— 这就是"指令顺序 = 缓存策略"的原因。
 *   - **Java 项目标准顺序**：先 COPY `pom.xml`（或分层 jar 的依赖层）→ 下载依赖 → 再 COPY 源码 → 编译。源码天天变，依赖月月变，把易变的放后面才能吃到缓存。
 *   - **多阶段构建**：第一阶段用带编译器的镜像构建产物，第二阶段只 COPY 产物进最小运行镜像，编译器、源码、构建缓存都不进最终镜像。
 *   - 易错点：在 Dockerfile 里硬编码密码（会永远留在层里，`docker history` 可见，应传 ARG/secret 或挂载）；用 `RUN cd /app && ...` 而不是 WORKDIR（cd 不跨指令生效）；把 ENTRYPOINT 写成 `["sh", "-c", "java ..."]` 导致 SIGTERM 发给了 sh 而不是 java，容器优雅停机失效。
 * ============================================================
 */

package com.heima.ch37;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为一个 Maven 项目写出"缓存友好"的完整 Dockerfile，并解释每条指令为什么放在那个位置。
 * 2. 用 `--entrypoint sh` 进入一个正在运行的镜像排查环境问题，退出后解释 ENTRYPOINT 与 CMD 的覆盖规则各是什么。
 * 3. 把 `ENTRYPOINT ["sh", "-c", "java -jar app.jar"]` 改成 exec 形式，解释为什么原写法会让 `docker stop` 等满 10 秒后才 SIGKILL。
 * 4. 思考题：`.dockerignore` 里应该排除哪些文件？（提示：`.git`、`target`、日志、密钥文件——各说出一个后果）
 * ============================================================
 */
