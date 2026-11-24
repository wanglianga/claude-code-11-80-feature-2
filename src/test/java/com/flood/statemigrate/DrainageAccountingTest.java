package com.flood.statemigrate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.flood.statemigrate.domain.*;
import com.flood.statemigrate.service.DrainageAccountingService;
import com.flood.statemigrate.service.PumpStationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排水贡献核算核心核验：
 * 两路线站级总量、未闭合区间、闸门关闭时长不计贡献、反转流量计改正、
 * 校准完整时才附单泵分解、校准误差跨阈进入待核。
 */
class DrainageAccountingTest extends BaseIntegrationTest {

    @Autowired private PumpStationService stationService;
    @Autowired private DrainageAccountingService accountingService;
    @Autowired private ObjectMapper om;

    private PumpScenarioBuilder pumps;

    @BeforeEach
    void setUp() {
        pumps = new PumpScenarioBuilder(stationService);
    }

    @Test
    void sharedOutletReversedMeterGateClosedRunAndCalibrationGap() {
        pumps.ps01("PS-01", "BASIN-T");
        Instant t0 = PumpScenarioBuilder.T0;
        DrainageRun run = accountingService.run("PS-01", t0, t0.plus(6, ChronoUnit.HOURS));

        // 路线 B（控制体守恒）站级总量：19440 m³
        assertEquals(19440.0, run.getRouteBNetM3(), 0.5);
        // 路线 A 缺校准段无法给单点量；已覆盖部分 16200 m³ 仅作参考
        assertNull(run.getRouteANetM3());
        assertEquals(16200.0, run.getRouteACoveredM3(), 0.5);
        assertFalse(run.isCalibrationComplete());
        assertNull(run.getPerPumpJson(), "校准不完整时不得附带单泵分解");
        // 缺校准段 → 窗口无法闭合，不给统一单点量
        assertEquals(AccountingStatus.UNCLOSED, run.getStatus());
        assertNull(run.getUnifiedNetM3());
        assertTrue(run.isDirectionValid());

        // 未闭合区间 = [T0+3h, T0+4h]，原因 CALIBRATION_GAP
        JsonNode unclosed = readTree(run.getUnclosedJson());
        assertEquals(1, unclosed.size());
        assertEquals("CALIBRATION_GAP", unclosed.get(0).path("reason").asText());
        assertEquals(t0.plus(3, ChronoUnit.HOURS).toString(), unclosed.get(0).path("from").asText());
        assertEquals(t0.plus(4, ChronoUnit.HOURS).toString(), unclosed.get(0).path("to").asText());

        // 设备质量记录：反转流量计、两台泵的闸门关闭运行、PG-2 缺校准段
        List<EquipmentQualityRecord> records = accountingService.qualityRecords("PS-01");
        assertTrue(hasRecord(records, QualityCategory.METER_REVERSED, "FM-OUT"));
        assertTrue(hasRecord(records, QualityCategory.GATE_CLOSED_RUNNING, "PG-1"));
        assertTrue(hasRecord(records, QualityCategory.GATE_CLOSED_RUNNING, "PG-2"));
        assertTrue(hasRecord(records, QualityCategory.CALIBRATION_GAP, "PG-2"));
        EquipmentQualityRecord gateClosed = records.stream()
                .filter(r -> r.getCategory() == QualityCategory.GATE_CLOSED_RUNNING
                        && r.getAssetId().equals("PG-1")).findFirst().orElseThrow();
        assertEquals(t0.plus(2, ChronoUnit.HOURS), gateClosed.getWindowStart());
        assertEquals(t0.plus(3, ChronoUnit.HOURS), gateClosed.getWindowEnd());

        // 闸门关闭分段 [T0+2h, T0+3h]：两路线净量均为 0，运行时长不得记成排水贡献
        JsonNode segments = readTree(run.getSegmentsJson());
        JsonNode closedSeg = null;
        for (JsonNode seg : segments) {
            if (seg.path("from").asText().equals(t0.plus(2, ChronoUnit.HOURS).toString())) {
                closedSeg = seg;
            }
        }
        assertNotNull(closedSeg);
        assertEquals(0.0, closedSeg.path("routeB").path("netM3").asDouble(), 0.5);
        assertEquals(0.0, closedSeg.path("routeA").path("netM3").asDouble(), 0.5);
        assertTrue(closedSeg.path("routeA").path("gateClosedZero").asBoolean());

        // 运行时长统计：PG-1 运行 4h，其中闸门关闭 1h，有效 3h
        JsonNode runtime = readTree(run.getRuntimeJson());
        assertEquals(14400.0, runtime.path("PG-1").path("runtimeSec").asDouble(), 0.1);
        assertEquals(3600.0, runtime.path("PG-1").path("gateClosedRuntimeSec").asDouble(), 0.1);
        assertEquals(10800.0, runtime.path("PG-1").path("effectiveSec").asDouble(), 0.1);
        assertEquals(10800.0, runtime.path("PG-2").path("runtimeSec").asDouble(), 0.1);
        assertEquals(3600.0, runtime.path("PG-2").path("gateClosedRuntimeSec").asDouble(), 0.1);
    }

    @Test
    void completeCalibrationClosesAndAttachesPerPumpDecomposition() {
        pumps.ps03("PS-03", "BASIN-T");
        Instant t0 = PumpScenarioBuilder.T0;
        DrainageRun run = accountingService.run("PS-03", t0, t0.plus(2, ChronoUnit.HOURS));

        assertEquals(AccountingStatus.CLOSED, run.getStatus());
        assertTrue(run.isDirectionValid());
        assertEquals(3240.0, run.getRouteBNetM3(), 0.5);
        assertEquals(3268.8, run.getRouteANetM3(), 0.5);
        // 统一估计：两路线不确定区间交集
        assertEquals(3268.8, run.getUnifiedNetM3(), 0.6);
        assertTrue(run.getUnifiedLowM3() <= run.getUnifiedNetM3());
        assertTrue(run.getUnifiedHighM3() >= run.getUnifiedNetM3());
        // 校准完整 → 附带单泵分解（与站级总量同一目标，不是另一口径）
        assertTrue(run.isCalibrationComplete());
        JsonNode perPump = readTree(run.getPerPumpJson());
        assertEquals(1, perPump.size());
        assertEquals("PG-7", perPump.get(0).path("pumpGroupId").asText());
        assertEquals(3268.8, perPump.get(0).path("netM3").asDouble(), 0.5);
        assertEquals("[]", run.getUnclosedJson());
    }

    @Test
    void calibrationErrorBreachGoesToPendingReview() {
        pumps.ps02("PS-02", "BASIN-T");
        Instant t0 = PumpScenarioBuilder.T0;
        DrainageRun run = accountingService.run("PS-02", t0, t0.plus(2, ChronoUnit.HOURS));

        // 两路线闭合（均为 2160 m³），但校准误差 20% 跨过安全阈值 15% → 待核
        assertEquals(AccountingStatus.PENDING_REVIEW, run.getStatus());
        assertEquals(2160.0, run.getRouteANetM3(), 0.5);
        assertEquals(2160.0, run.getRouteBNetM3(), 0.5);
        assertEquals(2160.0, run.getUnifiedNetM3(), 0.5);
        assertTrue(run.isDirectionValid());
        List<EquipmentQualityRecord> records = accountingService.qualityRecords("PS-02");
        assertTrue(hasRecord(records, QualityCategory.CALIBRATION_ERROR_BREACH, "PG-9"));
    }

    @Test
    void directionViolationFailsPhysicalDirectionCheck() {
        pumps.ps04("PS-04", "BASIN-T");
        Instant t0 = PumpScenarioBuilder.T0;
        DrainageRun run = accountingService.run("PS-04", t0, t0.plus(1, ChronoUnit.HOURS));

        // 净量 = 0.1×3600 − 1.0×5000 = −4640 m³，净流向反向 → 物理方向校验失败
        assertEquals(-4640.0, run.getRouteBNetM3(), 0.5);
        assertFalse(run.isDirectionValid());
        assertEquals(AccountingStatus.UNCLOSED, run.getStatus());
        assertNull(run.getUnifiedNetM3());
        assertTrue(readTree(run.getUnclosedJson()).toString().contains("DIRECTION_VIOLATION"));
        List<EquipmentQualityRecord> records = accountingService.qualityRecords("PS-04");
        assertTrue(hasRecord(records, QualityCategory.DIRECTION_VIOLATION, "PS-04"));
    }

    @Test
    void divergedRoutesCannotClose() {
        // 校准曲线声称 2.0 m³/s，但控制体守恒与出水道交叉校验均为 0.6 m³/s → 无法闭合
        String code = "PS-05";
        Instant t0 = PumpScenarioBuilder.T0;
        stationService.register(code, "偏差站", "BASIN-T", 2000, 0.02, 0.05, 0.15);
        Instant dayAgo = t0.minus(1, ChronoUnit.DAYS);
        stationService.addLineage(code, "CTL-5", "PG-5", "FM-OUT5", "OUT-5", dayAgo, null);
        stationService.addLineage(code, "CTL-5", null, "FM-IN5", "INLET-5", dayAgo, null);
        stationService.addCurve(code, "PG-5",
                "[{\"headM\":0,\"flowM3s\":2.0},{\"headM\":5,\"flowM3s\":2.0}]", 0.05, dayAgo, null);
        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0, "{\"levelM\":2.2}");
        tel(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, "LV-DOWN", t0, "{\"levelM\":3.4}");
        tel(code, TelemetryKind.FLOW_IN, "FM-IN5", t0, "{\"flowM3s\":0.3}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT5", t0, "{\"flowM3s\":0.0}");
        tel(code, TelemetryKind.GATE_STATE, "GATE-5", t0, "{\"state\":\"OPEN\"}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-5", t0, "{\"running\":false}");
        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(30, ChronoUnit.MINUTES), "{\"levelM\":2.47}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-5", t0.plus(30, ChronoUnit.MINUTES), "{\"running\":true}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT5", t0.plus(30, ChronoUnit.MINUTES), "{\"flowM3s\":0.6}");
        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(90, ChronoUnit.MINUTES), "{\"levelM\":1.93}");
        tel(code, TelemetryKind.PUMP_STATE, "PG-5", t0.plus(90, ChronoUnit.MINUTES), "{\"running\":false}");
        tel(code, TelemetryKind.FLOW_OUT, "FM-OUT5", t0.plus(90, ChronoUnit.MINUTES), "{\"flowM3s\":0.0}");
        tel(code, TelemetryKind.WATER_LEVEL_UPSTREAM, "LV-UP", t0.plus(120, ChronoUnit.MINUTES), "{\"levelM\":2.2}");

        DrainageRun run = accountingService.run(code, t0, t0.plus(2, ChronoUnit.HOURS));
        assertEquals(2160.0, run.getRouteBNetM3(), 0.5);
        assertEquals(7200.0, run.getRouteANetM3(), 0.5);
        assertEquals(AccountingStatus.UNCLOSED, run.getStatus());
        assertNull(run.getUnifiedNetM3());
        assertTrue(readTree(run.getUnclosedJson()).toString().contains("BALANCE_MISMATCH"));
        assertTrue(hasRecord(accountingService.qualityRecords(code), QualityCategory.BALANCE_MISMATCH, code));
    }

    @Test
    void rerunReplacesPreviousRunAndQualityRecords() {
        pumps.ps02("PS-02", "BASIN-T");
        Instant t0 = PumpScenarioBuilder.T0;
        DrainageRun first = accountingService.run("PS-02", t0, t0.plus(2, ChronoUnit.HOURS));
        DrainageRun second = accountingService.run("PS-02", t0, t0.plus(2, ChronoUnit.HOURS));
        assertNotEquals(first.getId(), second.getId());
        assertEquals(1, accountingService.list("PS-02").size());
        // 旧运行的质量记录随替换清除，不再残留
        assertTrue(qualityRecordRepo.findByRunIdOrderByCreatedAtAsc(first.getId()).isEmpty());
        assertFalse(accountingService.qualityRecordsOfRun(second.getId()).isEmpty());
    }

    private void tel(String code, TelemetryKind kind, String asset, Instant time, String payload) {
        stationService.addTelemetry(code, kind, asset, time, payload);
    }

    private boolean hasRecord(List<EquipmentQualityRecord> records, QualityCategory category, String assetId) {
        return records.stream().anyMatch(r -> r.getCategory() == category && r.getAssetId().equals(assetId));
    }

    private JsonNode readTree(String json) {
        return om.readTree(json);
    }
}
