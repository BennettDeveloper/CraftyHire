package com.app.craftyhire.dto.response;

import com.app.craftyhire.model.SkillScore;
import lombok.*;

import java.util.List;

/**
 * Response body returned after a job description is analyzed.
 *
 * Contains two parts:
 *   1. A human-readable analysis from Claude (role summary, responsibilities, ATS keywords, etc.)
 *   2. A ranked list of required skills with relevance scores for the skill bar UI
 *
 * The frontend displays the skills list as a visual bar chart (0–100%) and
 * shows the analysis text as context for the candidate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobAnalysisResponse {

    /**
     * Claude's structured analysis of the job description.
     * Covers: role summary, responsibilities, must-haves, nice-to-haves,
     * ATS keywords, and culture indicators.
     */
    private String analysis;

    /**
     * Ranked list of skills extracted from the job description.
     * Sorted by relevanceScore descending (highest importance first).
     * Used to render the skill importance bars on the frontend.
     */
    private List<SkillScore> skills;
}