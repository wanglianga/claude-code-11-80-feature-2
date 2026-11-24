package com.flood.statemigrate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 泵站登记：站前调蓄池水面面积（控制体蓄水换算）、量测容许误差与校准误差安全阈值。
 * pondAreaM2 用于把站前水位变化换算为蓄量变化（路线 B 控制体守恒）；
 * safetyErrorPct 为校准误差安全阈值，跨阈窗口进入待核（PENDING_REVIEW），不进入运行效率报表。
 */
@Entity
@Table(name = "pump_station")
public class PumpStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String stationCode;

    @Column(nullable = false, length = 128)
    private String name;

    /** 所属流域/汇水分区编码，流域汇总按此聚合 */
    @Column(nullable = false, length = 64)
    private String basinCode;

    /** 站前调蓄池水面面积（平方米） */
    @Column(nullable = false)
    private double pondAreaM2;

    /** 水位计容许误差（米），用于蓄量不确定区间 */
    @Column(nullable = false)
    private double levelTolM;

    /** 流量计容许误差（比例，如 0.05 = ±5%） */
    @Column(nullable = false)
    private double meterErrorPct;

    /** 校准误差安全阈值（比例，如 0.15 = 15%），跨阈窗口进入待核 */
    @Column(nullable = false)
    private double safetyErrorPct;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBasinCode() { return basinCode; }
    public void setBasinCode(String basinCode) { this.basinCode = basinCode; }
    public double getPondAreaM2() { return pondAreaM2; }
    public void setPondAreaM2(double pondAreaM2) { this.pondAreaM2 = pondAreaM2; }
    public double getLevelTolM() { return levelTolM; }
    public void setLevelTolM(double levelTolM) { this.levelTolM = levelTolM; }
    public double getMeterErrorPct() { return meterErrorPct; }
    public void setMeterErrorPct(double meterErrorPct) { this.meterErrorPct = meterErrorPct; }
    public double getSafetyErrorPct() { return safetyErrorPct; }
    public void setSafetyErrorPct(double safetyErrorPct) { this.safetyErrorPct = safetyErrorPct; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
