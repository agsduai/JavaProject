/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：8-09
 * 课名：面向对象小结
 * ------------------------------------------------------------
 * 知识点：
 *   - 封装完整步骤：属性 private 私有化 → 手写空参构造 + 全参构造 → 提供 getter/setter → 需要校验的逻辑写在 setter 中。
 *   - 类与对象：类是描述共同特征的模板，对象是类的具体实例；用 new 在内存中创建对象，用"对象名.成员"访问属性和方法。
 *   - this：在成员变量与局部变量重名时区分二者，this 代表当前对象。
 *   - 构造方法：与类同名、无返回值类型，new 时自动执行、用于初始化成员变量，且可以重载。
 *   - 标准 JavaBean 模板必须练到形成肌肉记忆，后续章节（继承、集合、IO）中的实体类全部沿用这套写法。
 * ============================================================
 */

package com.heima.ch08;

// ==================== 示例代码 ====================

// 本章小结模板：标准封装的完整步骤
class OopSummaryPhone {
    // 步骤1：属性私有化
    private String brand;
    private double price;

    // 步骤2：空参构造
    public OopSummaryPhone() {
    }

    // 步骤3：全参构造
    public OopSummaryPhone(String brand, double price) {
        this.brand = brand;
        this.price = price;
    }

    // 步骤4：getter/setter（需要校验的逻辑写在 setter 里）
    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        if (price >= 0) {
            this.price = price;
        } else {
            System.out.println("价格不能为负数");
        }
    }

    public void call() {
        System.out.println(brand + "手机正在打电话");
    }
}

public class OopSummary {
    public static void main(String[] args) {
        OopSummaryPhone p1 = new OopSummaryPhone("华为", 6999.0);
        p1.call();

        OopSummaryPhone p2 = new OopSummaryPhone();
        p2.setBrand("小米");
        p2.setPrice(-100); // 触发校验，赋值失败
        System.out.println(p2.getBrand() + " " + p2.getPrice());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 不看笔记，独立默写一个 Student 标准 JavaBean：私有属性 name、age、score，空参构造 + 全参构造 + 全部 getter/setter。
 * 2. 在 Student 的 setScore 中加入校验（0~100 之间），非法值打印提示且不修改，写测试类分别用合法值和非法值验证。
 * 3. 综合题：设计 Commodity（商品）类，属性 name、price、stock（库存）全部私有，要求价格必须大于等于 0、库存必须大于等于 0，并提供 purchase(int num) 方法模拟购买：库存不足时提示失败，成功则扣减库存并打印剩余库存。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 为社区图书角做一个最小领域模型：Book 与 Reader 两个类，封装字段、提供构造方法、用 this 区分同名变量，并在借书时做数据安全校验。这一题只做对象，不做界面。
 * 【任务】
 * 1. 定义 Book 类：私有字段 isbn、title、price、borrowed，提供无参构造、全参构造、getter/setter，price 为负时 set 直接拒绝并保持原值。
 * 2. 定义 Reader 类：字段 name、maxBooks、borrowedCount，提供 borrow(Book) 与 giveBack(Book) 方法，超过上限或书已被借出时给出提示且不改变状态。
 * 3. 在构造方法里用 this 区分同名字段，并说明「就近原则」在哪个位置会出错。
 * 4. 在 main 中构造 2 个读者、3 本书，跑一遍借书 → 重复借 → 超限 → 归还 → 再借的完整流程。
 * 【验收清单】
 * [ ] 所有字段 private，外部只能通过方法改状态。
 * [ ] 非法 price 赋值后原值不变，控制台有明确拒绝提示。
 * [ ] 同一本书不能被同一读者重复借出，borrowed 状态与实际一致。
 * [ ] toString() 输出包含书名与借阅状态，便于打印验证。
 * ============================================================
 */
