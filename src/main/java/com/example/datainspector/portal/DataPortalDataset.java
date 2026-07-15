package com.example.datainspector.portal;

public record DataPortalDataset(
        String publicDataPk,
        String title,
        String description,
        String provider,
        String updatedDate,
        String detailUrl,
        String fileDetailSn
) {
}
