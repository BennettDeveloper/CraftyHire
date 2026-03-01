package com.app.craftyhire.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for POST /api/skills/answer.
 * Submitted when the user answers a follow-up skill gap question.
 * The answer is processed by AdaptiveQuestionService to mark the gap as resolved
 * and enrich the context passed to Claude for document generation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillAnswerRequest {

    @NotBlank(message = "Skill name is required")
    private String skillName;

    /** Whether the user has direct or transferable experience with this skill */
    private boolean hasExperience;

    /**
     * The user's free-text description of their experience.
     * If hasExperience is true: describe direct experience.
     * If hasExperience is false: describe adjacent or transferable skills.
     */
    private String description;
}