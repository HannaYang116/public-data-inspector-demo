package com.example.datainspector.github;

import jakarta.validation.constraints.NotBlank;

public record GithubConnectRequest(
        @NotBlank String repositoryUrl
) {
}
