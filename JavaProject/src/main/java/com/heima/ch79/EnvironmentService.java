/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-06
 * 课名：环境即服务
 * ------------------------------------------------------------
 * 知识点：
 *   - **环境是研发效能最大的等待源之一**：**测试环境排队、配置漂移、环境被占着不用**——**环境即服务（EaaS）把环境变成"按需申请、自动创建、到期回收"的资源**（79-01 的瓶颈）。
 *   - **环境模板**：**一个环境 = 一组服务版本 + 配置 + 数据快照**——**模板化才能"一键复制"，避免手工搭环境**（79-02 的脚手架思想）。
 *   - **状态机**：`REQUESTED -> PROVISIONING -> READY -> EXPIRING -> RECLAIMED`（失败 -> `FAILED`）——**每次状态迁移要可审计**；**TTL 到期自动回收**（防止资源泄漏）。
 *   - **隔离级别**：**独占环境**（贵但干净）、**共享环境**（便宜但互相污染）、**按分支临时环境**（PR 环境）——**按测试类型选**。
 *   - 与 37/41 章的关系：37 章 Docker 提供"环境一致性"，**本章讲"环境怎么按需供给与回收"**——**容器是砖，EaaS 是楼**。
 *   - 易错点：环境手工创建（漂移）；不设 TTL（永远占用）；共享环境跑破坏性测试；环境与代码版本不对应（测了个旧版本）。
 * ============================================================
 */

package com.heima.ch79;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class EnvironmentService {
    enum State { REQUESTED, PROVISIONING, READY, EXPIRING, RECLAIMED, FAILED }

    static class Environment {
        final String id;
        final String owner;
        final String template;
        final String version;
        State state = State.REQUESTED;
        long createdAt;
        long ttlMs;
        final List<String> services = new ArrayList<>();

        Environment(String id, String owner, String template, String version,
                    long createdAt, long ttlMs) {
            this.id = id;
            this.owner = owner;
            this.template = template;
            this.version = version;
            this.createdAt = createdAt;
            this.ttlMs = ttlMs;
        }

        long expiresAt() {
            return createdAt + ttlMs;
        }

        void report() {
            System.out.printf("  %-10s owner=%-6s template=%-10s version=%-8s state=%-12s 服务=%s%n",
                    id, owner, template, version, state, services);
        }
    }

    static final Map<String, Environment> envs = new LinkedHashMap<>();
    static long now = 1_000_000;
    static long nextId = 1;

    static Environment request(String owner, String template, String version, long ttlMs) {
        String id = "env-" + nextId++;
        Environment env = new Environment(id, owner, template, version, now, ttlMs);
        envs.put(id, env);
        System.out.printf("  申请 %s（%s/%s，TTL=%d 分钟）%n", id, template, version, ttlMs / 60_000);
        return env;
    }

    /** 推进：REQUESTED -> PROVISIONING -> READY（模拟异步创建） */
    static void tick() {
        for (Environment e : envs.values()) {
            if (e.state == State.REQUESTED) {
                e.state = State.PROVISIONING;
                e.services.add("mysql");
                e.services.add("redis");
                e.services.add("app:" + e.version);
                System.out.println("  " + e.id + " -> PROVISIONING（创建 " + e.services + "）");
            } else if (e.state == State.PROVISIONING) {
                e.state = State.READY;
                System.out.println("  " + e.id + " -> READY");
            }
        }
    }

    /** 回收：到期或手动释放 */
    static void reclaimExpired() {
        for (Environment e : envs.values()) {
            if (e.state == State.READY && now >= e.expiresAt()) {
                e.state = State.EXPIRING;
                System.out.println("  " + e.id + " 到期 -> EXPIRING");
                e.state = State.RECLAIMED;
                e.services.clear();
                System.out.println("  " + e.id + " -> RECLAIMED（资源已释放）");
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("【申请环境】");
        Environment dev = request("alice", "java-service", "1.4.2", 2 * 60 * 60 * 1000L);
        Environment pr = request("bob", "java-service", "1.5.0-rc1", 30 * 60 * 1000L);

        System.out.println();
        System.out.println("【异步创建】");
        tick();
        tick();

        System.out.println();
        System.out.println("【环境列表】");
        envs.values().forEach(Environment::report);

        System.out.println();
        System.out.println("【时间推进 40 分钟：PR 环境到期】");
        now += 40 * 60 * 1000L;
        reclaimExpired();
        envs.values().forEach(Environment::report);

        System.out.println();
        System.out.println("【时间推进 3 小时：dev 环境也到期】");
        now += 3 * 60 * 60 * 1000L;
        reclaimExpired();
        envs.values().forEach(Environment::report);

        System.out.println();
        System.out.println("【隔离级别】");
        String[][] levels = {
                {"独占", "整套服务独立部署", "贵、干净", "集成测试/压测"},
                {"共享", "多人共用一套", "便宜、会污染", "日常联调"},
                {"PR 环境", "每个 PR 一套，合并即回收", "中等", "代码评审验收"},
        };
        for (String[] l : levels) {
            System.out.printf("  %-8s %-26s %-14s %s%n", l[0], l[1], l[2], l[3]);
        }

        System.out.println();
        System.out.println("【要点】模板化 + 状态机 + TTL 回收 + 按测试类型选隔离级别");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现环境状态机（REQUESTED/PROVISIONING/READY/RECLAIMED），验证异步创建与到期回收。
 * 2. 给环境加"续租"与"手动释放"操作，说明如何防止"占着不用"。
 * 3. 思考题：为什么"PR 环境"能显著缩短评审等待？从"环境排队"与"验收前置"分析。
 * ============================================================
 */
