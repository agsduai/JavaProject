/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-03
 * 课名：构建缓存与增量构建
 * ------------------------------------------------------------
 * 知识点：
 *   - **构建缓存的本质：输入不变则输出可复用**——**缓存键 = 源码内容 + 依赖版本 + 编译参数 + 环境（JDK 版本）的哈希**；**任何输入变化都必须换 key**（否则拿到错误的旧产物）。
 *   - **本地缓存 vs 远程缓存**：**本地**（单机 `.m2`/`build-cache`）命中快；**远程**（团队共享）让"别人构建过的"直接复用——**CI 的加速主要来自远程缓存**。
 *   - **增量构建**：**只重建"受影响的模块"**（79-04 的依赖图），**未变化的模块直接取缓存**——**monorepo 的关键优化**。
 *   - **缓存失效的三种原因**：**输入变了**（正常）、**key 设计漏了输入**（错误命中，最危险）、**缓存被清理**（性能回退）——**key 要"宁可多算，不可错用"**。
 *   - 与 41 章的关系：41 章 CI 流水线讲"每次全量构建"，**本章讲"怎么让流水线从 20 分钟降到 2 分钟"**。
 *   - 易错点：key 只哈希源码不哈希依赖（依赖升级拿到旧产物）；缓存不设过期（磁盘爆）；不同分支共享可变缓存（污染）；缓存命中率不监控。
 * ============================================================
 */

package com.heima.ch79;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class BuildCache {
    record Task(String module, List<String> sources, List<String> deps,
                String compilerArgs, String jdkVersion) {
    }

    /** 缓存键：所有影响产物的输入一起哈希 */
    static String cacheKey(Task task) {
        StringBuilder sb = new StringBuilder();
        task.sources().forEach(s -> sb.append("src:").append(s).append('\n'));
        task.deps().forEach(d -> sb.append("dep:").append(d).append('\n'));
        sb.append("args:").append(task.compilerArgs()).append('\n');
        sb.append("jdk:").append(task.jdkVersion).append('\n');
        return Integer.toHexString(sb.toString().hashCode());
    }

    static class Cache {
        final Map<String, String> local = new LinkedHashMap<>();
        final Map<String, String> remote = new LinkedHashMap<>();
        long localHits;
        long remoteHits;
        long misses;
        long builds;

        String build(Task task) {
            String key = cacheKey(task);
            if (local.containsKey(key)) {
                localHits++;
                System.out.printf("  %-12s 本地命中 key=%s%n", task.module(), key);
                return local.get(key);
            }
            if (remote.containsKey(key)) {
                remoteHits++;
                local.put(key, remote.get(key));
                System.out.printf("  %-12s 远程命中 key=%s（下载到本地）%n", task.module(), key);
                return remote.get(key);
            }
            misses++;
            builds++;
            String artifact = task.module() + "-" + key + ".jar";
            System.out.printf("  %-12s 未命中，执行构建 key=%s -> %s%n",
                    task.module(), key, artifact);
            local.put(key, artifact);
            remote.put(key, artifact);
            return artifact;
        }

        void report() {
            long total = localHits + remoteHits + misses;
            System.out.printf("  总计 %d 次：本地命中 %d，远程命中 %d，构建 %d，命中率 %.0f%%%n",
                    total, localHits, remoteHits, builds,
                    (double) (localHits + remoteHits) / total * 100);
            System.out.printf("  实际构建次数 %d（省下 %d 次）%n", builds, total - builds);
        }
    }

    public static void main(String[] args) {
        Task common = new Task("common", List.of("A.java", "B.java"),
                List.of("guava:33.0"), "-parameters", "21");
        Task order = new Task("order", List.of("O.java"),
                List.of("common:1.0", "guava:33.0"), "-parameters", "21");
        Task user = new Task("user", List.of("U.java"),
                List.of("common:1.0", "guava:33.0"), "-parameters", "21");

        Cache cache = new Cache();
        System.out.println("【第一次构建（冷缓存）】");
        cache.build(common);
        cache.build(order);
        cache.build(user);
        cache.report();

        System.out.println();
        System.out.println("【第二次构建（无变更）】");
        cache.build(common);
        cache.build(order);
        cache.build(user);
        cache.report();

        System.out.println();
        System.out.println("【只改 order 的一个源文件】");
        Task order2 = new Task("order", List.of("O.java", "O2.java"),
                List.of("common:1.0", "guava:33.0"), "-parameters", "21");
        cache.build(common);                        // 未变 -> 命中
        cache.build(order2);                        // 变了 -> 重建
        cache.build(user);                          // 未变 -> 命中
        cache.report();

        System.out.println();
        System.out.println("【依赖升级：guava 33 -> 34】");
        Task order3 = new Task("order", List.of("O.java", "O2.java"),
                List.of("common:1.0", "guava:34.0"), "-parameters", "21");
        System.out.println("  key 变化 -> " + cacheKey(order2).equals(cacheKey(order3)));
        cache.build(order3);

        System.out.println();
        System.out.println("【要点】key 要覆盖源码/依赖/参数/JDK；远程缓存是 CI 加速主力");
        System.out.println("  最危险的 bug：key 漏了输入 -> 错误命中旧产物");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现缓存键（源码 + 依赖 + 编译参数 + JDK 版本），验证"只改一个源文件"只有该模块重建。
 * 2. 故意从 key 中移除依赖版本，复现"依赖升级却命中旧产物"的错误。
 * 3. 思考题：为什么"远程缓存"是 CI 加速的关键？从"同一提交被多人/多次构建"分析。
 * ============================================================
 */
