/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-06
 * 课名：数据卷与持久化
 * ------------------------------------------------------------
 * 知识点：
 *   - **容器可写层是临时的**：`docker rm` 即销毁，进程写入的可写层数据全部丢失。凡是要"留下来"的数据（数据库文件、上传文件、日志归档），必须放在**卷（volume）**上。
 *   - **三种挂载方式**：`volume`（`-v mydata:/var/lib/mysql`，由 Docker 统一管理，位置与驱动与宿主机无关，**首选**）；`bind mount`（`-v /data/app:/app/data`，宿主机目录直接映射，适合配置文件与本地开发热更新）；`tmpfs`（内存挂载，容器停止即消失，适合临时敏感文件）。
 *   - **卷的生命周期独立于容器**：删容器卷还在，`docker volume ls` 可见；卷直到被 `docker volume rm` / `prune` 才删除。备份卷的标准姿势是起一个临时容器挂同一卷做 tar。
 *   - **具名卷 vs 匿名卷**：`-v mydata:/path` 是具名卷（可管理可复用）；`-v /path` 是匿名卷（一串随机 ID，容易堆积），规范要求永远给卷命名。
 *   - **bind mount 会"遮住"镜像内容**：挂载一个空宿主机目录到 `/app/conf`，镜像里该目录的默认配置文件在容器里就"消失"了。首次挂空目录想要"以镜像内容初始化"，用具名卷而不是 bind mount。
 *   - **挂载方向与权限**：容器内进程通常以非 root 跑，bind mount 的宿主机目录属主要对齐 UID，否则读写失败；Linux 上还会遇到 SELinux/AppArmor 标签问题。
 *   - **数据库容器的黄金法则**：数据目录必须挂卷，且卷随数据库版本一起升级（不要两个版本共用一个卷直接升级）；有主从时每副本一个独立卷。
 *   - 易错点：测试环境跑得好好的，删容器重建数据全丢（没挂卷）；挂了卷却挂在错误路径（数据库实际写 `/var/lib/postgresql/data` 而你挂的是 `/data`）；bind mount 空目录盖住镜像配置，应用"莫名"用默认配置跑起来；卷堆积几百个匿名卷从不清理，磁盘悄悄爆满。
 * ============================================================
 */

package com.heima.ch37;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DockerVolume {
    record Mount(String type, String spec, String persists, String useCase) {
    }

    static void mountTypes() {
        System.out.println("【三种挂载方式对比】");
        List<Mount> mounts = List.of(
                new Mount("volume", "-v pgdata:/var/lib/postgresql/data",
                        "Docker 管理，删容器仍在", "数据库等有状态服务（首选）"),
                new Mount("bind", "-v /home/me/app/conf:/app/conf",
                        "宿主机目录，人可直接编辑", "配置文件、本地开发热更新"),
                new Mount("tmpfs", "--mount type=tmpfs,dst=/tmp/secret",
                        "内存，容器停止即消失", "临时令牌、加密密钥"));
        for (Mount m : mounts) {
            System.out.printf("  %-6s %-40s%n      持久性：%-22s 用途：%s%n",
                    m.type(), m.spec(), m.persists(), m.useCase());
        }
        System.out.println();
    }

    static void containerLifecycleVsVolume() {
        System.out.println("【容器会死，卷不会：一次删除重建实验】");
        Map<String, String> volume = new LinkedHashMap<>();
        volume.put("pgdata/orders.tbl", "10000 行");
        System.out.println("  容器 app-a（挂卷 pgdata）写入：" + volume);
        System.out.println("  docker rm -f app-a          -> 可写层销毁，卷 pgdata 保留");
        System.out.println("  docker run -v pgdata:... app-b -> 重新挂上同一个卷");
        System.out.println("  app-b 读到：" + volume + "（数据完好）");
        System.out.println("  对照组：没挂卷的容器 rm 后，写入的 10000 行全部丢失");
        System.out.println();
    }

    static void bindMountShadow() {
        System.out.println("【bind mount 遮蔽效应：配置文件“消失”事故】");
        System.out.println("  镜像内 /app/conf/application.yml（内置默认配置）");
        System.out.println("  宿主机 /home/me/conf/ 是空目录");
        System.out.println("  运行：-v /home/me/conf:/app/conf");
        System.out.println("  容器内视图：/app/conf 为空 —— 镜像里的默认配置被宿主机空目录盖住");
        System.out.println("  现象：应用“随机”回到默认端口/默认密码，其实是读不到配置");
        System.out.println("  对策：配置目录挂载前先放好文件，或用具名卷做首次初始化");
        System.out.println();
    }

    static void volumeOps() {
        System.out.println("【卷的日常运维：备份与清理】");
        System.out.println("  备份 pgdata 卷（标准三行）：");
        System.out.println("    docker run --rm -v pgdata:/data -v $(pwd):/backup alpine \\");
        System.out.println("      tar czf /backup/pgdata.tgz -C /data .");
        System.out.println("  查看堆积：docker volume ls | wc -l");
        System.out.println("  清理无主卷：docker volume prune（先确认没有要保留的匿名卷！）");
        System.out.println("  磁盘排查：docker system df 看卷/镜像/容器分别占了多少");
        System.out.println();
    }

    static void statefulChecklist() {
        System.out.println("【有状态服务上线清单】");
        List<String> checks = List.of(
                "数据目录确认：镜像文档里的真实数据路径，而不是想当然的 /data",
                "具名卷命名：服务名-用途，如 mysql-data、es-data，禁止匿名卷",
                "UID/GID 对齐：bind mount 时容器进程与宿主机目录属主一致",
                "备份演练：卷备份脚本真实跑一次并验证可恢复",
                "容量规划：docker system df + 宿主机磁盘告警双保险");
        for (String c : checks) {
            System.out.println("  - " + c);
        }
    }

    public static void main(String[] args) {
        mountTypes();
        containerLifecycleVsVolume();
        bindMountShadow();
        volumeOps();
        statefulChecklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 跑一个 MySQL/Postgres 容器写入测试表，`docker rm -f` 后用同一个具名卷重建容器，验证数据还在；再跑一个不挂卷的对照组，记录差异。
 * 2. 复现"bind mount 空目录遮蔽配置"事故：挂一个空目录到 nginx 的 `/usr/share/nginx/html`，观察默认首页消失，然后给出两种修复方案。
 * 3. 写一个卷备份脚本（临时容器 + tar），并在另一台目录恢复测试，输出恢复后文件列表作为证据。
 * 4. 思考题：数据库容器的数据目录为什么推荐 volume 而不是 bind mount？至少给出两个可维护性理由。
 * ============================================================
 */
