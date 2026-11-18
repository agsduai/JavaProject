/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：16-07
 * 课名：对象流与序列化
 * ------------------------------------------------------------
 * 知识点：
 *   - 序列化：把对象转成字节序列写入文件；反序列化：从文件读回对象。
 *   - `ObjectOutputStream.writeObject(obj)` 序列化，`ObjectInputStream.readObject()` 反序列化。
 *   - 被序列化的类必须实现 `Serializable` 接口（标记接口，没有任何方法）。
 *   - `serialVersionUID` 是版本号，类结构改变后反序列化会校验它，不一致会抛 `InvalidClassException`，建议显式声明。
 *   - `transient` 修饰的成员变量不参与序列化，反序列化后得到默认值。
 *   - 易错：`Serializable` 只对当前类的字段生效，父类未实现序列化时父类字段不会被保存。
 * ============================================================
 */

package com.heima.ch16;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

// ==================== 示例代码 ====================

class ObjectStreamStudent implements Serializable {
    // 显式声明版本号，避免类改动后反序列化失败
    private static final long serialVersionUID = 1L;

    private String name;
    private int age;
    private transient String password; // transient：不参与序列化

    public ObjectStreamStudent(String name, int age, String password) {
        this.name = name;
        this.age = age;
        this.password = password;
    }

    @Override
    public String toString() {
        return "ObjectStreamStudent{name='" + name + "', age=" + age + ", password=" + password + "}";
    }
}

public class ObjectStream {
    public static void main(String[] args) {
        String path = "demo/student.dat";
        ObjectStreamStudent student = new ObjectStreamStudent("张三", 18, "123456");

        // 序列化：对象 -> 文件
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))) {
            out.writeObject(student);
            System.out.println("序列化完成：" + student);
        } catch (IOException e) {
            System.out.println("序列化失败：" + e.getMessage());
        }

        // 反序列化：文件 -> 对象
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
            ObjectStreamStudent read = (ObjectStreamStudent) in.readObject();
            System.out.println("反序列化结果：" + read); // password 为 null
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("反序列化失败：" + e.getMessage());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义 `Book` 类（书名、作者、价格）实现 `Serializable`，序列化 3 本书到文件，再反序列化读回来打印。
 * 2. 给 `Book` 增加一个 `transient` 修饰的字段，验证反序列化后它的值。
 * 3. 删除并重新添加一个字段后再次反序列化旧文件，观察 `serialVersionUID` 不一致导致的异常并解释原因。
 * ============================================================
 */
