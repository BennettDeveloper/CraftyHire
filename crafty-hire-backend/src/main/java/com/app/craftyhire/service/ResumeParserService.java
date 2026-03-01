package com.app.craftyhire.service;

import com.app.craftyhire.dto.request.ResumeRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Handles extraction of plain text from uploaded resume files.
 *
 * Supports three input formats:
 *   - PDF   (.pdf)  — via Apache PDFBox
 *   - Word  (.docx) — via Apache POI
 *   - LaTeX (.tex)  — plain text with LaTeX markup stripped
 *
 * The extracted text is passed to ClaudeService for AI-powered
 * resume tailoring. Clean, readable text yields better results from Claude.
 *
 * Scalability note: Add additional parsers here as new formats are supported
 * (e.g., .odt, .rtf, plain text). Each parser should return consistent,
 * whitespace-normalized plain text.
 */
@Slf4j
@Service
public class ResumeParserService {

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Auto-detects the file type and delegates to the appropriate parser.
     * This is the primary entry point used by ResumeController.
     *
     * Detection order:
     *   1. MIME type from the upload (most reliable)
     *   2. File extension fallback (for uploads with incorrect MIME types)
     *
     * @param file the uploaded resume file
     * @return plain text extracted from the resume
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file type is not supported
     */
    public String parseResume(MultipartFile file) throws IOException {
        String fileType = detectFileType(file);
        log.debug("Detected file type '{}' for: {}", fileType, file.getOriginalFilename());

        return switch (fileType) {
            case "PDF"   -> parsePDF(file);
            case "DOCX"  -> parseWord(file);
            case "LATEX" -> parseLatex(file);
            default -> throw new IllegalArgumentException(
                    "Unsupported file type: " + fileType +
                    ". Please upload a PDF, DOCX, or LaTeX (.tex) file.");
        };
    }

    /**
     * Detects the file type based on MIME type and filename extension.
     *
     * @param file the uploaded file
     * @return "PDF", "DOCX", or "LATEX"
     * @throws IllegalArgumentException if the type cannot be determined
     */
    public String detectFileType(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase()
                : "";

        // Check MIME type first (set by the browser during upload)
        if (contentType != null) {
            if (contentType.contains("pdf"))                         return "PDF";
            if (contentType.contains("word"))                        return "DOCX";
            if (contentType.contains("openxmlformats-officedocument")) return "DOCX";
        }

        // Fall back to filename extension
        if (filename.endsWith(".pdf"))              return "PDF";
        if (filename.endsWith(".docx"))             return "DOCX";
        if (filename.endsWith(".doc"))              return "DOCX";
        if (filename.endsWith(".tex"))              return "LATEX";

        throw new IllegalArgumentException(
                "Cannot determine file type for: " + file.getOriginalFilename() +
                ". Supported formats: PDF, DOCX, LaTeX (.tex)");
    }

    /**
     * Extracts plain text from a PDF resume using Apache PDFBox.
     * Preserves reading order of text across columns and sections.
     *
     * @param file the uploaded PDF file
     * @return plain text content of the PDF
     */
    public String parsePDF(MultipartFile file) throws IOException {
        // PDFBox 3.x uses Loader.loadPDF() instead of PDDocument.load()
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Sort by position ensures text is extracted in reading order
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.debug("Extracted {} characters from PDF: {}", text.length(), file.getOriginalFilename());
            return normalizeWhitespace(text);
        }
    }

    /**
     * Extracts plain text from a Word (.docx) resume using Apache POI.
     * Handles standard paragraphs, headers, and tables.
     *
     * @param file the uploaded DOCX file
     * @return plain text content of the Word document
     */
    public String parseWord(MultipartFile file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream());
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            log.debug("Extracted {} characters from DOCX: {}", text.length(), file.getOriginalFilename());
            return normalizeWhitespace(text);
        }
    }

    /**
     * Extracts readable text from a LaTeX (.tex) resume by stripping markup.
     *
     * LaTeX resumes are plain text files with formatting commands like
     * \textbf{}, \section{}, \begin{itemize}, etc. This method removes
     * the markup while preserving the content.
     *
     * Note: This handles the most common LaTeX resume patterns. Highly
     * custom LaTeX with complex macros may need additional stripping rules.
     *
     * @param file the uploaded .tex file
     * @return plain text with LaTeX commands removed
     */
    public String parseLatex(MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String stripped = stripLatexCommands(content);
        log.debug("Extracted {} characters from LaTeX: {}", stripped.length(), file.getOriginalFilename());
        return stripped;
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Strips LaTeX markup from a .tex file, leaving only readable content.
     *
     * Processing steps (in order):
     *   1. Remove the document preamble (everything before \begin{document})
     *   2. Remove \end{document}
     *   3. Remove \begin{...} and \end{...} environment markers
     *   4. Extract text content from commands like \textbf{text} → text
     *   5. Remove standalone commands like \newpage, \hline
     *   6. Remove remaining braces and percent-comment lines
     */
    private String stripLatexCommands(String latex) {
        String content = latex;

        // Remove everything before \begin{document} (preamble: packages, settings, etc.)
        int docStart = content.indexOf("\\begin{document}");
        if (docStart != -1) {
            content = content.substring(docStart + "\\begin{document}".length());
        }

        // Remove \end{document}
        content = content.replace("\\end{document}", "");

        // Remove \begin{...} and \end{...} environment tags (e.g., \begin{itemize})
        content = content.replaceAll("\\\\(begin|end)\\{[^}]*\\}", "");

        // Extract content from single-argument commands: \textbf{Hello} → Hello
        // Also handles \section{}, \subsection{}, \item, \href{url}{text} → text
        content = content.replaceAll("\\\\href\\{[^}]*\\}\\{([^}]*)\\}", "$1"); // \href{url}{text}
        content = content.replaceAll("\\\\[a-zA-Z]+\\{([^}]*)\\}", "$1");       // \cmd{text} → text

        // Remove standalone LaTeX commands (e.g., \newpage, \hline, \vspace)
        content = content.replaceAll("\\\\[a-zA-Z]+\\*?", "");

        // Remove LaTeX comment lines (% comment)
        content = content.replaceAll("(?m)%.*$", "");

        // Remove remaining braces and LaTeX special characters
        content = content.replaceAll("[{}]", "");
        content = content.replaceAll("\\\\", ""); // remaining backslashes

        return normalizeWhitespace(content);
    }

    /**
     * Collapses multiple blank lines into a single blank line and trims the result.
     * Produces cleaner text for the AI to process.
     */
    private String normalizeWhitespace(String text) {
        return text
                .replaceAll("[ \\t]+", " ")         // collapse horizontal whitespace
                .replaceAll("\\n{3,}", "\n\n")       // max 2 consecutive newlines
                .trim();
    }
}