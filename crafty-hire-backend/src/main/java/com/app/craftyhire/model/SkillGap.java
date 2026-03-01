package com.app.craftyhire.model;

import lombok.*;

/**
 * Represents a skill that is required by the job but may not be
 * well-represented in the user's resume.
 *
 * When gaps are detected, the AdaptiveQuestionService generates follow-up
 * questions to determine if the user has transferable or adjacent experience
 * that can be incorporated into the generated resume.
 *
 * A gap is considered "resolved" when either:
 *   - The user confirms they have direct experience with the skill, or
 *   - The user provides a description of transferable experience.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillGap {

    /** The skill that is missing or underrepresented in the resume */
    private String skillName;

    /** How important this missing skill is for the role (0.0 to 1.0) */
    private double relevanceScore;

    /** Whether the user has indicated they have experience with this skill */
    private boolean hasExperience;

    /**
     * Description of transferable experience the user provided.
     * Populated when the user answers a follow-up question explaining
     * adjacent or transferable skills.
     */
    private String transferableExperience;

    /**
     * Whether this gap has been addressed (confirmed experience exists
     * or transferable experience has been provided).
     * Used by AdaptiveQuestionService to determine if all gaps are resolved
     * before proceeding to generation.
     */
    private boolean resolved;
}