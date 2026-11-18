/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-10
 * 课名：Kubernetes 核心概念入门
 * ------------------------------------------------------------
 * 知识点：
 *   - **K8s 管的是"期望状态"而不是"过程"**：你声明"order 服务要有 3 个副本、用 2.3.1 镜像"，控制器循环（Reconcile Loop）持续比对期望与实际，少了就补、多了就删、不健康就重建。这是它与 compose 最本质的区别：**自愈与收敛**。
 *   - **Pod 是最小调度单元**：一个 Pod = 一个或多个共享网络/存储的容器（共用 localhost 与卷），要么整体一起调度。**不要在 Pod 里放互不相关的容器**，Pod 通常"一个主容器 + 可选 Sidecar"。
 *   - **Deployment 管 ReplicaSet 管 Pod**：Deployment 声明副本数与滚动更新策略，ReplicaSet 负责维持副本数量，Pod 才是真正跑的。回滚 = 回退到上一个 ReplicaSet，秒级完成。
 *   - **滚动更新参数**：`maxSurge`（最多多起几个新副本）与 `maxUnavailable`（最多少几个旧副本），两者决定更新过程中容量曲线。想"零损失发布"就 maxUnavailable=0 + maxSurge=1：先起新的，就绪后再杀旧的。
 *   - **探针三件套**：`startupProbe`（应用启动慢的保护，启动成功前不跑其他探针）、`readinessProbe`（不就绪就摘出 Service，不接流量但**不重启**）、`livenessProbe`（失败即重启容器，阈值太敏感会造成重启风暴）。探针失败是"接口 502 但容器还在跑"的最常见解释。
 *   - **Service 是稳定的虚拟 IP + 负载均衡**：Pod IP 随时生灭，Service 用 label selector 圈住一组 Pod，提供稳定 ClusterIP 与 DNS 名（`svc.namespace.svc.cluster.local`）。`NodePort/LoadBalancer` 是对外暴露的进阶形式。
 *   - **ConfigMap 与 Secret**：配置与镜像分离 —— ConfigMap 放普通配置，Secret 放敏感信息（base64 编码不是加密，权限控制才是关键），都可作为环境变量或文件挂载进 Pod。
 *   - **资源请求与限额**：`requests` 是调度依据（节点至少留这么多），`limits` 是运行上限；CPU 超 limit 被节流，内存超 limit 被 OOMKilled。**不配 requests 的 Pod 调度是赌博**，节点超卖会让所有服务一起抖动。
 *   - 易错点：liveness 探针指向依赖数据库的接口（数据库抖一下全集群重启）；镜像 tag 用 latest 且 imagePullPolicy 默认（K8s 不重新拉取，更新"不生效"）；Pod 里跑多个不相干进程（无法独立扩缩容与重启）；把配置写死在镜像里换环境就重新构建（应用 ConfigMap 分离）。
 * ============================================================
 */

package com.heima.ch37;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 minikube/kind 起一个本地集群，部署 3 副本 Deployment，手动删除一个 Pod 观察 ReplicaSet 自动补齐，记录事件输出。
 * 2. 修改 liveness 探针为一个必然失败的路径，观察重启风暴与退避间隔，然后按"探针分工"改成合理配置。
 * 3. 做一次真实滚动更新（v1 -> v2），期间用循环脚本请求 Service，验证 maxUnavailable=0 时无请求失败；再制造一个就绪失败的 v3，观察更新卡住与自动回滚。
 * 4. 思考题：compose 的 depends_on 与 K8s 的"不保证启动顺序" philosophic 冲突在哪？在 K8s 里应用应如何实现"等数据库就绪"？（提示：重试 + readiness）
 * ============================================================
 */
