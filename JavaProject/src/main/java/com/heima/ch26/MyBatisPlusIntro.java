/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-05
 * 课名：MyBatis-Plus：消灭样板 CRUD
 * ------------------------------------------------------------
 * 知识点：
 *   - MyBatis 的痛点：每张表都要写 `insert`、`selectById`、`updateById`、`deleteById`、`selectList`，10 张表就是 50 个方法 + 50 段 SQL，且大同小异。
 *   - MyBatis-Plus（MP）在 MyBatis 上做增强，**只做增强不做改变**：原有 MyBatis 用法全部保留，MP 额外提供通用 CRUD。
 *   - 核心接口 `BaseMapper<T>`：继承它就白拿 17 个方法，`insert`、`deleteById`、`updateById`、`selectById`、`selectList`、`selectPage`、`selectCount` 等，泛型 T 就是实体类。
 *   - 三个关键注解：
 *   - `@TableName("t_user")`：实体类对应的表名（类名与表名不一致时必须写）。
 *   - `@TableId(type = IdType.AUTO)`：主键字段，`AUTO` 数据库自增、`ASSIGN_ID` 雪花算法、`ASSIGN_UUID`。
 *   - `@TableField("user_name")`：字段名与列名不一致时映射；`@TableField(exist = false)` 表示该字段不是表字段。
 *   - 自动填充：`@TableField(fill = FieldFill.INSERT)` 配合 `MetaObjectHandler` 自动写 `createTime`、`updateTime`、`createBy`。
 *   - 逻辑删除：`@TableLogic` 标注的字段，`deleteById` 实际执行 `UPDATE ... SET deleted = 1`，查询自动加 `deleted = 0`。
 *   - 配置：`mybatis-plus.mapper-locations`、`mybatis-plus.configuration.map-underscore-to-camel-case=true`、`mybatis-plus.global-config.db-config.id-type=auto`。
 *   - 易错：实体类没写 `@TableName` 且类名与表名不一致，报 `Table 'xxx' doesn't exist`；`@TableId` 漏写导致主键策略用默认值；继承 `BaseMapper` 后忘了在启动类加 `@MapperScan`。
 * ============================================================
 */

package com.heima.ch26;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MyBatisPlusIntro {
    public static void main(String[] args) {
        System.out.println("=== 继承 BaseMapper 就有的方法 ===");
        String[] methods = {
                "insert(T entity)", "deleteById(Serializable id)", "deleteByMap(Map)",
                "delete(Wrapper)", "deleteBatchIds(Collection)",
                "updateById(T entity)", "update(T entity, Wrapper)",
                "selectById(Serializable id)", "selectBatchIds(Collection)",
                "selectByMap(Map)", "selectOne(Wrapper)", "selectCount(Wrapper)",
                "selectList(Wrapper)", "selectMaps(Wrapper)", "selectPage(Page, Wrapper)"};
        for (String m : methods) {
            System.out.println("  " + m);
        }

        System.out.println();
        System.out.println("=== 实体类 ===");
        System.out.println("""
                @TableName("t_user")
                public class User {
                    @TableId(type = IdType.AUTO)
                    private Long id;

                    @TableField("user_name")
                    private String userName;

                    private Integer age;

                    @TableField(fill = FieldFill.INSERT)
                    private String createTime;

                    @TableLogic
                    private Integer deleted;
                }
                """);

        System.out.println("=== 用 BaseMapper 完成增删改查（纯 JDK 模拟）===");
        UserMapper mapper = new UserMapper();
        User u1 = new User();
        u1.userName = "张三";
        u1.age = 18;
        mapper.insert(u1);

        User u2 = new User();
        u2.userName = "李四";
        u2.age = 20;
        mapper.insert(u2);

        System.out.println("  insert 两条后，表内容：");
        mapper.selectList().forEach(u -> System.out.println("    " + u));

        System.out.println();
        System.out.println("  selectById(1) -> " + mapper.selectById(1L));

        u1.age = 19;
        mapper.updateById(u1);
        System.out.println("  updateById 后 selectById(1) -> " + mapper.selectById(1L));

        mapper.deleteById(2L);
        System.out.println("  deleteById(2) 后剩余 " + mapper.selectCount() + " 条（逻辑删除，行还在，deleted=1）");
        System.out.println("  底层 SQL：UPDATE t_user SET deleted = 1 WHERE id = 2 AND deleted = 0");
    }

    /** 模拟实体 */
    static class User {
        Long id;
        String userName;
        Integer age;
        String createTime;
        Integer deleted = 0;

        @Override
        public String toString() {
            return "User{id=" + id + ", userName='" + userName + "', age=" + age
                    + ", deleted=" + deleted + "}";
        }
    }

    /** 模拟 BaseMapper<User>：通用 CRUD 由反射按字段名自动生成 */
    static class UserMapper {
        private final Map<Long, User> table = new LinkedHashMap<>();
        private long seq = 0;

        int insert(User entity) {
            entity.id = ++seq;                       // IdType.AUTO
            entity.createTime = "2026-11-17 10:00:00";  // MetaObjectHandler 自动填充
            entity.deleted = 0;
            table.put(entity.id, entity);
            return 1;
        }

        User selectById(Long id) {
            User u = table.get(id);
            return u != null && u.deleted == 0 ? u : null;   // @TableLogic 自动过滤
        }

        int updateById(User entity) {
            User old = table.get(entity.id);
            if (old == null || old.deleted != 0) {
                return 0;
            }
            copyNonNull(entity, old);                // MP 默认只更新非 null 字段
            return 1;
        }

        int deleteById(Long id) {
            User u = table.get(id);
            if (u == null || u.deleted != 0) {
                return 0;
            }
            u.deleted = 1;                           // 逻辑删除
            return 1;
        }

        List<User> selectList() {
            List<User> res = new ArrayList<>();
            for (User u : table.values()) {
                if (u.deleted == 0) {
                    res.add(u);
                }
            }
            return res;
        }

        long selectCount() {
            return selectList().size();
        }

        /** 反射拷贝非 null 字段，等价于 MP 的 updateById 语义 */
        static void copyNonNull(Object src, Object dst) {
            for (Field f : src.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                try {
                    Object v = f.get(src);
                    if (v != null) {
                        f.set(dst, v);
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写出一个 `Order` 实体类，包含 `id`、`orderNo`、`userId`、`amount`、`createTime`、`deleted`，标全 MP 需要的注解。
 * 2. `IdType.AUTO`、`ASSIGN_ID`、`ASSIGN_UUID` 分别适合什么场景？分库分表时为什么不能用 `AUTO`？
 * 3. 逻辑删除开启后，`selectById` 和 `deleteById` 生成的 SQL 分别是什么？如何查询已删除的数据？
 * ============================================================
 */
