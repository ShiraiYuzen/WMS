package com.example.wms.dto;

import java.util.ArrayList;
import java.util.List;

/** 新建订单请求：订单头 + 明细行 */
public class OrderCreateRequest {
    private String platform;
    private String customerName;
    private String customerPhone;
    private String address;
    private String priority;
    private List<ItemLine> items = new ArrayList<>();

    public static class ItemLine {
        private String skuCode;
        private Integer qty;

        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }
    }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public List<ItemLine> getItems() { return items; }
    public void setItems(List<ItemLine> items) { this.items = items; }
}
