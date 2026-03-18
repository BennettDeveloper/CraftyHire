package com.app.craftyhire.service;

import com.app.craftyhire.dto.request.CoverLetterRequest;
import com.app.craftyhire.dto.request.ResumeRequest;
import com.app.craftyhire.model.SkillAnswer;
import com.app.craftyhire.model.SkillGap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    /**
     * Optional LaTeX resume template loaded from the classpath.
     * If absent, the service falls back to its built-in plain-text output.
     */
    @Value("classpath:templates/resumes/resume-tex-template.tex")
    private Resource resumeLatexTemplateResource;

    /**
     * Optional DOCX resume template loaded from the classpath.
     * Bracketed placeholders (e.g. [Full Name]) are filled in by Claude.
     * If absent, the service falls back to its built-in plain-text output.
     */
    @Value("classpath:templates/resumes/resume-docx-template.docx")
    private Resource resumeDocxTemplateResource;

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
        String latexTemplate = loadLatexTemplate();
        String docxTemplate  = loadDocxTemplateText();

        // ADD THIS:
        log.info("Template sizes — LaTeX: {} chars, DOCX: {} chars, Resume: {} chars, JD: {} chars",
                latexTemplate  == null ? 0 : latexTemplate.length(),
                docxTemplate   == null ? 0 : docxTemplate.length(),
                request.getRawResumeText()   == null ? 0 : request.getRawResumeText().length(),
                request.getJobDescription()  == null ? 0 : request.getJobDescription().length());

        boolean isLatex = "LATEX".equalsIgnoreCase(request.getOutputFormat());
        boolean isDocx  = "WORD".equalsIgnoreCase(request.getOutputFormat())
                || "DOCX".equalsIgnoreCase(request.getOutputFormat());

        log.info(">>> generateResume called — format: '{}', isDocx: {}, isLatex: {}, docxTemplate null: {}",
                request.getOutputFormat(),
                isDocx,
                isLatex,
                loadDocxTemplateText() == null);

        String prompt;

        if (isLatex && latexTemplate != null) {
            // For LaTeX export: Claude replaces only the [INSERT HERE: ...] placeholders.
            // All LaTeX commands, formatting, and structure must remain byte-for-byte identical.
            prompt = """
                    You are an expert resume writer and LaTeX typesetter.

                    Your task is to fill in the LaTeX resume template below by replacing ONLY the
                    [INSERT HERE: ...] placeholder values with the candidate's real information,
                    tailored to the target job description.

                    CRITICAL RULES — read carefully:
                    1. Replace ONLY the text inside [INSERT HERE: ...] brackets. The replacement
                       text goes in place of the entire bracket expression including the brackets.
                    2. Do NOT modify any LaTeX commands, backslashes, braces, environments,
                       or any text that is not inside an [INSERT HERE: ...] bracket.
                    3. Tailor the content for the job: incorporate keywords from the job description,
                       quantify achievements, use strong action verbs. Do NOT invent experience.
                    4. If the candidate's resume does NOT have data for a section
                       (e.g., no projects, no second job, no awards), remove that entire
                       section block — including its \\section{}, \\resumeHeading, and
                       \\resumeItemListStart...\\resumeItemListEnd — from the output.
                    5. If a section has fewer items than the template (e.g., only 1 job instead
                       of 3), remove the extra blocks for the missing items entirely.
                    6. Escape these special LaTeX characters inside inserted text only:
                       & → \\&   %  → \\%   $ → \\$   # → \\#   _ → \\_   ^ → \\^{}
                    7. Output ONLY the complete LaTeX source file — no explanation, no markdown
                       fences, no commentary before or after.

                    ---
                    LATEX TEMPLATE:
                    """ + latexTemplate + """

                    ---
                    CANDIDATE'S RESUME:
                    """ + request.getRawResumeText() + """

                    ---
                    TARGET JOB DESCRIPTION:
                    """ + request.getJobDescription()
                    + (skillAnswerContext.isEmpty() ? "" : """

                    ---
                    ADDITIONAL EXPERIENCE TO INCORPORATE (from candidate's answers to follow-up questions):
                    """ + skillAnswerContext);

        } else if (isDocx && docxTemplate != null) {
            prompt = """
        You are an expert resume writer.

        Fill in the Word resume template below by replacing every [bracket placeholder]
        with the candidate's real information, tailored to the job description.

        CRITICAL RULES — violations will break the output:
        1.  Return ONLY a valid JSON object. No explanation, no markdown fences, no text before or after.
        2.  Each key must be the EXACT placeholder text including brackets,
            e.g. "[Job Title 1]", "[GitHub URL]", "[City, State]".
        3.  Each value is a plain string. No nested JSON, no extra brackets.
        4.  NEVER leave a placeholder value as its template hint text (e.g. do not return
            "[Job Title 1]" as a value — replace it with the actual job title).
        5.  NEVER return an empty string "" for contact fields like [LinkedIn URL],
            [GitHub URL], [Phone Number], [Professional Email], [City, State] —
            always fill these from the candidate's resume. If truly missing, write "N/A".
        6.  EVERY job slot must map to a DIFFERENT job from the candidate's resume.
            Job 1, Job 2, Job 3, Job 4 must each be a unique role. NEVER repeat the
            same company + title + date combination in more than one slot.
        7.  Fill jobs in reverse chronological order (most recent = Job 1).
        8.  If the candidate has fewer jobs than template slots, set ALL fields for the
            unused slots to "" (title, company, city, dates, and all bullets).
        9.  NEVER leave a section completely empty if the candidate has relevant data.
            Projects, Skills, and Education must always be filled if data exists.
        10. If a project slot has no matching project, set ALL its fields to "".
            NEVER output a project heading with empty technology/github/demo fields
            while leaving the description filled — either fill everything or empty everything.
        11. NEVER output an empty bullet. If a bullet slot has no content, set it to "".
        12. Tailor all content to the job description: use exact keywords, quantify
            achievements, use strong action verbs. Do NOT invent experience.
        13. CONTACT FIELDS — for each of these placeholders: [City, State], [Phone Number],
                        [Professional Email], [LinkedIn URL], [GitHub URL]:
                        - If the candidate's resume contains the information → fill it in.
                        - If the candidate's resume does NOT contain the information → set the value to "REMOVE".
                        The export pipeline will strip any token with value "REMOVE" from the output entirely,
                        including the surrounding separator " | " so the contact line stays clean.
        14. If a job has no meaningful bullet points to fill (e.g. a non-technical role with no
                         relevant accomplishments for this job description), set ALL fields for that job slot
                         to "" — title, company, city, dates, AND all bullets. Do not include a job heading
                         with empty bullets. Either fill the whole slot or empty the whole slot.
        
         15. NEVER output any text that still contains [ or ] characters in the final result.
             If a placeholder cannot be filled from the candidate's resume or job description,
             set it to "" — never leave bracket text in the output.
             16. The SKILLS & CERTIFICATIONS section has labeled bullet placeholders. You MUST
                 preserve the label (e.g. "Languages:", "Frameworks & Libraries:", "Databases:")
                 exactly as written in the template — only replace the bracketed value after the
                 label. For example:
                 "[Java | Python | SQL | JavaScript | etc.]" → "Python, C#, Java, TypeScript, SQL"
                 Never remove or merge the skill category labels.
             17. If a skill category has no matching data from the candidate's resume, set its
                 placeholder value to "" so the entire bullet is removed by the cleanup pass.
---
        TEMPLATE PLACEHOLDERS (you must provide a value for every key below):
        """ + docxTemplate + """

        ---
        CANDIDATE'S RESUME:
        """ + request.getRawResumeText() + """

        ---
        TARGET JOB DESCRIPTION:
        """ + request.getJobDescription()
                    + (skillAnswerContext.isEmpty() ? "" : """

        ---
        ADDITIONAL EXPERIENCE:
        """ + skillAnswerContext);
    } else {
            // For PDF (or DOCX/LaTeX without a template): generate plain text.
            // If a LaTeX template exists, use it as a structural guide so the sections
            // and ordering match what the user expects from their template.
            String structureGuide = (latexTemplate != null)
                    ? """

                    **Structure Guide:**
                    Use the section structure from this LaTeX template as your guide — maintain the same
                    sections and ordering, but output plain text (ALL-CAPS section headers, no LaTeX commands):

                    """ + latexTemplate
                    : """

                    **Output Format:**
                    Return the complete resume as plain text with these section headers \
                    (include only sections relevant to the candidate):
                    CONTACT INFORMATION
                    PROFESSIONAL SUMMARY
                    SKILLS
                    WORK EXPERIENCE
                    EDUCATION
                    PROJECTS (if applicable)
                    CERTIFICATIONS (if applicable)
                    """;

            prompt = """
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
                    """ + structureGuide + """

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
        }

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

    // ── Template Loading ─────────────────────────────────────────────────────

    /**
     * Reads the LaTeX resume template from the classpath.
     * Returns null (and logs a warning) if the file is missing or unreadable.
     */
    private String loadLatexTemplate() {
        try {
            if (resumeLatexTemplateResource.exists()) {
                String template = new String(
                        resumeLatexTemplateResource.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                log.debug("Loaded LaTeX resume template ({} chars)", template.length());
                return template;
            }
        } catch (IOException e) {
            log.warn("Could not load LaTeX resume template — falling back to default output: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extracts plain text (including bracket placeholders) from the DOCX resume template.
     * Uses Apache POI to iterate through every paragraph and concatenate their text.
     * Returns null (and logs a warning) if the file is missing or unreadable.
     */
    private String loadDocxTemplateText() {
        try {
            if (resumeDocxTemplateResource.exists()) {
                try (XWPFDocument doc = new XWPFDocument(resumeDocxTemplateResource.getInputStream())) {
                    StringBuilder sb = new StringBuilder();
                    for (XWPFParagraph para : doc.getParagraphs()) {
                        String text = para.getText();
                        sb.append(text).append("\n");
                    }
                    String template = sb.toString();
                    log.debug("Loaded DOCX resume template ({} chars)", template.length());
                    return template;
                }
            }
        } catch (IOException e) {
            log.warn("Could not load DOCX resume template — falling back to default output: {}", e.getMessage());
        }
        return null;
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
                .onStatus(status -> status.isError(), (req, res) -> {
                    String errorBody = new String(res.getBody().readAllBytes());
                    log.error("Claude API error {}: {}", res.getStatusCode(), errorBody);
                    throw new RuntimeException("Claude API error: " + res.getStatusCode() + " - " + errorBody);
                })
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