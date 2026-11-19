/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-08
 * 课名：制品与版本管理
 * ------------------------------------------------------------
 * 知识点：
 *   - **制品（Artifact）是"可部署的不可变产物"**：**jar/镜像 + 版本号 + 构建元数据**（commit、构建号、时间）——**同一版本必须对应同一份内容**，**不可覆盖**。
 *   - **语义化版本**：`MAJOR.MINOR.PATCH`——**MAJOR 不兼容、MINOR 向后兼容的新增、PATCH 修复**；**依赖范围**（`^1.2.0` 允许 1.x）——**版本比较是发布系统的基础能力**。
 *   - **制品晋升（Promotion）**：**同一制品从 dev -> staging -> prod 逐级晋升**，**每级有质量门禁**（测试/扫描/审批）——**晋升的是"同一份制品"，不是"重新构建"**（重新构建会引入差异）。
 *   - **可追溯性**：**从线上版本反查 commit/build/流水线/审批人**——**出问题能定位"这个包是谁、用什么代码、什么时候构建的"**。
 *   - 与 41 章的关系：41 章讲"金丝雀/回滚"，**回滚的前提是"制品不可变 + 版本可追溯"**——**本章是发布的地基**。
 *   - 易错点：每个环境重新构建（制品不一致）；版本覆盖（同一个 1.0.0 多次上传）；不做版本比较（`1.10` 被当成小于 `1.9`）；晋升无门禁（dev 直接上 prod）。
 * ============================================================
 */

package com.heima.ch79;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ArtifactPromotion {
    record Version(int major, int minor, int patch) implements Comparable<Version> {
        static Version parse(String s) {
            String[] parts = s.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("非法版本：" + s);
            }
            return new Version(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        }

        @Override
        public int compareTo(Version o) {
            if (major != o.major) {
                return Integer.compare(major, o.major);
            }
            if (minor != o.minor) {
                return Integer.compare(minor, o.minor);
            }
            return Integer.compare(patch, o.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    record Artifact(String name, Version version, String commit, String buildId,
                    String stage, boolean testsPassed, boolean scanPassed) {
        String coordinate() {
            return name + ":" + version;
        }
    }

    static final Map<String, Artifact> registry = new LinkedHashMap<>();

    static void publish(Artifact a) {
        String key = a.coordinate();
        if (registry.containsKey(key)) {
            throw new IllegalStateException("制品已存在，不可覆盖：" + key);
        }
        registry.put(key, a);
        System.out.printf("  发布 %-20s commit=%s build=%s stage=%s%n",
                key, a.commit(), a.buildId(), a.stage());
    }

    /** 晋升：同一制品换 stage，不重新构建 */
    static Artifact promote(Artifact a, String target) {
        List<String> order = List.of("dev", "staging", "prod");
        int cur = order.indexOf(a.stage());
        int next = order.indexOf(target);
        if (next != cur + 1) {
            System.out.printf("  非法晋升：%s -> %s（只能逐级）%n", a.stage(), target);
            return a;
        }
        if (!a.testsPassed() || !a.scanPassed()) {
            System.out.printf("  门禁失败：tests=%s scan=%s%n", a.testsPassed(), a.scanPassed());
            return a;
        }
        Artifact promoted = new Artifact(a.name(), a.version(), a.commit(), a.buildId(),
                target, a.testsPassed(), a.scanPassed());
        registry.put(promoted.coordinate() + "@" + target, promoted);
        System.out.printf("  晋升 %-20s %s -> %s（制品未重新构建）%n",
                a.coordinate(), a.stage(), target);
        return promoted;
    }

    public static void main(String[] args) {
        System.out.println("【版本比较】");
        List<Version> versions = new ArrayList<>(List.of(
                Version.parse("1.10.0"), Version.parse("1.9.0"), Version.parse("2.0.0"),
                Version.parse("1.9.1")));
        versions.sort(null);
        System.out.println("  排序：" + versions);
        System.out.println("  1.10.0 > 1.9.0 ? " + (Version.parse("1.10.0")
                .compareTo(Version.parse("1.9.0")) > 0));

        System.out.println();
        System.out.println("【发布】");
        Artifact a = new Artifact("order-service", Version.parse("1.5.0"),
                "a1b2c3d", "build-1001", "dev", true, true);
        publish(a);

        System.out.println();
        System.out.println("【重复发布同一版本】");
        try {
            publish(a);
        } catch (IllegalStateException e) {
            System.out.println("  " + e.getMessage());
        }

        System.out.println();
        System.out.println("【逐级晋升】");
        Artifact staging = promote(a, "staging");
        Artifact prod = promote(staging, "prod");
        System.out.println("  当前 prod：" + prod);

        System.out.println();
        System.out.println("【跳级晋升】");
        promote(a, "prod");

        System.out.println();
        System.out.println("【门禁失败】");
        Artifact bad = new Artifact("user-service", Version.parse("1.0.0"),
                "deadbee", "build-1002", "dev", true, false);
        publish(bad);
        promote(bad, "staging");

        System.out.println();
        System.out.println("【可追溯】");
        System.out.println("  prod 1.5.0 -> commit=a1b2c3d build=build-1001");
        System.out.println("  线上问题 -> 反查 commit -> 找到变更与审批人");

        System.out.println();
        System.out.println("【规律】同名同版本不可覆盖；晋升不重建；跳级/门禁失败被拒");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现语义化版本解析与比较，验证 `1.10.0 > 1.9.0`。
 * 2. 实现"同一制品逐级晋升"，验证 dev 不能直接跳 prod，且门禁失败被拒。
 * 3. 思考题：为什么"每个环境重新构建"是反模式？从"制品不一致 + 回滚不可信"分析。
 * ============================================================
 */
