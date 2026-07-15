package com.example.datainspector.statistics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StatisticRequest(
        @NotBlank String publicDataPk,
        @NotBlank String fileDetailSn,
        @NotBlank String datasetTitle,
        @NotBlank String columnName,
        @NotNull StatisticType statType,
        boolean forceRefresh
) {
}
