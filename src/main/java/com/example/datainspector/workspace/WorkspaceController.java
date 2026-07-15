package com.example.datainspector.workspace;

import com.example.datainspector.github.GithubConnectRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping("/archives")
    public ArchiveItem createArchive(@Valid @RequestBody ArchiveCreateRequest request) {
        return workspaceService.createArchive(request);
    }

    @GetMapping("/archives")
    public List<ArchiveItem> archives() {
        return workspaceService.archives();
    }

    @PostMapping("/archives/{archiveId}/statistics")
    public ArchiveItem saveStatistic(
            @PathVariable String archiveId,
            @Valid @RequestBody ArchiveStatisticRequest request
    ) {
        return workspaceService.saveStatistic(archiveId, request.statisticId());
    }

    @PostMapping("/projects")
    public ProjectBoard createProject(@Valid @RequestBody ProjectCreateRequest request) {
        return workspaceService.createProject(request);
    }

    @GetMapping("/projects")
    public List<ProjectBoard> projects() {
        return workspaceService.projects();
    }

    @PostMapping("/projects/{projectId}/github")
    public ProjectBoard connectGithub(
            @PathVariable String projectId,
            @Valid @RequestBody GithubConnectRequest request
    ) throws IOException, InterruptedException {
        return workspaceService.connectGithub(projectId, request.repositoryUrl());
    }

    @PostMapping("/projects/{projectId}/datasets")
    public ProjectBoard addDatasetToProject(
            @PathVariable String projectId,
            @Valid @RequestBody ProjectDatasetRequest request
    ) {
        return workspaceService.addDatasetToProject(projectId, request.archiveId());
    }

    @GetMapping("/projects/{projectId}/readme")
    public Map<String, String> readmeMarkdown(@PathVariable String projectId) {
        return workspaceService.readmeMarkdown(projectId);
    }

    @GetMapping("/archives/{archiveId}/issue-draft")
    public Map<String, String> issueDraft(@PathVariable String archiveId) {
        return workspaceService.issueDraft(archiveId);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return workspaceService.dashboard();
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
