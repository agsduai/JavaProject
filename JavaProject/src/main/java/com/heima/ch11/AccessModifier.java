/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-10
 * 课名：Java中的权限修饰符
 * ------------------------------------------------------------
 * 知识点：
 *   - 四种权限修饰符由小到大：private < 缺省（不写） < protected < public，范围越大越开放
 *   - private：只有本类可以访问
 *   - 缺省（什么都不写）：本类 + 同一个包中的其他类可以访问（也叫包权限）
 *   - protected：本类 + 同一个包中的其他类 + 不同包中的子类可以访问
 *   - public：任何位置都可以访问
 *   - 实际开发经验：成员变量一般用 private，方法一般用 public，构造方法一般用 public；只想给子类用的成员用 protected
 *   - 补充规则：修饰外部类时只能用 public 或缺省，不能写 private、protected 作用范围速记表： | 修饰符 | 本类 | 同一个包中的其他类 | 不同包中的子类 | 不同包中的其他类 | | --- | --- | --- | --- | --- | | private | 可访问 | 不可访问 | 不可访问 | 不可访问 | | 缺省 | 可访问 | 可访问 | 不可访问 | 不可访问 | | protected | 可访问 | 可访问 | 可访问 | 不可访问 | | public | 可访问 | 可访问 | 可访问 | 可访问 |
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

// 演示本类中四种权限修饰符的访问情况
class AccessModifierModifierDemo {
    private int a = 1;   // 私有：只有本类能用
    int b = 2;           // 缺省：本类 + 同一个包
    protected int c = 3; // 受保护：本类 + 同一个包 + 不同包的子类
    public int d = 4;    // 公有：任何位置都能用

    public void test() {
        // 本类内部：四种修饰的成员全部可以访问
        System.out.println(a);
        System.out.println(b);
        System.out.println(c);
        System.out.println(d);
    }
}

// 与 AccessModifierModifierDemo 在同一个包（同一个目录）中
public class AccessModifier {
    public static void main(String[] args) {
        AccessModifierModifierDemo m = new AccessModifierModifierDemo();
        // System.out.println(m.a); // 编译报错：private 只有本类能访问
        System.out.println(m.b); // 同一个包：缺省可以访问
        System.out.println(m.c); // 同一个包：protected 可以访问
        System.out.println(m.d); // public：任何地方都能访问
        m.test();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在一个类中按四种修饰符各定义一个成员变量，在同一个包的测试类中逐一访问，记录哪些编译通过、哪些报错，整理成表格。
 * 2. 新建两个包（如 com.fu 和 com.zi），让 com.zi.Zi 继承 com.fu.Fu，在 Zi 中访问 Fu 的四种成员变量，验证“不同包中只有 protected 和 public 能被子类访问”。
 * 3. 思考题：一个成员既不想让外界随便访问，又必须让“位于其他包中的子类”能使用，应该选择哪种修饰符？写出选择理由。
 * ============================================================
 */
