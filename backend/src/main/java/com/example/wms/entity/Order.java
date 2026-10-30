package com.example.wms.entity;

/**
 * 订单。status: 待分波 / 已分波 / 拣货中 / 已拣货 / 已复核 / 已出库 / 已取消
 */
public class Order {
    private Long id;
    private String orderNo;
    private String platform;       // 天猫 / 京东 / 抖音 / 拼多多
    private String customerName;
    private String customerPhone;
    private String address;
    private Integer itemCount;
    private Integer totalQty;
    private String status;
    private Long waveId;
    private String waveNo;
    private String priority;       // 普通 / 加急
    private String createTime;
    private String waveTime;
    private String finishTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getTotalQty() { return totalQty; }
    public void setTotalQty(Integer totalQty) { this.totalQty = totalQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getWaveId() { return waveId; }
    public void setWaveId(Long waveId) { this.waveId = waveId; }
    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getWaveTime() { return waveTime; }
    public void setWaveTime(String waveTime) { this.waveTime = waveTime; }
    public String getFinishTime() { return finishTime; }
    public void setFinishTime(String finishTime) { this.finishTime = finishTime; }
}
