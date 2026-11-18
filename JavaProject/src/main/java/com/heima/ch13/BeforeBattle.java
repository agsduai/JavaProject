/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：13-06
 * 课名：战斗前（增强属性，选择敌人，打印血条）
 * ------------------------------------------------------------
 * 知识点：
 *   - 战前增强：给每个我方角色随机提升攻击力或血量，本质是 get 出旧值、算好新值再 set 回去。
 *   - 选择敌人：打印敌人编号列表，让玩家录入编号，先校验合法性再锁定出战目标。
 *   - 血条：用循环把 '█' 拼接若干次得到字符串，填充格数 = 当前血量 / 最大血量 × 总格数。
 *   - 血条后面跟上"当前血量/最大血量"的数值，信息更直观。
 *   - System.out.printf 配合 %s、%d 占位符排版，可以让多行信息上下对齐。
 * ============================================================
 */

package com.heima.ch13;

import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

// ==================== 示例代码 ====================

class BeforeBattleRole {
    private String name;
    private int hp;
    private int attack;

    public BeforeBattleRole() {
    }

    public BeforeBattleRole(String name, int hp, int attack) {
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

class BeforeBattleHero extends BeforeBattleRole {
    private int anger;

    public BeforeBattleHero() {
    }

    public BeforeBattleHero(String name, int hp, int attack, int anger) {
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

class BeforeBattleEnemy extends BeforeBattleRole {
    private int type;

    public BeforeBattleEnemy() {
    }

    public BeforeBattleEnemy(int type, String name, int hp, int attack) {
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


public class BeforeBattle {
    static Scanner sc = new Scanner(System.in);
    static Random r = new Random();

    public static void main(String[] args) {
        ArrayList<BeforeBattleHero> heroes = new ArrayList<>();
        heroes.add(new BeforeBattleHero("阿玮", 100, 20, 0));
        heroes.add(new BeforeBattleHero("小波", 90, 25, 0));
        ArrayList<BeforeBattleEnemy> enemies = new ArrayList<>();
        enemies.add(new BeforeBattleEnemy(1, "史莱姆", 60, 10));
        enemies.add(new BeforeBattleEnemy(2, "哥布林", 80, 12));

        // 战前增强：每个角色随机提升攻击力或血量
        for (BeforeBattleHero h : heroes) {
            if (r.nextBoolean()) {
                int buff = r.nextInt(10) + 5;
                h.setAttack(h.getAttack() + buff);
                System.out.println(h.getName() + " 攻击力提升了 " + buff);
            } else {
                int buff = r.nextInt(30) + 10;
                h.setHp(h.getHp() + buff);
                System.out.println(h.getName() + " 血量提升了 " + buff);
            }
        }

        // 打印双方血条（我方血量上限假设为 150）
        System.out.println("===== 战斗前状态 =====");
        for (BeforeBattleHero h : heroes) {
            System.out.printf("%-6s %s %d/%d%n", h.getName(),
                    getHpBar(h.getHp(), 150, 10), h.getHp(), 150);
        }
        for (BeforeBattleEnemy e : enemies) {
            System.out.printf("%-6s %s %d/%d%n", e.getName(),
                    getHpBar(e.getHp(), e.getHp(), 10), e.getHp(), e.getHp());
        }
    }

    // 根据当前血量和最大血量生成血条字符串
    public static String getHpBar(int cur, int max, int cells) {
        int fill = cur * cells / max;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fill; i++) {
            sb.append('█');
        }
        // 用空格补齐剩余格子，保证各行血条宽度一致
        for (int i = fill; i < cells; i++) {
            sb.append(' ');
        }
        return "[" + sb + "]";
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 根据血量百分比在血条后追加状态文字：高于 60% 显示"良好"，30%~60% 显示"受伤"，低于 30% 显示"危险"。
 * 2. 选择敌人时做好校验：编号不存在时提示重新选择，录入非数字时程序不能崩溃。
 * 3. 战前增强改为玩家手动选择：打印选项让玩家决定每个角色是提升攻击力还是提升血量。
 * ============================================================
 */
