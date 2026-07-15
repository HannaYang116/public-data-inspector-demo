package com.example.datainspector.portal;

public record DownloadedDataset(
        String datasetId,
        String filename,
        byte[] bytes
) {
}
