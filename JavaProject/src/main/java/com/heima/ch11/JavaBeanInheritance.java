/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-07
 * 课名：带有继承结构的标准javabean类
 * ------------------------------------------------------------
 * 知识点：
 *   - 标准 JavaBean 四件套：类名见名知意、成员变量私有化、空参构造 + 全参构造、每个属性配套 getter/setter
 *   - 带继承结构时，父类和子类各自封装自己的属性：父类的属性在父类私有并提供 getter/setter，子类独有属性同样处理
 *   - 子类全参构造的写法：父类部分的属性用 `super(父类属性值)` 交给父类构造，子类独有的属性用 `this.xxx = xxx` 自己赋值
 *   - 子类空参构造中隐藏着 `super()`，会先执行父类的空参构造
 *   - 模板书写顺序建议：成员变量 → 空参构造 → 全参构造 → getter/setter
 *   - 使用方式：先 new 对象（空参 + set 逐个赋值，或全参构造一步到位），再通过 get 方法取值
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

// 父类：标准 JavaBean
class JavaBeanInheritancePerson {
    private String name; // 姓名
    private int age;     // 年龄

    public JavaBeanInheritancePerson() {
    }

    public JavaBeanInheritancePerson(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}

// 子类：标准 JavaBean，父类属性通过 super 传递
class JavaBeanInheritanceStudent extends JavaBeanInheritancePerson {
    private String studentId; // 子类独有属性：学号

    public JavaBeanInheritanceStudent() {
        // 隐藏着 super()，会先执行父类的空参构造
    }

    // 全参构造：父类属性交给 super，子类属性自己赋值
    public JavaBeanInheritanceStudent(String name, int age, String studentId) {
        super(name, age);
        this.studentId = studentId;
    }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
}

public class JavaBeanInheritance {
    public static void main(String[] args) {
        // 方式一：空参构造 + set 逐个赋值
        JavaBeanInheritanceStudent s1 = new JavaBeanInheritanceStudent();
        s1.setName("阿玮");
        s1.setAge(18);
        s1.setStudentId("S001");
        System.out.println(s1.getName() + "..." + s1.getAge() + "..." + s1.getStudentId());

        // 方式二：全参构造一步到位
        JavaBeanInheritanceStudent s2 = new JavaBeanInheritanceStudent("小黑", 20, "S002");
        System.out.println(s2.getName() + "..." + s2.getAge() + "..." + s2.getStudentId());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 按照 11-07 的完整模板编写父类 Employee（私有属性：name、salary；空参构造、全参构造、全部 getter/setter）。
 * 2. 在第 1 题基础上编写子类 Manager（独有私有属性：bonus 奖金），补全完整模板，并用“空参 + set”和“全参构造”两种方式各创建一个对象，打印全部属性值。
 * 3. 给 Manager 增加方法 work()，打印“管理员工，总收入：工资+奖金”；思考方法内部能否直接访问父类的私有属性 salary，把你的结论和原因用文字写清楚。
 * ============================================================
 */
