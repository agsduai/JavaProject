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
