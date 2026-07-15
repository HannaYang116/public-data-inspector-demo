package com.example.datainspector.statistics;

import java.time.Instant;
import java.util.Map;

public record StatisticResult(
        String id,
        String datasetId,
        String datasetTitle,
        String columnName,
        StatisticType statType,
        Map<String, Object> result,
        Instant analyzedAt,
        Instant expiresAt,
        int requestCount,
        int savedCount,
        boolean cached
) {
}
