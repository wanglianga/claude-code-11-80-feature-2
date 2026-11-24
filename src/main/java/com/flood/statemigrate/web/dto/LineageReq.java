package com.flood.statemigrate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** 设备沿革绑定请求：控制器/泵组/流量计绑定到有效区间 */
public record LineageReq(
        @NotBlank String controllerId,
        String pumpGroupId,
        @NotBlank String flowMeterId,
        @NotBlank String outletId,
        @NotNull Instant effectiveFrom,
        Instant effectiveTo
) {
}
