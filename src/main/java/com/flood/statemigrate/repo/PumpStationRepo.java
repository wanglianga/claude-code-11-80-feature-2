package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.PumpStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PumpStationRepo extends JpaRepository<PumpStation, Long> {

    Optional<PumpStation> findByStationCode(String stationCode);

    List<PumpStation> findByBasinCodeOrderByStationCodeAsc(String basinCode);

    List<PumpStation> findAllByOrderByStationCodeAsc();
}
