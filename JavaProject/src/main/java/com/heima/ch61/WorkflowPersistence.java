/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：61-08
 * 课名：长流程的持久化与恢复
 * ------------------------------------------------------------
 * 知识点：
 *   - **长流程的状态必须落库**：流程实例（当前节点/状态/版本）、历史轨迹（每个节点的动作与时间）、流程变量（表单数据/审批意见）——**三张表是工作流引擎的最小持久化模型**。
 *   - **恢复语义**：引擎重启 -> 加载"运行中"的实例 -> 按当前节点继续等待或推进——**这要求"当前节点"是完整可达的状态**（不能只存"第几步"，要存"在哪个节点等谁"）。
 *   - **状态与业务数据的一致性**：流程推进要同时改"流程实例状态"与"业务单据状态"（如审批通过 -> 单据变 APPROVED）——**两者必须同一事务**（39/49 章原则），否则出现"流程结束但单据未更新"。
 *   - **定时扫表 vs 事件驱动**：等待中的流程（超时提醒/自动通过）靠**定时扫描**（61-06 的延迟队列思想）或**时间轮**推进——扫描频率决定超时精度。
 *   - **历史轨迹的用途**：审计（谁在何时批了什么，51-10）、流程挖掘（哪一步最耗时）、申诉依据（44-09 证据链）——**轨迹不可修改**（append-only）。
 *   - 易错点：流程状态与业务数据分两个事务（不一致）；历史轨迹可覆盖（审计失效）；恢复逻辑只认"当前节点"不认变量（表单数据丢失）；等待中的实例没有超时处理（流程永久挂起）。
 * ============================================================
 */

package com.heima.ch61;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WorkflowPersistence {
    record InstanceRow(String id, String node, String status, int version,
                       Map<String, String> variables) {
    }

    record HistoryRow(String instanceId, String node, String action, String operator,
                      long atMs) {
    }

    /** 模拟两张表 */
    static final Map<String, InstanceRow> instanceTable = new LinkedHashMap<>();
    static final List<HistoryRow> historyTable = new ArrayList<>();

    static void create(String id, Map<String, String> vars) {
        instanceTable.put(id, new InstanceRow(id, "manager", "RUNNING", 0, new LinkedHashMap<>(vars)));
        historyTable.add(new HistoryRow(id, "start", "SUBMIT", "alice", 1000));
        System.out.printf("  创建实例 %s，变量=%s%n", id, vars);
    }

    /** 推进 + 落库（模拟同一事务） */
    static void advance(String id, String action, String operator, long now) {
        InstanceRow row = instanceTable.get(id);
        if (row == null || !row.status().equals("RUNNING")) {
            System.out.println("  实例不存在或已结束：" + id);
            return;
        }
        historyTable.add(new HistoryRow(id, row.node(), action, operator, now));
        String next = action.equals("APPROVE") ? "finance" : "manager";
        String status = action.equals("APPROVE") && row.node().equals("finance")
                ? "FINISHED" : "RUNNING";
        instanceTable.put(id, new InstanceRow(id, next, status, row.version() + 1, row.variables()));
        System.out.printf("  %s：%s --%s--> %s（状态 %s，变量保留 %s）%n",
                id, row.node(), action, next, status, row.variables());
    }

    /** 重启恢复：从表里加载运行中实例 */
    static void recover() {
        System.out.println("【引擎重启恢复】");
        instanceTable.forEach((id, row) -> {
            if (row.status().equals("RUNNING")) {
                System.out.printf("  恢复实例 %s -> 当前节点 %s（变量 %s）%n",
                        id, row.node(), row.variables());
            }
        });
    }

    public static void main(String[] args) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("amount", "12000");
        vars.put("reason", "设备采购");
        create("AP-1", vars);
        advance("AP-1", "APPROVE", "manager-zhang", 2000);
        System.out.println();
        recover();
        System.out.println();
        advance("AP-1", "APPROVE", "finance-li", 3000);
        System.out.println();
        System.out.println("【历史轨迹（append-only）】");
        historyTable.forEach(h -> System.out.printf("  %s %-8s %-8s %-12s @%d%n",
                h.instanceId(), h.node(), h.action(), h.operator(), h.atMs()));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整持久化：三张表（实例/历史/变量）+ 恢复逻辑 + "流程与业务单据同事务"，模拟引擎重启后继续审批。
 * 2. 实现"等待实例超时扫描"：每 10 分钟扫一次，超过 24 小时的待办自动提醒，超过 72 小时自动驳回。
 * 3. 思考题：为什么历史轨迹必须 append-only？从审计效力、流程挖掘、申诉举证三个角度分析。
 * ============================================================
 */
