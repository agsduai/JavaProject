# 第16章 IO流与File

本章解决"数据怎么进出程序"的问题：先学会用 `File` 类描述文件与目录，再掌握字节流、字符流两套读写体系，最后学习缓冲流、转换流、对象序列化与 Properties 配置文件的读写。IO 是所有"数据持久化"能力的基础，后面 JDBC、项目实战都会用到。

### 16-01 File类的创建与判断

**知识点**
- `File` 是文件和目录路径名的抽象表示，它本身不代表文件内容，只描述"路径"。
- 三种构造：`File(String pathname)`、`File(String parent, String child)`、`File(File parent, String child)`。
- 路径分隔符跨平台写法：用 `File.separator`，或直接写 `/`（Windows 也支持）。
- 判断方法：`exists()` 是否存在、`isFile()` 是否文件、`isDirectory()` 是否目录、`length()` 文件大小（字节）。
- 创建方法：`createNewFile()` 创建文件（已存在返回 false）、`mkdir()` 创建单级目录、`mkdirs()` 创建多级目录。
- 易错：相对路径的基准是"项目根目录"（IDEA 中就是模块所在目录），不是 `src` 目录。

**示例代码**
```java
import java.io.File;
import java.io.IOException;

public class FileCreateDemo {
    public static void main(String[] args) throws IOException {
        // 相对路径以项目根目录为基准
        File file = new File("demo/hello.txt");
        File dir = new File("demo");
        File deepDir = new File("demo/a/b/c");

        // mkdirs 会一次性创建不存在的多级目录
        System.out.println("创建多级目录：" + deepDir.mkdirs());
        System.out.println("目录是否存在：" + dir.exists());     // true
        System.out.println("是目录吗：" + dir.isDirectory());   // true
        System.out.println("是文件吗：" + dir.isFile());        // false

        // 创建文件，父目录必须已存在
        if (!file.exists()) {
            System.out.println("创建文件：" + file.createNewFile());
        }
        System.out.println("文件是否存在：" + file.exists());   // true
        System.out.println("文件大小：" + file.length() + " 字节"); // 0

        // 用父路径 + 子路径拼接，跨平台更安全
        File child = new File(dir, "note" + File.separator + "readme.txt");
        System.out.println("拼接后的路径：" + child.getPath());
        System.out.println("绝对路径：" + child.getAbsolutePath());
        System.out.println("文件名：" + child.getName());        // readme.txt
        System.out.println("父路径：" + child.getParent());
    }
}
```

**课后练习**
1. 在项目根目录下创建 `test/2026/11` 三级目录，并在其中创建一个空文件 `log.txt`，打印该文件的绝对路径和大小。
2. 编写方法 `boolean isTextFile(File f)`，判断传入的 `File` 是否是已存在的 `.txt` 文件。
3. 分别用三种构造方法创建指向同一个文件的 `File` 对象，打印它们的 `getPath()` 并比较差异。

### 16-02 File的遍历与删除

**知识点**
- `list()` 返回 `String[]`（只含名称），`listFiles()` 返回 `File[]`（含完整路径信息）。
- `listFiles(FileFilter filter)` 可以按条件过滤，`accept` 返回 `true` 才保留。
- `delete()` 删除文件或**空目录**；目录非空时删除失败，必须先删掉里面的内容。
- 递归删除目录的套路：先遍历子项递归删除，最后删自己。
- 递归遍历要注意：目录层级过深可能栈溢出，`listFiles` 在无权限时会返回 `null`。
- 易错：`delete()` 不抛异常、只返回 `boolean`，删除失败往往被静默忽略，务必检查返回值。

**示例代码**
```java
import java.io.File;

public class FileTraversalDemo {
    public static void main(String[] args) {
        File dir = new File("demo");
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("目录不存在，已创建：" + dir.getAbsolutePath());
        }

        // listFiles 获取目录下所有 File 对象
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                System.out.println((f.isDirectory() ? "[目录] " : "[文件] ") + f.getName());
            }
        }

        // 过滤器：只保留 .txt 文件
        File[] txtFiles = dir.listFiles(pathname ->
                pathname.isFile() && pathname.getName().endsWith(".txt"));
        if (txtFiles != null) {
            System.out.println("txt 文件个数：" + txtFiles.length);
        }

        // 递归统计目录下所有文件的总大小
        System.out.println("总大小：" + getTotalSize(dir) + " 字节");
    }

    public static long getTotalSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File f : files) {
            if (f.isFile()) {
                size += f.length();
            } else {
                size += getTotalSize(f); // 目录递归进去继续统计
            }
        }
        return size;
    }

    // 递归删除：先删子项，再删自己
    public static boolean deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
        }
        return dir.delete();
    }
}
```

**课后练习**
1. 递归遍历某个目录，输出所有 `.java` 文件的绝对路径，并统计总个数。
2. 编写方法 `copyTree(File src, File dest)` 的目录结构骨架：只创建相同结构的空目录，不复制文件内容。
3. 编写方法删除某个目录，要求先打印将被删除的所有文件路径，再执行删除，最后验证目录已不存在。

### 16-03 字节流读取与写出

**知识点**
- 字节流是所有流的基础，以 `byte`（8 位）为单位，能处理任意类型文件（图片、视频、文本）。
- `FileInputStream` 读取：`read()` 读一个字节（返回 0~255，末尾返回 -1）、`read(byte[] b)` 批量读取返回实际读取长度。
- `FileOutputStream` 写出：`write(int b)`、`write(byte[] b)`、`write(byte[] b, int off, int len)`。
- `FileOutputStream(String path, boolean append)` 第二个参数为 `true` 表示追加，默认 `false` 会覆盖原内容。
- 流用完必须关闭，推荐 `try-with-resources` 写法（`try (资源) {}`），异常时自动关闭。
- 易错：一次读一个字节效率极低；`read(byte[] b)` 返回的是"本次读到的字节数"，不是数组长度。

**示例代码**
```java
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class ByteStreamDemo {
    public static void main(String[] args) {
        String path = "demo/byte.txt";

        // 写出：try-with-resources 自动关闭流
        try (FileOutputStream out = new FileOutputStream(path)) {
            out.write("Hello IO".getBytes());        // 写出全部字节
            out.write('\n');
            out.write("第二行".getBytes());
        } catch (IOException e) {
            System.out.println("写出失败：" + e.getMessage());
        }

        // 读取：一次读一个字节
        try (FileInputStream in = new FileInputStream(path)) {
            int b;
            StringBuilder sb = new StringBuilder();
            while ((b = in.read()) != -1) {
                sb.append((char) b);
            }
            System.out.println("逐字节读取：\n" + sb);
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 读取：用字节数组批量读，效率更高
        try (FileInputStream in = new FileInputStream(path)) {
            byte[] buffer = new byte[1024];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = in.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, len)); // 只转换实际读到的部分
            }
            System.out.println("批量读取：\n" + sb);
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 追加写入
        try (FileOutputStream out = new FileOutputStream(path, true)) {
            out.write("\n追加的一行".getBytes());
            System.out.println("追加完成");
        } catch (IOException e) {
            System.out.println("追加失败：" + e.getMessage());
        }
    }
}
```

**课后练习**
1. 用字节流把一段中文字符串写入文件，再用字节流读出来，验证内容一致（注意编码要统一为 UTF-8）。
2. 编写方法 `copyFile(String src, String dest)`，用字节数组缓冲区实现文件复制，并打印复制的总字节数。
3. 统计一个文件中有多少个换行符 `\n`，要求用字节流一次读一个字节实现。

### 16-04 字符流读取与写出

**知识点**
- 字符流以字符为单位，专门处理文本，能正确处理中文（一个中文在 UTF-8 中占 3 个字节）。
- `FileReader` 读取：`read()` 读一个字符、`read(char[] cbuf)` 批量读取。
- `FileWriter` 写出：`write(String)`、`write(char[])`、`write(String, int off, int len)`。
- `FileWriter` 同样有追加模式构造：`new FileWriter(path, true)`。
- `FileWriter` 内部有缓冲区，`close()` 会先刷新再关闭；不关闭可能丢数据，也可手动 `flush()`。
- 易错：用字节流直接读中文再 `(char)` 强转会出现乱码，文本文件应优先用字符流。

**示例代码**
```java
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class CharStreamDemo {
    public static void main(String[] args) {
        String path = "demo/char.txt";

        // 字符流写出：直接写字符串，无需转字节
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("你好，字符流！\n");
            writer.write("第二行中文内容\n");
            writer.write("第三行");
            writer.flush(); // 手动刷新，确保数据落盘
        } catch (IOException e) {
            System.out.println("写出失败：" + e.getMessage());
        }

        // 字符流读取：一次读一个字符
        try (FileReader reader = new FileReader(path)) {
            int c;
            StringBuilder sb = new StringBuilder();
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
            System.out.println("逐字符读取：\n" + sb);
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 字符数组批量读取
        try (FileReader reader = new FileReader(path)) {
            char[] buffer = new char[8];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
            System.out.println("批量读取：\n" + sb);
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 追加写入
        try (FileWriter writer = new FileWriter(path, true)) {
            writer.write("\n追加：第四行");
            System.out.println("追加完成");
        } catch (IOException e) {
            System.out.println("追加失败：" + e.getMessage());
        }
    }
}
```

**课后练习**
1. 用 `FileWriter` 把一个字符串数组的每个元素写成一行，再用 `FileReader` 读回来并统计行数。
2. 读取一个文本文件，把其中所有英文小写字母转成大写后写入新文件。
3. 比较字节流和字符流读写同一段中文内容的结果，说明为什么字符流更适合处理文本。

### 16-05 缓冲流

**知识点**
- 缓冲流在内存中维护一个缓冲区（默认 8192 字节/字符），减少与磁盘的交互次数，显著提升效率。
- 四种缓冲流：`BufferedInputStream`、`BufferedOutputStream`、`BufferedReader`、`BufferedWriter`。
- `BufferedReader` 特有 `readLine()`：一次读一整行，读到末尾返回 `null`（不含换行符）。
- `BufferedWriter` 特有 `newLine()`：写入一个跨平台换行符。
- 包装流只需把基本流作为构造参数传入：`new BufferedReader(new FileReader(path))`。
- 易错：缓冲流的 `close()` 会同时关闭被包装的流，不需要也不应该重复关闭底层流。

**示例代码**
```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BufferedStreamDemo {
    public static void main(String[] args) {
        String path = "demo/buffered.txt";

        // BufferedWriter：按行写
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write("第一行：Java");
            writer.newLine();  // 跨平台换行
            writer.write("第二行：IO流");
            writer.newLine();
            writer.write("第三行：缓冲流");
        } catch (IOException e) {
            System.out.println("写出失败：" + e.getMessage());
        }

        // BufferedReader：readLine 按行读
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) { // 末尾返回 null
                System.out.println("第 " + lineNo + " 行：" + line);
                lineNo++;
            }
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 统计行数：readLine 读到 null 为止
        System.out.println("总行数：" + countLines(path));
    }

    public static int countLines(String path) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            while (reader.readLine() != null) {
                count++;
            }
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }
        return count;
    }
}
```

**课后练习**
1. 用缓冲流复制一个文本文件，并在每行前加上行号，输出到新文件。
2. 读取一个文本文件，统计其中非空行的数量以及最长一行的长度。
3. 用 `BufferedWriter` 生成一个含 100 行的文件，再用 `BufferedReader` 统计其中包含特定关键字的行数。

### 16-06 转换流与编码

**知识点**
- 转换流用于"字节流 ↔ 字符流"的桥梁，同时可以指定字符编码，解决乱码问题。
- `InputStreamReader(InputStream in, String charsetName)`：字节输入流转字符输入流。
- `OutputStreamWriter(OutputStream out, String charsetName)`：字节输出流转字符输出流。
- 常见编码：UTF-8（中文 3 字节）、GBK（中文 2 字节）；编码与解码用的字符集必须一致，否则乱码。
- JDK11 起可以用 `Files.readString`/`Files.writeString` 直接指定编码，写法更简洁。
- 易错：用 GBK 写入却用 UTF-8 读取，会出现"锟斤拷"这类乱码；中文 Windows 默认编码可能是 GBK。

**示例代码**
```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class ConvertStreamDemo {
    public static void main(String[] args) {
        String path = "demo/encoding.txt";

        // 用 UTF-8 编码写出
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            writer.write("编码测试：UTF-8 写出");
            writer.newLine();
            writer.write("第二行中文");
        } catch (IOException e) {
            System.out.println("写出失败：" + e.getMessage());
        }

        // 用 UTF-8 读取，编码一致才不会乱码
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("UTF-8 读取：" + line);
            }
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 故意用 GBK 读取 UTF-8 文件，观察乱码
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), "GBK"))) {
            System.out.println("GBK 读取（乱码演示）：" + reader.readLine());
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 演示字符串与字节数组的编解码
        String text = "你好";
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        byte[] gbk = text.getBytes(java.nio.charset.Charset.forName("GBK"));
        System.out.println("UTF-8 字节数：" + utf8.length); // 6
        System.out.println("GBK 字节数：" + gbk.length);     // 4
    }
}
```

**课后练习**
1. 用 GBK 编码写一个文件，再用 UTF-8 读取，记录乱码现象；然后把读取编码改成 GBK，验证内容恢复正常。
2. 把一个 UTF-8 编码的文件转成 GBK 编码的新文件，要求用转换流实现。
3. 统计一段中文文本分别用 UTF-8 和 GBK 编码后的字节数，并解释差异原因。

### 16-07 对象流与序列化

**知识点**
- 序列化：把对象转成字节序列写入文件；反序列化：从文件读回对象。
- `ObjectOutputStream.writeObject(obj)` 序列化，`ObjectInputStream.readObject()` 反序列化。
- 被序列化的类必须实现 `Serializable` 接口（标记接口，没有任何方法）。
- `serialVersionUID` 是版本号，类结构改变后反序列化会校验它，不一致会抛 `InvalidClassException`，建议显式声明。
- `transient` 修饰的成员变量不参与序列化，反序列化后得到默认值。
- 易错：`Serializable` 只对当前类的字段生效，父类未实现序列化时父类字段不会被保存。

**示例代码**
```java
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

class Student implements Serializable {
    // 显式声明版本号，避免类改动后反序列化失败
    private static final long serialVersionUID = 1L;

    private String name;
    private int age;
    private transient String password; // transient：不参与序列化

    public Student(String name, int age, String password) {
        this.name = name;
        this.age = age;
        this.password = password;
    }

    @Override
    public String toString() {
        return "Student{name='" + name + "', age=" + age + ", password=" + password + "}";
    }
}

public class ObjectStreamDemo {
    public static void main(String[] args) {
        String path = "demo/student.dat";
        Student student = new Student("张三", 18, "123456");

        // 序列化：对象 -> 文件
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))) {
            out.writeObject(student);
            System.out.println("序列化完成：" + student);
        } catch (IOException e) {
            System.out.println("序列化失败：" + e.getMessage());
        }

        // 反序列化：文件 -> 对象
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
            Student read = (Student) in.readObject();
            System.out.println("反序列化结果：" + read); // password 为 null
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("反序列化失败：" + e.getMessage());
        }
    }
}
```

**课后练习**
1. 定义 `Book` 类（书名、作者、价格）实现 `Serializable`，序列化 3 本书到文件，再反序列化读回来打印。
2. 给 `Book` 增加一个 `transient` 修饰的字段，验证反序列化后它的值。
3. 删除并重新添加一个字段后再次反序列化旧文件，观察 `serialVersionUID` 不一致导致的异常并解释原因。

### 16-08 Properties配置文件

**知识点**
- `Properties` 是 `Hashtable` 的子类，专门用于读写键值对配置，键和值都是 `String`。
- 常用方法：`setProperty(k, v)`、`getProperty(k)`、`stringPropertyNames()` 获取所有键。
- `load(Reader)` 从配置文件读取，`store(Writer, comments)` 写回配置文件。
- 配置文件格式：一行一个 `key=value`，`#` 开头是注释，值前后空格会被忽略。
- 典型用途：数据库连接参数、游戏关卡配置、程序开关等需要"改配置不改代码"的场景。
- 易错：`load` 使用 ISO-8859-1 解析，含中文时要用 `load(Reader)` 并指定 UTF-8，或把中文写成 Unicode 转义序列。

**示例代码**
```java
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

public class PropertiesDemo {
    public static void main(String[] args) {
        String path = "demo/config.properties";

        // 写入配置
        Properties props = new Properties();
        props.setProperty("username", "admin");
        props.setProperty("password", "123456");
        props.setProperty("url", "jdbc:mysql://localhost:3306/test");
        try (FileWriter writer = new FileWriter(path)) {
            props.store(writer, "数据库配置");
            System.out.println("配置已写入：" + path);
        } catch (IOException e) {
            System.out.println("写入失败：" + e.getMessage());
        }

        // 读取配置
        Properties loaded = new Properties();
        try (FileReader reader = new FileReader(path)) {
            loaded.load(reader);
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        System.out.println("用户名：" + loaded.getProperty("username"));
        System.out.println("密码：" + loaded.getProperty("password"));
        System.out.println("不存在的键（默认值）：" + loaded.getProperty("port", "3306"));

        // 遍历所有键
        Set<String> keys = loaded.stringPropertyNames();
        for (String key : keys) {
            System.out.println(key + " = " + loaded.getProperty(key));
        }
    }
}
```

**课后练习**
1. 创建 `app.properties`，写入程序名称、版本号、作者三项信息，再读取并格式化输出。
2. 编写方法 `String getConfig(String path, String key, String defaultValue)`，键不存在时返回默认值。
3. 用 `Properties` 实现一个简易的"多语言提示"配置，根据语言代码取出对应的问候语。

### 16-09 NIO与Files工具类

**知识点**
- NIO（New IO）从 JDK7 起提供 `java.nio.file` 包，核心是 `Path` 和 `Files`，API 比传统 IO 更简洁。
- `Path.of("demo/a.txt")` 创建路径对象；`Files.exists`、`Files.isDirectory`、`Files.size` 等做判断。
- `Files.readAllLines(path)` 一次性读所有行；`Files.readString(path)` 读成字符串（JDK11+）。
- `Files.writeString(path, content)`、`Files.write(path, lines)` 写文件，可指定 `StandardOpenOption.APPEND`。
- `Files.copy`、`Files.move`、`Files.delete`、`Files.createDirectories` 完成常见文件操作。
- 易错：`Files.readAllLines` 会把整个文件读进内存，大文件应改用 `Files.lines` 流式处理。

**示例代码**
```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class NioFilesDemo {
    public static void main(String[] args) {
        Path path = Path.of("demo", "nio.txt");

        try {
            // 创建父目录（存在也不报错）
            Files.createDirectories(path.getParent());

            // 写入：整体覆盖
            Files.writeString(path, "第一行 NIO\n第二行 Files\n", StandardCharsets.UTF_8);

            // 追加
            Files.writeString(path, "第三行 追加\n",
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);

            System.out.println("文件大小：" + Files.size(path) + " 字节");
            System.out.println("是否存在：" + Files.exists(path));

            // 读取全部内容
            System.out.println("---- readString ----");
            System.out.println(Files.readString(path, StandardCharsets.UTF_8));

            // 按行读取
            System.out.println("---- readAllLines ----");
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                System.out.println((i + 1) + ": " + lines.get(i));
            }

            // 流式读取：适合大文件
            System.out.println("---- Files.lines ----");
            try (var stream = Files.lines(path, StandardCharsets.UTF_8)) {
                System.out.println("非空行数：" + stream.filter(s -> !s.isBlank()).count());
            }

            // 复制文件
            Path copy = Path.of("demo", "nio_copy.txt");
            Files.copy(path, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("复制完成：" + copy.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("NIO 操作失败：" + e.getMessage());
        }
    }
}
```

**课后练习**
1. 用 `Files` 工具类创建一个文本文件，写入 5 行内容，再统计行数、字符数和文件字节大小。
2. 用 `Files.copy` 复制一个文件，并用 `Files.move` 把它改名，最后用 `Files.delete` 删除原文件。
3. 用 `Files.lines` 流式读取一个大文本文件，统计其中包含指定关键字的行数（要求不把整个文件读入内存）。

### 16-10 IO综合案例：文件内容统计与批量重命名

**知识点**
- 综合案例的解题套路：明确输入（源文件/目录）→ 选择流（文本用字符流，二进制用字节流）→ 处理数据 → 输出结果。
- 统计类需求适合用 `Map<String, Integer>` 累计词频，再排序输出。
- 批量重命名：`File.renameTo(File dest)` 或 `Files.move`，注意新名字不能与已有文件重名。
- 编码统一是文本处理的前提，读写两端都用 UTF-8 可以避免绝大多数乱码。
- 异常处理：单个文件失败不应中断整个批处理，捕获异常后记录并继续。
- 易错：重命名时如果目标文件已存在，`renameTo` 在 Windows 上会直接失败，要先判断。

**示例代码**
```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IoSummaryDemo {
    public static void main(String[] args) {
        String path = "demo/summary.txt";

        // 准备测试数据
        try (java.io.FileWriter writer = new java.io.FileWriter(path)) {
            writer.write("java io stream\n");
            writer.write("java collection map\n");
            writer.write("io stream file\n");
        } catch (IOException e) {
            System.out.println("准备数据失败：" + e.getMessage());
            return;
        }

        // 统计每个单词出现的次数
        Map<String, Integer> wordCount = new HashMap<>();
        int totalChars = 0;
        int totalLines = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                totalChars += line.length();
                String[] words = line.trim().split("\\s+");
                for (String word : words) {
                    if (word.isEmpty()) {
                        continue;
                    }
                    wordCount.merge(word.toLowerCase(), 1, Integer::sum);
                }
            }
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        System.out.println("总行数：" + totalLines + "，总字符数：" + totalChars);
        System.out.println("单词频次（降序）：");
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(wordCount.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));
        entries.forEach(e -> System.out.println("  " + e.getKey() + " = " + e.getValue()));

        // 批量重命名演示：把 demo/rename 下的 .tmp 改成 .txt
        java.io.File dir = new java.io.File("demo/rename");
        if (!dir.exists()) {
            dir.mkdirs();
            try {
                new java.io.File(dir, "a.tmp").createNewFile();
                new java.io.File(dir, "b.tmp").createNewFile();
            } catch (IOException e) {
                System.out.println("创建测试文件失败：" + e.getMessage());
            }
        }
        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".tmp"));
        if (files != null) {
            for (java.io.File f : files) {
                String newName = f.getName().replace(".tmp", ".txt");
                java.io.File dest = new java.io.File(dir, newName);
                boolean ok = f.renameTo(dest);
                System.out.println("重命名 " + f.getName() + " -> " + newName + "：" + (ok ? "成功" : "失败"));
            }
        }
    }
}
```

**课后练习**
1. 统计一个英文文本文件中的单词总数、不同单词数，并输出出现次数最多的 3 个单词。
2. 编写方法 `int countKeyword(File file, String keyword)`，统计文件中包含关键字的行数，要求处理读取异常。
3. 编写一个批量重命名工具：把指定目录下所有文件名中的空格替换成下划线，并输出每个文件的处理结果。

## 本章综合练习

### 综合题：日志文件统计与批量重命名

**场景**

给一个目录下的日志文件做一次体检：统计行数、按级别（INFO/WARN/ERROR）分类计数、找出出现最多的错误信息，再把文件名批量加上日期前缀。File、缓冲流、Properties 与 Files 都要用上。

**任务**
1. 用 File 遍历目录，过滤出 .log 文件，输出文件名、大小、最后修改时间，目录不存在时给出提示而不是抛异常。
2. 用 BufferedReader 逐行读取，按级别计数，统计总行数与 ERROR 占比，结果用 printf 对齐输出。
3. 把出现次数最多的前 3 条 ERROR 信息存入 Map 并排序输出，说明为什么用 LinkedHashMap 与 TreeMap 结果不同。
4. 从 config.properties 读取日期前缀配置，用 Files.move 批量重命名文件，重名时自动加序号避免覆盖。

**验收清单**
- [ ] 空目录、单文件、含子目录三种情况都能正常处理。
- [ ] 行数统计与 wc -l 结果一致，级别计数之和等于总行数。
- [ ] 批量重命名后原文件名可在日志中追溯，不会静默覆盖已有文件。
- [ ] 所有流都用 try-with-resources 关闭，没有资源泄漏。
