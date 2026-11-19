/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-05
 * 课名：记录模式与嵌套解构
 * ------------------------------------------------------------
 * 知识点：
 *   - **记录模式（Java 21 定稿）把"判断类型 + 取字段"合并为一次解构**：`case Point(int x, int y) -> ...`——**直接拿到组件值**，无需 `p.x()`。嵌套时层层展开：`case Line(Point(var x1, var y1), Point(var x2, var y2))`。
 *   - **解构的语义与限制**：记录模式按**组件声明顺序**匹配（位置模式），类型不匹配则不匹配；配合 sealed 可实现**结构化数据的穷举处理**（表达式树、AST、JSON、坐标几何）。
 *   - **与守卫组合**：`case Circle(Point(int x, int y), int r) when r > 10 -> ...`——**结构 + 条件**一次表达。
 *   - **典型应用：表达式求值器**——`sealed interface Expr permits Num, Add, Mul, Neg`，`eval` 用嵌套记录模式递归求值，代码量比 visitor 模式少一半且无遗漏风险（31 章 visitor 的现代替代）。
 *   - **适用边界**：记录模式适合"结构固定且封闭"的数据（sealed + record）；**开放结构（可能新增字段）不适合**——加字段会改所有解构点（编译器帮你发现，但改动面大）。
 *   - 易错点：组件顺序记错（按位置匹配，顺序错了类型对不上就 silently 不匹配）；与 var 混用时类型推断混乱（`Point(var x, var y)` 最清晰）；解构后忘记处理 null 组件。
 * ============================================================
 */

package com.heima.ch57;

// ==================== 示例代码 ====================

public class RecordPatternDemo {
    sealed interface Expr permits Num, Add, Mul, Neg {
    }

    record Num(int value) implements Expr {
    }

    record Add(Expr left, Expr right) implements Expr {
    }

    record Mul(Expr left, Expr right) implements Expr {
    }

    record Neg(Expr inner) implements Expr {
    }

    /** 嵌套记录模式：一行解构出操作数与运算符 */
    static int eval(Expr e) {
        return switch (e) {
            case Num(int v) -> v;
            case Add(Expr l, Expr r) -> eval(l) + eval(r);
            case Mul(Expr l, Expr r) -> eval(l) * eval(r);
            case Neg(Expr inner) -> -eval(inner);
        };
    }

    /** 结构 + 守卫：识别 "加法的右操作数为负数" 这种模式 */
    static boolean hasNegativeAddend(Expr e) {
        return switch (e) {
            case Add(Expr l, Neg(Num(int v))) -> v > 0;
            case Add(Expr l, Expr r) -> hasNegativeAddend(r);
            default -> false;
        };
    }

    public static void main(String[] args) {
        Expr e = new Mul(new Add(new Num(2), new Num(3)), new Neg(new Num(4)));
        System.out.println("  表达式求值：(2+3) * -(4) = " + eval(e));
        System.out.println("  含负加数：" + hasNegativeAddend(new Add(new Num(1), new Neg(new Num(8)))));
        System.out.println("  对比 visitor 模式：无需 4 个 visit 方法与接口改动");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整表达式求值器（含 `Div` 与除零保护），用嵌套记录模式写出 `eval` 并覆盖 6 个测试表达式。
 * 2. 实现 `record Line(Point a, Point b)` 与 `record Point(int x, int y)`，用嵌套模式判断"两条线是否共端点"。
 * 3. 思考题：记录模式与 visitor 模式的取舍是什么？从"新增类型"与"新增操作"两个维度的改动成本对比分析。
 * ============================================================
 */
