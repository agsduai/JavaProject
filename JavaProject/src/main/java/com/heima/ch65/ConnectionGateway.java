/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-02
 * 课名：长连接网关：连接管理与心跳
 * ------------------------------------------------------------
 * 知识点：
 *   - **连接网关的职责**：**握手鉴权**（连接建立时校验 token，绑定 userId 与连接）、**连接注册表**（userId/deviceId -> 连接句柄，内存 Map）、**心跳保活**（客户端与服务端双向心跳，检测半开连接）、**断线清理**（心跳超时摘除连接与注册）、**路由查询**（谁在线、在哪个网关）。
 *   - **心跳的两个方向**：**客户端定时 ping**（告诉服务端我还活着）、**服务端定时探活**（写空帧或 ping，检测 TCP 半开——**半开连接是最隐蔽的问题**：网络断了但双方 socket 都没收到 FIN）。
 *   - **连接的内存账**：每连接一个 Channel 对象 + 缓冲区 + 会话状态，**10 万连接约 1~3GB**（取决于缓冲区）；**心跳频率**（30s ping / 90s 超时是常见值）——**太频繁浪费，太稀疏发现慢**。
 *   - **网关的水平扩展与路由**：多网关实例 + **路由表**（userId -> gatewayId）；**跨网关投递**（A 在网关 1，B 在网关 2 -> 网关 1 通过内部 RPC/消息把消息转给网关 2 推送）。
 *   - **与 60-06 Netty 的对应**：网关就是 Netty 的经典应用（主从 Reactor + 业务线程池）；**不要在 EventLoop 里做阻塞操作**（60-06 铁律）。
 *   - 易错点：只靠 TCP keepalive（默认 2 小时，太慢）；心跳只客户端单向（服务端挂死客户端不知道）；连接表无上限（内存爆）；网关重启不重连（用户全掉线）。
 * ============================================================
 */

package com.heima.ch65;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ConnectionGateway {
    record Conn(String connId, String userId, String deviceId, String gatewayId,
                long lastPingMs, boolean alive) {
    }

    record Gateway(String id, List<Conn> conns) {
    }

    static final long PING_TIMEOUT_MS = 90_000;

    /** 心跳扫描：超时连接摘除 */
    static List<Conn> reap(List<Conn> conns, long now) {
        List<Conn> alive = new ArrayList<>();
        for (Conn c : conns) {
            long idle = now - c.lastPingMs();
            boolean ok = c.alive() && idle <= PING_TIMEOUT_MS;
            System.out.printf("  连接 %-8s user=%-6s device=%-6s 空闲=%-7dms -> %s%n",
                    c.connId(), c.userId(), c.deviceId(), idle, ok ? "存活" : "摘除");
            if (ok) {
                alive.add(c);
            }
        }
        return alive;
    }

    /** 路由：按 userId 查所在网关（多端则多连接） */
    static Map<String, List<String>> route(List<Conn> conns) {
        Map<String, List<String>> routes = new LinkedHashMap<>();
        for (Conn c : conns) {
            routes.computeIfAbsent(c.userId(), k -> new ArrayList<>())
                    .add(c.gatewayId() + "/" + c.deviceId());
        }
        return routes;
    }

    /** 跨网关投递：本地推送或转给目标网关 */
    static String deliver(Map<String, List<String>> routes, String toUser,
                          String fromGateway) {
        List<String> targets = routes.get(toUser);
        if (targets == null) {
            return "用户 " + toUser + " 不在线 -> 转离线消息（65-07）";
        }
        List<String> local = new ArrayList<>();
        List<String> remote = new ArrayList<>();
        for (String t : targets) {
            String gw = t.split("/")[0];
            if (gw.equals(fromGateway)) {
                local.add(t);
            } else {
                remote.add(t);
            }
        }
        return "本地推送=" + local + " 跨网关转发=" + remote;
    }

    public static void main(String[] args) {
        long now = 1_000_000;
        List<Conn> conns = new ArrayList<>(List.of(
                new Conn("c-1", "u-1", "phone", "gw-1", now - 10_000, true),
                new Conn("c-2", "u-1", "pad", "gw-2", now - 20_000, true),
                new Conn("c-3", "u-2", "phone", "gw-1", now - 200_000, true),
                new Conn("c-4", "u-3", "phone", "gw-2", now - 5_000, false)));
        System.out.println("【心跳扫描】");
        List<Conn> alive = reap(conns, now);
        System.out.println();
        System.out.println("【路由表】");
        Map<String, List<String>> routes = route(alive);
        routes.forEach((u, ts) -> System.out.printf("  %-6s -> %s%n", u, ts));
        System.out.println();
        System.out.println("【投递】");
        System.out.println("  u-1：" + deliver(routes, "u-1", "gw-1"));
        System.out.println("  u-2：" + deliver(routes, "u-2", "gw-1"));
        System.out.println("  u-9：" + deliver(routes, "u-9", "gw-1"));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现连接网关：连接注册/心跳刷新/超时摘除/路由表，模拟"半开连接（alive 但长时间无心跳）"被正确摘除。
 * 2. 实现多端登录：同一 userId 多 deviceId 各自一条连接，验证"新设备登录踢旧设备"与"多端并存"两种策略。
 * 3. 思考题：为什么 TCP keepalive 不能替代应用层心跳？从默认两小时探测间隔与半开连接的检测时效分析。
 * ============================================================
 */
