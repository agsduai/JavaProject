/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：49-10
 * 课名：遗留系统改造与演进式架构
 * ------------------------------------------------------------
 * 知识点：
 *   - **遗留系统不是技术问题，是演进策略问题**：三个月赶工的订单系统跑着 70% 的交易——推倒重写的风险（业务中断、隐性规则丢失）远大于演进改造。**演进式架构（evolutionary architecture）**：增量重构 + 持续可发布。
 *   - **改造的第一步是“知道现状”**：特征检测（failure mode analysis）——耦合度分析、圈复杂度热点、变更频率分布（**改得最频繁的类就是业务核心**，49-01 事件风暴的代码侧佐证）。补测试是改造的安全网（33 章测试金字塔：先给核心路径铺集成测试）。
 *   - **绞杀者路线（49-07）+ 接缝引入（seam）**：在旧代码里先挖出“接缝”（可替换点）——把“直接调库”改成“调接口”（49-04 端口），一步一小步，每步可发布可回退。
 *   - **数据同步期**：新旧库并存的同步方案（43-09 CDC）——双向写期要版本仲裁（45-08 fencing 思想），切换期灰度读。
 *   - **演进式架构的守护**：适应度函数（fitness function）——把架构约束写成自动化检查（依赖方向、分层违例、API 兼容性）进 CI，**让架构随演进不退化**（49-08 ArchUnit 的体系化）。
 *   - **改造的节奏与止损**：每两周一个可发布增量；改造期间冻结大需求（或只允许与改造相关的）；**改造预算有上限**（超过 X 人月仍未见效 -> 重新评估方案）。
 *   - 易错点：大爆炸重写（两年没上线，旧系统还在演进，永远追不上）；只重构不补测试（改一处崩三处）；接缝不清理（重构完留下抽象层垃圾）；没有业务方参与（技术自嗨改造，业务感知为零价值）。
 * ============================================================
 */

package com.heima.ch49;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class LegacyRefactor {
    static void hotspots() {
        System.out.println("【改造侦察：用数据找改造顺序】");
        record Hotspot(String className, int changeFreq, int cyclomatic, String verdict) {
        }
        List<Hotspot> spots = List.of(
                new Hotspot("OrderService.calcAmount", 42, 85, "核心域 + 高变更 -> 第一个改造"),
                new Hotspot("StockService.deduct", 18, 40, "核心域 -> 第二批"),
                new Hotspot("ReportExporter", 2, 60, "低变更 -> 缓一缓"),
                new Hotspot("LegacyUtil", 1, 120, "复杂但无人动 -> 包裹隔离，不重构"));
        for (Hotspot h : spots) {
            System.out.printf("  %-26s 变更%3d次/月 圈复杂度%3d -> %s%n",
                    h.className(), h.changeFreq(), h.cyclomatic(), h.verdict());
        }
        System.out.println("  侦察公式：变更频率 × 复杂度 = 改造收益；无人动的大泥球优先包裹");
        System.out.println();
    }

    static void seamIntro() {
        System.out.println("【接缝引入：每步可发布可回退】");
        List<String> steps = List.of(
                "第1步：补集成测试（当前行为快照，改造的安全网）",
                "第2步：挖接缝——calcAmount 里的优惠规则抽成接口（端口 49-04）",
                "第3步：旧实现挂接口（行为零变化，发布验证）",
                "第4步：新实现（策略族）并行运行，对比结果（暗发布 41-08）",
                "第5步：切流量 -> 删除旧实现 -> 移除临时开关");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  每一步都是一次正常发布：改造成本摊进日常迭代");
        System.out.println();
    }

    static void fitnessFunctions() {
        System.out.println("【适应度函数：让架构不退化】");
        record Fitness(String rule, String tool, String trigger) {
        }
        List<Fitness> fs = List.of(
                new Fitness("领域层零框架依赖", "ArchUnit / 依赖树检查", "每次构建"),
                new Fitness("禁止跨上下文 JOIN", "SQL 解析器静态扫描", "每次构建"),
                new Fitness("圈复杂度 < 15", "静态分析阈值", "每次构建"),
                new Fitness("API 向后兼容", "openapi-diff 检查", "每次 PR"),
                new Fitness("测试覆盖率核心域 > 70%", "覆盖率门禁（41 章）", "每次构建"));
        for (Fitness f : fs) {
            System.out.printf("  %-16s %-22s %s%n", f.rule(), f.tool(), f.trigger());
        }
        System.out.println("  本质：架构规则 = 可执行的测试（41-03 流水线闸门的架构版）");
        System.out.println();
    }

    static void budgetDiscipline() {
        System.out.println("【改造的止损纪律】");
        List<String> rules = List.of(
                "预算上限：本阶段改造 ≤ 3 人月，到期复盘收益",
                "需求冻结协调：改造窗口内业务需求排优先级（业务参与决策）",
                "可发布节奏：每两周一个增量上生产（不允许长期分支）",
                "收益度量：改造成本 vs 后续每次需求变更的节省估算",
                "止损线：连续两个增量未见收益 -> 重新评估方案");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  演进式改造是投资决策：有预算、有度量、有止损");
    }

    public static void main(String[] args) {
        hotspots();
        seamIntro();
        fitnessFunctions();
        budgetDiscipline();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用“变更频率 × 复杂度”给你的代码库做改造侦察，输出前 3 个热点与改造顺序建议。
 * 2. 选一个热点方法走完五步接缝改造（可缩小规模演示），每步记录可发布证据。
 * 3. 思考题：为什么“补测试在前、重构在后”是铁律？从行为快照与回归安全的角度分析顺序不可颠倒的原因。
 * ============================================================
 */
