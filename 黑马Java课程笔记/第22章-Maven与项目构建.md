# 第22章 Maven与项目构建

学完 JDBC，代码已经能连数据库了，但工程里还散落着几十个手写的 `javac` 命令和一堆来源不明的 jar 包。本章把"一个人写 demo"升级成"一个团队做工程"：先弄清 Maven 到底解决了什么问题，再依次掌握坐标、仓库、依赖范围、依赖冲突调解、生命周期与多模块聚合，最后亲手打一个可执行 jar。这一章的示例代码用纯 JDK 模拟 Maven 的核心机制，不依赖任何外部 jar，但每个概念都对应真实 `pom.xml` 里的写法。

### 22-01 为什么需要 Maven

**知识点**
- 没有构建工具时，一个项目要手工下载 jar、手工拼 `-classpath`、手工 `javac`、手工打 jar；换台机器或换个同事，全部要重来一遍。
- Maven 是 Apache 的项目管理与构建工具，用一份 `pom.xml`（Project Object Model）描述"项目是谁、依赖什么、怎么构建"。
- Maven 的三大能力：**依赖管理**（自动下载 jar 及其传递依赖）、**标准化构建**（一套命令完成编译/测试/打包/安装）、**统一目录结构**（约定优于配置）。
- **坐标**唯一标识一个构件：`groupId`（组织标识，通常域名反写）、`artifactId`（工程名）、`version`（版本号），合称 GAV。
- 仓库分三级：本地仓库（默认 `~/.m2/repository`）→ 私服（公司内网 Nexus）→ 中央仓库。查找顺序是本地 → 私服 → 中央。
- 易错：`groupId` 是组织标识不是 Java 包名，`artifactId` 才是工程名；同一个 GAV 在仓库里唯一，不存在"同版本不同内容"的两个 jar。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class MavenCoordinate {
    public static void main(String[] args) {
        String[] coords = {
                "org.springframework.boot:spring-boot-starter-web:3.3.2",
                "com.mysql:mysql-connector-j:8.4.0",
                "org.mybatis:mybatis:3.5.16",
                "com.heima:demo"    // 只写了两段，坐标不完整
        };
        List<String> valid = new ArrayList<>();
        for (String coord : coords) {
            String[] parts = coord.split(":");
            if (parts.length != 3) {
                System.out.println("[不合法] " + coord);
                System.out.println("         坐标必须由 groupId:artifactId:version 三段组成");
                System.out.println();
                continue;
            }
            String groupId = parts[0];
            String artifactId = parts[1];
            String version = parts[2];
            // 构件在仓库里的存放路径完全由坐标推导出来
            String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version
                    + "/" + artifactId + "-" + version + ".jar";
            System.out.println("groupId    = " + groupId);
            System.out.println("artifactId = " + artifactId);
            System.out.println("version    = " + version);
            System.out.println("仓库路径   = " + path);
            System.out.println();
            valid.add(artifactId);
        }
        System.out.println("合法坐标个数：" + valid.size() + " -> " + valid);
    }
}
```

**课后练习**
1. 写出你打算做的毕业项目自己的 GAV 坐标，并推导出它在本地仓库中的完整路径。
2. 解释为什么 Maven 必须用坐标而不能用"jar 包文件名"来管理依赖。
3. 列出本地仓库、私服、中央仓库三者的查找顺序，并说明私服存在的两个意义。

### 22-02 安装与仓库配置

**知识点**
- Maven 是绿色版：解压后把 `bin` 目录加入 `PATH`，用 `mvn -v` 验证，输出里会带上它使用的 JDK 版本。
- 全局配置文件是 `conf/settings.xml`，用户级配置是 `~/.m2/settings.xml`，用户级优先级更高。
- `localRepository` 指定本地仓库位置，默认在 `~/.m2/repository`；仓库体积会涨到几个 G，建议改到非系统盘。
- `<mirrors>` 配置镜像，国内一般用阿里云 `https://maven.aliyun.com/repository/public`，否则从中央仓库拉包会非常慢。
- IDEA 自带捆绑的 Maven，也可以在 `Settings → Build Tools → Maven` 里指定自己的安装目录和 settings 文件。
- 易错：改完 `settings.xml` 后 IDEA 里要重新导入（Reload），否则用的还是旧配置。

**示例代码**
```java
public class SettingsReader {
    public static void main(String[] args) {
        // 一个典型的 settings.xml 片段
        String xml = """
                <settings>
                    <localRepository>D:/maven-repo</localRepository>
                    <mirrors>
                        <mirror>
                            <id>aliyun</id>
                            <mirrorOf>central</mirrorOf>
                            <url>https://maven.aliyun.com/repository/public</url>
                        </mirror>
                    </mirrors>
                </settings>
                """;

        String repo = tag(xml, "localRepository");
        String mirrorOf = tag(xml, "mirrorOf");
        String url = tag(xml, "url");

        System.out.println("本地仓库：" + (repo.isEmpty() ? "未配置，使用默认 ~/.m2/repository" : repo));
        System.out.println("镜像覆盖：" + mirrorOf);
        System.out.println("镜像地址：" + url);

        // 本地仓库路径是否合法的简单校验
        boolean absolute = repo.matches("^[A-Za-z]:/.*") || repo.startsWith("/");
        System.out.println("仓库路径是绝对路径吗：" + absolute);
        if (!absolute) {
            System.out.println("警告：localRepository 建议写绝对路径，否则可能被解析到 Maven 安装目录下");
        }
    }

    // 极简标签取值：只取第一个同名标签之间的内容
    private static String tag(String xml, String name) {
        String open = "<" + name + ">";
        String close = "</" + name + ">";
        int start = xml.indexOf(open);
        int end = xml.indexOf(close, start);
        if (start < 0 || end < 0) {
            return "";
        }
        return xml.substring(start + open.length(), end).trim();
    }
}
```

**课后练习**
1. 把本地仓库改到 `D:/maven-repo` 并配置阿里云镜像，然后执行 `mvn help:effective-settings` 检查配置是否生效。
2. 说明 `settings.xml` 放在 Maven 安装目录和放在用户目录下有什么区别，团队协作时应该用哪一个。
3. 如果不配置镜像，`mvn clean compile` 第一次执行会发生什么？给出两种排查思路。

### 22-03 标准目录结构与pom.xml

**知识点**
- Maven 强制约定目录：`src/main/java`（源码）、`src/main/resources`（配置）、`src/test/java`（测试源码）、`src/test/resources`（测试配置）、`target`（构建输出）。
- 只要按约定放文件，就不用告诉 Maven"源码在哪"，这是"约定优于配置"的直接体现。
- `pom.xml` 必备四要素：`modelVersion`（固定 4.0.0）、`groupId`、`artifactId`、`version`。
- 常用可选元素：`packaging`（jar/war/pom，默认 jar）、`properties`（版本变量）、`dependencies`、`build`。
- `target` 目录是构建产物，可以随时删；`mvn clean` 就是删它。它不应该提交到 Git。
- 易错：把源码直接放在 `src` 根下、或者包名与目录层级对不上，Maven 会"找不到符号"，因为编译时只扫 `src/main/java`。

**示例代码**
```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class StandardLayout {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("maven-layout-");
        List<String> dirs = List.of(
                "src/main/java/com/heima",
                "src/main/resources",
                "src/test/java/com/heima",
                "src/test/resources",
                "target");
        for (String d : dirs) {
            Files.createDirectories(root.resolve(d));
        }
        Files.writeString(root.resolve("src/main/java/com/heima/App.java"),
                "package com.heima; public class App { }");
        Files.writeString(root.resolve("src/main/resources/application.properties"), "app.name=demo");
        Files.writeString(root.resolve("pom.xml"), "<project/>");

        System.out.println("标准工程结构：");
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted().forEach(p -> {
                if (p.equals(root)) {
                    System.out.println("my-project/");
                    return;
                }
                Path rel = root.relativize(p);
                String name = rel.getFileName().toString() + (Files.isDirectory(p) ? "/" : "");
                System.out.println("  ".repeat(rel.getNameCount()) + name);
            });
        }

        // 包名与目录必须一一对应：com.heima.App -> com/heima/App.java
        String className = "com.heima.App";
        String expected = "src/main/java/" + className.replace('.', '/') + ".java";
        System.out.println();
        System.out.println("类 " + className + " 的源码应放在：" + expected);
        System.out.println("该文件存在吗：" + Files.exists(root.resolve(expected)));

        // 清理临时目录
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
```

**课后练习**
1. 用 Maven 生成一个空工程（`mvn archetype:generate` 或 IDEA 新建），对照笔记画出它的目录树。
2. 把 `App.java` 里的包名改成 `com.heima.demo`，但文件还放在 `com/heima` 下，执行 `mvn compile` 看报什么错，并解释原因。
3. 说明 `src/main/resources` 和 `src/main/java` 下的非 `.java` 文件在打包后分别去了哪里。

### 22-04 坐标依赖与依赖范围

**知识点**
- `<dependency>` 写在 `<dependencies>` 里，同样用 GAV 定位，`scope` 决定这个 jar 参与哪些环节。
- `compile`（默认）：编译、测试、打包都在，会传递给下游项目。
- `provided`：编译和测试可用，打包不带（如 `servlet-api`，因为服务器已经提供）。
- `runtime`：编译不用、运行要用（如 JDBC 驱动，代码里只出现字符串类名）。
- `test`：只在测试阶段可用，不会打进产物（如 JUnit）。
- 易错：JDBC 驱动写成 `compile` 也能跑，但会让驱动被打进 war 包；规范写法是 `runtime`。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class DependencyScope {
    public static void main(String[] args) {
        // scope -> {编译期, 测试期, 打包产物}
        Map<String, boolean[]> table = new LinkedHashMap<>();
        table.put("compile", new boolean[]{true, true, true});
        table.put("provided", new boolean[]{true, true, false});
        table.put("runtime", new boolean[]{false, true, true});
        table.put("test", new boolean[]{false, true, false});

        System.out.printf("%-10s %-8s %-8s %-8s%n", "scope", "编译期", "测试期", "打包");
        System.out.println("------------------------------------------");
        for (Map.Entry<String, boolean[]> e : table.entrySet()) {
            boolean[] v = e.getValue();
            System.out.printf("%-10s %-8s %-8s %-8s%n",
                    e.getKey(), mark(v[0]), mark(v[1]), mark(v[2]));
        }

        System.out.println();
        String[][] deps = {
                {"junit:junit:4.13.2", "test"},
                {"jakarta.servlet:jakarta.servlet-api:6.0.0", "provided"},
                {"com.mysql:mysql-connector-j:8.4.0", "runtime"},
                {"org.mybatis:mybatis:3.5.16", "compile"}
        };
        for (String[] d : deps) {
            System.out.println(d[0] + "  ->  scope=" + d[1]
                    + "，会被打进 jar 吗：" + (table.get(d[1])[2] ? "会" : "不会"));
        }
    }

    private static String mark(boolean b) {
        return b ? "√" : "×";
    }
}
```

**课后练习**
1. 为你的项目列出 4 个依赖，分别标注应该用哪种 scope，并说明理由。
2. 把 JUnit 的 scope 误写成 `compile`，会导致什么后果？
3. 解释为什么 JDBC 驱动适合用 `runtime`，代码里又是怎么找到驱动类的。

### 22-05 依赖冲突与调解

**知识点**
- 传递依赖：A 依赖 B，B 依赖 C，引入 A 时 C 会被自动引入，这是依赖管理最省事也最容易出问题的地方。
- 同一个 jar 出现多个版本时，Maven 按两条规则选一个：**最短路径优先**（离项目层级最近的胜出），路径一样长时**先声明者优先**。
- 冲突的典型症状：`NoSuchMethodError`、`ClassNotFoundException`、`AbstractMethodError`，编译能过但运行报错。
- `<exclusions>` 主动排除某个传递依赖；`<optional>true</optional>` 让依赖不继续向下传递。
- `<dependencyManagement>` 统一锁定版本，子模块不写 `version` 也能拿到指定版本，这是解决冲突最有效的手段。
- 易错：`mvn dependency:tree` 是排查冲突的第一手段，不要靠猜。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DependencyMediation {
    public static void main(String[] args) {
        // 模拟：项目直接声明两个依赖，各自又带进来不同版本的 commons-lang3
        Node a = new Node("spring-boot-starter-web", "3.3.2");
        a.children.add(new Node("commons-lang3", "3.12.0"));

        Node b = new Node("some-utils", "1.0.0");
        Node mid = new Node("inner-lib", "2.0.0");
        mid.children.add(new Node("commons-lang3", "3.9"));
        b.children.add(mid);

        List<Node> roots = new ArrayList<>(List.of(a, b));
        System.out.println("依赖树：");
        for (Node n : roots) {
            print(n, 0);
        }

        // 最短路径优先：广度优先遍历，先到者胜；同层按声明顺序
        Map<String, String> resolved = new LinkedHashMap<>();
        List<Node> level = new ArrayList<>(roots);
        int depth = 1;
        while (!level.isEmpty()) {
            List<Node> next = new ArrayList<>();
            for (Node n : level) {
                resolved.putIfAbsent(n.artifactId, n.version + "（第 " + depth + " 层）");
                next.addAll(n.children);
            }
            level = next;
            depth++;
        }

        System.out.println();
        System.out.println("调解结果：");
        resolved.forEach((k, v) -> System.out.println("  " + k + " -> " + v));
        System.out.println();
        System.out.println("结论：commons-lang3 取 3.12.0，因为它到项目的路径更短（3.9 在第三层被跳过）");
    }

    static class Node {
        String artifactId;
        String version;
        List<Node> children = new ArrayList<>();

        Node(String artifactId, String version) {
            this.artifactId = artifactId;
            this.version = version;
        }
    }

    static void print(Node n, int depth) {
        System.out.println("  ".repeat(depth) + (depth == 0 ? "" : "+- ")
                + n.artifactId + ":" + n.version);
        for (Node c : n.children) {
            print(c, depth + 1);
        }
    }
}
```

**课后练习**
1. 在你的工程里执行 `mvn dependency:tree`，找出重复出现的 artifactId，说明 Maven 最终选了哪个版本。
2. 画出"最短路径优先"和"第一声明优先"两条规则的判定流程图。
3. 用 `<dependencyManagement>` 把某个 jar 锁定到指定版本，然后验证子模块里的写法。

### 22-06 生命周期与常用命令

**知识点**
- Maven 有三套相互独立的生命周期：`clean`（清理）、`default`（构建）、`site`（站点）。
- `default` 生命周期的关键阶段依次是：`validate → compile → test → package → verify → install → deploy`。
- 执行某个阶段时，**它前面的所有阶段都会先执行**；所以 `mvn package` 会自动先 `compile` 和 `test`。
- 每个阶段绑定若干插件的 goal，比如 `package` 阶段默认绑定 `maven-jar-plugin:jar`。
- 常用命令：`mvn clean`、`mvn compile`、`mvn test`、`mvn package`、`mvn install`、`mvn clean package -DskipTests`。
- `install` 把产物装进本地仓库供其它项目引用，`deploy` 推送到私服供团队引用。
- 易错：`-DskipTests` 是跳过执行但仍编译测试代码；`-Dmaven.test.skip=true` 连编译都跳过。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LifecycleRunner {
    public static void main(String[] args) {
        List<String> defaultLifecycle = new ArrayList<>(List.of(
                "validate", "initialize", "generate-sources", "process-sources",
                "generate-resources", "process-resources", "compile", "process-classes",
                "generate-test-sources", "process-test-sources", "generate-test-resources",
                "process-test-resources", "test-compile", "process-test-classes",
                "test", "prepare-package", "package", "verify", "install", "deploy"));

        List<String> cleanLifecycle = new ArrayList<>(List.of(
                "pre-clean", "clean", "post-clean"));

        run(cleanLifecycle, "clean", "mvn clean");
        run(defaultLifecycle, "package", "mvn clean package");

        System.out.println("说明：执行 package 时，它之前的所有阶段都会依次执行；");
        System.out.println("      install / deploy 在 package 之后，所以没有被触发。");
    }

    // 执行到 target 阶段为止（包含 target）
    private static void run(List<String> lifecycle, String target, String command) {
        int stop = lifecycle.indexOf(target);
        if (stop < 0) {
            System.out.println(command + " -> 未知阶段：" + target);
            return;
        }
        System.out.println("$ " + command);
        List<String> executed = new ArrayList<>(lifecycle.subList(0, stop + 1));
        for (int i = 0; i < executed.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + executed.get(i));
        }
        System.out.println("  共执行 " + executed.size() + " 个阶段，最后停在 " + target);
        System.out.println("  跳过的后续阶段：" + lifecycle.subList(stop + 1, lifecycle.size()));
        List<String> common = Arrays.asList("clean", "package", "install", "deploy");
        System.out.println("  提示：" + target + (common.contains(target) ? " 是常用目标阶段" : " 不是常用目标阶段"));
        System.out.println();
    }
}
```

**课后练习**
1. 依次执行 `mvn compile`、`mvn test`、`mvn package`、`mvn install`，观察每个命令实际触发了哪些阶段。
2. 说明 `mvn clean package -DskipTests` 和 `-Dmaven.test.skip=true` 的区别。
3. 如果 `package` 之后想立刻让另一个本地项目用上新版本，应该执行哪个命令？为什么是它而不是 `deploy`？

### 22-07 聚合与继承（多模块）

**知识点**
- 聚合（`<modules>`）：父工程一次命令构建所有子模块，父工程 `packaging` 必须是 `pom`。
- 继承（`<parent>`）：子模块继承父工程的 `properties`、`dependencies`、`dependencyManagement`、插件配置。
- `<dependencyManagement>` 只声明版本不引入依赖，子模块要用时仍需写 `<dependency>` 但可以省略 `version`。
- `<properties>` 里集中定义版本号，用 `${xxx.version}` 引用，升级时只改一处。
- 子模块的 `<relativePath>` 指向父 `pom.xml`，默认是 `../pom.xml`。
- 易错：父工程 `packaging` 写成 `jar` 会导致 `modules` 不生效；`dependencyManagement` 里写了依赖就以为已引入，结果子模块编译报"找不到符号"。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MultiModule {
    public static void main(String[] args) {
        // 父工程统一管理版本
        Map<String, String> managed = new LinkedHashMap<>();
        managed.put("mysql-connector-j", "8.4.0");
        managed.put("mybatis", "3.5.16");
        managed.put("junit-jupiter", "5.10.2");

        Module parent = new Module("heima-parent", "pom", null);
        parent.modules.add("heima-common");
        parent.modules.add("heima-service");
        parent.modules.add("heima-web");

        Module common = new Module("heima-common", "jar", "heima-parent");
        common.uses.add("junit-jupiter");

        Module service = new Module("heima-service", "jar", "heima-parent");
        service.uses.add("mybatis");
        service.uses.add("mysql-connector-j");

        Module web = new Module("heima-web", "jar", "heima-parent");
        web.uses.add("mybatis");

        List<Module> all = new ArrayList<>(List.of(parent, common, service, web));

        System.out.println("工程结构：");
        System.out.println(parent.name + " (packaging=pom)");
        for (Module m : all.subList(1, all.size())) {
            System.out.println("  +- " + m.name + " (parent=" + m.parent + ")");
            for (String dep : m.uses) {
                System.out.println("       " + dep + " : " + managed.get(dep) + "   <- 版本来自父工程");
            }
        }

        System.out.println();
        System.out.println("执行 mvn clean install（在父工程目录）：");
        for (Module m : all) {
            System.out.println("  " + m.name + " -> " + ("pom".equals(m.packaging) ? "聚合构建，无产物" : "构建 jar"));
        }

        System.out.println();
        System.out.println("若把父工程 packaging 误写成 jar：modules 会被忽略，只有父工程自己被构建。");
    }

    static class Module {
        String name;
        String packaging;
        String parent;
        List<String> modules = new ArrayList<>();
        List<String> uses = new ArrayList<>();

        Module(String name, String packaging, String parent) {
            this.name = name;
            this.packaging = packaging;
            this.parent = parent;
        }
    }
}
```

**课后练习**
1. 建一个 `parent + common + web` 三模块工程，在父工程里用 `dependencyManagement` 锁定版本，子模块省略 `version` 后验证能编译。
2. 说明聚合和继承分别解决什么问题，为什么实际项目里经常同一个 `pom.xml` 同时承担两者。
3. 父工程 `packaging` 写错会有什么现象？给出排查步骤。

### 22-08 综合案例：打一个可执行jar

**知识点**
- 普通 `mvn package` 打出的 jar 不含依赖，直接 `java -jar` 会报 `NoClassDefFoundError`。
- 两种解决办法：`maven-shade-plugin`（把依赖解压后一起塞进一个 jar）、`maven-assembly-plugin`（打成带 `lib` 目录的压缩包）。
- 可执行 jar 的关键是 `META-INF/MANIFEST.MF` 里的 `Main-Class`，它告诉 JVM 从哪个类开始执行。
- `maven-jar-plugin` 通过 `<manifest><mainClass>` 生成该字段；路径用全限定类名，不带 `.class`。
- `target/classes` 里的包结构会原样进入 jar 的根目录，所以 `com/heima/App.class` 在 jar 里也是这个路径。
- 易错：`Main-Class` 写错不会在打包时报错，只在 `java -jar` 时抛 `ClassNotFoundException`。

**示例代码**
```java
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class ExecutableJar {
    public static void main(String[] args) throws IOException {
        Path work = Files.createTempDirectory("jar-demo-");
        Path jarPath = work.resolve("demo-1.0.0.jar");

        // 1. 准备 MANIFEST：Main-Class 是 java -jar 的入口
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "com.heima.App");
        attrs.put(new Attributes.Name("Created-By"), "Maven 3.9.6");

        // 2. 把"编译产物"按包路径写进 jar
        try (OutputStream fos = Files.newOutputStream(jarPath);
             JarOutputStream jos = new JarOutputStream(fos, manifest)) {

            // 真实项目里这些是 target/classes 下的 .class 文件
            addEntry(jos, "com/heima/App.class", "（这里是编译后的字节码）");
            addEntry(jos, "com/heima/App$1.class", "（匿名内部类的字节码）");
            addEntry(jos, "application.properties", "app.name=demo\n");
        }

        System.out.println("已生成：" + jarPath.getFileName()
                + "，大小 " + Files.size(jarPath) + " 字节");
        System.out.println();

        // 3. 读回来验证结构
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            System.out.println("MANIFEST 中的入口类：" + jar.getManifest()
                    .getMainAttributes().getValue("Main-Class"));
            System.out.println("jar 内条目：");
            jar.stream().forEach(e -> System.out.println("  " + e.getName()));
        }

        System.out.println();
        System.out.println("运行方式：java -jar demo-1.0.0.jar");
        System.out.println("若 Main-Class 写错，打包不报错，运行时才抛 ClassNotFoundException。");

        // 清理临时文件
        Files.deleteIfExists(jarPath);
        Files.deleteIfExists(work);
    }

    private static void addEntry(JarOutputStream jos, String name, String content)
            throws IOException {
        jos.putNextEntry(new JarEntry(name));
        jos.write(content.getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
    }
}
```

**课后练习**
1. 给一个含第三方依赖的项目配置 `maven-shade-plugin`，打出 `java -jar` 能直接运行的包，并用 `jar tf` 检查内容。
2. 说明 `maven-jar-plugin` 和 `maven-shade-plugin` 打的包有什么区别，各自适用什么场景。
3. 一个 `java -jar` 报 `NoClassDefFoundError: com/mysql/cj/jdbc/Driver` 的包，问题最可能出在哪一步？给出排查顺序。

## 本章综合练习

### 综合题：多模块工程与可执行 jar

**场景**

把单模块工程改造成多模块 Maven 工程：common 放工具、dao 依赖 common、web 依赖 dao，最后打一个能 java -jar 直接跑的可执行包，并解决一次依赖冲突。

**任务**
1. 搭建 parent + 三个子模块的聚合结构，父 pom 用 dependencyManagement 统一版本，子模块只声明依赖不写版本号。
2. 在 dao 模块引入两个间接依赖不同版本的同一个库，制造依赖冲突，用 mvn dependency:tree 定位并用 exclusions 或 dependencyManagement 解决。
3. 配置 maven-shade-plugin 或 assembly 打可执行 jar，写清 Main-Class，验证 java -jar 能启动并读到 resources 下的配置。
4. 说明 compile、provided、runtime、test 四种依赖范围的区别，并各举一个真实使用场景。

**验收清单**
- [ ] mvn clean package 一次成功，三个模块产物齐全。
- [ ] dependency:tree 输出中冲突库只剩一个版本，运行时不出现 NoSuchMethodError。
- [ ] 可执行 jar 在干净目录下 java -jar 能跑起来，不依赖外部 classpath。
- [ ] 父 pom 中没有重复声明版本，子模块 pom 里看不到具体版本号。
