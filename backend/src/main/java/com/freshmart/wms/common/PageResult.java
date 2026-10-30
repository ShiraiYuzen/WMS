package com.freshmart.wms.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页响应体。
 *
 * @param <T> 行数据类型
 */
@Data
@Accessors(chain = true)
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    @Schema(description = "当前页数据")
    private List<T> records = Collections.emptyList();

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "页码")
    private int pageNum;

    @Schema(description = "每页条数")
    private int pageSize;

    @Schema(description = "总页数")
    private int pages;

    public static <T> PageResult<T> of(List<T> all, int pageNum, int pageSize) {
        PageResult<T> pr = new PageResult<>();
        int size = pageSize < 1 ? 10 : pageSize;
        int num = pageNum < 1 ? 1 : pageNum;
        int total = all == null ? 0 : all.size();
        int from = Math.min((num - 1) * size, total);
        int to = Math.min(from + size, total);
        pr.setRecords(all == null ? Collections.emptyList() : all.subList(from, to));
        pr.setTotal(total);
        pr.setPageNum(num);
        pr.setPageSize(size);
        pr.setPages((total + size - 1) / size);
        return pr;
    }

    public static <T> PageResult<T> of(List<T> all, PageQuery query) {
        return of(all, query.getPageNum(), query.getPageSize());
    }

    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return of(Collections.emptyList(), pageNum, pageSize);
    }
}
