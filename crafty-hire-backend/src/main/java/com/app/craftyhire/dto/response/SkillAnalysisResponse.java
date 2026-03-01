package com.app.craftyhire.dto.response;

import com.app.craftyhire.model.Question;
import com.app.craftyhire.model.SkillGap;
import lombok.*;

import java.util.List;

/**
 * Response body returned after comparing a resume to a job's required skills.
 *
 * The frontend uses this to:
 *   1. Display the skill gaps so the candidate understands what's missing
 *   2. Show follow-up questions one by one for the candidate to answer
 *   3. Check allResolved to decide whether to enable the "Generate" buttons
 *
 * If allResolved is true immediately (no significant gaps found), the frontend
 * can skip the question flow and go straight to generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillAnalysisResponse {

    /**
     * Skills required by the job that are not clearly demonstrated in the resume.
     * These are the gaps the follow-up questions aim to address.
     */
    private List<SkillGap> skillGaps;

    /**
     * Follow-up questions generated for each unresolved gap.
     * Ordered by skill relevance (most important gap first).
     * May be empty if no significant gaps were found.
     */
    private List<Question> questions;

    /**
     * Whether all skill gaps have already been resolved.
     * True if: no significant gaps were found, or all gaps already have answers.
     * When true, the frontend can enable the "Generate Resume" / "Generate Cover Letter" buttons.
     */
    private boolean allResolved;
}