package com.flood.statemigrate.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.flood.statemigrate.domain.*;
import com.flood.statemigrate.error.ApiException;
import com.flood.statemigrate.repo.CalibrationCurveRepo;
import com.flood.statemigrate.repo.EquipmentLineageRepo;
import com.flood.statemigrate.repo.StationTelemetryRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 水量平衡器：按上下游水位、进出流量和闸门状态逐段计算净排量及不确定区间。
 *
 * 物理模型（控制体 = 站前调蓄池，出水闸门位于排水通道上）：
 *   守恒：dS/dt = Q_in − Q_out，S = 站前水位 × 水面面积；
 *   闸门关闭时出水道断开，泵组运行只在站内循环，有效净排量为 0（运行分钟不计贡献）。
 *
 * 两条路线统一估计同一泵站同一时间窗的站级总有效净排量：
 *   路线 A（PUMP_CURVE）    逐段按泵组校准曲线 Q(head) 积分（仅运行且闸门开启的分段），
 *                           局部归因清楚；缺校准段无法给单点量；
 *   路线 B（CONTROL_VOLUME）逐段 Q_in·dt − ΔS 反推站级总净排量，覆盖更完整，
 *                           但无法将共用出水道的量精确分给单泵。
 * 出水道流量计作为交叉校验；读数方向与物理流向相反的流量计按物理一致性改正符号并记录。
 */
@Service
public class WaterBalanceService {

    private final StationTelemetryRepo telemetryRepo;
    private final EquipmentLineageRepo lineageRepo;
    private final CalibrationCurveRepo curveRepo;
    private final ObjectMapper om;

    public WaterBalanceService(StationTelemetryRepo telemetryRepo, EquipmentLineageRepo lineageRepo,
                               CalibrationCurveRepo curveRepo, ObjectMapper om) {
        this.telemetryRepo = telemetryRepo;
        this.lineageRepo = lineageRepo;
        this.curveRepo = curveRepo;
        this.om = om;
    }

    /** 分段计算状态（步进量：流量/闸门/泵状态；水位按点值线性插值） */
    private static final class Segment {
        Instant from;
        Instant to;
        Map<String, Double> flowIn = new LinkedHashMap<>();   // 进水流量计 -> m³/s
        Map<String, Double> flowOut = new LinkedHashMap<>();  // 出水道流量计 -> m³/s（原始读数）
        Map<String, Boolean> gateOpen = new LinkedHashMap<>(); // 闸门 -> 是否开启（默认开启）
        Map<String, Boolean> pumpRunning = new LinkedHashMap<>(); // 泵组 -> 是否运行（默认停止）
    }

    public BalanceResult compute(PumpStation station, Instant windowStart, Instant windowEnd) {
        if (!windowEnd.isAfter(windowStart)) {
            throw ApiException.badRequest("windowEnd 必须晚于 windowStart");
        }
        String code = station.getStationCode();
        List<StationTelemetry> events = telemetryRepo
                .findByStationCodeAndEventTimeBetweenOrderByEventTimeAsc(code, windowStart, windowEnd);

        // 水位序列（含窗口前最近一次读数），点值间线性插值
        List<double[]> upSeries = levelSeries(code, TelemetryKind.WATER_LEVEL_UPSTREAM, windowStart, windowEnd, events);
        List<double[]> downSeries = levelSeries(code, TelemetryKind.WATER_LEVEL_DOWNSTREAM, windowStart, windowEnd, events);
        boolean pumpRanInWindow = events.stream().anyMatch(e -> e.getKind() == TelemetryKind.PUMP_STATE
                && readTree(e.getPayloadJson()).path("running").asBoolean(false));
        if (upSeries.isEmpty()) {
            throw ApiException.unprocessable("窗口内无站前水位数据，控制体守恒无法计算: " + code);
        }
        if (pumpRanInWindow && downSeries.isEmpty()) {
            throw ApiException.unprocessable("窗口内泵组运行但无下游水位数据，无法计算扬程: " + code);
        }

        // 初始步进状态：窗口起点前最近一次读数
        Segment initial = new Segment();
        for (String asset : telemetryRepo.distinctAssets(code, TelemetryKind.FLOW_IN)) {
            initial.flowIn.put(asset, latestValue(code, TelemetryKind.FLOW_IN, asset, windowStart, "flowM3s", 0.0));
        }
        for (String asset : telemetryRepo.distinctAssets(code, TelemetryKind.FLOW_OUT)) {
            initial.flowOut.put(asset, latestValue(code, TelemetryKind.FLOW_OUT, asset, windowStart, "flowM3s", 0.0));
        }
        for (String asset : telemetryRepo.distinctAssets(code, TelemetryKind.GATE_STATE)) {
            initial.gateOpen.put(asset, latestGate(code, asset, windowStart));
        }
        for (String asset : telemetryRepo.distinctAssets(code, TelemetryKind.PUMP_STATE)) {
            initial.pumpRunning.put(asset, latestPump(code, asset, windowStart));
        }

        // 切分分段：窗口边界 + 窗口内全部事件时刻
        TreeSet<Instant> cuts = new TreeSet<>();
        cuts.add(windowStart);
        cuts.add(windowEnd);
        Map<Instant, List<StationTelemetry>> eventsAt = new TreeMap<>();
        for (StationTelemetry e : events) {
            if (e.getEventTime().isAfter(windowStart) && e.getEventTime().isBefore(windowEnd)) {
                cuts.add(e.getEventTime());
            }
            if (e.getEventTime().isBefore(windowEnd)) {
                eventsAt.computeIfAbsent(e.getEventTime(), k -> new ArrayList<>()).add(e);
            }
        }
        List<Instant> times = new ArrayList<>(cuts);
        List<Segment> segments = new ArrayList<>();
        Segment state = initial;
        for (int i = 0; i < times.size() - 1; i++) {
            Segment seg = state;
            seg.from = times.get(i);
            seg.to = times.get(i + 1);
            applyEvents(seg, eventsAt.getOrDefault(seg.from, List.of()));
            segments.add(seg);
            state = copy(seg);
        }
        segments.removeIf(s -> !s.to.isAfter(s.from));

        // 反向流量计检测：泵运行且闸门开启的分段内，出水道读数累计为负 → 物理方向不可能 → 装反
        Map<String, Boolean> reversed = new LinkedHashMap<>();
        for (String asset : initial.flowOut.keySet()) {
            double integral = 0;
            for (Segment s : segments) {
                if (anyPumpRunning(s) && anyGateOpen(s)) {
                    integral += s.flowOut.getOrDefault(asset, 0.0) * durationSec(s);
                }
            }
            reversed.put(asset, integral < -1e-6);
        }
        for (Segment s : segments) {
            for (Map.Entry<String, Boolean> e : reversed.entrySet()) {
                if (e.getValue()) {
                    s.flowOut.put(e.getKey(), -s.flowOut.getOrDefault(e.getKey(), 0.0));
                }
            }
        }

        BalanceResult result = new BalanceResult();
        result.outletMeterPresent = !initial.flowOut.isEmpty();
        List<EquipmentLineage> lineage = lineageRepo.findByStationCodeOrderByEffectiveFromAsc(code);
        List<CalibrationCurve> curves = curveRepo.findByStationCodeOrderByPumpGroupIdAscValidFromAsc(code);
        Map<String, double[]> perPumpAcc = new LinkedHashMap<>(); // pumpGroupId -> [net, unc]
        List<BalanceResult.Issue> rawIssues = new ArrayList<>();

        for (Segment s : segments) {
            double dt = durationSec(s);
            Instant mid = s.from.plusMillis((s.to.toEpochMilli() - s.from.toEpochMilli()) / 2);
            double upFrom = interp(upSeries, s.from);
            double upTo = interp(upSeries, s.to);
            double upMid = interp(upSeries, mid);
            Double downMid = downSeries.isEmpty() ? null : interp(downSeries, mid);
            double qIn = s.flowIn.values().stream().mapToDouble(Double::doubleValue).sum();
            double qOut = s.flowOut.values().stream().mapToDouble(Double::doubleValue).sum();
            boolean gateOpen = anyGateOpen(s);
            boolean anyRunning = anyPumpRunning(s);

            // ---- 路线 B：控制体守恒，净排量 = Q_in·dt − ΔS ----
            double netB = qIn * dt - (upTo - upFrom) * station.getPondAreaM2();
            double uncB = station.getMeterErrorPct() * Math.abs(qIn) * dt
                    + station.getLevelTolM() * station.getPondAreaM2();
            result.routeBNet += netB;
            result.routeBLow += netB - uncB;
            result.routeBHigh += netB + uncB;
            result.outletCrossCheckM3 += qOut * dt;

            // ---- 路线 A：泵组校准曲线积分（运行且闸门开启才计贡献） ----
            Map<String, Double> segPump = new LinkedHashMap<>();
            boolean segGateClosedZero = false;
            boolean segGap = false;
            double segA = 0;
            for (Map.Entry<String, Boolean> p : s.pumpRunning.entrySet()) {
                String pump = p.getKey();
                if (!Boolean.TRUE.equals(p.getValue())) {
                    continue;
                }
                Map<String, Double> stats = result.runtimeStats.computeIfAbsent(pump, k -> {
                    Map<String, Double> m = new LinkedHashMap<>();
                    m.put("runtimeSec", 0.0);
                    m.put("gateClosedRuntimeSec", 0.0);
                    m.put("effectiveSec", 0.0);
                    return m;
                });
                stats.merge("runtimeSec", dt, Double::sum);
                if (!gateOpen) {
                    // 闸门关闭运行：只在站内循环，不计排水贡献
                    stats.merge("gateClosedRuntimeSec", dt, Double::sum);
                    segGateClosedZero = true;
                    rawIssues.add(new BalanceResult.Issue(QualityCategory.GATE_CLOSED_RUNNING, pump, s.from, s.to,
                            "闸门关闭期间泵组运行，该时段不计排水贡献（运行分钟 ≠ 有效排水）"));
                    continue;
                }
                stats.merge("effectiveSec", dt, Double::sum);
                if (!boundEffective(lineage, pump, mid)) {
                    // 沿革绑定失效后仍上报运行遥测：不参与核算，反馈设备质量记录
                    segGap = true;
                    result.calibrationComplete = false;
                    rawIssues.add(new BalanceResult.Issue(QualityCategory.STALE_BINDING, pump, s.from, s.to,
                            "泵组在窗口内无有效设备沿革绑定，运行遥测不参与核算"));
                    continue;
                }
                if (downMid == null) {
                    segGap = true;
                    result.calibrationComplete = false;
                    rawIssues.add(new BalanceResult.Issue(QualityCategory.CALIBRATION_GAP, pump, s.from, s.to,
                            "缺下游水位遥测，无法计算扬程"));
                    continue;
                }
                double head = downMid - upMid;
                CalibrationCurve curve = validCurveAt(curves, pump, mid);
                if (curve == null) {
                    segGap = true;
                    result.calibrationComplete = false;
                    rawIssues.add(new BalanceResult.Issue(QualityCategory.CALIBRATION_GAP, pump, s.from, s.to,
                            "校准曲线未覆盖该时段（缺校准段）"));
                    continue;
                }
                Double flow = interpolateCurve(curve, head);
                if (flow == null) {
                    segGap = true;
                    result.calibrationComplete = false;
                    rawIssues.add(new BalanceResult.Issue(QualityCategory.CALIBRATION_GAP, pump, s.from, s.to,
                            "扬程 " + round3(head) + "m 超出校准曲线覆盖范围（缺校准段）"));
                    continue;
                }
                double v = flow * dt;
                double unc = v * curve.getErrorPct();
                segA += v;
                segPump.merge(pump, v, Double::sum);
                perPumpAcc.computeIfAbsent(pump, k -> new double[2]);
                perPumpAcc.get(pump)[0] += v;
                perPumpAcc.get(pump)[1] += unc;
                if (curve.getErrorPct() > station.getSafetyErrorPct()) {
                    rawIssues.add(new BalanceResult.Issue(QualityCategory.CALIBRATION_ERROR_BREACH, pump, s.from, s.to,
                            "校准误差 " + pct(curve.getErrorPct()) + " 跨过安全阈值 "
                                    + pct(station.getSafetyErrorPct()) + "，窗口须进入待核"));
                }
            }
            result.routeACovered += segA;

            Map<String, Object> segJson = new LinkedHashMap<>();
            segJson.put("from", s.from.toString());
            segJson.put("to", s.to.toString());
            segJson.put("durationSec", (long) dt);
            segJson.put("upLevelM", round3(upMid));
            if (downMid != null) {
                segJson.put("downLevelM", round3(downMid));
            }
            segJson.put("qInM3s", round3(qIn));
            segJson.put("qOutM3s", round3(qOut));
            segJson.put("gateOpen", s.gateOpen);
            segJson.put("pumpRunning", s.pumpRunning);
            segJson.put("routeB", Map.of(
                    "netM3", round3(netB), "lowM3", round3(netB - uncB), "highM3", round3(netB + uncB)));
            Map<String, Object> segAJson = new LinkedHashMap<>();
            segAJson.put("netM3", round3(segA));
            segAJson.put("perPump", segPump);
            segAJson.put("gateClosedZero", segGateClosedZero);
            segAJson.put("calibrationGap", segGap);
            segJson.put("routeA", segAJson);
            result.segments.add(segJson);
        }

        // 反向流量计问题记录（整窗），附带沿革绑定信息定位责任设备
        for (Map.Entry<String, Boolean> e : reversed.entrySet()) {
            if (e.getValue()) {
                result.issues.add(new BalanceResult.Issue(QualityCategory.METER_REVERSED, e.getKey(),
                        windowStart, windowEnd,
                        "出水道流量计读数方向与物理流向相反（泵运行且闸门开启时累计读数为负），已按物理一致性改正符号"
                                + bindingInfo(lineage, e.getKey(), windowStart)));
            }
        }
        result.issues.addAll(mergeIssues(rawIssues));

        // 路线 A 站级总量与单泵分解（仅校准完整时给单点量）
        if (result.calibrationComplete) {
            double net = 0;
            double unc = 0;
            for (Map.Entry<String, double[]> e : perPumpAcc.entrySet()) {
                net += e.getValue()[0];
                unc += e.getValue()[1];
                Map<String, Object> pp = new LinkedHashMap<>();
                pp.put("pumpGroupId", e.getKey());
                pp.put("netM3", round3(e.getValue()[0]));
                pp.put("lowM3", round3(e.getValue()[0] - e.getValue()[1]));
                pp.put("highM3", round3(e.getValue()[0] + e.getValue()[1]));
                result.perPump.add(pp);
            }
            result.routeANet = net;
            result.routeALow = net - unc;
            result.routeAHigh = net + unc;
        }
        return result;
    }

    // ---------- 内部 ----------

    private void applyEvents(Segment seg, List<StationTelemetry> events) {
        for (StationTelemetry e : events) {
            JsonNode p = readTree(e.getPayloadJson());
            switch (e.getKind()) {
                case FLOW_IN -> seg.flowIn.put(e.getAssetId(), p.path("flowM3s").asDouble(0));
                case FLOW_OUT -> seg.flowOut.put(e.getAssetId(), p.path("flowM3s").asDouble(0));
                case GATE_STATE -> seg.gateOpen.put(e.getAssetId(),
                        "OPEN".equalsIgnoreCase(p.path("state").asText("OPEN")));
                case PUMP_STATE -> seg.pumpRunning.put(e.getAssetId(), p.path("running").asBoolean(false));
                default -> { /* 水位由插值序列处理 */ }
            }
        }
    }

    private Segment copy(Segment s) {
        Segment c = new Segment();
        c.flowIn = new LinkedHashMap<>(s.flowIn);
        c.flowOut = new LinkedHashMap<>(s.flowOut);
        c.gateOpen = new LinkedHashMap<>(s.gateOpen);
        c.pumpRunning = new LinkedHashMap<>(s.pumpRunning);
        return c;
    }

    /** 水位序列：[epochSec, levelM]，含窗口前最近一次读数，按时间排序去重 */
    private List<double[]> levelSeries(String code, TelemetryKind kind, Instant ws, Instant we,
                                       List<StationTelemetry> windowEvents) {
        TreeMap<Instant, Double> points = new TreeMap<>();
        for (String asset : telemetryRepo.distinctAssets(code, kind)) {
            telemetryRepo.findFirstByStationCodeAndKindAndAssetIdAndEventTimeLessThanEqualOrderByEventTimeDesc(
                            code, kind, asset, ws)
                    .ifPresent(t -> points.put(t.getEventTime(),
                            readTree(t.getPayloadJson()).path("levelM").asDouble(0)));
        }
        for (StationTelemetry e : windowEvents) {
            if (e.getKind() == kind) {
                points.put(e.getEventTime(), readTree(e.getPayloadJson()).path("levelM").asDouble(0));
            }
        }
        List<double[]> series = new ArrayList<>();
        for (Map.Entry<Instant, Double> e : points.entrySet()) {
            series.add(new double[]{e.getKey().getEpochSecond(), e.getValue()});
        }
        return series;
    }

    /** 点值序列线性插值；窗口外按最近点值延伸 */
    private double interp(List<double[]> series, Instant t) {
        double x = t.getEpochSecond();
        if (series.isEmpty()) {
            return 0;
        }
        if (x <= series.get(0)[0]) {
            return series.get(0)[1];
        }
        double[] prev = series.get(0);
        for (int i = 1; i < series.size(); i++) {
            double[] cur = series.get(i);
            if (x <= cur[0]) {
                if (cur[0] == prev[0]) {
                    return cur[1];
                }
                double r = (x - prev[0]) / (cur[0] - prev[0]);
                return prev[1] + r * (cur[1] - prev[1]);
            }
            prev = cur;
        }
        return series.get(series.size() - 1)[1];
    }

    private double latestValue(String code, TelemetryKind kind, String asset, Instant at, String field, double dflt) {
        return telemetryRepo.findFirstByStationCodeAndKindAndAssetIdAndEventTimeLessThanEqualOrderByEventTimeDesc(
                        code, kind, asset, at)
                .map(t -> readTree(t.getPayloadJson()).path(field).asDouble(dflt))
                .orElse(dflt);
    }

    private boolean latestGate(String code, String asset, Instant at) {
        return telemetryRepo.findFirstByStationCodeAndKindAndAssetIdAndEventTimeLessThanEqualOrderByEventTimeDesc(
                        code, TelemetryKind.GATE_STATE, asset, at)
                .map(t -> "OPEN".equalsIgnoreCase(
                        readTree(t.getPayloadJson()).path("state").asText("OPEN")))
                .orElse(true);
    }

    private boolean latestPump(String code, String asset, Instant at) {
        return telemetryRepo.findFirstByStationCodeAndKindAndAssetIdAndEventTimeLessThanEqualOrderByEventTimeDesc(
                        code, TelemetryKind.PUMP_STATE, asset, at)
                .map(t -> readTree(t.getPayloadJson()).path("running").asBoolean(false))
                .orElse(false);
    }

    /** 分段时刻有效的校准曲线（取 validFrom 最新的一条） */
    private CalibrationCurve validCurveAt(List<CalibrationCurve> curves, String pumpGroupId, Instant t) {
        return curves.stream()
                .filter(c -> c.getPumpGroupId().equals(pumpGroupId) && c.validAt(t))
                .max(Comparator.comparing(CalibrationCurve::getValidFrom))
                .orElse(null);
    }

    /** Q-H 曲线分段线性插值；扬程超出覆盖范围返回 null（缺校准段） */
    private Double interpolateCurve(CalibrationCurve curve, double headM) {
        List<double[]> points = new ArrayList<>();
        for (JsonNode n : readTree(curve.getPointsJson())) {
            points.add(new double[]{n.path("headM").asDouble(0), n.path("flowM3s").asDouble(0)});
        }
        points.sort(Comparator.comparingDouble(p -> p[0]));
        if (points.isEmpty() || headM < points.get(0)[0] || headM > points.get(points.size() - 1)[0]) {
            return null;
        }
        return interp(points, headM);
    }

    private double interp(List<double[]> points, double x) {
        if (x <= points.get(0)[0]) {
            return points.get(0)[1];
        }
        double[] prev = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            double[] cur = points.get(i);
            if (x <= cur[0]) {
                if (cur[0] == prev[0]) {
                    return cur[1];
                }
                double r = (x - prev[0]) / (cur[0] - prev[0]);
                return prev[1] + r * (cur[1] - prev[1]);
            }
            prev = cur;
        }
        return points.get(points.size() - 1)[1];
    }

    /** 相邻问题区间合并（同类别同设备） */
    private List<BalanceResult.Issue> mergeIssues(List<BalanceResult.Issue> raw) {
        Map<String, BalanceResult.Issue> merged = new LinkedHashMap<>();
        List<BalanceResult.Issue> sorted = new ArrayList<>(raw);
        sorted.sort(Comparator.comparing(BalanceResult.Issue::category).thenComparing(BalanceResult.Issue::assetId)
                .thenComparing(BalanceResult.Issue::from));
        for (BalanceResult.Issue i : sorted) {
            String key = i.category() + "|" + i.assetId();
            BalanceResult.Issue prev = merged.get(key);
            if (prev != null && !i.from().isAfter(prev.to())) {
                merged.put(key, new BalanceResult.Issue(prev.category(), prev.assetId(), prev.from(),
                        i.to().isAfter(prev.to()) ? i.to() : prev.to(), prev.detail()));
            } else {
                merged.put(key, i);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 分段时刻泵组是否存在有效的设备沿革绑定 */
    private boolean boundEffective(List<EquipmentLineage> lineage, String pumpGroupId, Instant t) {
        return lineage.stream()
                .anyMatch(l -> pumpGroupId.equals(l.getPumpGroupId()) && l.effectiveAt(t));
    }

    /** 流量计在指定时刻的沿革绑定描述（控制器/泵组/出水道），用于质量记录定位 */
    private String bindingInfo(List<EquipmentLineage> lineage, String flowMeterId, Instant t) {
        return lineage.stream()
                .filter(l -> flowMeterId.equals(l.getFlowMeterId()) && l.effectiveAt(t))
                .findFirst()
                .map(l -> "（沿革绑定：控制器 " + l.getControllerId()
                        + (l.getPumpGroupId() != null ? " / 泵组 " + l.getPumpGroupId() : "")
                        + " / 出水道 " + l.getOutletId() + "）")
                .orElse("");
    }

    private boolean anyPumpRunning(Segment s) {
        return s.pumpRunning.values().stream().anyMatch(Boolean::booleanValue);
    }

    private boolean anyGateOpen(Segment s) {
        return s.gateOpen.isEmpty() || s.gateOpen.values().stream().anyMatch(Boolean::booleanValue);
    }

    private double durationSec(Segment s) {
        return (s.to.toEpochMilli() - s.from.toEpochMilli()) / 1000.0;
    }

    static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String pct(double ratio) {
        return round3(ratio * 100) + "%";
    }

    private JsonNode readTree(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("遥测 payload 解析失败: " + json, e);
        }
    }
}
