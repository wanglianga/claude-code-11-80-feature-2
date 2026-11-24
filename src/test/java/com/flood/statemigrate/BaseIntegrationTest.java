package com.flood.statemigrate;

import com.flood.statemigrate.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
abstract class BaseIntegrationTest {

    @Autowired protected StreamDefinitionRepo streamRepo;
    @Autowired protected RulePackageRepo rulePackageRepo;
    @Autowired protected CheckpointManifestRepo checkpointRepo;
    @Autowired protected StreamEventRepo eventRepo;
    @Autowired protected StateProofRepo proofRepo;
    @Autowired protected MigrationPlanRepo planRepo;
    @Autowired protected DualTrackRunRepo runRepo;
    @Autowired protected DualTrackDiffRepo diffRepo;
    @Autowired protected SwitchGateRepo gateRepo;
    @Autowired protected RollbackRecordRepo rollbackRepo;
    @Autowired protected EvidenceEntryRepo evidenceRepo;
    @Autowired protected PumpStationRepo pumpStationRepo;
    @Autowired protected EquipmentLineageRepo lineageRepo;
    @Autowired protected CalibrationCurveRepo curveRepo;
    @Autowired protected StationTelemetryRepo telemetryRepo;
    @Autowired protected DrainageRunRepo drainageRunRepo;
    @Autowired protected EquipmentQualityRecordRepo qualityRecordRepo;

    protected ScenarioBuilder scenario;

    @BeforeEach
    void cleanDb() {
        diffRepo.deleteAll();
        runRepo.deleteAll();
        evidenceRepo.deleteAll();
        planRepo.deleteAll();
        gateRepo.deleteAll();
        rollbackRepo.deleteAll();
        checkpointRepo.deleteAll();
        eventRepo.deleteAll();
        proofRepo.deleteAll();
        rulePackageRepo.deleteAll();
        streamRepo.deleteAll();
        qualityRecordRepo.deleteAll();
        drainageRunRepo.deleteAll();
        telemetryRepo.deleteAll();
        curveRepo.deleteAll();
        lineageRepo.deleteAll();
        pumpStationRepo.deleteAll();
        scenario = new ScenarioBuilder(streamRepo, rulePackageRepo, eventRepo, checkpointRepo);
    }
}
