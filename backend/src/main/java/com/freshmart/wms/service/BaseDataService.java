package com.freshmart.wms.service;

import com.freshmart.wms.common.PageQuery;
import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.domain.entity.Carrier;
import com.freshmart.wms.domain.entity.Employee;
import com.freshmart.wms.domain.entity.Sku;
import com.freshmart.wms.domain.entity.WarehouseLocation;
import com.freshmart.wms.domain.enums.*;
import com.freshmart.wms.repository.CarrierRepository;
import com.freshmart.wms.repository.EmployeeRepository;
import com.freshmart.wms.repository.LocationRepository;
import com.freshmart.wms.repository.SkuRepository;
import com.freshmart.wms.vo.EnumOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基础数据服务：商品、库位、人员、承运商、字典。
 *
 * <p>字典接口 {@link #allEnums()} 让前端不必硬编码枚举，
 * 后续新增枚举项只需改后端，前端下拉自动跟随。
 */
@Service
@RequiredArgsConstructor
public class BaseDataService {

    private final SkuRepository skuRepo;
    private final LocationRepository locationRepo;
    private final EmployeeRepository employeeRepo;
    private final CarrierRepository carrierRepo;

    /* ---------------- 商品 ---------------- */

    public PageResult<Sku> pageSkus(String keyword, String category, PageQuery query) {
        List<Sku> all = skuRepo.findAll().stream()
                .filter(s -> keyword == null || keyword.isBlank()
                        || s.getSkuCode().toLowerCase().contains(keyword.toLowerCase())
                        || s.getSkuName().contains(keyword))
                .filter(s -> category == null || category.isBlank() || category.equals(s.getCategory()))
                .collect(Collectors.toList());
        return PageResult.of(all, query);
    }

    public List<String> skuCategories() {
        return skuRepo.findAll().stream()
                .map(Sku::getCategory).distinct().sorted().collect(Collectors.toList());
    }

    /* ---------------- 库位 ---------------- */

    public PageResult<WarehouseLocation> pageLocations(String zone, PageQuery query) {
        List<WarehouseLocation> all = locationRepo.findAll().stream()
                .filter(l -> zone == null || zone.isBlank() || zone.equals(l.getZone()))
                .sorted((a, b) -> Integer.compare(
                        a.getPathSeq() == null ? 0 : a.getPathSeq(),
                        b.getPathSeq() == null ? 0 : b.getPathSeq()))
                .collect(Collectors.toList());
        return PageResult.of(all, query);
    }

    public List<String> zones() {
        return locationRepo.findAll().stream()
                .map(WarehouseLocation::getZone).distinct().sorted().collect(Collectors.toList());
    }

    /* ---------------- 人员 ---------------- */

    public PageResult<Employee> pageEmployees(String role, String team, PageQuery query) {
        List<Employee> all = employeeRepo.findAll().stream()
                .filter(e -> role == null || role.isBlank() || (e.getRole() != null && role.equals(e.getRole().getCode())))
                .filter(e -> team == null || team.isBlank() || team.equals(e.getTeam()))
                .collect(Collectors.toList());
        return PageResult.of(all, query);
    }

    public List<Employee> listByRole(EmployeeRole role) {
        return employeeRepo.find(e -> e.getRole() == role && Integer.valueOf(1).equals(e.getStatus()));
    }

    public List<String> teams() {
        return employeeRepo.findAll().stream()
                .map(Employee::getTeam).filter(t -> t != null && !t.isBlank())
                .distinct().sorted().collect(Collectors.toList());
    }

    /* ---------------- 承运商 ---------------- */

    public List<Carrier> listCarriers() {
        return carrierRepo.find(c -> Integer.valueOf(1).equals(c.getStatus()));
    }

    /* ---------------- 字典 ---------------- */

    /**
     * 一次性返回前端所需的全部字典。
     * 前端启动时拉一次并缓存到 Pinia，页面里用 {@code dict.orderStatus[code]} 取值。
     */
    public Map<String, List<EnumOptionVO>> allEnums() {
        Map<String, List<EnumOptionVO>> map = new LinkedHashMap<>();
        map.put("orderStatus", toOptions(OrderStatus.values()));
        map.put("waveStatus", toOptions(WaveStatus.values()));
        map.put("waveStrategy", toStrategyOptions());
        map.put("pickTaskStatus", toOptions(PickTaskStatus.values()));
        map.put("pickItemStatus", toOptions(PickItemStatus.values()));
        map.put("reviewStatus", toOptions(ReviewStatus.values()));
        map.put("diffType", toOptions(DiffType.values()));
        map.put("shortageStatus", toOptions(ShortageStatus.values()));
        map.put("shortageReason", toOptions(ShortageReason.values()));
        map.put("shortageHandleType", toHandleTypeOptions());
        map.put("handoverStatus", toOptions(HandoverStatus.values()));
        map.put("employeeRole", toOptions(EmployeeRole.values()));
        map.put("locationStatus", toOptions(LocationStatus.values()));
        map.put("priority", Arrays.asList(
                EnumOptionVO.of("URGENT", "加急"),
                EnumOptionVO.of("NORMAL", "普通")));
        map.put("platform", Arrays.asList(
                EnumOptionVO.of("天猫", "天猫"),
                EnumOptionVO.of("京东", "京东"),
                EnumOptionVO.of("拼多多", "拼多多"),
                EnumOptionVO.of("抖音商城", "抖音商城"),
                EnumOptionVO.of("美团优选", "美团优选")));
        map.put("packMaterial", Arrays.asList(
                EnumOptionVO.of("纸箱", "纸箱"),
                EnumOptionVO.of("气泡袋", "气泡袋"),
                EnumOptionVO.of("保温箱", "保温箱")));
        map.put("granularity", Arrays.asList(
                EnumOptionVO.of("DAY", "按天"),
                EnumOptionVO.of("WEEK", "按周")));
        return map;
    }

    private List<EnumOptionVO> toOptions(CodedEnum[] values) {
        List<EnumOptionVO> list = new ArrayList<>(values.length);
        for (CodedEnum v : values) {
            list.add(EnumOptionVO.of(v.getCode(), v.getDesc()));
        }
        return list;
    }

    private List<EnumOptionVO> toStrategyOptions() {
        return Arrays.stream(WaveStrategy.values())
                .map(v -> EnumOptionVO.of(v.getCode(), v.getDesc(), v.getDetail()))
                .collect(Collectors.toList());
    }

    private List<EnumOptionVO> toHandleTypeOptions() {
        return Arrays.stream(ShortageHandleType.values())
                .map(v -> EnumOptionVO.of(v.getCode(), v.getDesc(), v.getDetail()))
                .collect(Collectors.toList());
    }
}
