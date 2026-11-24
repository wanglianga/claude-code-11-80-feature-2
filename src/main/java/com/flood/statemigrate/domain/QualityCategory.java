package com.flood.statemigrate.domain;

/**
 * 设备质量记录类别（对账器反馈）：
 * METER_REVERSED            流量计反向（物理方向校验发现读数方向与实际流向相反，已按物理一致性改正符号）
 * GATE_CLOSED_RUNNING       闸门关闭期间泵组仍在运行（该时段不计排水贡献）
 * CALIBRATION_GAP           缺校准段（曲线失效或扬程超出覆盖范围，路线 A 无法给单点量）
 * CALIBRATION_ERROR_BREACH  校准误差跨过安全阈值（窗口进入待核）
 * BALANCE_MISMATCH          两路线站级总量偏差超容差，窗口无法闭合
 * DIRECTION_VIOLATION       物理方向校验失败（净流向与排水方向相反），不进入流域汇总
 * STALE_BINDING             设备沿革绑定失效后该泵组仍上报运行遥测，对应时段不参与核算
 */
public enum QualityCategory {
    METER_REVERSED,
    GATE_CLOSED_RUNNING,
    CALIBRATION_GAP,
    CALIBRATION_ERROR_BREACH,
    BALANCE_MISMATCH,
    DIRECTION_VIOLATION,
    STALE_BINDING
}
