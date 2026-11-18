/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-09
 * 课名：镜像瘦身与镜像安全
 * ------------------------------------------------------------
 * 知识点：
 *   - **瘦身的三板斧**：小基础镜像（alpine/distroless）、多阶段构建（编译器与构建产物不进运行镜像）、层内容最小化（同层内下载+清理、.dockerignore 减少上下文与意外文件）。
 *   - **多阶段构建是 Java 镜像的标配**：第一阶段 maven 镜像（~500MB）编译出 jar，第二阶段只 COPY jar 进 JRE 镜像。源码、`.m2` 仓库、maven 本体都不进最终镜像，体积与攻击面同时下降。
 *   - **镜像安全清单**：非 root 用户（`USER 10001`）、只读根文件系统（`--read-only`，可写目录单独挂卷）、最小能力集（`--cap-drop=ALL`）、禁用特权（`--privileged` 绝不上生产）、固定 tag/digest、定期重建镜像吃安全更新（"基础镜像有 CVE 就全量重构建"）。
 *   - **镜像扫描**：`docker scout` / trivy 按 CVE 库扫描镜像层，输出高危漏洞与修复版本。扫描要进 CI 门禁：高危漏洞阻断发布，而不是"扫完存档"。
 *   - **`docker history` 是审计工具**：每层对应一条指令，密码、密钥一旦被 COPY/RUN 进层里，删除文件也还在历史层里 —— 泄露后唯一正确的处置是**换密钥**，而不是"删镜像重传"。
 *   - **瘦身不是目的，交付一致性才是**：镜像从 900MB 到 90MB，pull 时间从 1 分钟到 6 秒，发布速度与带宽成本同步下降；但为省 10MB 换来调试能力全无（distroless 无 shell）需要评估团队排查手段。
 *   - **镜像标签规范**：`应用名:语义化版本-构建号-短摘要`（如 `order:2.3.1-142-a1b2c3d`），可追溯到源码提交；`latest` 只在开发环境用。
 *   - 易错点：把 `.git`、密钥、`node_modules` 全量 COPY 进镜像（体积大 + 密钥泄露）；alpine 里装 glibc 才能跑的 JVM（temurin 已提供 alpine 版，不要自己拼）；扫描工具只扫一次就归档（CVE 库天天更新）；用 root 跑应用（逃逸后直接拥有宿主机 root）。
 * ============================================================
 */

package com.heima.ch37;

import java.util.List;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用多阶段构建重构你现有的 Dockerfile，记录优化前后的镜像大小与 `docker history` 层数对比。
 * 2. 安装 trivy（或使用 `docker scout`）扫描自己的应用镜像，把 CRITICAL 漏洞的处理过程写成一段记录（修复/缓解/豁免，各附理由）。
 * 3. 让应用以 `USER 10001 + --read-only` 运行，找到所有需要单独挂可写卷的路径（日志、临时文件），并记录调试过程。
 * 4. 思考题：为什么"镜像里有密钥"的正确处置是换密钥而不是删层？从层的不可变性与镜像分发机制推导结论。
 * ============================================================
 */
