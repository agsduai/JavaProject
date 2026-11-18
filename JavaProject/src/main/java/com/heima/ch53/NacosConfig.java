/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：53-03
 * 课名：Nacos 配置中心与灰度
 * ------------------------------------------------------------
 * 知识点：
 *   - **Nacos config 的核心机制**：配置按 dataId+group+namespace 三元组定位；客户端**长轮询**（默认 30s 超时窗口，配置变更立即返回——比纯轮询省且快）；MD5 校验防重推；**@RefreshScope** 的实现本质是 Bean 销毁重建（49 章生命周期思想）。
 *   - **配置灰度发布**：Nacos 支持**按 IP/ Beta 发布**——配置先推给指定实例，验证后再全量（41-07 金丝雀的配置版）；**回滚 = 发布历史版本**（历史变更保留，45-06 MVCC 同源）。
 *   - **配置治理三原则**：① **配置分类**——数据库连接类（低频高敏）/业务开关类（高频）分 dataId；② **变更规范**——先在 test namespace 验证 + 变更说明必填 + 灰度；③ **敏感配置**——Jasypt 加密或接 KMS，明文密码进配置中心 = 36 章密钥纪律违规。
 *   - **大配置的坑**：单个 dataId 过大（推送包限制与解析压力）；@RefreshScope 滥用（大对象重建导致抖动，应只标注真正需要热更的 Bean）；**配置监听线程数**默认较小，大量 dataId 监听要调优。
 *   - **与 45-11 的对照**：45-11 手写了配置客户端（快照/单调/拉齐），Nacos 全部内置——**手写过的机制在产品里逐个对应**：快照=本地缓存文件、单调=revision、拉齐=兜底全量拉取。
 *   - 易错点：长轮询超时调太短（请求风暴）；灰度验证期把 Beta 当全量（未全量就宣布完成）；配置变更无审计（谁改的查不到，41-10）；RefreshScope 标注在启动重的 Bean（变更瞬间 GC/重建风暴）。
 * ============================================================
 */

package com.heima.ch53;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class NacosConfig {
    record ConfigSnapshot(String dataId, long version, String content, String md5) {
    }

    /** 长轮询：变更立即返回，无变更等满窗口 */
    static void longPolling(List<ConfigSnapshot> server, ConfigSnapshot local, int windowMs) {
        ConfigSnapshot hit = server.stream()
                .filter(s -> s.dataId().equals(local.dataId()) && s.version() > local.version())
                .findFirst().orElse(null);
        if (hit != null) {
            System.out.printf("  [%dms] 检测到变更 v%d -> 拉取并应用（MD5=%s）%n",
                    windowMs / 1000, hit.version(), hit.md5());
        } else {
            System.out.printf("  [%dms] 无变更，进入下一轮长轮询%n", windowMs / 1000);
        }
    }

    public static void main(String[] args) {
        ConfigSnapshot local = new ConfigSnapshot("order.yaml", 41, "timeout=3000", "md5-41");
        List<ConfigSnapshot> server = List.of(local,
                new ConfigSnapshot("order.yaml", 42, "timeout=5000", "md5-42"));
        longPolling(server, local, 30000);
        System.out.println();
        System.out.println("  对照纯轮询：30s 一次全量拉取 vs 长轮询变更秒级感知 + 无变更零数据传输");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整配置客户端（长轮询 + MD5 校验 + @RefreshScope 模拟重建 + 本地快照），服务端 3 版变更输出轨迹。
 * 2. 实现配置灰度发布：Beta 按 IP 列表推送，验证未灰度实例仍用旧版本，全量后切换。
 * 3. 思考题：为什么长轮询比纯轮询与纯推送都更适合配置中心？从"变更时效、无变更开销、实现复杂度"三维度对比。
 * ============================================================
 */
