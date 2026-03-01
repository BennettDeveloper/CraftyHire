package com.app.craftyhire.dto.request;

import com.app.craftyhire.model.SkillAnswer;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal request object passed to ClaudeService for cover letter generation.
 *
 * Separated from GenerateRequest to allow cover-letter-specific fields
 * to be added in the future (e.g., tone preference, company name, hiring manager name)
 * without polluting the shared GenerateRequest DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverLetterRequest {

    /** The user's parsed resume text, for context on their background */
    @NotBlank(message = "Resume text is required")
    private String resumeText;

    /** The job description, used to tailor the letter to the role */
    @NotBlank(message = "Job description is required")
    private String jobDescription;

    /**
     * Optional: A previous cover letter to use as a style and tone reference.
     * Claude will write in the user's existing voice rather than a generic style.
     */
    private String previousCoverLetter;

    /**
     * Skill gap answers to include as additional context.
     * Ensures the cover letter addresses any transferable experience the user provided.
     */
    @Builder.Default
    private List<SkillAnswer> skillAnswers = new ArrayList<>();

    /**
     * Desired output format.
     * Accepted values: "PDF", "DOCX", "LATEX"
     */
    private String outputFormat;
}