package com.flood.statemigrate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 站端遥测：水位、进出流量、闸门状态、泵组启停。
 * assetId 按类别取值：水位为 UPSTREAM/DOWNSTREAM 测点，流量为流量计编码，
 * 闸门为闸门编码，泵状态为泵组编码。
 */
@Entity
@Table(name = "station_telemetry", indexes = {
        @Index(name = "idx_telemetry_station_time", columnList = "stationCode,eventTime")
})
public class StationTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String stationCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TelemetryKind kind;

    @Column(nullable = false, length = 64)
    private String assetId;

    @Column(nullable = false)
    private Instant eventTime;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public TelemetryKind getKind() { return kind; }
    public void setKind(TelemetryKind kind) { this.kind = kind; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
}
