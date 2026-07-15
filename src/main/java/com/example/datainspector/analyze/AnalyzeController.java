package com.example.datainspector.analyze;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class AnalyzeController {
    private final AnalyzeService analyzeService;

    public AnalyzeController(AnalyzeService analyzeService) {
        this.analyzeService = analyzeService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DatasetPreview analyzeUpload(
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        return analyzeService.analyzeUpload(file);
    }

    @PostMapping("/url")
    public DatasetPreview analyzeUrl(
            @Valid @RequestBody UrlAnalyzeRequest request
    ) throws IOException, InterruptedException {
        return analyzeService.analyzeUrl(request.url());
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

    public record UrlAnalyzeRequest(
            @NotBlank String url
    ) {
    }
}
