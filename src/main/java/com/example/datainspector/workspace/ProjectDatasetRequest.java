package com.example.datainspector.workspace;

import jakarta.validation.constraints.NotBlank;

public record ProjectDatasetRequest(
        @NotBlank String archiveId
) {
}
