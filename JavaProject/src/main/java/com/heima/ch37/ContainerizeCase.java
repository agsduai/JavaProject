/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-11
 * 课名：综合案例：一次完整的容器化发布
 * ------------------------------------------------------------
 * 知识点：
 *   - **发布全流程九步**：本地验证 → 写 Dockerfile（多阶段）→ 构建镜像并打版本 tag → 推送仓库 → 单容器冒烟 → compose 起全栈联调 → 推到 K8s（改 Deployment 镜像）→ 观察滚动更新与监控 → 验证成功或秒级回滚。每一步都有明确的"通过标准"，失败就停，不带病前进。
 *   - **版本 tag 在构建时定型**：`docker build -t registry/app:2.3.1-142 .`，tag 里带构建号与提交摘要；**同一份产物推同一个 digest**，多环境部署的是同一个 digest，杜绝"测试环境和生产跑的不一样"。
 *   - **冒烟测试先于编排**：单容器 `docker run` 后先 curl 健康检查与核心接口，再进 compose 全栈；编排失败时先分清"镜像坏了"还是"编排配置坏了"，两回事排障路径完全不同。
 *   - **发布窗口的观测三件套**：Pod 就绪数、错误率（5xx 比例）、核心业务指标（下单成功率）。滚动更新期间任何一项劣化，立即回滚 —— 回滚是零成本操作（回到旧 ReplicaSet），而"带病坚持排查"的代价不可控。
 *   - **回滚演练是发布能力的一部分**：`kubectl rollout undo deployment/order` 一条命令回到上一版本；但没有提前演练过回滚的团队，第一次回滚往往发生在深夜事故里。
 *   - **失败案例的三类根因**：镜像问题（依赖缺失、配置硬编码）、编排问题（探针路径错、资源限额太小）、环境问题（数据库未迁移、DNS 不通）。发布 checklist 的意义是把三类问题的检查项前置。
 *   - 易错点：直接在 K8s 里改 Pod（Pod 是临时对象，会被 ReplicaSet 按期望状态拉回，必须改 Deployment）；更新镜像 tag 没变导致"不生效"（tag 相同则不重新拉取，要换 tag 或强制 imagePullPolicy: Always）；发布后不看指标就宣布成功（错误率可能 10 分钟后才起来）；回滚后不查根因（旧版本也在劣化，只是慢）。
 * ============================================================
 */

package com.heima.ch37;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 按本章九步流程完整发布一个自己的小项目，记录每一步的命令与"通过证据"，形成你自己的发布 checklist。
 * 2. 刻意制造一次发布事故（探针路径错或迁移脚本没跑），走完"观察 -> 诊断 -> 回滚"全流程，把时间线写成一篇复盘。
 * 3. 用 `kubectl set image` 更新时故意复用旧 tag，观察滚动更新"无变化"的现象，解释 imagePullPolicy 的默认行为并给出两种规避方案。
 * 4. 思考题：为什么说"没有演练过回滚的发布不是完整的发布"？从本章失败案例的三类根因出发，说明哪些事故只能靠回滚解决。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 把一个 Java 服务容器化并完成发布：写 Dockerfile、分层缓存优化、多阶段构建瘦身、Compose 编排应用与依赖、端口映射与数据卷、滚动更新与回滚。没有 Docker 环境时用命令序列与状态推演验证。
 * 【任务】
 * 1. 写 Dockerfile：基础镜像选择、依赖层与代码层分离以利用缓存、非 root 用户运行、暴露端口、健康检查。
 * 2. 用多阶段构建把镜像从完整 JDK 瘦身到 JRE 或分层镜像，对比前后体积并说明每一层删掉了什么。
 * 3. 写 docker-compose.yml 编排应用 + 数据库，配置端口映射、数据卷持久化、依赖启动顺序与健康检查。
 * 4. 设计滚动更新方案：新旧版本共存、健康检查通过才切流量、失败自动回滚，写出每一步命令与预期结果。
 * 【验收清单】
 * [ ] Dockerfile 改动代码后重建只重跑代码层，依赖层命中缓存。
 * [ ] 镜像体积相比单阶段构建下降明显，且运行时不使用 root。
 * [ ] Compose 一条命令拉起全部服务，数据卷重建容器后数据不丢。
 * [ ] 滚动更新过程中服务不中断（连续请求无 5xx），回滚能在分钟级完成。
 * ============================================================
 */
