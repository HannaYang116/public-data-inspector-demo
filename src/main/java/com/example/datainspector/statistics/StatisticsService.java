package com.example.datainspector.statistics;

import com.example.datainspector.analyze.AnalyzeService;
import com.example.datainspector.portal.DataPortalService;
import com.example.datainspector.portal.DownloadedDataset;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StatisticsService {
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final DataPortalService dataPortalService;
    private final AnalyzeService analyzeService;
    private final Map<String, StatisticResult> cache = new ConcurrentHashMap<>();

    public StatisticsService(DataPortalService dataPortalService, AnalyzeService analyzeService) {
        this.dataPortalService = dataPortalService;
        this.analyzeService = analyzeService;
    }

    public StatisticResult analyze(StatisticRequest request) throws IOException, InterruptedException {
        String key = key(request.publicDataPk(), request.columnName(), request.statType());
        StatisticResult cached = cache.get(key);
        Instant now = Instant.now();

        if (!request.forceRefresh() && cached != null && cached.expiresAt().isAfter(now)) {
            StatisticResult updated = new StatisticResult(
                    cached.id(),
                    cached.datasetId(),
                    cached.datasetTitle(),
                    cached.columnName(),
                    cached.statType(),
                    cached.result(),
                    cached.analyzedAt(),
                    cached.expiresAt(),
                    cached.requestCount() + 1,
                    cached.savedCount(),
                    true
            );
            cache.put(key, updated);
            return updated;
        }

        DownloadedDataset dataset = dataPortalService.downloadFileDataset(request.publicDataPk(), request.fileDetailSn());
        Map<String, Object> result = analyzeService.analyzeColumnStatistic(
                dataset.filename(),
                dataset.bytes(),
                request.columnName(),
                request.statType().name()
        );

        StatisticResult analyzed = new StatisticResult(
                key,
                request.publicDataPk(),
                request.datasetTitle(),
                request.columnName(),
                request.statType(),
                result,
                now,
                now.plus(CACHE_TTL),
                cached == null ? 1 : cached.requestCount() + 1,
                cached == null ? 0 : cached.savedCount(),
                false
        );
        cache.put(key, analyzed);
        return analyzed;
    }

    public StatisticResult markSaved(String statisticId) {
        StatisticResult found = cache.get(statisticId);
        if (found == null) {
            throw new IllegalArgumentException("저장할 통계 결과를 찾을 수 없습니다.");
        }

        StatisticResult updated = new StatisticResult(
                found.id(),
                found.datasetId(),
                found.datasetTitle(),
                found.columnName(),
                found.statType(),
                found.result(),
                found.analyzedAt(),
                found.expiresAt(),
                found.requestCount(),
                found.savedCount() + 1,
                found.cached()
        );
        cache.put(statisticId, updated);
        return updated;
    }

    public StatisticResult get(String statisticId) {
        StatisticResult found = cache.get(statisticId);
        if (found == null) {
            throw new IllegalArgumentException("통계 결과를 찾을 수 없습니다.");
        }
        return found;
    }

    public List<StatisticResult> top3(String datasetId) {
        return cache
                .values()
                .stream()
                .filter(result -> result.datasetId().equals(datasetId))
                .sorted(Comparator
                        .comparingInt(StatisticResult::savedCount)
                        .thenComparingInt(StatisticResult::requestCount)
                        .reversed())
                .limit(3)
                .toList();
    }

    public Map<String, Object> dashboard() {
        List<Map<String, Object>> popularStatistics = cache
                .values()
                .stream()
                .sorted(Comparator
                        .comparingInt(StatisticResult::savedCount)
                        .thenComparingInt(StatisticResult::requestCount)
                        .reversed())
                .limit(5)
                .map(result -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("datasetTitle", result.datasetTitle());
                    item.put("columnName", result.columnName());
                    item.put("statType", result.statType());
                    item.put("requestCount", result.requestCount());
                    item.put("savedCount", result.savedCount());
                    return item;
                })
                .toList();

        return Map.of(
                "cachedStatisticCount", cache.size(),
                "popularStatistics", popularStatistics
        );
    }

    private String key(String datasetId, String columnName, StatisticType statType) {
        return datasetId + "::" + columnName + "::" + statType;
    }
}
