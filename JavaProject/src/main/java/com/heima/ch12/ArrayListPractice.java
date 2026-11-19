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

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 做一个控制台通讯录：用 ArrayList 存联系人，支持增删改查与按姓名排序；再做一轮文本处理，统计敏感词出现次数并把手机号中间四位打码。String 与 ArrayList 的常用 API 要全部覆盖。
 * 【任务】
 * 1. 定义 Contact 类（name、phone、group），用 ArrayList<Contact> 实现增删改查，删除时按下标与按对象两种方式各写一遍。
 * 2. 实现按姓名排序（String 的 compareTo），并说明为什么不直接用 == 比较字符串。
 * 3. 实现敏感词统计：给定一段文本与词表，用 indexOf 循环计数，输出每个词的出现次数。
 * 4. 实现手机号打码：11 位手机号保留前 3 后 4，中间用 **** 替换，用 StringBuilder 拼接；长度非 11 位时原样返回。
 * 【验收清单】
 * [ ] 增删改查四个操作各有输出验证，删除不存在的元素有提示且不报错。
 * [ ] 排序后姓名按字典序升序，中文姓名也能稳定排序。
 * [ ] 敏感词计数与人工核对一致，重叠出现的情况有说明。
 * [ ] 打码方法对空串、短串、超长串都不抛异常。
 * ============================================================
 */
