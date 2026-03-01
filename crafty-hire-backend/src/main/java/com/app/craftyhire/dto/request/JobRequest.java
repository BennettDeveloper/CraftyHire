package com.app.craftyhire.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for POST /api/resume/analyze-job.
 * The user pastes the full job description text directly.
 *
 * Note: We intentionally use plain text rather than a URL because
 * scraping job boards (LinkedIn, Greenhouse, etc.) is unreliable.
 * This gives users full control and works with any job posting source.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobRequest {

    @NotBlank(message = "Job description is required")
    private String jobDescription;
}