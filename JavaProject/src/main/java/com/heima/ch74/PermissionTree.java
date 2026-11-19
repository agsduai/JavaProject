/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-06
 * 课名：权限树与菜单渲染
 * ------------------------------------------------------------
 * 知识点：
 *   - **菜单/按钮是权限的"展示形态"**：前端需要一棵**权限树**（目录 -> 菜单 -> 按钮）来渲染侧边栏与按钮显隐——**后端返回"用户可见的权限树"**，前端按树渲染。
 *   - **权限树的两种构建方式**：**后端构树**（返回嵌套 JSON，前端直接渲染，推荐）、**前端构树**（返回扁平列表，前端按 parentId 组装）——**后端构树减少前端逻辑**。
 *   - **菜单与权限码的绑定**：菜单节点带 `permissionCode`（如 `order` 目录、`order:read` 菜单、`order:refund` 按钮）——**渲染时按权限码过滤**（无权限的节点不返回或标记不可见）。
 *   - **与 71-04 的关系**：低代码平台的"元数据权限"也是同一套（字段/操作/菜单）——**权限树是通用形态**。
 *   - **性能**：权限树每次登录算一次（缓存 74-07）、**节点数控制在几百以内**（菜单不是无限嵌套）、**前端路由与权限树对齐**（避免"有菜单无路由"）。
 *   - 易错点：返回全量菜单前端隐藏（用户能看到无权菜单的结构 36-05）；权限树与路由不同步（点菜单 404）；树无排序（菜单乱序）；递归构树有环（数据错误导致栈溢出）。
 * ============================================================
 */

package com.heima.ch74;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class PermissionTree {
    record Node(long id, Long parentId, String name, String type, String permissionCode,
                int sort) {
    }

    static final List<Node> allNodes = new ArrayList<>();

    static void seed() {
        allNodes.add(new Node(1, null, "订单中心", "DIR", null, 1));
        allNodes.add(new Node(2, 1L, "订单列表", "MENU", "order:read", 1));
        allNodes.add(new Node(3, 1L, "退款管理", "MENU", "order:refund", 2));
        allNodes.add(new Node(4, 2L, "导出按钮", "BUTTON", "order:export", 1));
        allNodes.add(new Node(5, null, "用户中心", "DIR", null, 2));
        allNodes.add(new Node(6, 5L, "用户列表", "MENU", "user:read", 1));
        allNodes.add(new Node(7, 6L, "删除按钮", "BUTTON", "user:delete", 1));
    }

    /** 按权限过滤：无权限码的目录保留（若其下有可见子节点） */
    static List<Node> filterByPerms(Set<String> perms) {
        List<Node> visible = new ArrayList<>();
        for (Node n : allNodes) {
            boolean ok = n.permissionCode() == null || perms.contains(n.permissionCode());
            if (ok) {
                visible.add(n);
            }
        }
        // 自底向上：若目录下没有可见子节点，目录也去掉
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Node> next = new ArrayList<>();
            for (Node n : visible) {
                boolean hasChild = visible.stream().anyMatch(c ->
                        c.parentId() != null && c.parentId() == n.id());
                boolean isLeaf = !visible.stream().anyMatch(c ->
                        c.parentId() != null && c.parentId() == n.id());
                if (n.type().equals("DIR") && isLeaf) {
                    changed = true;
                    continue;
                }
                next.add(n);
            }
            visible = next;
        }
        return visible;
    }

    /** 构树：递归 + 排序 */
    static List<String> buildTree(List<Node> nodes, Long parentId, int depth) {
        List<String> lines = new ArrayList<>();
        List<Node> children = new ArrayList<>();
        for (Node n : nodes) {
            if (parentId == null ? n.parentId() == null : n.parentId() != null
                    && n.parentId().equals(parentId)) {
                children.add(n);
            }
        }
        children.sort((a, b) -> Integer.compare(a.sort(), b.sort()));
        for (Node n : children) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                sb.append("  ");
            }
            sb.append(n.type().equals("BUTTON") ? "- " : "▸ ")
                    .append(n.name()).append(" [").append(n.type()).append("]");
            if (n.permissionCode() != null) {
                sb.append(" (").append(n.permissionCode()).append(")");
            }
            lines.add(sb.toString());
            lines.addAll(buildTree(nodes, n.id(), depth + 1));
        }
        return lines;
    }

    public static void main(String[] args) {
        seed();
        Map<String, Set<String>> rolePerms = new LinkedHashMap<>();
        rolePerms.put("客服（order:read + user:read）", Set.of("order:read", "user:read"));
        rolePerms.put("客服主管（+refund/export）",
                Set.of("order:read", "order:refund", "order:export", "user:read"));
        rolePerms.put("管理员（全部）",
                Set.of("order:read", "order:refund", "order:export",
                        "user:read", "user:delete"));

        rolePerms.forEach((role, perms) -> {
            System.out.println("【" + role + "】");
            List<Node> visible = filterByPerms(perms);
            buildTree(visible, null, 1).forEach(l -> System.out.println("    " + l));
            System.out.println();
        });

        System.out.println("【要点】");
        System.out.println("  1) 返回「过滤后的树」，而不是全量菜单 + 前端隐藏（36-05）");
        System.out.println("  2) 空的目录节点要自底向上剪掉（否则侧边栏有空洞）");
        System.out.println("  3) 菜单节点与前端路由表对齐（避免点开 404）");
        System.out.println("  4) 权限树每次登录算一次 + 缓存（74-07）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现权限树过滤与构树：3 个角色各输出可见树，验证"空目录被剪掉"。
 * 2. 实现"前端路由对齐检查"：比对权限树的菜单码与前端路由表，输出"有菜单无路由"的清单。
 * 3. 思考题：为什么"返回全量菜单 + 前端隐藏"是安全问题？从"菜单结构泄露"与"直接调 API"分析。
 * ============================================================
 */
