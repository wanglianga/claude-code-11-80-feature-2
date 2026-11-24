package com.flood.statemigrate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 泵组校准曲线（Q-H 曲线）：扬程（下游水位 − 上游水位，米）到流量（立方米/秒）的分段线性插值。
 * pointsJson 示例：[{"headM":0.5,"flowM3s":1.2},{"headM":3.5,"flowM3s":0.6}]
 * validFrom/validTo 为校准有效区间；区间外或扬程超出曲线覆盖范围的分段为缺校准段，
 * 路线 A 无法给出单点量。errorPct 为校准误差（比例），超过泵站安全阈值的窗口进入待核。
 */
@Entity
@Table(name = "calibration_curve")
public class CalibrationCurve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String stationCode;

    @Column(nullable = false, length = 64)
    private String pumpGroupId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pointsJson;

    /** 校准误差（比例，如 0.08 = ±8%） */
    @Column(nullable = false)
    private double errorPct;

    @Column(nullable = false)
    private Instant validFrom;

    /** 校准有效区间终点；为空表示至今有效 */
    private Instant validTo;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getPumpGroupId() { return pumpGroupId; }
    public void setPumpGroupId(String pumpGroupId) { this.pumpGroupId = pumpGroupId; }
    public String getPointsJson() { return pointsJson; }
    public void setPointsJson(String pointsJson) { this.pointsJson = pointsJson; }
    public double getErrorPct() { return errorPct; }
    public void setErrorPct(double errorPct) { this.errorPct = errorPct; }
    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }
    public Instant getValidTo() { return validTo; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }
    public Instant getCreatedAt() { return createdAt; }

    /** 曲线在指定时刻是否处于校准有效区间（含端点） */
    public boolean validAt(Instant t) {
        return !t.isBefore(validFrom) && (validTo == null || !t.isAfter(validTo));
    }
}
