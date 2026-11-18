/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：13-10
 * 课名：游戏最终结算
 * ------------------------------------------------------------
 * 知识点：
 *   - 游戏结束的两种情况：我方全员阵亡 → 失败结算；玩家主动选择结束 → 按当前战绩结算。
 *   - 战绩数据（总轮数、击败敌人数、累计伤害）要在战斗过程中随时累加，不能等到结算时才算。
 *   - 判断全灭：遍历我方集合，所有角色血量都小于等于 0 即为失败。
 *   - 结算界面逐行打印胜负 + 各项战绩，再根据数值给一句评价，增加游戏成就感。
 *   - 收尾习惯：关闭 Scanner、打印"程序结束"，让程序有头有尾。
 * ============================================================
 */

package com.heima.ch13;

import java.util.ArrayList;
import java.util.Scanner;

// ==================== 示例代码 ====================

class FinalSettlementRole {
    private String name;
    private int hp;
    private int attack;

    public FinalSettlementRole() {
    }

    public FinalSettlementRole(String name, int hp, int attack) {
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

class FinalSettlementHero extends FinalSettlementRole {
    private int anger;

    public FinalSettlementHero() {
    }

    public FinalSettlementHero(String name, int hp, int attack, int anger) {
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

class FinalSettlementEnemy extends FinalSettlementRole {
    private int type;

    public FinalSettlementEnemy() {
    }

    public FinalSettlementEnemy(int type, String name, int hp, int attack) {
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


public class FinalSettlement {
    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        ArrayList<FinalSettlementHero> heroes = new ArrayList<>();
        heroes.add(new FinalSettlementHero("阿玮", 0, 35, 0));
        heroes.add(new FinalSettlementHero("小波", 20, 28, 0));

        // 战绩数据：真实项目中应在战斗过程中累加
        int roundCount = 3;    // 已战斗的轮数
        int killCount = 5;     // 击败的敌人数
        int totalDamage = 380; // 累计造成的伤害

        // 判断我方是否还有存活角色
        boolean allDead = true;
        for (FinalSettlementHero h : heroes) {
            if (h.getHp() > 0) {
                allDead = false;
                break;
            }
        }

        System.out.println("========= 游戏结束 =========");
        if (allDead) {
            System.out.println("我方全员阵亡，挑战失败！");
        } else {
            System.out.println("恭喜通关！你战胜了所有敌人！");
        }
        System.out.println("总轮数：" + roundCount);
        System.out.println("击败敌人：" + killCount + " 名");
        System.out.println("累计伤害：" + totalDamage);

        // 根据战绩给出一句评价
        if (totalDamage > 500) {
            System.out.println("评价：真乃一代战神！");
        } else {
            System.out.println("评价：路虽远，行则将至，再接再厉！");
        }
        sc.close();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 输出每名角色的个人战绩：造成的伤害、击杀数、当前存活状态。
 * 2. 按总伤害与击杀数综合计算评级（S、A、B、C 四档），不同评级打印不同的通关标语。
 * 3. 把每一轮的战斗记录拼接成完整战报字符串，游戏结束时逐行回放整场战斗。
 * ============================================================
 */
