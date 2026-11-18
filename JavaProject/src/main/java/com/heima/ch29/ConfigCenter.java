/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-07
 * 课名：配置中心与动态刷新
 * ------------------------------------------------------------
 * 知识点：
 *   - 配置散落在每个服务的 `application.yml` 里，改一个开关要重新打包发布 10 个服务——配置中心解决"集中管理 + 动态生效"。
 *   - 配置分层：**公共配置**（注册中心地址、日志格式）→ **应用级配置**（数据库、线程池）→ **环境级配置**（dev/test/prod 不同值）→ **实例级配置**（灰度开关）。优先级从低到高，后者覆盖前者。
 *   - 动态刷新：配置中心推送变更 → 客户端收到通知 → 更新本地缓存 → 触发 `@RefreshScope` 类重建或监听器回调。注意：**不是所有配置都能热更新**（数据库连接池大小、JVM 参数、线程池核心数通常需要重启）。
 *   - 灰度与回滚：配置变更要能按实例/按比例生效，并且能一键回滚到上一版本。配置错误导致的故障比代码 bug 更常见。
 *   - 安全：数据库密码等敏感配置要加密存储，客户端本地不能明文落盘；配置中心的访问要鉴权。
 *   - 易错：把配置中心当"实时数据库"频繁读写（配置是低频变更，不是状态存储）；改了配置但客户端没有监听，以为生效了；配置中心挂了服务无法启动（应支持本地快照兜底）。
 * ============================================================
 */

package com.heima.ch29;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ConfigCenter {
    /** 配置项：key -> (value, 版本号) */
    static class ConfigItem {
        String value;
        int version;

        ConfigItem(String value, int version) {
            this.value = value;
            this.version = version;
        }
    }

    /** 极简配置中心 */
    static class Center {
        final Map<String, ConfigItem> store = new LinkedHashMap<>();
        final List<String> listeners = new ArrayList<>();

        void put(String key, String value) {
            ConfigItem old = store.get(key);
            int v = old == null ? 1 : old.version + 1;
            store.put(key, new ConfigItem(value, v));
            System.out.println("  [中心] 更新 " + key + " = " + value + "（v" + v + "），推送变更");
            for (String l : listeners) {
                System.out.println("    -> 通知 " + l);
            }
        }
    }

    /** 客户端：本地快照 + 监听 */
    static class Client {
        final String name;
        final Map<String, String> snapshot = new HashMap<>();
        boolean hotReloadable = true;

        Client(String name, Center center) {
            this.name = name;
            center.listeners.add(name);
        }

        void load(Center center, String key) {
            ConfigItem item = center.store.get(key);
            if (item != null) {
                snapshot.put(key, item.value);
                System.out.println("  [" + name + "] 加载 " + key + " = " + item.value);
            }
        }

        void onChange(String key, String value) {
            if (!hotReloadable) {
                System.out.println("  [" + name + "] 收到 " + key + " 变更，但该项需要重启生效，暂存待下次启动");
                return;
            }
            snapshot.put(key, value);
            System.out.println("  [" + name + "] 热更新 " + key + " -> " + value);
        }
    }

    public static void main(String[] args) {
        Center center = new Center();
        center.store.put("app.timeout.ms", new ConfigItem("3000", 1));
        center.store.put("app.retry.max", new ConfigItem("3", 1));
        center.store.put("db.pool.size", new ConfigItem("20", 1));

        Client order = new Client("order-service", center);
        Client item = new Client("item-service", center);
        for (String k : List.of("app.timeout.ms", "app.retry.max", "db.pool.size")) {
            order.load(center, k);
            item.load(center, k);
        }

        System.out.println();
        System.out.println("=== 改超时：可热更新 ===");
        center.put("app.timeout.ms", "1500");
        order.onChange("app.timeout.ms", "1500");
        item.onChange("app.timeout.ms", "1500");

        System.out.println();
        System.out.println("=== 改连接池大小：需要重启 ===");
        order.hotReloadable = false;
        center.put("db.pool.size", "50");
        order.onChange("db.pool.size", "50");

        System.out.println();
        System.out.println("=== 配置优先级（后者覆盖前者）===");
        Map<String, String> merged = new LinkedHashMap<>();
        merged.put("公共配置 timeout=3000", "注册中心、日志格式");
        merged.put("应用配置 timeout=2000", "数据库、线程池");
        merged.put("环境配置 timeout=1000", "prod 覆盖 test");
        merged.put("实例配置 timeout=800", "灰度实例单独调小");
        for (Map.Entry<String, String> e : merged.entrySet()) {
            System.out.println("  " + e.getKey() + "  ← " + e.getValue());
        }
        System.out.println("  最终生效 timeout=800ms");

        System.out.println();
        System.out.println("=== 配置中心不可用：本地快照兜底 ===");
        System.out.println("  客户端启动时先从本地 snapshot 文件加载，再尝试连中心刷新");
        System.out.println("  -> 中心宕机不影响已有实例启动，只是拿不到最新配置");
        System.out.println("  版本回滚：中心保留最近 N 个版本，出问题一键回退 v" + (center.store.get("app.timeout.ms").version - 1));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Center` 加版本历史与 `rollback(key)` 方法，打印回滚后的值。
 * 2. 列出你项目中 5 个配置项，逐个判断能否热更新，并说明原因。
 * 3. 配置中心整体不可用时，服务应该如何启动？画出降级流程。
 * ============================================================
 */
