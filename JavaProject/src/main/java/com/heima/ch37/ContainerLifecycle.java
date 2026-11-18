/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-04
 * 课名：容器的生命周期
 * ------------------------------------------------------------
 * 知识点：
 *   - **容器的状态是一条状态机**：`created → running → paused → exited → removed`，其中 running 时 Docker 还维护一个 health 状态（starting/healthy/unhealthy）用于编排决策。`docker start` 是从 created/exited 回 running，`docker rm` 只能删已停止的容器（强制加 `-f`）。
 *   - **退出码是最重要的排障入口**：0 表示进程正常退出；137 = 128+9，SIGKILL，通常是内存超限被内核 OOM killer 杀掉；143 = 128+15，SIGTERM，是 `docker stop` 的默认动作；126/127 是命令不可执行/不存在；125 是 Docker 自身错误。看到 137 先查内存限额，看到 143 先确认应用是否优雅停机。
 *   - **`docker stop` 的 10 秒宽限期**：先发 SIGTERM 给 1 号进程，超过 `--time`（默认 10s）才 SIGKILL。应用必须捕获 SIGTERM 并触发优雅停机（处理完在途请求、关闭连接池），否则要么被硬杀丢请求，要么白白等 10 秒。
 *   - **重启策略决定"挂了怎么办"**：`no`（默认）、`on-failure[:N]`（非零退出才重启，最多 N 次）、`always`（只要不是手工 stop 就拉起）、`unless-stopped`（同 always，但手工 stop 后重启宿主机也不拉起）。策略是容器属性，改策略要重建容器。
 *   - **崩溃循环（crash loop）**：配置错误的应用每次启动即退出，always 策略会让它无限拉起。要有启动次数/退避机制，并靠健康检查+告警发现"容器在 running 但一直不就绪"。
 *   - **健康检查**：HEALTHCHECK 定期执行探针命令，连续 `--retries` 次失败转 unhealthy。健康检查失败**不会**自动重启容器（Docker 单机如此；K8s 的 liveness 才会重启），它只是把状态暴露给 `docker ps` 与编排系统。
 *   - **paused 状态**：`docker pause` 冻结进程（cgroup freezer），用于 CPU 满载时临时让路，不是停机，内存还占着。
 *   - 易错点：把 `docker restart` 当修复手段（不查退出码，容器带病循环重启）；用 `docker kill` 代替 `stop`（跳过优雅停机）；健康检查命令写得不对导致永远 healthy（例如用 curl 检查但没检查返回码）；1 号进程不转发信号，stop 永远超时。
 * ============================================================
 */

package com.heima.ch37;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 制造三种真实退出：容器内存限额 64M 后分配大数组（137）、ENTRYPOINT 指向不存在的脚本（127）、应用主动 System.exit(0)（0），记录 `docker inspect` 的 `State.OOMKilled` 字段。
 * 2. 写一个带 SIGTERM 钩子的 Java 程序（`Runtime.getRuntime().addShutdownHook`），打包进镜像，`docker stop` 验证能在 1 秒内优雅退出，并解释 exec 形式与 shell 形式的差别。
 * 3. 分别用 `no`、`on-failure:3`、`always` 跑同一个秒退镜像，观察 `docker ps -a` 的差异，写出三种策略各自适用的场景。
 * 4. 思考题：为什么单机 Docker 的健康检查失败不会重启容器？这与 K8s 的 liveness/readiness 探针的行为差异意味着什么？
 * ============================================================
 */
