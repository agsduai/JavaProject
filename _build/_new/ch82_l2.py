# -*- coding: utf-8 -*-
LESSONS = [
    dict(
        cls="RampupGate",
        title="放量阶梯与自动化门禁",
        points=[
            "**放量不是「1% 没问题就上 100%」**：**1% → 5% → 25% → 50% → 100% 每一档都要停下来看**——**观察窗口内样本量不够，等于没看**。",
            "**门禁要三类指标同时看**：**技术指标（错误率、P99、超时率）**、**业务指标（转化率、下单量）**、**资源指标（CPU、内存、连接池）**——**只看技术指标会放过「能跑但没人买」的版本**。",
            "**观察窗口要够长**：**样本量 n 与「要发现的最小差异」成反比**——**想发现 0.5% 的转化率差异，1% 流量下可能要等几小时**，**所以小流量档只卡技术指标，业务指标留到大流量档再判**。",
            "**门禁失败要能自动停**：**不是「报警然后等人」，是「自动把流量切回上一档」**——**人从看到告警到动手平均 5~10 分钟，这段损失比自动停大得多**。",
            "**放量节奏要和业务节律对齐**：**别在流量高峰前 10 分钟放量**，**别在没人的凌晨放到 100% 然后早上出事**——**放量窗口本身就是个决策**。",
            "易错点：1% 跑 5 分钟就上 100%；门禁阈值写死不看基线（大促期间错误率天然更高）；只卡错误率不卡延迟；失败只告警不自动回退。",
        ],
        code=r'''import java.util.LinkedHashMap;
import java.util.Map;

public class RampupGate {
    // 每一档的配置：流量比例、观察窗口、最小样本量、门禁阈值
    record Stage(int pct, int windowSec, long minSamples,
                 double maxErrRate, double maxP99Ms, double minCvr) {
    }

    // 实测数据
    record Observed(long samples, double errRate, double p99Ms, double cvr) {
    }

    static final Map<Integer, Stage> STAGES = new LinkedHashMap<>();

    static {
        STAGES.put(1, new Stage(1, 600, 20_000, 0.005, 300, 0.030));
        STAGES.put(5, new Stage(5, 900, 60_000, 0.005, 300, 0.030));
        STAGES.put(25, new Stage(25, 1800, 200_000, 0.008, 350, 0.029));
        STAGES.put(50, new Stage(50, 1800, 400_000, 0.008, 350, 0.029));
        STAGES.put(100, new Stage(100, 0, 0, 0.010, 400, 0.028));
    }

    // 返回 null 表示通过，否则返回失败原因
    static String gate(Stage s, Observed o) {
        if (o.samples() < s.minSamples()) {
            return String.format("样本不足：%d < %d，观察窗口没跑满，不能判",
                    o.samples(), s.minSamples());
        }
        if (o.errRate() > s.maxErrRate()) {
            return String.format("错误率 %.3f%% > %.3f%%", o.errRate() * 100, s.maxErrRate() * 100);
        }
        if (o.p99Ms() > s.maxP99Ms()) {
            return String.format("P99 %.0fms > %.0fms", o.p99Ms(), s.maxP99Ms());
        }
        if (o.cvr() < s.minCvr()) {
            return String.format("转化率 %.2f%% < %.2f%%", o.cvr() * 100, s.minCvr() * 100);
        }
        return null;
    }

    public static void main(String[] args) {
        // 模拟一次放量：5% 档出现延迟劣化，其余档正常
        Map<Integer, Observed> actual = new LinkedHashMap<>();
        actual.put(1, new Observed(25_000, 0.0012, 240, 0.0312));
        actual.put(5, new Observed(72_000, 0.0018, 520, 0.0305));    // P99 超标
        actual.put(25, new Observed(210_000, 0.0021, 300, 0.0301));
        actual.put(50, new Observed(430_000, 0.0025, 310, 0.0298));
        actual.put(100, new Observed(900_000, 0.0028, 320, 0.0295));

        System.out.println("【放量阶梯与门禁】");
        System.out.printf("%-6s %-8s %-10s %-9s %-9s %-9s %s%n",
                "档位", "观察窗口", "样本量", "错误率", "P99", "转化率", "门禁");
        boolean stopped = false;
        for (Map.Entry<Integer, Stage> e : STAGES.entrySet()) {
            Stage s = e.getValue();
            Observed o = actual.get(e.getKey());
            String fail = gate(s, o);
            String verdict;
            if (stopped) {
                verdict = "未执行（上一档已拦下）";
            } else if (fail == null) {
                verdict = "通过";
            } else {
                verdict = "拦截 -> " + fail;
                stopped = true;
            }
            System.out.printf("%-6s %-8s %-10s %-9s %-9s %-9s %s%n",
                    s.pct() + "%", s.windowSec() + "s", o.samples(),
                    String.format("%.2f%%", o.errRate() * 100),
                    String.format("%.0fms", o.p99Ms()),
                    String.format("%.2f%%", o.cvr() * 100),
                    verdict);
        }

        System.out.println();
        System.out.println("【读结论】");
        System.out.println("  1. 5% 档 P99 520ms 超过 300ms 阈值 -> 自动停在 5%，25% 及以后都没执行");
        System.out.println("  2. 1% 档样本 2.5 万 >= 门槛 2 万，判得动；5% 档 7.2 万 >= 6 万，也判得动");
        System.out.println("  3. 两档样本都够，所以 5% 档的拦截结论可信——不是「数据太少误判」");

        System.out.println();
        System.out.println("【样本不足时的正确反应】");
        Observed early = new Observed(3_000, 0.0010, 250, 0.0310);
        System.out.println("  1% 档只跑了 3000 个样本就去看结果 -> "
                + gate(STAGES.get(1), early));

        System.out.println();
        System.out.println("【小流量档只卡技术指标的原因】");
        System.out.println("  转化率基线 3%，想发现 0.3pp 的提升（3.0% -> 3.3%），每组需要约 5.3 万样本");
        System.out.println("  1% 档 10 分钟只有 2.5 万样本，只有需求的 47% -> 业务指标在这个档位根本判不出来");
        System.out.println("  -> 所以 1%/5% 档只卡错误率与 P99，转化率留到 25% 以后");

        System.out.println();
        System.out.println("【自动停 vs 人工停】");
        System.out.println("  自动停：告警触发即切回上一档，生效 5s");
        System.out.println("  人工停：告警 -> 值班看到 -> 确认 -> 执行，平均 5~10 分钟");
        System.out.println("  5 分钟 × 5% 流量 × 2 万 QPS = 30 万次请求在错误版本上");
    }
}''',
        exercises=[
            "把 5% 档的 P99 阈值改成 600ms，重跑，观察能放到哪一档，说明「阈值定松」的代价。",
            "给 gate 加一条「错误率相对基线涨幅 > 50%」的相对判据，说明为什么绝对值阈值在大促期间会失效。",
            "思考题：为什么 100% 档的观察窗口是 0？全量之后还需要门禁吗？",
        ],
    ),
    dict(
        cls="SchemaCompat",
        title="发布期的数据兼容：expand-contract",
        points=[
            "**滚动/灰度期间新旧版本同时在线**：**旧代码读新表结构会崩，新代码读旧数据会崩**——**所以数据库变更必须拆成「先兼容、再切换、后清理」三步**。",
            "**Expand（扩展）**：**加新字段/新表，新旧代码都能跑**——**新字段必须可空或有默认值**，**旧代码不认识它就当不存在**。",
            "**Migrate（双写/回填）**：**新代码同时写新旧两处，后台把历史数据回填**——**回填要能重跑、要限速**，**不能把库打挂**。",
            "**Contract（收缩）**：**确认所有实例都是新版本、所有数据都回填完，才能删旧字段**——**删早了，还没升级的实例立刻 500**。",
            "**三条铁律**：**加字段不删字段、改语义要加新字段、删字段要等两个发布周期**——**「两个周期」是给回滚留的余地**。",
            "易错点：一个发布里同时加字段又删字段；新字段设 NOT NULL 无默认值（旧代码插入失败）；回填不限速（打挂主库）；以为「代码回滚了数据也回来了」。",
        ],
        code=r'''import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchemaCompat {
    enum Phase { EXPAND, MIGRATE, CONTRACT }

    // 一行数据：旧字段 price(分)，新字段 priceYuan(字符串，带两位小数)
    static class Row {
        Integer priceFen;          // 旧字段，可空
        String priceYuan;          // 新字段，可空

        Row(Integer priceFen, String priceYuan) {
            this.priceFen = priceFen;
            this.priceYuan = priceYuan;
        }
    }

    static class Table {
        final List<Row> rows = new ArrayList<>();
        Phase phase = Phase.EXPAND;
        int errors;

        // 旧代码：只认 priceFen
        void writeOld(int fen) {
            Row r = new Row(fen, null);
            if (phase == Phase.CONTRACT) {
                errors++;
                System.out.println("    [旧代码写] 失败：priceFen 字段已被删除（NPE / Unknown column）");
                return;
            }
            rows.add(r);
        }

        // 新代码：两个都写
        void writeNew(int fen) {
            String yuan = String.format("%d.%02d", fen / 100, fen % 100);
            rows.add(new Row(phase == Phase.CONTRACT ? null : fen, yuan));
        }

        // 旧代码读：优先旧字段
        String readOld(int idx) {
            Row r = rows.get(idx);
            if (r.priceFen == null) {
                errors++;
                return "<读不到 priceFen>";
            }
            return "RMB " + String.format("%.2f", r.priceFen / 100.0);
        }

        // 新代码读：优先新字段，回退旧字段（兼容未回填的数据）
        String readNew(int idx) {
            Row r = rows.get(idx);
            if (r.priceYuan != null) {
                return "RMB " + r.priceYuan;
            }
            if (r.priceFen != null) {
                return "RMB " + String.format("%.2f", r.priceFen / 100.0) + "(旧字段回退)";
            }
            return "<两个字段都空>";
        }

        int backfill() {
            int n = 0;
            for (Row r : rows) {
                if (r.priceYuan == null && r.priceFen != null) {
                    r.priceYuan = String.format("%d.%02d", r.priceFen / 100, r.priceFen % 100);
                    n++;
                }
            }
            return n;
        }
    }

    public static void main(String[] args) {
        Table t = new Table();

        System.out.println("【阶段1 EXPAND】加新字段 priceYuan（可空），旧代码照常跑");
        t.writeOld(1999);
        t.writeOld(2580);
        System.out.println("  旧代码写入 2 行，新代码读取：");
        System.out.println("    row0 -> " + t.readNew(0));
        System.out.println("    row1 -> " + t.readNew(1));

        System.out.println();
        System.out.println("【阶段2 MIGRATE】新代码双写 + 回填历史数据");
        t.phase = Phase.MIGRATE;
        t.writeNew(3999);
        int filled = t.backfill();
        System.out.println("  双写 1 行，回填历史 " + filled + " 行");
        for (int i = 0; i < t.rows.size(); i++) {
            System.out.printf("    row%d 旧代码读 %-10s 新代码读 %s%n",
                    i, t.readOld(i), t.readNew(i));
        }
        System.out.println("  -> 两个版本读出来的价格完全一致，可以开始收缩");

        System.out.println();
        System.out.println("【阶段3 CONTRACT】删除旧字段 priceFen");
        t.phase = Phase.CONTRACT;
        for (Row r : t.rows) {
            r.priceFen = null;                    // 列被 DROP，历史行的旧值也一起没了
        }
        System.out.println("  如果此时还有旧实例在线：");
        t.writeOld(1000);
        System.out.println("  旧代码读历史行：" + t.readOld(0));
        System.out.println("  新代码不受影响：" + t.readNew(0));
        System.out.printf("  -> 累计错误 %d 次；这就是为什么删字段要等所有实例都升级完%n", t.errors);

        System.out.println();
        System.out.println("【错误做法对照】一个发布里同时加新字段 + 删旧字段");
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("发布中", "旧实例还在跑，新表结构已经生效");
        bad.put("旧实例读", "SELECT priceFen -> Unknown column，接口 500");
        bad.put("回滚", "代码回滚了，但字段已经被 DROP，数据回不来");
        bad.put("正确做法", "expand -> migrate -> contract 三个发布，每步都可回滚");
        bad.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}''',
        exercises=[
            "把 CONTRACT 阶段改成「先停旧实例再删字段」，重跑，验证错误次数变成 0。",
            "给 backfill 加限速（每批 100 行、批次间隔 50ms），统计 1 万行数据要回填多久。",
            "思考题：为什么「改字段语义」也要加新字段，而不是直接改旧字段的含义？从「旧代码如何解释这个值」分析。",
        ],
    ),
    dict(
        cls="AutoRollback",
        title="指标对比与自动回滚判据",
        points=[
            "**自动回滚的第一原则：判据要能抵抗噪声**——**单点抖动就回滚会造成「回滚风暴」**，**必须用「连续 N 个窗口」或「滑动窗口累计」**。",
            "**绝对阈值 + 相对阈值双保险**：**绝对阈值防「本来就差」**（错误率 > 1%），**相对阈值防「比基线差很多」**（错误率是基线的 3 倍）——**大促期间基线本身就高，只看绝对阈值会漏**。",
            "**业务指标的回滚要更保守**：**技术指标（错误率/延迟）可以秒级判定**，**业务指标（转化率）有天然波动，要等样本量够 + 连续 2~3 个窗口都差**才回滚。",
            "**回滚动作要分级**：**一级 = 关开关（亚秒）**、**二级 = 切流量回上一档（秒级）**、**三级 = 回滚代码（分钟级）**——**能用一级解决就别用三级**。",
            "**回滚后必须验证**：**回滚动作发出 ≠ 已恢复**，**要看指标真的回到基线**——**很多事故是「回滚了但没生效」，因为配置缓存没刷新**。",
            "易错点：单窗口判定就回滚（噪声触发）；只看绝对阈值；回滚后不看指标确认；回滚了代码但缓存/连接池里还是旧配置。",
        ],
        code=r'''import java.util.ArrayList;
import java.util.List;

public class AutoRollback {
    // 一个观测窗口的数据
    record Window(int idx, long requests, double errRate, double p99Ms, double cvr) {
    }

    record Baseline(double errRate, double p99Ms, double cvr) {
    }

    // 回滚判据：连续 N 个窗口不达标才回滚
    static class Judge {
        final Baseline base;
        final int consecutive;
        final List<String> history = new ArrayList<>();
        int badStreak;

        Judge(Baseline base, int consecutive) {
            this.base = base;
            this.consecutive = consecutive;
        }

        boolean fired;

        // 返回 null 表示继续观察，否则返回回滚原因
        String evaluate(Window w) {
            if (fired) {
                history.add("窗口" + w.idx() + "：已回滚，不再判定");
                return "已回滚";
            }
            List<String> reasons = new ArrayList<>();

            // 技术指标：绝对阈值
            if (w.errRate() > 0.01) {
                reasons.add(String.format("错误率 %.2f%% > 绝对阈值 1.00%%", w.errRate() * 100));
            }
            // 技术指标：相对阈值（基线 3 倍）
            if (w.errRate() > base.errRate() * 3) {
                reasons.add(String.format("错误率是基线 %.1f 倍（%.2f%% vs %.2f%%）",
                        w.errRate() / base.errRate(), w.errRate() * 100, base.errRate() * 100));
            }
            if (w.p99Ms() > base.p99Ms() * 2) {
                reasons.add(String.format("P99 是基线 %.1f 倍（%.0fms vs %.0fms）",
                        w.p99Ms() / base.p99Ms(), w.p99Ms(), base.p99Ms()));
            }
            // 业务指标：相对跌幅 > 20%
            if (w.cvr() < base.cvr() * 0.8) {
                reasons.add(String.format("转化率跌到基线的 %.0f%%（%.2f%% vs %.2f%%）",
                        w.cvr() / base.cvr() * 100, w.cvr() * 100, base.cvr() * 100));
            }

            if (reasons.isEmpty()) {
                badStreak = 0;
                history.add("窗口" + w.idx() + "：正常");
                return null;
            }
            badStreak++;
            String joined = String.join("；", reasons);
            if (badStreak >= consecutive) {
                fired = true;
                history.add("窗口" + w.idx() + "：连续 " + badStreak + " 个窗口不达标 -> 回滚");
                return joined + "（连续 " + badStreak + " 个窗口）";
            }
            history.add("窗口" + w.idx() + "：不达标但只连续 " + badStreak + " 个，继续观察");
            return null;
        }
    }

    public static void main(String[] args) {
        Baseline base = new Baseline(0.002, 250, 0.030);

        System.out.println("【场景1】单点抖动：第 3 个窗口 P99 突然飙到 900ms，随后立刻恢复");
        Judge j1 = new Judge(base, 2);
        List<Window> run1 = List.of(
                new Window(1, 50_000, 0.0020, 255, 0.0301),
                new Window(2, 50_000, 0.0022, 248, 0.0299),
                new Window(3, 50_000, 0.0021, 900, 0.0300),   // 抖动
                new Window(4, 50_000, 0.0019, 252, 0.0302),
                new Window(5, 50_000, 0.0020, 249, 0.0300));
        for (Window w : run1) {
            String r = j1.evaluate(w);
            System.out.printf("  窗口%d P99=%4.0fms -> %s%n", w.idx(), w.p99Ms(),
                    r == null ? "继续" : "回滚：" + r);
        }
        System.out.println("  结论：只抖动一个窗口，被「连续 2 个窗口」规则挡住了，不回滚");

        System.out.println();
        System.out.println("【场景2】真的坏了：从第 2 个窗口起错误率一直是基线的 3.7 倍以上");
        Judge j2 = new Judge(base, 2);
        List<Window> run2 = List.of(
                new Window(1, 50_000, 0.0020, 250, 0.0300),
                new Window(2, 50_000, 0.0075, 260, 0.0298),   // 3.75 倍
                new Window(3, 50_000, 0.0080, 265, 0.0295),   // 连续第 2 个
                new Window(4, 50_000, 0.0082, 270, 0.0290));
        for (Window w : run2) {
            String r = j2.evaluate(w);
            System.out.printf("  窗口%d 错误率=%.2f%% -> %s%n", w.idx(), w.errRate() * 100,
                    r == null ? "继续" : (r.equals("已回滚") ? "已回滚，后续窗口不再判定" : "回滚：" + r));
        }

        System.out.println();
        System.out.println("【场景3】业务指标跌了：转化率 3.00% -> 2.30%，技术指标全正常");
        Judge j3 = new Judge(base, 2);
        List<Window> run3 = List.of(
                new Window(1, 50_000, 0.0021, 251, 0.0230),
                new Window(2, 50_000, 0.0020, 249, 0.0228),
                new Window(3, 50_000, 0.0022, 253, 0.0231));
        for (Window w : run3) {
            String r = j3.evaluate(w);
            System.out.printf("  窗口%d 转化率=%.2f%% -> %s%n", w.idx(), w.cvr() * 100,
                    r == null ? "继续" : (r.equals("已回滚") ? "已回滚，后续窗口不再判定" : "回滚：" + r));
        }
        System.out.println("  结论：技术指标全绿也会回滚——「能跑但没人买」同样是事故");

        System.out.println();
        System.out.println("【回滚分级】");
        System.out.println("  一级 关开关    ：<1s，适用「新逻辑可以整体关掉」");
        System.out.println("  二级 切流量    ：~5s，适用「旧版本实例还在」");
        System.out.println("  三级 回滚代码  ：2~10min，适用「开关兜不住的结构性问题」");
        System.out.println("  原则：能一级解决就别用三级；每次回滚后必须确认指标真的回到基线");
    }
}''',
        exercises=[
            "把 consecutive 从 2 改成 1，重跑场景1，观察抖动是否触发回滚，说明「回滚风暴」是怎么来的。",
            "给 Judge 加一条「连续 5 个窗口错误率缓慢上升」的漂移检测，说明为什么单窗口判定抓不到慢故障。",
            "思考题：为什么业务指标的回滚阈值要用「相对基线的百分比」而不是绝对值？从「不同业务的转化率量级不同」分析。",
        ],
    ),
    dict(
        cls="ExperimentLayers",
        title="实验分层与正交设计",
        points=[
            "**一个用户可能同时在多个实验里**：**推荐算法实验 + UI 改版实验 + 价格展示实验**——**如果都从同一份流量里随机切，实验之间会互相污染**。",
            "**分层（Layer）**：**把流量按维度切成互不重叠的层**，**不同层用不同的 hash 种子**——**同一层的实验互斥，不同层的实验正交**。",
            "**正交的含义**：**A 实验的对照组/实验组里，B 实验的分组比例都应该是 50/50**——**这样才能各自独立归因**，**用交叉表就能验证**。",
            "**互斥（同层）**：**同一层内流量只能进一个实验**——**否则两个改版叠加，你不知道是谁的功劳**，**但会浪费流量（流量不够时要做流量复用）**。",
            "**分层的代价**：**层数越多，每层能用的流量越少**——**层是「互斥换清晰」，正交是「复用换效率」**，**两者要按实验数量权衡**。",
            "易错点：hash 的 key 里不带层名（同一个用户在所有层分组完全一致）；同层放两个实验（互相污染）；分层后忘了验证交叉表（以为正交其实不正交）。",
        ],
        code=r'''import java.util.LinkedHashMap;
import java.util.Map;

public class ExperimentLayers {
    // 稳定分桶，种子不同 -> 不同层互相独立
    static int bucket(String userId, int seed, int mod) {
        int h = seed;
        for (int i = 0; i < userId.length(); i++) {
            h ^= userId.charAt(i);
            h *= 0x01000193;
        }
        return Math.floorMod(h, mod);
    }

    // 一层的分流：0 = 对照组，1 = 实验组。key 里带层名 -> 同一个人在不同层的分组互相独立
    static int assign(String userId, String layer, int seed) {
        return bucket(userId + ":" + layer, seed, 100) < 50 ? 1 : 0;
    }

    // 错误做法：key 里不带层名，同一个 seed 就会让两层完全相关
    static int assignSharedKey(String userId, int seed) {
        return bucket(userId, seed, 100) < 50 ? 1 : 0;
    }

    public static void main(String[] args) {
        String[] layers = {"推荐算法", "UI改版", "价格展示"};
        int[] seeds = {0x811C9DC5, 0x12345678, 0x9E3779B9};

        int n = 200_000;
        // 统计：每个用户在三层的分组组合
        Map<String, Integer> cross = new LinkedHashMap<>();
        int[] solo = new int[3];                 // 每层实验组人数
        for (int i = 0; i < n; i++) {
            String u = "u" + i;
            int a = assign(u, layers[0], seeds[0]);
            int b = assign(u, layers[1], seeds[1]);
            int c = assign(u, layers[2], seeds[2]);
            solo[0] += a;
            solo[1] += b;
            solo[2] += c;
            cross.merge("" + a + b + c, 1, Integer::sum);
        }

        System.out.println("【三层实验，各自 50/50】共 " + n + " 个用户");
        for (int i = 0; i < layers.length; i++) {
            System.out.printf("  %-10s 实验组 %6d (%.2f%%)%n",
                    layers[i], solo[i], solo[i] * 100.0 / n);
        }

        System.out.println();
        System.out.println("【正交验证：A 实验分组 × B 实验分组的交叉表】");
        System.out.println("  如果两层独立，每个格子的占比都应该接近 25%");
        Map<String, Integer> pair = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String u = "u" + i;
            int a = assign(u, layers[0], seeds[0]);
            int b = assign(u, layers[1], seeds[1]);
            pair.merge("" + a + b, 1, Integer::sum);
        }
        System.out.printf("%-12s %-18s %-18s%n", "", "B=对照", "B=实验");
        for (int a = 0; a <= 1; a++) {
            System.out.printf("%-12s", "A=" + (a == 0 ? "对照" : "实验"));
            for (int b = 0; b <= 1; b++) {
                int cnt = pair.getOrDefault("" + a + b, 0);
                System.out.printf("%-18s", cnt + String.format(" (%.2f%%)", cnt * 100.0 / n));
            }
            System.out.println();
        }
        System.out.println("  -> 四个格子都在 25% 附近，说明两层正交，可以各自独立归因");

        System.out.println();
        System.out.println("【对照：key 不带层名 + 同一个种子 -> 完全相关，不正交】");
        Map<String, Integer> badPair = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String u = "u" + i;
            int a = assignSharedKey(u, seeds[0]);
            int b = assignSharedKey(u, seeds[0]);     // 同一个 key + 同一个种子
            badPair.merge("" + a + b, 1, Integer::sum);
        }
        System.out.printf("%-12s %-18s %-18s%n", "", "B=对照", "B=实验");
        for (int a = 0; a <= 1; a++) {
            System.out.printf("%-12s", "A=" + (a == 0 ? "对照" : "实验"));
            for (int b = 0; b <= 1; b++) {
                int cnt = badPair.getOrDefault("" + a + b, 0);
                System.out.printf("%-18s", cnt + String.format(" (%.2f%%)", cnt * 100.0 / n));
            }
            System.out.println();
        }
        System.out.println("  -> 只有对角两个格子有数据：进了 A 实验的人必然也进 B 实验");
        System.out.println("     这时 B 的效果里混着 A 的效果，两个实验的结论都不可信");
        System.out.println("     修法：bucket 的 key 里带上层名（本课 assign 的写法），或每层换一个种子");

        System.out.println();
        System.out.println("【8 种组合的分布】三层全交叉，应该均匀分成 8 份（各 12.5%）");
        cross.forEach((k, v) -> System.out.printf("  A/B/C=%s : %6d (%.2f%%)%n", k, v, v * 100.0 / n));

        System.out.println();
        System.out.println("【结论】层内互斥保证「同一份流量不打架」，跨层换种子保证「互不相关」；");
        System.out.println("  验证手段就是交叉表——格子均匀才叫正交，不均匀说明种子或分组逻辑有问题。");
    }
}''',
        exercises=[
            "把 B 层的种子改成和 A 层相同，重跑交叉表，观察四个格子如何塌成两个。",
            "加第四层（「新人引导」实验），验证 16 种组合是否各占 6.25%。",
            "思考题：为什么「同层放两个实验」会让归因失效？举一个「两个改版都提升转化率」的具体例子。",
        ],
    ),
]
