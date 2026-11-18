/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-05
 * 课名：类加载机制与双亲委派
 * ------------------------------------------------------------
 * 知识点：
 *   - 类的生命周期：加载 → 验证 → 准备 → 解析 → 初始化 → 使用 → 卸载。**准备**阶段给静态变量赋零值，**初始化**阶段才执行 `<clinit>`（静态赋值与静态块）。
 *   - **双亲委派**：类加载请求先交给父加载器，父加载器加载不了才自己加载。好处是**安全**（核心类不会被自定义的 `java.lang.String` 覆盖）与**唯一**（同一个类只加载一次）。
 *   - 三层加载器：**Bootstrap**（C++ 实现，加载 `java.base`）→ **Platform/Ext** → **Application**（classpath）。JDK 9 之后是模块化后的 Platform ClassLoader。
 *   - **打破双亲委派**的三种场景：SPI（`ServiceLoader` 用线程上下文类加载器加载实现类，如 JDBC 驱动）、Tomcat（每个 WebApp 独立加载器实现应用隔离）、热部署/OSGi（同一类多版本共存）。
 *   - **类初始化的时机**：`new`、访问静态字段（非 final 常量）、调用静态方法、反射、子类初始化先初始化父类、`main` 类。**被动引用**不触发初始化（如通过子类访问父类静态字段、数组声明、访问 `static final` 常量）。
 *   - 同一个类被不同加载器加载，会产生两个**互不兼容**的 Class 对象，`instanceof` 为 false，强转抛 `ClassCastException`。
 *   - 易错：以为 `Class.forName` 和 `ClassLoader.loadClass` 一样（前者默认初始化，后者不初始化）；自定义加载器忘了重写 `findClass` 而是覆盖 `loadClass`，破坏了双亲委派。
 * ============================================================
 */

package com.heima.ch30;

// ==================== 示例代码 ====================

public class ClassLoading {
    static class Parent {
        static {
            System.out.println("  Parent 静态块执行");
        }

        static int value = 10;

        static void hello() {
            System.out.println("  Parent.hello()");
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("  Child 静态块执行");
        }

        static int childValue = 20;
    }

    static final String CONSTANT = "编译期常量，不触发初始化";

    public static void main(String[] args) throws Exception {
        System.out.println("=== 主动引用：触发初始化 ===");
        System.out.println("  准备 new Child() 之前");
        new Child();
        System.out.println("  new 之后（先父后子）");

        System.out.println();
        System.out.println("=== 被动引用：不触发初始化 ===");
        System.out.println("  访问编译期常量 CONSTANT = " + CONSTANT + "（常量在编译期已内联到调用方）");
        Parent[] arr = new Parent[3];
        System.out.println("  声明数组 Parent[3] 长度 " + arr.length + "（只创建数组对象，不初始化 Parent）");
        int v = Child.value;
        System.out.println("  通过子类访问父类静态字段 Child.value = " + v + "（只初始化 Parent，不初始化 Child）");

        System.out.println();
        System.out.println("=== 类加载器层次 ===");
        ClassLoader app = ClassLoading.class.getClassLoader();
        System.out.println("  Application ClassLoader：" + app);
        System.out.println("  Platform  ClassLoader：" + app.getParent());
        System.out.println("  Bootstrap ClassLoader：" + app.getParent().getParent() + "（C++ 实现，返回 null）");

        System.out.println();
        System.out.println("=== 双亲委派：加载 java.lang.String 的请求一路向上 ===");
        Class<?> stringClass = Class.forName("java.lang.String");
        System.out.println("  String 的加载器：" + stringClass.getClassLoader() + "（null 表示 Bootstrap）");
        System.out.println("  -> 自定义加载器收到请求先委派给父，父能加载就不自己加载");
        System.out.println("  -> 防止伪造核心类，也保证同一个类只被加载一次");

        System.out.println();
        System.out.println("=== 打破双亲委派：SPI 与 Tomcat ===");
        System.out.println("  SPI：JDBC 的 Driver 由各厂商 jar 提供，Bootstrap 加载器看不到 classpath");
        System.out.println("       -> 用 Thread.currentThread().getContextClassLoader() 反向委派");
        System.out.println("  Tomcat：每个 WebApp 一个 WebappClassLoader，先自己加载再委派父");
        System.out.println("       -> 实现应用隔离，两个应用可以有不同版本的同一个库");

        System.out.println();
        System.out.println("=== 同一个类、不同加载器 = 两个不同的 Class ===");
        System.out.println("  classLoaderA.loadClass(X) != classLoaderB.loadClass(X)");
        System.out.println("  后果：instanceof 为 false，强转抛 ClassCastException");
        System.out.println("  这也是热部署容易踩的坑：旧对象还持有旧加载器的类");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个自定义 `ClassLoader` 从指定目录读取 `.class`，加载后打印 `getClassLoader()`。
 * 2. 用 `Class.forName("...")` 与 `ClassLoader.loadClass("...")` 分别加载 `Child`，对比静态块是否执行。
 * 3. 解释 Tomcat 为什么要打破双亲委派，如果不打破会发生什么。
 * ============================================================
 */
