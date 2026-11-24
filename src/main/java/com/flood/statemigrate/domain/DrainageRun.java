package com.flood.statemigrate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 排水贡献核算运行：同一泵站、同一时间窗的站级总有效净排量核算结果。
 * 两条路线统一估计同一目标：
 *   路线 A（PUMP_CURVE）    按泵组校准曲线积分流量，局部归因清楚；缺校准段无法给单点量，
 *                           此时 routeANetM3 为空、routeACoveredM3 记录已覆盖部分的参考量；
 *   路线 B（CONTROL_VOLUME）按泵站控制体水位/流量守恒反推站级总净排量，覆盖更完整，
 *                           但无法将共用出水道的量精确分给单泵。
 * 仅当泵组校准曲线完整（calibrationComplete）时 perPumpJson 附带单泵分解；
 * 分解口径不是独立目标，统一估计以站级总量为准。
 */
@Entity
@Table(name = "drainage_run")
public class DrainageRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String stationCode;

    @Column(nullable = false)
    private Instant windowStart;

    @Column(nullable = false)
    private Instant windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AccountingStatus status;

    /** 物理方向校验：净流向与排水方向一致（允许不确定区间内的微小反向） */
    @Column(nullable = false)
    private boolean directionValid;

    /** 路线 A 站级总净排量（立方米）；缺校准段时为空 */
    private Double routeANetM3;
    private Double routeALowM3;
    private Double routeAHighM3;

    /** 路线 A 已校准覆盖部分的参考量（缺校准段时仍记录，供对账定位） */
    private Double routeACoveredM3;

    /** 路线 B 站级总净排量（立方米）及不确定区间 */
    private Double routeBNetM3;
    private Double routeBLowM3;
    private Double routeBHighM3;

    /** 统一估计：站级总有效净排量及不确定区间；窗口未闭合时为空 */
    private Double unifiedNetM3;
    private Double unifiedLowM3;
    private Double unifiedHighM3;

    /** 泵组校准曲线是否覆盖窗口内全部运行分段 */
    @Column(nullable = false)
    private boolean calibrationComplete;

    /** 单泵分解：[{"pumpGroupId":"PG-1","netM3":..,"lowM3":..,"highM3":..}]；仅校准完整时非空 */
    @Column(columnDefinition = "TEXT")
    private String perPumpJson;

    /** 逐段明细：分段状态、两路线分段净量与不确定区间 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String segmentsJson;

    /** 未闭合区间：[{"from":..,"to":..,"reason":"CALIBRATION_GAP","detail":..}]；闭合时为空数组 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String unclosedJson;

    /** 运行时长统计：{"PG-1":{"runtimeSec":..,"gateClosedRuntimeSec":..,"effectiveSec":..}} */
    @Column(columnDefinition = "TEXT")
    private String runtimeJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    public AccountingStatus getStatus() { return status; }
    public void setStatus(AccountingStatus status) { this.status = status; }
    public boolean isDirectionValid() { return directionValid; }
    public void setDirectionValid(boolean directionValid) { this.directionValid = directionValid; }
    public Double getRouteANetM3() { return routeANetM3; }
    public void setRouteANetM3(Double routeANetM3) { this.routeANetM3 = routeANetM3; }
    public Double getRouteALowM3() { return routeALowM3; }
    public void setRouteALowM3(Double routeALowM3) { this.routeALowM3 = routeALowM3; }
    public Double getRouteAHighM3() { return routeAHighM3; }
    public void setRouteAHighM3(Double routeAHighM3) { this.routeAHighM3 = routeAHighM3; }
    public Double getRouteACoveredM3() { return routeACoveredM3; }
    public void setRouteACoveredM3(Double routeACoveredM3) { this.routeACoveredM3 = routeACoveredM3; }
    public Double getRouteBNetM3() { return routeBNetM3; }
    public void setRouteBNetM3(Double routeBNetM3) { this.routeBNetM3 = routeBNetM3; }
    public Double getRouteBLowM3() { return routeBLowM3; }
    public void setRouteBLowM3(Double routeBLowM3) { this.routeBLowM3 = routeBLowM3; }
    public Double getRouteBHighM3() { return routeBHighM3; }
    public void setRouteBHighM3(Double routeBHighM3) { this.routeBHighM3 = routeBHighM3; }
    public Double getUnifiedNetM3() { return unifiedNetM3; }
    public void setUnifiedNetM3(Double unifiedNetM3) { this.unifiedNetM3 = unifiedNetM3; }
    public Double getUnifiedLowM3() { return unifiedLowM3; }
    public void setUnifiedLowM3(Double unifiedLowM3) { this.unifiedLowM3 = unifiedLowM3; }
    public Double getUnifiedHighM3() { return unifiedHighM3; }
    public void setUnifiedHighM3(Double unifiedHighM3) { this.unifiedHighM3 = unifiedHighM3; }
    public boolean isCalibrationComplete() { return calibrationComplete; }
    public void setCalibrationComplete(boolean calibrationComplete) { this.calibrationComplete = calibrationComplete; }
    public String getPerPumpJson() { return perPumpJson; }
    public void setPerPumpJson(String perPumpJson) { this.perPumpJson = perPumpJson; }
    public String getSegmentsJson() { return segmentsJson; }
    public void setSegmentsJson(String segmentsJson) { this.segmentsJson = segmentsJson; }
    public String getUnclosedJson() { return unclosedJson; }
    public void setUnclosedJson(String unclosedJson) { this.unclosedJson = unclosedJson; }
    public String getRuntimeJson() { return runtimeJson; }
    public void setRuntimeJson(String runtimeJson) { this.runtimeJson = runtimeJson; }
    public Instant getCreatedAt() { return createdAt; }
}
