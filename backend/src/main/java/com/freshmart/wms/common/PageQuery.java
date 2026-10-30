package com.freshmart.wms.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 通用分页请求参数。所有列表查询接口继承或组合该类。
 */
@Data
@Accessors(chain = true)
@Schema(description = "分页查询参数")
public class PageQuery implements Serializable {

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "排序字段（实体属性名）", example = "createTime")
    private String sortField;

    @Schema(description = "排序方向：asc / desc", example = "desc")
    private String sortOrder = "desc";

    public int offset() {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        return (num - 1) * size;
    }

    public int limit() {
        return pageSize == null || pageSize < 1 ? 10 : pageSize;
    }

    public boolean desc() {
        return !"asc".equalsIgnoreCase(sortOrder);
    }
}
