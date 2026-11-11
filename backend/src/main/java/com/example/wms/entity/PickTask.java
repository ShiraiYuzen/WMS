package com.example.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("pick_task")
public class PickTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String waveNo;
    private String pickerNo;
    private String pickerName;
    private String status;
    private Integer itemCount;
    private Integer finishedCount;
    private Integer locationCount;
    private Integer shouldQty;
    private Integer actualQty;
    private Integer shortageQty;
    private Integer orderCount;
    private String startTime;
    private String finishTime;
    private Integer durationSec;
    private String pathText;
    private String remark;
    private String createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }
    public String getPickerNo() { return pickerNo; }
    public void setPickerNo(String pickerNo) { this.pickerNo = pickerNo; }
    public String getPickerName() { return pickerName; }
    public void setPickerName(String pickerName) { this.pickerName = pickerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getFinishedCount() { return finishedCount; }
    public void setFinishedCount(Integer finishedCount) { this.finishedCount = finishedCount; }
    public Integer getLocationCount() { return locationCount; }
    public void setLocationCount(Integer locationCount) { this.locationCount = locationCount; }
    public Integer getShouldQty() { return shouldQty; }
    public void setShouldQty(Integer shouldQty) { this.shouldQty = shouldQty; }
    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
    public Integer getShortageQty() { return shortageQty; }
    public void setShortageQty(Integer shortageQty) { this.shortageQty = shortageQty; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getFinishTime() { return finishTime; }
    public void setFinishTime(String finishTime) { this.finishTime = finishTime; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public String getPathText() { return pathText; }
    public void setPathText(String pathText) { this.pathText = pathText; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
