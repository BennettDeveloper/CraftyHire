package com.app.craftyhire.dto.request;

import com.app.craftyhire.model.SkillAnswer;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal request object used after a resume file has been parsed.
 *
 * Unlike GenerateRequest (which comes from the frontend), this is
 * typically constructed server-side after calling ResumeParserService
 * and passed between internal services.
 *
 * The distinction between rawResumeText and resumeText (in GenerateRequest)
 * allows future pre-processing steps (e.g., cleaning, normalizing) to be
 * inserted between parsing and generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeRequest {

    /** Raw text extracted from the uploaded resume file (PDF, DOCX, or LaTeX) */
    @NotBlank(message = "Resume text is required")
    private String rawResumeText;

    /** The job description to tailor the resume against */
    @NotBlank(message = "Job description is required")
    private String jobDescription;

    /**
     * Desired output format.
     * Accepted values: "PDF", "DOCX", "LATEX"
     */
    private String outputFormat;

    /** Optional: previous cover letter for tone/style reference */
    private String previousCoverLetter;

    /** Skill gap follow-up answers, if any have been collected */
    @Builder.Default
    private List<SkillAnswer> skillAnswers = new ArrayList<>();
}