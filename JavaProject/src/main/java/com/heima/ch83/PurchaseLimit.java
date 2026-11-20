/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-07
 * 课名：限购与防刷
 * ------------------------------------------------------------
 * 知识点：
 *   - **限购要按「维度」设计，不是只按用户**：**用户维度防正常人多买，设备维度防换号，IP 维度防批量注册，支付账号维度防同一张卡**——**单维度一定会被绕过**。
 *   - **「限购 N 件」要区分「每单限购」与「活动期累计限购」**：**每单限购用 `min(购买数, N)` 就能实现，累计限购必须落库计数并原子累加**。
 *   - **计数必须原子且带窗口**：**用 `AtomicInteger` 或 Redis `INCR` + 过期时间**，**「先查再写」同样会超**，**和库存是同一类问题**。
 *   - **幂等键是防重的最后一道闸**：**同一 `(userId, activityId, requestId)` 只允许成功一次**，**重试返回第一次结果**，**这能挡住「网络超时后客户端重试」造成的重复下单**。
 *   - **风控规则要能解释**：**命中「同设备 10 分钟内 20 个账号」要能给出具体证据**，**否则误杀正常用户（如家庭共用设备）时无法申诉**。
 *   - 易错点：只在网关限购（绕过网关直连服务就失效）；限购计数用内存 Map（多实例各算各的）；把「限购」做成「黑名单」导致正常用户被永久拦；风控阈值写死无法按活动调整。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PurchaseLimit {
    record Attempt(String userId, String deviceId, String ip, String requestId, int qty) {
    }

    // 多维度计数器：任一维度超限就拒绝
    static class LimitEngine {
        final int perUser;
        final int perDevice;
        final Map<String, AtomicInteger> userCount = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> deviceCount = new ConcurrentHashMap<>();
        final Set<String> idemKeys = ConcurrentHashMap.newKeySet();
        final Map<String, String> idemResult = new ConcurrentHashMap<>();

        LimitEngine(int perUser, int perDevice) {
            this.perUser = perUser;
            this.perDevice = perDevice;
        }

        String buy(Attempt a) {
            String key = a.userId() + "|" + a.requestId();
            String hit = idemResult.get(key);
            if (hit != null) {
                return "幂等重放 -> " + hit;
            }
            AtomicInteger u = userCount.computeIfAbsent(a.userId(), k -> new AtomicInteger());
            AtomicInteger d = deviceCount.computeIfAbsent(a.deviceId(), k -> new AtomicInteger());

            // 先占额度再判断，失败要回滚，避免「判断通过但额度被抢走」
            if (u.addAndGet(a.qty()) > perUser) {
                u.addAndGet(-a.qty());
                return "拒绝：用户 " + a.userId() + " 累计超限（上限 " + perUser + "）";
            }
            if (d.addAndGet(a.qty()) > perDevice) {
                d.addAndGet(-a.qty());
                u.addAndGet(-a.qty());
                return "拒绝：设备 " + a.deviceId() + " 累计超限（上限 " + perDevice + "）";
            }
            String result = "成功：买入 " + a.qty() + " 件";
            idemResult.put(key, result);
            idemKeys.add(key);
            return result;
        }
    }

    public static void main(String[] args) {
        LimitEngine eng = new LimitEngine(2, 3);

        System.out.println("【1. 单用户：上限 2 件，分两次买】");
        System.out.printf("  %s%n", eng.buy(new Attempt("u1", "d1", "1.1.1.1", "r1", 1)));
        System.out.printf("  %s%n", eng.buy(new Attempt("u1", "d1", "1.1.1.1", "r2", 1)));
        System.out.printf("  %s%n", eng.buy(new Attempt("u1", "d1", "1.1.1.1", "r3", 1)));
        System.out.println("  第三次被拒，因为用户维度累计已经到 2。");

        System.out.println();
        System.out.println("【2. 换账号但同一设备：设备维度兜住】");
        System.out.println("  u1 已经在设备 d1 上用掉 2 件额度，设备上限是 3");
        System.out.printf("  %s%n", eng.buy(new Attempt("u2", "d1", "1.1.1.1", "r4", 1)));
        System.out.printf("  %s%n", eng.buy(new Attempt("u3", "d1", "1.1.1.1", "r5", 1)));
        System.out.printf("  设备 d1 当前计数 %d（上限 %d）%n",
                eng.deviceCount.get("d1").get(), eng.perDevice);
        System.out.println("  u2 是新账号、自己一件没买，但设备额度只剩 1 件，只买到 1 件；");
        System.out.println("  u3 再来一件时设备额度已满 3，即便账号全新也被拒——单维度限购挡不住换号，设备维度能。");

        System.out.println();
        System.out.println("【3. 幂等：同一请求重试不重复扣额度】");
        System.out.printf("  %s%n", eng.buy(new Attempt("u4", "d2", "2.2.2.2", "r6", 1)));
        System.out.printf("  %s%n", eng.buy(new Attempt("u4", "d2", "2.2.2.2", "r6", 1)));
        System.out.printf("  用户 u4 当前计数 %d（重试没有变成 2）%n",
                eng.userCount.get("u4").get());

        System.out.println();
        System.out.println("【4. 只做单维度限购会漏掉什么】");
        Map<String, Set<String>> deviceUsers = new HashMap<>();
        String[] devices = {"d9", "d9", "d9", "d9", "d9"};
        String[] users = {"a1", "a2", "a3", "a4", "a5"};
        for (int i = 0; i < devices.length; i++) {
            deviceUsers.computeIfAbsent(devices[i], k -> new HashSet<>()).add(users[i]);
        }
        System.out.printf("  设备 d9 在 10 分钟内出现 %d 个不同账号 -> 命中风控规则%n",
                deviceUsers.get("d9").size());
        System.out.println("  如果只按用户限购，每个账号各买 2 件，5 个账号就是 10 件，全从同一台设备刷走。");

        System.out.println();
        System.out.println("【5. 风控结论要带证据，否则误杀无法申诉】");
        List<String> evidence = new ArrayList<>();
        evidence.add("设备 d9 关联账号 5 个");
        evidence.add("同一 IP 段 2.2.2.0/24 关联账号 5 个");
        evidence.add("下单间隔中位数 0.8s（正常用户约 35s）");
        for (String e : evidence) {
            System.out.println("  - " + e);
        }
        System.out.println("  有证据才能人工复核；只给一个「风险分 87」等于没给。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给限购加「活动期累计」与「每单限购」两套阈值，验证 u1 每单最多 2 件但活动期最多 3 件。
 * 2. 把 IP 维度也加进引擎，说明为什么家庭共用 WiFi 会被误杀，以及怎么降低误杀率。
 * 3. 思考题：如果限购计数放 Redis，Redis 主从切换丢了一次 INCR，会超卖多少？怎么补？
 * ============================================================
 */

