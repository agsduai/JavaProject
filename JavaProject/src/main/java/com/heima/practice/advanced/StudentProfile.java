/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 8-01
 * 课名：学生档案类
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】设计 `Student` 类：私有属性 `id`、`name`、`age`、`score`；提供空参构造、全参构造、getter/setter。要求 age 必须在 6~30，score 必须在 0~100，非法值给出提示且不修改原值；提供 `String getLevel()` 返回优秀/良好/及格/不及格。主方法创建 3 个学生并输出全部信息与等级。
 *   - 【考核点】封装、private、构造方法、this、数据校验、JavaBean 规范、成员方法。
 *   - 【易错点】setter 校验失败应 `return`，不能先赋值再判断；全参构造也应走校验逻辑或至少保证数据合法。
 *   - 【提示】`getLevel()` 内部用 if-else 链返回字符串，不要打印。
 * ============================================================
 */

package com.heima.practice.advanced;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：封装、构造方法与 setter 校验
class StudentProfileBook {
    private String name;
    private double price;

    public StudentProfileBook() {
    }

    public StudentProfileBook(String name, double price) {
        this.name = name;
        setPrice(price);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        if (price < 0) {
            System.out.println("价格不能为负数");
            return;
        }
        this.price = price;
    }
}

public class StudentProfile {
    public static void main(String[] args) {
        System.out.println("本课为设计/原理示例，请阅读代码与注释，并完成文件末尾的课后练习。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 增加 `static int count`，每创建一个学生对象自增，主方法输出创建总数（思考 static 属于类还是对象）。
 * 2. 增加 `boolean isExcellent()` 方法，筛选并输出所有优秀学生。
 * 3. 设计 `StudentTest` 类，用数组存 5 个学生，输出平均分最高的学生姓名与分数。
 * ============================================================
 */
