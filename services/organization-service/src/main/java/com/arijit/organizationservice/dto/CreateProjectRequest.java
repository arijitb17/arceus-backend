package com.arijit.organizationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 150) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase, alphanumeric, hyphen-separated") String slug,
        @Size(max = 500) String description
) {}
