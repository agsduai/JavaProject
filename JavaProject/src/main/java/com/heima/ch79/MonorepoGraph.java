/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-04
 * 课名：Monorepo 与依赖图
 * ------------------------------------------------------------
 * 知识点：
 *   - **Monorepo 的核心能力是"影响分析"**：**改了模块 A，只构建/测试"依赖 A 的模块"**（affected），**而不是全仓构建**——**依赖图是这一切的基础**。
 *   - **依赖图的两种边**：**编译依赖**（A 依赖 B 的 API）与**测试/运行依赖**——**影响分析要沿"反向边"传播**（谁依赖我，谁受影响）。
 *   - **拓扑排序**：**构建顺序必须"被依赖者先构建"**；**有环说明模块耦合错了**（应该抽公共模块或改依赖方向）——**环检测是 CI 的必备门禁**。
 *   - **与 79-03 的关系**：**affected 集合 + 构建缓存** = monorepo 增量构建的全部秘密——**先算影响面，再查缓存，最后只构建没命中的**。
 *   - 与 31 章的关系：31 章讲"设计模式与重构"，**依赖方向（稳定依赖稳定）是架构原则**；monorepo 的依赖图是它的可视化。
 *   - 易错点：全仓构建（慢）；影响分析只看直接依赖（漏传递依赖）；有环不报错（构建顺序随机）；不区分编译/测试依赖（过度构建）。
 * ============================================================
 */

package com.heima.ch79;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class MonorepoGraph {
    /** module -> 它依赖的模块 */
    static final Map<String, List<String>> deps = new LinkedHashMap<>();

    static {
        deps.put("common", List.of());
        deps.put("auth", List.of("common"));
        deps.put("order", List.of("common", "auth"));
        deps.put("payment", List.of("common", "order"));
        deps.put("web", List.of("order", "payment", "auth"));
        deps.put("report", List.of("common"));
    }

    /** 反向图：module -> 依赖它的模块 */
    static Map<String, List<String>> reverse() {
        Map<String, List<String>> rev = new LinkedHashMap<>();
        deps.keySet().forEach(m -> rev.put(m, new ArrayList<>()));
        for (Map.Entry<String, List<String>> e : deps.entrySet()) {
            for (String d : e.getValue()) {
                rev.get(d).add(e.getKey());
            }
        }
        return rev;
    }

    /** 拓扑排序：被依赖者在前 */
    static List<String> topoOrder() {
        List<String> order = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> done = new LinkedHashSet<>();
        for (String m : deps.keySet()) {
            dfs(m, visiting, done, order);
        }
        return order;
    }

    static void dfs(String m, Set<String> visiting, Set<String> done, List<String> order) {
        if (done.contains(m)) {
            return;
        }
        if (!visiting.add(m)) {
            throw new IllegalStateException("依赖环：" + visiting + " -> " + m);
        }
        for (String d : deps.getOrDefault(m, List.of())) {
            dfs(d, visiting, done, order);
        }
        visiting.remove(m);
        done.add(m);
        order.add(m);
    }

    /** 影响面：直接或间接依赖 changed 的模块 */
    static Set<String> affected(Set<String> changed) {
        Map<String, List<String>> rev = reverse();
        Set<String> out = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>(changed);
        while (!queue.isEmpty()) {
            String cur = queue.remove(0);
            for (String dependent : rev.getOrDefault(cur, List.of())) {
                if (out.add(dependent)) {
                    queue.add(dependent);
                }
            }
        }
        out.addAll(changed);
        return out;
    }

    public static void main(String[] args) {
        System.out.println("【依赖图】");
        deps.forEach((m, d) -> System.out.printf("  %-8s -> %s%n", m, d));

        System.out.println();
        System.out.println("【构建顺序（拓扑排序）】");
        System.out.println("  " + topoOrder());

        System.out.println();
        System.out.println("【影响分析】");
        for (String changed : List.of("common", "order", "report")) {
            Set<String> aff = affected(Set.of(changed));
            System.out.printf("  改 %-8s -> 需要构建 %d/%d 个模块：%s%n",
                    changed, aff.size(), deps.size(), aff);
        }

        System.out.println();
        System.out.println("【环检测】");
        deps.put("common", List.of("web"));          // 人为造环
        try {
            topoOrder();
        } catch (IllegalStateException e) {
            System.out.println("  " + e.getMessage() + "（CI 应直接失败）");
        }
        deps.put("common", List.of());

        System.out.println();
        System.out.println("【要点】拓扑排序定构建顺序；反向依赖算影响面；有环立即失败");
        System.out.println("  affected 集合再配合构建缓存（79-03），才是完整的增量构建");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现拓扑排序与环检测，验证有环时输出环路径并让 CI 失败。
 * 2. 实现 affected 影响分析：改 common 时哪些模块需要重建，验证传递依赖不遗漏。
 * 3. 思考题：为什么 monorepo 必须做影响分析？从"全仓构建时间随模块数线性增长"分析。
 * ============================================================
 */
