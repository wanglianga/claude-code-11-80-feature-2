package com.flood.statemigrate.web;

import com.flood.statemigrate.domain.*;
import com.flood.statemigrate.service.PumpStationService;
import com.flood.statemigrate.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Tag(name = "泵站与设备沿革")
@RestController
@RequestMapping("/api/pump-stations")
public class PumpStationController {

    private final PumpStationService stationService;

    public PumpStationController(PumpStationService stationService) {
        this.stationService = stationService;
    }

    @Operation(summary = "登记泵站（调蓄池面积、量测误差、校准误差安全阈值）")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PumpStation register(@Valid @RequestBody PumpStationReq req) {
        return stationService.register(req.stationCode(), req.name(), req.basinCode(), req.pondAreaM2(),
                req.levelTolM(), req.meterErrorPct(), req.safetyErrorPct());
    }

    @Operation(summary = "泵站列表")
    @GetMapping
    public List<PumpStation> list() {
        return stationService.list();
    }

    @Operation(summary = "泵站详情")
    @GetMapping("/{stationCode}")
    public PumpStation get(@PathVariable String stationCode) {
        return stationService.getByCode(stationCode);
    }

    @Operation(summary = "登记设备沿革：控制器/泵组/流量计绑定到有效区间")
    @PostMapping("/{stationCode}/lineage")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentLineage addLineage(@PathVariable String stationCode,
                                       @Valid @RequestBody LineageReq req) {
        return stationService.addLineage(stationCode, req.controllerId(), req.pumpGroupId(),
                req.flowMeterId(), req.outletId(), req.effectiveFrom(), req.effectiveTo());
    }

    @Operation(summary = "设备沿革列表")
    @GetMapping("/{stationCode}/lineage")
    public List<EquipmentLineage> listLineage(@PathVariable String stationCode) {
        return stationService.listLineage(stationCode);
    }

    @Operation(summary = "登记泵组校准曲线（Q-H 曲线、校准误差、有效区间）")
    @PostMapping("/{stationCode}/calibration-curves")
    @ResponseStatus(HttpStatus.CREATED)
    public CalibrationCurve addCurve(@PathVariable String stationCode,
                                     @Valid @RequestBody CalibrationCurveReq req) {
        return stationService.addCurve(stationCode, req.pumpGroupId(), req.pointsJson(),
                req.errorPct(), req.validFrom(), req.validTo());
    }

    @Operation(summary = "校准曲线列表")
    @GetMapping("/{stationCode}/calibration-curves")
    public List<CalibrationCurve> listCurves(@PathVariable String stationCode) {
        return stationService.listCurves(stationCode);
    }

    @Operation(summary = "写入站端遥测（水位/进出流量/闸门状态/泵组启停）")
    @PostMapping("/{stationCode}/telemetry")
    @ResponseStatus(HttpStatus.CREATED)
    public StationTelemetry addTelemetry(@PathVariable String stationCode,
                                         @Valid @RequestBody TelemetryReq req) {
        return stationService.addTelemetry(stationCode, req.kind(), req.assetId(),
                req.eventTime(), req.payloadJson());
    }

    @Operation(summary = "遥测查询（按类别与时间范围）")
    @GetMapping("/{stationCode}/telemetry")
    public List<StationTelemetry> listTelemetry(@PathVariable String stationCode,
                                                @RequestParam(required = false) TelemetryKind kind,
                                                @RequestParam(required = false) Instant from,
                                                @RequestParam(required = false) Instant to) {
        return stationService.listTelemetry(stationCode, kind, from, to);
    }
}
