package com.example.datainspector.statistics;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @PostMapping
    public StatisticResult analyze(@Valid @RequestBody StatisticRequest request) throws IOException, InterruptedException {
        return statisticsService.analyze(request);
    }

    @GetMapping("/top3")
    public List<StatisticResult> top3(@RequestParam String datasetId) {
        return statisticsService.top3(datasetId);
    }

    @PostMapping("/{statisticId}/saved")
    public StatisticResult markSaved(@PathVariable String statisticId) {
        return statisticsService.markSaved(statisticId);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return statisticsService.dashboard();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "resultCode", "400-1",
                        "msg", ex.getMessage()
                ));
    }
}
