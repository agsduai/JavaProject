/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：41-08
 * 课名：特性开关与暗发布
 * ------------------------------------------------------------
 * 知识点：
 *   - **特性开关把"部署"与"发布"解耦**：代码随时合入 main 部署上线，新功能由开关控制是否对用户可见。**部署 100 次，功能发布 1 次**——这是 trunk-based 开发的基础设施，也是发布风险从"一次性大爆炸"变成"随时小步释放"的支点。
 *   - **开关的四类生命周期**：发布开关（release toggle，功能上线后几天内删除）、运维开关（ops toggle，如降级到大促写保护）、实验开关（experiment，A/B 用完删除）、权限开关（permission，长期存在如付费功能）。**前三类是技术债，必须设过期时间**——堆积的"僵尸开关"会把代码分支逻辑变成迷宫。
 *   - **暗发布（dark launch）**：新代码在真实流量下运行但用户无感知——把结果写日志不返回用户，用真实负载验证性能与正确性（影子流量）。"新推荐算法算了但不展示，对比效果一周"是暗发布的典型用法。
 *   - **开关的实现层次**：最简单是配置中心布尔值；进阶带灰度规则（按用户/渠道/百分比）；再进阶与实验平台打通（分流+效果归因）。**开关读取必须在热路径上便宜**（本地缓存 + 秒级同步），否则开关本身就是故障源。
 *   - **开关的测试矩阵爆炸**：N 个开关 = 2^N 种组合。对策：开关声明默认值、组合测试只测核心路径、CI 里跑"全开/全关"两组回归。
 *   - **删除开关也是发布**：移除旧分支代码时走正常 PR + 测试，**"功能已全量、代码可删"要写进发布清单**。永远不删的开关 = 永远的死代码分支。
 *   - 易错点：开关默认值是"开"（忘了关闭就直接全量）；开关逻辑散落在几十个 if 里没有统一收口；开关名不可读（`flag1`、`test2`，半年后没人知道它控制什么）；用开关做长期权限管理（应上权限系统，开关系统承担不了审计职责）。
 * ============================================================
 */

package com.heima.ch41;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FeatureFlags {
    record Flag(String name, String kind, boolean defaultValue, String expire, String owner) {
    }

    static Map<String, Flag> flags = new LinkedHashMap<>();

    static void flagRegistry() {
        System.out.println("【开关注册表：名字可读、默认值、过期时间、责任人】");
        flags.put("order.batch-cancel", new Flag("order.batch-cancel", "release", false,
                "2026-12-31 全量后删除", "订单组-小王"));
        flags.put("search.vector-rank", new Flag("search.vector-rank", "experiment", false,
                "实验结束（2026-12-15）", "搜索组-小李"));
        flags.put("pay.write-protect", new Flag("pay.write-protect", "ops", false,
                "大促结束删除", "支付组"));
        flags.put("vip.discount", new Flag("vip.discount", "permission", true,
                "长期保留（转权限系统候选）", "产品"));
        for (Flag f : flags.values()) {
            System.out.printf("  %-20s %-11s 默认=%-5s 过期=%-24s 责任=%s%n",
                    f.name(), f.kind(), f.defaultValue(), f.expire(), f.owner());
        }
        System.out.println("  release/experiment 开关必须有删除计划，否则注册表拒绝新增");
        System.out.println();
    }

    static void decoupleDeployRelease() {
        System.out.println("【部署与发布解耦：代码先行，功能后放】");
        List<String> timeline = List.of(
                "11-01 合入批量取消代码（flag=false），部署 3 次，用户无感",
                "11-10 内部账号 flag=true（权限开关），收集反馈",
                "11-15 5% 用户灰度，错误率与转化观察",
                "11-20 全量 flag=true",
                "12-01 确认稳定 -> 删除开关与旧分支代码（一次正常 PR）");
        for (String t : timeline) {
            System.out.println("  " + t);
        }
        System.out.println("  对照：不开开关 = 11-20 一次性大爆炸，出问题只能整体回滚");
        System.out.println();
    }

    static void darkLaunch() {
        System.out.println("【暗发布：真跑但用户无感】");
        record Req(String query, String oldResult, String newResult, String action) {
        }
        List<Req> reqs = List.of(
                new Req("手机壳", "商品A", "商品C", "日志记录 diff，返回旧结果"),
                new Req("退款 政策", "FAQ-3", "FAQ-3", "一致，标记为正常"));
        for (Req r : reqs) {
            System.out.printf("  查询「%s」 旧=%-8s 新=%-8s -> %s%n",
                    r.query(), r.oldResult(), r.newResult(), r.action());
        }
        System.out.println("  一周后统计：新算法 CTR 提升 +2.1% 且 P99 持平 -> 开放灰度");
        System.out.println("  影子流量的三种用途：性能压测、正确性对比、效果实验");
        System.out.println();
    }

    static void testMatrix() {
        System.out.println("【开关组合的测试策略】");
        List<String> rules = List.of(
                "核心链路跑「全关」与「全开」两组回归（CI 固定两档）",
                "开关间声明依赖：B 依赖 A 开启时才生效，注册表校验",
                "组合爆炸（2^N）不可测 -> 限制同模块活跃开关 <= 3",
                "默认值即兜底：开关系统不可用时按默认值执行（fail safe）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        flagRegistry();
        decoupleDeployRelease();
        darkLaunch();
        testMatrix();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的项目实现一个极简开关服务（配置中心布尔值 + 本地缓存秒级同步），验证热路径读取开销 < 0.1ms。
 * 2. 给一个新功能设计完整的"部署-暗发布-灰度-全量-删开关"时间线，其中每一步的验证动作与责任人写清。
 * 3. 思考题：为什么"僵尸开关"是技术债？从代码分支组合数与认知负担两个角度量化 N 个僵尸开关的维护成本。
 * ============================================================
 */
