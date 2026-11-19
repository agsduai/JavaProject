/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-11
 * 课名：综合案例：内部开发者平台
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一条"自助服务链"**：**服务目录（79-09）-> 脚手架生成（79-02）-> 流水线模板（79-07）-> 构建缓存/影响分析（79-03/79-04）-> 环境申请（79-06）-> 制品晋升（79-08）-> 记分卡**——**开发者自助完成，平台只提供默认正确路径**。
 *   - **平台的价值指标**：**新服务上线时间**（从几天到几小时）、**环境等待时间**、**构建时长**、**Golden Path 覆盖率**——**用 79-01 的价值流度量平台自己的效果**。
 *   - **平台不是"工具堆"**：**统一入口 + 统一模板 + 统一度量**，**否则只是把散落工具换了个首页**。
 *   - **组织配套**：**平台团队做"产品"**（有用户、有反馈、有路线图），**不是"运维支持"**——**内部平台也要做用户研究**。
 *   - 与 79 章各节的关系：本章是"组件的组装"，**和 69/70/71 章一样，是能力的收口**。
 *   - 易错点：平台只做 UI 不做自动化（点完还要手工）；模板不维护（生成即过时）；不度量平台自身（不知道有没有用）；强制使用（团队绕过）。
 * ============================================================
 */

package com.heima.ch79;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class IdpCase {
    record Service(String name, String owner, String template, String pipeline,
                   String env, String version, int score) {
    }

    static final List<Service> catalog = new ArrayList<>();
    static long now = 0;

    static long step(String name, long costMinutes) {
        now += costMinutes;
        System.out.printf("  +%-4dmin %s%n", costMinutes, name);
        return now;
    }

    static Service onboard(String name, String owner) {
        System.out.println("【新服务上线：" + name + "】");
        step("门户登记（服务目录）", 2);
        step("脚手架生成（模板 + 参数校验）", 3);
        step("生成物编译 + 单测", 10);
        step("套用流水线模板（含扫描）", 2);
        step("申请 dev 环境（TTL 2h）", 1);
        step("首次构建（远程缓存未命中）", 8);
        step("部署 dev + 冒烟", 4);
        step("制品晋升 staging（门禁通过）", 2);
        step("记分卡初始化（owner/runbook/SLO）", 1);

        Service s = new Service(name, owner, "java-service", "java-ci-v3",
                "env-dev", "0.1.0", 60);
        catalog.add(s);
        System.out.printf("  总耗时：%d 分钟（传统流程通常 2~5 天）%n", now);
        return s;
    }

    static void change(String service, String file) {
        System.out.println();
        System.out.println("【变更：" + service + " 修改 " + file + "】");
        step("影响分析（只构建 " + service + "）", 1);
        step("构建缓存命中（依赖未变）", 1);
        step("单测 + 扫描（并行）", 6);
        step("部署 staging", 3);
        step("冒烟通过", 2);
        System.out.printf("  从提交到 staging：%d 分钟%n", 12);
    }

    static void report() {
        System.out.println();
        System.out.println("【平台看板】");
        System.out.println("  服务数：" + catalog.size());
        catalog.forEach(s -> System.out.printf("    %-8s owner=%-6s version=%-6s score=%d%n",
                s.name(), s.owner(), s.version(), s.score()));

        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("新服务上线", 33L);
        metrics.put("变更到 staging", 12L);
        metrics.put("环境申请等待", 1L);
        metrics.put("构建平均时长", 3L);
        metrics.put("GoldenPath 覆盖", 100L);
        System.out.println("  平台指标（分钟）：" + metrics);

        System.out.println();
        System.out.println("【平台自身也要度量】");
        System.out.println("  新服务上线时间 / 环境等待 / 构建时长 / 模板使用率 / 开发者满意度");
        System.out.println("  没有度量 -> 不知道平台有没有用 -> 无法争取投入");
    }

    public static void main(String[] args) {
        onboard("payment-service", "支付团队");
        change("payment-service", "PayService.java");
        onboard("risk-service", "风控团队");
        report();

        System.out.println();
        System.out.println("【验收】自助（无需工单）/ 默认正确（Golden Path）/ 可度量（价值流）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端模拟"新服务上线"：登记 -> 脚手架 -> 流水线 -> 环境 -> 晋升，统计总耗时。
 * 2. 给平台加"变更路径"：影响分析 + 缓存命中 + 并行测试，对比全量构建耗时。
 * 3. 思考题：为什么"平台只做 UI 不做自动化"没有价值？从"开发者仍要手工操作"分析。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 搭一个 IDP：服务模板与脚手架、自助创建仓库与流水线、环境即代码、服务目录与归属、黄金路径指标（DORA）、开发者体验度量。核心是「新服务从 0 到上线 1 小时」。
 * 【任务】
 * 1. 做服务脚手架：一个命令生成项目骨架（目录、依赖、Dockerfile、CI 配置、健康检查），新服务开箱可跑。
 * 2. 实现自助流水线：创建仓库后自动注入 CI 配置，提交即触发构建与部署到开发环境。
 * 3. 建服务目录：登记每个服务的负责人、依赖、SLO、文档链接，能查出「谁负责这个服务」。
 * 4. 度量 DORA 指标：部署频率、变更前置时间、变更失败率、恢复时间，给出采集方式与改进建议。
 * 【验收清单】
 * [ ] 从脚手架到开发环境可访问，全流程在一个小时内完成。
 * [ ] 流水线无需人工配置，新仓库自动具备构建部署能力。
 * [ ] 服务目录信息准确，任一告警能定位到负责团队。
 * [ ] 四项 DORA 指标有数据、有趋势、有对应的改进动作。
 * ============================================================
 */
