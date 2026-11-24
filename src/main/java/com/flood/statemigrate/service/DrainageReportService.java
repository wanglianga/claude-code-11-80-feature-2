package com.flood.statemigrate.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.flood.statemigrate.domain.*;
import com.flood.statemigrate.repo.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表服务：
 * 运行效率报表只统计闭合（CLOSED）窗口；校准误差跨阈进入待核（PENDING_REVIEW）
 * 与无法闭合（UNCLOSED）的窗口不进入效率报表，列入排除清单并附原因。
 * 流域汇总只累计通过物理方向校验（directionValid）且已有统一估计的净量；
 * 未闭合窗口没有统一单点量，不累计，列入排除清单。
 */
@Service
public class DrainageReportService {

    private final PumpStationRepo stationRepo;
    private final DrainageRunRepo runRepo;
    private final EquipmentQualityRecordRepo qualityRepo;
    private final PumpStationService stationService;
    private final ObjectMapper om;

    public DrainageReportService(PumpStationRepo stationRepo, DrainageRunRepo runRepo,
                                 EquipmentQualityRecordRepo qualityRepo, PumpStationService stationService,
                                 ObjectMapper om) {
        this.stationRepo = stationRepo;
        this.runRepo = runRepo;
        this.qualityRepo = qualityRepo;
        this.stationService = stationService;
        this.om = om;
    }

    /** 运行效率报表：仅闭合窗口计入；待核/未闭合窗口列入 excludedWindows */
    public Map<String, Object> efficiency(String stationCode, Instant from, Instant to) {
        stationService.getByCode(stationCode);
        List<DrainageRun> runs = runsInRange(stationCode, from, to);

        List<Map<String, Object>> included = new ArrayList<>();
        List<Map<String, Object>> excluded = new ArrayList<>();
        double totalNet = 0;
        double totalRuntimeMin = 0;
        double totalEffectiveMin = 0;
        double totalGateClosedMin = 0;
        for (DrainageRun run : runs) {
            Map<String, Double> runtime = runtimeTotals(run);
            if (run.getStatus() == AccountingStatus.CLOSED) {
                double runtimeMin = runtime.get("runtimeSec") / 60.0;
                double effectiveMin = runtime.get("effectiveSec") / 60.0;
                double gateClosedMin = runtime.get("gateClosedRuntimeSec") / 60.0;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("runId", run.getId());
                row.put("windowStart", run.getWindowStart().toString());
                row.put("windowEnd", run.getWindowEnd().toString());
                row.put("netM3", run.getUnifiedNetM3());
                row.put("pumpRuntimeMin", WaterBalanceService.round3(runtimeMin));
                row.put("gateClosedRuntimeMin", WaterBalanceService.round3(gateClosedMin));
                row.put("effectiveRuntimeMin", WaterBalanceService.round3(effectiveMin));
                row.put("netM3PerRuntimeMin",
                        runtimeMin > 0 ? WaterBalanceService.round3(run.getUnifiedNetM3() / runtimeMin) : null);
                included.add(row);
                totalNet += run.getUnifiedNetM3();
                totalRuntimeMin += runtimeMin;
                totalEffectiveMin += effectiveMin;
                totalGateClosedMin += gateClosedMin;
            } else {
                excluded.add(excludedWindow(run));
            }
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("windows", included.size());
        totals.put("netM3", WaterBalanceService.round3(totalNet));
        totals.put("pumpRuntimeMin", WaterBalanceService.round3(totalRuntimeMin));
        totals.put("gateClosedRuntimeMin", WaterBalanceService.round3(totalGateClosedMin));
        totals.put("effectiveRuntimeMin", WaterBalanceService.round3(totalEffectiveMin));
        totals.put("netM3PerRuntimeMin",
                totalRuntimeMin > 0 ? WaterBalanceService.round3(totalNet / totalRuntimeMin) : null);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("stationCode", stationCode);
        report.put("from", from.toString());
        report.put("to", to.toString());
        report.put("includedWindows", included);
        report.put("excludedWindows", excluded);
        report.put("totals", totals);
        return report;
    }

    /** 流域汇总：只累计通过物理方向校验且已有统一估计的净量 */
    public Map<String, Object> basinSummary(String basinCode, Instant from, Instant to) {
        List<PumpStation> stations = stationRepo.findByBasinCodeOrderByStationCodeAsc(basinCode);
        List<Map<String, Object>> stationRows = new ArrayList<>();
        List<Map<String, Object>> excluded = new ArrayList<>();
        double totalNet = 0;
        double totalLow = 0;
        double totalHigh = 0;
        int includedWindows = 0;
        for (PumpStation station : stations) {
            double stationNet = 0;
            int windows = 0;
            int closed = 0;
            int pendingReview = 0;
            for (DrainageRun run : runsInRange(station.getStationCode(), from, to)) {
                if (run.isDirectionValid() && run.getUnifiedNetM3() != null) {
                    stationNet += run.getUnifiedNetM3();
                    totalNet += run.getUnifiedNetM3();
                    totalLow += run.getUnifiedLowM3();
                    totalHigh += run.getUnifiedHighM3();
                    windows++;
                    includedWindows++;
                    if (run.getStatus() == AccountingStatus.PENDING_REVIEW) {
                        pendingReview++;
                    } else {
                        closed++;
                    }
                } else {
                    excluded.add(excludedWindow(run));
                }
            }
            if (windows > 0 || excluded.stream().anyMatch(
                    e -> e.get("stationCode").equals(station.getStationCode()))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("stationCode", station.getStationCode());
                row.put("netM3", WaterBalanceService.round3(stationNet));
                row.put("windows", windows);
                row.put("closedWindows", closed);
                row.put("pendingReviewWindows", pendingReview);
                stationRows.add(row);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("basinCode", basinCode);
        summary.put("from", from.toString());
        summary.put("to", to.toString());
        summary.put("totalNetM3", WaterBalanceService.round3(totalNet));
        summary.put("totalLowM3", WaterBalanceService.round3(totalLow));
        summary.put("totalHighM3", WaterBalanceService.round3(totalHigh));
        summary.put("includedWindows", includedWindows);
        summary.put("stations", stationRows);
        summary.put("excludedWindows", excluded);
        return summary;
    }

    // ---------- 内部 ----------

    private List<DrainageRun> runsInRange(String stationCode, Instant from, Instant to) {
        return runRepo.findByStationCodeAndWindowStartGreaterThanEqualAndWindowEndLessThanEqualOrderByWindowStartAsc(
                stationCode, from, to);
    }

    /** 排除窗口及原因（取自该次核算反馈到设备质量记录的类别） */
    private Map<String, Object> excludedWindow(DrainageRun run) {
        Set<String> reasons = new LinkedHashSet<>();
        for (EquipmentQualityRecord r : qualityRepo.findByRunIdOrderByCreatedAtAsc(run.getId())) {
            reasons.add(r.getCategory().name());
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stationCode", run.getStationCode());
        row.put("runId", run.getId());
        row.put("windowStart", run.getWindowStart().toString());
        row.put("windowEnd", run.getWindowEnd().toString());
        row.put("status", run.getStatus().name());
        row.put("directionValid", run.isDirectionValid());
        row.put("reasons", new ArrayList<>(reasons));
        return row;
    }

    /** 窗口内全部泵组的运行时长合计（秒） */
    private Map<String, Double> runtimeTotals(DrainageRun run) {
        Map<String, Double> totals = new LinkedHashMap<>();
        totals.put("runtimeSec", 0.0);
        totals.put("gateClosedRuntimeSec", 0.0);
        totals.put("effectiveSec", 0.0);
        if (run.getRuntimeJson() == null) {
            return totals;
        }
        try {
            JsonNode root = om.readTree(run.getRuntimeJson());
            for (Map.Entry<String, JsonNode> pump : root.properties()) {
                for (String key : totals.keySet()) {
                    totals.merge(key, pump.getValue().path(key).asDouble(0), Double::sum);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("运行时长统计解析失败: runId=" + run.getId(), e);
        }
        return totals;
    }
}
