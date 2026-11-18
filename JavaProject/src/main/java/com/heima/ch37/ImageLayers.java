/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：37-02
 * 课名：镜像与分层存储
 * ------------------------------------------------------------
 * 知识点：
 *   - **镜像 = 只读层的叠加**：每一层记录"相对上一层的文件变化"，从基础层的发行版文件，到 JDK 层，再到应用层。运行容器时，在最上面再叠一个**可写层**。
 *   - **分层带来两个核心收益**：**共享**（多个镜像共用同一个基础层，磁盘和网络只传一份）与**缓存**（构建时父层没变、指令没变，直接复用缓存层，秒级完成）。
 *   - **写时复制（copy-up）**：容器要改一个属于只读层的文件时，先把该文件从下层完整复制到可写层再改；要删除下层文件时，在可写层放一个"白障（whiteout）标记"遮住它。所以**改动越多，可写层越胖，启动越慢**。
 *   - **层里永远只增不删**：在后面一层 `RUN rm big.zip` 并不会让镜像变小，只是加了"它不存在"的记录，前一层的文件还在。想真正变小必须把删除动作合并进产生文件的**同一层**（例如同一条 `RUN` 里下载、解压、删除）。
 *   - **Dockerfile 每条指令大致对应一层**：FROM、RUN、COPY、ADD 产生层；ENV、EXPOSE、WORKDIR、CMD、ENTRYPOINT 只改元数据。
 *   - **镜像 tag 不是版本保证**：`latest` 是可变指针，今天的 latest 和明天的不一样；生产环境应固定 digest（`app@sha256:...`）或自建带版本约定的 tag 规则。
 *   - **pull 增量传输**：本地已有的层不会重新下载，`docker pull` 输出里的 "Layer already exists" 就是分层共享的直接体现。
 *   - 易错点：`RUN apt-get update && apt-get install -y xxx && rm -rf /var/lib/apt/lists/*` 拆成三条 RUN，删除那层根本不省空间；把日志写进容器可写层越跑越大（应输出到 stdout 或挂卷）；用 `latest` 部署导致两台机器跑的不是同一个版本。
 * ============================================================
 */

package com.heima.ch37;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ImageLayers {
    record Layer(String id, String desc, int sizeMb, String createdBy) {
    }

    record FileEntry(String path, String fromLayer, boolean whiteout) {
    }

    /** 模拟一个镜像的层栈：底层是系统，往上是运行时和应用 */
    static final List<Layer> IMAGE_APP = List.of(
            new Layer("a1f3", "基础发行版 /bin /lib /etc", 72, "FROM debian:12-slim"),
            new Layer("b7c2", "JRE 与字体、时区", 180, "RUN apt-get install jre"),
            new Layer("c9d4", "应用 jar 与启动脚本", 65, "COPY target/app.jar"));

    static void layerStack() {
        System.out.println("【镜像 app:1.0 的三层结构】");
        int total = 0;
        for (Layer l : IMAGE_APP) {
            total += l.sizeMb();
            System.out.printf("  层 %-4s %4dMB  %-24s <- %s%n",
                    l.id(), l.sizeMb(), l.desc(), l.createdBy());
        }
        System.out.printf("  镜像总大小 %dMB = 各层之和；每层只记录相对变化%n%n", total);
    }

    static void shareLayers() {
        System.out.println("【两个镜像共享基础层：pull 只传缺失的层】");
        List<Layer> imageV2 = List.of(
                IMAGE_APP.get(0), IMAGE_APP.get(1),
                new Layer("e2f8", "应用 jar（新版本）", 68, "COPY target/app.jar"));
        Map<String, Integer> localLayers = new LinkedHashMap<>();
        for (Layer l : IMAGE_APP) {
            localLayers.put(l.id(), l.sizeMb());
        }
        int downloaded = 0;
        System.out.println("  pull app:2.0（本地已有 app:1.0）");
        for (Layer l : imageV2) {
            if (localLayers.containsKey(l.id())) {
                System.out.printf("  层 %-4s already exists（跳过）%n", l.id());
            } else {
                downloaded += l.sizeMb();
                System.out.printf("  层 %-4s 下载 %dMB%n", l.id(), l.sizeMb());
            }
        }
        System.out.printf("  实际下载 %dMB，而不是整个镜像的 %dMB%n%n",
                downloaded, imageV2.stream().mapToInt(Layer::sizeMb).sum());
    }

    static void copyOnWrite() {
        System.out.println("【写时复制：容器改文件、删文件时发生了什么】");
        // 可写层初始为空
        List<FileEntry> writable = new ArrayList<>();
        List<FileEntry> lower = List.of(
                new FileEntry("/app/config.yml", "c9d4", false),
                new FileEntry("/app/old-lib.jar", "b7c2", false),
                new FileEntry("/tmp/cache.dat", "c9d4", false));
        System.out.println("  1) 容器 echo 修改 /app/config.yml：");
        writable.add(new FileEntry("/app/config.yml", "rw层(copy-up 自 c9d4)", false));
        showView(lower, writable);
        System.out.println("  2) 容器 rm /app/old-lib.jar：下层文件还在，只是被白障遮住");
        writable.add(new FileEntry("/app/old-lib.jar", "rw层", true));
        showView(lower, writable);
        System.out.println("  3) 结论：删文件不释放镜像空间；频繁写入让可写层变厚");
        System.out.println();
    }

    private static void showView(List<FileEntry> lower, List<FileEntry> writable) {
        System.out.println("     联合挂载后的最终视图：");
        List<String> seen = new ArrayList<>();
        for (FileEntry w : writable) {
            seen.add(w.path());
            System.out.printf("       %-18s 来自 %s%s%n", w.path(), w.fromLayer(),
                    w.whiteout() ? "（已被删除）" : "（容器修改版）");
        }
        for (FileEntry f : lower) {
            if (seen.contains(f.path())) {
                continue;
            }
            System.out.printf("       %-18s 来自 %s%n", f.path(), f.fromLayer());
        }
    }

    static void layersNeverShrink() {
        System.out.println("【rm 放进独立一层，镜像反而不会变小】");
        int base = 72, download = 120, jre = 180;
        int wrong = base + download + jre + 0;
        System.out.printf("  写法A（错误）：RUN curl 下载%dMB -> 新层；再 RUN rm -> 又一层%n", download);
        System.out.printf("    实际占用 %dMB：删除层只是加了白障，下载层仍在%n", wrong);
        int right = base + jre;
        System.out.printf("  写法B（正确）：RUN curl ... && rm -f tmp.zip 合并成一条指令%n");
        System.out.printf("    实际占用 %dMB：中间文件从未进入任何层%n", right);
        System.out.println("  口诀：产生临时文件的指令，必须与清理它的指令写在同一条 RUN 里");
        System.out.println();
    }

    public static void main(String[] args) {
        layerStack();
        shareLayers();
        copyOnWrite();
        layersNeverShrink();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在本机构建一个镜像，然后用 `docker history` 查看每一层的大小与产生指令，找出最大的一层并解释它为什么大。
 * 2. 写一个"错误 Dockerfile"（update/install/rm 拆三条 RUN）和"正确 Dockerfile"（合并成一条），分别构建，对比 `docker images` 里的体积差。
 * 3. 思考题：为什么把 `COPY . .` 放在 Dockerfile 第一行是缓存杀手？结合构建缓存的判定规则说明。
 * 4. 生产环境为什么应固定 `镜像@digest` 而不是用 `latest`？列出两个可被 latest 咬到的具体场景。
 * ============================================================
 */
