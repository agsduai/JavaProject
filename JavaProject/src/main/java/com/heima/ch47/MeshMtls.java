/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：47-04
 * 课名：mTLS 与零信任
 * ------------------------------------------------------------
 * 知识点：
 *   - **零信任的网格化落地**：内网不等于可信（36 章信任边界原则）——Mesh 里**每个服务都有身份（SPIFFE 身份）**，通信默认双向 TLS，授权按"服务身份"而不是 IP。**IP 会变，身份不变**。
 *   - **mTLS 的自动证书管理**：控制面作为 CA 给每个 Sidecar 签发短周期证书（如 24h），自动轮换——**人工管证书的时代结束**。握手由 Sidecar 完成，业务无感。
 *   - **PERMISSIVE 到 STRICT 的迁移**：PERMISSIVE 同时接受明文与 mTLS（过渡期），遥测显示全量加密后切 STRICT（拒绝明文）——**灰度式安全收紧**，与 47-01 三阶段对接。
 *   - **授权策略（AuthorizationPolicy）**：按身份授权——"只有 order 服务可以调 stock.deduct"——**服务间调用的最小权限**（44-09 的四道闸在基础设施层的实现）。拒绝记录可审计。
 *   - **mTLS 的性能与故障面**：握手与加解密开销（毫秒级，连接复用后摊薄）；证书轮换失败的故障模式（Sidecar 证书过期 -> 全网 503）——**证书体系本身要有监控与告警**。
 *   - **与业务安全的关系**：mTLS 解决"传输与身份"，不解决"业务鉴权"（用户级权限仍是业务/网关职责，36 章 JWT）——**两层正交，不互相替代**。
 *   - 易错点：STRICT 切换太急（有服务还没注入 Sidecar，明文流量全断）；把 mTLS 当业务鉴权（服务间身份≠用户身份）；证书轮换窗口无监控（过期事故深夜爆发）；AuthorizationPolicy 过宽（allow all 走形式）。
 * ============================================================
 */

package com.heima.ch47;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class MeshMtls {
    record Identity(String service, String spiffe, String certTtl) {
    }

    static void identityModel() {
        System.out.println("【服务身份：SPIFFE 替代 IP】");
        List<Identity> ids = List.of(
                new Identity("order", "spiffe://prod/ns/prod/sa/order", "24h 自动轮换"),
                new Identity("stock", "spiffe://prod/ns/prod/sa/stock", "24h 自动轮换"),
                new Identity("legacy-job", "未注入 Sidecar -> 无身份", "STRICT 下将被拒绝"));
        for (Identity i : ids) {
            System.out.printf("  %-10s %s（证书 %s）%n", i.service(), i.spiffe(), i.certTtl());
        }
        System.out.println("  Pod IP 重建即变；SPIFFE 身份随服务账号稳定 —— 授权的可靠锚点");
        System.out.println();
    }

    static void strictMigration() {
        System.out.println("【PERMISSIVE -> STRICT 的灰度迁移】");
        List<String> steps = List.of(
                "1) 全网 PERMISSIVE：明文与 mTLS 双收，业务无感",
                "2) 遥测统计：明文流量占比逐日下降（Sidecar 注入覆盖扩大）",
                "3) 明文占比 < 1%%：找出残留明文方（未注入的老服务）",
                "4) 切 STRICT：拒绝一切明文 -> 有明文依赖的服务立刻暴露",
                "5) 回退开关：STRICT -> PERMISSIVE（配置级秒回）");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void authzPolicy() {
        System.out.println("【授权策略：服务间最小权限】");
        record Rule(String target, String from, String action, String verdict) {
        }
        List<Rule> rules = List.of(
                new Rule("stock.deduct", "仅 order 服务", "ALLOW", "核心写接口收紧"),
                new Rule("stock.query", "order + reporting", "ALLOW", "只读放开给报表"),
                new Rule("stock.admin", "任何人", "DENY", "网格层直接拒绝"),
                new Rule("stock.*", "guest-job（无身份）", "DENY（STRICT 默认）", "无身份即无信任"));
        for (Rule r : rules) {
            System.out.printf("  %-12s 来自 %-16s %s -> %s%n",
                    r.target(), r.from(), r.action(), r.verdict());
        }
        System.out.println("  拒绝日志进审计（44-09 trace 思想）：谁试图越权一目了然");
        System.out.println();
    }

    static void layering() {
        System.out.println("【mTLS 与业务安全的分层】");
        List<String> layers = List.of(
                "传输与身份层（Mesh）：服务间 mTLS + 服务级授权 —— “谁在调我”",
                "业务鉴权层（网关/应用）：用户 JWT、角色权限 —— “用户能干什么”",
                "数据层（DB）：行级权限、字段脱敏 —— “能看哪些数据”");
        for (String l : layers) {
            System.out.println("  " + l);
        }
        System.out.println("  正交不替代：mTLS 通过不代表用户已鉴权；JWT 有效不代表调用方服务可信");
    }

    public static void main(String[] args) {
        identityModel();
        strictMigration();
        authzPolicy();
        layering();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在你的网格（或模拟）完成 PERMISSIVE -> STRICT 迁移演练，统计明文流量下降曲线与 STRICT 切换后的拒绝日志。
 * 2. 写出核心链路的 AuthorizationPolicy 矩阵（至少 4 条规则），演示一次"未注入服务被拒绝"的审计记录。
 * 3. 思考题：为什么服务间 mTLS 不能替代用户级 JWT 鉴权？从身份主体（服务 vs 用户）与信任传递的断点分析。
 * ============================================================
 */
