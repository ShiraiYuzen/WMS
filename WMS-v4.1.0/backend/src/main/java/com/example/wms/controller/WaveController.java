package com.example.wms.controller;

import com.example.wms.common.AuthContext;
import com.example.wms.common.Dict;
import com.example.wms.common.JdbcMaps;
import com.example.wms.common.LoginUser;
import com.example.wms.common.RequireRole;
import com.example.wms.dto.ApiResult;
import com.example.wms.dto.PageResult;
import com.example.wms.service.Repo;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/wave")
public class WaveController {

    private final Repo repo;

    public WaveController(Repo repo) {
        this.repo = repo;
    }

    // ---------------- 拣货员负载 ----------------

    @GetMapping("/picker-workload")
    public ApiResult<List<Map<String, Object>>> pickerWorkload() {
        List<Map<String, Object>> pickers = JdbcMaps.list(repo.jdbc(),
                "SELECT * FROM employee WHERE role='PICKER' AND status=1 ORDER BY employee_no");
        List<Map<String, Object>> r = new ArrayList<>();
        for (Map<String, Object> p : pickers) {
            String no = Repo.str(p.get("employeeNo"));
            long waiting = JdbcMaps.count(repo.jdbc(),
                    "SELECT COUNT(*) FROM wave WHERE picker_no=? AND status='ASSIGNED'", no);
            long working = JdbcMaps.count(repo.jdbc(),
                    "SELECT COUNT(*) FROM wave WHERE picker_no=? AND status IN ('ASSIGNED','PICKING','REVIEWING')", no);
            long finished = JdbcMaps.count(repo.jdbc(),
                    "SELECT COUNT(*) FROM wave WHERE picker_no=? AND status IN ('HANDOVERED','CLOSED')", no);
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("pickerNo", no);
            o.put("pickerName", p.get("employeeName"));
            o.put("team", p.get("team"));
            o.put("waitingWaves", waiting);
            o.put("workingWaves", working);
            o.put("finishedWaves", finished);
            o.put("load", waiting + working);
            r.add(o);
        }
        r.sort(Comparator.comparingLong(m -> ((Number) m.get("load")).longValue()));
        return ApiResult.ok(r);
    }

    // ---------------- 预览 ----------------

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/preview")
    public ApiResult<Map<String, Object>> preview(@RequestBody(required = false) Map<String, Object> body) {
        String strategy = body == null ? null : Repo.str(body.get("strategy"));
        if (strategy == null || strategy.isBlank()) strategy = "BY_AREA";
        List<String> orderNos = strList(body == null ? null : body.get("orderNos"));

        List<Map<String, Object>> orders = candidates(orderNos);
        if (orders.isEmpty()) throw new IllegalStateException("没有符合条件的订单，无法生成波次");
        Map<String, List<Map<String, Object>>> itemsByOrder = itemsOf(orders);
        List<Group> groups = buildGroups(orders, strategy, body, itemsByOrder);

        List<Map<String, Object>> gs = new ArrayList<>();
        int totalQty = 0;
        for (Group g : groups) {
            totalQty += g.totalQty;
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("groupKey", g.key);
            gm.put("suggestWaveName", Repo.today() + " " + g.key);
            gm.put("orderCount", g.orders.size());
            gm.put("skuCount", g.skuCount);
            gm.put("totalQty", g.totalQty);
            gm.put("locationCount", g.locationCount);
            gm.put("overCapacity", false);
            List<Map<String, Object>> os = new ArrayList<>();
            for (Map<String, Object> o : g.orders) {
                repo.enrichOrder(o);
                os.add(o);
            }
            gm.put("orders", os);
            gs.add(gm);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("strategy", strategy);
        r.put("strategyDesc", Dict.desc("waveStrategy", strategy));
        r.put("waveCount", groups.size());
        r.put("totalOrders", orders.size());
        r.put("totalQty", totalQty);
        r.put("groups", gs);
        return ApiResult.ok(r);
    }

    // ---------------- 生成 ----------------

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/generate")
    public ApiResult<List<Map<String, Object>>> generate(@RequestBody(required = false) Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        String strategy = body == null ? null : Repo.str(body.get("strategy"));
        if (strategy == null || strategy.isBlank()) strategy = "BY_AREA";
        boolean autoAssign = body != null && Boolean.parseBoolean(Repo.str(body.get("autoAssign")));
        // 创建人恒为当前登录人，不再信任请求体里的 creatorNo（SEC-18）
        String creatorNo = me.employeeNo();
        String creatorName = me.employeeName() == null ? "当前用户" : me.employeeName();
        String namePrefix = body == null ? null : Repo.str(body.get("namePrefix"));
        String prefix = namePrefix != null && !namePrefix.isBlank() ? namePrefix : Repo.today() + " 波次";
        List<String> orderNos = strList(body == null ? null : body.get("orderNos"));

        List<Map<String, Object>> orders = candidates(orderNos);
        if (orders.isEmpty()) throw new IllegalStateException("没有符合条件的订单，无法生成波次");

        Map<String, List<Map<String, Object>>> itemsByOrder = itemsOf(orders);
        List<Group> groups = buildGroups(orders, strategy, body, itemsByOrder);

        Map<String, Integer> pathSeq = locationPathSeq();
        List<String> created = new ArrayList<>();
        int gi = 0;
        for (Group g : groups) {
            gi++;
            String waveNo = repo.nextWaveNo();
            String waveName = prefix + " " + String.format("%02d", gi) + "（" + g.key + "）";
            String now = Repo.now();

            repo.jdbc().update("INSERT INTO wave (wave_no,wave_name,strategy,status,order_count,sku_count,total_qty," +
                            "location_count,province_scope,creator_no,creator_name,create_time,has_shortage) " +
                            "VALUES (?,?,?, 'CREATED', ?,?,?,?,?,?,?,?,0)",
                    waveNo, waveName, strategy, g.orders.size(), g.skuCount, g.totalQty,
                    g.locationCount, g.key, creatorNo, creatorName, now);

            int seq = 1;
            for (Map<String, Object> o : g.orders) {
                String orderNo = Repo.str(o.get("orderNo"));
                repo.jdbc().update("INSERT INTO wave_order (wave_no,order_no,seq_no) VALUES (?,?,?)",
                        waveNo, orderNo, seq++);
                repo.jdbc().update("UPDATE `order` SET status='WAVED', wave_no=? WHERE order_no=?", waveNo, orderNo);
            }

            createPickTask(waveNo, g, itemsByOrder, pathSeq, now);

            repo.log("WAVE", waveNo, "WAVE_CREATE", "生成波次", creatorNo, creatorName, me.role(),
                    "策略=" + Dict.desc("waveStrategy", strategy) + "，订单 " + g.orders.size() +
                            " 单，" + g.skuCount + " 个 SKU，共 " + g.totalQty + " 件");

            if (autoAssign) {
                Map<String, Object> lightest = lightestPicker();
                if (lightest != null) {
                    String pNo = Repo.str(lightest.get("employeeNo"));
                    String pName = Repo.str(lightest.get("employeeName"));
                    repo.jdbc().update("UPDATE wave SET picker_no=?, picker_name=?, status='ASSIGNED', assign_time=? WHERE wave_no=?",
                            pNo, pName, Repo.now(), waveNo);
                    repo.jdbc().update("UPDATE pick_task SET picker_no=?, picker_name=? WHERE wave_no=?", pNo, pName, waveNo);
                }
            }
            created.add(waveNo);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String no : created) result.add(repo.waveVo(no));
        return ApiResult.ok(result);
    }

    private void createPickTask(String waveNo, Group g, Map<String, List<Map<String, Object>>> itemsByOrder,
                                Map<String, Integer> pathSeq, String now) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> o : g.orders) {
            String orderNo = Repo.str(o.get("orderNo"));
            for (Map<String, Object> it : itemsByOrder.getOrDefault(orderNo, Collections.emptyList())) {
                String key = Repo.str(it.get("locationCode")) + "|" + Repo.str(it.get("skuCode"));
                Map<String, Object> m = merged.get(key);
                if (m == null) {
                    m = new LinkedHashMap<>();
                    m.put("locationCode", it.get("locationCode"));
                    m.put("skuCode", it.get("skuCode"));
                    m.put("skuName", it.get("skuName"));
                    m.put("spec", it.get("spec"));
                    m.put("unit", it.get("unit"));
                    m.put("shouldQty", 0);
                    m.put("related", new LinkedHashSet<String>());
                    merged.put(key, m);
                }
                m.put("shouldQty", Repo.i(m.get("shouldQty")) + Repo.i(it.get("qty")));
                @SuppressWarnings("unchecked")
                Set<String> rel = (Set<String>) m.get("related");
                rel.add(orderNo);
            }
        }

        List<Map<String, Object>> items = new ArrayList<>(merged.values());
        items.sort(Comparator.comparingInt(m -> pathSeq.getOrDefault(Repo.str(m.get("locationCode")), Integer.MAX_VALUE)));

        String taskNo = "PK" + waveNo.substring(2);
        int shouldQty = 0;
        Set<String> locs = new LinkedHashSet<>();
        StringBuilder path = new StringBuilder();
        for (Map<String, Object> m : items) {
            shouldQty += Repo.i(m.get("shouldQty"));
            String loc = Repo.str(m.get("locationCode"));
            locs.add(loc);
            if (path.length() > 0) path.append(" → ");
            path.append(loc);
        }

        repo.jdbc().update("INSERT INTO pick_task (task_no,wave_no,status,item_count,finished_count,location_count," +
                        "should_qty,actual_qty,shortage_qty,order_count,path_text,create_time) " +
                        "VALUES (?,?, 'WAITING', ?,0,?,?,0,0,?,?,?)",
                taskNo, waveNo, items.size(), locs.size(), shouldQty, g.orders.size(), path.toString(), now);

        int seq = 1;
        for (Map<String, Object> m : items) {
            @SuppressWarnings("unchecked")
            Set<String> rel = (Set<String>) m.get("related");
            repo.jdbc().update("INSERT INTO pick_item (task_no,wave_no,seq_no,location_code,sku_code,sku_name,spec," +
                            "unit,should_qty,actual_qty,shortage_qty,status,related_order_nos) VALUES (?,?,?,?,?,?,?,?,?,0,0,'PENDING',?)",
                    taskNo, waveNo, seq++, m.get("locationCode"), m.get("skuCode"), m.get("skuName"),
                    m.get("spec"), m.get("unit"), m.get("shouldQty"), String.join(",", rel));
        }
    }

    private Map<String, Object> lightestPicker() {
        List<Map<String, Object>> pickers = JdbcMaps.list(repo.jdbc(),
                "SELECT employee_no, employee_name FROM employee WHERE role='PICKER' AND status=1");
        Map<String, Object> best = null;
        long bestLoad = Long.MAX_VALUE;
        for (Map<String, Object> p : pickers) {
            long load = JdbcMaps.count(repo.jdbc(),
                    "SELECT COUNT(*) FROM wave WHERE picker_no=? AND status IN ('ASSIGNED','PICKING','REVIEWING')",
                    Repo.str(p.get("employeeNo")));
            if (load < bestLoad) {
                bestLoad = load;
                best = p;
            }
        }
        return best;
    }

    // ---------------- 指派 ----------------

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/assign")
    public ApiResult<Map<String, Object>> assign(@RequestBody Map<String, Object> body) {
        LoginUser me = AuthContext.require();
        String waveNo = Repo.str(body.get("waveNo"));
        String pickerNo = Repo.str(body.get("pickerNo"));
        if (waveNo == null || pickerNo == null) throw new IllegalArgumentException("缺少波次号或拣货员");
        Map<String, Object> w = JdbcMaps.one(repo.jdbc(), "SELECT * FROM wave WHERE wave_no=?", waveNo);
        if (w == null) throw new IllegalArgumentException("波次不存在");
        String st = Repo.str(w.get("status"));
        if (!"CREATED".equals(st) && !"ASSIGNED".equals(st)) {
            throw new IllegalStateException("只有「待分配 / 已分配」的波次可以改派拣货员");
        }
        Map<String, Object> picker = JdbcMaps.one(repo.jdbc(), "SELECT * FROM employee WHERE employee_no=?", pickerNo);
        if (picker == null) throw new IllegalArgumentException("拣货员不存在");
        if (!"PICKER".equals(Repo.str(picker.get("role")))) {
            throw new IllegalArgumentException(Repo.str(picker.get("employeeName")) + " 不是拣货员");
        }
        String pickerName = Repo.str(picker.get("employeeName"));
        repo.jdbc().update("UPDATE wave SET picker_no=?, picker_name=?, status='ASSIGNED', assign_time=? WHERE wave_no=?",
                pickerNo, pickerName, Repo.now(), waveNo);
        repo.jdbc().update("UPDATE pick_task SET picker_no=?, picker_name=? WHERE wave_no=?", pickerNo, pickerName, waveNo);
        repo.log("WAVE", waveNo, "WAVE_ASSIGN", "分配拣货员", me.employeeNo(), me.employeeName(), me.role(),
                "拣货员=" + pickerName);
        return ApiResult.ok(repo.waveVo(waveNo));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/{waveNo}/close")
    public ApiResult<Map<String, Object>> close(@PathVariable String waveNo) {
        LoginUser me = AuthContext.require();
        Map<String, Object> w = JdbcMaps.one(repo.jdbc(), "SELECT * FROM wave WHERE wave_no=?", waveNo);
        if (w == null) throw new IllegalArgumentException("波次不存在");
        if (!"HANDOVERED".equals(Repo.str(w.get("status")))) {
            throw new IllegalStateException("只有「已出库交接」的波次可以归档");
        }
        repo.jdbc().update("UPDATE wave SET status='CLOSED' WHERE wave_no=?", waveNo);
        repo.log("WAVE", waveNo, "WAVE_CLOSE", "归档波次", me.employeeNo(), me.employeeName(), me.role(), "波次归档");
        return ApiResult.ok(repo.waveVo(waveNo));
    }

    @RequireRole({"ADMIN", "WAVE_OPERATOR"})
    @PostMapping("/{waveNo}/cancel")
    public ApiResult<Map<String, Object>> cancel(@PathVariable String waveNo) {
        LoginUser me = AuthContext.require();
        Map<String, Object> w = JdbcMaps.one(repo.jdbc(), "SELECT * FROM wave WHERE wave_no=?", waveNo);
        if (w == null) throw new IllegalArgumentException("波次不存在");
        String st = Repo.str(w.get("status"));
        if (!"CREATED".equals(st) && !"ASSIGNED".equals(st)) {
            throw new IllegalStateException("只有「待分配 / 已分配」的波次可以取消");
        }
        List<Map<String, Object>> rels = JdbcMaps.list(repo.jdbc(),
                "SELECT order_no FROM wave_order WHERE wave_no=?", waveNo);
        repo.jdbc().update("UPDATE `order` SET status='POOLED', wave_no=NULL WHERE wave_no=? AND status='WAVED'", waveNo);
        repo.jdbc().update("DELETE FROM wave_order WHERE wave_no=?", waveNo);
        List<String> taskNos = new ArrayList<>();
        for (Map<String, Object> t : JdbcMaps.list(repo.jdbc(),
                "SELECT task_no FROM pick_task WHERE wave_no=?", waveNo)) {
            taskNos.add(Repo.str(t.get("taskNo")));
        }
        repo.jdbc().update("DELETE FROM pick_task WHERE wave_no=?", waveNo);
        for (String taskNo : taskNos) {
            repo.jdbc().update("DELETE FROM pick_item WHERE task_no=?", taskNo);
        }
        repo.jdbc().update("UPDATE wave SET status='CANCELLED' WHERE wave_no=?", waveNo);
        repo.log("WAVE", waveNo, "WAVE_CANCEL", "取消波次", me.employeeNo(), me.employeeName(), me.role(),
                "退回 " + rels.size() + " 笔订单至订单池");
        return ApiResult.ok(repo.waveVo(waveNo));
    }

    // ---------------- 分页/详情 ----------------

    @GetMapping("/page")
    public ApiResult<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String waveNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) String pickerNo,
            @RequestParam(required = false) Boolean onlyWorking,
            @RequestParam(required = false) Boolean onlyShortage,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        StringBuilder sql = new StringBuilder("SELECT * FROM wave WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (notBlank(waveNo)) {
            sql.append(" AND (wave_no LIKE ? OR wave_name LIKE ?)");
            String k = "%" + waveNo.trim() + "%";
            args.add(k); args.add(k);
        }
        if (notBlank(status)) { sql.append(" AND status=?"); args.add(status); }
        if (notBlank(strategy)) { sql.append(" AND strategy=?"); args.add(strategy); }
        if (notBlank(pickerNo)) { sql.append(" AND picker_no=?"); args.add(pickerNo); }
        if (Boolean.TRUE.equals(onlyWorking)) {
            sql.append(" AND status IN ('ASSIGNED','PICKING','REVIEWING')");
        }
        if (Boolean.TRUE.equals(onlyShortage)) {
            sql.append(" AND has_shortage=1");
        }
        sql.append(" ORDER BY CASE WHEN status IN ('ASSIGNED','PICKING','REVIEWING') THEN 0 ELSE 1 END, create_time DESC, id DESC");

        Map<String, Object> pg = JdbcMaps.page(repo.jdbc(), sql.toString(), pageNum, pageSize, args.toArray());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pg.get("records");
        for (Map<String, Object> w : rows) {
            repo.enrichWave(w);
            w.put("orders", JdbcMaps.list(repo.jdbc(),
                    "SELECT o.* FROM `order` o JOIN wave_order wo ON wo.order_no=o.order_no " +
                            "WHERE wo.wave_no=? ORDER BY wo.seq_no", Repo.str(w.get("waveNo"))));
        }
        return ApiResult.ok(new PageResult<>(rows, ((Number) pg.get("total")).longValue(), pageNum, pageSize));
    }

    @GetMapping("/{waveNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String waveNo) {
        Map<String, Object> w = repo.waveVo(waveNo);
        if (w == null) throw new IllegalArgumentException("波次不存在");
        return ApiResult.ok(w);
    }

    // ---------------- 分组辅助 ----------------

    private static class Group {
        String key;
        final List<Map<String, Object>> orders = new ArrayList<>();
        int skuCount;
        int totalQty;
        int locationCount;
    }

    private List<Map<String, Object>> candidates(List<String> orderNos) {
        StringBuilder sql = new StringBuilder("SELECT * FROM `order` WHERE ");
        List<Object> args = new ArrayList<>();
        if (orderNos != null && !orderNos.isEmpty()) {
            sql.append("order_no IN (").append(String.join(",", Collections.nCopies(orderNos.size(), "?"))).append(")");
            args.addAll(orderNos);
            sql.append(" AND status IN ('CREATED','POOLED')");
        } else {
            sql.append("status='POOLED'");
        }
        sql.append(" ORDER BY CASE priority WHEN 'URGENT' THEN 0 ELSE 1 END, pay_time ASC");
        return JdbcMaps.list(repo.jdbc(), sql.toString(), args.toArray());
    }

    private Map<String, List<Map<String, Object>>> itemsOf(List<Map<String, Object>> orders) {
        Map<String, List<Map<String, Object>>> map = new HashMap<>();
        if (orders.isEmpty()) return map;
        List<String> nos = new ArrayList<>();
        for (Map<String, Object> o : orders) nos.add(Repo.str(o.get("orderNo")));
        String in = String.join(",", Collections.nCopies(nos.size(), "?"));
        List<Map<String, Object>> items = JdbcMaps.list(repo.jdbc(),
                "SELECT * FROM order_item WHERE order_no IN (" + in + ")", nos.toArray());
        for (Map<String, Object> it : items) {
            map.computeIfAbsent(Repo.str(it.get("orderNo")), k -> new ArrayList<>()).add(it);
        }
        return map;
    }

    private String groupKeyOf(Map<String, Object> o, String strategy) {
        switch (strategy) {
            case "BY_CARRIER": {
                String n = Repo.str(o.get("carrierName"));
                return n == null || n.isBlank() ? "未知承运商" : n;
            }
            case "BY_TIMELINESS":
                return "URGENT".equals(Repo.str(o.get("priority"))) ? "加急订单" : "普通订单";
            case "BY_ORDER_COUNT":
                return "均衡批次";
            default: {
                String p = Repo.str(o.get("province"));
                return p == null || p.isBlank() ? "未知区域" : p;
            }
        }
    }

    private List<Group> buildGroups(List<Map<String, Object>> candidates, String strategy,
                                    Map<String, Object> body, Map<String, List<Map<String, Object>>> itemsByOrder) {
        int maxOrders = intOr(body == null ? null : body.get("maxOrdersPerWave"), 12);
        int maxSkus = intOr(body == null ? null : body.get("maxSkusPerWave"), 30);
        int maxQty = intOr(body == null ? null : body.get("maxQtyPerWave"), 120);

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> o : candidates) {
            grouped.computeIfAbsent(groupKeyOf(o, strategy), k -> new ArrayList<>()).add(o);
        }

        List<Group> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            String baseKey = entry.getKey();
            List<Map<String, Object>> cur = new ArrayList<>();
            Set<String> skuSet = new HashSet<>();
            Set<String> locSet = new HashSet<>();
            int qty = 0;
            int batch = 1;

            for (Map<String, Object> o : entry.getValue()) {
                List<Map<String, Object>> items = itemsByOrder.getOrDefault(Repo.str(o.get("orderNo")), Collections.emptyList());
                Set<String> orderSkus = new HashSet<>();
                int orderQty = 0;
                for (Map<String, Object> it : items) {
                    orderSkus.add(Repo.str(it.get("skuCode")));
                    orderQty += Repo.i(it.get("qty"));
                }
                Set<String> merged = new HashSet<>(skuSet);
                merged.addAll(orderSkus);

                boolean overflow = !cur.isEmpty()
                        && (cur.size() + 1 > maxOrders || merged.size() > maxSkus || qty + orderQty > maxQty);
                if (overflow) {
                    result.add(makeGroup(baseKey, batch, cur, skuSet, locSet, qty));
                    cur = new ArrayList<>();
                    skuSet = new HashSet<>();
                    locSet = new HashSet<>();
                    qty = 0;
                    batch++;
                }
                cur.add(o);
                skuSet.addAll(orderSkus);
                for (Map<String, Object> it : items) locSet.add(Repo.str(it.get("locationCode")));
                qty += orderQty;
            }
            if (!cur.isEmpty()) result.add(makeGroup(baseKey, batch, cur, skuSet, locSet, qty));
        }
        return result;
    }

    private Group makeGroup(String baseKey, int batch, List<Map<String, Object>> cur,
                            Set<String> skuSet, Set<String> locSet, int qty) {
        Group g = new Group();
        g.key = batch > 1 ? baseKey + " #" + batch : baseKey;
        g.orders.addAll(cur);
        g.skuCount = skuSet.size();
        g.locationCount = locSet.size();
        g.totalQty = qty;
        return g;
    }

    private Map<String, Integer> locationPathSeq() {
        Map<String, Integer> m = new HashMap<>();
        for (Map<String, Object> loc : JdbcMaps.list(repo.jdbc(), "SELECT location_code, path_seq FROM location")) {
            m.put(Repo.str(loc.get("locationCode")), Repo.i(loc.get("pathSeq")));
        }
        return m;
    }

    private static int intOr(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try {
            int v = Integer.parseInt(String.valueOf(o));
            return v > 0 ? v : def;
        } catch (Exception e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object o) {
        if (o instanceof List<?> list) {
            List<String> r = new ArrayList<>();
            for (Object x : list) if (x != null) r.add(String.valueOf(x));
            return r;
        }
        return Collections.emptyList();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
