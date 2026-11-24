package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.EquipmentQualityRecord;
import com.flood.statemigrate.domain.QualityCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentQualityRecordRepo extends JpaRepository<EquipmentQualityRecord, Long> {

    List<EquipmentQualityRecord> findByStationCodeOrderByCreatedAtAsc(String stationCode);

    List<EquipmentQualityRecord> findByRunIdOrderByCreatedAtAsc(Long runId);

    void deleteByRunId(Long runId);

    List<EquipmentQualityRecord> findByStationCodeAndCategoryOrderByCreatedAtAsc(
            String stationCode, QualityCategory category);
}
