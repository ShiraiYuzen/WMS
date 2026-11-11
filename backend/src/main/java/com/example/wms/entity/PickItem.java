package com.example.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("pick_item")
public class PickItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String waveNo;
    private Integer seqNo;
    private String locationCode;
    private String skuCode;
    private String skuName;
    private String spec;
    private String unit;
    private Integer shouldQty;
    private Integer actualQty;
    private Integer shortageQty;
    private String status;
    private String relatedOrderNos;
    private String scanTime;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Integer getShouldQty() { return shouldQty; }
    public void setShouldQty(Integer shouldQty) { this.shouldQty = shouldQty; }
    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
    public Integer getShortageQty() { return shortageQty; }
    public void setShortageQty(Integer shortageQty) { this.shortageQty = shortageQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRelatedOrderNos() { return relatedOrderNos; }
    public void setRelatedOrderNos(String relatedOrderNos) { this.relatedOrderNos = relatedOrderNos; }
    public String getScanTime() { return scanTime; }
    public void setScanTime(String scanTime) { this.scanTime = scanTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
