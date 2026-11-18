/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：12-10
 * 课名：ArrayList练习
 * ------------------------------------------------------------
 * 知识点：
 *   - 自定义类型存集合四步走：定义 JavaBean → 创建对象 → add 存入集合 → get 取出使用。
 *   - 集合里存的是对象的地址值；直接打印对象名得到的是"类名@地址值"，需要自己拼接属性来展示（后续会学重写 toString）。
 *   - 查找元素的套路：遍历逐个比较，找到返回索引，约定俗成找不到返回 -1。
 *   - 按条件删除的套路：倒序遍历 + 条件判断 + remove，避免漏删。
 *   - 做集合题先想清楚"存什么（类）、怎么存（集合）、怎么处理（遍历逻辑）"，再动手写代码。
 * ============================================================
 */

package com.heima.ch12;

import java.util.ArrayList;

// ==================== 示例代码 ====================

class ArrayListPracticeStudent {
    private String name;
    private int score;

    public ArrayListPracticeStudent() {
    }

    public ArrayListPracticeStudent(String name, int score) {
        this.name = name;
        this.score = score;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }
}

public class ArrayListPractice {
    public static void main(String[] args) {
        // 练习1：存学生对象并遍历
        ArrayList<ArrayListPracticeStudent> list = new ArrayList<>();
        list.add(new ArrayListPracticeStudent("张三", 85));
        list.add(new ArrayListPracticeStudent("李四", 59));
        list.add(new ArrayListPracticeStudent("王五", 92));
        list.add(new ArrayListPracticeStudent("赵六", 47));

        for (int i = 0; i < list.size(); i++) {
            ArrayListPracticeStudent stu = list.get(i);
            System.out.println(stu.getName() + " 的成绩是 " + stu.getScore());
        }

        // 练习2：查找集合中的元素，按姓名查找并返回索引
        System.out.println("王五的索引：" + findByName(list, "王五")); // 2

        // 练习3：删除所有不及格（分数低于 60）的学生，倒序删除避免漏删
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).getScore() < 60) {
                list.remove(i);
            }
        }
        System.out.println("删除不及格学生后：");
        for (ArrayListPracticeStudent stu : list) {
            System.out.println(stu.getName() + " " + stu.getScore());
        }
    }

    // 按姓名查找，找到返回索引，找不到返回 -1
    public static int findByName(ArrayList<ArrayListPracticeStudent> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义 Student 类（姓名、年龄），存 3 个学生对象到集合，键盘录入一个年龄，删除集合中所有该年龄的学生并输出剩余学生。
 * 2. 求集合中分数最高的学生对象并输出其全部信息；集合为空时给出友好提示。
 * 3. 定义 Book 类（书名、价格），创建集合存 5 本书，把价格高于 50 元的书筛选到新集合并输出。
 * ============================================================
 */
