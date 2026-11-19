/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-04
 * 课名：ASM：读取与修改字节码
 * ------------------------------------------------------------
 * 知识点：
 *   - **ASM 是"直接操作字节码"的底层库**（Spring/CGLIB/很多 APM 都用它）：核心是 **Visitor 模式**——`ClassReader` 逐条"读"字节码并回调 `ClassVisitor`，`ClassWriter` 逐条"写"出新字节码——**像 SAX 解析 XML 一样流式处理**（31 章访问者模式的经典应用）。
 *   - **读改写的三段式**：`ClassReader`（源）+ `ClassVisitor`（你的修改逻辑）+ `ClassWriter`（目标）——**在 `visitMethod` 里返回自定义的 `MethodVisitor`，在 `visitCode`/`visitInsn` 里插入指令**。
 *   - **插入埋点的标准姿势**：方法入口插 `System.nanoTime()` 并存入局部变量，`visitInsn` 遇到 `RETURN` 系列时插入"计算耗时 + 上报"——**修改的是指令序列，源码完全不变**。
 *   - **ASM 的代价与风险**：API 极底层（要懂 JVM 指令与栈帧）、**版本兼容**（不同 Class 版本要匹配 ASM 版本）、**改错一个偏移就 `VerifyError`**——**生产建议用更高层封装（ByteBuddy 66-05）**。
 *   - **与 60-08 的呼应**：SkyWalking Agent 的字节码增强就是"插件按框架匹配 -> ASM 改字节码 -> 插桩"——**本章把这层拆开看**。
 *   - 易错点：忘记调 `visitEnd`（字节码不完整）；`ClassWriter` 用错 COMPUTE_FRAMES（栈帧计算失败）；改字节码后没重新计算 maxStack/maxLocals（VerifyError）；ASM 版本与 Class 版本不匹配。
 * ============================================================
 */

package com.heima.ch66;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class AsmDemo {
    /** 极简指令模型：模拟 ASM 的 Visitor 流式处理 */
    record Insn(String op, String operand) {
        @Override
        public String toString() {
            return operand.isEmpty() ? op : op + " " + operand;
        }
    }

    /** ClassReader：逐条遍历指令并回调 visitor */
    static class Reader {
        final List<Insn> code;

        Reader(List<Insn> code) {
            this.code = code;
        }

        void accept(java.util.function.Consumer<Insn> visitor) {
            code.forEach(visitor);
        }
    }

    /** MethodVisitor 模拟：在入口与 return 处插桩 */
    static class TimingVisitor {
        final List<Insn> out = new ArrayList<>();
        int localVar = 1;

        void visitCode() {
            out.add(new Insn("invokestatic", "System.nanoTime"));
            out.add(new Insn("lstore", String.valueOf(localVar)));
            out.add(new Insn("// 方法开始计时", ""));
        }

        void visitInsn(Insn insn) {
            if (insn.op().endsWith("return")) {
                out.add(new Insn("ldc", "orderService.query"));
                out.add(new Insn("lload", String.valueOf(localVar)));
                out.add(new Insn("invokestatic", "System.nanoTime"));
                out.add(new Insn("lsub", ""));
                out.add(new Insn("invokestatic", "Metric.record"));
                out.add(new Insn("// 上报耗时", ""));
            }
            out.add(insn);
        }

        void add(Insn insn) {
            out.add(insn);
        }
    }

    /** 模拟对一个方法做耗时插桩 */
    static List<Insn> instrument(List<Insn> original) {
        TimingVisitor mv = new TimingVisitor();
        mv.visitCode();
        new Reader(original).accept(mv::visitInsn);
        return mv.out;
    }

    public static void main(String[] args) {
        List<Insn> original = List.of(
                new Insn("aload_0", ""),
                new Insn("invokevirtual", "OrderDao.select"),
                new Insn("areturn", ""));
        System.out.println("【原始指令序列】");
        original.forEach(i -> System.out.println("  " + i));

        System.out.println();
        System.out.println("【增强后（入口计时 + return 前上报）】");
        instrument(original).forEach(i -> System.out.println("  " + i));

        System.out.println();
        System.out.println("【关键点】");
        List<String> notes = List.of(
                "插桩位置：visitCode 之后（方法入口）、RETURN 之前",
                "局部变量编号：要避开已有变量（本例 lstore 1）",
                "改完后 ClassWriter 需重算 maxStack/maxLocals，否则 VerifyError",
                "ASM 版本要与 Class 文件版本匹配（major 65 需较新 ASM）");
        notes.forEach(n -> System.out.println("  - " + n));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展插桩：为非 void 方法与 void 方法分别插入计时（`areturn` 与 `return` 两条路径），验证两种返回都上报。
 * 2. 用真实 ASM 写一个"给所有 public 方法插入耗时统计"的 Agent，挂到示例程序上验证输出。
 * 3. 思考题：为什么插桩要考虑"局部变量编号"？从"字节码里的局部变量表槽位"与"栈帧大小计算"分析。
 * ============================================================
 */
