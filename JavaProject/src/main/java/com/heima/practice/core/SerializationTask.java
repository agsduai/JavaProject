/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 16-02
 * 课名：对象序列化存取
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现对象存取：可序列化类（员工：姓名/工号/入职日期）-> ObjectOutputStream 写文件 -> ObjectInputStream 读回并校验一致；演示 serialVersionUID 不匹配的 InvalidClassException（改类结构后用旧文件读）；演示 transient 字段（密码）不序列化。
 *   - 【考核点】Serializable 接口、serialVersionUID 作用、transient、对象流关闭顺序、版本兼容。
 *   - 【易错点】忘记 serialVersionUID（编译器自动生成，类一改就失效）；transient 后反序列化为默认值未处理；静态字段不会被序列化的误解。
 *   - 【提示】写两个版本类（V1/V2 不同 UID）演示异常比讲十遍都清楚。
 * ============================================================
 */

package com.heima.practice.core;

import java.io.*;

// ==================== 示例代码 ====================

public class SerializationTask {
    static class Token implements Serializable {
        private static final long serialVersionUID = 1L;
        String user;
        transient String secret;       // 不序列化

        Token(String user, String secret) {
            this.user = user;
            this.secret = secret;
        }
    }

    public static void main(String[] args) throws Exception {
        File f = File.createTempFile("tok", ".bin");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))) {
            out.writeObject(new Token("u1", "s3cret"));
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            Token t = (Token) in.readObject();
            System.out.printf("  user=%s secret=%s（transient 读回为 %s）%n",
                    t.user, t.secret, t.secret);
        }
        f.deleteOnExit();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现员工存取完整版（含日期字段与一致性校验），并演示 UID 不匹配异常。
 * 2. 实现"transient 字段的反序列化恢复"（readObject 定制），让 secret 从安全源重建。
 * 3. 思考题：为什么生产环境更推荐 JSON/Protobuf 而不是 Java 原生序列化？从安全（36 章）与跨语言两个角度分析。
 * 
 * 
 * ## 第17章 多线程
 * ============================================================
 */
