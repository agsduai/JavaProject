/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：8-08
 * 课名：构造方法的注意事项
 * ------------------------------------------------------------
 * 知识点：
 *   - 如果一个类中一个构造方法都没写，编译器会自动赠送一个空参构造（方法体为空）。
 *   - 一旦手动写了有参构造，系统就不再赠送空参构造，此时再写"new 类名()"会编译报错。
 *   - 解决办法：想要两种创建方式都可用，就必须把空参构造和有参构造都手动写出来。
 *   - 构造方法可以重载：参数个数、类型、顺序不同即构成重载，创建对象时按传入的参数自动匹配。
 *   - 标准 JavaBean：属性私有化 + 空参构造 + 全参构造 + getter/setter，是实际开发中实体类的标准写法。
 *   - IDEA 快捷键：Alt+Insert 可以自动生成构造方法和 getter/setter，没必要手敲。
 * ============================================================
 */

package com.heima.ch08;

// ==================== 示例代码 ====================

// 标准 JavaBean（标准实体类）写法
class ConstructorNotesUser {
    private String username; // 用户名
    private String password; // 密码

    // 注意：只写下面那个有参构造后，系统就不再赠送空参构造
    // 想让两种 new 方式都可用，必须手动补上空参构造（构造方法可以重载）
    public ConstructorNotesUser() {
    }

    // 有参构造（全参构造）
    public ConstructorNotesUser(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

public class ConstructorNotes {
    public static void main(String[] args) {
        // 方式1：空参构造 + setter 赋值
        ConstructorNotesUser u1 = new ConstructorNotesUser();
        u1.setUsername("admin");
        u1.setPassword("123456");
        System.out.println(u1.getUsername() + " " + u1.getPassword());

        // 方式2：有参构造，创建的同时赋值
        ConstructorNotesUser u2 = new ConstructorNotesUser("阿玮", "888888");
        System.out.println(u2.getUsername() + " " + u2.getPassword());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个 Dog 类只定义有参构造，然后在测试类中写"new Dog()"，把编译器报错信息完整记录下来并解释原因。
 * 2. 给 Dog 类补上空参构造，使两种创建方式都能使用；再添加一个只接收 name 的构造方法，体现构造方法的重载。
 * 3. 按标准 JavaBean 格式完整书写 Movie 类（属性 title、score、actor 全部私有，空参构造 + 全参构造 + getter/setter），并用两种创建方式各生成一个对象打印信息。
 * ============================================================
 */
