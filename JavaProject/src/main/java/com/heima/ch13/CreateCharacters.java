/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：13-05
 * 课名：准备工作（创建敌我双方的角色）
 * ------------------------------------------------------------
 * 知识点：
 *   - 用集合管理角色：ArrayList<Hero> 存我方出战池、ArrayList<Enemy> 存敌人池，战斗时遍历集合行动。
 *   - 敌人随机生成：名字从字符串数组按随机下标取，血量、攻击力在设定区间内随机。
 *   - 任意区间 [min, max] 的随机数公式：r.nextInt(max - min + 1) + min。
 *   - 把"创建角色"封装成方法并返回集合，main 方法里只做调用和测试打印。
 *   - 创建完立刻遍历打印集合，先验证数据正确，再进入下一阶段的开发。
 * ============================================================
 */

package com.heima.ch13;

import java.util.ArrayList;
import java.util.Random;

// ==================== 示例代码 ====================

class CreateCharactersRole {
    private String name;
    private int hp;
    private int attack;

    public CreateCharactersRole() {
    }

    public CreateCharactersRole(String name, int hp, int attack) {
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

class CreateCharactersHero extends CreateCharactersRole {
    private int anger;

    public CreateCharactersHero() {
    }

    public CreateCharactersHero(String name, int hp, int attack, int anger) {
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

class CreateCharactersEnemy extends CreateCharactersRole {
    private int type;

    public CreateCharactersEnemy() {
    }

    public CreateCharactersEnemy(int type, String name, int hp, int attack) {
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


public class CreateCharacters {
    static Random r = new Random();

    public static void main(String[] args) {
        ArrayList<CreateCharactersHero> heroes = createHeroes();
        ArrayList<CreateCharactersEnemy> enemies = createEnemies();

        System.out.println("我方角色：");
        for (CreateCharactersHero h : heroes) {
            System.out.println(h.getName() + " 血量" + h.getHp() + " 攻击" + h.getAttack());
        }
        System.out.println("敌方角色：");
        for (CreateCharactersEnemy e : enemies) {
            System.out.println(e.getName() + " 血量" + e.getHp() + " 攻击" + e.getAttack());
        }
    }

    // 创建我方出战池
    public static ArrayList<CreateCharactersHero> createHeroes() {
        ArrayList<CreateCharactersHero> list = new ArrayList<>();
        list.add(new CreateCharactersHero("阿玮", 100, 20, 0));
        list.add(new CreateCharactersHero("小波", 90, 25, 0));
        list.add(new CreateCharactersHero("皮皮", 110, 15, 0));
        return list;
    }

    // 随机创建敌方角色
    public static ArrayList<CreateCharactersEnemy> createEnemies() {
        String[] names = {"史莱姆", "哥布林", "骷髅兵", "蝙蝠怪", "石头人"};
        ArrayList<CreateCharactersEnemy> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            // 随机名字：从数组中按随机下标取
            String name = names[r.nextInt(names.length)];
            // 血量区间 [40, 80]，攻击力区间 [8, 15]
            int hp = r.nextInt(80 - 40 + 1) + 40;
            int attack = r.nextInt(15 - 8 + 1) + 8;
            list.add(new CreateCharactersEnemy(i + 1, name, hp, attack));
        }
        return list;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 敌人数量改为随机 3~5 个，并保证同一波敌人名字互不重复。
 * 2. 给 Enemy 增加难度星级（1~3 星），打印敌人信息时用 ★ 展示星级。
 * 3. 我方出战角色改为从 5 个预设名单中随机抽取 2 名，要求多次运行时抽到的组合可以不同。
 * ============================================================
 */
