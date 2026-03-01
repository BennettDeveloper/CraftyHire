package com.app.craftyhire.service;

import com.app.craftyhire.dto.request.CoverLetterRequest;
import com.app.craftyhire.dto.request.ResumeRequest;
import com.app.craftyhire.model.SkillAnswer;
import com.app.craftyhire.model.SkillGap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core AI service responsible for all Claude-powered document generation.
 *
 * This is the primary integration point with the Anthropic Claude API.
 * It handles the high-level generation tasks:
 *   - Analyzing job descriptions for career advice
 *   - Generating ATS-optimized, tailored resumes
 *   - Generating personalized cover letters
 *   - Generating follow-up questions for skill gaps
 *   - Processing user answers to incorporate into documents
 *
 * Each public method constructs a carefully crafted prompt and returns
 * Claude's response as plain text, ready for the FileExportService to format.
 *
 * Scalability note: Prompts are currently embedded as strings. For production,
 * consider moving prompts to a database or config files so they can be
 * A/B tested and updated without redeploying the application.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeService {

    private final RestClient claudeRestClient;
    private final JobAnalysisService jobAnalysisService;

    @Value("${anthropic.api.model}")
    private String model;

    @Value("${anthropic.api.max-tokens}")
    private int maxTokens;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Analyzes a job description and returns a structured career overview.
     *
     * Used as an initial step when the user pastes a job description.
     * The response provides context that helps the user understand what
     * the employer is looking for before their resume is tailored.
     *
     * @param jobDescription the raw job posting text
     * @return a structured analysis covering responsibilities, qualifications, and ATS keywords
     */
    public String analyzeJobDescription(String jobDescription) {
        log.debug("Analyzing job description ({} chars)", jobDescription.length());

        String prompt = """
                You are a professional career advisor and resume expert.
                Analyze this job description and provide a structured overview covering:

                1. **Role Summary** — what this position is and who it's for (2-3 sentences)
                2. **Key Responsibilities** — the main things this person will do day-to-day
                3. **Must-Have Qualifications** — non-negotiable requirements
                4. **Nice-to-Have Qualifications** — preferred but not required
                5. **ATS Keywords** — the most important keywords to include in a resume for this role
                6. **Culture Indicators** — anything that hints at the team culture or work environment

                Be concise and practical. This analysis will be shown to a candidate preparing their application.

                Job Description:
                """ + jobDescription;

        return callClaude(prompt);
    }

    /**
     * Generates an ATS-optimized resume tailored to a specific job.
     *
     * Claude rewrites the candidate's existing resume to:
     *   - Naturally incorporate exact keywords from the job description
     *   - Lead with the most relevant experience for this role
     *   - Quantify achievements wherever possible
     *   - Use strong action verbs and professional formatting
     *   - Incorporate any additional context from skill gap answers
     *
     * The output is plain text with clear section headers, ready to be
     * formatted and exported by FileExportService.
     *
     * @param request contains the resume text, job description, output format, and any skill answers
     * @return tailored resume as plain text
     */
    public String generateResume(ResumeRequest request) {
        log.debug("Generating tailored resume for request with {} skill answers",
                request.getSkillAnswers() == null ? 0 : request.getSkillAnswers().size());

        String skillAnswerContext = buildSkillAnswerContext(request.getSkillAnswers());

        String prompt = """
                You are an expert resume writer specializing in ATS (Applicant Tracking System) optimization.

                Rewrite the candidate's resume to be perfectly tailored for the target job.

                **Requirements:**
                - Incorporate exact keywords from the job description naturally (not keyword stuffing)
                - Lead with the most relevant experience for this specific role
                - Quantify every achievement with numbers, percentages, or scale where possible
                - Use strong action verbs: led, built, designed, increased, reduced, launched, etc.
                - Keep bullet points concise (1-2 lines each) and impactful
                - Do NOT invent experience — only work with what's provided
                - Maintain a professional, clean tone throughout

                **Output Format:**
                Return the complete resume as plain text with these section headers (include only sections relevant to the candidate):
                CONTACT INFORMATION
                PROFESSIONAL SUMMARY
                SKILLS
                WORK EXPERIENCE
                EDUCATION
                PROJECTS (if applicable)
                CERTIFICATIONS (if applicable)

                ---
                CANDIDATE'S CURRENT RESUME:
                """ + request.getRawResumeText() + """

                ---
                TARGET JOB DESCRIPTION:
                """ + request.getJobDescription()
                + (skillAnswerContext.isEmpty() ? "" : """

                ---
                ADDITIONAL EXPERIENCE TO INCORPORATE (from candidate's answers to follow-up questions):
                """ + skillAnswerContext);

        String result = callClaude(prompt);
        log.info("Resume generation complete ({} chars)", result.length());
        return result;
    }

    /**
     * Generates a personalized cover letter for a job application.
     *
     * If the user provides a previous cover letter, Claude adapts to their
     * writing voice and tone. Otherwise, a professional default style is used.
     *
     * The letter is 3-4 paragraphs (~300 words) and:
     *   - Opens with a strong, specific introduction
     *   - Highlights 2-3 directly relevant achievements
     *   - Shows genuine interest in the company/role
     *   - Closes with a confident call to action
     *
     * @param request contains the resume, job description, optional previous letter, and skill answers
     * @return personalized cover letter as plain text
     */
    public String generateCoverLetter(CoverLetterRequest request) {
        log.debug("Generating cover letter");

        String skillAnswerContext = buildSkillAnswerContext(request.getSkillAnswers());
        boolean hasPreviousLetter = request.getPreviousCoverLetter() != null
                && !request.getPreviousCoverLetter().isBlank();

        String prompt = """
                You are an expert cover letter writer who creates compelling, personalized cover letters.

                Write a professional cover letter for this job application.

                **Requirements:**
                - Length: 3-4 paragraphs, approximately 250-350 words
                - Opening: Strong, attention-grabbing — reference the specific role and company
                - Body: 2-3 specific, quantified achievements that directly match the job requirements
                - Closing: Confident, clear call to action
                - Tone: Professional but personable — avoid generic phrases like "I am writing to apply for..."
                """
                + (hasPreviousLetter ? """
                - IMPORTANT: Match the writing style and tone of the previous cover letter provided below.
                  The candidate has a specific voice — preserve it.
                """ : "")
                + """

                ---
                CANDIDATE'S RESUME:
                """ + request.getResumeText() + """

                ---
                JOB DESCRIPTION:
                """ + request.getJobDescription()
                + (hasPreviousLetter ? """

                ---
                PREVIOUS COVER LETTER (match this style and tone):
                """ + request.getPreviousCoverLetter() : "")
                + (skillAnswerContext.isEmpty() ? "" : """

                ---
                ADDITIONAL EXPERIENCE TO INCORPORATE:
                """ + skillAnswerContext);

        String result = callClaude(prompt);
        log.info("Cover letter generation complete ({} chars)", result.length());
        return result;
    }

    /**
     * Generates a follow-up question to ask when a skill gap is detected.
     *
     * The question is designed to be friendly and non-intimidating, asking
     * if the candidate has direct OR transferable experience that wasn't
     * captured in their resume.
     *
     * @param gap the skill gap to ask about
     * @return a single question string to display to the user
     */
    public String askFollowUpQuestion(SkillGap gap) {
        log.debug("Generating follow-up question for skill gap: {}", gap.getSkillName());

        int scorePercent = (int) (gap.getRelevanceScore() * 100);

        String prompt = String.format("""
                A skill gap has been detected in a candidate's resume. Generate a single, friendly follow-up question.

                Gap Details:
                - Skill: %s
                - Importance for this role: %d%%
                - This skill does not appear in their resume

                The question must:
                - Be conversational and encouraging, not intimidating
                - Ask about BOTH direct experience AND transferable/adjacent skills
                - Be specific to "%s" but acknowledge that related experience counts
                - Be one sentence or two at most

                Return ONLY the question text — no labels, no explanation, just the question.
                """, gap.getSkillName(), scorePercent, gap.getSkillName());

        return callClaude(prompt).trim();
    }

    /**
     * Processes the user's answer to a skill gap follow-up question and
     * returns a brief strategy for incorporating that experience into the documents.
     *
     * This strategy is stored and passed back to generateResume/generateCoverLetter
     * so Claude knows how to frame the candidate's transferable experience.
     *
     * @param answer the user's free-text answer describing their experience
     * @param gap    the skill gap this answer addresses
     * @return a 2-3 sentence strategy for framing the experience
     */
    public String processFollowUpAnswer(String answer, SkillGap gap) {
        log.debug("Processing follow-up answer for skill: {}", gap.getSkillName());

        String prompt = String.format("""
                A candidate answered a follow-up question about a skill gap. Determine how to best present their experience.

                Skill Required: %s (importance: %d%%)
                Candidate's Answer: %s

                Write a brief strategy (2-3 sentences) for how to frame this experience in their resume and cover letter.
                Focus on connecting their background to the required skill in a way that's honest and compelling.

                Return ONLY the strategy text — no labels, no preamble.
                """,
                gap.getSkillName(),
                (int) (gap.getRelevanceScore() * 100),
                answer);

        return callClaude(prompt).trim();
    }

    // ── Claude API ───────────────────────────────────────────────────────────

    /**
     * Sends a user message to the Claude API and returns the response text.
     *
     * Uses the shared claudeRestClient which has the API key and headers
     * pre-configured (see ApiClientConfig).
     *
     * @param userMessage the prompt to send
     * @return Claude's text response
     * @throws RuntimeException if the API call fails or the response is malformed
     */
    @SuppressWarnings("unchecked")
    private String callClaude(String userMessage) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "user", "content", userMessage)
                )
        );

        Map<String, Object> response = claudeRestClient.post()
                .uri("/v1/messages")
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null) {
            throw new RuntimeException("Empty response from Claude API");
        }

        // API response format: { "content": [{ "type": "text", "text": "..." }] }
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("No content in Claude API response");
        }

        return (String) content.get(0).get("text");
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Converts a list of SkillAnswer objects into a readable block of text
     * to include in generation prompts. Returns empty string if no answers.
     *
     * Example output:
     *   - Kubernetes: Yes, has experience. "Managed 3-node K8s cluster for microservices project."
     *   - GraphQL: No direct experience. "Familiar with REST APIs; has not worked with GraphQL directly."
     */
    private String buildSkillAnswerContext(List<SkillAnswer> skillAnswers) {
        if (skillAnswers == null || skillAnswers.isEmpty()) {
            return "";
        }

        return skillAnswers.stream()
                .map(answer -> String.format(
                        "- %s: %s. \"%s\"",
                        answer.getSkillName(),
                        answer.isHasExperience() ? "Has experience" : "No direct experience",
                        answer.getDescription() != null ? answer.getDescription() : ""
                ))
                .collect(Collectors.joining("\n"));
    }
}