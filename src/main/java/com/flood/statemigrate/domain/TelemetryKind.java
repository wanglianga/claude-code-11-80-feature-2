package com.flood.statemigrate.domain;

/**
 * 站端遥测类别：
 * WATER_LEVEL_UPSTREAM   站前（上游/调蓄池）水位，payload {"levelM": 2.5}
 * WATER_LEVEL_DOWNSTREAM 出水侧（下游）水位，payload {"levelM": 3.8}
 * FLOW_IN                站前进水流量计，payload {"flowM3s": 0.5}（流入控制体为正）
 * FLOW_OUT               出水道流量计，payload {"flowM3s": 1.2}（排出站外为正；装反的表记为负值）
 * GATE_STATE             出水闸门状态，payload {"state": "OPEN"|"CLOSED"}
 * PUMP_STATE             泵组启停状态，payload {"running": true|false}
 */
public enum TelemetryKind {
    WATER_LEVEL_UPSTREAM,
    WATER_LEVEL_DOWNSTREAM,
    FLOW_IN,
    FLOW_OUT,
    GATE_STATE,
    PUMP_STATE
}
