/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-09
 * 课名：打包与部署
 * ------------------------------------------------------------
 * 知识点：
 *   - 打包：`mvn clean package` 产出 `app.jar`。Spring Boot 的 fat jar 把依赖一起打进去，`java -jar app.jar` 直接跑；thin jar 需要配合 `lib/` 目录和 `-Dloader.path`。
 *   - 配置外部化优先级（从高到低）：命令行参数 > 环境变量 > `application-{profile}.yml` > `application.yml` > 代码默认值。生产环境用环境变量注入密码，绝不写进 jar。
 *   - `profile` 隔离：`application-dev.yml` / `application-test.yml` / `application-prod.yml`，启动时 `--spring.profiles.active=prod`。
 *   - JVM 参数：`-Xms` 与 `-Xmx` 设成一样避免堆反复扩缩；容器里用 `-XX:MaxRAMPercentage=75` 而不是写死 `-Xmx2g`（否则容器限制 1G、JVM 却按 2G 算，直接被 OOMKill）。
 *   - 优雅停机：`server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase=30s`，先停止接收新请求，等在处理的请求完成再退出。没有优雅停机，发版时正在下单的用户会收到 502。
 *   - 启动脚本要做的：切到 jar 目录、设置 JVM 参数、`nohup` 后台运行、写 pid 文件、日志重定向、`start/stop/restart/status` 四个子命令。
 *   - 部署顺序：先起新实例 -> 等健康检查通过 -> 从负载均衡摘旧实例 -> 停旧实例（滚动发布），而不是"先停后起"。
 *   - 时区与端口：容器默认 UTC，`-Duser.timezone=Asia/Shanghai` 否则时间差 8 小时；端口冲突时用 `--server.port`。
 *   - 易错：jar 里的 `application-prod.yml` 存着数据库密码（能直接被 `unzip` 出来）；`nohup` 没重定向导致 `nohup.out` 撑爆磁盘；健康检查路径被鉴权拦截器挡住，导致探针永远失败。
 * ============================================================
 */

package com.heima.ch27;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class Deployment {
    static final long START_AT = System.currentTimeMillis();

    public static void main(String[] args) {
        System.out.println("=== 构建产物 ===");
        System.out.println("  target/app-1.0.0.jar      可执行 fat jar（含依赖）");
        System.out.println("  target/app-1.0.0.jar.original  原始 thin jar");
        System.out.println("  验证: unzip -l app.jar | grep BOOT-INF/lib | wc -l  -> 依赖个数");

        System.out.println();
        System.out.println("=== 配置优先级（高 -> 低）===");
        Map<String, String> conf = new LinkedHashMap<>();
        conf.put("1. 命令行参数", "--server.port=8081");
        conf.put("2. 环境变量", "SPRING_DATASOURCE_PASSWORD=xxx");
        conf.put("3. profile 文件", "application-prod.yml");
        conf.put("4. 默认文件", "application.yml");
        conf.put("5. 代码默认值", "@Value 的默认值");
        conf.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));
        System.out.println("  密码只走环境变量，jar 里不放明文");

        System.out.println();
        System.out.println("=== 启动命令 ===");
        String cmd = "java -Xms512m -Xmx512m -XX:MaxRAMPercentage=75 "
                + "-Duser.timezone=Asia/Shanghai "
                + "-jar app.jar --spring.profiles.active=prod --server.port=8080";
        System.out.println("  " + cmd);

        System.out.println();
        System.out.println("=== 启动脚本 deploy.sh ===");
        for (String line : script().split("\n")) {
            System.out.println("  " + line);
        }

        System.out.println();
        System.out.println("=== 健康检查（真实起一个 HTTP 服务验证）===");
        healthCheckDemo();

        System.out.println();
        System.out.println("=== 滚动发布顺序 ===");
        String[] steps = {
                "1. 新实例启动，注册到负载均衡（此时不接流量）",
                "2. 轮询 /actuator/health/readiness 直到 UP",
                "3. 负载均衡开始把流量分给新实例",
                "4. 旧实例从负载均衡摘除（不再有新请求）",
                "5. 等待 30s 让在处理的请求跑完（优雅停机）",
                "6. 旧实例退出，循环处理下一台",
        };
        for (String s : steps) {
            System.out.println("  " + s);
        }
    }

    static String script() {
        return """
                #!/bin/bash
                APP_NAME=app
                JAR=$APP_NAME.jar
                PID_FILE=$APP_NAME.pid
                LOG_FILE=logs/$APP_NAME.out
                JVM_OPTS="-Xms512m -Xmx512m -XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai"

                start() {
                  if [ -f $PID_FILE ] && kill -0 $(cat $PID_FILE) 2>/dev/null; then
                    echo "$APP_NAME already running, pid=$(cat $PID_FILE)"; exit 0
                  fi
                  mkdir -p logs
                  nohup java $JVM_OPTS -jar $JAR --spring.profiles.active=prod \\
                        > $LOG_FILE 2>&1 &
                  echo $! > $PID_FILE
                  echo "started pid=$(cat $PID_FILE)"
                }

                stop() {
                  if [ ! -f $PID_FILE ]; then echo "not running"; exit 0; fi
                  PID=$(cat $PID_FILE)
                  kill $PID                       # SIGTERM -> 触发优雅停机
                  for i in $(seq 1 30); do
                    kill -0 $PID 2>/dev/null || break
                    sleep 1
                  done
                  kill -0 $PID 2>/dev/null && kill -9 $PID
                  rm -f $PID_FILE
                  echo "stopped"
                }

                case "$1" in
                  start) start ;;
                  stop) stop ;;
                  restart) stop; start ;;
                  status) [ -f $PID_FILE ] && kill -0 $(cat $PID_FILE) 2>/dev/null \\
                            && echo "running" || echo "not running" ;;
                  *) echo "usage: $0 {start|stop|restart|status}"; exit 1 ;;
                esac
                """;
    }

    static void healthCheckDemo() {
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/actuator/health", exchange -> {
                byte[] body = ("{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"},"
                        + "\"redis\":{\"status\":\"UP\"}},\"uptimeMs\":" + uptime() + "}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            server.createContext("/actuator/health/readiness", exchange -> {
                // 模拟：DB 正常 -> UP；DB 挂 -> OUT_OF_SERVICE (503)
                boolean dbUp = true;
                int status = dbUp ? 200 : 503;
                byte[] body = ("{\"status\":\"" + (dbUp ? "UP" : "OUT_OF_SERVICE") + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            server.setExecutor(null);
            server.start();
            int port = server.getAddress().getPort();
            String base = "http://127.0.0.1:" + port;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            for (String path : new String[]{"/actuator/health", "/actuator/health/readiness"}) {
                HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                        .timeout(Duration.ofSeconds(2)).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                System.out.println("  GET " + path + " -> " + resp.statusCode() + " " + resp.body());
            }
        } catch (Exception e) {
            System.out.println("  当前环境无法监听本地端口，改用模拟输出: " + e.getClass().getSimpleName());
            System.out.println("  GET /actuator/health           -> 200 {\"status\":\"UP\",\"db\":\"UP\",\"redis\":\"UP\"}");
            System.out.println("  GET /actuator/health/readiness -> 200 {\"status\":\"UP\"}");
        } finally {
            if (server != null) {
                server.stop(0);
                System.out.println("  服务已优雅关闭");
            }
        }
    }

    static long uptime() {
        return System.currentTimeMillis() - START_AT;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为什么容器里推荐 `-XX:MaxRAMPercentage=75` 而不是写死 `-Xmx2g`？如果容器内存限制 1G 而 JVM 配了 `-Xmx2g`，会发生什么？
 * 2. 优雅停机解决了什么问题？如果没有它，发版瞬间正在下单的用户会看到什么？
 * 3. 把数据库密码写进 `application-prod.yml` 打进 jar，攻击者怎么拿到？给出三种更安全的做法。
 * ============================================================
 */
