/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-03
 * 课名：DTO、VO 与参数校验
 * ------------------------------------------------------------
 * 知识点：
 *   - 三种对象的边界：`DTO` 接收前端入参（只包含允许前端传的字段），`Entity` 对应数据库表，`VO` 返回给前端（去掉密码、内部状态，加上格式化字段）。
 *   - 为什么不能直接用 Entity 收参：前端只要多传一个 `role=admin` 或 `balance=999999`，`BeanUtils.copyProperties` 一拷就变成提权漏洞。
 *   - 校验分层：格式校验（非空、长度、范围、正则）在入口做；业务校验（用户名是否重复、库存是否足够）在 Service 做。两者的区别是"不查库就能判断" vs "必须查库才能判断"。
 *   - 一次返回全部错误，而不是遇到第一个就抛：前端能一次性提示所有问题，用户不用改一个提交一次。
 *   - 校验规则要用常量/注解集中管理，别散落在 if 里。真实项目用 `jakarta.validation` 的 `@NotBlank`、`@Size`、`@Pattern`、`@Min`。
 *   - 金额用 `long` 存分或 `BigDecimal`，绝不用 `double`；`0.1 + 0.2 != 0.3` 在金额上是事故。
 *   - 易错：`@Valid` 只对 `@RequestBody` 生效，`@RequestParam` 上的校验要类上加 `@Validated`；嵌套对象要加 `@Valid` 才会级联校验。
 * ============================================================
 */

package com.heima.ch27;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DtoValidation {
    /** 前端能传的字段，一个不多 */
    static class ItemCreateDTO {
        String name;
        Integer priceCent;
        Integer stock;
        String description;

        ItemCreateDTO(String name, Integer priceCent, Integer stock, String description) {
            this.name = name;
            this.priceCent = priceCent;
            this.stock = stock;
            this.description = description;
        }
    }

    /** 数据库实体，含前端不该碰的字段 */
    static class ItemEntity {
        Long id;
        String name;
        int priceCent;
        int stock;
        String description;
        String status = "OFF_SHELF";
        String ownerId = "system";
    }

    public static void main(String[] args) {
        System.out.println("=== 合法请求 ===");
        ItemCreateDTO ok = new ItemCreateDTO("机械键盘", 39900, 100, "87 键 红轴");
        System.out.println("  " + create(ok));

        System.out.println();
        System.out.println("=== 一次返回全部错误 ===");
        ItemCreateDTO bad = new ItemCreateDTO("", -1, 0, null);
        System.out.println("  " + create(bad));

        System.out.println();
        System.out.println("=== 名称超长 + 描述超长 ===");
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 70; i++) {
            longName.append('键');
        }
        StringBuilder longDesc = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longDesc.append('描');
        }
        System.out.println("  " + create(new ItemCreateDTO(longName.toString(), 100, 1, longDesc.toString())));

        System.out.println();
        System.out.println("=== 前端偷偷传了 status/ownerId 会怎样 ===");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "鼠标");
        raw.put("priceCent", 9900);
        raw.put("stock", 10);
        raw.put("status", "ON_SHELF");
        raw.put("ownerId", "admin");
        System.out.println("  DTO 只认白名单字段，status/ownerId 被丢弃");
        System.out.println("  " + create(fromRaw(raw)));

        System.out.println();
        System.out.println("=== 金额为什么不能用 double ===");
        System.out.println("  0.1 + 0.2 = " + (0.1 + 0.2));
        System.out.println("  用分做整数：10 + 20 = " + (10 + 20) + " 分 = 0.30 元");

        System.out.println();
        System.out.println("=== 校验规则表 ===");
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("name", "非空，2~64 字符");
        rules.put("priceCent", "必填，> 0，单位分");
        rules.put("stock", "必填，0~999999");
        rules.put("description", "可选，<= 500 字符");
        rules.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
    }

    /** 只把白名单字段搬进 DTO，等价于 Jackson 绑定到固定字段 */
    static ItemCreateDTO fromRaw(Map<String, Object> raw) {
        return new ItemCreateDTO(
                (String) raw.get("name"),
                (Integer) raw.get("priceCent"),
                (Integer) raw.get("stock"),
                (String) raw.get("description"));
    }

    /** 格式校验：不查库就能判断的都在这里 */
    static List<String> validate(ItemCreateDTO dto) {
        List<String> errors = new ArrayList<>();
        if (dto.name == null || dto.name.isBlank()) {
            errors.add("name 不能为空");
        } else if (dto.name.trim().length() < 2 || dto.name.trim().length() > 64) {
            errors.add("name 长度必须在 2~64 之间，当前 " + dto.name.trim().length());
        }
        if (dto.priceCent == null) {
            errors.add("priceCent 不能为空");
        } else if (dto.priceCent <= 0) {
            errors.add("priceCent 必须大于 0，当前 " + dto.priceCent);
        } else if (dto.priceCent > 100_000_000) {
            errors.add("priceCent 超出上限（100 万元）");
        }
        if (dto.stock == null) {
            errors.add("stock 不能为空");
        } else if (dto.stock < 0 || dto.stock > 999_999) {
            errors.add("stock 必须在 0~999999 之间，当前 " + dto.stock);
        }
        if (dto.description != null && dto.description.length() > 500) {
            errors.add("description 不能超过 500 字符，当前 " + dto.description.length());
        }
        return errors;
    }

    /** 接口入口：校验 -> DTO 转 Entity -> Entity 转 VO */
    static String create(ItemCreateDTO dto) {
        List<String> errors = validate(dto);
        if (!errors.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", 400);
            body.put("msg", "参数校验失败");
            body.put("data", errors);
            return body.toString();
        }
        ItemEntity entity = toEntity(dto);
        entity.id = 1L;
        return "201 " + toVo(entity);
    }

    static ItemEntity toEntity(ItemCreateDTO dto) {
        ItemEntity e = new ItemEntity();
        e.name = dto.name.trim();
        e.priceCent = dto.priceCent;
        e.stock = dto.stock;
        e.description = dto.description == null ? "" : dto.description;
        return e;                     // status/ownerId 用默认值，前端无权指定
    }

    static Map<String, Object> toVo(ItemEntity e) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", e.id);
        vo.put("name", e.name);
        vo.put("price", String.format("%.2f", e.priceCent / 100.0));
        vo.put("stock", e.stock);
        vo.put("status", e.status);
        return vo;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `ItemCreateDTO` 增加 `categoryId` 字段，要求"必填且必须存在"，说明哪个校验放 DTO、哪个放 Service。
 * 2. 为什么"遇到第一个错误就 return"对前端不友好？如果错误有 10 条，怎么设计返回结构让前端能定位到具体字段？
 * 3. 用 `double` 存价格，在"打 8.5 折"和"累加 1000 笔订单"两个场景下分别会出现什么偏差？用分做整数怎么避免？
 * ============================================================
 */
