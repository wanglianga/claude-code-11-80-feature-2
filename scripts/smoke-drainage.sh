#!/usr/bin/env bash
# 泵站排水贡献核算端到端冒烟：
#   设备沿革 → 两路线站级总量 → 未闭合区间 → 闸门关闭零贡献 → 反转流量计改正
#   → 校准误差跨阈待核 → 运行效率报表排除 → 流域汇总方向校验
# 前置：应用已启动且演示数据为初始状态（如需重置见 README「重置演示数据」）。
set -euo pipefail

BASE=${BASE:-http://127.0.0.1:8080}
AUTH=${AUTH:-operator:operator123}
FROM=${FROM:-2020-01-01T00:00:00Z}
TO=${TO:-2030-01-01T00:00:00Z}

req() { curl -sf -u "$AUTH" -H 'Content-Type: application/json' "$@"; }
step() { echo; echo "=== $* ==="; }

step "1. 泵站登记（演示数据：PS-01/PS-02/PS-03，流域 BASIN-01）"
STATIONS=$(req "$BASE/api/pump-stations")
echo "$STATIONS" | jq '[.[] | {stationCode, name, basinCode, pondAreaM2, safetyErrorPct}]'
echo "$STATIONS" | jq -e 'length == 3 and .[0].stationCode == "PS-01"' >/dev/null

step "2. PS-01 设备沿革：两台泵组共用出水道 OUT-1 与流量计 FM-OUT"
LINEAGE=$(req "$BASE/api/pump-stations/PS-01/lineage")
echo "$LINEAGE" | jq '[.[] | {controllerId, pumpGroupId, flowMeterId, outletId}]'
echo "$LINEAGE" | jq -e '[.[] | select(.flowMeterId=="FM-OUT" and .outletId=="OUT-1")] | length == 2' >/dev/null

step "3. PS-01 核算：路线 B=19440，路线 A 缺校准段无法给单点量 → 未闭合"
RUN1=$(req "$BASE/api/pump-stations/PS-01/drainage-runs" | jq '.[0]')
echo "$RUN1" | jq '{status, directionValid, routeANetM3, routeACoveredM3, routeBNetM3, unifiedNetM3, calibrationComplete}'
echo "$RUN1" | jq -e '.status == "UNCLOSED" and .routeANetM3 == null and .calibrationComplete == false
  and .unifiedNetM3 == null and (.routeBNetM3 - 19440 | fabs < 0.5)
  and (.routeACoveredM3 - 16200 | fabs < 0.5)' >/dev/null
echo "$RUN1" | jq -e '.unclosedJson | fromjson | any(.reason == "CALIBRATION_GAP")' >/dev/null
echo "未闭合区间:"
echo "$RUN1" | jq '.unclosedJson | fromjson'

step "4. PS-01 闸门关闭分段：两路线净量均为 0（运行时长不得记成排水贡献）"
echo "$RUN1" | jq -e '.segmentsJson | fromjson
  | map(select(.gateOpen["GATE-1"] == false))
  | length == 1 and .[0].routeB.netM3 == 0 and .[0].routeA.netM3 == 0
    and .[0].routeA.gateClosedZero == true' >/dev/null
echo "$RUN1" | jq '.segmentsJson | fromjson | map(select(.gateOpen["GATE-1"] == false))
  | .[0] | {from, to, routeB, routeA: .routeA.netM3}'
echo "$RUN1" | jq -e '.runtimeJson | fromjson
  | .["PG-1"].runtimeSec == 14400 and .["PG-1"].gateClosedRuntimeSec == 3600
    and .["PG-1"].effectiveSec == 10800' >/dev/null
echo "PG-1 运行 4h，其中闸门关闭 1h 不计贡献，有效 3h ✓"

step "5. PS-01 设备质量记录：反转流量计 / 闸门关闭运行 / 缺校准段"
QR1=$(req "$BASE/api/pump-stations/PS-01/quality-records")
echo "$QR1" | jq '[.[] | {category, assetId, detail: (.detail[:36])}]'
echo "$QR1" | jq -e 'any(.category == "METER_REVERSED" and .assetId == "FM-OUT")
  and any(.category == "GATE_CLOSED_RUNNING" and .assetId == "PG-1")
  and any(.category == "GATE_CLOSED_RUNNING" and .assetId == "PG-2")
  and any(.category == "CALIBRATION_GAP" and .assetId == "PG-2")' >/dev/null

step "6. 同窗口重跑（幂等替换）：PS-01 仍未闭合"
RERUN=$(req -X POST "$BASE/api/pump-stations/PS-01/drainage-runs" -d "{
  \"windowStart\": $(echo "$RUN1" | jq '.windowStart'),
  \"windowEnd\": $(echo "$RUN1" | jq '.windowEnd')}")
echo "$RERUN" | jq '{status, routeBNetM3}'
echo "$RERUN" | jq -e '.status == "UNCLOSED"' >/dev/null
[ "$(req "$BASE/api/pump-stations/PS-01/drainage-runs" | jq 'length')" = "1" ] \
  || { echo "同窗口重跑应替换而非新增"; exit 1; }

step "7. PS-02 校准误差跨阈：两路线闭合但进入待核（PENDING_REVIEW）"
RUN2=$(req "$BASE/api/pump-stations/PS-02/drainage-runs" | jq '.[0]')
echo "$RUN2" | jq '{status, routeANetM3, routeBNetM3, unifiedNetM3}'
echo "$RUN2" | jq -e '.status == "PENDING_REVIEW" and (.unifiedNetM3 - 2160 | fabs < 0.5)' >/dev/null

step "8. PS-03 校准完整：两路线闭合（CLOSED）并附单泵分解"
RUN3=$(req "$BASE/api/pump-stations/PS-03/drainage-runs" | jq '.[0]')
echo "$RUN3" | jq '{status, routeANetM3, routeBNetM3, unifiedNetM3, perPump: (.perPumpJson | fromjson)}'
echo "$RUN3" | jq -e '.status == "CLOSED" and (.routeBNetM3 - 3240 | fabs < 0.5)
  and (.routeANetM3 - 3268.8 | fabs < 0.5) and (.unifiedNetM3 - 3268.8 | fabs < 0.6)
  and (.perPumpJson | fromjson | .[0].pumpGroupId == "PG-7")' >/dev/null

step "9. 运行效率报表：待核窗口不进入报表"
EFF2=$(req "$BASE/api/pump-stations/PS-02/efficiency?from=$FROM&to=$TO")
echo "$EFF2" | jq '{included: (.includedWindows | length), excluded: .excludedWindows}'
echo "$EFF2" | jq -e '.includedWindows | length == 0' >/dev/null
echo "$EFF2" | jq -e '.excludedWindows[0].status == "PENDING_REVIEW"
  and (.excludedWindows[0].reasons | contains(["CALIBRATION_ERROR_BREACH"]))' >/dev/null
EFF3=$(req "$BASE/api/pump-stations/PS-03/efficiency?from=$FROM&to=$TO")
echo "$EFF3" | jq '{included: (.includedWindows | length), totals}'
echo "$EFF3" | jq -e '.includedWindows | length == 1' >/dev/null
EFF1=$(req "$BASE/api/pump-stations/PS-01/efficiency?from=$FROM&to=$TO")
echo "$EFF1" | jq -e '.includedWindows | length == 0' >/dev/null
echo "PS-01 未闭合、PS-02 待核均不进入运行效率报表 ✓"

step "10. 流域汇总：只累计通过物理方向校验的净量"
SUMMARY=$(req "$BASE/api/basins/BASIN-01/drainage-summary?from=$FROM&to=$TO")
echo "$SUMMARY" | jq '{totalNetM3, includedWindows, stations, excludedWindows}'
echo "$SUMMARY" | jq -e '(.totalNetM3 - 5428.8 | fabs < 0.8) and .includedWindows == 2
  and (.excludedWindows | map(.stationCode) | contains(["PS-01"]))' >/dev/null

echo
echo "SMOKE_OK"
