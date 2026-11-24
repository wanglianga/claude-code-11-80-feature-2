package com.flood.statemigrate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 设备质量记录：对账器把无法闭合窗口的原因反馈到具体设备（流量计/泵组/控制器）。
 * 记录关联产生它的核算运行（runId）与问题区间 [windowStart, windowEnd]。
 */
@Entity
@Table(name = "equipment_quality_record", indexes = {
        @Index(name = "idx_quality_station", columnList = "stationCode,createdAt")
})
public class EquipmentQualityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String stationCode;

    /** 产生该记录的核算运行 */
    @Column(nullable = false)
    private Long runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QualityCategory category;

    /** 责任设备：流量计/泵组/闸门编码 */
    @Column(nullable = false, length = 64)
    private String assetId;

    /** 问题区间起点（窗口内的子区间） */
    @Column(nullable = false)
    private Instant windowStart;

    /** 问题区间终点 */
    @Column(nullable = false)
    private Instant windowEnd;

    @Column(nullable = false, length = 512)
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public QualityCategory getCategory() { return category; }
    public void setCategory(QualityCategory category) { this.category = category; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getCreatedAt() { return createdAt; }
}
