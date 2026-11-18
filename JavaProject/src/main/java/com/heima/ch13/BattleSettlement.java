/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：13-09
 * 课名：战斗结算（恢复血量，增加属性，是否继续）
 * ------------------------------------------------------------
 * 知识点：
 *   - 一波敌人清空后进入结算，结算不等于游戏结束：回血、涨属性、询问是否继续。
 *   - 回血规则：每个存活角色回复最大血量的 20%，回血后不能超过上限，需要 if 判断截断。
 *   - 属性成长：胜利后攻击力 +5（或血量上限提升），游戏才有"越打越强"的正反馈。
 *   - 是否继续用循环 + 分支：选 1 生成新一波敌人再战，选 2 进入最终结算。
 *   - 把"一波战斗"封装成方法，用返回值把结果（如是否全灭）带回主流程，主流程才好判断走向。
 * ============================================================
 */

package com.heima.ch13;

import java.util.ArrayList;
import java.util.Scanner;

// ==================== 示例代码 ====================

class BattleSettlementRole {
    private String name;
    private int hp;
    private int attack;

    public BattleSettlementRole() {
    }

    public BattleSettlementRole(String name, int hp, int attack) {
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

class BattleSettlementHero extends BattleSettlementRole {
    private int anger;

    public BattleSettlementHero() {
    }

    public BattleSettlementHero(String name, int hp, int attack, int anger) {
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

class BattleSettlementEnemy extends BattleSettlementRole {
    private int type;

    public BattleSettlementEnemy() {
    }

    public BattleSettlementEnemy(int type, String name, int hp, int attack) {
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


public class BattleSettlement {
    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        ArrayList<BattleSettlementHero> heroes = new ArrayList<>();
        heroes.add(new BattleSettlementHero("阿玮", 60, 30, 0));
        heroes.add(new BattleSettlementHero("小波", 45, 25, 0));
        // 假设我方血量上限都是 100
        int maxHp = 100;

        // 模拟一轮战斗胜利后的结算
        for (BattleSettlementHero h : heroes) {
            // 恢复血量：回复最大血量的 20%，但不能超过上限
            int heal = maxHp * 20 / 100;
            if (h.getHp() + heal > maxHp) {
                h.setHp(maxHp);
            } else {
                h.setHp(h.getHp() + heal);
            }
            // 增加属性：攻击力 +5
            h.setAttack(h.getAttack() + 5);
            System.out.println(h.getName() + " 恢复到 " + h.getHp()
                    + " 点血量，攻击力提升到 " + h.getAttack());
        }

        // 询问玩家是否继续战斗
        while (true) {
            System.out.println("是否继续挑战下一波敌人？1. 继续 2. 结束");
            String choice = sc.next();
            if ("1".equals(choice)) {
                System.out.println("准备生成下一波敌人...");
                break;
            } else if ("2".equals(choice)) {
                System.out.println("结束本次冒险");
                break;
            } else {
                System.out.println("输入有误，请重新选择");
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 结算时统计本轮每个角色受到的总伤害，并按伤害发放金币奖励，金币数存入 Hero 类。
 * 2. 回血比例与连胜场次挂钩：每连胜一场，回血比例提升 5%。
 * 3. 结算界面增加撤退选项：撤退保留当前属性但连胜清零，然后返回主菜单。
 * ============================================================
 */
