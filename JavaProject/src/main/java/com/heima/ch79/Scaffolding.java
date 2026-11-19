/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-02
 * 课名：脚手架与模板工程
 * ------------------------------------------------------------
 * 知识点：
 *   - **脚手架的价值不是"省几行代码"，而是"把最佳实践固化"**：**目录结构、依赖版本、日志/配置/健康检查、CI 文件**一次生成，**新服务天然合规**（79-09 的 Golden Path）。
 *   - **模板三要素**：**参数**（服务名/包名/owner）、**占位符**（`${serviceName}`）、**条件块**（是否需要数据库/缓存）——**参数校验在生成前完成**（缺参数直接报错，不生成半成品）。
 *   - **模板的两种形态**：**文件模板**（整个工程目录 + 占位符替换）与**代码模板**（生成单个类/方法）——**脚手架用前者**，IDE 用后者。
 *   - **生成即校验**：**生成后编译/单测必须通过**（否则模板腐烂没人用）——**模板要纳入 CI**（定期生成一个样例工程并构建）。
 *   - 与 71 章的关系：71 章低代码是"元数据驱动运行时"，**脚手架是"元数据驱动代码生成"**——**一个生成配置，一个生成代码**。
 *   - 易错点：模板里写死版本（升级要改所有生成物）；不校验参数（生成非法包名）；模板不维护（生成物编译不过）；生成后不注册到服务目录（找不到 owner）。
 * ============================================================
 */

package com.heima.ch79;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class Scaffolding {
    record TemplateFile(String path, String content) {
    }

    record Request(String serviceName, String packageName, String owner,
                   boolean withDatabase, boolean withCache) {
    }

    static List<String> validate(Request req) {
        List<String> errors = new ArrayList<>();
        if (!req.serviceName().matches("^[a-z][a-z0-9-]{2,30}$")) {
            errors.add("serviceName 必须是 3~31 位小写字母/数字/连字符");
        }
        if (!req.packageName().matches("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$")) {
            errors.add("packageName 不是合法 Java 包名");
        }
        if (req.owner() == null || req.owner().isBlank()) {
            errors.add("owner 必填（服务目录需要责任人）");
        }
        return errors;
    }

    static Map<String, String> render(Request req) {
        Map<String, String> files = new LinkedHashMap<>();

        files.put("pom.xml", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>${serviceName}</artifactId>
                  <version>0.1.0-SNAPSHOT</version>
                </project>
                """.replace("${serviceName}", req.serviceName()));

        String pkgPath = req.packageName().replace('.', '/');
        files.put("src/main/java/" + pkgPath + "/Application.java", """
                package ${packageName};

                public class Application {
                    public static void main(String[] args) {
                        System.out.println("${serviceName} started, owner=${owner}");
                    }
                }
                """.replace("${packageName}", req.packageName())
                .replace("${serviceName}", req.serviceName())
                .replace("${owner}", req.owner()));

        files.put("src/main/resources/application.yml", """
                app:
                  name: ${serviceName}
                  owner: ${owner}
                ${databaseBlock}${cacheBlock}""".replace("${serviceName}", req.serviceName())
                .replace("${owner}", req.owner())
                .replace("${databaseBlock}", req.withDatabase()
                        ? "  datasource:\n    url: jdbc:mysql://localhost:3306/" + req.serviceName() + "\n"
                        : "")
                .replace("${cacheBlock}", req.withCache()
                        ? "  cache:\n    type: redis\n"
                        : ""));

        files.put("README.md", """
                # ${serviceName}

                - owner: ${owner}
                - 由脚手架生成，遵循团队 Golden Path
                """.replace("${serviceName}", req.serviceName())
                .replace("${owner}", req.owner()));

        files.put(".github/workflows/ci.yml", """
                name: ci
                on: [push]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - run: mvn -q verify
                """);

        return files;
    }

    public static void main(String[] args) {
        System.out.println("【参数校验】");
        Request bad = new Request("X", "com.1bad", "", false, false);
        List<String> errors = validate(bad);
        System.out.println("  非法请求：" + errors);

        System.out.println();
        Request good = new Request("order-service", "com.example.order", "交易团队",
                true, true);
        List<String> ok = validate(good);
        System.out.println("  合法请求校验结果：" + (ok.isEmpty() ? "通过" : ok));

        System.out.println();
        System.out.println("【生成文件】");
        Map<String, String> files = render(good);
        files.keySet().forEach(p -> System.out.println("  " + p));

        System.out.println();
        System.out.println("【application.yml】");
        System.out.println(files.get("src/main/resources/application.yml"));

        System.out.println("【生成后动作】");
        String[][] steps = {
                {"编译", "mvn -q verify", "模板不腐烂的底线"},
                {"注册", "写入服务目录", "owner/分级/文档（79-09）"},
                {"建流水线", "套用流水线模板", "79-07"},
                {"建环境", "按需创建 dev", "79-06"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-8s %-22s %s%n", s[0], s[1], s[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 设计一个脚手架参数（服务名/包名/owner/是否用数据库），实现参数校验与三个文件的生成。
 * 2. 给模板加"条件块"（有缓存才生成 Redis 配置），验证开关生效。
 * 3. 思考题：为什么"模板必须纳入 CI 定期生成并编译"？从"模板腐烂"与"开发者信任"分析。
 * ============================================================
 */
