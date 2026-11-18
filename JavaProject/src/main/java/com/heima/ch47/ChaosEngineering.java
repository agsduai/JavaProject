/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：47-10
 * 课名：混沌工程
 * ------------------------------------------------------------
 * 知识点：
 *   - **混沌工程的定义：在受控条件下主动注入故障，验证系统韧性的科学方法**——不是“乱搞生产”，而是有稳态假设、有实验假设、有中止条件的实验设计（47-03 的故障注入原语 + 41-11 演练纪律 + 42-11 复盘方法）。
 *   - **四步实验法**：① 定义稳态（正常时的指标基线）；② 假设（注入 X 后系统仍满足稳态）；③ 注入（延迟/错误/资源耗尽/节点消灭）；④ 观察与验证（假设成立或发现缺陷）。**每次实验产出要么是“韧性确认”，要么是“改进项”**。
 *   - **实验的爆炸半径控制**：从最小流量开始（金丝雀注入）、非高峰时段、可随时中止（一键停止）、生产前先在预发验证。**“第一次混沌实验打挂生产”是教训不是方法论**——Netflix 也是从基础设施演练开始的。
 *   - **注入的层次**：基础设施层（杀节点/磁盘满）、网络层（延迟/分区/丢包）、应用层（依赖超时/错误注入/时钟跳变）、数据层（慢查询/主从延迟放大）。**从低层开始逐层深入**。
 *   - **实验对象优先级**：依赖最多且无降级的服务 > 刚上线的弹性配置 > 新的容灾机制。**每次大事故后的改进项都应该变成下一次实验的验证目标**（42-11 复盘的闭环）。
 *   - **组织形态**：混沌实验要审批与值守（像发布一样走流程，41-10）；游戏日（game day）团队协作演练；实验结果入库形成韧性知识库。
 *   - 易错点：无稳态假设的“乱注入”（出事了不知道是不是注入导致）；在生产高峰做实验；实验发现缺陷不跟进修复（白炸）；把混沌当猎奇（没有把改进项闭环到 backlog）。
 * ============================================================
 */

package com.heima.ch47;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ChaosEngineering {
    record Experiment(String name, String steadyState, String hypothesis, String inject,
                      String abort, String outcome) {
    }

    static void experimentDesign() {
        System.out.println("【实验设计：下单依赖库存超时韧性】");
        List<Experiment> exps = List.of(
                new Experiment("EXP-01 库存延迟",
                        "下单成功率>99.5%，P99<300ms",
                        "注入 200ms 延迟后仍满足稳态",
                        "Mesh 故障注入：延迟+200ms，比例 20%",
                        "成功率<99% 立即中止",
                        "假设成立：重试+降级生效，P99+40ms"),
                new Experiment("EXP-02 库存错误",
                        "同上",
                        "5% 503 注入下熔断按预期打开",
                        "错误注入：503 比例 5%",
                        "错误率>5% 或大面积客诉",
                        "发现缺陷：熔断阈值过宽 -> 调整为连续 5 次"),
                new Experiment("EXP-03 节点消灭",
                        "同上",
                        "随机杀 1 个 order Pod 无感",
                        "kill -9 + K8s 自愈",
                        "副本不可用超 2 分钟",
                        "韧性确认：30s 内新 Pod 接管"));
        for (Experiment e : exps) {
            System.out.printf("  %s%n    稳态：%s%n    假设：%s%n    注入：%s%n    中止：%s%n    结果：%s%n",
                    e.name(), e.steadyState(), e.hypothesis(), e.inject(), e.abort(), e.outcome());
        }
        System.out.println();
    }

    static void blastRadius() {
        System.out.println("【爆炸半径控制】");
        List<String> controls = List.of(
                "时间：非高峰 + 值班在岗 + 变更冻结期外",
                "范围：金丝雀比例注入（5% 起），只影响部分用户",
                "中止：一键停止注入 + 自动中止阈值（成功率跌破）",
                "预演：相同实验先在 staging 完整跑通",
                "审批：实验单审批（41-10 发布单同构）");
        for (String c : controls) {
            System.out.println("  - " + c);
        }
        System.out.println("  红线：资金链路首次实验必须在预发；生产实验最小化用户影响");
        System.out.println();
    }

    static void closedLoop() {
        System.out.println("【实验产出进闭环：韧性的持续改进】");
        Map<String, String> loop = new LinkedHashMap<>();
        loop.put("EXP-02 发现熔断过宽", "改进项：调整阈值 -> 下次实验验证 -> 关闭");
        loop.put("EXP-01 确认降级有效", "知识库：记录“stock 延迟 200ms 无影响”作为容量依据");
        loop.put("历史事故 INC-456", "复现实验：连接池耗尽注入 -> 验证修复（42-11）");
        for (Map.Entry<String, String> e : loop.entrySet()) {
            System.out.printf("  %-20s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  判定：每次事故的修复，都应有对应的混沌实验证明它真的修好了");
    }

    public static void main(String[] args) {
        experimentDesign();
        blastRadius();
        closedLoop();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 设计并执行（或桌面推演）三个混沌实验（延迟/错误/资源耗尽），完整写出四步实验法文档。
 * 2. 为实验平台实现“一键中止”与自动中止阈值（成功率跌破即停），并用模拟故障验证。
 * 3. 思考题：为什么“无稳态假设的注入”是危险的？从事故归因与实验可信度两个角度分析稳态基线的作用。
 * ============================================================
 */
