/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：44-11
 * 课名：综合案例：运维 Agent（日志诊断）
 * ------------------------------------------------------------
 * 知识点：
 *   - **把全书 AI 线索收束成一个生产级 Agent**：目标"告警触发后 5 分钟内给出初步诊断"。链路：告警（42 章）-> 运维 Agent 被 @ -> ReAct 循环（44-02）：查告警详情 -> 查服务日志（MCP 工具，44-07）-> 查变更记录（41-10 发布台账）-> 查相似历史事故（RAG，40-08）-> 输出诊断报告（含置信度与建议动作，高危动作只建议不执行，44-09）。
 *   - **工具集设计（六个 MCP 工具）**：`query_alert`（告警详情）、`search_logs`（按服务/时间窗检索）、`list_releases`（最近发布）、`query_metrics`（指标切片）、`search_incidents`（历史相似事故）、`create_issue`（建跟进工单，写操作走审批）。**每个工具只读或低危——诊断 Agent 不做变更**。
 *   - **循环的真实轨迹**：轮 1 查告警确认服务与症状 -> 轮 2 拉日志发现 DB 超时模式 -> 轮 3 查发布台账发现 10 分钟前刚发布 -> 轮 4 查历史相似事故命中"连接池耗尽"预案 -> 输出诊断（根因假设 + 置信度 0.75 + 建议动作：回滚 + 扩容）。**5 分钟人工要做的第一轮排查，Agent 90 秒完成**。
 *   - **价值与边界的平衡**：Agent 产出的诊断**必须带证据链**（每条结论引用哪次工具调用）与置信度；值班人员是决策者，Agent 是分析员。**诊断 Agent 的失败不是"没找到根因"，而是"给了错误的高置信结论"**——校准置信度靠评测集（44-10）。
 *   - **上线闭环**：评测集（50 条历史事故的诊断重放）+ 回归门禁 + 灰度（先观察模式再接入值班群）+ 审计 trace 全量落库 + 点踩样本回流评测集。**第 40 章的智能客服与本章的运维 Agent 共用同一套工程底座**——这就是"AI 平台化"。
 *   - 易错点：诊断结论不带证据链（值班不敢用也不该用）；Agent 直接执行变更（诊断与处置的权限必须分离）；历史事故检索只按关键词（语义检索 + 元数据过滤才准）；没有"不知道"路径（证据不足时明确说证据不足，而不是编一个根因）。
 * ============================================================
 */

package com.heima.ch44;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class OpsAgentCase {
    record ToolCall(int round, String tool, String args, String result) {
    }

    static void diagnosticTrace() {
        System.out.println("【告警：order 服务错误率 4%，Agent 被触发】");
        List<ToolCall> trace = List.of(
                new ToolCall(1, "query_alert", "{alert:AL-3391}",
                        "order 服务 5xx 4%（阈值1%），持续 3 分钟，P99 正常"),
                new ToolCall(2, "search_logs", "{service:order,window:10m,level:ERROR}",
                        "top 模式：stock 服务连接超时（占 83%）"),
                new ToolCall(3, "query_metrics", "{service:stock,metric:db_pool}",
                        "stock 连接池使用率 98%，lag 无异常"),
                new ToolCall(4, "list_releases", "{service:stock,window:1h}",
                        "11 分钟前发布 stock:2.1.0-77"),
                new ToolCall(5, "search_incidents", "{symptom:连接池耗尽+发布相关}",
                        "历史 INC-301：发布引入慢查询 -> 连接池耗尽（相似度 0.81）"),
                new ToolCall(6, "create_issue", "{title:stock 2.1.0 疑似连接泄漏}",
                        "工单 OPS-1204 已创建（待认领）"));
        for (ToolCall t : trace) {
            System.out.printf("  轮%d %-16s(%s)%n      -> %s%n",
                    t.round(), t.tool(), t.args(), t.result());
        }
        System.out.println("  全程 87 秒，6 轮，token 消耗 21K（预算内）");
        System.out.println();
    }

    static void reportWithEvidence() {
        System.out.println("【诊断报告：每条结论都带证据链】");
        Map<String, String> report = new LinkedHashMap<>();
        report.put("根因假设", "stock:2.1.0-77 引入连接泄漏（置信度 0.75）");
        report.put("证据1", "轮2：错误日志 83% 集中在 stock 连接超时");
        report.put("证据2", "轮3：stock 连接池 98%，其他资源正常");
        report.put("证据3", "轮4：发布时间与故障起点相差 11 分钟");
        report.put("证据4", "轮5：历史 INC-301 相似度 0.81，同型根因");
        report.put("建议动作", "① 回滚 stock 至 2.0.9-76（人执行）② 观察连接池曲线 ③ 认领 OPS-1204");
        report.put("不确定声明", "未直接确认泄漏代码路径，需值班确认堆栈后定论");
        for (Map.Entry<String, String> e : report.entrySet()) {
            System.out.printf("  %-8s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  原则：Agent 是分析员，值班是决策者；证据不足就明说");
        System.out.println();
    }

    static void launchChecklist() {
        System.out.println("【上线闭环：评测 + 灰度 + 审计】");
        List<String> items = List.of(
                "评测集：50 条历史事故重放（诊断命中率、置信度校准）",
                "回归门禁：诊断质量回退 >5% 拦截（Prompt/模型变更都跑）",
                "灰度：先观察模式（报告只发不发建议）-> 值班群接入",
                "权限：全部只读工具 + create_issue 走审批；不做任何变更",
                "审计：trace 全量落库，点踩样本回流评测集",
                "成本：单次诊断 ≤25K token，超预算终止并告警");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  与 40 章智能客服共用底座：网关(44-08)/评测(44-10)/审计(44-09) 全复用");
    }

    public static void main(String[] args) {
        diagnosticTrace();
        reportWithEvidence();
        launchChecklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现运维 Agent 的最小闭环（模拟模型与工具）：告警输入 -> ReAct 诊断 -> 带证据链报告，注入一次"证据不足"场景验证"不知道"路径。
 * 2. 用 5 条历史告警重放评测你的 Agent（可虚构），统计诊断命中率与平均轮数，写一条优化结论。
 * 3. 思考题：为什么诊断 Agent"给错误的高置信结论"比"找不到根因"更危险？从值班决策依赖与信任校准两个角度分析置信度为何必须被评测校准。
 * ============================================================
 */
