package com.freshmart.wms.config;

import com.freshmart.wms.domain.entity.*;
import com.freshmart.wms.domain.enums.*;
import com.freshmart.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 内存种子数据初始化器。
 *
 * <p><b>为什么需要它</b>：当前项目未接数据库，若空库启动，前端所有列表都是空的，
 * 无法验证页面与接口。这里造一份「看起来像真的」的数据集。
 *
 * <p><b>数据分两类</b>：
 * <ol>
 *   <li><b>历史数据</b> —— 7 个波次，覆盖从「已分配」到「已归档」的完整状态谱，
 *       并附带拣货任务、缺货记录、复核记录、交接单，供效率复盘页面出图。</li>
 *   <li><b>待作业数据</b> —— 20 笔「新建待汇聚」订单 + 12 笔「已入池」订单，
 *       留给使用者亲手走一遍 汇聚 → 生成波次 → 拣货 → 复核 → 交接 的闭环。</li>
 * </ol>
 *
 * <p>随机数使用固定种子，保证每次启动得到同一份数据，便于对照排查。
 *
 * <p><b>接库后</b>：把 {@code wms.mock.enabled} 置为 false 即可关闭本初始化器。
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wms.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockDataInitializer implements ApplicationRunner {

    private final CarrierRepository carrierRepo;
    private final EmployeeRepository employeeRepo;
    private final LocationRepository locationRepo;
    private final SkuRepository skuRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final WaveRepository waveRepo;
    private final WaveOrderRepository waveOrderRepo;
    private final PickTaskRepository pickTaskRepo;
    private final PickItemRepository pickItemRepo;
    private final ShortageRepository shortageRepo;
    private final ReviewRepository reviewRepo;
    private final ReviewDiffRepository reviewDiffRepo;
    private final HandoverRepository handoverRepo;
    private final OperationLogRepository logRepo;

    private final Random random = new Random(20261016L);

    /** 业务单号发号器，形如 SO20261016000001。 */
    private final Map<String, Integer> seqMap = new HashMap<>();

    /* ======================================================================== */
    /* 种子常量                                                                  */
    /* ======================================================================== */

    private static final String[] PLATFORMS = {"天猫", "京东", "拼多多", "抖音商城", "美团优选"};

    private static final String[][] CARRIERS = {
            {"SF", "顺丰速运", "陈师傅", "13800000001", "18:00"},
            {"JD", "京东物流", "林师傅", "13800000002", "17:30"},
            {"ZTO", "中通快递", "黄师傅", "13800000003", "19:00"},
            {"YTO", "圆通速递", "王师傅", "13800000004", "18:30"},
            {"STO", "申通快递", "李师傅", "13800000005", "19:30"},
    };

    /** 收货地：省份 / 城市 / 详细地址前缀。 */
    private static final String[][] AREAS = {
            {"广东省", "深圳市", "南山区科技园路"},
            {"广东省", "广州市", "天河区体育西路"},
            {"浙江省", "杭州市", "西湖区文三路"},
            {"浙江省", "宁波市", "鄞州区中山东路"},
            {"江苏省", "南京市", "鼓楼区中山北路"},
            {"江苏省", "苏州市", "工业园区星湖街"},
            {"上海市", "上海市", "浦东新区张江路"},
            {"北京市", "北京市", "朝阳区建国路"},
            {"四川省", "成都市", "武侯区人民南路"},
            {"湖北省", "武汉市", "洪山区珞喻路"},
    };

    /** 商品主数据：名称 / 规格 / 单位 / 品类。 */
    private static final String[][] SKUS = {
            {"红富士苹果", "5kg/箱", "箱", "仁果类"},
            {"新疆库尔勒香梨", "4kg/箱", "箱", "仁果类"},
            {"海南贵妃芒", "3kg/箱", "箱", "热带水果"},
            {"智利车厘子", "2kg/盒", "盒", "浆果类"},
            {"泰国金枕榴莲", "1个", "个", "热带水果"},
            {"赣南脐橙", "5kg/箱", "箱", "柑橘类"},
            {"越南红心火龙果", "2kg/箱", "箱", "热带水果"},
            {"山东沾化冬枣", "2kg/箱", "箱", "核果类"},
            {"陕西猕猴桃", "3kg/箱", "箱", "浆果类"},
            {"广西百香果", "1kg/袋", "袋", "浆果类"},
            {"云南蓝莓", "125g/盒", "盒", "浆果类"},
            {"智利牛油果", "6个/盒", "盒", "热带水果"},
            {"菲律宾香蕉", "5kg/箱", "箱", "热带水果"},
            {"福建蜜柚", "2个/袋", "袋", "柑橘类"},
            {"四川丑橘", "5kg/箱", "箱", "柑橘类"},
            {"台湾凤梨", "2个/箱", "箱", "热带水果"},
            {"甘肃花牛苹果", "4kg/箱", "箱", "仁果类"},
            {"山东烟台大樱桃", "1kg/盒", "盒", "核果类"},
            {"新疆哈密瓜", "1个", "个", "瓜类"},
            {"海南小台农芒果", "5kg/箱", "箱", "热带水果"},
            {"广西沃柑", "5kg/箱", "箱", "柑橘类"},
            {"云南红提", "2kg/箱", "箱", "浆果类"},
            {"陕西冬枣", "2kg/箱", "箱", "核果类"},
            {"海南香水菠萝", "3个/箱", "箱", "热带水果"},
            {"福建琯溪蜜柚", "4个/箱", "箱", "柑橘类"},
            {"江西南丰蜜桔", "5kg/箱", "箱", "柑橘类"},
            {"湖北秭归脐橙", "5kg/箱", "箱", "柑橘类"},
            {"湖南冰糖橙", "5kg/箱", "箱", "柑橘类"},
            {"浙江象山红美人", "2kg/箱", "箱", "柑橘类"},
            {"广东增城挂绿荔枝", "1kg/盒", "盒", "核果类"},
            {"海南椰青", "9个/箱", "箱", "热带水果"},
            {"云南阳光玫瑰葡萄", "2kg/箱", "箱", "浆果类"},
            {"山东莱阳梨", "5kg/箱", "箱", "仁果类"},
            {"河北黄冠梨", "5kg/箱", "箱", "仁果类"},
            {"山西玉露香梨", "4kg/箱", "箱", "仁果类"},
            {"辽宁丹东草莓", "1kg/盒", "盒", "浆果类"},
            {"江苏阳山水蜜桃", "8个/箱", "箱", "核果类"},
            {"安徽砀山酥梨", "5kg/箱", "箱", "仁果类"},
            {"河南灵宝苹果", "5kg/箱", "箱", "仁果类"},
            {"宁夏硒砂瓜", "1个", "个", "瓜类"},
    };

    /** 员工：工号 / 姓名 / 角色 / 班组。 */
    private static final Object[][] EMPLOYEES = {
            {"W001", "王敏", EmployeeRole.WAVE_OPERATOR, "波次调度组"},
            {"W002", "李强", EmployeeRole.WAVE_OPERATOR, "波次调度组"},
            {"P001", "张伟", EmployeeRole.PICKER, "拣货一组"},
            {"P002", "刘洋", EmployeeRole.PICKER, "拣货一组"},
            {"P003", "陈静", EmployeeRole.PICKER, "拣货二组"},
            {"P004", "赵磊", EmployeeRole.PICKER, "拣货二组"},
            {"P005", "孙悦", EmployeeRole.PICKER, "拣货三组"},
            {"P006", "周鹏", EmployeeRole.PICKER, "拣货三组"},
            {"R001", "吴倩", EmployeeRole.REVIEWER, "复核组"},
            {"R002", "郑浩", EmployeeRole.REVIEWER, "复核组"},
            {"R003", "冯雪", EmployeeRole.REVIEWER, "复核组"},
            {"A001", "何军", EmployeeRole.ADMIN, "仓库管理"},
    };

    /** 库区：编码 / 名称 / 温层。 */
    private static final String[][] ZONES = {
            {"A", "冷藏区", "0-4℃"},
            {"B", "常温区", "15-25℃"},
            {"C", "干货区", "常温干燥"},
            {"D", "周转区", "临时周转"},
    };

    /* ======================================================================== */
    /* 入口                                                                     */
    /* ======================================================================== */

    @Override
    public void run(ApplicationArguments args) {
        long t0 = System.currentTimeMillis();
        log.info("开始初始化内存种子数据 ...");

        seedCarriers();
        seedEmployees();
        seedLocations();
        List<Sku> skus = seedSkus();
        List<OrderInfo> allOrders = seedOrders(skus);
        seedHistoricalWaves(allOrders);

        log.info("种子数据初始化完成，耗时 {} ms —— 承运商 {} / 员工 {} / 库位 {} / SKU {} / 订单 {} / 波次 {}",
                System.currentTimeMillis() - t0,
                carrierRepo.count(), employeeRepo.count(), locationRepo.count(),
                skuRepo.count(), orderRepo.count(), waveRepo.count());
        log.info("其中待作业订单：待汇聚 {} 笔，已入池 {} 笔",
                orderRepo.count(o -> o.getStatus() == OrderStatus.CREATED),
                orderRepo.count(o -> o.getStatus() == OrderStatus.POOLED));
    }

    /* ======================================================================== */
    /* 1. 承运商                                                                 */
    /* ======================================================================== */

    private void seedCarriers() {
        for (String[] c : CARRIERS) {
            carrierRepo.save(new Carrier()
                    .setCarrierCode(c[0])
                    .setCarrierName(c[1])
                    .setContactName(c[2])
                    .setContactPhone(c[3])
                    .setDailyPickupTime(c[4])
                    .setStatus(1));
        }
    }

    /* ======================================================================== */
    /* 2. 员工                                                                   */
    /* ======================================================================== */

    private void seedEmployees() {
        int i = 0;
        for (Object[] e : EMPLOYEES) {
            employeeRepo.save(new Employee()
                    .setEmployeeNo((String) e[0])
                    .setEmployeeName((String) e[1])
                    .setRole((EmployeeRole) e[2])
                    .setTeam((String) e[3])
                    .setPhone("139" + String.format("%08d", 10000000 + i++))
                    .setStatus(1));
        }
    }

    /* ======================================================================== */
    /* 3. 库位                                                                   */
    /* ======================================================================== */

    private void seedLocations() {
        for (int zi = 0; zi < ZONES.length; zi++) {
            String zone = ZONES[zi][0];
            for (int aisle = 1; aisle <= 3; aisle++) {
                for (int rack = 1; rack <= 2; rack++) {
                    for (int level = 1; level <= 2; level++) {
                        locationRepo.save(new WarehouseLocation()
                                .setLocationCode(String.format("%s-%02d-%02d-%02d", zone, aisle, rack, level))
                                .setZone(zone)
                                .setAisle(String.format("%02d", aisle))
                                .setRack(String.format("%02d", rack))
                                .setLevel(String.format("%02d", level))
                                .setLocationType(zi == 3 ? "BULK" : "PICK")
                                .setStatus(LocationStatus.ENABLED)
                                .setPreferredCarrierCode(zi < CARRIERS.length ? CARRIERS[zi][0] : null)
                                .setPathSeq(zi * 10000 + aisle * 100 + rack * 10 + level));
                    }
                }
            }
        }
    }

    /* ======================================================================== */
    /* 4. 商品                                                                   */
    /* ======================================================================== */

    private List<Sku> seedSkus() {
        List<String> locationCodes = locationRepo.findAll().stream()
                .map(WarehouseLocation::getLocationCode)
                .sorted()
                .collect(Collectors.toList());

        List<Sku> list = new ArrayList<>();
        for (int i = 0; i < SKUS.length; i++) {
            String[] s = SKUS[i];
            Sku sku = new Sku()
                    .setSkuCode(String.format("SKU%05d", 10001 + i))
                    .setSkuName(s[0])
                    .setSpec(s[1])
                    .setUnit(s[2])
                    .setCategory(s[3])
                    .setBarcode("69" + String.format("%011d", 10000000000L + i))
                    .setDefaultLocationCode(locationCodes.get(i % locationCodes.size()))
                    .setWeight(0.5 + random.nextInt(80) / 10.0)
                    .setSafetyStock(20 + random.nextInt(60))
                    // 前 3 个 SKU 库存留低，方便演示缺货场景
                    .setStockQty(i < 3 ? 3 + random.nextInt(5) : 120 + random.nextInt(500))
                    .setStatus(1);
            list.add(skuRepo.save(sku));
        }
        return list;
    }

    /* ======================================================================== */
    /* 5. 订单                                                                   */
    /* ======================================================================== */

    private List<OrderInfo> seedOrders(List<Sku> skus) {
        List<OrderInfo> orders = new ArrayList<>();
        // 60 笔订单：后 28 笔留给历史波次，前 32 笔为待作业
        for (int i = 0; i < 60; i++) {
            String[] area = AREAS[random.nextInt(AREAS.length)];
            String[] carrier = CARRIERS[random.nextInt(CARRIERS.length)];
            boolean urgent = random.nextInt(10) < 2;
            LocalDateTime payTime = LocalDateTime.now()
                    .minusHours(2 + random.nextInt(46))
                    .minusMinutes(random.nextInt(60));

            OrderInfo order = new OrderInfo()
                    .setOrderNo(nextNo("SO"))
                    .setPlatform(PLATFORMS[random.nextInt(PLATFORMS.length)])
                    .setCarrierCode(carrier[0])
                    .setCarrierName(carrier[1])
                    .setReceiverName(randomReceiver())
                    .setReceiverPhone("13" + String.format("%09d", random.nextInt(1000000000)))
                    .setReceiverAddress(area[2] + (10 + random.nextInt(200)) + "号")
                    .setProvince(area[0])
                    .setCity(area[1])
                    .setPriority(urgent ? "URGENT" : "NORMAL")
                    .setPayTime(payTime)
                    .setExpectedDeliveryTime(payTime.plusDays(urgent ? 1 : 3))
                    .setStatus(OrderStatus.CREATED)
                    .setBuyerRemark(random.nextInt(5) == 0 ? "请尽快发货，谢谢" : null);
            order.setCreateTime(payTime);
            order = orderRepo.save(order);

            // 每单 1~4 种商品
            int lineCount = 1 + random.nextInt(4);
            Set<Integer> used = new HashSet<>();
            double amount = 0.0;
            int totalQty = 0;
            for (int line = 1; line <= lineCount; line++) {
                int idx;
                do {
                    idx = random.nextInt(skus.size());
                } while (!used.add(idx));
                Sku sku = skus.get(idx);
                int qty = 1 + random.nextInt(3);
                double price = Math.round((15 + random.nextDouble() * 180) * 100) / 100.0;
                double lineAmount = Math.round(price * qty * 100) / 100.0;
                amount += lineAmount;
                totalQty += qty;

                orderItemRepo.save(new OrderItem()
                        .setOrderNo(order.getOrderNo())
                        .setLineNo(line)
                        .setSkuCode(sku.getSkuCode())
                        .setSkuName(sku.getSkuName())
                        .setSpec(sku.getSpec())
                        .setUnit(sku.getUnit())
                        .setQty(qty)
                        .setPickedQty(0)
                        .setLocationCode(sku.getDefaultLocationCode())
                        .setPrice(price)
                        .setAmount(lineAmount));
            }
            order.setSkuCount(lineCount).setTotalQty(totalQty)
                    .setAmount(Math.round(amount * 100) / 100.0);
            orderRepo.save(order);
            orders.add(order);
        }
        return orders;
    }

    private String randomReceiver() {
        String[] surnames = {"张", "李", "王", "刘", "陈", "杨", "黄", "赵", "吴", "周", "徐", "孙", "马", "朱", "胡"};
        String[] given = {"伟", "芳", "娜", "敏", "静", "磊", "洋", "强", "军", "杰", "涛", "明", "超", "霞", "丽"};
        return surnames[random.nextInt(surnames.length)] + given[random.nextInt(given.length)];
    }

    /* ======================================================================== */
    /* 6. 历史波次（供效率复盘）                                                  */
    /* ======================================================================== */

    /**
     * 构造 7 个波次，覆盖完整状态谱。
     *
     * <p>后 28 笔订单被消耗进这些波次，前 32 笔保持「待作业」状态。
     */
    private void seedHistoricalWaves(List<OrderInfo> allOrders) {
        // 取订单列表的后 28 笔作为历史波次的素材
        List<OrderInfo> pool = new ArrayList<>(allOrders.subList(32, 60));
        Collections.shuffle(pool, random);

        int cursor = 0;
        // {策略, 状态, 订单数, 拣货员, 复核员, 创建人, 是否含缺货, 距今天数}
        Object[][] plan = {
                {WaveStrategy.BY_AREA, WaveStatus.CLOSED, 5, "P001", "R001", "W001", false, 6},
                {WaveStrategy.BY_CARRIER, WaveStatus.CLOSED, 6, "P002", "R002", "W001", true, 5},
                {WaveStrategy.BY_AREA, WaveStatus.HANDOVERED, 4, "P003", "R003", "W002", false, 4},
                {WaveStrategy.BY_TIMELINESS, WaveStatus.REVIEWED, 3, "P004", "R001", "W002", false, 3},
                {WaveStrategy.BY_AREA, WaveStatus.PICKED, 4, "P005", "R002", "W001", false, 2},
                {WaveStrategy.BY_CARRIER, WaveStatus.PICKING, 4, "P006", "R003", "W001", false, 1},
                {WaveStrategy.BY_AREA, WaveStatus.ASSIGNED, 2, "P001", "R001", "W002", false, 0},
        };

        for (int wi = 0; wi < plan.length; wi++) {
            Object[] p = plan[wi];
            WaveStrategy strategy = (WaveStrategy) p[0];
            WaveStatus status = (WaveStatus) p[1];
            int orderCount = (Integer) p[2];
            String pickerNo = (String) p[3];
            String reviewerNo = (String) p[4];
            String creatorNo = (String) p[5];
            boolean withShortage = (Boolean) p[6];
            int daysAgo = (Integer) p[7];

            List<OrderInfo> waveOrders = new ArrayList<>();
            for (int i = 0; i < orderCount && cursor < pool.size(); i++) {
                waveOrders.add(pool.get(cursor++));
            }
            if (waveOrders.isEmpty()) {
                continue;
            }
            buildWave(wi + 1, strategy, status, waveOrders, pickerNo, reviewerNo, creatorNo, withShortage, daysAgo);
        }
    }

    /**
     * 按给定状态，把一个波次及其下游单据一次性造全。
     */
    private void buildWave(int idx, WaveStrategy strategy, WaveStatus status, List<OrderInfo> orders,
                           String pickerNo, String reviewerNo, String creatorNo,
                           boolean withShortage, int daysAgo) {

        Employee picker = findByNo(pickerNo);
        Employee reviewer = findByNo(reviewerNo);
        Employee creator = findByNo(creatorNo);

        LocalDateTime base = LocalDateTime.now().minusDays(daysAgo).withHour(8).withMinute(30).withSecond(0).withNano(0);
        LocalDateTime assignTime = base.plusMinutes(20);
        LocalDateTime pickStart = base.plusMinutes(35);
        // 每单约 4 分钟拣货耗时，制造出合理的效率数据
        long pickSeconds = 180L + orders.size() * 150L + random.nextInt(240);
        LocalDateTime pickFinish = pickStart.plusSeconds(pickSeconds);
        long reviewSeconds = 90L + orders.size() * 55L + random.nextInt(120);
        LocalDateTime reviewFinish = pickFinish.plusSeconds(reviewSeconds);
        LocalDateTime handoverTime = reviewFinish.plusMinutes(12 + random.nextInt(20));

        String waveNo = "WV" + base.toLocalDate().toString().replace("-", "") + String.format("%03d", idx);
        String provinceScope = orders.stream().map(OrderInfo::getProvince).distinct().limit(3)
                .collect(Collectors.joining("/"));

        Wave wave = new Wave()
                .setWaveNo(waveNo)
                .setWaveName(base.toLocalDate() + " " + provinceScope + "波次 " + String.format("%02d", idx))
                .setStrategy(strategy)
                .setStatus(status)
                .setOrderCount(orders.size())
                .setProvinceScope(provinceScope)
                .setCreatorNo(creator.getEmployeeNo())
                .setCreatorName(creator.getEmployeeName())
                .setPickerNo(picker.getEmployeeNo())
                .setPickerName(picker.getEmployeeName())
                .setReviewerNo(reviewer.getEmployeeNo())
                .setReviewerName(reviewer.getEmployeeName())
                .setHasShortage(withShortage);
        wave.setCreateTime(base);
        wave.setAssignTime(status == WaveStatus.CREATED ? null : assignTime);
        wave.setPickStartTime(rank(status) >= rank(WaveStatus.PICKING) ? pickStart : null);
        wave.setPickFinishTime(rank(status) >= rank(WaveStatus.PICKED) ? pickFinish : null);
        wave.setPickDurationSec(rank(status) >= rank(WaveStatus.PICKED) ? pickSeconds : null);
        wave.setReviewFinishTime(rank(status) >= rank(WaveStatus.REVIEWED) ? reviewFinish : null);
        wave.setHandoverTime(rank(status) >= rank(WaveStatus.HANDOVERED) ? handoverTime : null);
        if (rank(status) >= rank(WaveStatus.HANDOVERED)) {
            wave.setTotalDurationSec(java.time.Duration.between(base, handoverTime).getSeconds());
        }

        /* ---------------- 订单归入波次 ---------------- */

        int seq = 1;
        int totalQty = 0;
        Set<String> skuSet = new LinkedHashSet<>();
        Set<String> locSet = new LinkedHashSet<>();
        for (OrderInfo o : orders) {
            List<OrderItem> items = orderItemRepo.find(it -> it.getOrderNo().equals(o.getOrderNo()));
            for (OrderItem it : items) {
                skuSet.add(it.getSkuCode());
                locSet.add(it.getLocationCode());
                totalQty += it.getQty();
            }
            // 已完成拣货的波次，回写订单明细的实拣数量
            if (rank(status) >= rank(WaveStatus.PICKED)) {
                for (OrderItem it : items) {
                    it.setPickedQty(it.getQty());
                    orderItemRepo.save(it);
                }
            }
            o.setWaveNo(waveNo);
            o.setStatus(mapOrderStatus(status));
            orderRepo.save(o);

            waveOrderRepo.save(new WaveOrder()
                    .setWaveNo(waveNo).setOrderNo(o.getOrderNo()).setSeqNo(seq++).setHeld(false));
        }
        wave.setSkuCount(skuSet.size()).setTotalQty(totalQty).setLocationCount(locSet.size());
        wave = waveRepo.save(wave);

        logRepo.save(saveLog("WAVE", waveNo, "WAVE_CREATE", "生成波次",
                creator.getEmployeeNo(), creator.getEmployeeName(), creator.getRole().getDesc(),
                "策略=" + strategy.getDesc() + "，订单数=" + orders.size()));

        /* ---------------- 拣货任务与明细 ---------------- */

        PickTask task = buildPickTask(wave, orders, picker, status, withShortage);
        if (withShortage) {
            wave.setHasShortage(true);
            waveRepo.save(wave);
        }

        /* ---------------- 复核记录 ---------------- */

        if (rank(status) >= rank(WaveStatus.REVIEWED)) {
            buildReviewRecords(wave, orders, reviewer, status, task, withShortage);
        }

        /* ---------------- 出库交接 ---------------- */

        if (rank(status) >= rank(WaveStatus.HANDOVERED)) {
            buildHandover(wave, orders, reviewer, handoverTime);
        }
    }

    /** 构造拣货任务与明细行（按库位路径排序，同一 库位+SKU 合并）。 */
    private PickTask buildPickTask(Wave wave, List<OrderInfo> orders, Employee picker,
                                   WaveStatus waveStatus, boolean withShortage) {

        LocalDateTime start = wave.getPickStartTime();
        LocalDateTime finish = wave.getPickFinishTime();
        boolean started = rank(waveStatus) >= rank(WaveStatus.PICKING);
        boolean done = rank(waveStatus) >= rank(WaveStatus.PICKED);

        // 汇总：key = 库位 + SKU
        Map<String, PickItem> merged = new LinkedHashMap<>();
        Map<String, Set<String>> relatedOrders = new HashMap<>();
        Map<String, Sku> skuMap = skuRepo.findAll().stream()
                .collect(Collectors.toMap(Sku::getSkuCode, s -> s));

        for (OrderInfo o : orders) {
            for (OrderItem it : orderItemRepo.find(x -> x.getOrderNo().equals(o.getOrderNo()))) {
                String key = it.getLocationCode() + "|" + it.getSkuCode();
                PickItem pi = merged.get(key);
                if (pi == null) {
                    Sku sku = skuMap.get(it.getSkuCode());
                    pi = new PickItem()
                            .setWaveNo(wave.getWaveNo())
                            .setLocationCode(it.getLocationCode())
                            .setSkuCode(it.getSkuCode())
                            .setSkuName(it.getSkuName())
                            .setSpec(it.getSpec())
                            .setUnit(it.getUnit())
                            .setShouldQty(0)
                            .setActualQty(0)
                            .setShortageQty(0)
                            .setStatus(PickItemStatus.PENDING);
                    merged.put(key, pi);
                    relatedOrders.put(key, new LinkedHashSet<>());
                }
                pi.setShouldQty(pi.getShouldQty() + it.getQty());
                relatedOrders.get(key).add(o.getOrderNo());
            }
        }

        // 按库位路径排序
        List<String> pathOrder = locationRepo.findAll().stream()
                .sorted(Comparator.comparing(WarehouseLocation::getPathSeq))
                .map(WarehouseLocation::getLocationCode)
                .collect(Collectors.toList());
        List<PickItem> items = new ArrayList<>(merged.values());
        items.sort(Comparator.comparingInt(pi -> pathOrder.indexOf(pi.getLocationCode())));

        String taskNo = "PK" + wave.getWaveNo().substring(2);
        int seq = 1;
        int shouldTotal = 0;
        int actualTotal = 0;
        int shortageTotal = 0;
        int finished = 0;
        // 指定一条明细作为「缺货演示行」；若波次无明细则跳过缺货构造
        String shortageKey = null;
        if (withShortage && !items.isEmpty()) {
            PickItem probe = items.get(Math.min(1, items.size() - 1));
            shortageKey = probe.getLocationCode() + "|" + probe.getSkuCode();
        }

        for (PickItem pi : items) {
            pi.setTaskNo(taskNo).setSeqNo(seq++);
            pi.setRelatedOrderNos(String.join(",", relatedOrders.get(pi.getLocationCode() + "|" + pi.getSkuCode())));
            shouldTotal += pi.getShouldQty();

            String key = pi.getLocationCode() + "|" + pi.getSkuCode();
            if (done) {
                if (key.equals(shortageKey)) {
                    // 制造一条缺货：应拣 N，实拣 N-2
                    int shortQty = Math.min(2, Math.max(1, pi.getShouldQty() - 1));
                    pi.setShortageQty(shortQty);
                    pi.setActualQty(pi.getShouldQty() - shortQty);
                    pi.setStatus(shortQty >= pi.getShouldQty() ? PickItemStatus.SHORTAGE : PickItemStatus.PARTIAL);
                    shortageTotal += shortQty;
                } else {
                    pi.setActualQty(pi.getShouldQty());
                    pi.setStatus(PickItemStatus.PICKED);
                }
                pi.setScanTime(finish.minusSeconds(60L * (items.size() - seq + 1)));
                actualTotal += pi.getActualQty();
                finished++;
            } else if (started) {
                // 拣货中：前一半行已完成
                if (seq <= items.size() / 2 + 1) {
                    pi.setActualQty(pi.getShouldQty());
                    pi.setStatus(PickItemStatus.PICKED);
                    pi.setScanTime(start.plusSeconds(45L * seq));
                    actualTotal += pi.getActualQty();
                    finished++;
                }
            }
            pickItemRepo.save(pi);
        }

        PickTaskStatus taskStatus = done
                ? (shortageTotal > 0 ? PickTaskStatus.EXCEPTION : PickTaskStatus.FINISHED)
                : (started ? PickTaskStatus.PICKING : PickTaskStatus.WAITING);

        PickTask task = new PickTask()
                .setTaskNo(taskNo)
                .setWaveNo(wave.getWaveNo())
                .setPickerNo(picker.getEmployeeNo())
                .setPickerName(picker.getEmployeeName())
                .setStatus(taskStatus)
                .setItemCount(items.size())
                .setFinishedCount(finished)
                .setLocationCount(wave.getLocationCount())
                .setShouldQty(shouldTotal)
                .setActualQty(actualTotal)
                .setShortageQty(shortageTotal)
                .setOrderCount(orders.size())
                .setStartTime(started ? start : null)
                .setFinishTime(done ? finish : null)
                .setDurationSec(done ? wave.getPickDurationSec() : null)
                .setPathText(items.stream().map(PickItem::getLocationCode).distinct().collect(Collectors.joining(" → ")));
        task.setCreateTime(wave.getAssignTime() != null ? wave.getAssignTime() : wave.getCreateTime());
        task = pickTaskRepo.save(task);

        if (done) {
            logRepo.save(saveLog("PICK", taskNo, "PICK_FINISH", "拣货完成",
                    picker.getEmployeeNo(), picker.getEmployeeName(), picker.getRole().getDesc(),
                    "应拣 " + shouldTotal + " 件，实拣 " + actualTotal + " 件，缺货 " + shortageTotal + " 件"));
        }

        /* ---------------- 缺货记录 ---------------- */

        if (withShortage && done) {
            PickItem shortItem = items.stream()
                    .filter(i -> i.getShortageQty() != null && i.getShortageQty() > 0)
                    .findFirst().orElse(null);
            if (shortItem != null) {
                ShortageRecord sr = new ShortageRecord()
                        .setShortageNo(nextNo("SH"))
                        .setWaveNo(wave.getWaveNo())
                        .setTaskNo(taskNo)
                        .setOrderNo(shortItem.getRelatedOrderNos().split(",")[0])
                        .setLocationCode(shortItem.getLocationCode())
                        .setSkuCode(shortItem.getSkuCode())
                        .setSkuName(shortItem.getSkuName())
                        .setShouldQty(shortItem.getShouldQty())
                        .setShortageQty(shortItem.getShortageQty())
                        .setReason(ShortageReason.STOCK_ZERO)
                        .setReporterNo(picker.getEmployeeNo())
                        .setReporterName(picker.getEmployeeName())
                        .setReportTime(finish)
                        .setStatus(ShortageStatus.PENDING);
                sr.setCreateTime(finish);
                shortageRepo.save(sr);
            }
        }
        return task;
    }

    /** 构造复核记录。 */
    private void buildReviewRecords(Wave wave, List<OrderInfo> orders, Employee reviewer,
                                    WaveStatus waveStatus, PickTask task, boolean withShortage) {
        boolean done = rank(waveStatus) >= rank(WaveStatus.REVIEWED);
        LocalDateTime finish = wave.getReviewFinishTime();

        int orderIdx = 0;
        for (OrderInfo o : orders) {
            List<OrderItem> items = orderItemRepo.find(it -> it.getOrderNo().equals(o.getOrderNo()));
            int shouldQty = items.stream().mapToInt(OrderItem::getQty).sum();
            int actualQty = items.stream().mapToInt(it -> it.getPickedQty() == null ? 0 : it.getPickedQty()).sum();

            // 让含缺货波次的第 2 单产生差异
            boolean thisHasDiff = withShortage && orderIdx == 1;

            ReviewRecord rr = new ReviewRecord()
                    .setReviewNo(nextNo("RV"))
                    .setWaveNo(wave.getWaveNo())
                    .setOrderNo(o.getOrderNo())
                    .setReviewerNo(reviewer.getEmployeeNo())
                    .setReviewerName(reviewer.getEmployeeName())
                    .setShouldSkuCount(items.size())
                    .setShouldQty(shouldQty)
                    .setActualQty(thisHasDiff ? shouldQty - 2 : actualQty)
                    .setStartTime(finish.minusSeconds(40L * (orders.size() - orderIdx)))
                    .setFinishTime(finish.minusSeconds(30L * (orders.size() - orderIdx - 1)))
                    .setPackMaterial(randomPackMaterial())
                    .setPackageCount(1 + random.nextInt(2))
                    .setWeight(Math.round((1.5 + random.nextDouble() * 6) * 100) / 100.0);

            if (thisHasDiff) {
                rr.setStatus(ReviewStatus.DIFF).setDiffType(DiffType.SHORT).setDiffQty(2)
                        .setDiffDetail("缺货 2 件：库位实物不足");
            } else {
                rr.setStatus(ReviewStatus.PASS).setDiffType(DiffType.NONE).setDiffQty(0);
            }
            rr.setDurationSec(java.time.Duration.between(rr.getStartTime(), rr.getFinishTime()).getSeconds());
            rr.setCreateTime(rr.getStartTime());
            rr = reviewRepo.save(rr);

            if (thisHasDiff) {
                OrderItem first = items.get(0);
                reviewDiffRepo.save(new ReviewDiffItem()
                        .setReviewNo(rr.getReviewNo())
                        .setWaveNo(wave.getWaveNo())
                        .setOrderNo(o.getOrderNo())
                        .setSkuCode(first.getSkuCode())
                        .setSkuName(first.getSkuName())
                        .setDiffType(DiffType.SHORT)
                        .setShouldQty(first.getQty())
                        .setActualQty(first.getQty() - 2)
                        .setDiffQty(-2)
                        .setResponsibleStage("PICK")
                        .setResponsibleNo(task.getPickerNo())
                        .setRemark("拣货员反馈库位实物不足"));
            }
            orderIdx++;
        }

        if (done) {
            logRepo.save(saveLog("REVIEW", wave.getWaveNo(), "REVIEW_FINISH", "复核完成",
                    reviewer.getEmployeeNo(), reviewer.getEmployeeName(), reviewer.getRole().getDesc(),
                    "复核 " + orders.size() + " 单"));
        }
    }

    /** 构造出库交接单。 */
    private void buildHandover(Wave wave, List<OrderInfo> orders, Employee operator, LocalDateTime handoverTime) {
        List<ReviewRecord> reviews = reviewRepo.find(r -> wave.getWaveNo().equals(r.getWaveNo()));
        int packageCount = reviews.stream().mapToInt(r -> r.getPackageCount() == null ? 1 : r.getPackageCount()).sum();
        double weight = reviews.stream().mapToDouble(r -> r.getWeight() == null ? 0 : r.getWeight()).sum();

        String handoverNo = "HO" + wave.getWaveNo().substring(2);
        HandoverRecord ho = new HandoverRecord()
                .setHandoverNo(handoverNo)
                .setWaveNos(wave.getWaveNo())
                .setCarrierCode(orders.get(0).getCarrierCode())
                .setCarrierName(orders.get(0).getCarrierName())
                .setOrderCount(orders.size())
                .setPackageCount(packageCount)
                .setTotalWeight(Math.round(weight * 100) / 100.0)
                .setStatus(HandoverStatus.CONFIRMED)
                .setHandoverUserNo(operator.getEmployeeNo())
                .setHandoverUserName(operator.getEmployeeName())
                .setReceiveName(findCarrierContact(orders.get(0).getCarrierCode()))
                .setVehicleNo("粤B" + String.format("%05d", random.nextInt(100000)))
                .setHandoverTime(handoverTime);
        ho.setCreateTime(handoverTime.minusMinutes(15));
        handoverRepo.save(ho);

        logRepo.save(saveLog("HANDOVER", handoverNo, "HANDOVER_CONFIRM", "交接完成",
                operator.getEmployeeNo(), operator.getEmployeeName(), operator.getRole().getDesc(),
                "交接 " + orders.size() + " 单 / " + packageCount + " 件"));
    }

    /* ======================================================================== */
    /* 工具方法                                                                  */
    /* ======================================================================== */

    /** 波次状态在流程中的推进序号，用于判断「是否已到达某环节」。 */
    private static int rank(WaveStatus s) {
        return switch (s) {
            case CREATED -> 0;
            case ASSIGNED -> 1;
            case PICKING -> 2;
            case PICKED -> 3;
            case REVIEWING -> 4;
            case REVIEWED -> 5;
            case HANDOVERED -> 6;
            case CLOSED -> 7;
            case CANCELLED -> -1;
        };
    }

    /** 波次状态映射到订单状态。 */
    private static OrderStatus mapOrderStatus(WaveStatus s) {
        return switch (s) {
            case CREATED, ASSIGNED, PICKING -> OrderStatus.WAVED;
            case PICKED, REVIEWING -> OrderStatus.PICKED;
            case REVIEWED -> OrderStatus.REVIEWED;
            case HANDOVERED, CLOSED -> OrderStatus.OUTBOUND;
            case CANCELLED -> OrderStatus.CANCELLED;
        };
    }

    private Employee findByNo(String employeeNo) {
        return employeeRepo.findOne(e -> e.getEmployeeNo().equals(employeeNo))
                .orElseThrow(() -> new IllegalStateException("种子员工不存在: " + employeeNo));
    }

    private String findCarrierContact(String carrierCode) {
        return carrierRepo.findOne(c -> c.getCarrierCode().equals(carrierCode))
                .map(Carrier::getContactName).orElse("承运商司机");
    }

    private String randomPackMaterial() {
        String[] materials = {"纸箱", "气泡袋", "保温箱"};
        return materials[random.nextInt(materials.length)];
    }

    /** 业务单号发号：前缀 + 日期 + 4 位流水。 */
    private String nextNo(String prefix) {
        String day = LocalDateTime.now().toLocalDate().toString().replace("-", "");
        String key = prefix + day;
        int seq = seqMap.merge(key, 1, Integer::sum);
        return key + String.format("%04d", seq);
    }

    private OperationLog saveLog(String bizType, String bizNo, String action, String actionDesc,
                             String operatorNo, String operatorName, String operatorRole, String detail) {
        OperationLog ol = new OperationLog()
                .setBizType(bizType).setBizNo(bizNo).setAction(action).setActionDesc(actionDesc)
                .setOperatorNo(operatorNo).setOperatorName(operatorName).setOperatorRole(operatorRole)
                .setDetail(detail).setOperateTime(LocalDateTime.now());
        ol.setCreateTime(LocalDateTime.now());
        return logRepo.save(ol);
    }
}
