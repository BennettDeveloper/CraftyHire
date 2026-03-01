package com.app.craftyhire.service;

import com.app.craftyhire.model.Question;
import com.app.craftyhire.model.SkillGap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the adaptive follow-up question flow for skill gap resolution.
 *
 * When a candidate's resume doesn't clearly demonstrate a required skill,
 * this service:
 *   1. Generates targeted follow-up questions (via ClaudeService) for each gap
 *   2. Processes the candidate's answers and marks gaps as resolved
 *   3. Tracks whether all gaps have been addressed before generation
 *
 * Questions are prioritized by skill relevance — the most important gaps
 * are asked first to minimize friction if the user stops early.
 *
 * Scalability note: Currently generates one question per gap. In the future,
 * this could support multi-turn conversations for complex skill gaps, or
 * skip questions for gaps below a configurable relevance threshold.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveQuestionService {

    private final ClaudeService claudeService;

    // Only generate questions for skills above this importance threshold.
    // Gaps below this level are too minor to ask about.
    private static final double QUESTION_RELEVANCE_THRESHOLD = 0.4;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Generates a follow-up question for each unresolved skill gap.
     *
     * Questions are sorted by relevance score (highest first) so the user
     * is asked about the most critical gaps early in the flow.
     *
     * Only gaps above the QUESTION_RELEVANCE_THRESHOLD are included —
     * minor gaps are skipped to avoid overwhelming the user.
     *
     * Note: Each gap generates one Claude API call. For large gap lists,
     * consider batching in a future optimization.
     *
     * @param gaps list of skill gaps identified by JobAnalysisService
     * @return list of Question objects to present to the user
     */
    public List<Question> identifyGaps(List<SkillGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return List.of();
        }

        List<Question> questions = gaps.stream()
                .filter(gap -> !gap.isResolved())
                .filter(gap -> gap.getRelevanceScore() >= QUESTION_RELEVANCE_THRESHOLD)
                .sorted(Comparator.comparingDouble(SkillGap::getRelevanceScore).reversed())
                .map(gap -> {
                    String questionText = claudeService.askFollowUpQuestion(gap);
                    return Question.builder()
                            .skillName(gap.getSkillName())
                            .questionText(questionText)
                            .questionType(determineQuestionType(gap))
                            .build();
                })
                .collect(Collectors.toList());

        log.info("Generated {} follow-up questions for {} skill gaps", questions.size(), gaps.size());
        return questions;
    }

    /**
     * Processes the user's answer to a skill gap question and returns
     * an updated SkillGap marked as resolved.
     *
     * Any non-empty answer resolves the gap — the candidate has provided
     * context that Claude can incorporate during generation. The actual
     * framing strategy is handled by ClaudeService during document generation.
     *
     * @param question the question that was answered
     * @param answer   the candidate's free-text answer
     * @return an updated SkillGap with resolved=true and the answer stored
     */
    public SkillGap processAnswer(Question question, String answer) {
        boolean hasDirectExperience = determineIfDirectExperience(answer);

        SkillGap resolved = SkillGap.builder()
                .skillName(question.getSkillName())
                // Relevance score preserved from original gap (set by caller if needed)
                .relevanceScore(0.0)
                .hasExperience(hasDirectExperience)
                .transferableExperience(answer != null ? answer.trim() : "")
                .resolved(true)
                .build();

        log.debug("Processed answer for '{}': hasExperience={}, resolved=true",
                question.getSkillName(), hasDirectExperience);

        return resolved;
    }

    /**
     * Returns true if all gaps in the list have been resolved.
     * Used to determine whether the user can proceed to document generation.
     *
     * @param gaps the current list of skill gaps
     * @return true if the list is empty or every gap has resolved=true
     */
    public boolean allGapsResolved(List<SkillGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return true;
        }
        boolean allResolved = gaps.stream().allMatch(SkillGap::isResolved);
        log.debug("Gap resolution status: {}/{} resolved",
                gaps.stream().filter(SkillGap::isResolved).count(), gaps.size());
        return allResolved;
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Determines the question type based on the skill's relevance score.
     *
     * High-importance skills (>= 70%) get OPEN_ENDED questions — the candidate
     * should explain their experience in detail so Claude has rich context.
     *
     * Lower-importance skills get YES_NO questions — a simple confirmation
     * is enough context for Claude to handle the gap gracefully.
     */
    private String determineQuestionType(SkillGap gap) {
        return gap.getRelevanceScore() >= 0.7 ? "OPEN_ENDED" : "YES_NO";
    }

    /**
     * Makes a best-effort determination of whether the answer describes
     * direct experience (true) or no/transferable experience (false).
     *
     * Used to set the hasExperience flag on the resolved gap for Claude's context.
     * Claude will interpret the full answer text regardless of this flag.
     */
    private boolean determineIfDirectExperience(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase().trim();
        // Simple negative patterns — if the answer clearly says no, mark false
        return !lower.matches("^(no|nope|never|n/a|none|not really|not at all|i don't|i do not).*");
    }
}