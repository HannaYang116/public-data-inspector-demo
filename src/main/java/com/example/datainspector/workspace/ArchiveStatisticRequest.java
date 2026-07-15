package com.example.datainspector.workspace;

import jakarta.validation.constraints.NotBlank;

public record ArchiveStatisticRequest(
        @NotBlank String statisticId
) {
}
