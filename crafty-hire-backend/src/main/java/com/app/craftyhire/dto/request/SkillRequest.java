package com.app.craftyhire.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for POST /api/skills/analyze.
 * Used to identify skill gaps between a job description and the user's resume.
 * The response will contain a list of SkillGap objects and any generated questions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillRequest {

    @NotBlank(message = "Job description is required")
    private String jobDescription;

    @NotBlank(message = "Resume text is required")
    private String resumeText;
}