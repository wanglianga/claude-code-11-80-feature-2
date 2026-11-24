package com.flood.statemigrate.service;

import tools.jackson.databind.ObjectMapper;
import com.flood.statemigrate.domain.*;
import com.flood.statemigrate.error.ApiException;
import com.flood.statemigrate.repo.DrainageRunRepo;
import com.flood.statemigrate.repo.EquipmentQualityRecordRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 排水贡献核算编排：水量平衡器逐段计算 → 对账器闭合核验 →
 * 持久化核算运行与设备质量记录。同一泵站同一窗口重复核算时替换旧结果。
 */
@Service
public class DrainageAccountingService {

    private final PumpStationService stationService;
    private final WaterBalanceService waterBalanceService;
    private final ReconcileService reconcileService;
    private final DrainageRunRepo runRepo;
    private final EquipmentQualityRecordRepo qualityRepo;
    private final ObjectMapper om;

    public DrainageAccountingService(PumpStationService stationService, WaterBalanceService waterBalanceService,
                                     ReconcileService reconcileService, DrainageRunRepo runRepo,
                                     EquipmentQualityRecordRepo qualityRepo, ObjectMapper om) {
        this.stationService = stationService;
        this.waterBalanceService = waterBalanceService;
        this.reconcileService = reconcileService;
        this.runRepo = runRepo;
        this.qualityRepo = qualityRepo;
        this.om = om;
    }

    @Transactional
    public DrainageRun run(String stationCode, Instant windowStart, Instant windowEnd) {
        PumpStation station = stationService.getByCode(stationCode);
        BalanceResult balance = waterBalanceService.compute(station, windowStart, windowEnd);
        ReconcileService.ReconcileOutcome outcome =
                reconcileService.reconcile(station, balance, windowStart, windowEnd);

        // 同一窗口重跑：替换旧核算运行及其质量记录
        for (DrainageRun old : runRepo.findByStationCodeAndWindowStartAndWindowEnd(
                stationCode, windowStart, windowEnd)) {
            qualityRepo.deleteByRunId(old.getId());
            runRepo.delete(old);
        }

        DrainageRun run = new DrainageRun();
        run.setStationCode(stationCode);
        run.setWindowStart(windowStart);
        run.setWindowEnd(windowEnd);
        run.setStatus(outcome.status());
        run.setDirectionValid(outcome.directionValid());
        run.setRouteANetM3(balance.routeANet != null ? WaterBalanceService.round3(balance.routeANet) : null);
        run.setRouteALowM3(balance.routeANet != null ? WaterBalanceService.round3(balance.routeALow) : null);
        run.setRouteAHighM3(balance.routeANet != null ? WaterBalanceService.round3(balance.routeAHigh) : null);
        run.setRouteACoveredM3(WaterBalanceService.round3(balance.routeACovered));
        run.setRouteBNetM3(WaterBalanceService.round3(balance.routeBNet));
        run.setRouteBLowM3(WaterBalanceService.round3(balance.routeBLow));
        run.setRouteBHighM3(WaterBalanceService.round3(balance.routeBHigh));
        run.setUnifiedNetM3(outcome.unifiedNetM3());
        run.setUnifiedLowM3(outcome.unifiedLowM3());
        run.setUnifiedHighM3(outcome.unifiedHighM3());
        run.setCalibrationComplete(balance.calibrationComplete);
        run.setPerPumpJson(balance.calibrationComplete ? writeJson(balance.perPump) : null);
        run.setSegmentsJson(writeJson(balance.segments));
        run.setUnclosedJson(writeJson(outcome.unclosed()));
        run.setRuntimeJson(writeJson(balance.runtimeStats));
        runRepo.save(run);

        // 对账器把无法闭合窗口的原因反馈到设备质量记录
        List<BalanceResult.Issue> all = balance.issues;
        all.addAll(outcome.additionalIssues());
        for (BalanceResult.Issue issue : all) {
            EquipmentQualityRecord record = new EquipmentQualityRecord();
            record.setStationCode(stationCode);
            record.setRunId(run.getId());
            record.setCategory(issue.category());
            record.setAssetId(issue.assetId());
            record.setWindowStart(issue.from());
            record.setWindowEnd(issue.to());
            record.setDetail(issue.detail());
            qualityRepo.save(record);
        }
        return run;
    }

    public DrainageRun get(Long runId) {
        return runRepo.findById(runId).orElseThrow(() -> ApiException.notFound("核算运行不存在: " + runId));
    }

    public List<DrainageRun> list(String stationCode) {
        stationService.getByCode(stationCode);
        return runRepo.findByStationCodeOrderByWindowStartAsc(stationCode);
    }

    public List<EquipmentQualityRecord> qualityRecords(String stationCode) {
        stationService.getByCode(stationCode);
        return qualityRepo.findByStationCodeOrderByCreatedAtAsc(stationCode);
    }

    public List<EquipmentQualityRecord> qualityRecordsOfRun(Long runId) {
        get(runId);
        return qualityRepo.findByRunIdOrderByCreatedAtAsc(runId);
    }

    private String writeJson(Object value) {
        try {
            return om.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
