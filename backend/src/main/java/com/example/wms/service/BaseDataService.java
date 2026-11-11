package com.example.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.wms.common.Dict;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.Carrier;
import com.example.wms.entity.Employee;
import com.example.wms.entity.Location;
import com.example.wms.entity.Sku;
import com.example.wms.mapper.CarrierMapper;
import com.example.wms.mapper.EmployeeMapper;
import com.example.wms.mapper.LocationMapper;
import com.example.wms.mapper.SkuMapper;
import com.example.wms.service.support.BizNumbers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 主数据：单表查询优先走 MyBatis-Plus {@code BaseMapper} + {@code LambdaQueryWrapper}。
 */
@Service
public class BaseDataService {

    private final SkuMapper skuMapper;
    private final LocationMapper locationMapper;
    private final EmployeeMapper employeeMapper;
    private final CarrierMapper carrierMapper;
    private final ObjectMapper objectMapper;

    public BaseDataService(SkuMapper skuMapper,
                           LocationMapper locationMapper,
                           EmployeeMapper employeeMapper,
                           CarrierMapper carrierMapper,
                           ObjectMapper objectMapper) {
        this.skuMapper = skuMapper;
        this.locationMapper = locationMapper;
        this.employeeMapper = employeeMapper;
        this.carrierMapper = carrierMapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, List<Map<String, String>>> enums() {
        return Dict.all();
    }

    public List<String> skuCategories() {
        return skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .select(Sku::getCategory)
                        .isNotNull(Sku::getCategory)
                        .groupBy(Sku::getCategory)
                        .orderByAsc(Sku::getCategory))
                .stream()
                .map(Sku::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toList());
    }

    public PageResult<Map<String, Object>> skuPage(String keyword, String category, int pageNum, int pageSize) {
        LambdaQueryWrapper<Sku> q = new LambdaQueryWrapper<>();
        if (notBlank(keyword)) {
            String k = keyword.trim();
            q.and(w -> w.like(Sku::getSkuCode, k).or().like(Sku::getSkuName, k).or().like(Sku::getBarcode, k));
        }
        if (notBlank(category)) {
            q.eq(Sku::getCategory, category);
        }
        q.orderByAsc(Sku::getSkuCode);
        Page<Sku> page = skuMapper.selectPage(new Page<>(pageNum, pageSize), q);
        return toPage(page, pageNum, pageSize);
    }

    public List<String> zones() {
        return locationMapper.selectList(new LambdaQueryWrapper<Location>()
                        .select(Location::getZone)
                        .isNotNull(Location::getZone)
                        .groupBy(Location::getZone)
                        .orderByAsc(Location::getZone))
                .stream()
                .map(Location::getZone)
                .filter(z -> z != null && !z.isBlank())
                .collect(Collectors.toList());
    }

    public PageResult<Map<String, Object>> locationPage(String zone, int pageNum, int pageSize) {
        LambdaQueryWrapper<Location> q = new LambdaQueryWrapper<>();
        if (notBlank(zone)) {
            q.eq(Location::getZone, zone);
        }
        q.orderByAsc(Location::getPathSeq).orderByAsc(Location::getLocationCode);
        Page<Location> page = locationMapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Location loc : page.getRecords()) {
            Map<String, Object> m = toMap(loc);
            m.put("statusDesc", Dict.desc("locationStatus", BizNumbers.str(m.get("status"))));
            rows.add(m);
        }
        return new PageResult<>(rows, page.getTotal(), pageNum, pageSize);
    }

    public List<Map<String, Object>> employeeByRole(String role) {
        LambdaQueryWrapper<Employee> q = new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStatus, 1)
                .orderByAsc(Employee::getEmployeeNo);
        if (notBlank(role)) {
            q.eq(Employee::getRole, role);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employee e : employeeMapper.selectList(q)) {
            Map<String, Object> m = toMap(e);
            m.put("roleDesc", Dict.desc("employeeRole", BizNumbers.str(m.get("role"))));
            rows.add(m);
        }
        return rows;
    }

    public List<String> teams() {
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                        .select(Employee::getTeam)
                        .isNotNull(Employee::getTeam)
                        .groupBy(Employee::getTeam)
                        .orderByAsc(Employee::getTeam))
                .stream()
                .map(Employee::getTeam)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.toList());
    }

    public PageResult<Map<String, Object>> employeePage(String role, String team, int pageNum, int pageSize) {
        LambdaQueryWrapper<Employee> q = new LambdaQueryWrapper<>();
        if (notBlank(role)) {
            q.eq(Employee::getRole, role);
        }
        if (notBlank(team)) {
            q.eq(Employee::getTeam, team);
        }
        q.orderByAsc(Employee::getEmployeeNo);
        Page<Employee> page = employeeMapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employee e : page.getRecords()) {
            Map<String, Object> m = toMap(e);
            m.put("roleDesc", Dict.desc("employeeRole", BizNumbers.str(m.get("role"))));
            rows.add(m);
        }
        return new PageResult<>(rows, page.getTotal(), pageNum, pageSize);
    }

    public List<Map<String, Object>> carrierList() {
        List<Carrier> list = carrierMapper.selectList(new LambdaQueryWrapper<Carrier>()
                .eq(Carrier::getStatus, 1)
                .orderByAsc(Carrier::getCarrierCode));
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (Carrier c : list) {
            rows.add(toMap(c));
        }
        return rows;
    }

    private PageResult<Map<String, Object>> toPage(Page<?> page, int pageNum, int pageSize) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object row : page.getRecords()) {
            rows.add(toMap(row));
        }
        return new PageResult<>(rows, page.getTotal(), pageNum, pageSize);
    }

    private Map<String, Object> toMap(Object bean) {
        if (bean == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(bean, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
