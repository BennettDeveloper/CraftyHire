package com.app.craftyhire.dto.request;

import com.app.craftyhire.model.SkillAnswer;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for resume and cover letter generation endpoints:
 *   POST /api/resume/generate-resume
 *   POST /api/resume/generate-cover-letter
 *
 * Contains all context Claude needs to generate tailored documents:
 * the user's parsed resume text, the job description, any skill gap answers,
 * an optional previous cover letter, and the desired output format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateRequest {

    /** The user's resume text, extracted server-side from their uploaded file */
    @NotBlank(message = "Resume text is required")
    private String resumeText;

    /** The full job description to tailor the resume against */
    @NotBlank(message = "Job description is required")
    private String jobDescription;

    /**
     * Desired output file format.
     * Accepted values: "PDF", "DOCX", "LATEX"
     */
    @NotBlank(message = "Output format is required")
    private String outputFormat;

    /**
     * Optional: A previous cover letter to use as a style/tone reference.
     * Claude will adapt the user's existing voice rather than generating
     * something generic.
     */
    private String previousCoverLetter;

    /**
     * Optional: User answers to skill gap follow-up questions.
     * Provided when the initial resume doesn't fully match the job and
     * the user has been asked about transferable experience.
     * Defaults to an empty list if not provided.
     */
    @Builder.Default
    private List<SkillAnswer> skillAnswers = new ArrayList<>();
}