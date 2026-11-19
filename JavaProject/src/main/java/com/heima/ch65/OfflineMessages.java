/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-07
 * 课名：离线消息与推送
 * ------------------------------------------------------------
 * 知识点：
 *   - **离线消息的本质是"暂存 + 补发"**：接收方不在线时，消息已落库（STORED），**不推送**；**用户上线时按会话 seq 拉取增量**——**所以"离线消息"不是单独的消息类型，而是"未 ACK 的消息"**（65-04 的状态机）。
 *   - **两种取回方式**：**上线时主动拉取**（客户端发 `sync(lastSeq)`，最可靠）与**系统推送唤醒**（APNs/FCM 推通知，用户点开 App 再拉）——**推送只是"叫醒"，不是"投递"**（推送可能丢，拉取才是事实）。
 *   - **离线消息的保留策略**：按时间（7 天）与条数（每会话 1000 条）双限制——**超出后只保留"未读汇总"**（提示"你有 N 条未读，更早的消息请在历史中查看"）；**存储上要与"消息漫游"区分**（65-08：漫游是全量历史，离线是"待补发"）。
 *   - **推送的降级与合并**：同一会话短时间多条离线消息 -> **合并成一条推送**（避免轰炸）；**免打扰/静音**的会话不推送（只更新未读数）；**推送失败的兜底**是用户下次打开 App 主动拉取。
 *   - **与 28/62 章的关系**：离线消息 = "未被消费的消息"，与 MQ 的 lag 同构；**保留策略 = 62-08 的 retention**。
 *   - 易错点：把推送当投递（推送失败就以为消息丢了）；离线消息无限堆积（存储爆炸）；多条离线消息逐条推送（用户体验灾难）；无"已读汇总"（用户不知道有多少未读）。
 * ============================================================
 */

package com.heima.ch65;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class OfflineMessages {
    record Msg(long seq, String convId, String from, String text, long atMs) {
    }

    /** 每用户每会话的消息流水（服务端存储） */
    static final Map<String, List<Msg>> store = new LinkedHashMap<>();
    /** 每用户每会话的已读位点（65-05） */
    static final Map<String, Long> readSeq = new LinkedHashMap<>();
    /** 在线状态 */
    static final Map<String, Boolean> online = new LinkedHashMap<>();

    static void receive(String toUser, String convId, String from, String text, long now) {
        List<Msg> list = store.computeIfAbsent(toUser + "@" + convId, k -> new ArrayList<>());
        long seq = list.size() + 1;
        list.add(new Msg(seq, convId, from, text, now));
        if (Boolean.TRUE.equals(online.get(toUser))) {
            System.out.printf("  %s 在线 -> 实时推送 seq=%d%n", toUser, seq);
        } else {
            System.out.printf("  %s 离线 -> 暂存 seq=%d（等上线拉取）%n", toUser, seq);
        }
    }

    /** 上线拉取：按 seq 增量 + 保留策略 */
    static void login(String user, int maxPerConv) {
        online.put(user, true);
        System.out.println("  " + user + " 上线 -> 拉取增量");
        store.forEach((key, list) -> {
            if (!key.startsWith(user + "@")) {
                return;
            }
            String convId = key.substring(key.indexOf('@') + 1);
            long last = readSeq.getOrDefault(key, 0L);
            List<Msg> delta = new ArrayList<>();
            for (Msg m : list) {
                if (m.seq() > last) {
                    delta.add(m);
                }
            }
            if (delta.isEmpty()) {
                return;
            }
            if (delta.size() > maxPerConv) {
                System.out.printf("    会话 %s：未读 %d 条，超出上限 %d -> 只返回最新 %d 条 + 未读汇总%n",
                        convId, delta.size(), maxPerConv, maxPerConv);
                delta = delta.subList(delta.size() - maxPerConv, delta.size());
            }
            System.out.printf("    会话 %s：补发 %d 条 -> %s%n", convId, delta.size(),
                    delta.stream().map(Msg::text).toList());
            readSeq.put(key, list.get(list.size() - 1).seq());
        });
    }

    /** 推送合并：同会话多条离线只推一条 */
    static void pushNotify(String user) {
        int totalUnread = 0;
        int convCount = 0;
        for (Map.Entry<String, List<Msg>> e : store.entrySet()) {
            if (!e.getKey().startsWith(user + "@")) {
                continue;
            }
            long last = readSeq.getOrDefault(e.getKey(), 0L);
            long unread = e.getValue().stream().filter(m -> m.seq() > last).count();
            if (unread > 0) {
                convCount++;
                totalUnread += unread;
            }
        }
        if (totalUnread == 0) {
            System.out.println("  无未读，不推送");
            return;
        }
        if (convCount == 1) {
            System.out.printf("  推送：你有 %d 条新消息%n", totalUnread);
        } else {
            System.out.printf("  推送（合并）：你有 %d 个会话共 %d 条新消息%n", convCount, totalUnread);
        }
    }

    public static void main(String[] args) {
        online.put("u-2", false);
        System.out.println("【消息到达】");
        for (int i = 1; i <= 5; i++) {
            receive("u-2", "c-1", "u-1", "msg-" + i, i * 1000L);
        }
        receive("u-2", "c-2", "u-3", "另一会话", 6000L);

        System.out.println();
        System.out.println("【推送（合并）】");
        pushNotify("u-2");

        System.out.println();
        System.out.println("【上线拉取】");
        login("u-2", 3);

        System.out.println();
        System.out.println("【再次上线：已无未读】");
        login("u-2", 3);
        System.out.println();
        System.out.println("  结论：推送只负责叫醒，拉取才是投递的事实来源");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现离线消息完整流程：暂存/上线拉取/保留上限/未读汇总，用 100 条离线消息验证"超限只补最新 + 汇总提示"。
 * 2. 实现推送合并与静音：会话免打扰时不推送只累加未读，验证"3 个会话 10 条消息只推 1 条"。
 * 3. 思考题：为什么"推送失败不等于消息丢失"？从"推送只叫醒、拉取是事实源"的设计分离分析。
 * ============================================================
 */
