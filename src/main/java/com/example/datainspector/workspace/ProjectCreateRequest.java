package com.example.datainspector.workspace;

import jakarta.validation.constraints.NotBlank;

public record ProjectCreateRequest(
        @NotBlank String title,
        String description
) {
}
