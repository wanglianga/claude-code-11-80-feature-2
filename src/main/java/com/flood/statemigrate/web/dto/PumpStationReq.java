package com.flood.statemigrate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** 泵站登记请求 */
public record PumpStationReq(
        @NotBlank String stationCode,
        @NotBlank String name,
        @NotBlank String basinCode,
        @Positive double pondAreaM2,
        @PositiveOrZero double levelTolM,
        @PositiveOrZero double meterErrorPct,
        @Positive double safetyErrorPct
) {
}
