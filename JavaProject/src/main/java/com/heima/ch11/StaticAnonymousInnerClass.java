/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-20
 * 课名：静态内部类和匿名内部类
 * ------------------------------------------------------------
 * 知识点：
 *   - 静态内部类：写在成员位置上并用 static 修饰的类，属于外部类本身，随外部类加载
 *   - 静态内部类访问外部类的限制：只能访问外部类的静态成员，因为非静态成员属于对象，而静态内部类创建时可能还没有外部类对象
 *   - 创建静态内部类对象的格式：`外部类名.内部类名 对象名 = new 外部类名.内部类名();`（注意中间没有 new 外部类）
 *   - 匿名内部类：没有名字的特殊局部内部类，本质是一个“继承了该父类 / 实现了该接口”的子类对象
 *   - 匿名内部类的三种使用场景：① 继承父类重写方法 ② 实现接口重写方法 ③ 作为方法参数直接传递
 *   - 匿名内部类基本格式：`new 类名/接口名() { 重写方法 }`，适合只需要使用一次的场合，用完即弃
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

class StaticAnonymousInnerClassOuterDemo {
    static int staticNum = 10; // 外部类静态成员
    int num = 20;              // 外部类非静态成员

    // 静态内部类
    static class Inner {
        public void show() {
            // 只能访问外部类的静态成员；访问 num 会编译报错
            System.out.println("访问外部类静态变量：" + staticNum);
        }
    }
}

public class StaticAnonymousInnerClass {
    public static void main(String[] args) {
        // 静态内部类对象的创建：new 外部类名.内部类名()
        StaticAnonymousInnerClassOuterDemo.Inner oi = new StaticAnonymousInnerClassOuterDemo.Inner();
        oi.show();

        // 匿名内部类场景一：继承父类，重写方法
        StaticAnonymousInnerClassAnimal a = new StaticAnonymousInnerClassAnimal() {
            @Override
            public void eat() {
                System.out.println("匿名子类对象：狗吃骨头");
            }
        };
        a.eat();

        // 匿名内部类场景二：实现接口，重写方法
        StaticAnonymousInnerClassSwim s = new StaticAnonymousInnerClassSwim() {
            @Override
            public void swim() {
                System.out.println("匿名实现类对象：正在游泳");
            }
        };
        s.swim();

        // 匿名内部类场景三：作为方法参数直接传递
        go(new StaticAnonymousInnerClassSwim() {
            @Override
            public void swim() {
                System.out.println("匿名对象作为参数：参加游泳比赛");
            }
        });
    }

    // 参数是接口类型：只要能实现该接口的对象都能传进来
    public static void go(StaticAnonymousInnerClassSwim s) {
        System.out.println("比赛开始：");
        s.swim();
    }
}

// 父类
class StaticAnonymousInnerClassAnimal {
    public void eat() {
        System.out.println("吃东西");
    }
}

// 接口
interface StaticAnonymousInnerClassSwim {
    void swim();
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编写外部类 School 与静态内部类 Teacher，用 `new School.Teacher()` 创建对象，并在内部类中分别尝试访问外部类的静态成员和非静态成员，记录编译结果。
 * 2. 定义接口 Jump（方法 jump()），用匿名内部类实现它并打印“跳高运动员在跳高”，创建对象后直接调用 jump()。
 * 3. 编写方法 `public static void start(Runner r)`（自定义接口 Runner，方法 run()），在调用处用“匿名对象作为参数”的方式分别传入两种不同的实现并调用。
 * ============================================================
 */
