package com.app.craftyhire.dto.response;

import lombok.*;

/**
 * Response body returned after a resume or cover letter is generated.
 *
 * The frontend displays the content for the user to review before downloading.
 * To download, the content is sent back to the export endpoints
 * (POST /api/export/pdf, /api/export/word, /api/export/latex).
 *
 * Scalability note: Add a 'tokensUsed' field here in the future to track
 * API usage per generation for billing or rate-limiting purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateDocumentResponse {

    /** The full generated document as plain text */
    private String content;

    /**
     * The type of document that was generated.
     * Values: "RESUME" or "COVER_LETTER"
     * Used by the frontend to label the preview and set the correct
     * documentType when calling the export endpoints.
     */
    private String documentType;
}