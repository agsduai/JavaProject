/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-08
 * 课名：继承的作业
 * ------------------------------------------------------------
 * 知识点：
 *   - 本课为作业布置课，共 4 道题，覆盖本章继承全部核心考点：设计抽取、标准 JavaBean、方法重写、构造方法执行顺序
 *   - 作业一（Employee 体系设计）：父类 Employee，私有属性姓名 name、工资 salary，方法 work() 打印“员工在工作”；子类 Manager，私有属性奖金 bonus，重写 work() 打印“经理在管理团队”；子类 Cook，私有属性菜系 style，重写 work() 打印“厨师在做菜”。要求：父类子类全部按标准 JavaBean 编写，子类全参构造必须用 super 传递父类属性；测试类中用“空参 + set”和“全参构造”两种方式各创建对象并调用 work()
 *   - 作业二（动物继承结构带重写）：父类 Animal，属性 name，方法 eat() 打印“动物吃东西”；子类 Cat 重写 eat() 打印“猫吃鱼”，并新增抓老鼠方法 catchMouse()；子类 Dog 重写 eat() 打印“狗吃骨头”，并新增看家方法 lookHome()。要求：重写必须加 @Override；测试类中调用 Cat 的 eat、catchMouse 和 Dog 的 eat、lookHome，并额外验证“子类对象能调用父类中未被重写的成员”
 *   - 作业三（构造方法调用顺序分析）：给定父类 Fu（空参构造打印 “F...”、有参构造打印 “F(String)...”）与子类 Zi（空参构造打印 “Z...”、有参构造打印 “Z(String)...” 且第一行写 super(1)），写出分别执行 new Zi()、new Zi(1)、new Fu() 时的控制台输出，并画出 Zi 对象内存中父子空间的示意图
 *   - 作业四（手机/平板设备继承结构）：需求为手机（属性：品牌、价格；功能：打电话、玩游戏）与平板（属性：品牌、价格、尺寸；功能：看视频）。要求：自行判断哪些内容抽取到父类 Device、哪些留在子类；全部按标准 JavaBean 编写；父类提供“开机”方法，手机重写打印“手机快速开机”，平板重写打印“平板大屏开机”；编写测试类验证所有功能
 *   - 统一要求：所有属性必须私有化并提供 getter/setter；重写必须加 @Override；每个作业都要有独立的测试类并附运行结果截图或输出记录
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

// 骨架演示：带继承结构的标准 JavaBean 的“写法风格”。
// 只示范模板套路，各作业所需的属性和方法请自行设计
class InheritanceHomeworkDemoPerson {
    private String name; // 私有属性
    private int age;

    public InheritanceHomeworkDemoPerson() {
    }

    public InheritanceHomeworkDemoPerson(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public void show() {
        System.out.println(getName() + "..." + getAge());
    }
}

// 子类骨架：父类属性走 super，子类独有属性走 this，仅示范风格
class InheritanceHomeworkDemoStudent extends InheritanceHomeworkDemoPerson {
    private String sid; // 子类独有属性

    public InheritanceHomeworkDemoStudent() {
        // 隐藏着 super()，先执行父类空参构造
    }

    public InheritanceHomeworkDemoStudent(String name, int age, String sid) {
        super(name, age); // 父类部分交给父类构造
        this.sid = sid;   // 子类部分自己赋值
    }

    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }
}

public class InheritanceHomework {
    public static void main(String[] args) {
        System.out.println("本课为设计/原理示例，请阅读代码与注释，并完成文件末尾的课后练习。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完成作业一：Employee 员工体系设计（题面与要求见知识点清单）。
 * 2. 完成作业二：动物继承结构带重写（题面与要求见知识点清单）。
 * 3. 完成作业三：构造方法调用顺序分析（题面与要求见知识点清单）。
 * 4. 完成作业四：手机/平板设备继承结构（题面与要求见知识点清单）。
 * ============================================================
 */
