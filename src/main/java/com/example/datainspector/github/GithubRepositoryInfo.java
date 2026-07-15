package com.example.datainspector.github;

import java.time.Instant;

public record GithubRepositoryInfo(
        String owner,
        String name,
        String fullName,
        String description,
        String htmlUrl,
        String language,
        int stargazersCount,
        Instant updatedAt
) {
}
