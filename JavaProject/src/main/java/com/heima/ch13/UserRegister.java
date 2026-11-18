/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：13-02
 * 课名：用户类和注册的业务
 * ------------------------------------------------------------
 * 知识点：
 *   - JavaBean 标准写法：属性全部 private，提供无参构造和全参构造，给每个属性提供 getter/setter。
 *   - User 类只需要两个属性：username、password；注册的本质就是把录入的数据变成 User 对象存起来。
 *   - 格式校验：用户名、密码长度要求 3~15 位，不合法要循环重新录入，校验通过才能继续往下走。
 *   - 唯一性校验：注册前遍历 ArrayList<User> 比对用户名是否已存在，存在则提示重新录入。
 *   - 用集合充当"数据库"：ArrayList<User> 定义为静态成员变量，注册成功就 add 进去。
 * ============================================================
 */

package com.heima.ch13;

import java.util.ArrayList;
import java.util.Scanner;

// ==================== 示例代码 ====================

class UserRegisterUser {
    private String username;
    private String password;

    public UserRegisterUser() {
    }

    public UserRegisterUser(String username, String password) {
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

public class UserRegister {
    static Scanner sc = new Scanner(System.in);
    // 用集合模拟数据库，存放所有已注册的用户
    static ArrayList<UserRegisterUser> userList = new ArrayList<>();

    public static void main(String[] args) {
        userList.add(new UserRegisterUser("zhangsan", "123456"));
        register();
        System.out.println("当前已注册用户数：" + userList.size());
    }

    // 注册业务：用户名校验 + 唯一性校验 + 密码校验 + 存入集合
    public static void register() {
        String username;
        while (true) {
            System.out.println("请输入用户名（3~15 位）：");
            username = sc.next();
            if (username.length() < 3 || username.length() > 15) {
                System.out.println("用户名长度不合法，请重新录入");
                continue;
            }
            if (isExists(username)) {
                System.out.println("用户名已被占用，请重新录入");
            } else {
                break;
            }
        }
        String password;
        while (true) {
            System.out.println("请输入密码（3~15 位）：");
            password = sc.next();
            if (password.length() >= 3 && password.length() <= 15) {
                break;
            }
            System.out.println("密码长度不合法，请重新录入");
        }
        // 校验全部通过，注册信息存入集合
        userList.add(new UserRegisterUser(username, password));
        System.out.println("注册成功！");
    }

    // 判断用户名是否已存在
    public static boolean isExists(String username) {
        for (UserRegisterUser u : userList) {
            if (u.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 注册时要求输入两次密码，两次不一致时提示"两次密码不一致"并重新录入。
 * 2. 用户名校验升级：只允许出现字母和数字，包含其他字符时提示"用户名包含非法字符"并重新录入。
 * 3. 注册成功后打印当前已注册的用户总数，并思考：当用户量非常大时，"遍历查重"这种方式会有什么问题？
 * ============================================================
 */
