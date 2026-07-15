package com.example.datainspector.workspace;

import com.example.datainspector.statistics.StatisticResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record ArchiveItem(
        String id,
        String memberId,
        String datasetId,
        String datasetTitle,
        String provider,
        String detailUrl,
        String category,
        List<String> tags,
        String memo,
        String status,
        List<StatisticResult> statistics,
        Instant createdAt
) {
    public ArchiveItem withStatistic(StatisticResult statistic) {
        List<StatisticResult> next = new ArrayList<>(statistics);
        next.add(statistic);
        return new ArchiveItem(id, memberId, datasetId, datasetTitle, provider, detailUrl, category, tags, memo, status, next, createdAt);
    }
}
