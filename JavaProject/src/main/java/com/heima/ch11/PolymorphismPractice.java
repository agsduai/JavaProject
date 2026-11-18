/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-13
 * 课名：多态的综合练习
 * ------------------------------------------------------------
 * 知识点：
 *   - 题面：编写公司“统一接待系统”，入口方法可以接收所有人员对象；学生来时打印“欢迎学生：某某，请到3楼自习”，老师来时打印“欢迎老师：某某，请到2楼备课”；接待完成后学生执行自习、老师执行备课（各自特有行为）
 *   - 思路第一步：抽象父类 Person（私有姓名属性 + 全套构造/getter/setter + 通用方法 sayHello）
 *   - 思路第二步：Student、Teacher 继承 Person，各自重写 sayHello 打印不同欢迎语，并各自定义特有方法 study / teach
 *   - 思路第三步：编写静态方法 `welcome(Person p)` 作为统一入口，参数声明为父类类型，即可接收任何子类对象 —— 这就是多态参数
 *   - 思路第四步：入口内部如需调用子类特有方法，先用 instanceof 判断真实类型，再强转调用（推荐 JDK14+ 的一步到位写法）
 *   - 考点覆盖：多态三前提、编译看左边运行看右边、instanceof + 向下转型
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

class PolymorphismPracticePerson {
    private String name;

    public PolymorphismPracticePerson() {
    }

    public PolymorphismPracticePerson(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public void sayHello() {
        System.out.println("你好，我是" + name);
    }
}

class PolymorphismPracticeStudent extends PolymorphismPracticePerson {
    public PolymorphismPracticeStudent() {
    }

    public PolymorphismPracticeStudent(String name) {
        super(name);
    }

    @Override
    public void sayHello() {
        System.out.println("欢迎学生：" + getName() + "，请到3楼自习");
    }

    // 子类特有方法
    public void study() {
        System.out.println(getName() + "开始自习");
    }
}

class PolymorphismPracticeTeacher extends PolymorphismPracticePerson {
    public PolymorphismPracticeTeacher() {
    }

    public PolymorphismPracticeTeacher(String name) {
        super(name);
    }

    @Override
    public void sayHello() {
        System.out.println("欢迎老师：" + getName() + "，请到2楼备课");
    }

    // 子类特有方法
    public void teach() {
        System.out.println(getName() + "开始备课");
    }
}

public class PolymorphismPractice {
    public static void main(String[] args) {
        welcome(new PolymorphismPracticeStudent("阿玮"));
        welcome(new PolymorphismPracticeTeacher("老王"));
    }

    // 统一入口：参数为父类类型，能接收所有子类对象 —— 多态
    public static void welcome(PolymorphismPracticePerson p) {
        // 编译看左边，运行看右边：执行的是传入对象重写后的方法
        p.sayHello();

        // 调用子类特有方法：先判断真实类型，再一步到位转型
        if (p instanceof PolymorphismPracticeStudent stu) { // JDK14+ 新写法
            stu.study();
        } else if (p instanceof PolymorphismPracticeTeacher t) {
            t.teach();
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 变形题：编写“宠物诊所登记系统”，父类 Pet（属性：名字、年龄），子类 Dog（特有方法：打疫苗）与 Bird（特有方法：体检），统一入口方法 register(Pet p) 打印各自的登记欢迎语并调用各自的特有方法。
 * 2. 变形题：编写“支付系统”，父类 Pay（方法 pay(double money)），子类 WechatPay 重写后打印“微信支付 xx 元”、AliPay 重写后打印“支付宝支付 xx 元”，统一入口 scan(Pay p, double money) 完成支付，并累计打印总支付金额。
 * 3. 挑战题：在第 2 题基础上增加“退款”功能：父类新增 refund 方法，入口方法能同时处理支付与退款，并对非法金额（小于等于 0）给出错误提示。
 * ============================================================
 */
