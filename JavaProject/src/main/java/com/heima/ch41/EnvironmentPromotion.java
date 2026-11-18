/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：41-05
 * 课名：环境晋升与配置管理
 * ------------------------------------------------------------
 * 知识点：
 *   - **环境的本质是"不同信任级别的同一套系统"**：dev（开发者自由实验）-> test（自动化回归）-> staging（生产镜像，最后防线）-> prod（真实流量）。**环境数量按团队规模裁剪**，两环境（test+prod）是多数小团队的最优解，环境越多晋升越慢。
 *   - **staging 的价值在于"像生产"**：同配置规模（或缩比）、同依赖版本、同数据结构（脱敏数据）、同拓扑。staging 与 prod 差异越大，通过 staging 的信心越低——"staging 测过了"不等于"生产没问题"，但差异可以管理。
 *   - **配置与制品分离是环境晋升的前提**：同一个制品在 dev/test/prod 用不同配置（数据源、密钥、域名）。配置来源优先级：环境变量 > 配置中心 > 配置文件 > 代码默认值。**配置里绝不出现"环境 if-else"写在代码里**。
 *   - **配置即资产，需要版本与审计**：配置中心（Nacos/Apollo）记录每次修改的人、时间、diff、灰度范围；敏感配置（密码/密钥）走 secret 管理（K8s Secret + KMS/Vault），**不进代码库不进聊天记录**。
 *   - **晋升节奏与质量门槛**：环境晋升是流水线的阶段——test 绿灯才能进 staging，staging 冒烟绿灯才能进 prod。**每一跳都是自动的**，人工只出现在 prod 的最终放行（审批按钮）。
 *   - **环境漂移（drift）是慢性病**：某人在 prod 机器上手工改了一个 nginx 参数，三个月后所有人都忘了——staging 复现不了、重建环境不对。对策：环境定义代码化（IaC）、变更一律走流水线、定期 diff 环境配置告警。
 *   - 易错点：配置写在代码里用 profile 硬编码 if；密钥提交进 git（第 36 章的历史层教训同样适用）；staging 用生产真实数据（合规事故）；各环境版本不一致还互相拷贝配置文件。
 * ============================================================
 */

package com.heima.ch41;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class EnvironmentPromotion {
    record Env(String name, String trustLevel, String dataSource, String configSource, String gate) {
    }

    static void envMatrix() {
        System.out.println("【环境矩阵：同一制品，逐级晋升】");
        List<Env> envs = List.of(
                new Env("dev", "开发者自由实验", "内存/容器化DB", "本地默认值", "无（自测）"),
                new Env("test", "自动化回归", "脱敏子集", "配置中心 test 命名空间", "流水线全量绿灯"),
                new Env("staging", "生产镜像验证", "脱敏全量结构", "配置中心 staging", "冒烟 + 压测通过"),
                new Env("prod", "真实流量", "生产库", "配置中心 prod + Secret", "人工审批放行"));
        for (Env e : envs) {
            System.out.printf("  %-8s 数据源=%-14s 配置=%-24s 准入=%s%n",
                    e.name(), e.dataSource(), e.configSource(), e.gate());
        }
        System.out.println("  制品相同，配置按环境注入 —— 这是晋升语义的全部内容");
        System.out.println();
    }

    static void configPriority() {
        System.out.println("【配置优先级：环境变量 > 配置中心 > 文件 > 代码默认】");
        record Key(String key, String codeDefault, String center, String envVar, String actual) {
        }
        List<Key> keys = List.of(
                new Key("db.url", "jdbc:localhost", "jdbc:test-db:3306", "jdbc:prod-db:3306", "jdbc:prod-db:3306"),
                new Key("cache.ttl", "60", "300", null, "300"));
        for (Key k : keys) {
            String actual = k.envVar() != null ? k.envVar()
                    : k.center() != null ? k.center() : k.codeDefault();
            System.out.printf("  %-9s 代码=%-16s 中心=%-18s 环境变量=%-18s 生效=%s%n",
                    k.key(), k.codeDefault(), k.center(),
                    k.envVar() == null ? "-" : k.envVar(), actual);
        }
        System.out.println("  高优先级覆盖低优先级；优先级顺序全团队一致并有文档");
        System.out.println();
    }

    static void driftDetection() {
        System.out.println("【环境漂移：手工变更的三个月后】");
        record Drift(String where, String what, String found, String fix) {
        }
        List<Drift> drifts = List.of(
                new Drift("prod nginx", "手工加了 client_max_body_size 50m",
                        "staging 复现不了上传问题", "参数进 IaC，diff 告警"),
                new Drift("prod JVM", "手工改了 -Xmx 应急", "重启后配置回到镜像默认，问题复发",
                        "应急参数当天进配置中心"),
                new Drift("test DB", "手工改了表结构", "test 与 prod schema 校验失败",
                        "schema 变更走迁移脚本（41-09）"));
        for (Drift d : drifts) {
            System.out.printf("  %-10s %s%n    发现：%s%n    修复：%s%n",
                    d.where(), d.what(), d.found(), d.fix());
        }
        System.out.println("  原则：环境的每一次变更都走流水线，机器 diff 比人脑可靠");
    }

    public static void main(String[] args) {
        envMatrix();
        configPriority();
        driftDetection();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的项目定义环境矩阵（各环境的数据/配置/准入门槛），指出当前缺失的一道门禁并落地。
 * 2. 用配置中心（或环境变量 + 启动打印）验证配置优先级，故意在三处设置不同值，记录生效来源。
 * 3. 思考题：为什么环境数量要克制？从晋升周期与"staging 信心"的平衡分析两环境方案的优势。
 * ============================================================
 */
