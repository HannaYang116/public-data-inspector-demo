package com.example.datainspector.workspace;

import com.example.datainspector.github.GithubRepositoryInfo;
import com.example.datainspector.github.GithubService;
import com.example.datainspector.statistics.StatisticResult;
import com.example.datainspector.statistics.StatisticsService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkspaceService {
    private static final String DEMO_MEMBER_ID = "demo";

    private final StatisticsService statisticsService;
    private final GithubService githubService;
    private final Map<String, ArchiveItem> archives = new ConcurrentHashMap<>();
    private final Map<String, ProjectBoard> projects = new ConcurrentHashMap<>();

    public WorkspaceService(StatisticsService statisticsService, GithubService githubService) {
        this.statisticsService = statisticsService;
        this.githubService = githubService;
    }

    public ArchiveItem createArchive(ArchiveCreateRequest request) {
        ArchiveItem item = new ArchiveItem(
                UUID.randomUUID().toString(),
                DEMO_MEMBER_ID,
                request.datasetId(),
                request.datasetTitle(),
                nullToBlank(request.provider()),
                nullToBlank(request.detailUrl()),
                nullToDefault(request.category(), "미분류"),
                request.tags() == null ? List.of() : request.tags(),
                nullToBlank(request.memo()),
                nullToDefault(request.status(), "후보"),
                new ArrayList<>(),
                Instant.now()
        );
        archives.put(item.id(), item);
        return item;
    }

    public List<ArchiveItem> archives() {
        return archives
                .values()
                .stream()
                .filter(item -> item.memberId().equals(DEMO_MEMBER_ID))
                .sorted(Comparator.comparing(ArchiveItem::createdAt).reversed())
                .toList();
    }

    public ArchiveItem saveStatistic(String archiveId, String statisticId) {
        ArchiveItem archive = requireArchive(archiveId);
        StatisticResult statistic = statisticsService.markSaved(statisticId);
        ArchiveItem updated = archive.withStatistic(statistic);
        archives.put(archiveId, updated);
        return updated;
    }

    public ProjectBoard createProject(ProjectCreateRequest request) {
        ProjectBoard project = new ProjectBoard(
                UUID.randomUUID().toString(),
                DEMO_MEMBER_ID,
                request.title(),
                nullToBlank(request.description()),
                null,
                new ArrayList<>(),
                Instant.now()
        );
        projects.put(project.id(), project);
        return project;
    }

    public List<ProjectBoard> projects() {
        return projects
                .values()
                .stream()
                .filter(project -> project.memberId().equals(DEMO_MEMBER_ID))
                .sorted(Comparator.comparing(ProjectBoard::createdAt).reversed())
                .toList();
    }

    public ProjectBoard connectGithub(String projectId, String repositoryUrl) throws IOException, InterruptedException {
        ProjectBoard project = requireProject(projectId);
        GithubRepositoryInfo repositoryInfo = githubService.fetchPublicRepository(repositoryUrl);
        ProjectBoard updated = project.withGithub(repositoryInfo);
        projects.put(projectId, updated);
        return updated;
    }

    public ProjectBoard addDatasetToProject(String projectId, String archiveId) {
        ProjectBoard project = requireProject(projectId);
        ArchiveItem archive = requireArchive(archiveId);
        ProjectBoard updated = project.withDataset(archive);
        projects.put(projectId, updated);
        return updated;
    }

    public Map<String, String> readmeMarkdown(String projectId) {
        ProjectBoard project = requireProject(projectId);
        StringBuilder markdown = new StringBuilder();
        markdown.append("## 사용 데이터\n\n");

        for (ArchiveItem item : project.datasets()) {
            markdown.append("### ").append(item.datasetTitle()).append("\n");
            markdown.append("- 출처: ").append(item.detailUrl().isBlank() ? "공공데이터포털" : item.detailUrl()).append("\n");
            markdown.append("- 제공기관: ").append(item.provider().isBlank() ? "미상" : item.provider()).append("\n");
            markdown.append("- 카테고리: ").append(item.category()).append("\n");
            markdown.append("- 활용 상태: ").append(item.status()).append("\n");
            if (!item.memo().isBlank()) {
                markdown.append("- 메모: ").append(item.memo()).append("\n");
            }
            if (!item.statistics().isEmpty()) {
                markdown.append("- 저장한 통계: ");
                markdown.append(item.statistics().stream().map(stat -> stat.columnName() + " " + stat.statType()).toList());
                markdown.append("\n");
            }
            markdown.append("\n");
        }

        return Map.of("markdown", markdown.toString());
    }

    public Map<String, String> issueDraft(String archiveId) {
        ArchiveItem item = requireArchive(archiveId);
        String title = "[DATA] " + item.datasetTitle() + " 데이터 검토";
        String body = """
                ## 데이터 정보
                - 데이터명: %s
                - 제공기관: %s
                - 출처: %s

                ## 작업 내용
                - 주요 컬럼 확인
                - 결측치 및 이상값 확인
                - 서비스에서 사용할 컬럼 선정
                - 필요한 통계 분석 결과 정리
                """.formatted(
                item.datasetTitle(),
                item.provider().isBlank() ? "미상" : item.provider(),
                item.detailUrl().isBlank() ? "공공데이터포털" : item.detailUrl()
        );

        return Map.of(
                "title", title,
                "body", body
        );
    }

    public Map<String, Object> dashboard() {
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (ArchiveItem item : archives()) {
            categoryCounts.put(item.category(), categoryCounts.getOrDefault(item.category(), 0L) + 1);
        }

        return Map.of(
                "archiveCount", archives.size(),
                "projectCount", projects.size(),
                "categoryCounts", categoryCounts,
                "statistics", statisticsService.dashboard()
        );
    }

    private ArchiveItem requireArchive(String archiveId) {
        ArchiveItem archive = archives.get(archiveId);
        if (archive == null || !archive.memberId().equals(DEMO_MEMBER_ID)) {
            throw new IllegalArgumentException("아카이브를 찾을 수 없습니다.");
        }
        return archive;
    }

    private ProjectBoard requireProject(String projectId) {
        ProjectBoard project = projects.get(projectId);
        if (project == null || !project.memberId().equals(DEMO_MEMBER_ID)) {
            throw new IllegalArgumentException("프로젝트를 찾을 수 없습니다.");
        }
        return project;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
