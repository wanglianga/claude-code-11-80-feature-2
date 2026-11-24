package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.DrainageRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DrainageRunRepo extends JpaRepository<DrainageRun, Long> {

    List<DrainageRun> findByStationCodeOrderByWindowStartAsc(String stationCode);

    List<DrainageRun> findByStationCodeAndWindowStartAndWindowEnd(String stationCode, Instant windowStart, Instant windowEnd);

    List<DrainageRun> findByStationCodeAndWindowStartGreaterThanEqualAndWindowEndLessThanEqualOrderByWindowStartAsc(
            String stationCode, Instant from, Instant to);
}
