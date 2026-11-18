/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 13-01
 * 课名：控制台图书管理系统（综合项目）
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现图书管理系统：① 用户注册/登录（用户名唯一、密码 6 位以上、5 位验证码）；② 图书管理（添加、查询、修改库存、删除）；③ 借阅/归还（库存校验、借阅记录）；④ 菜单循环；⑤ 统计（在借数量、库存不足提醒）。要求至少 4 个类（`Book`、`User`、`BookService`、`Main`），使用面向对象 + 集合 + 字符串处理，方法职责单一。
 *   - 【考核点】类与对象、封装、集合、字符串、循环与分支、简单分层（实体类/业务类/入口类）、数据一致性。
 *   - 【易错点】借阅要先判断库存再扣减；删除图书前要检查是否在借；验证码比较忽略大小写。
 *   - 【提示】`BookService` 只负责业务，`Main` 只负责菜单与输入输出，避免所有代码堆在 main 中。
 * ============================================================
 */

package com.heima.practice.comprehensive;

import java.util.ArrayList;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：实体类 + 业务类 + 入口类的分层骨架
class LibraryManagementSystemBook {
    private String id;
    private String name;
    private int stock;

    public LibraryManagementSystemBook(String id, String name, int stock) {
        this.id = id;
        this.name = name;
        this.stock = stock;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock;
    }
}

class LibraryManagementSystemBookService {
    private ArrayList<LibraryManagementSystemBook> books = new ArrayList<>();

    public void add(LibraryManagementSystemBook book) {
        books.add(book);
    }

    public void showAll() {
        for (LibraryManagementSystemBook b : books) {
            System.out.println(b.getName() + " 库存：" + b.getStock());
        }
    }
}

public class LibraryManagementSystem {
    public static void main(String[] args) {
        LibraryManagementSystemBookService service = new LibraryManagementSystemBookService();
        service.add(new LibraryManagementSystemBook("B001", "Java 入门", 5));
        service.showAll();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 增加借阅记录类 `BorrowRecord`（用户、图书、借出时间戳），输出指定用户的借阅历史。
 * 2. 增加权限控制：管理员可增删图书，普通用户只能查询与借阅（用 `isAdmin` 字段判断）。
 * 3. 增加库存不足自动提醒：遍历全部图书，库存 ≤ 2 时输出提醒列表，并统计种类数。
 * 
 * 
 * ## 第14章 常用类API
 * ============================================================
 */
