package com.example.wms.entity;

public class OrderItem {
    private Long id;
    private Long orderId;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String location;
    private Integer qty;
    private Integer pickedQty;
    private Integer shortageQty;
    private String status;   // 待拣 / 已拣 / 缺货

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public Integer getPickedQty() { return pickedQty; }
    public void setPickedQty(Integer pickedQty) { this.pickedQty = pickedQty; }
    public Integer getShortageQty() { return shortageQty; }
    public void setShortageQty(Integer shortageQty) { this.shortageQty = shortageQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
