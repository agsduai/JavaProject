/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-09
 * 课名：MyBatis：SqlSession 与执行器
 * ------------------------------------------------------------
 * 知识点：
 *   - **MyBatis 的三层结构**：**SqlSessionFactory**（全局单例，持有 Configuration）-> **SqlSession**（一次会话/一个事务，**非线程安全**）-> **Executor**（真正执行 SQL，含缓存与批处理）。**Mapper 接口只是 SqlSession 的代理入口**（23-01 的代理在此得到源码层解释）。
 *   - **Executor 的三种类型**：`SIMPLE`（默认，每次新建 Statement）、`REUSE`（复用 Statement）、`BATCH`（批量执行，**事务提交时统一 flush**）——**批量插入必须用 BATCH**（否则一条一条发，21-02 的性能教训在 ORM 层）。
 *   - **一级缓存 vs 二级缓存**：一级缓存（SqlSession 级，**默认开启**，同一会话内相同查询复用）——**在事务/会话内数据可能被其他会话改掉而缓存不知**（脏读风险）；二级缓存（Mapper/namespace 级，**需显式开启**，跨会话共享）——**多表关联/写操作时容易不一致**，生产普遍关闭。
 *   - **参数绑定与 `#{}` vs `${}`**：`#{}` 走 `PreparedStatement` 占位符（**防注入**，36-02 的源码层印证）；`${}` 是字符串替换（**注入风险**）——**`${}` 只允许用于表名/列名等标识符，且必须白名单**（39-02 的标识符纪律）。
 *   - **插件（Interceptor）机制**：MyBatis 用动态代理拦截 `Executor/StatementHandler/ParameterHandler/ResultSetHandler` 四大组件——**分页插件、SQL 审计、多租户注入（55-03）都靠它**；**插件链的顺序决定行为**（多个插件叠加时注意）。
 *   - 易错点：SqlSession 跨线程复用（线程不安全）；用 `${}` 拼用户输入（注入）；BATCH 模式忘 flush（数据没写进去）；二级缓存开着又频繁跨表写（读到旧数据）。
 * ============================================================
 */

package com.heima.ch60;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MybatisDemo {
    interface UserMapper {
        String findById(long id);

        int insert(String name);
    }

    /** 模拟 SqlSession + Executor + 一级缓存 */
    static class MiniSqlSession {
        final Map<String, String> database = new LinkedHashMap<>();
        final Map<String, String> localCache = new LinkedHashMap<>();    // 一级缓存
        final List<String> auditLog = new ArrayList<>();
        boolean batchMode;

        MiniSqlSession(boolean batchMode) {
            this.batchMode = batchMode;
            database.put("user:1", "alice");
            database.put("user:2", "bob");
        }

        String select(String key) {
            if (localCache.containsKey(key)) {
                auditLog.add("CACHE HIT " + key);
                return localCache.get(key);
            }
            auditLog.add("SQL SELECT " + key);
            String value = database.get(key);
            if (value != null) {
                localCache.put(key, value);
            }
            return value;
        }

        int insert(String key, String value) {
            auditLog.add(batchMode ? "BATCH ADD " + key : "SQL INSERT " + key);
            database.put(key, value);
            localCache.clear();                       // 写操作清空一级缓存
            return 1;
        }

        /** Mapper 代理：接口方法 -> SqlSession 调用（23-01 的源码层解释） */
        @SuppressWarnings("unchecked")
        <T> T getMapper(Class<T> mapperType) {
            return (T) Proxy.newProxyInstance(mapperType.getClassLoader(),
                    new Class<?>[]{mapperType},
                    (InvocationHandler) (proxy, method, args) -> {
                        if (method.getName().equals("findById")) {
                            return select("user:" + args[0]);
                        }
                        if (method.getName().equals("insert")) {
                            return insert("user:" + (database.size() + 1), (String) args[0]);
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    public static void main(String[] args) {
        System.out.println("【Mapper 代理 + 一级缓存】");
        var session = new MiniSqlSession(false);
        UserMapper mapper = session.getMapper(UserMapper.class);
        System.out.println("  第 1 次查询：" + mapper.findById(1));
        System.out.println("  第 2 次查询：" + mapper.findById(1) + "（命中一级缓存）");
        mapper.insert("carol");
        System.out.println("  写后查询 1：" + mapper.findById(1) + "（缓存已清，重新查）");
        System.out.println("  审计日志：" + session.auditLog);
        System.out.println();

        System.out.println("【BATCH 模式：延迟执行】");
        var batchSession = new MiniSqlSession(true);
        UserMapper batchMapper = batchSession.getMapper(UserMapper.class);
        for (int i = 0; i < 3; i++) {
            batchMapper.insert("user-" + i);
        }
        System.out.println("  审计日志：" + batchSession.auditLog);
        System.out.println("  注意：BATCH 模式需事务提交时才真正 flush（未提交可能一条没写）");
        System.out.println();

        System.out.println("【#{} 与 ${} 的区别】");
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("#{id}", "PreparedStatement 占位符 -> 值永不进 SQL 结构（防注入）");
        modes.put("${id}", "字符串替换 -> 可注入！仅限白名单标识符（表名/列名）");
        modes.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展 MiniSqlSession：实现 `REUSE` 与 `BATCH` 两种 Executor 的执行日志差异，量化批量写入的"语句发送次数"。
 * 2. 实现 MyBatis 插件模拟：写一个拦截器在 `select` 前后打印耗时与 SQL 次数，统计一个业务流程的 N+1 查询。
 * 3. 思考题：为什么一级缓存在"长事务"里容易读到旧数据？从"缓存生命周期 = SqlSession 生命周期"与其余会话的写入分析。
 * ============================================================
 */
