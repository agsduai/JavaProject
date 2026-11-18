/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：13-07
 * 课名：战斗中（我方回合）
 * ------------------------------------------------------------
 * 知识点：
 *   - 回合制的最小闭环：我方行动 → 敌方行动 → 判断胜负 → 进入下一回合。
 *   - 伤害计算：基础伤害 = 攻击力，再加随机波动（如 ±5），最低保底 1 点，避免出现 0 伤害。
 *   - 暴击：随机一个 0~99 的数，小于暴击率（如 30）判定暴击，伤害翻倍。
 *   - 敌人掉血调用父类封装好的 hurt 方法；血量小于等于 0 就从敌人集合中移除，表示阵亡。
 *   - 每次攻击都打印战斗日志（谁对谁造成多少伤害、剩余血量），回合制游戏的体验感靠日志撑起来。
 * ============================================================
 */

package com.heima.ch13;

import java.util.ArrayList;
import java.util.Random;

// ==================== 示例代码 ====================

class HeroTurnRole {
    private String name;
    private int hp;
    private int attack;

    public HeroTurnRole() {
    }

    public HeroTurnRole(String name, int hp, int attack) {
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

class HeroTurnHero extends HeroTurnRole {
    private int anger;

    public HeroTurnHero() {
    }

    public HeroTurnHero(String name, int hp, int attack, int anger) {
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

class HeroTurnEnemy extends HeroTurnRole {
    private int type;

    public HeroTurnEnemy() {
    }

    public HeroTurnEnemy(int type, String name, int hp, int attack) {
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


public class HeroTurn {
    static Random r = new Random();

    public static void main(String[] args) {
        HeroTurnHero hero = new HeroTurnHero("阿玮", 100, 30, 0);
        ArrayList<HeroTurnEnemy> enemies = new ArrayList<>();
        enemies.add(new HeroTurnEnemy(1, "史莱姆", 80, 10));
        enemies.add(new HeroTurnEnemy(2, "哥布林", 50, 12));

        // 我方回合：攻击第一个存活的敌人
        HeroTurnEnemy target = enemies.get(0);
        attack(hero, target);

        // 敌人血量为 0 时移出集合
        if (target.getHp() <= 0) {
            System.out.println(target.getName() + " 被击败了！");
            enemies.remove(target);
        }
        System.out.println("剩余敌人数：" + enemies.size());
    }

    // 我方攻击逻辑：计算伤害、判定暴击、敌方掉血
    public static void attack(HeroTurnHero hero, HeroTurnEnemy enemy) {
        int damage = hero.getAttack();
        // 暴击判定：0~99 的随机数小于 30 视为暴击（30% 概率），伤害翻倍
        boolean isCrit = r.nextInt(100) < 30;
        if (isCrit) {
            damage *= 2;
        }
        // 伤害在 ±5 内随机波动
        damage += r.nextInt(11) - 5;
        if (damage < 1) {
            damage = 1;
        }
        // 敌方掉血
        enemy.hurt(damage);
        if (isCrit) {
            System.out.println(hero.getName() + " 打出了暴击！对 "
                    + enemy.getName() + " 造成 " + damage + " 点伤害");
        } else {
            System.out.println(hero.getName() + " 对 " + enemy.getName()
                    + " 造成 " + damage + " 点伤害");
        }
        System.out.println(enemy.getName() + " 剩余血量：" + enemy.getHp());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 我方多名角色依次行动，每名角色由玩家选择攻击目标，也可以选择跳过本回合。
 * 2. 增加连击机制：20% 概率追加一次半价伤害的攻击，并把每次攻击记录成战斗日志字符串。
 * 3. 暴击伤害改为攻击力的 1.5~2.5 倍之间的随机值（伤害取整数），并统计本回合的总输出伤害。
 * ============================================================
 */
