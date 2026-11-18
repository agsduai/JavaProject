/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：48-07
 * 课名：流式服务与连接管理
 * ------------------------------------------------------------
 * 知识点：
 *   - **推理的流式连接是长连接**：一个请求占连接 5~60 秒（生成期间）——**连接数 ≈ 并发请求数**，容量账多一条“每副本最大并发流”（受引擎批上限约束，48-03）。
 *   - **断流与续传**：客户端网络抖动断开 -> 引擎侧生成是否继续？**策略**：断开即取消（省算力，用户刷新重问）或服务端缓冲续传（SSE Last-Event-ID，40-04）——**按业务选择**：客服场景“断开即取消”更省成本，产品文案场景“缓冲续传”体验更好。
 *   - **背压与取消的传播**：用户关闭页面 -> 客户端断流 -> 网关 -> 引擎取消该请求（释放 KV 与批槽位）——**取消链路必须打通**，否则“用户走了 GPU 还在算”（成本黑洞）。44-08 超时预算的终止传播。
 *   - **SSE 生产的推理特有问题**：心跳与生成间隙（长思考阶段无 token 输出 -> 中间设备断连，40-04 的 ping 复用）；错误事件格式统一（引擎错误 -> 网关转译成业务错误事件）。
 *   - **多模态/长上下文的连接画像**：输入大（上传图片/prompt 长）时 TTFT 长，连接更久——**连接超时与读超时要按画像配置**，不能套普通 API 的 30s。
 *   - 易错点：取消不传播（幽灵计算烧钱）；中间设备断长连接（无心跳）；SSE 缓冲被网关开启（40-04 的坑在推理网关重现）；连接超时照抄普通服务配置（长生成被掐）。
 * ============================================================
 */

package com.heima.ch48;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ServingStreaming {
    static void connectionMath() {
        System.out.println("【长连接的容量账】");
        double avgDurationSec = 15;
        int concurrentStreams = 32;
        System.out.printf("  单副本并发流 %d，平均时长 %.0fs -> 每秒新连接承载 ≈ %.1f 个%n",
                concurrentStreams, avgDurationSec, concurrentStreams / avgDurationSec);
        System.out.printf("  5000 tok/s 集群（平均输出 300）≈ %.0f 请求/秒 -> 需要 %.0f 个并发流槽位%n",
                5000 / 300.0, 5000 / 300.0 * avgDurationSec);
        System.out.println("  并发流槽位 = 引擎批上限（48-03 压测得出），网关排队长度与之匹配");
        System.out.println();
    }

    static void cancelPropagation() {
        System.out.println("【取消传播：用户走了，算力要停】");
        List<String> chain = List.of(
                "用户关闭页面 -> 客户端断开 SSE",
                "网关检测断连 -> 向引擎发送取消（abort 请求）",
                "引擎释放该请求的批槽位与 KV（PagedAttention 回收）",
                "计费只算已生成 token（44-08 归因）",
                "指标：cancelled_ratio —— 突增说明产品断流率高或网关误判");
        for (String s : chain) {
            System.out.println("  " + s);
        }
        System.out.println("  反例：取消链路缺失 -> 幽灵生成：用户已离开，GPU 继续算 30s");
        System.out.println();
    }

    static void keepalivePolicy() {
        System.out.println("【心跳与超时的推理特调】");
        String[][] rows = {
                {"SSE 心跳", "生成间隙（长思考/重试）发 : ping", "40-04 同款，防中间设备断连"},
                {"读超时", "网关 120s（按输出画像）", "普通 API 的 30s 会掐长生成"},
                {"空闲超时", "60s 无任何字节 -> 断开", "防挂死连接占槽位"},
                {"断开策略", "客服=取消即止；文案=服务端缓冲续传", "按业务成本与体验选择"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-30s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void sseGateway() {
        System.out.println("【推理网关的 SSE 转译职责】");
        List<String> duties = List.of(
                "协议转换：引擎事件 -> 统一业务事件（delta/usage/error）",
                "usage 尾包：本次请求 tokens_in/out（44-08 记账）",
                "错误转译：引擎 500/OOM -> 业务可理解的错误事件（40-02 分类）",
                "缓冲关闭：X-Accel-Buffering: no（40-04 陷阱在推理网关重现）",
                "Last-Event-ID 续传：按业务策略启用（40-04）");
        for (String d : duties) {
            System.out.println("  - " + d);
        }
        System.out.println("  网关是推理服务的“协议适配层 + 记账层”，与 44-08 AI 网关合一");
    }

    public static void main(String[] args) {
        connectionMath();
        cancelPropagation();
        keepalivePolicy();
        sseGateway();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 按你的流量画像计算并发流槽位需求，并与引擎批上限对齐，写出网关排队配置。
 * 2. 实现取消传播闭环（模拟）：客户端断开 -> 网关 -> 引擎取消 -> 资源释放，验证“取消后不再计费”。
 * 3. 思考题：为什么“断开即取消”与“缓冲续传”没有绝对正确答案？用客服与文案两个场景的成本/体验权衡说明。
 * ============================================================
 */
