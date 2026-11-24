package com.flood.statemigrate;

import com.flood.statemigrate.domain.TelemetryKind;
import com.flood.statemigrate.service.PumpStationService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 泵站排水核算测试场景：与演示数据同构，但使用固定时刻便于精确断言。
 * PS-01 两泵共用出水道 + 反转流量计 + 闸门关闭运行 + 缺校准段；
 * PS-02 校准误差跨阈；PS-03 校准完整闭合；PS-04 净流向反向。
 */
class PumpScenarioBuilder {

    static final Instant T0 = Instant.parse("2026-11-24T00:00:00Z");

    private final PumpStationService stationService;

    PumpScenarioBuilder(PumpStationService stationService) {
        this.stationService = stationService;
    }

    /** PS-01：路线 B = 19440；路线 A 覆盖 16200 但缺校准段无法给单点量 → UNCLOSED */
    void ps01(String code, String basin) {
        stationService.register(code, "城区东站", basin, 5000, 0.02, 0.05, 0.15);
        Instant dayAgo = T0.minus(1, ChronoUnit.DAYS);
        stationService.addLineage(code, "CTL-1", "PG-1", "FM-OUT", "OUT-1", dayAgo, null);
        stationService.addLineage(code, "CTL-1", "PG-2", "FM-OUT", "OUT-1", dayAgo, null);
        stationService.addLineage(code, "CTL-1", null, "FM-IN", "INLET-1", dayAgo, null);
        stationService.addCurve(code, "PG-1",
                "[{\"headM\":0,\"flowM3s\":1.2},{\"headM\":5,\"flowM3s\":1.2}]", 0.05, dayAgo, null);
        stationService.addCurve(code, "PG-2",
                "[{\"headM\":0,\"flowM3s\":0.9},{\"headM\":5,\"flowM3s\":0.9}]", 0.08,
                dayAgo, T0.plus(3, ChronoUnit.HOURS));

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0, "{\"levelM\":3.0}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", T0, "{\"levelM\":3.8}");
        tel(code, TelemetryKind.FLOW_IN, "FM-IN", T0, "{\"flowM3s\":0.5}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", T0, "{\"flowM3s\":0.0}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-1", T0, "{\"state\":\"OPEN\"}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-1", T0, "{\"running\":false}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-2", T0, "{\"running\":false}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(30, ChronoUnit.MINUTES), "{\"levelM\":3.18}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-1", T0.plus(30, ChronoUnit.MINUTES), "{\"running\":true}");
        // 反转流量计：实际排出 1.2 m³/s，读数为负
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", T0.plus(30, ChronoUnit.MINUTES), "{\"flowM3s\":-1.2}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(60, ChronoUnit.MINUTES), "{\"levelM\":2.928}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", T0.plus(60, ChronoUnit.MINUTES), "{\"levelM\":3.85}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-2", T0.plus(60, ChronoUnit.MINUTES), "{\"running\":true}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", T0.plus(60, ChronoUnit.MINUTES), "{\"flowM3s\":-2.1}");

        // 闸门关闭运行：T0+2h 关闸，泵继续运行 1 小时（不计排水贡献）
        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(120, ChronoUnit.MINUTES), "{\"levelM\":1.776}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", T0.plus(120, ChronoUnit.MINUTES), "{\"levelM\":3.9}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-1", T0.plus(120, ChronoUnit.MINUTES), "{\"state\":\"CLOSED\"}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", T0.plus(120, ChronoUnit.MINUTES), "{\"flowM3s\":0.0}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(180, ChronoUnit.MINUTES), "{\"levelM\":2.136}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-1", T0.plus(180, ChronoUnit.MINUTES), "{\"state\":\"OPEN\"}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", T0.plus(180, ChronoUnit.MINUTES), "{\"flowM3s\":-2.1}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(240, ChronoUnit.MINUTES), "{\"levelM\":0.984}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", T0.plus(240, ChronoUnit.MINUTES), "{\"levelM\":3.95}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-2", T0.plus(240, ChronoUnit.MINUTES), "{\"running\":false}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", T0.plus(240, ChronoUnit.MINUTES), "{\"flowM3s\":-1.2}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(270, ChronoUnit.MINUTES), "{\"levelM\":0.732}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-1", T0.plus(270, ChronoUnit.MINUTES), "{\"running\":false}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT", T0.plus(270, ChronoUnit.MINUTES), "{\"flowM3s\":0.0}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(360, ChronoUnit.MINUTES), "{\"levelM\":1.272}");
    }

    /** PS-02：路线 A = 路线 B = 2160，校准误差 20% 跨阈 → PENDING_REVIEW */
    void ps02(String code, String basin) {
        stationService.register(code, "临江站", basin, 2000, 0.02, 0.05, 0.15);
        Instant dayAgo = T0.minus(1, ChronoUnit.DAYS);
        stationService.addLineage(code, "CTL-2", "PG-9", "FM-OUT2", "OUT-2", dayAgo, null);
        stationService.addLineage(code, "CTL-2", null, "FM-IN2", "INLET-2", dayAgo, null);
        stationService.addCurve(code, "PG-9",
                "[{\"headM\":0,\"flowM3s\":0.6},{\"headM\":5,\"flowM3s\":0.6}]", 0.20, dayAgo, null);

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0, "{\"levelM\":2.2}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", T0, "{\"levelM\":3.4}");
        tel(code, TelemetryKind.FLOW_IN, "FM-IN2", T0, "{\"flowM3s\":0.3}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT2", T0, "{\"flowM3s\":0.0}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-2", T0, "{\"state\":\"OPEN\"}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-9", T0, "{\"running\":false}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(30, ChronoUnit.MINUTES), "{\"levelM\":2.47}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-9", T0.plus(30, ChronoUnit.MINUTES), "{\"running\":true}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT2", T0.plus(30, ChronoUnit.MINUTES), "{\"flowM3s\":0.6}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(90, ChronoUnit.MINUTES), "{\"levelM\":1.93}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-9", T0.plus(90, ChronoUnit.MINUTES), "{\"running\":false}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT2", T0.plus(90, ChronoUnit.MINUTES), "{\"flowM3s\":0.0}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(120, ChronoUnit.MINUTES), "{\"levelM\":2.2}");
    }

    /** PS-03：路线 B = 3240，路线 A ≈ 3268.8（扬程插值），校准完整 → CLOSED 并附单泵分解 */
    void ps03(String code, String basin) {
        stationService.register(code, "高新区站", basin, 3000, 0.02, 0.05, 0.15);
        Instant dayAgo = T0.minus(1, ChronoUnit.DAYS);
        stationService.addLineage(code, "CTL-3", "PG-7", "FM-OUT3", "OUT-3", dayAgo, null);
        stationService.addLineage(code, "CTL-3", null, "FM-IN3", "INLET-3", dayAgo, null);
        stationService.addCurve(code, "PG-7",
                "[{\"headM\":0.5,\"flowM3s\":1.0},{\"headM\":1.5,\"flowM3s\":0.8},"
                        + "{\"headM\":2.5,\"flowM3s\":0.6}]", 0.04, dayAgo, null);

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0, "{\"levelM\":2.6}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", T0, "{\"levelM\":3.5}");
        tel(code, TelemetryKind.FLOW_IN, "FM-IN3", T0, "{\"flowM3s\":0.4}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT3", T0, "{\"flowM3s\":0.0}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-3", T0, "{\"state\":\"OPEN\"}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-7", T0, "{\"running\":false}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(30, ChronoUnit.MINUTES), "{\"levelM\":2.84}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-7", T0.plus(30, ChronoUnit.MINUTES), "{\"running\":true}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT3", T0.plus(30, ChronoUnit.MINUTES), "{\"flowM3s\":0.9}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(90, ChronoUnit.MINUTES), "{\"levelM\":2.24}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-7", T0.plus(90, ChronoUnit.MINUTES), "{\"running\":false}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT3", T0.plus(90, ChronoUnit.MINUTES), "{\"flowM3s\":0.0}");

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(120, ChronoUnit.MINUTES), "{\"levelM\":2.48}");
    }

    /** PS-04：水位上涨远超进水解释能力（净流向反向）→ 物理方向校验失败 */
    void ps04(String code, String basin) {
        stationService.register(code, "反向站", basin, 5000, 0.02, 0.05, 0.15);
        Instant dayAgo = T0.minus(1, ChronoUnit.DAYS);
        stationService.addLineage(code, "CTL-4", null, "FM-IN4", "INLET-4", dayAgo, null);

        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0, "{\"levelM\":2.0}");
        tel(code, TelemetryKind.FLOW_IN, "FM-IN4", T0, "{\"flowM3s\":0.1}");
        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", T0.plus(60, ChronoUnit.MINUTES), "{\"levelM\":3.0}");
    }

    private void tel(String code, TelemetryKind kind, String asset, Instant time, String payload) {
        stationService.addTelemetry(code, kind, asset, time, payload);
    }
}
