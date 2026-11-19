/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-09
 * 课名：构建流水线
 * ------------------------------------------------------------
 * 知识点：
 *   - **native 构建进入 CI 的四个关注点**：**构建时间**（5~15 分钟，要缓存依赖与分层）、**构建资源**（内存 4~8GB，CI runner 要够）、**确定性**（同样输入产出可复现）、**产物管理**（可执行文件大，制品库要支持）。
 *   - **构建缓存的三个层次**：**依赖缓存**（Maven/Gradle 本地库）、**Docker 层缓存**（37-02 的构建缓存）、**native-image 缓存**（部分工具支持，但收益有限）——**分层 Dockerfile 是主要手段**。
 *   - **流水线阶段设计**（41-03 的扩展）：编译 -> 测试 -> **AOT 处理** -> **native 编译** -> 镜像构建 -> 扫描 -> 推送 -> 部署——**native 编译要放在测试之后**（测试失败不浪费 15 分钟）。
 *   - **与 41 章的联动**：native 产物作为**不可变制品**（41-04）、金丝雀发布（41-07）、灰度对比（JVM vs native）。
 *   - 易错点：native 构建放最前面（测试失败白构建）；无依赖缓存（每次全量下载）；CI runner 内存不足（构建 OOM）；不扫镜像（36-10）。
 * ============================================================
 */

package com.heima.ch72;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class NativePipeline {
    record Stage(String name, int minutes, boolean blocking, String note) {
    }

    static void pipeline() {
        System.out.println("【流水线阶段（native 构建）】");
        List<Stage> stages = List.of(
                new Stage("编译", 2, true, "javac / mvn compile"),
                new Stage("单元测试", 4, true, "失败即停（不浪费 native 构建）"),
                new Stage("AOT 处理", 1, true, "spring-boot:process-aot 生成配置"),
                new Stage("native 编译", 12, true, "native-image（最贵）"),
                new Stage("镜像构建", 1, true, "多阶段：distroless + 可执行文件"),
                new Stage("镜像扫描", 2, false, "CVE 扫描（36-10）"),
                new Stage("推送与部署", 2, true, "推制品库 -> 部署"));
        int total = 0;
        for (Stage s : stages) {
            total += s.minutes();
            System.out.printf("  %-12s %2dmin %s %s%n", s.name(), s.minutes(),
                    s.blocking() ? "阻断" : "告警", s.note());
        }
        System.out.printf("  全链路约 %d 分钟（native 编译占 %.0f%%）%n", total,
                stages.get(3).minutes() * 100.0 / total);
        System.out.println();
    }

    static void optimization() {
        System.out.println("【缩短构建时间的三招】");
        Map<String, String> opt = new LinkedHashMap<>();
        opt.put("依赖缓存", "缓存 ~/.m2 或 ~/.gradle（省 1~3 分钟）");
        opt.put("分层 Dockerfile", "先 COPY pom 下载依赖，再 COPY 源码（37-03 缓存策略）");
        opt.put("并行", "单元测试与 AOT 处理并行（互不依赖）");
        opt.put("跳过非必要内容", "只编译需要的模块；用 --gc=serial 减小编译负担（视场景）");
        opt.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
        System.out.println();
    }

    static void artifactManagement() {
        System.out.println("【产物管理】");
        List<String> notes = new ArrayList<>();
        notes.add("产物：可执行文件（60~120MB）+ 镜像（含可执行文件）");
        notes.add("版本：语义化版本 + 提交摘要（41-04），不可变");
        notes.add("保留：release 永久、SNAPSHOT 30 天（41-04 保留策略）");
        notes.add("校验：sha256 + 签名（供应链安全 36-10）");
        notes.forEach(n -> System.out.println("  - " + n));
        System.out.println();
    }

    static void determinism() {
        System.out.println("【构建确定性】");
        System.out.println("  native-image 的产物可能受构建环境（CPU 特性/时区/路径）影响");
        System.out.println("  做法：固定构建镜像（如 ghcr.io/graalvm/native-image:21 固定 digest）");
        System.out.println("        固定时区/编码；避免把构建时间写进产物");
        System.out.println("  验证：同一提交构建两次，比对 sha256（41-04 制品不可变）");
    }

    public static void main(String[] args) {
        pipeline();
        optimization();
        artifactManagement();
        determinism();
        System.out.println();
        System.out.println("  铁律：native 编译放最后（前面的门禁失败就不浪费 12 分钟）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 设计 native 流水线（7 个阶段 + 并行 + 缓存），估算优化前后的总时长。
 * 2. 写出固定构建环境的方案（镜像 digest、时区、编码），说明如何验证"构建确定性"。
 * 3. 思考题：为什么"native 编译要放在所有门禁之后"？从"构建成本"与"快速反馈"的权衡分析。
 * ============================================================
 */
