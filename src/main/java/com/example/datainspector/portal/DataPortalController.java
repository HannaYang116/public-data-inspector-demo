package com.example.datainspector.portal;

import com.example.datainspector.analyze.DatasetPreview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data-go-kr")
public class DataPortalController {
    private final DataPortalService dataPortalService;

    public DataPortalController(DataPortalService dataPortalService) {
        this.dataPortalService = dataPortalService;
    }

    @GetMapping("/search")
    public List<DataPortalDataset> search(
            @RequestParam @NotBlank String keyword
    ) throws IOException, InterruptedException {
        return dataPortalService.searchFileDatasets(keyword);
    }

    @PostMapping("/analyze")
    public DatasetPreview analyze(
            @Valid @RequestBody DataPortalAnalyzeRequest request
    ) throws IOException, InterruptedException {
        return dataPortalService.analyzeFileDataset(request.publicDataPk(), request.normalizedFileDetailSn());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "resultCode", "400-1",
                        "msg", ex.getMessage()
                ));
    }
}
