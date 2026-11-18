/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：41-06
 * 课名：发布策略：滚动与蓝绿
 * ------------------------------------------------------------
 * 知识点：
 *   - **发布策略回答的是"新旧版本怎么共存、怎么切流量、出问题怎么退"**。三种主流：滚动（逐批替换，资源省）、蓝绿（两套环境整体切换，切换最快）、金丝雀（小比例真实流量验证再放量，风险最小）。
 *   - **滚动发布（rolling update）**：第 37 章 K8s 的 maxSurge/maxUnavailable 就是它。优点：资源需求少（不需要双倍容量）；缺点：新旧版本**共存期**要兼容——API 兼容、数据兼容、消息兼容。**共存期是滚动发布的全部难点**。
 *   - **蓝绿发布（blue-green）**：绿集群部署新版、冒烟验证，负载均衡一次切流，蓝集群待命作为回滚。切换秒级、回滚秒级（切回去）。代价：**双倍资源**；数据层通常是共享的，所以**数据库变更必须在切换前完成且双向兼容**（41-09）。
 *   - **共存期的兼容三件套**：① API 兼容（旧客户端能调用新服务：字段只增不改删）；② 数据兼容（新旧代码都能读写同一份数据：新字段可空、删列分三步）；③ 消息兼容（消费者能处理新消息格式：加字段容忍未知字段）。三者任一缺失，滚动/蓝绿都会在共存期出事故。
 *   - **切换瞬间的会话与缓存**：会话状态不能存在本地内存（内存粘性会"粘"到旧版本）；缓存的序列化结构要兼容（旧版本缓存被新版本读到）。切换前清掉易变的本地状态。
 *   - **回滚不是 re-deploy 旧包那么简单**：数据库变更已生效时，回滚代码不回滚数据（41-09 的双向兼容救你）；消息队列里积压的新格式消息回滚后还在消费。**回滚演练 = 代码回滚 + 数据兼容确认 + 缓存清理**。
 *   - 易错点：滚动发布直接改字段语义（旧实例读到新语义数据）；蓝绿切换忘了清会话缓存（用户看到旧版页面数据）；蓝绿共享数据库却做了破坏性 DDL（切回蓝直接崩）；发布窗口选在高峰期（共存期负载翻倍）。
 * ============================================================
 */

package com.heima.ch41;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class RollingBlueGreen {
    record Instance(String name, String version, boolean ready, int trafficPct) {
    }

    static void rolling() {
        System.out.println("【滚动发布：maxSurge=1, maxUnavailable=0，逐批替换】");
        List<Instance> fleet = new ArrayList<>(List.of(
                new Instance("pod-1", "v1", true, 25),
                new Instance("pod-2", "v1", true, 25),
                new Instance("pod-3", "v1", true, 25),
                new Instance("pod-4", "v1", true, 25)));
        for (int i = 0; i < 4; i++) {
            fleet.set(i, new Instance("pod-" + (i + 1), "v2", true, 25));
            System.out.printf("  step%d: 替换 %s -> v2，可用容量始终 4/4%n", i + 1, fleet.get(i).name());
        }
        System.out.println("  共存期：pod-1(v2) 与 pod-4(v1) 同时接流量 —— API/数据/消息必须兼容");
        System.out.println();
    }

    static void blueGreen() {
        System.out.println("【蓝绿发布：整套切换，回滚秒级】");
        List<String> steps = List.of(
                "step1 蓝集群(v1)承载 100% 流量，绿集群(v2)部署完成并冒烟通过",
                "step2 LB 切流：蓝 0% -> 绿 100%（秒级，切前清本地会话/缓存）",
                "step3 观察 30 分钟：错误率/SLO 达标",
                "step4 绿转正；蓝保留 24h 作回滚（切回 = LB 指回蓝）",
                "step5 确认稳定后回收蓝集群资源");
        for (String s : steps) {
            System.out.println("  " + s.replace("%", "%"));
        }
        System.out.println("  代价：双倍资源；数据库必须切换前完成双向兼容迁移");
        System.out.println();
    }

    static void compatibilityChecklist() {
        System.out.println("【共存期兼容三件套：缺一必出事故】");
        String[][] items = {
                {"API 兼容", "字段只增不改不删；删字段先停用再下线（两版发布）", "旧客户端调新服务"},
                {"数据兼容", "新字段可空；改语义分三步走（加列->双写->切读）", "新旧实例读写同一份数据"},
                {"消息兼容", "消费者容忍未知字段；格式变更加版本号", "队列里新旧消息混杂"},
        };
        for (String[] i : items) {
            System.out.printf("  %-8s %-42s 场景：%s%n", i[0], i[1], i[2]);
        }
        System.out.println();
    }

    static void rollbackRehearsal() {
        System.out.println("【回滚演练清单】");
        List<String> checks = List.of(
                "代码回滚：切回旧制品（蓝绿=切流，滚动=回退 Deployment）",
                "数据确认：确认没有破坏性 DDL；有则回滚被数据迁移卡住",
                "缓存清理：本地会话清空；Redis 序列化结构兼容确认",
                "消息确认：新格式消息回滚后消费不报错",
                "时间线：回滚全程 < 2 分钟（演练实测并记录）");
        for (String c : checks) {
            System.out.println("  [ ] " + c);
        }
    }

    public static void main(String[] args) {
        rolling();
        blueGreen();
        compatibilityChecklist();
        rollbackRehearsal();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在你的 K8s 环境（或 minikube）真实执行一次滚动发布，观察 maxSurge/maxUnavailable 的容量曲线，记录共存期长度。
 * 2. 设计一次蓝绿发布方案：容量规划、切流步骤、回滚预案、数据兼容确认，其中"切流后清理本地会话"的具体实现必须写清楚。
 * 3. 思考题：为什么蓝绿发布对数据库变更的要求比滚动更严格？从"切换瞬间数据必须已被新旧两个版本共同理解"推导。
 * ============================================================
 */
