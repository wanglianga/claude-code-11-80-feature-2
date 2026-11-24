package com.flood.statemigrate.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** 排水贡献核算窗口请求 */
public record DrainageRunReq(
        @NotNull Instant windowStart,
        @NotNull Instant windowEnd
) {
}
