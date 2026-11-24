package com.flood.statemigrate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/** 泵组校准曲线登记请求 */
public record CalibrationCurveReq(
        @NotBlank String pumpGroupId,
        @NotBlank String pointsJson,
        @PositiveOrZero double errorPct,
        @NotNull Instant validFrom,
        Instant validTo
) {
}
