/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：13-04
 * 课名：准备工作（编写游戏角色的继承结构）
 * ------------------------------------------------------------
 * 知识点：
 *   - 战斗角色之间有大量公共内容（名字、血量、攻击力），把公共属性和公共方法抽取到父类 Role，避免重复代码。
 *   - 继承语法：`class Hero extends Role`，子类可以直接使用父类的非私有成员，私有属性通过 getter/setter 访问。
 *   - 子类构造方法里用 super(...) 调用父类构造，先完成公共属性赋值，再给子类独有属性赋值。
 *   - 公共行为封装在父类：如 hurt(伤害) 负责扣血并保底为 0，我方和敌方通用。
 *   - 设计思想：先找同类事物的共性抽父类，再让子类按需扩展自己的独有属性和方法。
 * ============================================================
 */

package com.heima.ch13;

// ==================== 示例代码 ====================

class GameRoleStructureRole {
    private String name; // 名字
    private int hp;      // 血量
    private int attack;  // 攻击力

    public GameRoleStructureRole() {
    }

    public GameRoleStructureRole(String name, int hp, int attack) {
        this.name = name;
        this.hp = hp;
        this.attack = attack;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getAttack() {
        return attack;
    }

    public void setAttack(int attack) {
        this.attack = attack;
    }

    // 公共行为：受击扣血，血量最低降到 0
    public void hurt(int damage) {
        this.hp -= damage;
        if (this.hp < 0) {
            this.hp = 0;
        }
    }
}

class GameRoleStructureHero extends GameRoleStructureRole {
    private int anger; // 我方独有属性：怒气值（供后续扩展技能使用）

    public GameRoleStructureHero() {
    }

    public GameRoleStructureHero(String name, int hp, int attack, int anger) {
        // super 调用父类构造，完成公共属性赋值
        super(name, hp, attack);
        this.anger = anger;
    }

    public int getAnger() {
        return anger;
    }

    public void setAnger(int anger) {
        this.anger = anger;
    }
}

class GameRoleStructureEnemy extends GameRoleStructureRole {
    private int type; // 敌人独有属性：类型编号（1 小怪 2 精英怪）

    public GameRoleStructureEnemy() {
    }

    public GameRoleStructureEnemy(int type, String name, int hp, int attack) {
        super(name, hp, attack);
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}

public class GameRoleStructure {
    public static void main(String[] args) {
        // 父类引用指向子类对象，公共属性和方法直接可用
        GameRoleStructureRole hero = new GameRoleStructureHero("阿玮", 100, 20, 0);
        GameRoleStructureRole enemy = new GameRoleStructureEnemy(1, "小怪", 50, 10);
        System.out.println(hero.getName() + " 血量：" + hero.getHp());
        System.out.println(enemy.getName() + " 血量：" + enemy.getHp());

        // 敌人受到我方一次攻击，调用父类封装好的 hurt 方法
        enemy.hurt(hero.getAttack());
        System.out.println(enemy.getName() + " 剩余血量：" + enemy.getHp());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 Role 增加防御力（defense）属性，受击时实际掉血 = 伤害 - 防御力，最低掉 1 点血。
 * 2. 给 Hero 增加 releaseSkill 方法：造成 2 倍攻击力的伤害并消耗 50 点怒气，怒气不足时提示无法释放。
 * 3. 新增 Boss 类继承 Enemy，增加"每回合吸血 5 点"的特性，并编写测试代码验证。
 * ============================================================
 */
