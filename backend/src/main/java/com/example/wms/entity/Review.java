package com.example.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String reviewNo;
    private String waveNo;
    private String orderNo;
    private String reviewerNo;
    private String reviewerName;
    private String status;
    private Integer shouldSkuCount;
    private Integer shouldQty;
    private Integer actualQty;
    private String diffType;
    private Integer diffQty;
    private String diffDetail;
    private String packMaterial;
    private Integer packageCount;
    private BigDecimal weight;
    private String startTime;
    private String finishTime;
    private Integer durationSec;
    private String remark;
    private String createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReviewNo() { return reviewNo; }
    public void setReviewNo(String reviewNo) { this.reviewNo = reviewNo; }
    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getReviewerNo() { return reviewerNo; }
    public void setReviewerNo(String reviewerNo) { this.reviewerNo = reviewerNo; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getShouldSkuCount() { return shouldSkuCount; }
    public void setShouldSkuCount(Integer shouldSkuCount) { this.shouldSkuCount = shouldSkuCount; }
    public Integer getShouldQty() { return shouldQty; }
    public void setShouldQty(Integer shouldQty) { this.shouldQty = shouldQty; }
    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
    public String getDiffType() { return diffType; }
    public void setDiffType(String diffType) { this.diffType = diffType; }
    public Integer getDiffQty() { return diffQty; }
    public void setDiffQty(Integer diffQty) { this.diffQty = diffQty; }
    public String getDiffDetail() { return diffDetail; }
    public void setDiffDetail(String diffDetail) { this.diffDetail = diffDetail; }
    public String getPackMaterial() { return packMaterial; }
    public void setPackMaterial(String packMaterial) { this.packMaterial = packMaterial; }
    public Integer getPackageCount() { return packageCount; }
    public void setPackageCount(Integer packageCount) { this.packageCount = packageCount; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getFinishTime() { return finishTime; }
    public void setFinishTime(String finishTime) { this.finishTime = finishTime; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
