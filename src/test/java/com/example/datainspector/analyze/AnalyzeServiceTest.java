package com.example.datainspector.analyze;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyzeServiceTest {
    private final AnalyzeService analyzeService = new AnalyzeService();

    @Test
    void analyzeCsvUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "parks.csv",
                "text/csv",
                """
                        name,city,score
                        중앙공원,서울,4.5
                        호수공원,고양,5
                        """.getBytes(StandardCharsets.UTF_8)
        );

        DatasetPreview preview = analyzeService.analyzeUpload(file);

        assertThat(preview.fileType()).isEqualTo("CSV");
        assertThat(preview.columns()).containsExactly("name", "city", "score");
        assertThat(preview.rowCount()).isEqualTo(2);
        assertThat(preview.previewRows()).hasSize(2);
        assertThat(preview.columnTypes()).containsEntry("score", "number");
    }

    @Test
    void analyzeJsonUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "libraries.json",
                "application/json",
                """
                        [
                          {"name": "A도서관", "seatCount": 120},
                          {"name": "B도서관", "seatCount": 80}
                        ]
                        """.getBytes(StandardCharsets.UTF_8)
        );

        DatasetPreview preview = analyzeService.analyzeUpload(file);

        assertThat(preview.fileType()).isEqualTo("JSON");
        assertThat(preview.columns()).containsExactly("name", "seatCount");
        assertThat(preview.rowCount()).isEqualTo(2);
        assertThat(preview.columnTypes()).containsEntry("seatCount", "number");
    }

    @Test
    void blockLocalUrl() {
        assertThatThrownBy(() -> analyzeService.analyzeUrl("http://127.0.0.1/data.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("내부망");
    }
}
