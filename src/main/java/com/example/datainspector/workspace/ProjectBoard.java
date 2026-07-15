package com.example.datainspector.workspace;

import com.example.datainspector.github.GithubRepositoryInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record ProjectBoard(
        String id,
        String memberId,
        String title,
        String description,
        GithubRepositoryInfo githubRepository,
        List<ArchiveItem> datasets,
        Instant createdAt
) {
    public ProjectBoard withGithub(GithubRepositoryInfo repositoryInfo) {
        return new ProjectBoard(id, memberId, title, description, repositoryInfo, datasets, createdAt);
    }

    public ProjectBoard withDataset(ArchiveItem archiveItem) {
        List<ArchiveItem> next = new ArrayList<>(datasets);
        next.add(archiveItem);
        return new ProjectBoard(id, memberId, title, description, githubRepository, next, createdAt);
    }
}
