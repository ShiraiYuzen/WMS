package com.example.wms.entity;

/**
 * 复核记录。result: 通过 / 异常
 */
public class ReviewRecord {
    private Long id;
    private Long waveId;
    private String waveNo;
    private Long orderId;
    private String orderNo;
    private Long checkerId;
    private String checkerName;
    private String result;
    private Integer packCount;
    private Integer errorCount;
    private String remark;
    private String createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWaveId() { return waveId; }
    public void setWaveId(Long waveId) { this.waveId = waveId; }
    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getCheckerId() { return checkerId; }
    public void setCheckerId(Long checkerId) { this.checkerId = checkerId; }
    public String getCheckerName() { return checkerName; }
    public void setCheckerName(String checkerName) { this.checkerName = checkerName; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Integer getPackCount() { return packCount; }
    public void setPackCount(Integer packCount) { this.packCount = packCount; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
