/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-11
 * 课名：综合案例：用户模块
 * ------------------------------------------------------------
 * 知识点：
 *   - 模块划分：`UserController`（参数接收与响应）-> `UserService`（业务规则、密码加密、token 签发）-> `UserMapper`（数据访问）。Controller 不写业务，Service 不碰 HTTP 对象。
 *   - 接口设计：
 *   - `POST /api/user/register` 注册：用户名唯一、密码强度、返回用户 id。
 *   - `POST /api/user/login` 登录：返回 token + 基础信息（不含密码）。
 *   - `GET /api/user/profile` 查自己：从 token 取 userId，不接受前端传 id（否则就是越权漏洞）。
 *   - `PUT /api/user/profile` 改资料：只允许改昵称、头像等白名单字段。
 *   - 统一响应：`Result<T>{code, msg, data}`，业务异常抛 `BusinessException`，由全局异常处理器转成对应 code。
 *   - 越权防护：所有"查/改自己的数据"的接口，userId 一律从 token 里取，绝不能信任前端传参（水平越权最常见的成因）。
 *   - 事务边界：注册涉及"插入用户 + 初始化账户"两步，要加 `@Transactional`；但发短信、发邮件这类外部调用要放在事务外（或事务提交后）。
 *   - 易错：返回实体直接序列化导致密码泄露；更新接口用前端传的整个对象覆盖（把 role 也改了）；唯一索引没建，并发注册出两个同名用户。
 * ============================================================
 */

package com.heima.ch26;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class UserModuleCase {
    /** 模拟数据库 */
    static final Map<Long, Map<String, Object>> USER_TABLE = new LinkedHashMap<>();
    static long seq = 0;

    public static void main(String[] args) {
        System.out.println("=== 注册 ===");
        System.out.println("  " + register("zhangsan", "Passw0rd!", "张三"));
        System.out.println("  " + register("zhangsan", "Passw0rd!", "张三"));
        System.out.println("  " + register("lisi", "123", "李四"));
        System.out.println("  " + register("lisi", "Passw0rd!", "李四"));

        System.out.println();
        System.out.println("=== 登录 ===");
        String token = null;
        Map<String, Object> loginResp = login("zhangsan", "Passw0rd!");
        System.out.println("  " + loginResp);
        token = (String) loginResp.get("token");

        System.out.println();
        System.out.println("=== 查自己的资料（userId 来自 token）===");
        System.out.println("  " + profile(token));
        System.out.println("  注意：接口不接受前端传 id，杜绝水平越权");

        System.out.println();
        System.out.println("=== 改资料：只允许白名单字段 ===");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("nickname", "张三丰");
        patch.put("avatar", "/files/a.png");
        patch.put("role", "admin");            // 恶意字段，必须被忽略
        System.out.println("  " + updateProfile(token, patch));

        System.out.println();
        System.out.println("=== 未登录访问 ===");
        System.out.println("  " + profile(null));
    }

    static Map<String, Object> register(String username, String rawPassword, String nickname) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (username == null || username.length() < 4) {
            return fail(resp, 400, "用户名至少 4 位");
        }
        if (!rawPassword.matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) {
            return fail(resp, 400, "密码至少 8 位且含字母和数字");
        }
        for (Map<String, Object> u : USER_TABLE.values()) {
            if (username.equals(u.get("username"))) {
                return fail(resp, 409, "用户名已存在");
            }
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", ++seq);
        user.put("username", username);
        user.put("password", "hashed:" + Integer.toHexString(rawPassword.hashCode()));
        user.put("nickname", nickname);
        user.put("role", "user");
        USER_TABLE.put((Long) user.get("id"), user);

        resp.put("code", 200);
        resp.put("msg", "注册成功");
        resp.put("data", Map.of("id", user.get("id")));
        return resp;
    }

    static Map<String, Object> login(String username, String rawPassword) {
        Map<String, Object> resp = new LinkedHashMap<>();
        Map<String, Object> found = null;
        for (Map<String, Object> u : USER_TABLE.values()) {
            if (username.equals(u.get("username"))) {
                found = u;
                break;
            }
        }
        String hashed = "hashed:" + Integer.toHexString(rawPassword.hashCode());
        if (found == null || !hashed.equals(found.get("password"))) {
            return fail(resp, 401, "用户名或密码错误");
        }
        resp.put("code", 200);
        resp.put("msg", "登录成功");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", "mock-jwt-" + found.get("id"));
        data.put("nickname", found.get("nickname"));
        data.put("role", found.get("role"));
        resp.put("data", data);            // 不含 password 字段
        resp.put("token", data.get("token"));
        return resp;
    }

    static Map<String, Object> profile(String token) {
        Map<String, Object> resp = new LinkedHashMap<>();
        Long userId = currentUserId(token);
        if (userId == null) {
            return fail(resp, 401, "未登录");
        }
        Map<String, Object> user = USER_TABLE.get(userId);
        Map<String, Object> data = new LinkedHashMap<>(user);
        data.remove("password");           // 出参永不带密码
        resp.put("code", 200);
        resp.put("msg", "ok");
        resp.put("data", data);
        return resp;
    }

    static Map<String, Object> updateProfile(String token, Map<String, Object> patch) {
        Map<String, Object> resp = new LinkedHashMap<>();
        Long userId = currentUserId(token);
        if (userId == null) {
            return fail(resp, 401, "未登录");
        }
        Map<String, Object> user = USER_TABLE.get(userId);
        // 白名单：只有这些字段允许改，role/password 一律忽略
        for (String field : new String[]{"nickname", "avatar"}) {
            if (patch.containsKey(field)) {
                user.put(field, patch.get(field));
            }
        }
        resp.put("code", 200);
        resp.put("msg", "更新成功");
        resp.put("data", Map.of("nickname", user.get("nickname")));
        return resp;
    }

    static Long currentUserId(String token) {
        if (token == null || !token.startsWith("mock-jwt-")) {
            return null;
        }
        try {
            return Long.parseLong(token.substring("mock-jwt-".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Map<String, Object> fail(Map<String, Object> resp, int code, String msg) {
        resp.put("code", code);
        resp.put("msg", msg);
        return resp;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把本案例的 token 换成 26-08 的 JWT 实现，并加上"token 过期返回 401、前端跳登录页"的处理。
 * 2. 为什么更新资料要用白名单字段而不是黑名单（排除 password、role）？举一个黑名单漏掉字段被提权的例子。
 * 3. 注册接口并发提交两次相同用户名，如何保证只成功一次？从数据库和代码两个层面说明。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 实现完整的用户模块：注册登录、JWT 签发与校验、拦截器解析令牌写入上下文、AOP 记录接口耗时与操作日志、自定义注解做权限校验，最后用 MyBatis-Plus 简化 CRUD。
 * 【任务】
 * 1. 用 MyBatis-Plus 的 BaseMapper 完成用户 CRUD，再用条件构造器写一个按用户名模糊 + 状态筛选 + 分页的查询。
 * 2. 实现登录签发 JWT（含 userId、过期时间、签名），写拦截器校验 token 并放入 ThreadLocal 上下文，未登录访问返回 401。
 * 3. 写 @RequireRole 注解 + AOP 切面，在方法执行前校验当前用户角色，无权限抛业务异常并返回 403。
 * 4. 写一个 @LogCost 注解 + AOP，记录每个接口的耗时与方法名，超过 500ms 打印告警。
 * 【验收清单】
 * [ ] 登录后携带 token 访问受保护接口成功，不带或带过期 token 返回 401。
 * [ ] 普通用户访问管理员接口返回 403，管理员访问成功。
 * [ ] AOP 日志能打印方法名与耗时，切面不侵入业务代码。
 * [ ] 分页查询返回总数与当前页数据，参数非法时返回明确错误。
 * ============================================================
 */
