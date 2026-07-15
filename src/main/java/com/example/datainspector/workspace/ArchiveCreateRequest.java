package com.example.datainspector.workspace;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ArchiveCreateRequest(
        @NotBlank String datasetId,
        @NotBlank String datasetTitle,
        String provider,
        String detailUrl,
        String category,
        List<String> tags,
        String memo,
        String status
) {
}
