package com.flood.statemigrate.web;

import com.flood.statemigrate.service.DrainageReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@Tag(name = "排水报表与流域汇总")
@RestController
public class DrainageReportController {

    private final DrainageReportService reportService;

    public DrainageReportController(DrainageReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "运行效率报表：仅闭合窗口计入；校准误差跨阈的待核窗口与未闭合窗口不进入报表")
    @GetMapping("/api/pump-stations/{stationCode}/efficiency")
    public Map<String, Object> efficiency(
            @PathVariable String stationCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.efficiency(stationCode, from, to);
    }

    @Operation(summary = "流域汇总：只累计通过物理方向校验且已有统一估计的净量")
    @GetMapping("/api/basins/{basinCode}/drainage-summary")
    public Map<String, Object> basinSummary(
            @PathVariable String basinCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.basinSummary(basinCode, from, to);
    }
}
