package com.example.datainspector.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GithubService {
    private static final Pattern REPO_PATTERN = Pattern.compile("github\\.com/([^/]+)/([^/#?]+)");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public GithubRepositoryInfo fetchPublicRepository(String repositoryUrl) throws IOException, InterruptedException {
        Matcher matcher = REPO_PATTERN.matcher(repositoryUrl);
        if (!matcher.find()) {
            throw new IllegalArgumentException("GitHub 레포 URL 형식이 올바르지 않습니다.");
        }

        String owner = matcher.group(1);
        String repo = matcher.group(2).replace(".git", "");
        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo;

        HttpRequest request = HttpRequest
                .newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "public-data-inspector-demo")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("공개 GitHub 레포를 찾을 수 없습니다.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("GitHub 레포 조회에 실패했습니다. HTTP 상태: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return new GithubRepositoryInfo(
                owner,
                root.path("name").asText(repo),
                root.path("full_name").asText(owner + "/" + repo),
                root.path("description").asText(""),
                root.path("html_url").asText(repositoryUrl),
                root.path("language").asText(""),
                root.path("stargazers_count").asInt(0),
                Instant.parse(root.path("updated_at").asText())
        );
    }
}
