/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 12-03
 * 课名：通讯录管理（ArrayList CRUD）
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】设计 `Contact`（name、phone），`ContactManager` 用 `ArrayList<Contact>` 实现：添加、按姓名删除、按姓名查找、修改电话、显示全部、按姓名排序。用菜单循环驱动，所有操作给出成功/失败提示。
 *   - 【考核点】ArrayList 增删改查、泛型、对象集合、遍历、删除时的下标陷阱、排序。
 *   - 【易错点】正序遍历删除会漏删，应倒序遍历或使用迭代器；按姓名查找可能重名，要明确返回第一条还是全部。
 *   - 【提示】排序可用 `Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()))`。
 * ============================================================
 */

package com.heima.practice.comprehensive;

import java.util.ArrayList;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：ArrayList 的增删遍历
public class ContactManager {
    public static void main(String[] args) {
        ArrayList<String> names = new ArrayList<>();
        names.add("张三");
        names.add("李四");
        names.add("王五");
        names.remove("李四");
        for (String name : names) {
            System.out.println(name);
        }
        System.out.println("大小：" + names.size());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 增加“按姓名模糊查找”（contains），输出所有匹配的联系人。
 * 2. 实现“删除所有姓张的联系人”，用倒序遍历避免漏删，并说明原因。
 * 3. 统计通讯录中每个姓氏（姓名字符串第一个字）的人数，按人数从高到低输出。
 * 
 * 
 * ## 第13章 阶段项目实战
 * ============================================================
 */
