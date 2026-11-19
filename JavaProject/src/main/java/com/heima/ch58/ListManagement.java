/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：58-07
 * 课名：名单管理与名单同步
 * ------------------------------------------------------------
 * 知识点：
 *   - **名单是风控最快见效的手段**：黑名单（必拦）、白名单（必放）、灰名单（观察/挑战）、设备名单、IP 名单、商户名单——**名单的命中应该是最短路径**（否则拦不住真实攻击的量）。
 *   - **名单的存储与查询性能**：千万级黑名单用**布隆过滤器预筛 + 精确集合复核**（40-10/54-07 同款）；**Redis Set/Bitmap** 支持 O(1) 判断；**本地缓存 + 变更推送**（58-08）避免每次决策都查中心——**名单查询要在 1ms 内**。
 *   - **名单的同步链路**：业务系统/风控运营 -> 名单中心（唯一事实源）-> 变更事件（MQ）-> 各决策节点本地缓存更新——**推拉结合**：推送失败靠定期全量对齐兜底（45-11 快照自持思想）。
 *   - **名单的时效与冲突**：临时名单带 TTL（活动期封禁）；**多名单冲突**（黑名单 + 白名单同命中）——**白名单优先**（人工介入的强信号）或"黑名单优先"（安全优先）是业务决策，必须显式定义。
 *   - **名单的审计与误伤**：每次名单变更有操作人/原因/审批（51-10）；**误加名单是高频事故**（客诉）——需要"名单生效前预览影响面"（影响多少笔交易）与一键撤销。
 *   - 易错点：名单无 TTL（临时封禁变永久，客诉）；名单无审批（运营误加）；名单查询走 DB（性能崩）；黑白名单冲突无优先级（结果随机）。
 * ============================================================
 */

package com.heima.ch58;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ListManagement {
    /** 名单项：值 + 类型 + 过期时间 + 原因 */
    record Entry(String value, String listType, long expireAtMs, String reason) {
        boolean active(long now) {
            return expireAtMs == 0 || now < expireAtMs;
        }
    }

    /** 布隆预筛（快速否定） + 精确集合（避免误伤） */
    static class ListIndex {
        final BitSet bloom = new BitSet(4096);
        final Map<String, Entry> exact = new LinkedHashMap<>();

        void add(Entry e) {
            exact.put(e.value(), e);
            bloom.set(hash1(e.value()));
            bloom.set(hash2(e.value()));
        }

        boolean mightContain(String v) {
            return bloom.get(hash1(v)) && bloom.get(hash2(v));
        }

        static int hash1(String v) {
            return Math.abs(v.hashCode()) & 4095;
        }

        static int hash2(String v) {
            return Math.abs(v.hashCode() * 31 + 7) & 4095;
        }
    }

    /** 黑白名单冲突：白名单优先（人工强信号） */
    static String decide(ListIndex black, ListIndex white, String value, long now) {
        Entry w = white.exact.get(value);
        if (w != null && w.active(now)) {
            return "WHITELIST -> ALLOW（白名单优先）";
        }
        Entry b = black.exact.get(value);
        if (b != null && b.active(now)) {
            return "BLACKLIST -> BLOCK（原因：" + b.reason() + "）";
        }
        if (black.mightContain(value)) {
            return "布隆可能命中但精确未命中 -> ALLOW（误判，安全侧少拦）";
        }
        return "未命中名单 -> 继续后续规则";
    }

    public static void main(String[] args) {
        long now = 1000;
        var black = new ListIndex();
        black.add(new Entry("card-6222XXXX", "black", 0, "历史盗刷"));
        black.add(new Entry("ip-10.0.0.9", "black", 500, "临时封禁（已过期）"));
        var white = new ListIndex();
        white.add(new Entry("card-6222XXXX", "white", 0, "VIP 客户人工放行"));

        System.out.println("  黑+白同命中：" + decide(black, white, "card-6222XXXX", now));
        System.out.println("  黑名单过期：" + decide(black, white, "ip-10.0.0.9", now));
        System.out.println("  普通值：" + decide(black, white, "card-9999", now));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整名单中心：增删改查 + TTL + 审批字段 + 变更事件，模拟"运营误加名单 -> 预览影响面 -> 一键撤销"全流程。
 * 2. 实现"名单同步"：名单中心推送到 3 个决策节点（本地缓存），注入推送失败验证定期全量对齐兜底。
 * 3. 思考题：黑白名单冲突该谁优先？分别从"安全优先"与"人工强信号"两个立场论证，并给出你的默认选择与配置化方案。
 * ============================================================
 */
