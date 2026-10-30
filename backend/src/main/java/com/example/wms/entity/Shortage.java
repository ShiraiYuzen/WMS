package com.example.wms.entity;

/**
 * 缺货记录。status: 待处理 / 已补货 / 已核减
 */
public class Shortage {
    private Long id;
    private Long waveId;
    private String waveNo;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String location;
    private Integer requiredQty;
    private Integer pickedQty;
    private Integer shortQty;
    private String reason;       // 库存不足 / 库位缺货 / 商品破损 / 其他
    private Long reporterId;
    private String reporterName;
    private String status;
    private String handleRemark;
    private String createTime;
    private String handleTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWaveId() { return waveId; }
    public void setWaveId(Long waveId) { this.waveId = waveId; }
    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getRequiredQty() { return requiredQty; }
    public void setRequiredQty(Integer requiredQty) { this.requiredQty = requiredQty; }
    public Integer getPickedQty() { return pickedQty; }
    public void setPickedQty(Integer pickedQty) { this.pickedQty = pickedQty; }
    public Integer getShortQty() { return shortQty; }
    public void setShortQty(Integer shortQty) { this.shortQty = shortQty; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHandleRemark() { return handleRemark; }
    public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getHandleTime() { return handleTime; }
    public void setHandleTime(String handleTime) { this.handleTime = handleTime; }
}
