package com.example.wms.dto;

import java.util.ArrayList;
import java.util.List;

/** 拣货提交请求：按 SKU 提交实拣数量与缺货数量 */
public class PickSubmitRequest {
    private Long waveId;
    private List<ItemLine> items = new ArrayList<>();

    public static class ItemLine {
        private String skuCode;
        private Integer pickedQty;
        private Integer shortageQty;
        private String reason;

        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public Integer getPickedQty() { return pickedQty; }
        public void setPickedQty(Integer pickedQty) { this.pickedQty = pickedQty; }
        public Integer getShortageQty() { return shortageQty; }
        public void setShortageQty(Integer shortageQty) { this.shortageQty = shortageQty; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public Long getWaveId() { return waveId; }
    public void setWaveId(Long waveId) { this.waveId = waveId; }
    public List<ItemLine> getItems() { return items; }
    public void setItems(List<ItemLine> items) { this.items = items; }
}
