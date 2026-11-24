package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.CalibrationCurve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalibrationCurveRepo extends JpaRepository<CalibrationCurve, Long> {

    List<CalibrationCurve> findByStationCodeOrderByPumpGroupIdAscValidFromAsc(String stationCode);

    List<CalibrationCurve> findByStationCodeAndPumpGroupIdOrderByValidFromAsc(String stationCode, String pumpGroupId);
}
