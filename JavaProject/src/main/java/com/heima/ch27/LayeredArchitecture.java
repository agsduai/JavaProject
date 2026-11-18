/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-02
 * 课名：分层架构：Controller、Service、Repository
 * ------------------------------------------------------------
 * 知识点：
 *   - 四层职责：`Controller` 只做"收参数、调 Service、包响应"，不写业务；`Service` 只写业务规则与事务边界；`Repository/Mapper` 只做数据访问；`Entity/DTO/VO` 只做数据载体。
 *   - 依赖方向必须单向：Controller -> Service -> Repository -> 数据库。反向依赖（Repository 调 Service）会形成环，编译能过但运行期难测。
 *   - 面向接口编程：Service 依赖 `ItemRepository` 接口而不是 `MemoryItemRepository` 实现，这样换存储、加缓存、写单测替换成假实现都不用改业务代码。
 *   - 装饰器模式加缓存：写一个 `CachingItemRepository` 实现同一个接口，内部包一层真实实现，Service 完全不知道缓存的存在。
 *   - 事务边界在 Service：一个业务用例可能调多个 Repository 方法，只有 Service 知道"这几步必须一起成功"。
 *   - Controller 不要直接返回 Entity：Entity 跟着表结构变，接口契约会跟着抖；用 VO 隔离。
 *   - 易错：Service 之间互相注入形成环（A 依赖 B，B 依赖 A），Spring 启动直接报循环依赖；把 `HttpServletRequest` 传进 Service，业务层被 Web 容器绑死，单测跑不起来。
 * ============================================================
 */

package com.heima.ch27;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// ==================== 示例代码 ====================

public class LayeredArchitecture {
    public static void main(String[] args) {
        // 组装依赖：像 Spring 容器那样在最外层把实现塞进去
        ItemRepository real = new MemoryItemRepository();
        ItemRepository withCache = new CachingItemRepository(real);
        ItemService service = new ItemService(withCache);
        ItemController controller = new ItemController(service);

        System.out.println("=== 新增 ===");
        System.out.println("  " + controller.create("机械键盘", 39900));
        System.out.println("  " + controller.create("显示器", 129900));

        System.out.println();
        System.out.println("=== 查询（第一次走库，第二次走缓存）===");
        System.out.println("  " + controller.detail(1L));
        System.out.println("  " + controller.detail(1L));
        System.out.println("  " + controller.detail(999L));

        System.out.println();
        System.out.println("=== 列表 ===");
        System.out.println("  " + controller.list());

        System.out.println();
        System.out.println("=== 改价：Service 负责清缓存 ===");
        System.out.println("  " + controller.updatePrice(1L, 35900));
        System.out.println("  " + controller.detail(1L));

        System.out.println();
        System.out.println("=== 底层换了实现，上层一行没改 ===");
        ItemRepository another = new MemoryItemRepository();
        another.save(new Item(1L, "机械键盘", 39900));
        System.out.println("  " + new ItemController(new ItemService(another)).detail(1L));

        System.out.println();
        System.out.println("=== 各层职责 ===");
        Map<String, String> duties = new LinkedHashMap<>();
        duties.put("Controller", "收参数、校验格式、调 Service、包统一响应");
        duties.put("Service", "业务规则、事务边界、编排多个 Repository");
        duties.put("Repository", "只做增删改查，不含业务判断");
        duties.put("Entity/VO", "数据载体，Entity 对表，VO 对接口");
        duties.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
    }

    /** 数据载体 */
    static class Item {
        Long id;
        String name;
        int priceCent;

        Item(Long id, String name, int priceCent) {
            this.id = id;
            this.name = name;
            this.priceCent = priceCent;
        }

        Item copy() {
            return new Item(id, name, priceCent);
        }
    }

    /** 数据访问接口：Service 只认这个 */
    interface ItemRepository {
        Item save(Item item);

        Optional<Item> findById(Long id);

        List<Item> findAll();

        boolean updatePrice(Long id, int priceCent);
    }

    /** 内存实现，模拟 MySQL */
    static class MemoryItemRepository implements ItemRepository {
        private final Map<Long, Item> table = new LinkedHashMap<>();
        private long seq = 0;

        @Override
        public Item save(Item item) {
            Item stored = item.copy();
            stored.id = ++seq;
            table.put(stored.id, stored);
            return stored.copy();
        }

        @Override
        public Optional<Item> findById(Long id) {
            return Optional.ofNullable(table.get(id)).map(Item::copy);
        }

        @Override
        public List<Item> findAll() {
            List<Item> list = new ArrayList<>();
            for (Item i : table.values()) {
                list.add(i.copy());
            }
            return list;
        }

        @Override
        public boolean updatePrice(Long id, int priceCent) {
            Item i = table.get(id);
            if (i == null) {
                return false;
            }
            i.priceCent = priceCent;
            return true;
        }
    }

    /** 装饰器：加缓存，不侵入业务 */
    static class CachingItemRepository implements ItemRepository {
        private final ItemRepository delegate;
        private final Map<Long, Item> cache = new LinkedHashMap<>();
        int hits;
        int misses;

        CachingItemRepository(ItemRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Item save(Item item) {
            return delegate.save(item);
        }

        @Override
        public Optional<Item> findById(Long id) {
            Item cached = cache.get(id);
            if (cached != null) {
                hits++;
                System.out.println("    [cache] hit id=" + id + " 命中次数=" + hits);
                return Optional.of(cached.copy());
            }
            misses++;
            Optional<Item> fromDb = delegate.findById(id);
            fromDb.ifPresent(i -> {
                cache.put(i.id, i.copy());
                System.out.println("    [cache] miss id=" + id + "，已回填，未命中次数=" + misses);
            });
            return fromDb;
        }

        @Override
        public List<Item> findAll() {
            return delegate.findAll();
        }

        @Override
        public boolean updatePrice(Long id, int priceCent) {
            cache.remove(id);
            return delegate.updatePrice(id, priceCent);
        }
    }

    /** 业务层：只写规则，不碰 HTTP，也不碰 SQL */
    static class ItemService {
        private final ItemRepository repository;

        ItemService(ItemRepository repository) {
            this.repository = repository;
        }

        Item create(String name, int priceCent) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("商品名不能为空");
            }
            if (priceCent <= 0) {
                throw new IllegalArgumentException("价格必须大于 0");
            }
            return repository.save(new Item(null, name.trim(), priceCent));
        }

        Item detail(Long id) {
            return repository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("商品不存在: " + id));
        }

        List<Item> list() {
            return repository.findAll();
        }

        Item updatePrice(Long id, int priceCent) {
            if (priceCent <= 0) {
                throw new IllegalArgumentException("价格必须大于 0");
            }
            if (!repository.updatePrice(id, priceCent)) {
                throw new IllegalStateException("商品不存在: " + id);
            }
            return detail(id);
        }
    }

    /** 接口层：只负责参数和响应 */
    static class ItemController {
        private final ItemService service;

        ItemController(ItemService service) {
            this.service = service;
        }

        String create(String name, int priceCent) {
            try {
                Item item = service.create(name, priceCent);
                return "201 " + toVo(item);
            } catch (RuntimeException e) {
                return "400 " + e.getMessage();
            }
        }

        String detail(Long id) {
            try {
                return "200 " + toVo(service.detail(id));
            } catch (RuntimeException e) {
                return "404 " + e.getMessage();
            }
        }

        String list() {
            List<Map<String, Object>> vos = new ArrayList<>();
            for (Item i : service.list()) {
                vos.add(toVo(i));
            }
            return "200 " + vos;
        }

        String updatePrice(Long id, int priceCent) {
            try {
                return "200 " + toVo(service.updatePrice(id, priceCent));
            } catch (IllegalArgumentException e) {
                return "400 " + e.getMessage();
            } catch (RuntimeException e) {
                return "404 " + e.getMessage();
            }
        }

        /** Entity -> VO：价格按元显示，不暴露内部字段 */
        static Map<String, Object> toVo(Item item) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", item.id);
            vo.put("name", item.name);
            vo.put("price", String.format("%.2f", item.priceCent / 100.0));
            return vo;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 如果要给 `ItemService` 加"创建商品时校验名称不能重复"，这段逻辑应该写在哪一层？为什么不能写在 Controller？
 * 2. 用装饰器实现"限流 Repository"（每秒最多查 100 次），说说它和把限流写在 Service 里相比有什么好处。
 * 3. 为什么 Controller 不能直接返回 Entity？举一个"给表加字段导致接口契约变化"的具体例子。
 * ============================================================
 */
