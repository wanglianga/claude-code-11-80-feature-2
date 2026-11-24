package com.flood.statemigrate.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.flood.statemigrate.domain.*;
import com.flood.statemigrate.error.ApiException;
import com.flood.statemigrate.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 泵站登记、设备沿革、校准曲线与站端遥测的登记查询。
 */
@Service
public class PumpStationService {

    private final PumpStationRepo stationRepo;
    private final EquipmentLineageRepo lineageRepo;
    private final CalibrationCurveRepo curveRepo;
    private final StationTelemetryRepo telemetryRepo;
    private final ObjectMapper om;

    public PumpStationService(PumpStationRepo stationRepo, EquipmentLineageRepo lineageRepo,
                              CalibrationCurveRepo curveRepo, StationTelemetryRepo telemetryRepo,
                              ObjectMapper om) {
        this.stationRepo = stationRepo;
        this.lineageRepo = lineageRepo;
        this.curveRepo = curveRepo;
        this.telemetryRepo = telemetryRepo;
        this.om = om;
    }

    @Transactional
    public PumpStation register(String stationCode, String name, String basinCode, double pondAreaM2,
                                double levelTolM, double meterErrorPct, double safetyErrorPct) {
        if (stationRepo.findByStationCode(stationCode).isPresent()) {
            throw ApiException.conflict("泵站编码已存在: " + stationCode);
        }
        if (pondAreaM2 <= 0) {
            throw ApiException.badRequest("站前调蓄池水面面积须为正数");
        }
        if (levelTolM < 0 || meterErrorPct < 0 || safetyErrorPct <= 0) {
            throw ApiException.badRequest("量测误差须非负且校准误差安全阈值须为正数");
        }
        PumpStation s = new PumpStation();
        s.setStationCode(stationCode);
        s.setName(name);
        s.setBasinCode(basinCode);
        s.setPondAreaM2(pondAreaM2);
        s.setLevelTolM(levelTolM);
        s.setMeterErrorPct(meterErrorPct);
        s.setSafetyErrorPct(safetyErrorPct);
        return stationRepo.save(s);
    }

    public PumpStation getByCode(String stationCode) {
        return stationRepo.findByStationCode(stationCode)
                .orElseThrow(() -> ApiException.notFound("泵站不存在: " + stationCode));
    }

    public List<PumpStation> list() {
        return stationRepo.findAllByOrderByStationCodeAsc();
    }

    // ---------- 设备沿革 ----------

    @Transactional
    public EquipmentLineage addLineage(String stationCode, String controllerId, String pumpGroupId,
                                       String flowMeterId, String outletId,
                                       Instant effectiveFrom, Instant effectiveTo) {
        getByCode(stationCode);
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw ApiException.badRequest("有效区间终点须晚于起点");
        }
        EquipmentLineage l = new EquipmentLineage();
        l.setStationCode(stationCode);
        l.setControllerId(controllerId);
        l.setPumpGroupId(pumpGroupId);
        l.setFlowMeterId(flowMeterId);
        l.setOutletId(outletId);
        l.setEffectiveFrom(effectiveFrom);
        l.setEffectiveTo(effectiveTo);
        return lineageRepo.save(l);
    }

    public List<EquipmentLineage> listLineage(String stationCode) {
        getByCode(stationCode);
        return lineageRepo.findByStationCodeOrderByEffectiveFromAsc(stationCode);
    }

    // ---------- 校准曲线 ----------

    @Transactional
    public CalibrationCurve addCurve(String stationCode, String pumpGroupId, String pointsJson,
                                     double errorPct, Instant validFrom, Instant validTo) {
        getByCode(stationCode);
        if (errorPct < 0) {
            throw ApiException.badRequest("校准误差须非负");
        }
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw ApiException.badRequest("校准有效区间终点须晚于起点");
        }
        JsonNode points = readTree(pointsJson);
        if (!points.isArray() || points.size() < 2) {
            throw ApiException.badRequest("校准曲线至少需 2 个 (headM, flowM3s) 点");
        }
        for (JsonNode p : points) {
            if (!p.has("headM") || !p.has("flowM3s")) {
                throw ApiException.badRequest("校准曲线点须含 headM 与 flowM3s");
            }
        }
        CalibrationCurve c = new CalibrationCurve();
        c.setStationCode(stationCode);
        c.setPumpGroupId(pumpGroupId);
        c.setPointsJson(pointsJson);
        c.setErrorPct(errorPct);
        c.setValidFrom(validFrom);
        c.setValidTo(validTo);
        return curveRepo.save(c);
    }

    public List<CalibrationCurve> listCurves(String stationCode) {
        getByCode(stationCode);
        return curveRepo.findByStationCodeOrderByPumpGroupIdAscValidFromAsc(stationCode);
    }

    // ---------- 站端遥测 ----------

    @Transactional
    public StationTelemetry addTelemetry(String stationCode, TelemetryKind kind, String assetId,
                                         Instant eventTime, String payloadJson) {
        getByCode(stationCode);
        JsonNode p = readTree(payloadJson);
        switch (kind) {
            case WATER_LEVEL_UPSTREAM, WATER_LEVEL_DOWNSTREAM -> requireField(p, "levelM", kind);
            case FLOW_IN, FLOW_OUT -> requireField(p, "flowM3s", kind);
            case GATE_STATE -> {
                requireField(p, "state", kind);
                String state = p.path("state").asText("");
                if (!"OPEN".equalsIgnoreCase(state) && !"CLOSED".equalsIgnoreCase(state)) {
                    throw ApiException.badRequest("闸门状态须为 OPEN 或 CLOSED: " + state);
                }
            }
            case PUMP_STATE -> requireField(p, "running", kind);
        }
        StationTelemetry t = new StationTelemetry();
        t.setStationCode(stationCode);
        t.setKind(kind);
        t.setAssetId(assetId);
        t.setEventTime(eventTime);
        t.setPayloadJson(payloadJson);
        return telemetryRepo.save(t);
    }

    public List<StationTelemetry> listTelemetry(String stationCode, TelemetryKind kind,
                                                Instant from, Instant to) {
        getByCode(stationCode);
        if (from != null && to != null) {
            return kind != null
                    ? telemetryRepo.findByStationCodeAndKindAndEventTimeBetweenOrderByEventTimeAsc(
                            stationCode, kind, from, to)
                    : telemetryRepo.findByStationCodeAndEventTimeBetweenOrderByEventTimeAsc(stationCode, from, to);
        }
        Instant now = Instant.now();
        Instant defaultFrom = now.minusSeconds(7L * 24 * 3600);
        return kind != null
                ? telemetryRepo.findByStationCodeAndKindAndEventTimeBetweenOrderByEventTimeAsc(
                        stationCode, kind, defaultFrom, now)
                : telemetryRepo.findByStationCodeAndEventTimeBetweenOrderByEventTimeAsc(
                        stationCode, defaultFrom, now);
    }

    private void requireField(JsonNode p, String field, TelemetryKind kind) {
        if (!p.has(field)) {
            throw ApiException.badRequest(kind + " 遥测 payload 缺少字段: " + field);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw ApiException.badRequest("payloadJson 不是合法 JSON: " + e.getMessage());
        }
    }
}
