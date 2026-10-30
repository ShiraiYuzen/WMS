package com.example.wms.entity;

/**
 * 波次。status: 待拣货 / 拣货中 / 待复核 / 待出库 / 已出库 / 已取消
 */
public class Wave {
    private Long id;
    private String waveNo;
    private String status;
    private Integer orderCount;
    private Integer itemCount;
    private Integer totalQty;
    private Long pickerId;
    private String pickerName;
    private Long checkerId;
    private String checkerName;
    private Long creatorId;
    private String creatorName;
    private String strategy;
    private String remark;
    private String createTime;
    private String startTime;
    private String pickFinishTime;
    private String reviewFinishTime;
    private String outboundTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getTotalQty() { return totalQty; }
    public void setTotalQty(Integer totalQty) { this.totalQty = totalQty; }
    public Long getPickerId() { return pickerId; }
    public void setPickerId(Long pickerId) { this.pickerId = pickerId; }
    public String getPickerName() { return pickerName; }
    public void setPickerName(String pickerName) { this.pickerName = pickerName; }
    public Long getCheckerId() { return checkerId; }
    public void setCheckerId(Long checkerId) { this.checkerId = checkerId; }
    public String getCheckerName() { return checkerName; }
    public void setCheckerName(String checkerName) { this.checkerName = checkerName; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getPickFinishTime() { return pickFinishTime; }
    public void setPickFinishTime(String pickFinishTime) { this.pickFinishTime = pickFinishTime; }
    public String getReviewFinishTime() { return reviewFinishTime; }
    public void setReviewFinishTime(String reviewFinishTime) { this.reviewFinishTime = reviewFinishTime; }
    public String getOutboundTime() { return outboundTime; }
    public void setOutboundTime(String outboundTime) { this.outboundTime = outboundTime; }
}
