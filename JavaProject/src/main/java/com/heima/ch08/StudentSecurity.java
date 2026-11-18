/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：8-05
 * 课名：数据安全的练习：学生
 * ------------------------------------------------------------
 * 知识点：
 *   - 题面：定义 Student 类，属性 name、age 全部用 private 修饰；提供 getName/setName、getAge/setAge；setAge 中校验年龄必须在 0~120 之间，非法则打印提示且不修改；再提供 study() 方法打印学习信息；最后写测试类验证。
 *   - 思路：第一步属性私有化，第二步在 setter 方法内用 if 校验参数，第三步提供 getter 返回属性值，第四步在测试类中只通过方法访问属性。
 *   - 易错点：校验不通过时不要给 age 赋值，保持原值即可；setter 的参数名如果与成员变量重名，需要用 this 区分（下一课详细讲解）。
 * ============================================================
 */

package com.heima.ch08;

// ==================== 示例代码 ====================

class StudentSecurityStudent {
    private String name; // 姓名
    private int age;     // 年龄

    // 设置年龄，带合法性校验
    public void setAge(int a) {
        if (a >= 0 && a <= 120) {
            age = a;
        } else {
            System.out.println("输入的年龄有误，赋值失败，年龄保持默认值0");
        }
    }

    // 获取年龄
    public int getAge() {
        return age;
    }

    // 设置姓名
    public void setName(String n) {
        name = n;
    }

    // 获取姓名
    public String getName() {
        return name;
    }

    // 行为方法
    public void study() {
        System.out.println("名为" + name + "的学生正在学习Java");
    }
}

public class StudentSecurity {
    public static void main(String[] args) {
        StudentSecurityStudent stu = new StudentSecurityStudent();

        stu.setName("阿玮");
        stu.setAge(18); // 合法，成功赋值
        System.out.println(stu.getName() + "：" + stu.getAge());
        stu.study();

        stu.setAge(200); // 非法，被校验拦下
        System.out.println("再次获取年龄：" + stu.getAge()); // 仍然是18
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义一个 GameRole（游戏角色）类，private 属性 name、hp（血量）；setHp 校验血量必须在 0~100 之间，非法则提示并保持原值；提供 getter；再写 fight() 方法打印"XX 正在战斗，当前血量 XX"。
 * 2. 给 GameRole 增加一个受击方法被调用时血量减 10，若血量已经是 0 则打印"已阵亡"且不再减血，写测试类连续受击验证。
 * 3. 定义 BankAccount（银行账户）类，private 属性 owner（户主）和 balance（余额）：存款方法要求金额必须大于 0，取款方法要求金额不能超过余额，否则打印失败提示，写测试类覆盖合法与非法两种情况。
 * ============================================================
 */
