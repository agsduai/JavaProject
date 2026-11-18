/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：第15-36章综合卷 卷二
 * 课名：数据与框架综合（MySQL + JDBC + MyBatis + Spring Boot）
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现「用户中心迷你栈」：① SQL 层——建表（用户/地址两表）与三条典型 SQL（join/分页/聚合），附 EXPLAIN 说明；② DAO 层——JdbcTemplate + RowMapper + 参数绑定审计（21 章模板）；③ Mapper 层——动态代理 Mapper（23 章）配动态 SQL（条件查询）；④ 应用层——校验注解 + 统一异常 + 自动配置模拟（24 章）。验收：SQL 全参数化、三层边界清晰、异常与校验响应规范。
 *   - 【考核点】SQL 语义与执行计划、模板与映射分层、动态 SQL、校验与统一异常、层间依赖方向。
 *   - 【易错点】层间穿透（Controller 直调 JDBC）；动态 SQL 首条件 AND；校验错误逐个返回（应聚合）。
 *   - 【提示】用内存表模拟数据源（46-08 同款），把四层真实串起来——重点考核分层纪律而非真实 DB 连接。
 * ============================================================
 */

package com.heima.practice.midexam;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class MidStackExam {
    /** 数据层：只管存取 */
    static class UserDao {
        List<String> table = new ArrayList<>();

        void insert(String name) {
            table.add(name);
        }

        List<String> findByName(String name) {
            return table.stream().filter(n -> n.equals(name)).toList();
        }
    }

    /** 应用层：编排 + 校验，不写 SQL */
    static class UserService {
        final UserDao dao;

        UserService(UserDao dao) {
            this.dao = dao;
        }

        List<String> register(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name 不能为空");
            }
            dao.insert(name);
            return dao.findByName(name);
        }
    }

    public static void main(String[] args) {
        UserService svc = new UserService(new UserDao());
        System.out.println("  注册结果：" + svc.register("alice"));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整实现四层迷你栈（含两条表的 SQL 设计与 EXPLAIN 说明），演示用户注册→查询→聚合全流程。
 * 2. 故意"层间穿透"一次（Controller 拼 SQL），对照评审清单（49-11 六问）说明违反了哪条边界。
 * 3. 为本卷全部决策写 ADR（52-02）：存储、DAO 模板、校验策略、异常规范。
 * ============================================================
 */
