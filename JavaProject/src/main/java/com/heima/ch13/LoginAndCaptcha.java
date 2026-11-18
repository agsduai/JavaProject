/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：13-03
 * 课名：验证码和登录业务的完整实现
 * ------------------------------------------------------------
 * 知识点：
 *   - 验证码：5 位字符，从"大小写字母 + 数字"共 62 个字符中随机取；把字符池写成字符串，用随机下标取字符再拼接最简单。
 *   - 登录三步校验：先验证码（忽略大小写用 equalsIgnoreCase）→ 再用户名 → 再密码，验证码不对就不用往后比了。
 *   - 用户名要在集合中查询：没查到提示"用户名未注册"；查到了再比对密码，不一致提示"密码错误"。
 *   - 提示信息要模糊化（如统一提示"用户名或密码错误"），避免暴露"该用户名存在"这类信息，这是真实项目的安全习惯。
 *   - 登录成功后打印欢迎语，后续从这里衔接进入游戏模块。
 * ============================================================
 */

package com.heima.ch13;

import java.util.Random;
import java.util.Scanner;

// ==================== 示例代码 ====================

public class LoginAndCaptcha {
    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        // 模拟已注册用户（真实项目应来自用户集合）
        String rightUsername = "zhangsan";
        String rightPassword = "123456";

        // 第一步：生成并校验验证码
        String code = getCode();
        System.out.println("验证码：" + code);
        System.out.println("请输入验证码：");
        String inputCode = sc.next();
        // 验证码比较忽略大小写
        if (!code.equalsIgnoreCase(inputCode)) {
            System.out.println("验证码错误，登录失败");
            return;
        }

        // 第二步：校验用户名和密码
        System.out.println("请输入用户名：");
        String username = sc.next();
        System.out.println("请输入密码：");
        String password = sc.next();
        if (username.equals(rightUsername) && password.equals(rightPassword)) {
            System.out.println("登录成功，欢迎 " + username + " 进入游戏！");
        } else {
            System.out.println("用户名或密码错误，登录失败");
        }
    }

    // 生成 5 位验证码：大小写字母 + 数字
    public static String getCode() {
        String pool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            int index = r.nextInt(pool.length());
            sb.append(pool.charAt(index));
        }
        return sb.toString();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 生成验证码时保证 5 位中至少包含一个数字，不满足就重新生成。
 * 2. 登录失败时给用户 3 次机会，3 次全部失败则提示"账户已被锁定"并结束程序。
 * 3. 注册成功后自动进入登录环节，登录成功后打印"欢迎进入游戏"，把注册和登录串成一条完整流程。
 * ============================================================
 */
