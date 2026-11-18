### 53-10 Spring Cloud vs Spring Cloud Alibaba 对照

**知识点**
- **两套栈的组件对照表**（29 章抽象的两侧实现）：Eureka/ZK/Consul vs Nacos；Hystrix/Resilience4j vs Sentinel；Ribbon/LoadBalancer vs Dubbo LB；Zuul/Gateway vs SCG；Config vs Nacos Config；Feign vs Dubbo/Feign——**接口语义相同，实现与运维特性不同**。
- **运维特性差异**：Nacos 一体化（注册+配置一个控制台）vs 分离组件（Eureka+Config+Bus 组合）；Sentinel 控制台实时规则 vs Resilience4j 代码级；SkyWalking vs Zipkin+Grafana 组合。**运维集成度是 SCA 的显著优势**（51-10 成本的运维税更低）。
- **迁移的真实成本**：SC -> SCA：注册中心替换（客户端依赖+注解）+ 熔断替换 + 网关替换——**接口抽象层做得好则成本低**（49-04 端口适配器：业务依赖抽象接口，实现可换）；没有抽象层的系统迁移成本 = 重构。
- **选型建议**：国内业务 + 团队熟悉阿里生态 + 需要 Nacos/Sentinel 一体化 -> SCA；多云/出海/强 OTel 栈 -> 通用 SC + 自选组件；**两者混用要谨慎**（同一服务注册两套注册中心的治理混乱）。
- 易错点：混用两套注册中心；把 SCA 当"必须的"（29 章抽象层 + 任意实现都行）；对照表死记硬背（理解抽象接口即可推演）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ScVsSca {
    record Pair(String concern, String sc, String sca, String diffNote) {
    }

    static void compare() {
        System.out.println("【组件对照：同一抽象，两侧实现】");
        List<Pair> pairs = List.of(
                new Pair("注册发现", "Eureka/Consul", "Nacos", "Nacos 一体化注册+配置+保护阈值"),
                new Pair("配置", "Config+Bus", "Nacos Config", "Nacos 长轮询免 Bus"),
                new Pair("熔断", "Resilience4j", "Sentinel", "Sentinel 控制台+热点+持久化"),
                new Pair("网关", "SC Gateway", "SC Gateway", "相同；SCG 常与 Sentinel 联动"),
                new Pair("RPC", "OpenFeign", "Dubbo/Feign", "Dubbo 长连接性能+泛化调用"),
                new Pair("追踪", "Zipkin/OTel", "SkyWalking", "SkyWalking 零侵入+拓扑+剖析"));
        for (Pair p : pairs) {
            System.out.printf("  %-8s SC=%-16s SCA=%-14s 差异：%s%n",
                    p.concern(), p.sc(), p.sca(), p.diffNote());
        }
        System.out.println();
        System.out.println("  结论：抽象接口（29 章）掌握后，两侧实现可互换、可混选、可迁移");
    }

    public static void main(String[] args) {
        compare();
    }
}
```

**课后练习**
1. 为六个关注点各写一段"切换实现"的迁移清单（代码改动面/配置改动面/风险）。
2. 检查你的项目是否存在抽象层：把熔断接口抽象出来（49-04），验证 Resilience4j 与模拟 Sentinel 实现可互换。
3. 思考题：为什么"混用两套注册中心"是治理灾难？从服务发现的唯一事实源与排障路径分析。

---

### 53-11 综合案例：阿里栈微服务上线检查单

**知识点**
- **上线检查单（全部组件生产化动作的汇总）**：版本矩阵对齐（53-01）→ Nacos 集群与保护阈值（53-02）→ 配置灰度与审计（53-03）→ Dubbo 超时重试与幂等（53-04）→ Sentinel 规则持久化与压测校准（53-05）→ Seata 模式选型与 undo_log（53-06）→ 网关鉴权与头清理（53-07）→ SkyWalking 完整性巡检（53-08）→ 接入顺序回退预案（53-09）。
- **验收证据三件套**：功能证据（组件行为演示）、压测证据（限流/熔断触发实测）、故障演练证据（摘节点/配置回滚/Seata 回滚）——**41 章发布门禁的生态版**。
- **常见上线事故清单**：版本错配（发现为空）、规则未持久化（重启清零）、Seata 超时（长业务回滚失败）、网关头伪造、Agent 静默失效——**每条都有前置检查项**。
- 易错点：检查单一次性使用（应进 CI/变更模板，41 章思想）；只查功能不查故障路径；证据不归档（出事后无据可查）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class AliLaunchCase {
    record Gate(String component, String check, String evidence) {
    }

    static void launchGates() {
        System.out.println("【阿里栈上线门禁（逐项证据）】");
        List<Gate> gates = List.of(
                new Gate("版本矩阵", "三件套版本对齐核对", "依赖树 + 官方矩阵截图"),
                new Gate("Nacos", "3 节点集群 + 保护阈值 + 心跳调优", "摘节点演练记录"),
                new Gate("配置", "灰度发布 + 变更审计 + 敏感加密", "灰度轨迹 + 审计台账"),
                new Gate("Dubbo", "超时/重试配置 + 幂等核对", "Failover 演练记录"),
                new Gate("Sentinel", "规则持久化 + 压测校准", "限流触发实测曲线"),
                new Gate("Seata", "模式选型 ADR + undo_log + 回滚演练", "回滚演练记录"),
                new Gate("网关", "鉴权链 + 头清理 + 统一异常", "伪造穿透测试记录"),
                new Gate("SkyWalking", "Agent 版本兼容 + trace 巡检", "完整性巡检报告"));
        for (Gate g : gates) {
            System.out.printf("  %-8s %-26s 证据：%s%n", g.component(), g.check(), g.evidence());
        }
        System.out.println();
        System.out.println("  纪律：检查单进变更模板（41-10），每次接入/升级都走一遍");
    }

    public static void main(String[] args) {
        launchGates();
    }
}
```

**课后练习**
1. 为你团队的阿里栈接入定制完整检查单（补充本章未覆盖的公司特有项），组织一次上线评审。
2. 从"常见上线事故清单"中选两个，编写演练脚本验证检查项能拦截它们。
3. 思考题：为什么"证据归档"是上线门禁不可省的部分？从 41-10 审计与 42-11 复盘的依赖关系分析。
