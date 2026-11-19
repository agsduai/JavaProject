/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：61-07
 * 课名：工作流引擎：状态机与审批流
 * ------------------------------------------------------------
 * 知识点：
 *   - **工作流引擎与调度的区别**：调度是"**时间驱动的批量任务**"（61-01），工作流是"**人机混合的长流程**"——审批流里有人工节点（等待审批）、有状态持久化（流程跑一周）、有分支与回退。**两者都用 DAG 表达，但工作流的节点要持久化状态**。
 *   - **状态机是工作流的内核**：流程定义（节点 + 转移条件）-> 流程实例（某个业务单据的执行状态）-> 任务（待办）-> 变量（流程上下文）——**与 51-03 的支付状态机同源，只是节点更多、有等待态**。
 *   - **审批流的四个要素**：**节点类型**（审批/抄送/条件分支/并行会签）、**审批人规则**（指定人/角色/上级/动态计算）、**流转规则**（同意/驳回/加签/转办）、**超时策略**（自动通过/自动驳回/催办）——**每个要素都是配置，不是代码**。
 *   - **长流程的持久化与恢复**（本章重点）：流程实例跑一周，**每次状态变更都要落库**（当前节点/历史轨迹/变量），引擎重启后从库恢复——**这与 43-05 的 checkpoint 思想一致：状态自持 + 可恢复**。
 *   - **幂等的流程推进**：同一个审批动作重复提交（用户双击/网络重试）——**用"任务 ID + 动作 + 版本号"做幂等**（51-03 同源）；并行会签要处理"部分同意"的汇总（所有节点完成才推进）。
 *   - 易错点：流程状态只存内存（引擎重启流程全丢）；审批人规则硬编码（组织调整要改代码）；并行会签无汇总逻辑（有人同意就推进）；流程定义变更影响在途实例（需版本隔离）。
 * ============================================================
 */

package com.heima.ch61;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WorkflowEngine {
    enum NodeType {
        START, APPROVE, CONDITION, END
    }

    record Node(String id, NodeType type, String approver, String next) {
    }

    record Instance(String id, String currentNode, String status,
                    List<String> history, int version) {
    }

    static final Map<String, Node> definition = new LinkedHashMap<>();

    static void define() {
        definition.put("start", new Node("start", NodeType.START, null, "manager"));
        definition.put("manager", new Node("manager", NodeType.APPROVE, "直属主管", "finance"));
        definition.put("finance", new Node("finance", NodeType.APPROVE, "财务", "check"));
        definition.put("check", new Node("check", NodeType.CONDITION, null, "end"));
        definition.put("end", new Node("end", NodeType.END, null, null));
    }

    /** 推进一个节点：幂等（版本校验）+ 落库（历史追加） */
    static Instance approve(Instance inst, String action, int expectedVersion) {
        if (inst.version() != expectedVersion) {
            System.out.printf("  幂等拒绝：期望版本 %d，实际 %d（重复提交）%n",
                    expectedVersion, inst.version());
            return inst;
        }
        Node cur = definition.get(inst.currentNode());
        if (cur == null || cur.type() == NodeType.END) {
            System.out.println("  流程已结束");
            return inst;
        }
        List<String> history = new ArrayList<>(inst.history());
        if (!action.equals("APPROVE")) {
            history.add(cur.id() + ":REJECT -> 回退 start");
            return new Instance(inst.id(), "start", "RUNNING", history, inst.version() + 1);
        }
        history.add(cur.id() + ":APPROVE");
        String next = cur.next();
        Node nextNode = definition.get(next);
        String status = nextNode != null && nextNode.type() == NodeType.END ? "FINISHED" : "RUNNING";
        return new Instance(inst.id(), next, status, history, inst.version() + 1);
    }

    public static void main(String[] args) {
        define();
        System.out.println("【流程定义】");
        definition.values().forEach(n ->
                System.out.printf("  %-8s %-9s 审批人=%-8s -> %s%n",
                        n.id(), n.type(), n.approver() == null ? "-" : n.approver(), n.next()));

        System.out.println();
        System.out.println("【流程实例推进】");
        Instance inst = new Instance("AP-9001", "start", "RUNNING", new ArrayList<>(), 0);
        inst = approve(inst, "APPROVE", 0);
        System.out.printf("  当前节点=%s 版本=%d%n", inst.currentNode(), inst.version());
        inst = approve(inst, "APPROVE", 1);
        System.out.printf("  当前节点=%s 版本=%d%n", inst.currentNode(), inst.version());
        System.out.println("  重复提交（版本 1 已过期）：");
        approve(inst, "APPROVE", 1);
        inst = approve(inst, "APPROVE", 2);
        inst = approve(inst, "APPROVE", 3);
        System.out.printf("  最终状态=%s 历史=%s%n", inst.status(), inst.history());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展工作流引擎：支持条件分支（金额 >1 万走财务总监）、并行会签（三人全部同意才推进）、驳回回退，用三个实例验证。
 * 2. 实现"超时策略"：审批节点超过 24 小时自动通过（或驳回），用时间推进模拟验证。
 * 3. 思考题：为什么"流程定义变更"要做版本隔离？从"在途实例按旧版本继续跑"与"新实例用新版本"的兼容性分析。
 * ============================================================
 */
