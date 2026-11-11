package com.example.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("shortage")
public class Shortage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String shortageNo;
    private String waveNo;
    private String taskNo;
    private String orderNo;
    private String locationCode;
    private String skuCode;
    private String skuName;
    private Integer shouldQty;
    private Integer shortageQty;
    private String reason;
    private String reporterNo;
    private String reporterName;
    private String reportTime;
    private String status;
    private String handleType;
    private String handlerNo;
    private String handlerName;
    private String handleTime;
    private String handleRemark;
    private String substituteSkuCode;
    private String createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShortageNo() { return shortageNo; }
    public void setShortageNo(String shortageNo) { this.shortageNo = shortageNo; }
    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }
    public Integer getShouldQty() { return shouldQty; }
    public void setShouldQty(Integer shouldQty) { this.shouldQty = shouldQty; }
    public Integer getShortageQty() { return shortageQty; }
    public void setShortageQty(Integer shortageQty) { this.shortageQty = shortageQty; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getReporterNo() { return reporterNo; }
    public void setReporterNo(String reporterNo) { this.reporterNo = reporterNo; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    public String getReportTime() { return reportTime; }
    public void setReportTime(String reportTime) { this.reportTime = reportTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHandleType() { return handleType; }
    public void setHandleType(String handleType) { this.handleType = handleType; }
    public String getHandlerNo() { return handlerNo; }
    public void setHandlerNo(String handlerNo) { this.handlerNo = handlerNo; }
    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String handlerName) { this.handlerName = handlerName; }
    public String getHandleTime() { return handleTime; }
    public void setHandleTime(String handleTime) { this.handleTime = handleTime; }
    public String getHandleRemark() { return handleRemark; }
    public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }
    public String getSubstituteSkuCode() { return substituteSkuCode; }
    public void setSubstituteSkuCode(String substituteSkuCode) { this.substituteSkuCode = substituteSkuCode; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
