/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 10-02
 * 课名：订单状态枚举与流转
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】定义 `OrderStatus` 枚举：`CREATED("已创建")`、`PAID("已支付")`、`SHIPPED("已发货")`、`FINISHED("已完成")`、`CANCELED("已取消")`，包含中文描述属性与 getter。编写 `boolean canTransfer(OrderStatus from, OrderStatus to)` 判断合法流转：创建→支付/取消；支付→发货；发货→完成；终态不可再流转。主方法测试全部合法与非法组合并输出结果。
 *   - 【考核点】enum 定义、枚举属性与构造、枚举比较用 `==`、switch 配合枚举、values/valueOf。
 *   - 【易错点】枚举构造方法默认私有，不能加 public；枚举项必须写在最前面；`==` 比 `equals` 更安全。
 *   - 【提示】可用 `switch (from)` 分支 + `switch (to)` 判断合法目标状态，或直接写 `if` 组合。
 * ============================================================
 */

package com.heima.practice.advanced;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：枚举的属性、构造与遍历
public enum OrderStatusFlow {
    SPRING("春天"), SUMMER("夏天");

    private final String desc;

    OrderStatusFlow(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public static void main(String[] args) {
        for (OrderStatusFlow s : OrderStatusFlow.values()) {
            System.out.println(s + "：" + s.getDesc());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `OrderStatus` 增加 `boolean isFinal()` 方法（完成/取消为终态），并在主方法中筛选所有终态。
 * 2. 用 switch 表达式（JDK17 箭头语法）根据状态输出下一步操作提示。
 * 3. 给定一个订单状态数组（含重复），统计各状态出现次数并按次数从高到低输出。
 * 
 * 
 * > 完成本册后进入《第11-14章-综合实操.md》。原理题务必动手画内存图，算法题务必自行测试边界数据。
 * ============================================================
 */
