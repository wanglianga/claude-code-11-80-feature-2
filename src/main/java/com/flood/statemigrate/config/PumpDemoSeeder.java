package com.flood.statemigrate.config;

import com.flood.statemigrate.domain.TelemetryKind;
import com.flood.statemigrate.repo.PumpStationRepo;
import com.flood.statemigrate.service.DrainageAccountingService;
import com.flood.statemigrate.service.PumpStationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 泵站排水贡献核算演示数据（流域 BASIN-01 三座泵站，窗口起点为 8 小时前）：
 *  PS-01 城区东站：两台共用出水道 OUT-1 的泵（PG-1/PG-2）、一台反转流量计 FM-OUT、
 *        一次闸门关闭运行（T0+2h~T0+3h 泵仍在转）、泵站前后水位变化；
 *        PG-2 校准曲线只覆盖到 T0+3h → 缺校准段，窗口无法闭合（UNCLOSED）。
 *  PS-02 临江站：校准误差 20% 跨过安全阈值 15% → 窗口进入待核（PENDING_REVIEW），
 *        不进入运行效率报表。
 *  PS-03 高新区站：校准完整、两路线闭合（CLOSED），附带单泵分解。
 */
@Component
public class PumpDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PumpDemoSeeder.class);

    private final PumpStationRepo stationRepo;
    private final PumpStationService stationService;
    private final DrainageAccountingService accountingService;
    private final boolean seedEnabled;

    public PumpDemoSeeder(PumpStationRepo stationRepo, PumpStationService stationService,
                          DrainageAccountingService accountingService,
                          @Value("${app.seed-demo-data:true}") boolean seedEnabled) {
        this.stationRepo = stationRepo;
        this.stationService = stationService;
        this.accountingService = accountingService;
        this.seedEnabled = seedEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled || stationRepo.count() > 0) {
            return;
        }
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(8, ChronoUnit.HOURS);
        seedPs01(t0);
        seedPs02(t0);
        seedPs03(t0);
        log.info("泵站排水核算演示数据就绪: PS-01/PS-02/PS-03 (BASIN-01)");
    }

    /**
     * PS-01：两泵共用出水道、反转流量计、闸门关闭运行、前后水位变化。
     * 窗口 [T0, T0+6h]；进水 0.5 m³/s 恒定；调蓄池 5000 m²。
     * PG-1 曲线 1.2 m³/s（全程有效，误差 5%）；PG-2 曲线 0.9 m³/s（误差 8%）只校准到 T0+3h。
     * 预期：路线 B = 19440 m³；路线 A 覆盖 16200 m³ 但缺校准段无法给单点量；
     *       闸门关闭 [T0+2h,T0+3h] 不计贡献；未闭合区间 [T0+3h,T0+4h]（CALIBRATION_GAP）。
     */
    private void seedPs01(Instant t0) {
        String code = "PS-01";
        stationService.register(code, "城区东站", "BASIN-01", 5000, 0.02, 0.05, 0.15);
        Instant dayAgo = t0.minus(1, ChronoUnit.DAYS);
        // 设备沿革：两台泵组共用出水道 OUT-1 与出水道流量计 FM-OUT；FM-IN 为站级进水流量计
        stationService.addLineage(code, "CTL-1", "PG-1", "FM-OUT", "OUT-1", dayAgo, null);
        stationService.addLineage(code, "CTL-1", "PG-2", "FM-OUT", "OUT-1", dayAgo, null);
        stationService.addLineage(code, "CTL-1", null, "FM-IN", "INLET-1", dayAgo, null);
        // 校准曲线：PG-2 只校准到 T0+3h，之后为缺校准段
        stationService.addCurve(code, "PG-1",
                "[{\"headM\":0,\"flowM3s\":1.2},{\"headM\":5,\"flowM3s\":1.2}]", 0.05, dayAgo, null);
        stationService.addCurve(code, "PG-2",
                "[{\"headM\":0,\"flowM3s\":0.9},{\"headM\":5,\"flowM3s\":0.9}]", 0.08,
                dayAgo, t0.plus(3, ChronoUnit.HOURS));

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0, "{\"levelM\":3.0}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", t0, "{\"levelM\":3.8}");
        tel(code, TelemetryKind.FLOW_IN, "FM-IN", t0, "{\"flowM3s\":0.5}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", t0, "{\"flowM3s\":0.0}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-1", t0, "{\"state\":\"OPEN\"}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-1", t0, "{\"running\":false}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-2", t0, "{\"running\":false}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(30, ChronoUnit.MINUTES), "{\"levelM\":3.18}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-1", t0.plus(30, ChronoUnit.MINUTES), "{\"running\":true}");
        // 反转流量计：实际排出 1.2 m³/s，读数为负
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", t0.plus(30, ChronoUnit.MINUTES), "{\"flowM3s\":-1.2}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(60, ChronoUnit.MINUTES), "{\"levelM\":2.928}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", t0.plus(60, ChronoUnit.MINUTES), "{\"levelM\":3.85}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-2", t0.plus(60, ChronoUnit.MINUTES), "{\"running\":true}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", t0.plus(60, ChronoUnit.MINUTES), "{\"flowM3s\":-2.1}");

        // 闸门关闭运行：T0+2h 关闸，泵继续运行 1 小时（不计排水贡献）
        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(120, ChronoUnit.MINUTES), "{\"levelM\":1.776}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", t0.plus(120, ChronoUnit.MINUTES), "{\"levelM\":3.9}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-1", t0.plus(120, ChronoUnit.MINUTES), "{\"state\":\"CLOSED\"}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", t0.plus(120, ChronoUnit.MINUTES), "{\"flowM3s\":0.0}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(180, ChronoUnit.MINUTES), "{\"levelM\":2.136}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-1", t0.plus(180, ChronoUnit.MINUTES), "{\"state\":\"OPEN\"}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", t0.plus(180, ChronoUnit.MINUTES), "{\"flowM3s\":-2.1}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(240, ChronoUnit.MINUTES), "{\"levelM\":0.984}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", t0.plus(240, ChronoUnit.MINUTES), "{\"levelM\":3.95}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-2", t0.plus(240, ChronoUnit.MINUTES), "{\"running\":false}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", t0.plus(240, ChronoUnit.MINUTES), "{\"flowM3s\":-1.2}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(270, ChronoUnit.MINUTES), "{\"levelM\":0.732}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-1", t0.plus(270, ChronoUnit.MINUTES), "{\"running\":false}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", t0.plus(270, ChronoUnit.MINUTES), "{\"flowM3s\":0.0}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(360, ChronoUnit.MINUTES), "{\"levelM\":1.272}");

        accountingService.run(code, t0, t0.plus(6, ChronoUnit.HOURS));
    }

    /**
     * PS-02：校准误差 20% 跨过安全阈值 15% → 两路线闭合但窗口进入待核（不进入运行效率报表）。
     * 窗口 [T0, T0+2h]；进水 0.3 m³/s；调蓄池 2000 m²；PG-9 曲线 0.6 m³/s。
     * 预期：路线 A = 路线 B = 2160 m³，统一估计生效，状态 PENDING_REVIEW。
     */
    private void seedPs02(Instant t0) {
        String code = "PS-02";
        stationService.register(code, "临江站", "BASIN-01", 2000, 0.02, 0.05, 0.15);
        Instant dayAgo = t0.minus(1, ChronoUnit.DAYS);
        stationService.addLineage(code, "CTL-2", "PG-9", "FM-OUT2", "OUT-2", dayAgo, null);
        stationService.addLineage(code, "CTL-2", null, "FM-IN2", "INLET-2", dayAgo, null);
        stationService.addCurve(code, "PG-9",
                "[{\"headM\":0,\"flowM3s\":0.6},{\"headM\":5,\"flowM3s\":0.6}]", 0.20, dayAgo, null);

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0, "{\"levelM\":2.2}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", t0, "{\"levelM\":3.4}");
        tel(code, TelemetryKind.FLOW_IN, "FM-IN2", t0, "{\"flowM3s\":0.3}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT2", t0, "{\"flowM3s\":0.0}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-2", t0, "{\"state\":\"OPEN\"}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-9", t0, "{\"running\":false}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(30, ChronoUnit.MINUTES), "{\"levelM\":2.47}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-9", t0.plus(30, ChronoUnit.MINUTES), "{\"running\":true}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT2", t0.plus(30, ChronoUnit.MINUTES), "{\"flowM3s\":0.6}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(90, ChronoUnit.MINUTES), "{\"levelM\":1.93}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-9", t0.plus(90, ChronoUnit.MINUTES), "{\"running\":false}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT2", t0.plus(90, ChronoUnit.MINUTES), "{\"flowM3s\":0.0}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(120, ChronoUnit.MINUTES), "{\"levelM\":2.2}");

        accountingService.run(code, t0, t0.plus(2, ChronoUnit.HOURS));
    }

    /**
     * PS-03：校准完整、两路线在容差内闭合（CLOSED），附带单泵分解。
     * 窗口 [T0, T0+2h]；进水 0.4 m³/s；调蓄池 3000 m²；
     * PG-7 为非常量校准曲线（扬程插值），误差 4%。
     * 预期：路线 B = 3240 m³，路线 A ≈ 3268.8 m³，统一估计取两路线不确定区间交集。
     */
    private void seedPs03(Instant t0) {
        String code = "PS-03";
        stationService.register(code, "高新区站", "BASIN-01", 3000, 0.02, 0.05, 0.15);
        Instant dayAgo = t0.minus(1, ChronoUnit.DAYS);
        stationService.addLineage(code, "CTL-3", "PG-7", "FM-OUT3", "OUT-3", dayAgo, null);
        stationService.addLineage(code, "CTL-3", null, "FM-IN3", "INLET-3", dayAgo, null);
        stationService.addCurve(code, "PG-7",
                "[{\"headM\":0.5,\"flowM3s\":1.0},{\"headM\":1.5,\"flowM3s\":0.8},"
                        + "{\"headM\":2.5,\"flowM3s\":0.6}]", 0.04, dayAgo, null);

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0, "{\"levelM\":2.6}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", t0, "{\"levelM\":3.5}");
        tel(code, TelemetryKind.FLOW_IN, "FM-IN3", t0, "{\"flowM3s\":0.4}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT3", t0, "{\"flowM3s\":0.0}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-3", t0, "{\"state\":\"OPEN\"}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-7", t0, "{\"running\":false}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(30, ChronoUnit.MINUTES), "{\"levelM\":2.84}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-7", t0.plus(30, ChronoUnit.MINUTES), "{\"running\":true}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT3", t0.plus(30, ChronoUnit.MINUTES), "{\"flowM3s\":0.9}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(90, ChronoUnit.MINUTES), "{\"levelM\":2.24}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-7", t0.plus(90, ChronoUnit.MINUTES), "{\"running\":false}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT3", t0.plus(90, ChronoUnit.MINUTES), "{\"flowM3s\":0.0}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(120, ChronoUnit.MINUTES), "{\"levelM\":2.48}");

        accountingService.run(code, t0, t0.plus(2, ChronoUnit.HOURS));
    }

    private void tel(String stationCode, TelemetryKind kind, String assetId, Instant time, String payload) {
        stationService.addTelemetry(stationCode, kind, assetId, time, payload);
    }
}
