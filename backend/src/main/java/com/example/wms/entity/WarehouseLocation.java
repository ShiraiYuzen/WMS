package com.example.wms.entity;

/** 库位（第三版新增，融合自 A 版的库位管理） */
public class WarehouseLocation {
    private Long id;
    private String locationCode;
    private String zone;      // 库区 A/B/C/D
    private String type;      // 拣货位 / 存储位 / 暂存位
    private String status;    // 启用 / 停用
    private Integer capacity;
    private String remark;
    private String createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
