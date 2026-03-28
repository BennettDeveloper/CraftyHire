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

    /**
     * Optional DOCX cover letter template loaded from the classpath.
     * Bracketed placeholders (e.g. [Your Full Name]) are filled in by Claude.
     * If absent, the service falls back to its built-in plain-text output.
     */
    @Value("classpath:templates/coverletters/coverletter-docx-template.docx")
    private Resource coverLetterDocxTemplateResource;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Analyzes a job description and returns a structured career overview.
     *
     * Used as an initial step when the user pastes a job description.
     * The response provides context that helps the user understand what
     * the employer is looking for before their resume is tailored.
     *
     * This is the %'s prompt call
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
        14. If a job slot has no directly relevant technical bullet points for the target role,
                                DO NOT omit the job — instead write 2-3 bullets focused on TRANSFERABLE SKILLS
                                such as leadership, communication, customer service, teamwork, problem-solving,
                                attention to detail, and operational excellence. Every job the candidate has held
                                must appear in the resume. A job entry should only be set to "" if the template
                                has more slots than the candidate has jobs.
                                A heading with any empty fields must not appear in the output.
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
        18. CHRONOLOGICAL ORDERING — across ALL sections (Experience, Projects, Education),
                                order entries with the MOST RECENT date at the top and the OLDEST at the bottom.
                                - For Experience: sort by start date, most recent job first.
                                - For Education: sort by graduation/end date, most recent program first.
                                - For Projects: sort by most recently worked on or most relevant, most recent first.
                                - If a role is current/present (e.g. "Aug 2024 – Present"), it ranks as the most
                                  recent and must always appear first.
                                - Never mix chronological order — do not place an older job above a newer one.
        19. SKILLS SECTION LABELS — each skills bullet has a bold label followed by a\s
                                bracketed placeholder, e.g.:
                                    "Languages: [Languages: e.g. Java | Python | SQL | JavaScript]"
                                You must ONLY replace the bracketed portion. The label before the bracket
                                (e.g. "Languages:", "Frameworks & Libraries:", "Databases:", "Tools & Platforms:",
                                "Methodologies:", "Professional Skills:", "Certifications:") must be preserved
                                EXACTLY as-is in your JSON value.

                                For example, the JSON entry should look like:
                                    "[Languages: e.g. Java | Python | SQL | JavaScript]": "Python, C#, Java, TypeScript, SQL"

                                The label "Languages:" lives outside the bracket in the template and must\s
                                never be included in or removed by your replacement value.
        20. DATES — copy every date EXACTLY as written in the candidate's resume. Do not
                                reformat, estimate, abbreviate, or change any date under any circumstance.
                                If the original says "Aug 2024 - current", output "Aug 2024 - current" exactly.
                                If the original says "Sep. 2021 - May 2024", output "Sep. 2021 - May 2024" exactly.
                                Never invent, approximate, or alter a date. Dishonest dates on a resume are
                                unacceptable and will disqualify the candidate.
        21. CONTACT FIELDS — copy [City, State], [Phone Number], [Professional Email],
            [LinkedIn URL], [GitHub URL] EXACTLY as written in the candidate's resume.
            Do not infer, guess, or substitute any contact information. If a field is
            genuinely not present in the resume, set it to "REMOVE". Never fabricate
        22. PAGE LENGTH — the final resume MUST fit on one page. If space is tight, apply
                                these sacrifices IN ORDER — never skip a level:
                                1. Shorten bullet point text to be more concise
                                2. Reduce bullet points per job to 2 (never fewer than 1 per job)
                                3. Reduce project bullets to 1 per project
                                4. Remove project description lines, keeping only bullets
                                5. Remove the least relevant PROJECT slot entirely
                                6. Suggest reducing font size (the export pipeline will handle this
                                   automatically — flag it by adding this exact line at the very end
                                   of your JSON response, outside all other fields:
                                   "COMPRESS_FONT": "true")
                                NEVER remove Education or Experience entries under any circumstance.
                                Education is always included even if trimmed to just the degree name,
                                institution, and graduation year with no additional detail.
                                Priority order highest to lowest:
                                - Experience (never sacrificed)
                                - Education (never sacrificed, may be trimmed to one line)
                                - Projects (first to be cut if space is needed) 
        23. PUNCTUATION — never use em dashes (—) or en dashes (–) anywhere in the resume
                                body text except as date range separators between two dates
                                (e.g. "Jun. 2023 - Sep. 2023"). For all other uses replace with:
                                - A colon ":" for label-to-value relationships
                                  e.g. "Executive Member: Game Design & Development Club" NOT "Executive Member—Game Design & Development Club"
                                  e.g. "Member: UDel Competitive Programming Club" NOT "Member—UDel Competitive Programming Club"
                                - A comma "," for list separation
                                - A period "." to end a sentence
                                Never use "—" or "–" mid-sentence or after a label as a stylistic separator.
                                This rule has zero exceptions outside of date ranges.
        24. PROFESSIONAL SUMMARY HONESTY — every claim in the Professional Summary MUST be
                                directly traceable to specific content in the candidate's resume. Do not invent
                                metrics, role titles, domain expertise, or responsibilities that do not appear
                                in the resume.

                                ALLOWED — referencing and tailoring real experience:
                                - If the resume mentions "300+ students" you may reference scale of impact
                                - If the resume mentions ".NET deployed to 80% of power plants" you may reference
                                  enterprise-scale systems experience
                                - You may use job description keywords ONLY if the candidate has demonstrated
                                  that skill somewhere in their resume

                                NOT ALLOWED — fabricating to match the job description:
                                - Do not invent user counts, system scales, or metrics not in the resume
                                - Do not claim domain expertise (e.g. "Mobile Device Management") if the
                                  candidate has never worked in that domain
                                - Do not imply responsibilities the candidate has never held

                                If the candidate's experience is not a strong match for a section of the job
                                description, omit that claim from the summary entirely rather than fabricating it.
                                Honesty is more important than keyword coverage.
                                
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
     * When outputFormat is DOCX/WORD, Claude fills the coverletter-docx-template.docx
     * placeholders and returns JSON — the same approach used for the resume DOCX flow.
     * For PDF and LaTeX, Claude returns plain text that follows the template's structure,
     * writing style, font choice (Times New Roman), spacing, and word count.
     *
     * If the user provides a previous cover letter, Claude adapts to their voice and tone.
     *
     * Template spec (coverletter-docx-template.docx):
     *   - Font: Times New Roman, 11pt
     *   - Format: Standard business letter
     *       contact header → date → company → "Dear Hiring Manager," →
     *       3 body paragraphs → "Sincerely," → candidate name
     *   - Spacing: ~0.33 in before/after each paragraph (240 twips)
     *   - Tone: Formal but personable, first-person, no generic openers
     *   - Word count: 250–350 words across the three body paragraphs
     *
     * @param request contains the resume, job description, output format,
     *                optional previous letter, and skill answers
     * @return JSON string (DOCX) or plain text (PDF/LaTeX) cover letter content
     */
    public String generateCoverLetter(CoverLetterRequest request) {
        log.debug("Generating cover letter (format: {})", request.getOutputFormat());

        String skillAnswerContext = buildSkillAnswerContext(request.getSkillAnswers());
        boolean hasPreviousLetter = request.getPreviousCoverLetter() != null
                && !request.getPreviousCoverLetter().isBlank();

        boolean isDocx = "WORD".equalsIgnoreCase(request.getOutputFormat())
                || "DOCX".equalsIgnoreCase(request.getOutputFormat());

        String docxTemplate = isDocx ? loadCoverLetterDocxTemplateText() : null;

        String prompt;

        if (isDocx && docxTemplate != null) {
            // ── DOCX path: fill template placeholders, return JSON ──────────────
            prompt = """
                    You are an expert cover letter writer.

                    Fill in the cover letter template below by replacing every [bracket placeholder]
                    with the candidate's real information, tailored to the job description.

                    WRITING STYLE — match the template EXACTLY:
                    - Font: Times New Roman, 11pt (implied by the template — write accordingly)
                    - Page format: Standard business letter
                        * Candidate contact block at top (name, city/state, email, phone)
                        * Date line
                        * Company name
                        * Greeting: "Dear Hiring Manager,"
                        * 3 body paragraphs
                        * Closing: "Sincerely," followed by candidate name
                    - Spacing: One blank line between each section/paragraph
                    - Tone: Professional, personable, sincere — formal but human
                    - Sentences: Complete, grammatically correct sentences.
                      NO dashes used to break up sentences. NO bullet points in the body.
                    - Word count: 250–350 words across the three body paragraphs combined
                    - No bold or italic text in the body paragraphs

                    CRITICAL RULES:
                    1.  Return ONLY a valid JSON object. No explanation, no markdown fences, no text before or after.
                    2.  Each key must be the EXACT placeholder text including brackets,
                        e.g. "[Your Full Name]", "[Job Title]", "[Company Name]".
                    3.  Each value is a plain string. No nested JSON, no extra brackets.
                    4.  NEVER leave a placeholder value as its template hint text — replace it with
                        real, specific content drawn from the candidate's resume and job description.
                    5.  [Company Name] appears in multiple places — always supply the same value.
                    6.  [Date] — format as "Month DD, YYYY" using today's date (March 19, 2026).
                    7.  Contact fields ([Your Full Name], [City, State, ZIP], [Email Address],
                        [Phone Number]) — copy EXACTLY from the candidate's resume.
                        If genuinely missing, set the value to "REMOVE".
                    8.  [Your Name] at the sign-off — use the same value as [Your Full Name].
                    9.  NEVER output any text that still contains [ or ] characters in the final result.
                        If a placeholder cannot be filled, set it to "" — never leave bracket text.
                    10. Write body paragraphs that flow naturally in first-person, active voice.
                        Content must be specific — reference real details from the resume and job description.
                    11. DO NOT invent experience or qualifications not present in the resume.
                    12. Avoid generic openers like "I am writing to apply for..." or
                        "I believe I would be a great fit..." — open with something compelling and specific.
                    """
                    + (hasPreviousLetter ? """
                    13. Match the writing voice and tone of the previous cover letter provided below.
                        The candidate has a specific voice — preserve it throughout.
                    """ : "")
                    + """

                    ---
                    TEMPLATE PLACEHOLDERS (provide a value for EVERY key below):
                    """ + docxTemplate + """

                    ---
                    CANDIDATE'S RESUME:
                    """ + request.getResumeText() + """


                    ---
                    JOB DESCRIPTION:
                    """ + request.getJobDescription()
                    + (hasPreviousLetter ? """

                    ---
                    PREVIOUS COVER LETTER (match this candidate's writing voice and tone):
                    """ + request.getPreviousCoverLetter() : "")
                    + (skillAnswerContext.isEmpty() ? "" : """

                    ---
                    ADDITIONAL EXPERIENCE TO INCORPORATE:
                    """ + skillAnswerContext);

        } else {
            // ── PDF / LaTeX / plain-text path ────────────────────────────────────
            // Instruct Claude to follow the same template structure and style
            // even without the actual DOCX file.
            prompt = """
                    You are an expert cover letter writer who creates compelling, personalized cover letters.

                    Write a professional cover letter for this job application.

                    WRITING STYLE — follow the template format exactly:
                    - Font style: Times New Roman, 11pt (professional serif) — write for a clean, formal look
                    - Page format: Standard business letter layout:
                        * Candidate contact block: name, city/state, email, phone (one item per line)
                        * Today's date: March 19, 2026
                        * Recipient: company name
                        * Greeting: "Dear Hiring Manager,"
                        * 3 body paragraphs (no bullet points, no subheadings)
                        * Closing: "Sincerely," followed by a blank line, then the candidate's name
                    - Spacing: One blank line between each section/paragraph
                    - Tone: Professional, personable, sincere — formal but human, first-person active voice
                    - Sentences: Complete, grammatically correct sentences.
                      NO dashes used to break up sentences. NO bullet points in the body.
                    - Word count: 250–350 words across the three body paragraphs combined
                    - No bold or italic formatting in the body

                    PARAGRAPH STRUCTURE (3 body paragraphs):
                    1. Opening — Name the specific role and company. Express genuine interest by
                       referencing something concrete about the company (mission, product, values, or impact).
                    2. Experience & Background — Highlight 2–3 relevant achievements from the candidate's
                       resume with specific details and numbers where available. Connect skills directly
                       to the job requirements.
                    3. Projects / Additional Strengths — Reference a relevant project or additional skill
                       area. Demonstrate the ability to deliver real results.
                    4. Closing — Express enthusiasm, invite further conversation, thank the reader.

                    REQUIREMENTS:
                    - Incorporate exact keywords from the job description naturally
                    - Do NOT invent experience — only use what is in the resume
                    - Avoid generic phrases like "I am writing to apply for..." or "I believe I would be..."
                    """
                    + (hasPreviousLetter ? """
                    - IMPORTANT: Match the writing style, tone, and voice of the previous cover letter
                      provided below. The candidate has a specific voice — preserve it throughout.
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
                    PREVIOUS COVER LETTER (match this style, tone, and voice):
                    """ + request.getPreviousCoverLetter() : "")
                    + (skillAnswerContext.isEmpty() ? "" : """

                    ---
                    ADDITIONAL EXPERIENCE TO INCORPORATE:
                    """ + skillAnswerContext);
        }

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
     * Extracts plain text (including bracket placeholders) from the DOCX cover letter template.
     * Uses Apache POI to iterate through every paragraph and concatenate their text.
     * Returns null (and logs a warning) if the file is missing or unreadable.
     */
    private String loadCoverLetterDocxTemplateText() {
        try {
            if (coverLetterDocxTemplateResource.exists()) {
                try (XWPFDocument doc = new XWPFDocument(coverLetterDocxTemplateResource.getInputStream())) {
                    StringBuilder sb = new StringBuilder();
                    for (XWPFParagraph para : doc.getParagraphs()) {
                        String text = para.getText();
                        sb.append(text).append("\n");
                    }
                    String template = sb.toString();
                    log.debug("Loaded cover letter DOCX template ({} chars)", template.length());
                    return template;
                }
            }
        } catch (IOException e) {
            log.warn("Could not load cover letter DOCX template — falling back to default output: {}", e.getMessage());
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