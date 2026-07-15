package com.example.datainspector.analyze;

import java.util.List;
import java.util.Map;

public record DatasetPreview(
        String filename,
        String fileType,
        List<String> columns,
        int rowCount,
        List<Map<String, Object>> previewRows,
        Map<String, String> columnTypes
) {
}
