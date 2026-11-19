/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-01
 * 课名：认证与授权全景
 * ------------------------------------------------------------
 * 知识点：
 *   - **认证（Authentication）回答"你是谁"，授权（Authorization）回答"你能做什么"**——两者必须分开设计：认证由登录/令牌完成，授权由权限模型完成；**混在一起会导致"登录即全权"的经典漏洞**（36-05 越权）。
 *   - **认证的四种方式**：**会话 Cookie**（服务端状态，适合 Web）、**Token（JWT）**（无状态，适合 API/跨域）、**OAuth2 授权**（第三方代授权，64-02）、**SSO 单点登录**（一次登录访问多系统，74-02）——**选型看场景**（B 端多系统用 SSO，开放平台用 OAuth2）。
 *   - **授权的三层模型**：**功能权限**（能不能调这个接口/看这个菜单）、**数据权限**（能看哪些行 74-05）、**字段权限**（能看哪些字段 68-05）——**企业系统三层都要**。
 *   - **与 68 章的关系**：68 章讲"数据隐私的访问控制"（数据视角），本章讲"企业权限体系的组织方式"（系统视角）——**同一套控制，不同抽象层**。
 *   - **权限系统的演进路径**：硬编码角色 -> 角色表 + 权限表（RBAC）-> 支持数据范围（ABAC 雏形）-> 统一权限中台（跨系统统一）——**不要一步到位，按痛点走**（49-10 演进纪律）。
 *   - 易错点：认证成功就认为有权限（缺少授权检查）；只做菜单权限不做接口权限（直接调 API）；权限判断散落各处（无统一入口）；管理员权限无限大（无审计 74-10）。
 * ============================================================
 */

package com.heima.ch74;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AuthOverview {
    record AuthMethod(String name, String state, String fit, String risk) {
    }

    static void methods() {
        System.out.println("【四种认证方式】");
        List<AuthMethod> methods = List.of(
                new AuthMethod("Session Cookie", "服务端存会话", "传统 Web 后台", "集群需共享会话"),
                new AuthMethod("JWT Token", "无状态", "API / 移动端 / 跨域", "无法主动失效（需黑名单）"),
                new AuthMethod("OAuth2", "授权码/客户端凭证", "第三方接入", "scope 配置不当（64-02）"),
                new AuthMethod("SSO", "集中认证", "企业多系统", "中心化单点风险（74-02）"));
        for (AuthMethod m : methods) {
            System.out.printf("  %-16s 状态=%-12s 适合=%-20s 风险=%s%n",
                    m.name(), m.state(), m.fit(), m.risk());
        }
        System.out.println();
    }

    static void authnVsAuthz() {
        System.out.println("【认证 vs 授权】");
        String[][] rows = {
                {"回答", "你是谁", "你能做什么"},
                {"载体", "账号/令牌/证书", "角色/权限/数据范围"},
                {"失败", "401 未登录", "403 无权限"},
                {"常见漏洞", "弱密码/令牌泄露", "越权/IDOR（36-05）"},
                {"本课程", "74-02 SSO、64-02 OAuth2", "74-04 RBAC、74-05 ABAC、68-05 数据权限"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s 认证=%-28s 授权=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void threeLayers() {
        System.out.println("【授权的三层】");
        Map<String, String> layers = new LinkedHashMap<>();
        layers.put("功能权限", "接口/菜单/按钮：能不能调这个 API");
        layers.put("数据权限", "行级：能看哪些数据（本部门/本人）");
        layers.put("字段权限", "列级：能看哪些字段（脱敏 68-03）");
        layers.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.println();
        System.out.println("  反模式：只做菜单权限 -> 用户直接调 API 绕过（前端隐藏不可信）");
    }

    public static void main(String[] args) {
        methods();
        authnVsAuthz();
        threeLayers();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的系统列出"认证方式 + 授权层数"，标注现有缺口（如只有菜单无接口权限）。
 * 2. 对比 Session 与 JWT 在"集群部署/主动失效/跨域"三个场景下的取舍。
 * 3. 思考题：为什么"认证成功不代表有权限"？举一个"登录普通用户直接调管理接口"的漏洞路径。
 * ============================================================
 */
