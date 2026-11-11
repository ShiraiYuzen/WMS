package com.example.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("review_diff")
public class ReviewDiff {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String reviewNo;
    private String waveNo;
    private String orderNo;
    private String skuCode;
    private String skuName;
    private String diffType;
    private Integer shouldQty;
    private Integer actualQty;
    private Integer diffQty;
    private String responsibleStage;
    private String responsibleNo;
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
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }
    public String getDiffType() { return diffType; }
    public void setDiffType(String diffType) { this.diffType = diffType; }
    public Integer getShouldQty() { return shouldQty; }
    public void setShouldQty(Integer shouldQty) { this.shouldQty = shouldQty; }
    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
    public Integer getDiffQty() { return diffQty; }
    public void setDiffQty(Integer diffQty) { this.diffQty = diffQty; }
    public String getResponsibleStage() { return responsibleStage; }
    public void setResponsibleStage(String responsibleStage) { this.responsibleStage = responsibleStage; }
    public String getResponsibleNo() { return responsibleNo; }
    public void setResponsibleNo(String responsibleNo) { this.responsibleNo = responsibleNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
