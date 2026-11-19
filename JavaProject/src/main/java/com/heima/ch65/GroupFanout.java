/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-06
 * 课名：群聊扇出：写扩散与读扩散
 * ------------------------------------------------------------
 * 知识点：
 *   - **群聊的核心问题是"一条消息发给谁、存几份"**：**写扩散（Write Fanout）**——消息写入每个成员的收件箱（N 份），读时只读自己的收件箱（快）；**读扩散（Read Fanout）**——只存一份在群会话里，读时按会话拉取（省空间，但拉取要处理"我在这个群"的权限与分页）。
 *   - **写扩散的代价**：500 人群 -> 1 条消息 500 份写入（**写放大**）；**大群（万人群）写扩散直接压垮存储**——所以大群通常用"读扩散 + 成员级已读"。
 *   - **读扩散的代价**：读时要"聚合我加入的所有群的最近消息"（会话列表），**拉取历史要按 seq 分页**；**已读位点按会话存**（每个成员一个 readSeq）。
 *   - **混合方案（工业主流）**：**小群写扩散**（成员少，读取快）、**大群读扩散**（成员多，写入省）——**按群规模阈值切换**；**会话列表用"最后消息 + 未读数"的轻量索引**（不展开全部消息）。
 *   - **与 54-02 配额/62-01 分区的类比**：扇出是"消息的路由与复制策略"——**与广播查询 vs 单分片查询的取舍同构**（39-05）。
 *   - 易错点：所有群都用写扩散（大群写放大爆炸）；读扩散不做会话列表索引（每次打开 App 要扫全部群）；群成员变更不同步（新成员看到旧消息或看不到）；已读位点按成员不按会话（群内已读混乱）。
 * ============================================================
 */

package com.heima.ch65;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class GroupFanout {
    /** 会话存储：群消息只存一份（读扩散基础） */
    static final Map<String, List<String>> groupMessages = new LinkedHashMap<>();
    /** 收件箱：userId -> 消息引用列表（写扩散） */
    static final Map<String, List<String>> inboxes = new LinkedHashMap<>();

    static final int WRITE_FANOUT_THRESHOLD = 200;   // 成员数阈值

    static void sendToGroup(String gid, List<String> members, String text) {
        groupMessages.computeIfAbsent(gid, k -> new ArrayList<>()).add(text);
        int seq = groupMessages.get(gid).size();
        boolean writeFanout = members.size() <= WRITE_FANOUT_THRESHOLD;
        if (writeFanout) {
            for (String m : members) {
                inboxes.computeIfAbsent(m, k -> new ArrayList<>())
                        .add(gid + "#" + seq + ":" + text);
            }
            System.out.printf("  群 %s（%d 人）-> 写扩散：1 条消息写 %d 份收件箱%n",
                    gid, members.size(), members.size());
        } else {
            System.out.printf("  群 %s（%d 人）-> 读扩散：只存 1 份，读时按成员聚合%n",
                    gid, members.size());
        }
    }

    /** 读：小群读收件箱，大群读群会话（简化：都按群拉取） */
    static List<String> readGroup(String gid, String userId, List<String> members,
                                  int limit) {
        if (!members.contains(userId)) {
            return List.of("403 非群成员");
        }
        List<String> all = groupMessages.getOrDefault(gid, List.of());
        int from = Math.max(0, all.size() - limit);
        List<String> out = new ArrayList<>();
        for (int i = from; i < all.size(); i++) {
            out.add((i + 1) + ":" + all.get(i));
        }
        return out;
    }

    static void compareCost(int members, int msgCount) {
        System.out.printf("  群 %d 人 × %d 条消息：%n", members, msgCount);
        System.out.printf("    写扩散存储 = %d 条（每成员每消息一份）%n", members * msgCount);
        System.out.printf("    读扩散存储 = %d 条（每群一份）%n", msgCount);
        System.out.printf("    写放大倍数 = %d×%n", members);
    }

    public static void main(String[] args) {
        List<String> small = List.of("u-1", "u-2", "u-3");
        List<String> big = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            big.add("u-" + i);
        }
        System.out.println("【小群（3 人）】");
        sendToGroup("g-small", small, "晚上开会");
        System.out.println("  收件箱 u-1：" + inboxes.get("u-1"));
        System.out.println("  读群消息：" + readGroup("g-small", "u-1", small, 10));

        System.out.println();
        System.out.println("【大群（5000 人）】");
        sendToGroup("g-big", big, "全员公告");
        System.out.println("  读群消息（u-0）：" + readGroup("g-big", "u-0", big, 10));
        System.out.println("  非成员读取：" + readGroup("g-big", "u-x", big, 10));

        System.out.println();
        System.out.println("【存储成本对比】");
        compareCost(3, 100);
        compareCost(5000, 100);
        System.out.println();
        System.out.println("  阈值策略：<=200 人写扩散（读快），>200 人读扩散（写省）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现扇出策略切换：按成员数选择写/读扩散，用 3 人群与 5000 人群各发 100 条消息，输出存储量与读取路径。
 * 2. 实现"群成员变更"：新成员入群后只能看到入群后的消息，退群后不能再读，验证权限边界。
 * 3. 思考题：为什么"大群读扩散"需要额外的会话列表索引？从"打开 App 时如何快速展示最近会话"分析。
 * ============================================================
 */
