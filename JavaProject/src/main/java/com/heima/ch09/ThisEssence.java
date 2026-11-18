/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：9-03
 * 课名：this关键字的本质
 * ------------------------------------------------------------
 * 知识点：
 *   - 本质：this 就是"当前调用该方法的对象"的地址值，方法被谁调用，方法内的 this 就代表谁。
 *   - 例如 stu1.show() 执行时，方法内的 this 是 stu1 的地址；改成 stu2.show()，this 就变成 stu2 的地址。
 *   - this 不是变量声明出来的，它是成员方法隐含的一部分，随方法被哪个对象调用而动态确定。
 *   - this.成员变量 = 形参 的执行过程：先用 this 拿到当前对象的地址，再去堆中找到该对象的成员变量并赋值。
 *   - 直接打印 this 可以看到"类名@十六进制地址"形式的输出，与打印对象本身的结果一致。
 *   - static 方法中没有 this（静态与对象无关，下一章展开），this 只存在于非静态的成员方法中。
 * ============================================================
 */

package com.heima.ch09;

// ==================== 示例代码 ====================

// 演示 this 的本质（结合注释画出内存图）
public class ThisEssence {
    public static void main(String[] args) {
        // 堆中创建两个对象：0x0011 和 0x0022
        ThisEssenceThisCat c1 = new ThisEssenceThisCat();
        c1.name = "阿玮";

        ThisEssenceThisCat c2 = new ThisEssenceThisCat();
        c2.name = "小黑";

        // c1 调用 say：say 方法进栈，方法内的 this 就是 0x0011
        c1.say();
        // c2 调用 say：say 再次进栈，这次方法内的 this 就是 0x0022
        c2.say();

        // 打印对象本身，输出"类名@地址值"，可与 say 中打印的 this 对比
        System.out.println(c1);
        System.out.println(c2);
    }
}

class ThisEssenceThisCat {
    String name;

    public void say() {
        // this 代表当前调用该方法的对象的地址值
        // c1 调用时，下面这行等价于打印 c1 的 name 和地址
        System.out.println("我是" + this.name + "，我的地址是" + this);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 创建两个 Student 对象并赋不同的 name，让它们分别调用同一个 introduce() 方法，预测两次打印中 this 的地址值是否相同，运行验证并画图说明。
 * 2. 读代码预测输出：类中定义方法 self() 返回 this，main 中执行 System.out.println(s.self() == s)，结果是 true 还是 false？画出内存图解释原因。
 * 3. 手写一个 Counter 类，成员变量 count，方法 add() 中写 this.count++，创建两个对象各调用 add 三次，预测并验证两个对象的 count 是否互不影响，用内存图说明原因。
 * ============================================================
 */
