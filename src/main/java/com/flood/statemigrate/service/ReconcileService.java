package com.flood.statemigrate.service;

import com.flood.statemigrate.domain.AccountingStatus;
import com.flood.statemigrate.domain.PumpStation;
import com.flood.statemigrate.domain.QualityCategory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对账器：核验两条路线的站级总量能否闭合，标出无法闭合的窗口（及子区间），
 * 并把原因反馈到设备质量记录。
 * 闭合规则：路线 A 校准完整且 |A−B| ≤ max(绝对容差, 比例容差×max(|A|,|B|))，
 * 且出水道流量计交叉校验一致；物理方向校验失败（净流向反向）一律无法闭合。
 * 闭合但校准误差跨过安全阈值的窗口进入待核（PENDING_REVIEW），不进入运行效率报表。
 */
@Service
public class ReconcileService {

    /** 闭合容差：比例（10%）与绝对量（50 m³）取大 */
    public static final double CLOSURE_TOL_PCT = 0.10;
    public static final double CLOSURE_TOL_ABS_M3 = 50.0;

    public record ReconcileOutcome(
            AccountingStatus status,
            boolean directionValid,
            Double unifiedNetM3,
            Double unifiedLowM3,
            Double unifiedHighM3,
            List<Map<String, Object>> unclosed,
            List<BalanceResult.Issue> additionalIssues) {}

    public ReconcileOutcome reconcile(PumpStation station, BalanceResult b,
                                      Instant windowStart, Instant windowEnd) {
        List<Map<String, Object>> unclosed = new ArrayList<>();
        List<BalanceResult.Issue> extra = new ArrayList<>();

        // 物理方向校验：净流向须与排水方向一致（允许不确定区间内的微小反向）
        double dirTol = Math.max(CLOSURE_TOL_ABS_M3, CLOSURE_TOL_PCT * Math.abs(b.routeBNet));
        boolean directionValid = b.routeBNet >= -dirTol
                && (!b.outletMeterPresent || b.outletCrossCheckM3 >= -dirTol);
        if (!directionValid) {
            unclosed.add(unclosedWindow(windowStart, windowEnd, "DIRECTION_VIOLATION",
                    "净流向与排水方向相反（路线 B 净量 " + WaterBalanceService.round3(b.routeBNet)
                            + " m³，出水道交叉校验 " + WaterBalanceService.round3(b.outletCrossCheckM3) + " m³）"));
            extra.add(new BalanceResult.Issue(QualityCategory.DIRECTION_VIOLATION, station.getStationCode(),
                    windowStart, windowEnd, "物理方向校验失败：净流向与排水方向相反，不进入流域汇总"));
        }

        // 缺校准段 / 绑定失效 → 路线 A 无法给单点量，窗口无法闭合
        if (!b.calibrationComplete) {
            for (BalanceResult.Issue i : b.issues) {
                if (i.category() == QualityCategory.CALIBRATION_GAP || i.category() == QualityCategory.STALE_BINDING) {
                    unclosed.add(unclosedWindow(i.from(), i.to(), i.category().name(), i.assetId() + ": " + i.detail()));
                }
            }
        } else if (b.routeANet != null) {
            // 两路线站级总量比对
            double tol = Math.max(CLOSURE_TOL_ABS_M3,
                    CLOSURE_TOL_PCT * Math.max(Math.abs(b.routeANet), Math.abs(b.routeBNet)));
            if (Math.abs(b.routeANet - b.routeBNet) > tol) {
                unclosed.add(unclosedWindow(windowStart, windowEnd, "BALANCE_MISMATCH",
                        "路线 A " + WaterBalanceService.round3(b.routeANet) + " m³ 与路线 B "
                                + WaterBalanceService.round3(b.routeBNet) + " m³ 偏差超容差 "
                                + WaterBalanceService.round3(tol) + " m³"));
                extra.add(new BalanceResult.Issue(QualityCategory.BALANCE_MISMATCH, station.getStationCode(),
                        windowStart, windowEnd, "两路线站级总量偏差超容差，窗口无法闭合"));
            }
        }

        // 出水道流量计交叉校验（符号改正后）与路线 B 一致性
        if (b.outletMeterPresent) {
            double tol = Math.max(CLOSURE_TOL_ABS_M3,
                    CLOSURE_TOL_PCT * Math.max(Math.abs(b.outletCrossCheckM3), Math.abs(b.routeBNet)));
            if (Math.abs(b.outletCrossCheckM3 - b.routeBNet) > tol) {
                unclosed.add(unclosedWindow(windowStart, windowEnd, "BALANCE_MISMATCH",
                        "出水道交叉校验 " + WaterBalanceService.round3(b.outletCrossCheckM3)
                                + " m³ 与路线 B " + WaterBalanceService.round3(b.routeBNet) + " m³ 偏差超容差"));
                extra.add(new BalanceResult.Issue(QualityCategory.BALANCE_MISMATCH, station.getStationCode(),
                        windowStart, windowEnd, "出水道流量计累计与控制体守恒结果偏差超容差"));
            }
        }

        boolean closed = unclosed.isEmpty();
        boolean errorBreached = b.issues.stream()
                .anyMatch(i -> i.category() == QualityCategory.CALIBRATION_ERROR_BREACH);
        AccountingStatus status = !closed ? AccountingStatus.UNCLOSED
                : errorBreached ? AccountingStatus.PENDING_REVIEW : AccountingStatus.CLOSED;

        // 统一估计：两路线不确定区间求交（无交集时取包络均值）；未闭合不给统一单点量
        Double unifiedNet = null;
        Double unifiedLow = null;
        Double unifiedHigh = null;
        if (closed && b.routeANet != null) {
            double low = Math.max(b.routeALow, b.routeBLow);
            double high = Math.min(b.routeAHigh, b.routeBHigh);
            if (low <= high) {
                unifiedNet = WaterBalanceService.round3((low + high) / 2);
                unifiedLow = WaterBalanceService.round3(low);
                unifiedHigh = WaterBalanceService.round3(high);
            } else {
                unifiedNet = WaterBalanceService.round3((b.routeANet + b.routeBNet) / 2);
                unifiedLow = WaterBalanceService.round3(Math.min(b.routeALow, b.routeBLow));
                unifiedHigh = WaterBalanceService.round3(Math.max(b.routeAHigh, b.routeBHigh));
            }
        }
        return new ReconcileOutcome(status, directionValid, unifiedNet, unifiedLow, unifiedHigh,
                unclosed, extra);
    }

    private Map<String, Object> unclosedWindow(Instant from, Instant to, String reason, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from.toString());
        m.put("to", to.toString());
        m.put("reason", reason);
        m.put("detail", detail);
        return m;
    }
}
