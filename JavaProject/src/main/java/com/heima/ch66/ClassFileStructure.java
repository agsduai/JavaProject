/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-03
 * 课名：字节码基础：Class 文件结构
 * ------------------------------------------------------------
 * 知识点：
 *   - **Class 文件是 JVM 的"可执行格式"**：固定以魔数 `0xCAFEBABE` 开头，随后是版本号、常量池、访问标志、字段表、方法表、属性表——**一切 Java 代码（含 Kotlin/Scala）最终都是这个结构**。
 *   - **文件头的关键字段**：`magic`（4 字节，0xCAFEBABE，用于快速校验）、`minor_version`/`major_version`（**major 决定 JDK 版本兼容**：52=Java 8、61=Java 17、65=Java 21）、`constant_pool_count`（常量池条目数）、`constant_pool`（字符串/类名/方法签名的去重表）。
 *   - **方法表的结构**：`access_flags`（public/static 等）、`name_index`、`descriptor_index`（描述符如 `(I)Ljava/lang/String;`）、`attributes`（**其中 `Code` 属性里有真正的指令序列**）——**字节码增强改的就是 Code 属性里的指令**。
 *   - **栈帧与指令**：JVM 是基于栈的虚拟机，指令如 `aload_0`（压入 this）、`invokevirtual`（调用虚方法）、`ireturn`（返回 int）——**插桩就是在这串指令里"插入新指令"**。
 *   - **版本兼容的实践意义**：**Agent 用高版本 JDK 编译，被挂载的应用用低版本运行会 `UnsupportedClassVersionError`**——**Agent 要用目标应用的最低版本编译**（或提供多版本）。
 *   - 易错点：以为改源码就能改行为（运行时是字节码）；Agent 与目标应用 JDK 版本不匹配；混淆过的类（方法名被改）导致切点匹配失败；忽略常量池的索引引用（插指令要改索引）。
 * ============================================================
 */

package com.heima.ch66;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ClassFileStructure {
    record ClassHeader(int magic, int minor, int major, int constantPoolCount) {
        String jdkVersion() {
            return switch (major) {
                case 52 -> "Java 8";
                case 55 -> "Java 11";
                case 61 -> "Java 17";
                case 65 -> "Java 21";
                case 69 -> "Java 25";
                default -> "未知（major=" + major + "）";
            };
        }
    }

    /** 解析 Class 文件头：魔数 + 版本 + 常量池计数 */
    static ClassHeader parseHeader(byte[] bytes) {
        int magic = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
        int minor = ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF);
        int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        int cpCount = ((bytes[8] & 0xFF) << 8) | (bytes[9] & 0xFF);
        return new ClassHeader(magic, minor, major, cpCount);
    }

    static void inspectOwnClass() throws Exception {
        System.out.println("【解析本类自身的 Class 文件】");
        String resource = ClassFileStructure.class.getSimpleName() + ".class";
        try (InputStream in = ClassFileStructure.class.getResourceAsStream(resource)) {
            if (in == null) {
                System.out.println("  未找到 class 资源（可能运行在特殊环境）");
                return;
            }
            byte[] bytes = in.readAllBytes();
            ClassHeader h = parseHeader(bytes);
            System.out.printf("  文件大小=%d 字节%n", bytes.length);
            System.out.printf("  magic=0x%08X（期望 0xCAFEBABE）-> %s%n", h.magic(),
                    h.magic() == 0xCAFEBABE ? "合法" : "非法");
            System.out.printf("  版本=%d.%d -> %s%n", h.major(), h.minor(), h.jdkVersion());
            System.out.printf("  常量池条目数=%d%n", h.constantPoolCount());
        }
        System.out.println();
    }

    static void inspectFake() {
        System.out.println("【非法/异常 Class 文件识别】");
        List<byte[]> samples = new ArrayList<>();
        samples.add(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 65, 0, 20});
        samples.add(new byte[]{0x7F, (byte) 0x45, 0x4C, 0x46, 0, 0, 0, 65, 0, 20});
        for (byte[] s : samples) {
            ClassHeader h = parseHeader(s);
            System.out.printf("  magic=0x%08X -> %s%n", h.magic(),
                    h.magic() == 0xCAFEBABE ? "Class 文件（版本 " + h.jdkVersion() + "）"
                            : "非 Class 文件（拒绝加载）");
        }
        System.out.println();
        System.out.println("  用途：Agent 在 transform 里用魔数快速判断输入是否为合法字节码");
    }

    public static void main(String[] args) throws Exception {
        inspectOwnClass();
        inspectFake();
        System.out.println("  JDK 21 编译产物 major=65；Agent 挂到 JDK 17 应用需用 17 编译");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展解析器：跳过常量池并读出 `access_flags`、方法数量与每个方法的名字（需要解析常量池的 UTF8 条目）。
 * 2. 用 `javap -v` 对照你解析出的 Class 文件头信息，验证 magic/version/常量池计数一致。
 * 3. 思考题：为什么 Class 文件要以魔数开头？从"格式快速识别"与"错误信息友好"两个角度分析。
 * ============================================================
 */
