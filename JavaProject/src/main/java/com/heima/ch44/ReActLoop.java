/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：44-02
 * 课名：ReAct 循环与多步规划
 * ------------------------------------------------------------
 * 知识点：
 *   - **ReAct 是 Agent 循环的经典范式**：每轮输出 **Thought（我该干什么）-> Action（调什么工具+参数）-> Observation（工具返回）**，循环直到 Thought 判定目标完成。它的价值是**把推理过程显式化**——每个决策可审计、出错可定位到具体轮次。
 *   - **显式规划 vs 隐式规划**：隐式（每轮只看当前状态决定下一步，ReAct 默认）简单灵活但可能绕远路；显式（先让模型输出完整计划再逐步执行）可控性强但计划可能失效需要重规划。**生产常用混合：先出计划，执行中允许修订**。
 *   - **上下文膨胀是循环的成本曲线**：每轮把"历史 Thought/Action/Observation"全部回传——5 轮 × 每轮 800 token = 4000 token 仅历史。**Observation 必须精炼**（40-06 的工具结果精炼原则在 Agent 里更关键），必要时对历史做摘要压缩（44-03）。
 *   - **失败处理是循环的健壮性核心**：工具报错（返回结构化错误让模型重试或换路）、死循环检测（同一 Action 连续 N 次 -> 强制终止）、计划偏离检测（执行 3 轮后对照原计划，偏离过大回到重规划）。
 *   - **多步任务的检查点（checkpoint）**：长任务在第 N 步崩溃，从检查点恢复而不是从头再来——每轮结束把（目标、已完成步骤、中间产物）持久化。**Agent 恢复语义与流处理 checkpoint（43-05）同构**。
 *   - **可观测性（42 章思想在此复用）**：每次循环记录 trace：轮次、Thought、Action、耗时、token 消耗。**Agent 调试的唯一入口就是这份 trace**，没有 trace 的 Agent 出错等于盲修。
 *   - 易错点：Observation 原样塞 JSON 大对象（上下文爆炸）；没有死循环检测（同参数调用刷屏）；Thought 不输出（黑盒循环，无法审计）；恢复语义缺失（长任务崩溃全部重跑）。
 * ============================================================
 */

package com.heima.ch44;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ReActLoop {
    record Round(int no, String thought, String action, String observation, int tokens) {
    }

    /** 模拟的执行轨迹：查库存 -> 计算补货量 -> 校验预算 -> 完成 */
    static void reactTrace() {
        System.out.println("【目标：为爆款 A100 制定补货方案（预算上限 5000 元）】");
        List<Round> rounds = List.of(
                new Round(1, "先查当前库存与销量", "query_stock(A100)", "库存 12，日均销量 30", 180),
                new Round(2, "按 7 天销量估算需求：30×7-12=198 件", "calc(30*7-12)", "建议补货 198 件", 220),
                new Round(3, "198 件单价 25 元 = 4950 元，需校验预算", "check_budget(4950)", "预算内（上限 5000）", 200),
                new Round(4, "预算通过，输出方案", "finish", "补货 198 件，总价 4950 元", 150));
        int totalTokens = 0;
        for (Round r : rounds) {
            totalTokens += r.tokens();
            System.out.printf("  轮%d Thought: %s%n        Action: %s%n        Observation: %s%n",
                    r.no(), r.thought(), r.action(), r.observation());
        }
        System.out.printf("  终止：finish；总消耗 %d token（历史每轮全量回传）%n", totalTokens);
        System.out.println();
    }

    static void guards() {
        System.out.println("【循环护栏：健壮性的四道闸】");
        String[][] guards = {
                {"最大轮数", "8 轮到顶 -> 强制终止并输出已有结论", "防无限循环"},
                {"重复检测", "同一 Action+参数 连续 2 次 -> 终止", "防模型绕圈"},
                {"失败重试", "工具错误返回 {error:...} -> 模型可换路", "失败也是信息"},
                {"偏离检测", "第 3 轮对照原计划，偏差过大 -> 重规划", "防跑题"},
        };
        for (String[] g : guards) {
            System.out.printf("  %-8s %-40s %s%n", g[0], g[1], g[2]);
        }
        System.out.println();
    }

    static void checkpointRecovery() {
        System.out.println("【长任务的检查点与恢复】");
        record Checkpoint(int round, String done, String next) {
        }
        List<Checkpoint> cps = List.of(
                new Checkpoint(1, "库存=12 已查", "计算补货量"),
                new Checkpoint(2, "补货量 198 已算", "校验预算"),
                new Checkpoint(3, "预算通过", "输出方案"));
        Checkpoint crash = cps.get(1);
        System.out.printf("  崩溃于第 3 轮前 -> 从检查点恢复：%s（已完成）%n        继续：%s%n",
                crash.done(), crash.next());
        System.out.println("  持久化内容：目标 + 已完成步骤 + 中间产物 + 权限状态");
        System.out.println("  与 43-05 流处理 checkpoint 同构：可恢复优于可重跑");
    }

    public static void main(String[] args) {
        reactTrace();
        guards();
        checkpointRecovery();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现带全部四道护栏的 ReAct 循环（模拟模型决策），构造"工具反复失败"与"同参数绕圈"两种场景验证护栏。
 * 2. 给循环加检查点：在第 2 轮后注入崩溃，验证恢复后从第 3 步继续且最终结果正确。
 * 3. 思考题：为什么 Observation 必须精炼而 Thought 可以保留？从上下文成本与可审计性的不同需求分析两者的处理差异。
 * ============================================================
 */
