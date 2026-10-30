package com.example.wms.dto;

/** 出库交接请求 */
public class OutboundSubmitRequest {
    private Long waveId;
    private String carrier;
    private String handoverTo;
    private String remark;

    public Long getWaveId() { return waveId; }
    public void setWaveId(Long waveId) { this.waveId = waveId; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getHandoverTo() { return handoverTo; }
    public void setHandoverTo(String handoverTo) { this.handoverTo = handoverTo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
