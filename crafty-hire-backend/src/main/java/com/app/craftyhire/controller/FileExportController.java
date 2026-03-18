package com.app.craftyhire.controller;

import com.app.craftyhire.dto.request.ExportRequest;
import com.app.craftyhire.service.FileExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * REST controller for downloading generated documents as files.
 *
 * After the user reviews their generated resume or cover letter, they call
 * one of these endpoints to download it in their preferred format.
 *
 * All endpoints accept the same ExportRequest body:
 *   - content:      the generated document text (from GenerateDocumentResponse)
 *   - format:       "PDF", "DOCX", or "LATEX"
 *   - documentType: "RESUME" or "COVER_LETTER" (affects filename and formatting)
 *
 * Responses use Content-Disposition: attachment so the browser triggers a
 * file download rather than displaying the content inline.
 *
 * Scalability note: For high-traffic scenarios, consider moving file generation
 * to an async job queue and returning a download URL instead of the raw bytes.
 * This prevents large PDF generation from blocking HTTP threads.
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class FileExportController {

    private final FileExportService fileExportService;

    /**
     * Export a generated document as a PDF file.
     *
     * Returns the PDF as a binary download with:
     *   Content-Type: application/pdf
     *   Content-Disposition: attachment; filename="resume.pdf" (or "cover_letter.pdf")
     *
     * @param request content, format (should be "PDF"), and documentType
     */
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> exportPDF(@Valid @RequestBody ExportRequest request) throws IOException {
        log.info("Exporting {} as PDF", request.getDocumentType());

        byte[] pdf = fileExportService.exportToPDF(request.getContent(), request.getDocumentType());
        String filename = buildFilename(request.getDocumentType(), "pdf");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Export a generated document as a Word (.docx) file.
     *
     * Returns the DOCX as a binary download with the correct Word MIME type:
     *   Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
     *   Content-Disposition: attachment; filename="resume.docx"
     *
     * @param request content, format (should be "DOCX"), and documentType
     */
    @PostMapping("/word")
    public ResponseEntity<byte[]> exportWord(@Valid @RequestBody ExportRequest request) throws IOException {
        log.info("Exporting {} as DOCX", request.getDocumentType());

        byte[] docx = fileExportService.exportToWord(request.getContent(), request.getDocumentType());
        String filename = buildFilename(request.getDocumentType(), "docx");

        MediaType docxMediaType = MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(docxMediaType)
                .body(docx);
    }

    /**
     * Export a generated document as a LaTeX (.tex) source file.
     *
     * Returns the LaTeX source as a UTF-8 text download.
     * The user can compile it with pdflatex or xelatex to produce a PDF.
     *
     * Content-Type: application/x-tex
     * Content-Disposition: attachment; filename="resume.tex"
     *
     * @param request content, format (should be "LATEX"), and documentType
     */
    @PostMapping("/latex")
    public ResponseEntity<byte[]> exportLatex(@Valid @RequestBody ExportRequest request) {
        log.info("Exporting {} as LaTeX", request.getDocumentType());

        byte[] latex = fileExportService.exportToLatex(request.getContent(), request.getDocumentType());
        String filename = buildFilename(request.getDocumentType(), "tex");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/x-tex"))
                .body(latex);
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Builds the download filename based on document type and extension.
     * Examples: "resume.pdf", "cover_letter.docx", "resume.tex"
     */
    private String buildFilename(String documentType, String extension) {
        String base = "RESUME".equalsIgnoreCase(documentType) ? "resume" : "cover_letter";
        return base + "." + extension;
    }
}



