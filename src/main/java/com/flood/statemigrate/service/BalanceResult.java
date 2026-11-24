package com.flood.statemigrate.service;

import com.flood.statemigrate.domain.QualityCategory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水量平衡计算结果（服务内部使用）：
 * 逐段明细、两条路线的站级总量与不确定区间、出水道交叉校验量、
 * 运行时长统计与计算过程中发现的问题（供对账器反馈设备质量记录）。
 */
public class BalanceResult {

    /** 计算中发现的问题（反向流量计/闸门关闭运行/缺校准段/校准误差跨阈/缺遥测） */
    public record Issue(QualityCategory category, String assetId, Instant from, Instant to, String detail) {}

    /** 逐段明细（JSON 可序列化） */
    public final List<Map<String, Object>> segments = new ArrayList<>();

    /** 路线 A（泵组校准曲线积分）：站级总量；存在缺校准段时为 null */
    public Double routeANet;
    public double routeALow;
    public double routeAHigh;
    /** 路线 A 已校准覆盖部分的参考量 */
    public double routeACovered;
    /** 泵组校准曲线是否覆盖窗口内全部运行分段 */
    public boolean calibrationComplete = true;
    /** 单泵分解（仅校准完整时有意义） */
    public final List<Map<String, Object>> perPump = new ArrayList<>();

    /** 路线 B（控制体守恒）：站级总量与不确定区间 */
    public double routeBNet;
    public double routeBLow;
    public double routeBHigh;

    /** 出水道流量计（符号改正后）累计排量，用于交叉校验 */
    public double outletCrossCheckM3;
    public boolean outletMeterPresent;

    /** 运行时长统计：pumpGroupId -> {runtimeSec, gateClosedRuntimeSec, effectiveSec} */
    public final Map<String, Map<String, Double>> runtimeStats = new LinkedHashMap<>();

    /** 计算中发现的问题 */
    public final List<Issue> issues = new ArrayList<>();
}
