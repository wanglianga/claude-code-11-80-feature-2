package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.EquipmentLineage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentLineageRepo extends JpaRepository<EquipmentLineage, Long> {

    List<EquipmentLineage> findByStationCodeOrderByEffectiveFromAsc(String stationCode);
}
