package com.app.craftyhire.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for the file export endpoints:
 *   POST /api/export/pdf
 *   POST /api/export/word
 *   POST /api/export/latex
 *
 * Contains the generated document content and metadata needed to
 * apply the correct formatting template during export.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {

    /** The generated text content to be formatted and exported */
    @NotBlank(message = "Content is required")
    private String content;

    /**
     * The target file format.
     * Accepted values: "PDF", "DOCX", "LATEX"
     */
    @NotBlank(message = "Format is required")
    private String format;

    /**
     * The type of document being exported.
     * Accepted values: "RESUME", "COVER_LETTER"
     * Used to select the appropriate formatting template.
     */
    @NotBlank(message = "Document type is required")
    private String documentType;
}