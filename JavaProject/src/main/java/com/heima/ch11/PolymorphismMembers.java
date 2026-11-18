/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-12
 * 课名：多态调用成员的特点和弊端以及解决方案
 * ------------------------------------------------------------
 * 知识点：
 *   - 成员变量：编译看左边，运行看左边 —— 变量没有“运行看右边”一说；父类中没有该变量直接编译报错，父子同名变量取的是父类的值
 *   - 成员方法：编译看左边，运行看右边 —— 编译检查父类是否有声明，运行执行子类重写后的方法体
 *   - 弊端：多态下父类引用无法直接调用子类特有的方法，因为编译阶段只看父类，父类中没有就报错
 *   - 解决方案：向下转型（强制类型转换）—— `子类类型 变量名 = (子类类型) 父类引用;`，转换后即可调用子类特有成员
 *   - 强转有风险：如果对象的真实类型与目标类型不匹配，运行时会抛出 ClassCastException，所以强转前必须用 instanceof 判断
 *   - instanceof 用法：`对象 instanceof 类型` 返回 boolean；JDK14+ 支持判断并转型一步到位：`if (p instanceof Student s) { s.xxx(); }`
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

public class PolymorphismMembers {
    public static void main(String[] args) {
        PolymorphismMembersPerson p = new PolymorphismMembersStudent();

        // 成员变量：编译看左边，运行看左边
        // System.out.println(p.score); // 编译报错：PolymorphismMembersPerson 中没有 score
        System.out.println(p.name); // 输出“父类Person的name”：变量永远取父类的

        // 成员方法：编译看左边，运行看右边
        p.show(); // 执行的是子类重写后的方法

        // 弊端演示：study 是子类特有方法，父类中没有，直接调用编译报错
        // p.study();

        // 解决方案：instanceof 判断 + 向下转型
        if (p instanceof PolymorphismMembersStudent) {
            PolymorphismMembersStudent s = (PolymorphismMembersStudent) p;
            s.study(); // 强转后即可调用子类特有方法
        }

        // JDK14+ 新写法：判断与转型一步到位
        if (p instanceof PolymorphismMembersStudent stu) {
            stu.study();
        }
    }
}

class PolymorphismMembersPerson {
    String name = "父类Person的name";

    public void show() {
        System.out.println("PolymorphismMembersPerson 的 show");
    }
}

class PolymorphismMembersStudent extends PolymorphismMembersPerson {
    String name = "子类Student的name"; // 与父类同名
    int score = 100;                  // 子类特有变量

    @Override
    public void show() {
        System.out.println("PolymorphismMembersStudent 重写后的 show");
    }

    // 子类特有方法
    public void study() {
        System.out.println("学生在学习");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编写多态代码 `Animal a = new Cat();`，分别验证：访问成员变量时取的是父类还是子类的值，调用重写方法时执行的是谁的逻辑，并把结论写在代码注释里。
 * 2. 给 Cat 增加特有方法 catchMouse()，用多态变量 a 直接调用观察编译报错，再用 instanceof + 强转修复；最后故意把一个非 Cat 类型的对象强转成 Cat，观察运行时的 ClassCastException。
 * 3. 编写方法 `public static void feed(Animal a)`，要求内部用 instanceof 判断：传入的是狗就调用其特有的看家方法，传入的是猫就调用其特有的抓老鼠方法。
 * ============================================================
 */
