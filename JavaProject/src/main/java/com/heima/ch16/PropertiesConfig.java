/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：16-08
 * 课名：Properties配置文件
 * ------------------------------------------------------------
 * 知识点：
 *   - `Properties` 是 `Hashtable` 的子类，专门用于读写键值对配置，键和值都是 `String`。
 *   - 常用方法：`setProperty(k, v)`、`getProperty(k)`、`stringPropertyNames()` 获取所有键。
 *   - `load(Reader)` 从配置文件读取，`store(Writer, comments)` 写回配置文件。
 *   - 配置文件格式：一行一个 `key=value`，`#` 开头是注释，值前后空格会被忽略。
 *   - 典型用途：数据库连接参数、游戏关卡配置、程序开关等需要"改配置不改代码"的场景。
 *   - 易错：`load` 使用 ISO-8859-1 解析，含中文时要用 `load(Reader)` 并指定 UTF-8，或把中文写成 Unicode 转义序列。
 * ============================================================
 */

package com.heima.ch16;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

// ==================== 示例代码 ====================

public class PropertiesConfig {
    public static void main(String[] args) {
        String path = "demo/config.properties";

        // 写入配置
        Properties props = new Properties();
        props.setProperty("username", "admin");
        props.setProperty("password", "123456");
        props.setProperty("url", "jdbc:mysql://localhost:3306/test");
        try (FileWriter writer = new FileWriter(path)) {
            props.store(writer, "数据库配置");
            System.out.println("配置已写入：" + path);
        } catch (IOException e) {
            System.out.println("写入失败：" + e.getMessage());
        }

        // 读取配置
        Properties loaded = new Properties();
        try (FileReader reader = new FileReader(path)) {
            loaded.load(reader);
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        System.out.println("用户名：" + loaded.getProperty("username"));
        System.out.println("密码：" + loaded.getProperty("password"));
        System.out.println("不存在的键（默认值）：" + loaded.getProperty("port", "3306"));

        // 遍历所有键
        Set<String> keys = loaded.stringPropertyNames();
        for (String key : keys) {
            System.out.println(key + " = " + loaded.getProperty(key));
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 创建 `app.properties`，写入程序名称、版本号、作者三项信息，再读取并格式化输出。
 * 2. 编写方法 `String getConfig(String path, String key, String defaultValue)`，键不存在时返回默认值。
 * 3. 用 `Properties` 实现一个简易的"多语言提示"配置，根据语言代码取出对应的问候语。
 * ============================================================
 */
