package com.app.craftyhire.model;

import lombok.*;

/**
 * Represents a follow-up question generated when a skill gap is detected.
 *
 * Example: If the job requires "Kubernetes" but the resume doesn't mention it,
 * the system might ask: "Do you have experience with Kubernetes or similar
 * container orchestration tools like Docker Swarm or Nomad?"
 *
 * Scalability note: 'questionType' drives frontend UI rendering.
 * "YES_NO" → radio buttons, "OPEN_ENDED" → text area.
 * Future types: "MULTIPLE_CHOICE", "RATING", "YEARS_OF_EXPERIENCE", etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    /** The skill gap this question is trying to address */
    private String skillName;

    /** The question text to display to the user */
    private String questionText;

    /**
     * The type of question, used to determine frontend input type.
     * Current values: "YES_NO", "OPEN_ENDED"
     */
    private String questionType;
}