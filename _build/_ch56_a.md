### 56-01 稳定性体系总览

**知识点**
- **稳定性工程 = 把"少出事、出事快恢复"变成可运营的体系**：三大支柱——**容量**（资源够不够：压测/画像/扩容 47-08）、**预案**（出事怎么办：降级/切换/回滚的自动化）、**治理**（不出大事：变更管控/风险清单/演练常态化）。42 章是"看得见"，本章是"管得住"。
- **稳定性的预算思维**：99.99% = 月 43 分钟错误预算（42-05）——预算怎么花（发布/演练/大促）是运营决策；**预算耗尽自动冻结发布**（42-05 的落地机制）。
- **稳定性工程的输入输出**：输入=历史事故（42-11 复盘）+ 业务增长（52-01 估算）+ 架构变更（49 章）；输出=预案库、容量报告、演练记录、风险清单——**每季度审视一次体系本身的健康度**。
- **组织形态**：稳定性是"全员职责 + 专项牵头"——值班体系（42-10）、SRE 或稳定性专项组、业务方参与预案评审。**没有组织保障的稳定性工程只有文档**。
- **与全书的关系**：42 观测（看得见）、47 云原生（跑得稳）、48 推理（特殊资源）、51 支付（资金级）——本章是把这些能力**运营化、常态化、制度化**。
- 易错点：稳定性=运维的事（开发参与的预案才是真预案）；体系建设一次成型（应随业务增长滚动）；度量缺失（不知道体系是否有效）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class StabilityOverview {
    record Pillar(String pillar, String question, String outputs) {
    }

    static void pillars() {
        System.out.println("【三大支柱】");
        List<Pillar> ps = List.of(
                new Pillar("容量", "资源够不够？", "压测报告/容量画像/扩容计划（47-08）"),
                new Pillar("预案", "出事怎么办？", "预案库/降级开关/演练记录（44-08）"),
                new Pillar("治理", "怎么不出大事？", "变更管控/风险清单/复盘闭环（42-11）"));
        for (Pillar p : ps) {
            System.out.printf("  %-4s %-10s 输出=%s%n", p.pillar(), p.question(), p.outputs());
        }
        System.out.println();
    }

    static void budgetOps() {
        System.out.println("【错误预算的运营化】");
        double slo = 99.95;
        double budgetMin = 30 * 24 * 60 * (1 - slo / 100);
        double spent = 15.0;
        System.out.printf("  SLO %.2f%% -> 月预算 %.0f 分钟；已花 %.0f（%.0f%%）%n",
                slo, budgetMin, spent, spent / budgetMin * 100);
        String verdict = spent / budgetMin > 0.8 ? "预算吃紧：非核心发布冻结"
                : spent / budgetMin > 1.0 ? "耗尽：只允许修复类变更" : "健康：正常迭代";
        System.out.println("  判定：" + verdict);
        System.out.println("  机制：预算状态接发布门禁（41 章）—— 超支自动拦截");
    }

    public static void main(String[] args) {
        pillars();
        budgetOps();
    }
}
```

**课后练习**
1. 为你的系统画三大支柱现状图（每支柱的输出物有哪些/缺哪些），制定补齐计划。
2. 实现错误预算自动门禁：预算状态与发布流水线联动（超支拦截）。
3. 思考题：为什么"稳定性工程"必须包含组织形态？用"预案是开发写的还是运维写的"的质量差异论证。

---

### 56-02 全链路压测

**知识点**
- **单系统压测 ≠ 全链路压测**：单系统压测验证组件容量；**全链路压测**模拟真实用户流量打穿整条链路（网关->服务->缓存->DB->MQ），验证**整体容量与连锁反应**（A 服务扩容后 B 服务成瓶颈 48-06）。大促前必做。
- **全链路压测的技术前提**：**影子库/影子表**（压测数据与生产数据隔离——写压测标记的数据到影子存储，不污染生产数据 36 章隔离纪律）；**流量标记**（压测流量全链路带标记，中间件识别路由到影子）；**数据构造**（仿真数据脱敏生成 46-09）。
- **实施流程**：容量目标确认（52-01）-> 影子设施准备 -> 流量注入（逐步加压到目标 ×1.5）-> 瓶颈定位与扩容（48-03/47-08）-> 复测达标 -> 报告与预案更新。
- **压测中的发现通常是"连锁瓶颈"**：DB 连接池 -> 缓存带宽 -> 下游限流阈值 -> 日志写入——**扩容不是加机器，是消除每一级瓶颈**。
- 易错点：影子隔离缺失（压测数据污染生产）；压测流量无标记（监控被污染）；只压峰值不压持续（OOM 在持续负载才出现）；压测后不更新预案（数字没变成能力）。
- **成本与频次**：全链路压测成本高（影子设施+人力）——大促前必做 + 重大架构变更后 + 季度例行（缩水版）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ShadowRoutingDemo {
    /** 影子路由：压测流量带标记 -> 影子存储 */
    record Write(String table, String data, boolean shadow) {
    }

    static String route(Write w) {
        if (w.shadow()) {
            return "shadow_" + w.table();          // 影子表
        }
        return w.table();                           // 正式表
    }

    public static void main(String[] args) {
        List<Write> writes = List.of(
                new Write("orders", "real-1", false),
                new Write("orders", "pt-1001", true),       // 压测订单
                new Write("orders", "real-2", false));
        for (Write w : writes) {
            System.out.printf("  写 %s(%s) -> %s%n", w.data(), w.shadow() ? "压测" : "真实", route(w));
        }
        System.out.println("  标记在入口注入，全链路透传（43-06/47-08）—— 数据零污染");
    }
}
```

**课后练习**
1. 实现影子路由器完整版（入口标记 + 全链路透传 + 影子表/影子 topic 路由），验证生产数据零污染。
2. 设计一次全链路压测方案（目标/影子设施/加压曲线/瓶颈预案/报告模板）。
3. 思考题：为什么全链路压测能发现"单系统压测发现不了"的问题？举一个"各系统都达标但链路挂了"的连锁瓶颈例子。

---

### 56-03 容量画像与水位管理

**知识点**
- **容量画像是"资源与流量的对应关系表"**：每服务在典型负载下的 CPU/内存/连接/带宽画像 + 峰值水位——**画像来自压测与真实监控的结合**（48-06 推理容量画像的通用版）。
- **水位管理的三线**：**安全线**（70%，正常）、**告警线**（85%，扩容启动）、**极限线**（95%，预案触发/限流）——**三线的依据是压测拐点**（47-08）而非拍脑袋。
- **容量规划节奏**：季度规划（按业务增长预测 52-01）+ 月度审视（实际增长 vs 预测偏差）+ 大促专项——**容量是滚动预测而不是年度拍板**。
- **画像的颗粒度**：服务级不够——**接口级画像**（不同接口资源消耗差异巨大）与**租户/渠道维度**（55-08 噪声邻居）——画像越细，容量决策越准。
- 易错点：水位线拍脑袋（不基于压测拐点）；画像过期（架构变更后没更新）；只看均值不看分位（P99 画像才是扩容依据）；垂直扩容依赖（加内存不查泄漏，30 章）。
- **与弹性伸缩的分工**：画像与水位是"认知层"（知道什么时候该扩），HPA/KEDA 是"执行层"（自动扩）——**没有画像的自动伸缩是盲扩**。

**示例代码**
```java
import java.util.List;

public class WatermarkDemo {
    record Profile(String service, double safePct, double alarmPct, double limitPct) {
    }

    static String judge(Profile p, double current) {
        if (current >= p.limitPct()) {
            return "LIMIT：触发预案（限流/降级）";
        }
        if (current >= p.alarmPct()) {
            return "ALARM：启动扩容（47-08）";
        }
        return "SAFE";
    }

    public static void main(String[] args) {
        List<Profile> profiles = List.of(
                new Profile("order", 70, 85, 95),
                new Profile("search", 60, 80, 92));     // 拐点不同，三线不同
        for (Profile p : profiles) {
            System.out.printf("  %s 当前 %.0f%% -> %s%n", p.service(), 80.0, judge(p, 80));
        }
        System.out.println("  三线依据：压测拐点（48-03 曲线）—— 不是拍脑袋");
    }
}
```

**课后练习**
1. 实现容量水位管理器（画像表 + 三线判定 + 扩容/预案联动），用压测数据校准三线。
2. 为你的核心服务做接口级容量画像（压测不同接口），对比服务级画像的差异。
3. 思考题：为什么"P99 画像"比"平均画像"更适合做扩容依据？从长尾请求的资源占用特征分析。

---

### 56-04 预案平台与自动开关

**知识点**
- **预案是"写好的故障应对"**：每类故障 -> 触发条件 -> 执行动作（开关/降级/扩容/切换）-> 恢复条件——**预案的形态从文档到平台**：预案项=配置化开关，一键执行，执行有审计（51-10）。
- **预案的生命周期**：编写（故障模式分析 42-11）-> 评审（值班可执行粒度）-> **演练验证**（47-10，没演练过的预案是想象）-> 常态值班手册 -> 复盘更新。**预案有效期**：架构变更后预案要重审（41 章发布联动）。
- **开关的分类**：降级开关（关功能保核心）、限流开关（挡流量）、跳转开关（切备用）、数据开关（写保护）——**开关要分级授权**（核心开关双人操作 51-07）与演练（开关本身也可能坏——开关演练 47-10）。
- **自动预案**：可自动化的预案自动执行（熔断/自动扩容/自动回滚 44-08 阶梯）——**自动化预案的前提是判据可靠**（误触发也是事故），因此自动预案也要演练。
- 易错点：预案文档化不平台化（半夜执行靠翻文档）；开关无演练（关键时刻开关失灵）；预案与实际架构脱节；预案执行无审计（谁开的开关说不清）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ContingencyPlatform {
    record Contingency(String name, String trigger, String action, boolean auto,
                       boolean rehearsed) {
    }

    static void audit() {
        System.out.println("【预案库与健康度】");
        List<Contingency> cs = List.of(
                new Contingency("库存Redis挂 -> 本地缓存兜底", "健康检查失败", "切本地缓存+告警",
                        true, true),
                new Contingency("支付渠道降级", "成功率<95%", "切备用渠道", true, true),
                new Contingency("全站限流", "网关QPS达上限", "分级限流", true, false));
        for (Contingency c : cs) {
            System.out.printf("  %-24s 触发=%-14s 自动=%s 演练=%s%n",
                    c.name(), c.trigger(), c.auto() ? "是" : "否",
                    c.rehearsed() ? "✓" : "未演练（风险项）");
        }
        System.out.println();
        System.out.println("  健康度 = 演练覆盖率；未演练的预案标红进季度计划（47-10）");
    }

    public static void main(String[] args) {
        audit();
    }
}
```

**课后练习**
1. 实现预案平台迷你版（预案注册/触发/执行/审计），演示"自动预案 + 人工预案"两类执行。
2. 实现"预案演练覆盖率"报表，把未演练预案生成季度计划。
3. 思考题：为什么自动预案"误触发也是事故"？构造一个判据误报导致正常业务被降级的用例，说明判据可靠性要求。

---

### 56-05 大促保障作战手册

**知识点**
- **大促保障四阶段**：**备战**（T-30：容量核算 56-03、扩容、压测 56-02、预案演练）、**临战**（T-1：冻结发布 41 章、值班排班、开关预置、预案就位）、**作战**（T0：值守看板、预案执行、实时扩缩、快速决策机制 42-10）、**复盘**（T+3：数据复盘、问题清单、改进项）。
- **作战值守的组织**：指挥（IC）/各域负责人/执行者/对外沟通（42-10 角色分工的大促版）；**决策机制**：预案内的事按预案执行，预案外的事升级决策（明确升级路径）。
- **值守看板**：核心链路水位（56-03）、业务指标（GMV/成功率）、渠道健康（51-10）、预案执行状态——**一个看板回答"现在要不要动手"**。
- **恢复期纪律**：扩容的资源恢复要有节奏（**立刻回缩会二次抖动**——缓存冷、连接重建）；预案开关的回开也要分批（41-07 灰度思想）。
- 易错点：备战期改动业务代码（冻结失守）；作战期预案外操作无决策（各自为战）；恢复期立即回缩资源；复盘只记成功不记惊险时刻。
- **大促的"降级预案"要按业务影响排序**：先关哪类功能、保哪类功能——**排序由业务方确认**（GMV 优先 vs 体验优先）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class PromotionWarRoom {
    record Phase(String phase, String window, String keyActions) {
    }

    static void phases() {
        System.out.println("【大促四阶段】");
        List<Phase> ps = List.of(
                new Phase("备战 T-30", "容量核算+压测+预案演练", "产出：容量报告+预案就位清单"),
                new Phase("临战 T-1", "发布冻结+值守排班+开关预置", "产出：作战手册确认"),
                new Phase("作战 T0", "值守看板+预案执行+实时决策", "产出：处置记录（时间线）"),
                new Phase("复盘 T+3", "数据复盘+问题清单+改进项", "产出：复盘报告（42-11）"));
        for (Phase p : ps) {
            System.out.printf("  %-10s %-24s %s%n", p.phase(), p.window(), p.keyActions());
        }
        System.out.println();
        List<String> degradeOrder = List.of(
                "先关：推荐个性化（保交易）",
                "再关：评论/晒单（保交易核心）",
                "最后动：交易主链路（预案级）",
                "排序由业务方确认 —— 技术不替业务拍板");
        degradeOrder.forEach(s -> System.out.println("  " + s));
    }

    public static void main(String[] args) {
        phases();
    }
}
```

**课后练习**
1. 编写一份完整大促作战手册（四阶段 + 降级排序 + 值班表 + 决策升级路径），组织桌面推演。
2. 实现"恢复期资源回缩节奏"模拟：立即回缩 vs 分批回缩的抖动对比。
3. 思考题：为什么"降级排序"必须业务方确认？构造两个排序错误的后果用例（先关交易 vs 先关推荐）。

---

### 56-06 故障演练常态化

**知识点**
- **演练的常态化形态**：**例行演练**（季度游戏日 47-10）、**发布后演练**（新架构上线后的专项验证 41 章）、**随机演练**（Chaos 自助平台随机注入——高级形态，需成熟护栏）、**红蓝对抗**（蓝队注入、红队定位——攻防式训练）。
- **演练的选题来源**：历史事故复现（42-11 改进项验证）、架构变更的新故障面（49 章改造引入）、**想象中的最坏情况**（"如果 ES 全挂会怎样"）、行业事故借鉴（别家的学费也是学费）。
- **演练的度量**：MTTR 趋势、预案演练覆盖率（56-04）、发现缺陷数、值班人员参与率——**演练是训练也是度量**。
- **演练的规模化**：自助混沌平台（参数化注入 + 护栏自动 47-10）让团队自助演练；演练结果进知识库（故障手册更新）。
- 易错点：演练变成表演（预知故障点）；只在预发演练（生产问题生产练）；演练缺陷不闭环（47-10）；演练安排在业务高峰（风险失控）。
- **与 41 章发布的联动**：演练不通过的架构变更不放开流量——**演练是发布门禁的一部分**。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class DrillRoutines {
    record Drill(String quarter, String scenario, String defectFound, String closed) {
    }

    static void track() {
        System.out.println("【演练台账：缺陷闭环跟踪】");
        List<Drill> drills = List.of(
                new Drill("Q1", "ES 主节点全挂", "集群恢复后索引未自动分配", "已修复+复测"),
                new Drill("Q2", "支付渠道全断", "降级话术缺失", "已上线+演练验证"),
                new Drill("Q3", "缓存雪崩模拟", "待定", "改进项进 Q4"));
        for (Drill d : drills) {
            System.out.printf("  %s %s -> 缺陷：%s（%s）%n",
                    d.quarter(), d.scenario(), d.defectFound(), d.closed());
        }
        System.out.println();
        System.out.println("  健康度 = 缺陷闭环率；未闭环缺陷进季度目标（47-10 闭环纪律）");
    }

    public static void main(String[] args) {
        track();
    }
}
```

**课后练习**
1. 建立演练台账模板（季度/场景/缺陷/闭环），补录你团队最近三次事故对应的演练。
2. 设计"自助混沌平台"的护栏清单（哪些注入允许自助、哪些需审批），并说明分级理由。
3. 思考题：为什么"随机演练"比"计划演练"更能暴露真实问题？又为什么随机演练需要更成熟的护栏？
