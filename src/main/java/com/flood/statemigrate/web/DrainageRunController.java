package com.flood.statemigrate.web;

import com.flood.statemigrate.domain.DrainageRun;
import com.flood.statemigrate.domain.EquipmentQualityRecord;
import com.flood.statemigrate.service.DrainageAccountingService;
import com.flood.statemigrate.web.dto.DrainageRunReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "泵站排水贡献核算")
@RestController
public class DrainageRunController {

    private final DrainageAccountingService accountingService;

    public DrainageRunController(DrainageAccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @Operation(summary = "运行核算：水量平衡器逐段计算两路线净排量，对账器闭合并反馈设备质量记录")
    @PostMapping("/api/pump-stations/{stationCode}/drainage-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public DrainageRun run(@PathVariable String stationCode, @Valid @RequestBody DrainageRunReq req) {
        return accountingService.run(stationCode, req.windowStart(), req.windowEnd());
    }

    @Operation(summary = "核算运行列表")
    @GetMapping("/api/pump-stations/{stationCode}/drainage-runs")
    public List<DrainageRun> list(@PathVariable String stationCode) {
        return accountingService.list(stationCode);
    }

    @Operation(summary = "核算运行详情（两路线站级总量、统一估计、逐段明细、未闭合区间、单泵分解）")
    @GetMapping("/api/drainage-runs/{runId}")
    public DrainageRun get(@PathVariable Long runId) {
        return accountingService.get(runId);
    }

    @Operation(summary = "核算运行反馈的设备质量记录")
    @GetMapping("/api/drainage-runs/{runId}/quality-records")
    public List<EquipmentQualityRecord> qualityRecordsOfRun(@PathVariable Long runId) {
        return accountingService.qualityRecordsOfRun(runId);
    }

    @Operation(summary = "泵站设备质量记录（反向流量计/闸门关闭运行/缺校准段/校准误差跨阈等）")
    @GetMapping("/api/pump-stations/{stationCode}/quality-records")
    public List<EquipmentQualityRecord> qualityRecords(@PathVariable String stationCode) {
        return accountingService.qualityRecords(stationCode);
    }
}
