package com.app.craftyhire.controller;

import com.app.craftyhire.dto.request.SkillAnswerRequest;
import com.app.craftyhire.dto.request.SkillRequest;
import com.app.craftyhire.dto.response.SkillAnalysisResponse;
import com.app.craftyhire.dto.response.SkillAnswerResponse;
import com.app.craftyhire.model.Question;
import com.app.craftyhire.model.SkillGap;
import com.app.craftyhire.model.SkillScore;
import com.app.craftyhire.service.AdaptiveQuestionService;
import com.app.craftyhire.service.JobAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for skill gap analysis and adaptive follow-up questions.
 *
 * These endpoints are called after the user has both parsed their resume
 * and analyzed the job description. The flow is:
 *
 *   1. POST /api/skills/analyze  — compare resume to job, get gaps + questions
 *   2. POST /api/skills/answer   — answer one question at a time
 *   3. Repeat step 2 until the frontend sees allResolved=true
 *   4. Proceed to generate-resume / generate-cover-letter
 *
 * This API is stateless: the frontend maintains the list of gaps and
 * passes updated state back with each generate request.
 * No server-side session storage is required.
 */
@Slf4j
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final JobAnalysisService jobAnalysisService;
    private final AdaptiveQuestionService adaptiveQuestionService;

    /**
     * Analyze skill gaps between a job description and the candidate's resume.
     *
     * Steps:
     *   1. Extract required skills from the job description (via Claude)
     *   2. Compare skills against the resume to find gaps (via Claude semantic matching)
     *   3. Generate follow-up questions for each unresolved gap (via Claude)
     *   4. Return gaps, questions, and overall resolution status
     *
     * If allResolved is true in the response, the user can skip the question
     * flow and go straight to generation — their resume already covers the job well.
     *
     * Returns 200 OK with { skillGaps, questions, allResolved }.
     *
     * @param request the job description and parsed resume text
     */
    @PostMapping("/analyze")
    public ResponseEntity<SkillAnalysisResponse> getSkillAnalysis(
            @Valid @RequestBody SkillRequest request) {

        log.info("Analyzing skill gaps for resume ({} chars) vs job ({} chars)",
                request.getResumeText().length(), request.getJobDescription().length());

        // Extract and rank required skills from the job description
        List<SkillScore> skills = jobAnalysisService.extractSkills(request.getJobDescription());

        // Compare skills to the resume — returns only the gaps
        List<SkillGap> gaps = jobAnalysisService.compareToResume(request.getResumeText(), skills);

        // Generate a follow-up question for each unresolved gap
        List<Question> questions = adaptiveQuestionService.identifyGaps(gaps);

        // Check if all gaps are already resolved (may be true if no significant gaps found)
        boolean allResolved = adaptiveQuestionService.allGapsResolved(gaps);

        log.info("Skill analysis complete: {} gaps, {} questions, allResolved={}",
                gaps.size(), questions.size(), allResolved);

        return ResponseEntity.ok(SkillAnalysisResponse.builder()
                .skillGaps(gaps)
                .questions(questions)
                .allResolved(allResolved)
                .build());
    }

    /**
     * Submit the user's answer to a single skill gap follow-up question.
     *
     * The frontend calls this once per question, then updates its local state
     * with the returned updatedGap. When all gaps in local state are resolved,
     * the frontend enables the generate buttons.
     *
     * The updated gap (with the user's answer stored as transferableExperience)
     * should be included in the skillAnswers list when calling generate endpoints.
     *
     * Returns 200 OK with { updatedGap, resolved }.
     *
     * @param request the skill name, whether they have experience, and their description
     */
    @PostMapping("/answer")
    public ResponseEntity<SkillAnswerResponse> answerSkillQuestion(
            @Valid @RequestBody SkillAnswerRequest request) {

        log.debug("Processing skill gap answer for: {}", request.getSkillName());

        // Build the Question context needed by AdaptiveQuestionService
        Question question = Question.builder()
                .skillName(request.getSkillName())
                .questionText("") // text not needed for processing the answer
                .questionType("OPEN_ENDED")
                .build();

        // Process the answer and get the resolved gap
        SkillGap updatedGap = adaptiveQuestionService.processAnswer(question, request.getDescription());

        // Preserve the hasExperience flag from the request (more reliable than inferring from text)
        updatedGap.setHasExperience(request.isHasExperience());

        return ResponseEntity.ok(SkillAnswerResponse.builder()
                .updatedGap(updatedGap)
                .resolved(updatedGap.isResolved())
                .build());
    }
}