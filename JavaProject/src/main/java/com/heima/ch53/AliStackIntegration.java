/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：53-09
 * 课名：生态整合：一个订单服务的阿里栈落地
 * ------------------------------------------------------------
 * 知识点：
 *   - **把全部组件串到一个服务上**：订单服务接入——Nacos（注册+配置）、Sentinel（下单资源限流+熔断）、Dubbo（调库存）/RocketMQ（发事件）、Seata AT（订单-账务跨库）、SkyWalking（链路）——**53-01 选位图的落地版**。
 *   - **接入顺序与风险**：先注册发现（基础设施）-> 再配置中心（参数化）-> 再 RPC（调用链改造）-> 再 Sentinel（防护）-> 再 Seata（事务改造，侵入最大放后面）-> 最后链路（观测补全）。**每步可独立回退**（49-10 演进纪律）。
 *   - **配置清单示例**：Nacos 两个 dataId（application.yaml + sentinel-rules）；Dubbo 超时与重试（53-04）；Seata 模式与 undo_log；SkyWalking agent 参数（采样率/服务名）——**配置总览表是接入验收物**。
 *   - **集成测试清单**：注册发现 OK、配置热更 OK、限流触发 OK、跨服务事务回滚 OK、trace 完整 OK——**五项全过才算接入完成**（53-01 预览的落地）。
 *   - 易错点：一次性全接入（故障域叠加，无法归因）；Seata 放最先（undo_log 建表/版本兼容阻塞主线）；接入后无配置总览（运维交接灾难）。
 * ============================================================
 */

package com.heima.ch53;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class AliStackIntegration {
    record Step(int order, String action, String risk, String rollback) {
    }

    static void roadmap() {
        System.out.println("【订单服务接入阿里栈：六步路线】");
        List<Step> steps = List.of(
                new Step(1, "Nacos 注册发现", "低：本地缓存兜底", "退回直连 IP 配置"),
                new Step(2, "Nacos 配置中心", "低：参数化而已", "退回本地 yaml"),
                new Step(3, "Dubbo 调库存", "中：调用链改造", "退回 Feign/直连"),
                new Step(4, "Sentinel 防护", "低：规则可空", "规则清空即无防护原样"),
                new Step(5, "Seata AT（订单-账务）", "高：事务语义变化", "退回本地消息表（39-09）"),
                new Step(6, "SkyWalking 链路", "低：观测增强", "摘 agent 即回退"));
        for (Step s : steps) {
            System.out.printf("  步骤%d %-20s 风险=%-18s 回退=%s%n",
                    s.order(), s.action(), s.risk(), s.rollback());
        }
        System.out.println("  顺序原则：低风险先上，事务改造最后（49-10 演进纪律）");
    }

    public static void main(String[] args) {
        roadmap();
        System.out.println();
        List<String> gates = List.of(
                "[ ] 注册发现：3 实例互相可见，摘 1 个自动剔除",
                "[ ] 配置：热更生效 + 灰度验证",
                "[ ] Sentinel：限流触发正确 + 规则持久化（重启不丢）",
                "[ ] Seata：跨服务回滚演练 + undo_log 就位",
                "[ ] SkyWalking：trace 完整性巡检通过（53-08）");
        for (String g : gates) {
            System.out.println("  " + g);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的服务制定六步接入路线（含每步风险与回退），输出接入验收清单。
 * 2. 演练"Seata 回滚"：订单-账务跨库回滚演示（或模拟），验证 undo_log 反向补偿。
 * 3. 思考题：为什么 Seata 接入放在最后？从侵入度、全局锁风险与"其他步骤依赖稳定基础"三个角度论证顺序。
 * ============================================================
 */
