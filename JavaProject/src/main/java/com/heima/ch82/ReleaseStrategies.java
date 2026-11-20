/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-01
 * 课名：四种发布形态：蓝绿、金丝雀、滚动、功能开关
 * ------------------------------------------------------------
 * 知识点：
 *   - **蓝绿部署**：**两套完整环境，切流量在网关做**——**回滚就是切回来（秒级）**，但**资源翻倍**，且**数据库是共享的，回滚救不了脏数据**。
 *   - **金丝雀（灰度）**：**新版本只接一小部分流量（1%~10%），观察后再扩**——**爆炸半径可控**，是**互联网公司的主流选择**，代价是**要能按用户/请求分流**。
 *   - **滚动发布**：**逐台替换实例**——**不额外占资源**，但**新旧版本会同时在线一段时间**，**回滚要把整个滚动过程倒着走一遍（分钟级）**，且**期间接口必须双向兼容**。
 *   - **功能开关（Feature Flag）**：**代码先全量上线，行为由开关控制**——**回滚是改一个配置（亚秒级）**，但**开关会烂在代码里**，必须有**开关生命周期管理与清理机制**。
 *   - **回滚耗时不是一个数，是四段之和**：**发现时间 + 决策时间 + 生效时间 + 数据修复时间**——**前两段是人的问题，后两段才是技术问题**，很多团队只优化了「生效时间」。
 *   - 易错点：把「能回滚代码」当成「能回滚」；蓝绿共享库却以为切回去就干净了；滚动发布期间改了接口字段（旧实例解析不了）；开关加了不删（一年后没人敢动）。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

import java.util.ArrayList;
import java.util.List;

public class ReleaseStrategies {
    // 一次发布要付出的代价，全部量化成数字
    record Strategy(String name, int extraInstancePct, int rollbackEffectSec,
                    int blastRadiusPct, boolean needsRouting) {
    }

    // 回滚总耗时 = 发现 + 决策 + 生效 + 数据修复
    record Timeline(int detectSec, int decideSec, int effectSec, int repairSec) {
        int total() {
            return detectSec + decideSec + effectSec + repairSec;
        }
    }

    static void table(List<Strategy> list) {
        System.out.printf("%-12s %-10s %-10s %-10s %s%n",
                "形态", "额外资源", "生效耗时", "爆炸半径", "需要分流能力");
        for (Strategy s : list) {
            System.out.printf("%-12s %-10s %-10s %-10s %s%n",
                    s.name(), s.extraInstancePct() + "%",
                    s.rollbackEffectSec() + "s",
                    s.blastRadiusPct() + "%",
                    s.needsRouting() ? "是" : "否");
        }
    }

    static void timeline(String name, Timeline t) {
        System.out.printf("  %-10s 发现 %3ds + 决策 %3ds + 生效 %3ds + 数据修复 %3ds = %4ds（%.1f 分钟）%n",
                name, t.detectSec(), t.decideSec(), t.effectSec(), t.repairSec(),
                t.total(), t.total() / 60.0);
    }

    public static void main(String[] args) {
        List<Strategy> list = new ArrayList<>();
        list.add(new Strategy("蓝绿", 100, 5, 100, true));
        list.add(new Strategy("金丝雀", 10, 5, 10, true));
        list.add(new Strategy("滚动", 0, 300, 60, false));
        list.add(new Strategy("功能开关", 0, 1, 100, true));
        table(list);

        System.out.println();
        System.out.println("【回滚耗时拆解】同一个「价格算错了」的事故，四种形态各自多久恢复");
        timeline("蓝绿", new Timeline(120, 60, 5, 600));
        timeline("金丝雀", new Timeline(120, 60, 5, 0));
        timeline("滚动", new Timeline(120, 60, 300, 600));
        timeline("功能开关", new Timeline(120, 60, 1, 0));

        System.out.println();
        System.out.println("【读结论】");
        System.out.println("  1. 金丝雀的爆炸半径只有 10%，所以「数据修复」可以是 0——脏数据只影响了 10% 用户");
        System.out.println("  2. 蓝绿和功能开关都是 100% 爆炸半径，代码切回去了，已经写错的订单还得人工修");
        System.out.println("  3. 滚动发布的 300s 生效耗时最容易被忽略：它要倒着滚一遍");
        System.out.println("  4. 四种形态的「发现 120s + 决策 60s」完全一样——这段是人的流程，不换架构就优化不掉");

        System.out.println();
        System.out.println("【选型】");
        System.out.println("  改动影响资金/库存  -> 金丝雀 + 功能开关（双重保险）");
        System.out.println("  纯前端/展示层改版  -> 功能开关（最快回滚）");
        System.out.println("  无状态服务扩容     -> 滚动（最省资源）");
        System.out.println("  数据库大版本升级   -> 蓝绿 + 双写（唯一能整体切换的方案）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给滚动发布加一条约束：集群 20 台、每台启动 40s、滚动步长 2 台，算出全量替换与回滚各需多久。
 * 2. 把「发现时间」从 120s 改成 30s（加自动告警），重算四种形态的总回滚耗时，说明瓶颈转移到了哪里。
 * 3. 思考题：为什么说「蓝绿共享数据库时，切回去不等于回滚干净」？举一个具体的脏数据例子。
 * ============================================================
 */

