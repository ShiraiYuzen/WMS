package com.freshmart.wms.controller;

import com.freshmart.wms.common.PageQuery;
import com.freshmart.wms.common.PageResult;
import com.freshmart.wms.common.R;
import com.freshmart.wms.domain.entity.Carrier;
import com.freshmart.wms.domain.entity.Employee;
import com.freshmart.wms.domain.entity.Sku;
import com.freshmart.wms.domain.entity.WarehouseLocation;
import com.freshmart.wms.domain.enums.EmployeeRole;
import com.freshmart.wms.service.BaseDataService;
import com.freshmart.wms.vo.EnumOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 基础数据与字典接口。
 *
 * <p>前端启动时调用 {@code /api/base/enums} 拉一次字典并缓存到 Pinia，
 * 页面里用 {@code dict.waveStatus[code]} 取中文，避免前后端字典漂移。
 */
@Tag(name = "08. 基础数据", description = "商品、库位、人员、承运商、字典")
@RestController
@RequestMapping("/api/base")
@RequiredArgsConstructor
public class BaseDataController {

    private final BaseDataService baseDataService;

    /* ---------------- 字典 ---------------- */

    @Operation(summary = "全部字典", description = "一次返回前端所需的全部枚举字典")
    @GetMapping("/enums")
    public R<Map<String, List<EnumOptionVO>>> enums() {
        return R.ok(baseDataService.allEnums());
    }

    /* ---------------- 商品 ---------------- */

    @Operation(summary = "商品分页查询")
    @GetMapping("/sku/page")
    public R<PageResult<Sku>> skuPage(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String category,
                                      PageQuery query) {
        return R.ok(baseDataService.pageSkus(keyword, category, query));
    }

    @Operation(summary = "商品品类列表")
    @GetMapping("/sku/categories")
    public R<List<String>> skuCategories() {
        return R.ok(baseDataService.skuCategories());
    }

    /* ---------------- 库位 ---------------- */

    @Operation(summary = "库位分页查询", description = "按库位路径序返回，与拣货路线一致")
    @GetMapping("/location/page")
    public R<PageResult<WarehouseLocation>> locationPage(@RequestParam(required = false) String zone,
                                                         PageQuery query) {
        return R.ok(baseDataService.pageLocations(zone, query));
    }

    @Operation(summary = "库区列表")
    @GetMapping("/location/zones")
    public R<List<String>> zones() {
        return R.ok(baseDataService.zones());
    }

    /* ---------------- 人员 ---------------- */

    @Operation(summary = "人员分页查询")
    @GetMapping("/employee/page")
    public R<PageResult<Employee>> employeePage(@RequestParam(required = false) String role,
                                                @RequestParam(required = false) String team,
                                                PageQuery query) {
        return R.ok(baseDataService.pageEmployees(role, team, query));
    }

    @Operation(summary = "按角色查询人员", description = "role 取值：WAVE_OPERATOR / PICKER / REVIEWER / ADMIN")
    @GetMapping("/employee/by-role")
    public R<List<Employee>> employeesByRole(@RequestParam String role) {
        return R.ok(baseDataService.listByRole(EmployeeRole.of(role)));
    }

    @Operation(summary = "班组列表")
    @GetMapping("/employee/teams")
    public R<List<String>> teams() {
        return R.ok(baseDataService.teams());
    }

    /* ---------------- 承运商 ---------------- */

    @Operation(summary = "承运商列表")
    @GetMapping("/carrier/list")
    public R<List<Carrier>> carriers() {
        return R.ok(baseDataService.listCarriers());
    }
}
