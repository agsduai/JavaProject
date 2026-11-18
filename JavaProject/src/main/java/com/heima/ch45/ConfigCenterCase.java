/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-11
 * 课名：综合案例：配置中心的高可用设计
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章共识知识收束成一个生产组件**：配置中心（29 章已用过 Nacos 视角）的底座设计——**Raft 集群存配置元数据（45-03/04）+ MVCC 与 watch 推送（45-06）+ 分级读（45-02）+ 变更审计（41 章思想）+ 客户端本地快照兜底**。
 *   - **写路径**：控制台变更 -> 写请求 -> Raft 过半提交 -> 版本递增 -> 长轮询/gRPC 流推送变更到客户端 -> 客户端落地本地快照。**推送失败靠客户端定期全量拉齐兜底**（推拉结合）。
 *   - **读路径分级**：业务进程启动时全量拉取（线性一致读一次）；运行中读本地缓存（微秒级，最终一致）；配置变更后热更新（watch 推送 + 版本号确认）。**业务热路径零共识开销**——共识只发生在配置变更瞬间。
 *   - **容灾设计**：Raft 集群挂 -> 客户端用**本地快照继续跑**（配置不常变，旧配置可用性高于准确性——PACELC 的业务化选择）；恢复后按版本对齐。**"客户端自持快照"是配置中心与传统数据库的最大区别**。
 *   - **脑裂防护**：客户端只接受**版本号单调递增**的配置（fencing 思想，45-08）——分区期旧实例的推送（旧版本）被拒；同版本冲突靠 hash 校验。
 *   - **灰度与回滚**：配置变更按实例灰度（41-07 金丝雀思想）+ 一键回滚到任意历史版本（MVCC 天然支持）+ 变更审计（谁改的、改了什么、谁审批的）。
 *   - 易错点：客户端本地快照缺失（配置中心挂 = 全公司进程起不来）；推送依赖长轮询无超时兜底（推送丢失后配置永久不更新）；热更新不校验 schema（一个 typo 全线崩）；配置变更无灰度直接全量（错误的连接串推到全部实例）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ConfigCenterCase {
    /** 服务端：Raft 底座上的配置表 + 版本 */
    static class ConfigServer {
        final Map<String, String> data = new LinkedHashMap<>();
        long version = 0;
        final List<String> audit = new ArrayList<>();

        void put(String key, String value, String operator) {
            // 简化：假设已过半提交
            data.put(key, value);
            version++;
            audit.add(String.format("v%d %s 改 %s=%s", version, operator, key, value));
            System.out.printf("  [服务端] v%d 提交：%s=%s（操作人 %s）%n", version, key, value, operator);
        }

        String readLinear() {
            return "v" + version;
        }
    }

    /** 客户端：本地快照 + 版本单调 + 推拉结合 */
    static class ConfigClient {
        final Map<String, String> local = new LinkedHashMap<>();
        long localVersion = 0;

        void bootstrap(ConfigServer server) {
            local.putAll(server.data);
            localVersion = server.version;
            System.out.printf("  [客户端] 启动全量拉取：%s（线性一致读 %s）%n",
                    local, server.readLinear());
        }

        void onPush(String key, String value, long version) {
            if (version <= localVersion) {
                System.out.printf("  [客户端] 拒绝 v%d（本地已是 v%d）—— fencing 防旧实例推送%n",
                        version, localVersion);
                return;
            }
            local.put(key, value);
            localVersion = version;
            System.out.printf("  [客户端] 热更新 v%d：%s=%s + 落地本地快照%n", version, key, value);
        }

        void pullFallback(ConfigServer server) {
            if (server.version > localVersion) {
                onPush(null, null, server.version);
            }
        }
    }

    static void fullFlow() {
        System.out.println("【一次配置变更的完整链路】");
        ConfigServer server = new ConfigServer();
        server.put("db.url", "jdbc:mysql://db-a:3306/order", "ops-wang");
        ConfigClient client = new ConfigClient();
        client.bootstrap(server);
        System.out.println("  —— 10 分钟后修改连接串 ——");
        server.put("db.url", "jdbc:mysql://db-b:3306/order", "ops-wang");
        client.onPush("db.url", server.data.get("db.url"), server.version);
        System.out.println("  业务热路径全程未碰共识集群（本地缓存读取）");
        System.out.println();
    }

    static void disasterRecovery() {
        System.out.println("【容灾：Raft 集群挂了业务怎么办】");
        List<String> timeline = List.of(
                "T0 Raft 集群 3 台全挂（机房故障）",
                "T1 客户端推送失败 -> 静默重试，继续用本地快照服务",
                "T2 业务进程照常启动（新实例读本地快照文件）",
                "T3 恢复后按版本对齐：本地 v42 < 服务端 v43 -> 全量拉取",
                "T4 审计复盘：确认快照落盘策略（每版本落盘 + 保留 5 份）");
        for (String t : timeline) {
            System.out.println("  " + t);
        }
        System.out.println("  设计哲学：配置的可用性（旧配置能跑）优先于绝对最新——业务化 PACELC");
        System.out.println();
    }

    static void checklist() {
        System.out.println("【配置中心上线检查单】");
        List<String> items = List.of(
                "Raft 3~5 台专用部署，SSD，监控 commit P99 与换届",
                "客户端启动全量 + 运行热更新 + 定期兜底拉齐",
                "本地快照落盘（版本单调/fencing），防旧实例推送",
                "变更审计 + 按历史版本一键回滚（MVCC）",
                "schema 校验：变更先过格式与连接性检查再提交",
                "灰度：按实例比例生效，观察指标再全量",
                "演练：杀掉全部服务端节点，验证业务自持快照存活");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        fullFlow();
        disasterRecovery();
        checklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现配置中心迷你版（服务端 + 客户端）：版本单调校验、本地快照落盘、拉齐兜底，并完成"杀掉全部服务端"演练记录。
 * 2. 给配置变更加 schema 校验与灰度（按实例尾号生效），用错误的 JDBC 串演示"校验拦截"与"灰度爆炸半径限制"。
 * 3. 思考题：为什么配置中心客户端"自持快照"是它的可用性核心？对比 43-05 流作业 checkpoint 与 45-08 fencing，说明三者在"状态自持 + 版本防御"上的共同思想。
 * ============================================================
 */
