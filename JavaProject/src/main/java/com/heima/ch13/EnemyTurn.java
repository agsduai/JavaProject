/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：13-08
 * 课名：战斗中（敌方回合）
 * ------------------------------------------------------------
 * 知识点：
 *   - 敌方回合：遍历敌人集合，每个存活的敌人随机攻击一名我方角色。
 *   - 随机目标：r.nextInt(heroes.size()) 得到下标，注意先保证我方集合不为空。
 *   - 敌人行动前先判断自己是否存活（可能上一手已被打死），用 continue 跳过阵亡敌人。
 *   - 敌方伤害逻辑可以与我方对称：随机波动 ±2、保底 1 点，需要暴击时复用同样套路。
 *   - 我方角色被打死后要从集合移除或做标记，否则后续回合还会被敌人选中攻击。
 * ============================================================
 */

package com.heima.ch13;

import java.util.ArrayList;
import java.util.Random;

// ==================== 示例代码 ====================

class EnemyTurnRole {
    private String name;
    private int hp;
    private int attack;

    public EnemyTurnRole() {
    }

    public EnemyTurnRole(String name, int hp, int attack) {
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

class EnemyTurnHero extends EnemyTurnRole {
    private int anger;

    public EnemyTurnHero() {
    }

    public EnemyTurnHero(String name, int hp, int attack, int anger) {
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

class EnemyTurnEnemy extends EnemyTurnRole {
    private int type;

    public EnemyTurnEnemy() {
    }

    public EnemyTurnEnemy(int type, String name, int hp, int attack) {
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


public class EnemyTurn {
    static Random r = new Random();

    public static void main(String[] args) {
        ArrayList<EnemyTurnHero> heroes = new ArrayList<>();
        heroes.add(new EnemyTurnHero("阿玮", 100, 30, 0));
        heroes.add(new EnemyTurnHero("小波", 80, 25, 0));

        ArrayList<EnemyTurnEnemy> enemies = new ArrayList<>();
        enemies.add(new EnemyTurnEnemy(1, "史莱姆", 60, 15));
        enemies.add(new EnemyTurnEnemy(2, "哥布林", 70, 12));

        // 敌方回合：每个存活的敌人随机攻击一名我方角色
        for (int i = 0; i < enemies.size(); i++) {
            EnemyTurnEnemy enemy = enemies.get(i);
            // 阵亡的敌人跳过行动
            if (enemy.getHp() <= 0) {
                continue;
            }
            // 随机挑选一名我方角色作为目标
            EnemyTurnHero target = heroes.get(r.nextInt(heroes.size()));
            int damage = enemy.getAttack() + r.nextInt(6) - 2;
            if (damage < 1) {
                damage = 1;
            }
            target.hurt(damage);
            System.out.println(enemy.getName() + " 攻击了 " + target.getName()
                    + "，造成 " + damage + " 点伤害，"
                    + target.getName() + " 剩余血量 " + target.getHp());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 敌人增加集火策略：30% 概率所有敌人攻击同一个我方目标。
 * 2. 敌方 Boss 每回合自动回复 5 点血量，并打印回复日志。
 * 3. 已阵亡的我方角色不能再被敌人选中；当全员阵亡时直接跳转到失败结算。
 * ============================================================
 */
