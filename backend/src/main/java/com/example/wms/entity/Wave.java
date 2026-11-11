package com.example.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("wave")
public class Wave {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String waveNo;
    private String waveName;
    private String strategy;
    private String status;
    private Integer orderCount;
    private Integer skuCount;
    private Integer totalQty;
    private Integer locationCount;
    private String provinceScope;
    private String creatorNo;
    private String creatorName;
    private String pickerNo;
    private String pickerName;
    private String reviewerNo;
    private String reviewerName;
    private String createTime;
    private String assignTime;
    private String pickStartTime;
    private String pickFinishTime;
    private String reviewFinishTime;
    private String handoverTime;
    private Integer pickDurationSec;
    private Integer totalDurationSec;
    private Integer hasShortage;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }
    public String getWaveName() { return waveName; }
    public void setWaveName(String waveName) { this.waveName = waveName; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public Integer getSkuCount() { return skuCount; }
    public void setSkuCount(Integer skuCount) { this.skuCount = skuCount; }
    public Integer getTotalQty() { return totalQty; }
    public void setTotalQty(Integer totalQty) { this.totalQty = totalQty; }
    public Integer getLocationCount() { return locationCount; }
    public void setLocationCount(Integer locationCount) { this.locationCount = locationCount; }
    public String getProvinceScope() { return provinceScope; }
    public void setProvinceScope(String provinceScope) { this.provinceScope = provinceScope; }
    public String getCreatorNo() { return creatorNo; }
    public void setCreatorNo(String creatorNo) { this.creatorNo = creatorNo; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public String getPickerNo() { return pickerNo; }
    public void setPickerNo(String pickerNo) { this.pickerNo = pickerNo; }
    public String getPickerName() { return pickerName; }
    public void setPickerName(String pickerName) { this.pickerName = pickerName; }
    public String getReviewerNo() { return reviewerNo; }
    public void setReviewerNo(String reviewerNo) { this.reviewerNo = reviewerNo; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getAssignTime() { return assignTime; }
    public void setAssignTime(String assignTime) { this.assignTime = assignTime; }
    public String getPickStartTime() { return pickStartTime; }
    public void setPickStartTime(String pickStartTime) { this.pickStartTime = pickStartTime; }
    public String getPickFinishTime() { return pickFinishTime; }
    public void setPickFinishTime(String pickFinishTime) { this.pickFinishTime = pickFinishTime; }
    public String getReviewFinishTime() { return reviewFinishTime; }
    public void setReviewFinishTime(String reviewFinishTime) { this.reviewFinishTime = reviewFinishTime; }
    public String getHandoverTime() { return handoverTime; }
    public void setHandoverTime(String handoverTime) { this.handoverTime = handoverTime; }
    public Integer getPickDurationSec() { return pickDurationSec; }
    public void setPickDurationSec(Integer pickDurationSec) { this.pickDurationSec = pickDurationSec; }
    public Integer getTotalDurationSec() { return totalDurationSec; }
    public void setTotalDurationSec(Integer totalDurationSec) { this.totalDurationSec = totalDurationSec; }
    public Integer getHasShortage() { return hasShortage; }
    public void setHasShortage(Integer hasShortage) { this.hasShortage = hasShortage; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
