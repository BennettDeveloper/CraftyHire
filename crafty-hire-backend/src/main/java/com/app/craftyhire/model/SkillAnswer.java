package com.app.craftyhire.model;

import lombok.*;

/**
 * Represents the user's answer to a follow-up skill gap question.
 *
 * Collected when the system asks the user about a skill not clearly shown
 * in their resume. These answers are passed to ClaudeService so the
 * generated resume and cover letter can incorporate the additional context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillAnswer {

    /** The skill this answer is addressing */
    private String skillName;

    /** Whether the user has experience with this skill */
    private boolean hasExperience;

    /**
     * The user's description of their experience.
     * If hasExperience is true: describe direct experience.
     * If hasExperience is false: describe transferable/adjacent skills.
     * Claude uses this to naturally weave the experience into the documents.
     */
    private String description;
}