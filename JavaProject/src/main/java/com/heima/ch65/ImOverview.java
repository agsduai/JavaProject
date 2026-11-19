/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-01
 * 课名：IM 系统全景：连接、消息、会话
 * ------------------------------------------------------------
 * 知识点：
 *   - **IM 与 HTTP 服务的根本差异是"服务端主动推送"**：HTTP 是"客户端问、服务端答"；IM 是"A 发消息，服务端要主动推给 B"——**必须有长连接**（WebSocket/TCP 自研协议），否则只能靠轮询（延迟高、浪费大）。
 *   - **IM 的四大子系统**：**连接层**（长连接网关：握手、心跳、断线重连）、**消息层**（可靠投递：ACK、重传、去重、顺序）、**会话层**（会话列表、已读、未读数、多端同步）、**存储层**（消息漫游、离线消息、历史查询）。
 *   - **三个核心难点**：**可靠性**（消息不丢不重，43/62 章语义在 IM 的强化）、**一致性**（多端同步：手机读过，平板也要显示已读）、**扩展性**（10 万长连接、群聊 500 人扇出）。
 *   - **与 47-02 WebSocket 的关系**：47-02 讲了 WebSocket 握手与心跳（传输层）；本章讲"建在长连接之上的消息系统"（应用层语义）——**传输与业务分层**。
 *   - **IM 的存储模型**：**消息表按会话分片**（conversationId 为 key，同会话消息物理相邻，便于拉取最近 N 条）；**每用户一个收件箱**（写扩散）或**只存会话流水**（读扩散）——65-06 的核心权衡。
 *   - 易错点：用轮询做"实时"（延迟与成本双输）；消息只存最新状态（聊天记录丢失）；没有消息序号（乱序、重复无法判断）；群聊按成员逐条复制（写放大爆炸）。
 * ============================================================
 */

package com.heima.ch65;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ImOverview {
    record Subsystem(String name, String duty, String keyProblem) {
    }

    static void subsystems() {
        System.out.println("【IM 四大子系统】");
        List<Subsystem> subs = List.of(
                new Subsystem("连接层", "握手、心跳、断线重连", "10 万连接的内存与调度"),
                new Subsystem("消息层", "ACK、重传、去重、顺序", "不丢不重不乱序"),
                new Subsystem("会话层", "会话列表、已读、未读、多端", "多端状态一致"),
                new Subsystem("存储层", "漫游、离线、历史查询", "海量消息的读写模型"));
        for (Subsystem s : subs) {
            System.out.printf("  %-6s %-24s 难点：%s%n", s.name(), s.duty(), s.keyProblem());
        }
        System.out.println();
    }

    static void pushVsPolling() {
        System.out.println("【推送 vs 轮询】");
        String[][] rows = {
                {"轮询（每秒）", "1s 平均延迟", "10 万在线 = 每秒 10 万次请求"},
                {"长轮询", "秒级，且连接占用", "仍是「请求-响应」模型"},
                {"长连接推送", "毫秒级", "维持 10 万连接（内存 + 心跳）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s 延迟=%-20s 代价=%s%n", r[0], r[1], r[2]);
        }
        System.out.println("  结论：实时 IM 必须长连接；轮询只用于降级兜底");
        System.out.println();
    }

    static void keyConcepts() {
        System.out.println("【三个必须记住的概念】");
        Map<String, String> concepts = new LinkedHashMap<>();
        concepts.put("会话（conversation）", "单聊是双人会话，群聊是多人会话；消息按会话聚合");
        concepts.put("消息序号（seq）", "会话内单调递增；用于排序、去重、断线补偿");
        concepts.put("收件箱（inbox）", "每个用户能看到的消息引用列表；写扩散或读扩散（65-06）");
        concepts.forEach((k, v) -> System.out.printf("  %-18s %s%n", k, v));
    }

    public static void main(String[] args) {
        subsystems();
        pushVsPolling();
        keyConcepts();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 画出你理解的 IM 四层架构（连接/消息/会话/存储），标注每层的数据流与存储位置。
 * 2. 对比"每秒轮询"与"长连接推送"在 10 万在线用户下的请求量与延迟，量化差异。
 * 3. 思考题：为什么"消息序号"必须是"会话内"而非"全局"递增？从单会话有序需求与全局序号瓶颈分析。
 * ============================================================
 */
