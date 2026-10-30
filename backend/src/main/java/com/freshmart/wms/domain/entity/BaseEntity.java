package com.freshmart.wms.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类。
 *
 * <p>字段命名与后续建表规范一一对应：{@code id} 主键、{@code createTime/updateTime} 审计字段、
 * {@code deleted} 逻辑删除标记。当前内存仓储会忽略 {@code deleted}，
 * 接入 MyBatis-Plus 后在 {@code application.yml} 中开启逻辑删除即可自动生效。
 */
@Data
@Accessors(chain = true)
public abstract class BaseEntity implements Serializable {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最后更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除标记：0 正常 / 1 已删除", hidden = true)
    private Integer deleted = 0;
}
