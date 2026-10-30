package com.example.wms.dto;

import java.util.ArrayList;
import java.util.List;

/** 生成波次请求：选择待分波订单 + 指派拣货员 */
public class WaveCreateRequest {
    private List<Long> orderIds = new ArrayList<>();
    private Long pickerId;
    private String remark;

    public List<Long> getOrderIds() { return orderIds; }
    public void setOrderIds(List<Long> orderIds) { this.orderIds = orderIds; }
    public Long getPickerId() { return pickerId; }
    public void setPickerId(Long pickerId) { this.pickerId = pickerId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
