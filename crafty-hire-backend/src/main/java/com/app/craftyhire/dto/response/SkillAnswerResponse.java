package com.app.craftyhire.dto.response;

import com.app.craftyhire.model.SkillGap;
import lombok.*;

/**
 * Response body returned after the user answers a skill gap follow-up question.
 *
 * The frontend uses updatedGap to replace the corresponding gap in its local state,
 * and checks 'resolved' to confirm this gap is now addressed.
 *
 * The frontend tracks overall resolution state locally — it calls allGapsResolved()
 * by checking if all gaps in its state have resolved=true, then enables generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillAnswerResponse {

    /**
     * The skill gap updated with the user's answer.
     * Contains the transferableExperience and hasExperience fields populated
     * from the user's response. This is passed to Claude during generation.
     */
    private SkillGap updatedGap;

    /**
     * Whether this specific gap is now resolved.
     * Will always be true in the current implementation — any non-empty
     * answer resolves the gap.
     */
    private boolean resolved;
}