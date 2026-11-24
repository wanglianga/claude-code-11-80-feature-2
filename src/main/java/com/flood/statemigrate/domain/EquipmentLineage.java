package com.flood.statemigrate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 设备沿革：把控制器、泵组和流量计绑定到有效区间。
 * 同一出水道（outletId）可被多台泵组共享；pumpGroupId 为空表示站级进出流量计
 * （不隶属于某一泵组，如站前进水流量计）。核算按分段时刻查询当时有效的绑定；
 * 绑定失效后仍上报的遥测不参与核算并记入设备质量记录。
 */
@Entity
@Table(name = "equipment_lineage")
public class EquipmentLineage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String stationCode;

    @Column(nullable = false, length = 64)
    private String controllerId;

    /** 泵组编码；为空表示站级计量设备绑定 */
    @Column(length = 64)
    private String pumpGroupId;

    @Column(nullable = false, length = 64)
    private String flowMeterId;

    /** 出水道编码（多台泵组可共用同一出水道） */
    @Column(nullable = false, length = 64)
    private String outletId;

    @Column(nullable = false)
    private Instant effectiveFrom;

    /** 有效区间终点；为空表示至今有效 */
    private Instant effectiveTo;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getControllerId() { return controllerId; }
    public void setControllerId(String controllerId) { this.controllerId = controllerId; }
    public String getPumpGroupId() { return pumpGroupId; }
    public void setPumpGroupId(String pumpGroupId) { this.pumpGroupId = pumpGroupId; }
    public String getFlowMeterId() { return flowMeterId; }
    public void setFlowMeterId(String flowMeterId) { this.flowMeterId = flowMeterId; }
    public String getOutletId() { return outletId; }
    public void setOutletId(String outletId) { this.outletId = outletId; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(Instant effectiveTo) { this.effectiveTo = effectiveTo; }
    public Instant getCreatedAt() { return createdAt; }

    /** 绑定在指定时刻是否有效（含端点） */
    public boolean effectiveAt(Instant t) {
        return !t.isBefore(effectiveFrom) && (effectiveTo == null || !t.isAfter(effectiveTo));
    }
}
