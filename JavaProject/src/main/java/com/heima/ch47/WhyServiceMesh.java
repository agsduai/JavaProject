/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：47-01
 * 课名：从 K8s 到 Service Mesh
 * ------------------------------------------------------------
 * 知识点：
 *   - **K8s 解决了"容器怎么跑"，没解决"服务之间怎么通话"**：重试、超时、熔断、灰度、mTLS 这些治理逻辑散落在每个服务的 SDK 里——**每种语言、每个版本各实现一遍，质量参差**。Service Mesh 把治理逻辑从业务代码抽走，下沉到基础设施层。
 *   - **Mesh 的两层结构**：**数据面**（每个 Pod 旁的代理，转发与治理，如 Envoy）+ **控制面**（下发策略与配置，如 Istiod）。业务代码只管业务，流量治理全部由 Sidecar 执行——**Java 团队不再维护"重试 SDK"**。
 *   - **Mesh 解决的真实痛点**：多语言治理统一（Java/Go/Python 一致行为）；治理逻辑升级不用改业务代码（升级 Sidecar 即全网生效）；流量拓扑自动发现（依赖地图）；mTLS 自动化（证书轮换无人值守）。
 *   - **Mesh 的代价要先看清**：每跳多两代理（延迟 +0.5~2ms）、每 Pod 多一份资源（Sidecar 内存 50~100MB）、运维复杂度上升（控制面本身要高可用）。**服务数量少于 20 个、单语言团队，Mesh 可能是过度设计**——Spring Cloud SDK 方案足够。
 *   - **接入的渐进路径**：先观察（Sidecar 只透传不治理）-> 再治理（重试/超时/灰度策略接管）-> 再安全（mTLS strict）——**逐步收权，随时可退**（41 章灰度思想的基础设施版）。
 *   - **Java 生态的关系**：Spring Cloud（SDK 治理）与 Mesh（Sidecar 治理）是竞争关系——混用时功能重复（SDK 的熔断 + Sidecar 的重试叠加）。**选 Mesh 后 SDK 治理功能应关闭**，避免双重重试放大故障。
 *   - 易错点：SDK 与 Sidecar 双重重试（重试风暴放大，29 章教训）；把 Mesh 当万能（治理问题先问是不是代码 bug）；全量接入不留观察期（问题定位难度陡增）；小规模团队上 Mesh（运维成本大于收益）。
 * ============================================================
 */

package com.heima.ch47;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WhyServiceMesh {
    record Concern(String concern, String sdkWay, String meshWay) {
    }

    static void beforeAfter() {
        System.out.println("【治理逻辑：SDK 时代 vs Mesh 时代】");
        List<Concern> concerns = List.of(
                new Concern("重试/超时", "每个服务配 Feign/Resilience4j", "VirtualService 策略，全网统一"),
                new Concern("熔断降级", "Resilience4j 代码级配置", "Sidecar 异常检测自动熔断"),
                new Concern("灰度发布", "网关 + 彩蛋逻辑各自实现", "按 header/比例切流，配置即生效"),
                new Concern("mTLS", "自管证书，轮换脚本", "自动签发轮换，业务无感"),
                new Concern("依赖拓扑", "手工维护文档", "遥测自动生成服务图"));
        for (Concern c : concerns) {
            System.out.printf("  %-8s SDK：%-30s Mesh：%s%n", c.concern(), c.sdkWay(), c.meshWay());
        }
        System.out.println();
    }

    static void layered() {
        System.out.println("【Mesh 两层结构】");
        Map<String, String> layers = new LinkedHashMap<>();
        layers.put("数据面", "Envoy Sidecar：拦截进出 Pod 的流量，执行转发/重试/灰度/mTLS");
        layers.put("控制面", "Istiod：把策略翻译成 Envoy 配置下发；证书签发；服务发现聚合");
        layers.put("业务 Pod", "只有业务容器 + Sidecar 容器，业务代码零治理代码");
        for (Map.Entry<String, String> e : layers.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  关键理解：治理逻辑是“配置”而不是“代码”——改策略不重启业务");
        System.out.println();
    }

    static void adoptionCurve() {
        System.out.println("【渐进接入：三阶段收权】");
        String[][] phases = {
                {"阶段1 观察", "Sidecar 透传，采集拓扑与延迟基线", "风险为零，产出决策数据"},
                {"阶段2 治理", "重试/超时/灰度接管（SDK 同类功能关闭）", "先在非核心服务验证"},
                {"阶段3 安全", "mTLS PERMISSIVE -> STRICT，零信任落地", "证书体系稳定后切"},
        };
        for (String[] p : phases) {
            System.out.printf("  %-10s %-34s %s%n", p[0], p[1], p[2]);
        }
        System.out.println("  每阶段有回退开关：Sidecar 注入按命名空间灰度");
        System.out.println();
    }

    static void sdkConflict() {
        System.out.println("【双重重试：混用的放大效应】");
        int retriesSdk = 3, retriesMesh = 3;
        int worst = retriesSdk * retriesMesh;
        System.out.printf("  SDK 重试 %d 次 × Sidecar 重试 %d 次 = 最多 %d 次真实请求%n",
                retriesSdk, retriesMesh, worst);
        System.out.println("  下游本来就过载：9 次重试把它彻底打穿（29 章重试风暴）");
        System.out.println("  规则：接入 Mesh 后关闭 SDK 层重试/熔断，治理只留一处");
        System.out.println("  演练：故意下游 500，观察双重重试的请求放大曲线");
    }

    public static void main(String[] args) {
        beforeAfter();
        layered();
        adoptionCurve();
        sdkConflict();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 盘点你团队 SDK 里的治理功能清单（重试/熔断/灰度），评估"Mesh 接管后需要关闭哪些"，写出冲突矩阵。
 * 2. 设计三阶段接入计划：选一个非核心服务走完观察->治理->mTLS 全流程，记录每阶段的验证证据。
 * 3. 思考题：为什么"服务少于 20 个、单语言团队"不上 Mesh？从运维成本与治理收益的交点分析。
 * ============================================================
 */
