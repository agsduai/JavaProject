/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：68-09
 * 课名：审计与可追溯
 * ------------------------------------------------------------
 * 知识点：
 *   - **审计是隐私合规的"证据链"**：**谁（who）在何时（when）从哪（where）对什么数据（what）做了什么（action）**——每条记录不可篡改、可按主体/资源/时间检索。
 *   - **审计的三类内容**：**访问审计**（查询/导出/查看详情）、**变更审计**（修改/删除/权限变更）、**管理审计**（授权/审批/配置变更）——**敏感数据的"读取"也要审计**（不止写）。
 *   - **不可篡改的实现**：**append-only 表**（只插入，禁更新删除）、**哈希链**（每条记录含前一条的哈希，篡改一条则后续全部不匹配）、**WORM 存储/区块链**（高合规场景）。
 *   - **审计的落地要点**：**异步写入**（不阻塞业务 42/66 章）、**脱敏存储**（审计里也不能存明文敏感数据）、**保留期**（通常 6 个月~3 年，按法规 68-10）、**可检索**（按用户/资源/时间/操作类型）。
 *   - **与 51-10 的关系**：支付的操作审计是"资金证据"，本章的数据访问审计是"隐私证据"——**同一套 append-only + 哈希链思想**。
 *   - 易错点：审计可被业务账号删除（等于没有）；审计不含"读"操作（只记写）；审计日志明文存敏感数据；审计无检索能力（出了事查不出来）。
 * ============================================================
 */

package com.heima.ch68;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AuditTrail {
    /** 审计记录：含前一条哈希，形成哈希链 */
    record Entry(long seq, long atMs, String who, String action, String resource,
                 String detail, String prevHash, String hash) {
    }

    static final List<Entry> ledger = new ArrayList<>();
    static long seq = 0;

    /** 简化哈希：真实系统用 SHA-256（36-06 同款） */
    static String hashOf(String content) {
        return Integer.toHexString(content.hashCode()) + "-"
                + Integer.toHexString((content + "#salt").hashCode());
    }

    static void record(String who, String action, String resource, String detail, long atMs) {
        String prev = ledger.isEmpty() ? "GENESIS" : ledger.get(ledger.size() - 1).hash();
        String content = (seq + 1) + "|" + atMs + "|" + who + "|" + action + "|"
                + resource + "|" + detail + "|" + prev;
        Entry e = new Entry(++seq, atMs, who, action, resource, detail, prev, hashOf(content));
        ledger.add(e);
        System.out.printf("  #%d %s %-8s %-16s %s%n", e.seq(), e.who(), e.action(),
                e.resource(), e.detail());
    }

    /** 完整性校验：重算哈希链 */
    static boolean verify() {
        String prev = "GENESIS";
        for (Entry e : ledger) {
            String content = e.seq() + "|" + e.atMs() + "|" + e.who() + "|" + e.action()
                    + "|" + e.resource() + "|" + e.detail() + "|" + prev;
            if (!e.prevHash().equals(prev) || !e.hash().equals(hashOf(content))) {
                System.out.println("  校验失败于 #" + e.seq() + "（链断裂或被篡改）");
                return false;
            }
            prev = e.hash();
        }
        return true;
    }

    static void query(String who, String resource) {
        System.out.println("  查询 who=" + who + " resource=" + resource + "：");
        ledger.stream()
                .filter(e -> (who == null || e.who().equals(who))
                        && (resource == null || e.resource().equals(resource)))
                .forEach(e -> System.out.printf("    #%d %s%n", e.seq(), e.action()));
    }

    public static void main(String[] args) {
        System.out.println("【写入审计（含读取操作）】");
        record("agent-01", "QUERY", "user:phone", "查看用户手机号（脱敏视图）", 1000);
        record("agent-01", "EXPORT", "order:2026-11", "导出 500 条订单（已审批 REQ-9）", 2000);
        record("dba-02", "ALTER", "user:id_card", "新增加密列", 3000);
        record("admin-03", "GRANT", "role:risk", "授予 L4 数据访问权限", 4000);
        record("agent-01", "QUERY", "user:phone", "再次查看（第 2 次）", 5000);

        System.out.println();
        System.out.println("【完整性校验】" + (verify() ? "通过（哈希链完整）" : "失败"));

        System.out.println();
        System.out.println("【篡改检测】把 #2 的 detail 改掉后重算");
        Entry original = ledger.get(1);
        ledger.set(1, new Entry(original.seq(), original.atMs(), original.who(),
                original.action(), original.resource(), "导出 5 条订单（已审批 REQ-9，已篡改）",
                original.prevHash(), original.hash()));
        System.out.println("  " + (verify() ? "未检测到（不应该）" : "检测到篡改（哈希不匹配）"));
        ledger.set(1, original);

        System.out.println();
        System.out.println("【检索】");
        query("agent-01", "user:phone");

        System.out.println();
        System.out.println("【审计要点】");
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("覆盖读操作", "敏感数据的查询/导出都要记");
        rules.put("不可篡改", "append-only + 哈希链");
        rules.put("脱敏存储", "审计日志本身也不放明文");
        rules.put("可检索", "按 who/resource/时间/动作");
        rules.put("保留期", "按法规（通常 6 个月~3 年）");
        rules.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现审计链：append-only 写入 + 哈希链校验 + 按主体/资源检索，并演示篡改被检出。
 * 2. 实现"审计异步写入"：业务线程只入队，后台批量落盘，验证业务耗时不受审计影响。
 * 3. 思考题：为什么"读取操作"必须审计？从"内部人员批量查询导出"是最常见泄露渠道分析（36 章内部风险）。
 * ============================================================
 */
