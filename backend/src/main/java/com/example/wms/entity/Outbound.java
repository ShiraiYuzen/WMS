package com.example.wms.entity;

/**
 * 出库交接记录。
 */
public class Outbound {
    private Long id;
    private Long waveId;
    private String waveNo;
    private Integer orderCount;
    private Integer totalQty;
    private String carrier;       // 承运商
    private String handoverTo;    // 交接人
    private Long operatorId;
    private String operatorName;
    private String status;        // 已交接
    private String remark;
    private String createTime;
    private String handoverTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWaveId() { return waveId; }
    public void setWaveId(Long waveId) { this.waveId = waveId; }
    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public Integer getTotalQty() { return totalQty; }
    public void setTotalQty(Integer totalQty) { this.totalQty = totalQty; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getHandoverTo() { return handoverTo; }
    public void setHandoverTo(String handoverTo) { this.handoverTo = handoverTo; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getHandoverTime() { return handoverTime; }
    public void setHandoverTime(String handoverTime) { this.handoverTime = handoverTime; }
}
