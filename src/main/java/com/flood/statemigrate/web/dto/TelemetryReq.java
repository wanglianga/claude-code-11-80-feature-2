package com.flood.statemigrate.web.dto;

import com.flood.statemigrate.domain.TelemetryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** 站端遥测写入请求 */
public record TelemetryReq(
        @NotNull TelemetryKind kind,
        @NotBlank String assetId,
        @NotNull Instant eventTime,
        @NotBlank String payloadJson
) {
}
