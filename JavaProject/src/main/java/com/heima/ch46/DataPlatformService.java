/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：46-10
 * 课名：数据平台服务化
 * ------------------------------------------------------------
 * 知识点：
 *   - **数据平台的价值在"被业务系统消费"**：报表是终点，**特征 API（推荐/风控实时取数）、指标 API（运营后台）、榜单缓存（大屏/首页）**是数据反哺业务的出口——46 章所有铺垫（分层、质量、MPP）最终为了这几个服务。
 *   - **指标平台统一口径**：指标定义（名称/口径/维度/负责人）注册一次，报表/大屏/接口全部引用——**"GMV 是多少"只有一个权威答案**（46-01 打架问题的终解）。指标变更走评审，历史口径留档。
 *   - **特征平台**：离线特征（湖表 T+1）与在线特征（Redis 实时）**同一套定义**——训练与推理特征一致性（"训练用昨天数据、线上用今天数据"的口径错位是模型效果劣化的隐形元凶）。
 *   - **数据 API 的工程要求**：与业务 API 同标准（40-09 分层、限流、鉴权、监控）；**查询下推到正确的存储**（点查走 Redis/StarRocks，聚合走 MPP）；大结果集异步导出（对象存储 + 通知）。
 *   - **数据的 SLA 与告警**：API 可用性、数据新鲜度（"数据最后更新于 5 分钟前"要暴露）、下游依赖的数据分区就绪通知（调度事件驱动而不是轮询）。
 *   - **组织协作**：数据团队提供平台与规范，业务团队生产与消费——**自助化是目标**（自助建表模板、自助质量规则、自助指标注册），全部人工排队的服务不可扩展。
 *   - 易错点：指标口径散落在各报表 SQL 里（没人说得清哪个准）；特征线上线下两套定义（训练-推理偏差）；数据 API 走了错误存储（点查打到 Trino 全表扫）；"数据就绪"靠人盯着（下游 9 点开工发现上游 10 点才产出）。
 * ============================================================
 */

package com.heima.ch46;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DataPlatformService {
    static void metricRegistry() {
        System.out.println("【指标平台：GMV 只有一个定义】");
        record Metric(String name, String formula, String dims, String owner, List<String> consumers) {
        }
        List<Metric> metrics = List.of(
                new Metric("gmv_daily", "SUM(dwd.orders.amount) WHERE status=PAID",
                        "date/category/channel", "data-lee",
                        List.of("大屏", "运营日报", "运营后台 API")),
                new Metric("order_count", "COUNT(DISTINCT order_id)", "date/status", "data-lee",
                        List.of("大屏", "周报")));
        for (Metric m : metrics) {
            System.out.printf("  %-10s 口径=%s%n      维度=%-20s 负责人=%s 消费方=%d 个%n",
                    m.name(), m.formula(), m.dims(), m.owner(), m.consumers().size());
        }
        System.out.println("  变更流程：改口径 -> 评审 -> 版本留档 -> 消费方通知（41-10 审计思想）");
        System.out.println();
    }

    static void featureConsistency() {
        System.out.println("【特征平台：训练与推理同源】");
        record Feature(String name, String offline, String online, String consistent) {
        }
        List<Feature> feats = List.of(
                new Feature("user_p30_click", "湖表 T+1 聚合", "Redis 实时累加", "口径一致：近30天点击"),
                new Feature("shop_hot_score", "湖表计算", "湖表快照回灌 Redis", "同一份计算逻辑"));
        for (Feature f : feats) {
            System.out.printf("  %-14s 离线=%-14s 在线=%-16s %s%n",
                    f.name(), f.offline(), f.online(), f.consistent());
        }
        System.out.println("  反例：离线口径=去重点击，在线口径=原始点击 -> 训练与线上分布不一致");
        System.out.println("  对策：特征定义注册化，离线在线由同一份代码/SQL 生成");
        System.out.println();
    }

    static void dataApi() {
        System.out.println("【数据 API 的存储路由】");
        record Api(String path, String storage, String latency, String note) {
        }
        List<Api> apis = List.of(
                new Api("GET /metrics/gmv?date=", "StarRocks 物化视图", "45ms", "预聚合命中"),
                new Api("GET /rank/products", "Redis（ADS 回灌）", "3ms", "大屏榜单"),
                new Api("GET /export/orders?month=", "异步导出->OSS", "分钟级", "大结果集不走同步链路"));
        for (Api a : apis) {
            System.out.printf("  %-28s %-18s %-7s %s%n", a.path(), a.storage(), a.latency(), a.note());
        }
        System.out.println("  反例：点查打到 Trino 扫明细 -> 8s 超时 —— 存储选型错位");
        System.out.println();
    }

    static void freshnessSignal() {
        System.out.println("【数据新鲜度必须可查询】");
        List<String> items = List.of(
                "每个 API 响应带 meta.data_freshness（分区时间）",
                "分区就绪事件驱动下游调度（DAG 依赖，不靠轮询）",
                "新鲜度 SLA 告警：日报数据 9 点未产出即告警",
                "新鲜度降级策略：分区缺失时 API 显式返回“数据延迟”而非旧值冒充");
        for (String i : items) {
            System.out.println("  - " + i);
        }
        System.out.println("  42 章可观测思想在数据平台的落地：数据也有 SLI/SLO");
    }

    public static void main(String[] args) {
        metricRegistry();
        featureConsistency();
        dataApi();
        freshnessSignal();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的团队注册 3 个核心指标（口径/维度/消费方），实现一个"口径唯一"的指标查询接口原型。
 * 2. 排查一次"训练-推理特征偏差"（可虚构）：从特征定义、离线口径、在线口径三个环节定位不一致点并修复。
 * 3. 思考题：为什么"数据新鲜度"是数据 API 的 SLA 之一？从下游决策依赖与错误数据冒充新鲜的风险两个角度分析。
 * ============================================================
 */
