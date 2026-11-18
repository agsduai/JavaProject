/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-02
 * 课名：如何设计一个复杂的继承结构
 * ------------------------------------------------------------
 * 知识点：
 *   - 设计套路：先看需求 → 列出每个类的属性和方法 → 找出共同点 → 把共同点抽取到父类 → 子类 `extends` 父类
 *   - 典型例子：学生有姓名、年龄、学号、学习方法；老师有姓名、年龄、工号、讲课方法 → 把姓名、年龄、吃饭、睡觉抽到父类 Person
 *   - 抽取原则：所有子类都有的内容放父类；只有部分子类有的内容留在子类
 *   - 父类中的成员要让子类能直接使用，一般用 public（或 protected）修饰，暂时不要用 private
 *   - 抽象成父类的过程就是“从具体到一般”的归纳思维，这也是面向对象设计的基本功
 *   - 设计完成后要写测试类验证：子类对象既能调父类方法，也能调自己的方法
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

// 父类：从需求中抽取出来的共同属性和方法
class InheritanceStructurePerson {
    String name; // 姓名
    int age;     // 年龄

    public void eat() {
        System.out.println(name + "正在吃饭");
    }

    public void sleep() {
        System.out.println(name + "正在睡觉");
    }
}

// 子类：学生，继承 InheritanceStructurePerson 后只写自己独有的内容
class InheritanceStructureStudent extends InheritanceStructurePerson {
    String studentId; // 独有属性：学号

    public void study() {
        System.out.println(name + "正在学习");
    }
}

// 子类：老师
class InheritanceStructureTeacher extends InheritanceStructurePerson {
    String workId; // 独有属性：工号

    public void teach() {
        System.out.println(name + "正在讲课");
    }
}

public class InheritanceStructure {
    public static void main(String[] args) {
        InheritanceStructureStudent s = new InheritanceStructureStudent();
        s.name = "阿玮";
        s.age = 18;
        s.studentId = "S001";
        s.eat();   // 调用父类的方法
        s.study(); // 调用子类自己的方法

        InheritanceStructureTeacher t = new InheritanceStructureTeacher();
        t.name = "老王";
        t.age = 35;
        t.workId = "T001";
        t.teach();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 设计继承结构：父类 Device（属性：品牌 brand、价格 price；方法：开机 powerOn），子类 Phone（方法：打电话 call）和 Computer（方法：编程 code），并在测试类中各创建一个对象调用全部方法。
 * 2. 需求：动物园里有猫、鸟、鱼三种动物。先在纸上列出“哪些属性和方法是共同的、哪些是独有的”抽取分析表，再按分析结果编写父类 Animal 与三个子类。
 * 3. 在第 2 题基础上，给每个子类增加一个独有属性（如猫的捕鼠数、鸟的飞行高度），验证子类对象可以同时使用父类和子类的成员。
 * ============================================================
 */
