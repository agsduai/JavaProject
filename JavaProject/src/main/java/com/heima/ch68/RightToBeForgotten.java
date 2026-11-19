/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：68-10
 * 课名：数据删除与遗忘权
 * ------------------------------------------------------------
 * 知识点：
 *   - **遗忘权（Right to be Forgotten）是法定义务**：用户有权要求删除其个人信息，企业需在合理期限内删除并**通知下游接收方**——**"删除"不是删一行数据，是全链路清除**。
 *   - **删除的四个难点**：**分布式副本**（主库/从库/缓存/ES/数仓/备份）、**派生数据**（聚合结果、报表、模型训练样本）、**引用完整性**（订单需要保留但用户信息要删 -> **匿名化替代删除**）、**备份的不可删除性**（备份介质里的历史副本，通常用"加密擦除" 68-04 解决）。
 *   - **三种删除策略**：**物理删除**（真删，最彻底但要处理引用）、**匿名化**（保留业务记录，抹掉个人标识——订单保留但用户变为 `deleted_user_hash`）、**加密擦除**（销毁密钥，数据不可解——用于备份/冷存储）。
 *   - **删除的工程实现**：**删除清单**（该用户数据分布在哪些表/系统）+ **级联任务**（异步、可重试、可观测 61 章）+ **删除凭证**（记录删了什么、何时、由谁 68-09）+ **定期核验**（抽查是否还有残留）。
 *   - 与 55-07 的关系：SaaS 退租的"数据清除"是同一问题（多系统清除 + 凭证），**本章是用户级的版本**。
 *   - 易错点：只删业务库（缓存/ES/数仓仍有）；删除不留凭证（无法证明已删）；忽略派生数据（模型记住了用户数据）；备份永不清理（历史副本永久存在）。
 * ============================================================
 */

package com.heima.ch68;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RightToBeForgotten {
    /** 数据分布：系统 -> 存储的条目 */
    static final Map<String, List<String>> stores = new LinkedHashMap<>();

    static {
        stores.put("mysql.user", new ArrayList<>(List.of("u-1:phone:13800001234",
                "u-1:id_card:110101199001011234", "u-2:phone:13900005678")));
        stores.put("mysql.order", new ArrayList<>(List.of("u-1:order:o-1:address:杭州",
                "u-2:order:o-2:address:上海")));
        stores.put("redis.user_cache", new ArrayList<>(List.of("u-1:profile", "u-2:profile")));
        stores.put("es.user_index", new ArrayList<>(List.of("doc-u-1", "doc-u-2")));
        stores.put("dwh.user_dim", new ArrayList<>(List.of("u-1 维度行", "u-2 维度行")));
        stores.put("backup.daily", new ArrayList<>(List.of("u-1 备份副本（加密）",
                "u-2 备份副本（加密）")));
    }

    record DeletionResult(String store, String strategy, int affected, String note) {
    }

    /** 删除策略：按存储类型选择 */
    static DeletionResult deleteFrom(String store, String userId) {
        List<String> items = stores.get(store);
        int before = items.size();
        switch (store) {
            case "mysql.user" -> {
                items.removeIf(s -> s.startsWith(userId + ":"));
                return new DeletionResult(store, "物理删除", before - items.size(),
                        "用户主数据彻底删除");
            }
            case "mysql.order" -> {
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).startsWith(userId + ":order:")) {
                        items.set(i, items.get(i).replace(":address:", ":address_anonymized:"));
                    }
                }
                return new DeletionResult(store, "匿名化", 1,
                        "订单保留（业务需要），地址字段匿名化");
            }
            case "redis.user_cache", "es.user_index" -> {
                items.removeIf(s -> s.contains(userId));
                return new DeletionResult(store, "物理删除", before - items.size(),
                        "缓存与索引立即失效");
            }
            case "dwh.user_dim" -> {
                items.removeIf(s -> s.startsWith(userId + " "));
                return new DeletionResult(store, "物理删除 + 重建", before - items.size(),
                        "维度表删除后需重建派生报表");
            }
            case "backup.daily" -> {
                return new DeletionResult(store, "加密擦除", 0,
                        "备份介质不可改，销毁该用户数据密钥 -> 不可解密");
            }
            default -> {
                return new DeletionResult(store, "未处理", 0, "未覆盖的存储（风险）");
            }
        }
    }

    static void execute(String userId) {
        System.out.println("【删除请求：用户 " + userId + "】");
        List<DeletionResult> results = new ArrayList<>();
        for (String store : stores.keySet()) {
            results.add(deleteFrom(store, userId));
        }
        results.forEach(r -> System.out.printf("  %-18s %-12s 影响=%-2d %s%n",
                r.store(), r.strategy(), r.affected(), r.note()));
        System.out.println();
        System.out.println("【删除后核验】");
        stores.forEach((store, items) -> {
            long remain = items.stream().filter(s -> s.contains(userId)).count();
            System.out.printf("  %-18s 残留 %d 条%s%n", store, remain,
                    remain == 0 ? "" : "（" + items.stream()
                            .filter(s -> s.contains(userId)).toList() + "）");
        });
        System.out.println();
        System.out.println("  凭证：本次删除记录已写入审计链（68-09），保留 3 年");
    }

    public static void main(String[] args) {
        execute("u-1");

        System.out.println();
        System.out.println("【删除策略选型】");
        Map<String, String> choice = new LinkedHashMap<>();
        choice.put("用户主数据", "物理删除（无业务依赖或可级联）");
        choice.put("业务单据", "匿名化（保留记录，抹除个人标识）");
        choice.put("缓存/索引", "物理删除 + 失效广播（65-05 同源）");
        choice.put("数仓/模型", "删除 + 重建派生数据（模型需重训，66-11）");
        choice.put("备份", "加密擦除（销毁密钥，备份不可改）");
        choice.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));

        System.out.println();
        System.out.println("【难点清单】");
        List<String> hard = List.of(
                "派生数据：报表/聚合/模型样本都可能含个人信息",
                "备份：不可删除，只能加密擦除",
                "引用完整性：删用户但订单要留 -> 匿名化",
                "下游通知：共享过数据的第三方也要通知删除（68-06）");
        hard.forEach(h -> System.out.println("  - " + h));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现全链路删除：6 个存储各用合适策略（物理/匿名化/加密擦除），验证删除后残留为零。
 * 2. 设计"删除清单"模板（系统/表/字段/策略/责任人/凭证），并对你业务的一个用户跑一遍推演。
 * 3. 思考题：为什么"备份"只能用加密擦除？从"备份介质不可修改"与"历史副本必须可恢复"的矛盾分析。
 * ============================================================
 */
