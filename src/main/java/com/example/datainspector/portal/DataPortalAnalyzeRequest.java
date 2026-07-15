package com.example.datainspector.portal;

import jakarta.validation.constraints.NotBlank;

public record DataPortalAnalyzeRequest(
        @NotBlank String publicDataPk,
        String fileDetailSn
) {
    public String normalizedFileDetailSn() {
        return fileDetailSn == null || fileDetailSn.isBlank() ? "1" : fileDetailSn;
    }
}
