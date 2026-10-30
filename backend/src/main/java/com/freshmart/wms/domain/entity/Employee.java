package com.freshmart.wms.domain.entity;

import com.freshmart.wms.domain.enums.EmployeeRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 仓库作业人员。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "仓库作业人员")
public class Employee extends BaseEntity {

    @Schema(description = "工号", example = "P001")
    private String employeeNo;

    @Schema(description = "姓名", example = "张伟")
    private String employeeName;

    @Schema(description = "角色：波次员 / 拣货员 / 复核员 / 仓库主管")
    private EmployeeRole role;

    @Schema(description = "所属班组", example = "拣货一组")
    private String team;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "在职状态：1 在职 / 0 离职")
    private Integer status = 1;
}
