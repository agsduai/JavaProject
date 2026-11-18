/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：3-06
 * 课名：switch语句
 * ------------------------------------------------------------
 * 知识点：
 *   - 格式：`switch (表达式) { case 值1: 语句体1; break; ... default: 语句体n; break; }`
 *   - 执行流程：拿表达式的值依次与各 case 匹配，命中后执行对应语句体，遇到 break 结束整个 switch
 *   - 所有 case 都不匹配时执行 default（兜底分支）
 *   - 表达式支持的类型：byte、short、char、int 及其包装类、枚举、String（JDK17）
 *   - case 后面只能写字面量或常量，不能写变量；各 case 的值不能重复
 *   - switch 适合"一个值对应固定几种结果"的场景，比多分支 if 更清晰
 * ============================================================
 */

package com.heima.ch03;

import java.util.Scanner;

// ==================== 示例代码 ====================

public class SwitchStatement {
    public static void main(String[] args) {
        // 键盘录入星期几，输出当天安排
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入今天星期几（1~7）：");
        int week = sc.nextInt();
        switch (week) {
            case 1:
                System.out.println("周一：开始上课");
                break;
            case 2:
                System.out.println("周二：写练习题");
                break;
            case 3:
                System.out.println("周三：阶段性测试");
                break;
            case 4:
                System.out.println("周四：错题复盘");
                break;
            case 5:
                System.out.println("周五：项目实战");
                break;
            default:
                System.out.println("周末：好好休息");
                break;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 键盘录入 1~7 输出对应的星期名称（星期一 ~ 星期日），超出范围的数字输出"输入有误"。
 * 2. 键盘录入手机按键编号 1~3：1 打印"打开相册"，2 打印"打开相机"，3 打印"打开通讯录"，其他编号打印"按键无效"。
 * 3. 键盘录入会员积分档位：100 输出"青铜会员"，200 输出"白银会员"，300 输出"黄金会员"，其他数值输出"普通用户"。
 * ============================================================
 */
