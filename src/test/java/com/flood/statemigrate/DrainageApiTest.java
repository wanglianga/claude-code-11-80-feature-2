package com.flood.statemigrate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 泵站排水核算接口层冒烟：鉴权、登记、校验与核算触发 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DrainageApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void drainageApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/pump-stations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerLineageCurveAndTelemetry() throws Exception {
        mockMvc.perform(post("/api/pump-stations").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"stationCode":"PS-API","name":"接口站","basinCode":"BASIN-API",
                                 "pondAreaM2":1000,"levelTolM":0.02,"meterErrorPct":0.05,"safetyErrorPct":0.15}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stationCode").value("PS-API"));

        mockMvc.perform(post("/api/pump-stations/PS-API/lineage").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"controllerId":"CTL-A","pumpGroupId":"PG-A","flowMeterId":"FM-A",
                                 "outletId":"OUT-A","effectiveFrom":"2026-11-20T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pumpGroupId").value("PG-A"));

        mockMvc.perform(post("/api/pump-stations/PS-API/calibration-curves")
                        .with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"pumpGroupId":"PG-A","pointsJson":"[{\\"headM\\":0,\\"flowM3s\\":1.0},{\\"headM\\":5,\\"flowM3s\\":1.0}]",
                                 "errorPct":0.05,"validFrom":"2026-11-20T00:00:00Z"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pump-stations/PS-API/telemetry").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"kind":"WATER_LEVEL_UPSTREAM","assetId":"LV-UP",
                                 "eventTime":"2026-11-24T00:00:00Z","payloadJson":"{\\"levelM\\":2.5}"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("WATER_LEVEL_UPSTREAM"));

        mockMvc.perform(get("/api/pump-stations").with(httpBasic("operator", "operator123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stationCode").value("PS-API"));
    }

    @Test
    void invalidGateStateRejected() throws Exception {
        mockMvc.perform(post("/api/pump-stations").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"stationCode":"PS-API-2","name":"校验站","basinCode":"BASIN-API",
                                 "pondAreaM2":1000,"levelTolM":0.02,"meterErrorPct":0.05,"safetyErrorPct":0.15}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/pump-stations/PS-API-2/telemetry").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"kind":"GATE_STATE","assetId":"GATE-9",
                                 "eventTime":"2026-11-24T00:00:00Z","payloadJson":"{\\"state\\":\\"HALF\\"}"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runOnMissingStationIsNotFound() throws Exception {
        mockMvc.perform(post("/api/pump-stations/PS-MISSING/drainage-runs")
                        .with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"windowStart":"2026-11-24T00:00:00Z","windowEnd":"2026-11-24T02:00:00Z"}
                                """))
                .andExpect(status().isNotFound());
    }
}
