/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 23-01
 * 课名：Mapper 动态代理模拟
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】用 JDK 动态代理模拟 MyBatis Mapper：定义 UserMapper 接口（findById/update/insert）；代理 InvocationHandler 解析方法名与参数 -> 映射到"SQL 模板注册表" -> 执行模拟查询 -> 结果经 RowMapper 转对象返回；接口无需实现类（MyBatis 的核心魔法拆解）；演示未注册 SQL 时的报错。
 *   - 【考核点】动态代理（31 章）、接口到 SQL 的映射机制、参数绑定、代理层的职责（MyBatis 只做映射，执行交 JDBC）。
 *   - 【易错点】以为 Mapper 有实现类（运行时代理生成）；方法名拼写错在编译期不报（运行期才报 SQL 未注册）；把业务逻辑写进 Mapper（应只有数据访问）。
 *   - 【提示】SQL 注册表 key = 接口全名.方法名；invoke 里按 key 取模板执行。
 * ============================================================
 */

package com.heima.practice.framework;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// ==================== 示例代码 ====================

public class MapperProxyTask {
    interface UserDao {
        String findById(long id);
    }

    public static void main(String[] args) {
        UserDao dao = (UserDao) Proxy.newProxyInstance(
                UserDao.class.getClassLoader(), new Class<?>[]{UserDao.class},
                (InvocationHandler) (proxy, method, args1) -> {
                    System.out.println("  代理拦截：" + method.getName() + " 参数=" + args1[0]);
                    return "user-" + args1[0];
                });
        System.out.println("  调用结果：" + dao.findById(1001));
        System.out.println("  接口没有实现类 —— 代理在运行时生成行为");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 Mapper 代理（SQL 注册表 + 参数绑定 + 结果映射 + 未注册报错），跑三个接口方法。
 * 2. 支持注解 SQL（@Select("...")），代理读取方法注解而不是注册表。
 * 3. 思考题：为什么 MyBatis 选择"接口 + 动态代理"而不是"XML 生成实现类"？从开发体验与运行时灵活性对比。
 * ============================================================
 */
