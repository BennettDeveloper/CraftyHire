package com.app.craftyhire.dto.response;

import lombok.*;

/**
 * Response body returned after a resume file is successfully parsed.
 *
 * The frontend stores resumeText and includes it in all subsequent requests
 * (skill analysis, resume generation, cover letter generation).
 * This keeps the API stateless — no server-side session is needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResumeResponse {

    /** The full plain text extracted from the uploaded file */
    private String resumeText;

    /**
     * The detected file type of the uploaded resume.
     * Values: "PDF", "DOCX", "LATEX"
     * Displayed in the UI to confirm the file was read correctly.
     */
    private String fileType;
}