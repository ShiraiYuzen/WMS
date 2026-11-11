package com.example.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("handover")
public class Handover {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String handoverNo;
    private String waveNos;
    private String carrierCode;
    private String carrierName;
    private Integer orderCount;
    private Integer packageCount;
    private BigDecimal totalWeight;
    private String status;
    private String handoverUserNo;
    private String handoverUserName;
    private String receiveName;
    private String vehicleNo;
    private String createTime;
    private String handoverTime;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHandoverNo() { return handoverNo; }
    public void setHandoverNo(String handoverNo) { this.handoverNo = handoverNo; }
    public String getWaveNos() { return waveNos; }
    public void setWaveNos(String waveNos) { this.waveNos = waveNos; }
    public String getCarrierCode() { return carrierCode; }
    public void setCarrierCode(String carrierCode) { this.carrierCode = carrierCode; }
    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public Integer getPackageCount() { return packageCount; }
    public void setPackageCount(Integer packageCount) { this.packageCount = packageCount; }
    public BigDecimal getTotalWeight() { return totalWeight; }
    public void setTotalWeight(BigDecimal totalWeight) { this.totalWeight = totalWeight; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHandoverUserNo() { return handoverUserNo; }
    public void setHandoverUserNo(String handoverUserNo) { this.handoverUserNo = handoverUserNo; }
    public String getHandoverUserName() { return handoverUserName; }
    public void setHandoverUserName(String handoverUserName) { this.handoverUserName = handoverUserName; }
    public String getReceiveName() { return receiveName; }
    public void setReceiveName(String receiveName) { this.receiveName = receiveName; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getHandoverTime() { return handoverTime; }
    public void setHandoverTime(String handoverTime) { this.handoverTime = handoverTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
