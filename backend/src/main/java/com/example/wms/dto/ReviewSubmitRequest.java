package com.example.wms.dto;

import java.util.ArrayList;
import java.util.List;

/** 复核提交请求：逐单复核结果 + 差错明细 */
public class ReviewSubmitRequest {
    private Long waveId;
    private List<OrderReview> reviews = new ArrayList<>();

    public static class OrderReview {
        private Long orderId;
        private String result;      // 通过 / 异常
        private Integer packCount;
        private String remark;
        private List<ErrorLine> errors = new ArrayList<>();

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public Integer getPackCount() { return packCount; }
        public void setPackCount(Integer packCount) { this.packCount = packCount; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public List<ErrorLine> getErrors() { return errors; }
        public void setErrors(List<ErrorLine> errors) { this.errors = errors; }
    }

    public static class ErrorLine {
        private String skuCode;
        private String errorType;   // 错拣 / 漏拣 / 多拣 / 破损 / 其他
        private Integer qty;
        private String description;

        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public String getErrorType() { return errorType; }
        public void setErrorType(String errorType) { this.errorType = errorType; }
        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public Long getWaveId() { return waveId; }
    public void setWaveId(Long waveId) { this.waveId = waveId; }
    public List<OrderReview> getReviews() { return reviews; }
    public void setReviews(List<OrderReview> reviews) { this.reviews = reviews; }
}
