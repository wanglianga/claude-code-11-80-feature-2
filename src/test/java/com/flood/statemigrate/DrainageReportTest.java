package com.flood.statemigrate;

import com.flood.statemigrate.domain.AccountingStatus;
import com.flood.statemigrate.domain.DrainageRun;
import com.flood.statemigrate.service.DrainageAccountingService;
import com.flood.statemigrate.service.DrainageReportService;
import com.flood.statemigrate.service.PumpStationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 报表核验：运行效率报表排除待核/未闭合窗口；流域汇总只累计通过物理方向校验的净量。
 */
class DrainageReportTest extends BaseIntegrationTest {

    @Autowired private PumpStationService stationService;
    @Autowired private DrainageAccountingService accountingService;
    @Autowired private DrainageReportService reportService;

    private static final Instant FROM = PumpScenarioBuilder.T0.minus(1, ChronoUnit.HOURS);
    private static final Instant TO = PumpScenarioBuilder.T0.plus(7, ChronoUnit.HOURS);

    @BeforeEach
    void setUp() {
        PumpScenarioBuilder pumps = new PumpScenarioBuilder(stationService);
        Instant t0 = PumpScenarioBuilder.T0;
        pumps.ps01("PS-01", "BASIN-T");
        accountingService.run("PS-01", t0, t0.plus(6, ChronoUnit.HOURS));
        pumps.ps02("PS-02", "BASIN-T");
        accountingService.run("PS-02", t0, t0.plus(2, ChronoUnit.HOURS));
        pumps.ps03("PS-03", "BASIN-T");
        accountingService.run("PS-03", t0, t0.plus(2, ChronoUnit.HOURS));
        pumps.ps04("PS-04", "BASIN-T");
        accountingService.run("PS-04", t0, t0.plus(1, ChronoUnit.HOURS));
    }

    @Test
    @SuppressWarnings("unchecked")
    void efficiencyReportIncludesOnlyClosedWindows() {
        Map<String, Object> report = reportService.efficiency("PS-03", FROM, TO);
        List<Map<String, Object>> included = (List<Map<String, Object>>) report.get("includedWindows");
        assertEquals(1, included.size());
        assertEquals(60.0, (Double) included.get(0).get("pumpRuntimeMin"), 0.1);
        Map<String, Object> totals = (Map<String, Object>) report.get("totals");
        assertEquals(3268.8, (Double) totals.get("netM3"), 0.6);
        assertEquals(1, totals.get("windows"));
        assertTrue(((List<Map<String, Object>>) report.get("excludedWindows")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingReviewWindowExcludedFromEfficiencyReport() {
        // 校准误差跨阈的窗口须进入待核而不进入运行效率报表
        Map<String, Object> report = reportService.efficiency("PS-02", FROM, TO);
        assertTrue(((List<Map<String, Object>>) report.get("includedWindows")).isEmpty());
        List<Map<String, Object>> excluded = (List<Map<String, Object>>) report.get("excludedWindows");
        assertEquals(1, excluded.size());
        assertEquals(AccountingStatus.PENDING_REVIEW.name(), excluded.get(0).get("status"));
        assertTrue(((List<String>) excluded.get(0).get("reasons")).contains("CALIBRATION_ERROR_BREACH"));
        Map<String, Object> totals = (Map<String, Object>) report.get("totals");
        assertEquals(0, totals.get("windows"));
        assertEquals(0.0, (Double) totals.get("netM3"), 0.001);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unclosedWindowExcludedFromEfficiencyReportWithReasons() {
        Map<String, Object> report = reportService.efficiency("PS-01", FROM, TO);
        assertTrue(((List<Map<String, Object>>) report.get("includedWindows")).isEmpty());
        List<Map<String, Object>> excluded = (List<Map<String, Object>>) report.get("excludedWindows");
        assertEquals(1, excluded.size());
        assertEquals(AccountingStatus.UNCLOSED.name(), excluded.get(0).get("status"));
        List<String> reasons = (List<String>) excluded.get(0).get("reasons");
        assertTrue(reasons.contains("CALIBRATION_GAP"));
        assertTrue(reasons.contains("GATE_CLOSED_RUNNING"));
        assertTrue(reasons.contains("METER_REVERSED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void basinSummaryAccumulatesOnlyDirectionValidNetVolumes() {
        Map<String, Object> summary = reportService.basinSummary("BASIN-T", FROM, TO);
        // 仅 PS-02（待核但方向有效，2160）与 PS-03（闭合，3268.8）计入；PS-01 未闭合、PS-04 方向校验失败
        assertEquals(5428.8, (Double) summary.get("totalNetM3"), 0.8);
        assertEquals(2, summary.get("includedWindows"));

        List<Map<String, Object>> stations = (List<Map<String, Object>>) summary.get("stations");
        // 四站均列出（未闭合/方向校验失败的站净量为 0，仅作透明展示，不累计）
        assertEquals(4, stations.size());
        Map<String, Object> ps02 = stations.stream()
                .filter(s -> s.get("stationCode").equals("PS-02")).findFirst().orElseThrow();
        assertEquals(2160.0, (Double) ps02.get("netM3"), 0.5);
        assertEquals(1, ps02.get("pendingReviewWindows"));
        Map<String, Object> ps03 = stations.stream()
                .filter(s -> s.get("stationCode").equals("PS-03")).findFirst().orElseThrow();
        assertEquals(3268.8, (Double) ps03.get("netM3"), 0.6);
        assertEquals(1, ps03.get("closedWindows"));
        Map<String, Object> ps01row = stations.stream()
                .filter(s -> s.get("stationCode").equals("PS-01")).findFirst().orElseThrow();
        assertEquals(0.0, (Double) ps01row.get("netM3"), 0.001);

        List<Map<String, Object>> excluded = (List<Map<String, Object>>) summary.get("excludedWindows");
        assertEquals(2, excluded.size());
        Map<String, Object> ps01 = excluded.stream()
                .filter(s -> s.get("stationCode").equals("PS-01")).findFirst().orElseThrow();
        assertEquals(AccountingStatus.UNCLOSED.name(), ps01.get("status"));
        Map<String, Object> ps04 = excluded.stream()
                .filter(s -> s.get("stationCode").equals("PS-04")).findFirst().orElseThrow();
        assertEquals(false, ps04.get("directionValid"));
        assertTrue(((List<String>) ps04.get("reasons")).contains("DIRECTION_VIOLATION"));
    }

    @Test
    void drainageRunsAreListedPerStation() {
        List<DrainageRun> runs = accountingService.list("PS-01");
        assertEquals(1, runs.size());
        assertEquals(AccountingStatus.UNCLOSED, runs.get(0).getStatus());
    }
}
