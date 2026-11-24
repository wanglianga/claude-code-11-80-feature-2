package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.StationTelemetry;
import com.flood.statemigrate.domain.TelemetryKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StationTelemetryRepo extends JpaRepository<StationTelemetry, Long> {

    List<StationTelemetry> findByStationCodeAndEventTimeBetweenOrderByEventTimeAsc(
            String stationCode, Instant from, Instant to);

    List<StationTelemetry> findByStationCodeAndKindAndEventTimeBetweenOrderByEventTimeAsc(
            String stationCode, TelemetryKind kind, Instant from, Instant to);

    @Query("select distinct t.assetId from StationTelemetry t where t.stationCode=:stationCode and t.kind=:kind")
    List<String> distinctAssets(String stationCode, TelemetryKind kind);

    /** 窗口起点之前某设备最近一次读数（建立分段初始状态） */
    Optional<StationTelemetry> findFirstByStationCodeAndKindAndAssetIdAndEventTimeLessThanEqualOrderByEventTimeDesc(
            String stationCode, TelemetryKind kind, String assetId, Instant at);
}
