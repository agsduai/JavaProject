/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：23-09
 * 课名：缓存机制
 * ------------------------------------------------------------
 * 知识点：
 *   - 一级缓存（`SqlSession` 级）：默认开启，同一个会话内相同 SQL + 相同参数只查一次库。
 *   - 一级缓存失效的四种情况：`commit`/`close`、执行增删改、`clearCache()`、查询参数不同。
 *   - 二级缓存（`Mapper` 级 / namespace 级）：跨会话共享，默认关闭，需在 XML 加 `<cache/>` 且实体类实现 `Serializable`。
 *   - 二级缓存的坑：多表关联时，一个表更新不会让另一个 namespace 的缓存失效，容易出现脏数据；生产环境更常用 Redis 做集中缓存。
 *   - `useCache="false"` 可对单条查询关闭二级缓存；`flushCache="true"` 让语句执行后清空缓存。
 *   - 易错：把"一级缓存"当成"跨请求缓存"——它只在同一个 `SqlSession` 里有效，Web 请求间不共享。
 * ============================================================
 */

package com.heima.ch23;

import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class CacheDemo {
    public static void main(String[] args) {
        System.out.println("=== 一级缓存（同一 SqlSession）===");
        SqlSession session = new SqlSession();
        System.out.println("第 1 次查：" + session.select("SELECT * FROM student WHERE id = 1"));
        System.out.println("第 2 次查：" + session.select("SELECT * FROM student WHERE id = 1"));
        System.out.println("命中缓存，未访问数据库");
        System.out.println("第 3 次查（不同参数）：" + session.select("SELECT * FROM student WHERE id = 2"));
        System.out.println();

        System.out.println("=== 执行 update 后一级缓存被清空 ===");
        session.update("UPDATE student SET name = '张三丰' WHERE id = 1");
        System.out.println("再查：" + session.select("SELECT * FROM student WHERE id = 1"));
        System.out.println("-> 缓存已失效，重新查库");
        System.out.println();

        System.out.println("=== commit 也会清空一级缓存 ===");
        session.select("SELECT * FROM student WHERE id = 1");
        session.commit();
        session.select("SELECT * FROM student WHERE id = 1");
        System.out.println();

        System.out.println("=== 二级缓存（跨 SqlSession，namespace 级）===");
        SecondLevelCache cache = new SecondLevelCache();
        SqlSession s1 = new SqlSession(cache);
        SqlSession s2 = new SqlSession(cache);
        System.out.println("s1 查询：" + s1.select("SELECT * FROM student WHERE id = 1"));
        System.out.println("s1 关闭（一级缓存销毁，数据写入二级缓存）");
        s1.close();
        System.out.println("s2 查询：" + s2.select("SELECT * FROM student WHERE id = 1"));
        System.out.println("-> 命中二级缓存，未访问数据库");
        System.out.println();

        System.out.println("=== 二级缓存的坑 ===");
        System.out.println("namespace=StudentMapper 更新了 student 表，但 namespace=ScoreMapper");
        System.out.println("的缓存不会失效，可能读到旧的关联数据（脏读）");
        System.out.println("-> 多表操作建议关闭二级缓存，改用 Redis 统一管理");
    }

    static class SqlSession implements AutoCloseable {
        private final Map<String, String> level1 = new HashMap<>();
        private final SecondLevelCache level2;
        private int dbHits = 0;

        SqlSession() {
            this(null);
        }

        SqlSession(SecondLevelCache level2) {
            this.level2 = level2;
        }

        String select(String sql) {
            if (level1.containsKey(sql)) {
                return level1.get(sql) + "   [一级缓存]";
            }
            if (level2 != null && level2.has(sql)) {
                String v = level2.get(sql);
                level1.put(sql, v);
                return v + "   [二级缓存]";
            }
            dbHits++;
            String value = "结果集#" + dbHits;
            level1.put(sql, value);
            return value + "   [查询数据库]";
        }

        void update(String sql) {
            dbHits++;
            System.out.println("  [SQL] " + sql + "   -> 清空一级缓存");
            level1.clear();
            if (level2 != null) {
                level2.clear();
            }
        }

        void commit() {
            System.out.println("  [事务] commit -> 清空一级缓存");
            level1.clear();
        }

        @Override
        public void close() {
            if (level2 != null) {
                level2.putAll(level1);
            }
            level1.clear();
        }
    }

    static class SecondLevelCache {
        private final Map<String, String> data = new HashMap<>();

        boolean has(String sql) {
            return data.containsKey(sql);
        }

        String get(String sql) {
            return data.get(sql);
        }

        void putAll(Map<String, String> m) {
            data.putAll(m);
        }

        void clear() {
            data.clear();
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一段代码验证"同一个 `SqlSession` 内两次相同查询只发一次 SQL"。
 * 2. 列出让一级缓存失效的四种操作，并各自举例。
 * 3. 说明二级缓存为什么在多表场景下容易产生脏数据，给出你的规避方案。
 * ============================================================
 */
