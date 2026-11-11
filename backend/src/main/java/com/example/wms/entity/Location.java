package com.example.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("location")
public class Location {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String locationCode;
    private String zone;
    private String zoneName;
    private String aisle;
    private String rack;
    private String level;
    private String locationType;
    private String status;
    private String preferredCarrierCode;
    private Integer pathSeq;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public String getAisle() { return aisle; }
    public void setAisle(String aisle) { this.aisle = aisle; }
    public String getRack() { return rack; }
    public void setRack(String rack) { this.rack = rack; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPreferredCarrierCode() { return preferredCarrierCode; }
    public void setPreferredCarrierCode(String preferredCarrierCode) { this.preferredCarrierCode = preferredCarrierCode; }
    public Integer getPathSeq() { return pathSeq; }
    public void setPathSeq(Integer pathSeq) { this.pathSeq = pathSeq; }
}
