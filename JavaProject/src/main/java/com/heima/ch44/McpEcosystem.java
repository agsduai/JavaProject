/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：44-07
 * 课名：MCP 与工具生态
 * ------------------------------------------------------------
 * 知识点：
 *   - **MCP（Model Context Protocol）解决的问题是"N 个应用 × M 个工具"的 M×N 集成爆炸**：每个 AI 应用都要重写一遍"怎么调数据库/怎么发邮件/怎么查 Jira"。MCP 把工具做成**标准化服务（MCP Server）**，任何支持协议的客户端（MCP Host）即插即用——**M×N 变成 M+N**。
 *   - **MCP 的三个原语**：**Tools**（模型可调用的函数，等同 40-06 的工具）、**Resources**（可读取的数据资源，如文件/记录）、**Prompts**（预置提示模板）。客户端发现 Server 能力（能力协商）后把工具清单注入模型。
 *   - **协议形态**：stdio（本地进程，最简单）与 HTTP+SSE（远程服务）。Server 是独立进程——**Java 写 MCP Server 就是写一个标准化工具服务**，工具实现仍是你的 Java 代码，MCP 只是壳。
 *   - **MCP 改变的工程格局**：工具的**复用与治理**集中化——权限、审计、限流在 Server 层统一做（44-09 的权限检查下沉到工具服务）；工具升级与应用解耦。
 *   - **生态与自建的分界**：通用能力（GitHub/Jira/数据库/文件系统）用社区 Server；核心业务工具（查订单、退款）自建 Server 并纳入正常研发流程（测试/评审/监控）。**自建 Server 也是生产服务**，不是脚本。
 *   - **安全是 MCP 的第一议题**：接入第三方 Server = 给 Agent 新的能力，**能力协商清单要人工审批**；Server 返回的内容也是"输入"（可能带注入，40-03 的防线照用）；工具描述（description）被模型信任，恶意 Server 可以诱导调用。
 *   - 易错点：把 MCP Server 当轻量脚本（无鉴权无审计直接上）；接入社区 Server 不审权限（Agent 获得了删除文件能力）；工具描述含糊导致模型乱选（40-06 的经验在 MCP 同样适用）；本地 stdio Server 无并发控制（阻塞 Agent 循环）。
 * ============================================================
 */

package com.heima.ch44;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class McpEcosystem {
    /** MCP Server 的能力清单（模型可见的契约） */
    record McpTool(String name, String desc, String scope, String approval) {
    }

    static void capabilityHandshake() {
        System.out.println("【能力协商：Host 发现 Server 的工具清单】");
        List<McpTool> tools = List.of(
                new McpTool("jira_query", "按 JQL 查询工单", "read:jira", "预审批通过"),
                new McpTool("db_query", "执行只读 SQL", "read:dw", "预审批通过"),
                new McpTool("jira_transition", "变更工单状态", "write:jira", "每次调用需确认"),
                new McpTool("fs_delete", "删除文件", "write:fs", "拒绝接入（高危）"));
        System.out.println("  Server: jira-server + internal-db-server");
        for (McpTool t : tools) {
            System.out.printf("  %-16s %-16s 权限域=%-10s 接入=%s%n",
                    t.name(), t.desc(), t.scope(), t.approval());
        }
        System.out.println("  接入策略：能力清单人工审批，高危工具直接拒绝");
        System.out.println();
    }

    static void callFlow() {
        System.out.println("【一次 MCP 工具调用的完整链路】");
        List<String> steps = List.of(
                "1) Host 把已接入工具的清单（name/desc/schema）注入模型",
                "2) 模型输出 tool_use: {name: db_query, args: {sql: ...}}",
                "3) Host 权限检查（scope 匹配 + 敏感工具二次确认）",
                "4) Host 经协议调用 Server -> Server 执行返回结果",
                "5) 结果作为 tool_result 回传模型（40-06 循环不变）");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  对模型而言 MCP 工具与内置工具无差别；差别在治理层");
        System.out.println();
    }

    static void serverAsService() {
        System.out.println("【自建 MCP Server = 生产服务】");
        Map<String, String> checklist = new LinkedHashMap<>();
        checklist.put("鉴权", "token 校验 + 按调用方的 scope 限权");
        checklist.put("审计", "每次调用记录调用方/参数/耗时");
        checklist.put("限流", "防 Agent 循环打爆下游（44-02 护栏外的第二道）");
        checklist.put("测试", "工具实现走正常单测 + 集成测试");
        for (Map.Entry<String, String> e : checklist.entrySet()) {
            System.out.printf("  %-4s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  社区 Server 用于通用能力；业务 Server 必须进正常研发流程");
        System.out.println();
    }

    static void securityNotes() {
        System.out.println("【MCP 特有的安全点】");
        List<String> notes = List.of(
                "工具描述是模型的输入：恶意 Server 可写诱导性描述 -> 接入前人工审阅",
                "Server 返回内容也是输入：防二次注入（40-03 包裹 + 输出校验）",
                "权限最小化：read Server 不给 write scope，宁可多建 Server",
                "版本锁定：Server 升级 = 能力变化，变更走审批");
        for (String n : notes) {
            System.out.println("  - " + n);
        }
    }

    public static void main(String[] args) {
        capabilityHandshake();
        callFlow();
        serverAsService();
        securityNotes();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用官方 SDK（或手写 stdio 协议）实现一个业务 MCP Server（如"查订单状态"），完成能力清单、鉴权与审计日志。
 * 2. 设计你团队的 MCP 接入审批表：第三方 Server 的工具清单、权限域、审批结论，演示一次"高危工具拒绝接入"。
 * 3. 思考题：为什么 MCP 的价值是"M×N 变 M+N"？对比接入前后每新增一个 AI 应用/一个工具的开发工作量。
 * ============================================================
 */
