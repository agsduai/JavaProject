/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：44-01
 * 课名：从工具调用到 Agent
 * ------------------------------------------------------------
 * 知识点：
 *   - **工具调用（40-06）是一次决策，Agent 是一组自主决策的循环**：工具调用是"用户说一句话 -> 模型决定调一次工具 -> 返回"；Agent 是"给一个目标 -> 模型反复【思考 -> 调工具 -> 观察结果 -> 再思考】直到目标完成"。**Agent = LLM 循环 + 工具 + 终止条件**，没有更神秘的东西。
 *   - **Agent 的最小组成五要素**：目标（用户任务）、工具集（能做什么）、循环控制器（最多几轮、何时停）、记忆（上下文与状态，44-03）、护栏（权限与预算，44-09）。**五要素里四个是工程，只有一个是模型**——Java 工程师的领地在此。
 *   - **自主性是双刃剑**：Agent 可以把"查库存 -> 补货 -> 通知采购"自己编排完（人不在环）；代价是**错误也自主传播**——第一步查错，后面全错且更难追溯。因此 Agent 的自主边界（哪些操作允许自主、哪些要人确认）是架构决策不是产品细节。
 *   - **Agent 与工作流（workflow）的分界**：工作流是**人写死的步骤图**（确定性，模型只是节点里的生成器）；Agent 是**模型自己决定下一步**（开放性，行为不可穷举）。**生产系统的主流是"工作流为主 + 局部 Agent"**：结构确定的部分写死，不确定的分支交给 Agent 循环。
 *   - **什么时候值得用 Agent**：任务步骤无法预先枚举（排障、研究型任务）、需要多轮工具交互（先查再算再验证）、单次工具调用循环（44-02 的 ReAct）比手写编排便宜。**步骤固定的任务写成工作流更可靠**。
 *   - 易错点：把 Agent 当万能药（能用普通函数解决的也套 Agent，成本与不可控性双升）；没有终止条件的循环（模型反复调工具直到上下文爆炸）；自主权限过大（Agent 直接执行退款）；混淆工作流与 Agent（把确定性流程写成 Agent，回归测试都做不了）。
 * ============================================================
 */

package com.heima.ch44;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AgentIntro {
    record Tool(String name, String desc, String effect) {
    }

    static final List<Tool> TOOLS = List.of(
            new Tool("query_stock", "查询商品库存", "只读"),
            new Tool("create_po", "创建补货单", "写操作，需审批"),
            new Tool("notify", "发通知", "写操作"));

    /** Agent 循环：思考 -> 工具 -> 观察 -> 继续/终止 */
    static void agentLoop() {
        System.out.println("【目标：库存低于 20 的商品自动补货】");
        String goal = "检查商品 A100 的库存，低于 20 则补货 100 件并通知采购";
        List<String> trace = new ArrayList<>();
        // 轮 1：思考 -> 工具
        trace.add("思考1：需要先查库存 -> 调用 query_stock(A100)");
        int stock = 12;                                    // 工具执行结果
        trace.add("观察1：库存=" + stock + " -> 低于 20");
        // 轮 2：判断 -> 写操作走审批（护栏）
        trace.add("思考2：需补货 -> create_po 是写操作 -> 记录待审批而非直接执行");
        trace.add("观察2：补货单 PO-1 已创建（待审批）");
        // 轮 3：通知 -> 终止
        trace.add("思考3：通知采购 -> notify");
        trace.add("观察3：已通知 -> 目标完成，终止");
        for (String t : trace) {
            System.out.println("  " + t);
        }
        System.out.println("  循环三要素：终止条件（目标完成/达最大轮数）+ 工具结果注入 + 每轮重审权限");
        System.out.println();
    }

    static void agentVsWorkflow() {
        System.out.println("【Agent 与工作流的分界：步骤是否可预先枚举】");
        String[][] rows = {
                {"退费审批流", "工作流", "步骤固定：校验->计算->落库，确定性优先"},
                {"客服问题诊断", "Agent", "先问什么、查什么都取决于用户输入"},
                {"报表生成", "工作流", "数据源与格式固定"},
                {"线上事故排查", "Agent", "排查路径无法预先穷举"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-6s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  生产主流：工作流为主 + 局部 Agent —— 可测试性优先");
        System.out.println();
    }

    static void autonomyBoundary() {
        System.out.println("【自主边界：哪些操作允许 Agent 自主执行】");
        Map<String, String> boundary = new LinkedHashMap<>();
        boundary.put("只读查询", "自主执行（读不会造成不可逆损失）");
        boundary.put("内部通知", "自主执行，但记录审计");
        boundary.put("创建待审单据", "允许创建，执行需人审批");
        boundary.put("资金操作/删除/外发", "禁止自主，只能建议 + 人工执行");
        for (Map.Entry<String, String> e : boundary.entrySet()) {
            System.out.printf("  %-14s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  判据：操作是否不可逆 + 影响是否可估算");
        System.out.println("  循环控制器每轮重查权限：上一轮允许不代表这一轮允许");
    }

    public static void main(String[] args) {
        agentLoop();
        agentVsWorkflow();
        autonomyBoundary();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 40-06 的单次工具调用改造成最小 Agent 循环（终止条件 + 最大轮数 + 每轮权限检查），实现"查订单 -> 判断是否超时 -> 超时则建议补偿"三步自主任务。
 * 2. 为你的业务划分"工作流 vs Agent"边界：列 6 个任务，标注归属与理由，找出一个"误用 Agent"的候选并改成工作流。
 * 3. 思考题：为什么说 Agent 的五要素里"四个是工程"？从循环控制、记忆、护栏的实现复杂度与模型能力的边界分析。
 * ============================================================
 */
