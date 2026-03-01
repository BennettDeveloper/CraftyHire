package com.app.craftyhire.model;

import lombok.*;

/**
 * Represents a skill extracted from a job description along with
 * how relevant/important that skill is for the role.
 *
 * Example: { skillName: "React", relevanceScore: 0.92, category: "Frontend" }
 *
 * Displayed to the user as a list of skill bars (0% – 100%) so they can
 * see at a glance what the job prioritizes before generating their resume.
 *
 * Scalability note: 'category' allows grouping skills by type
 * (e.g., Technical, Soft Skills, Tools) for richer UI display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillScore {

    /** Name of the skill as extracted from the job description */
    private String skillName;

    /**
     * How important this skill is for the role (0.0 to 1.0).
     * Displayed as a percentage on the frontend (e.g., 0.92 → 92%).
     */
    private double relevanceScore;

    /**
     * Category grouping for this skill.
     * Examples: "Technical", "Soft Skills", "Tools", "Domain Knowledge"
     */
    private String category;
}