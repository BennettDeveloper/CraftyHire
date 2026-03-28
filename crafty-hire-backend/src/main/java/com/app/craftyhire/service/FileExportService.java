package com.app.craftyhire.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formats and exports generated document content to downloadable file formats.
 *
 * Supports three export formats:
 *   - PDF    — via Apache PDFBox (multi-page, with section header detection)
 *   - DOCX   — via Apache POI (formatted paragraphs with heading styles)
 *   - LaTeX  — plain text wrapped in a clean LaTeX article template
 *
 * Input: plain text content produced by ClaudeService, with ALL-CAPS lines
 * used as section headers (e.g., "WORK EXPERIENCE", "SKILLS").
 *
 * Scalability note: For production-quality output, consider replacing the
 * PDFBox implementation with a template-based approach (e.g., HTML-to-PDF
 * using OpenHTMLtoPDF) for finer typography control. The current implementation
 * produces clean, readable output suitable for the demo.
 */
@Slf4j
@Service
public class FileExportService {

    @Value("classpath:templates/resumes/resume-docx-template.docx")
    private Resource resumeDocxTemplate;

    @Value("classpath:templates/coverletters/coverletter-docx-template.docx")
    private Resource coverLetterDocxTemplate;

    // ── PDF Layout Constants ─────────────────────────────────────────────────
    private static final float PAGE_HEIGHT        = PDRectangle.A4.getHeight();
    private static final float PAGE_WIDTH         = PDRectangle.A4.getWidth();
    private static final float MARGIN             = 60f;
    private static final float CONTENT_WIDTH      = PAGE_WIDTH - (2 * MARGIN);
    private static final float Y_START            = PAGE_HEIGHT - MARGIN;
    private static final float Y_MIN              = MARGIN;

    // Font sizes (points)
    private static final float HEADER_FONT_SIZE   = 13f;
    private static final float BODY_FONT_SIZE     = 10.5f;

    // Line heights (points)
    private static final float HEADER_LINE_HEIGHT = 22f;
    private static final float BODY_LINE_HEIGHT   = 14f;
    private static final float BLANK_LINE_HEIGHT  = 7f;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Exports document content as a PDF byte array.
     *
     * Handles:
     *   - ALL-CAPS section headers rendered in bold at a larger font size
     *   - Automatic word wrapping for lines that exceed the page width
     *   - Automatic page breaks when content overflows a page
     *
     * @param content      plain text document content from ClaudeService
     * @param documentType "RESUME" or "COVER_LETTER" (used for future template selection)
     * @return PDF file as a byte array, ready to send as a download response
     */
    public byte[] exportToPDF(String content, String documentType) throws IOException {
        log.debug("Exporting {} as PDF ({} chars)", documentType, content.length());

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Fonts must be created from the document context
            PDFont boldFont   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont normalFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(document, page);
            float y = Y_START;

            for (String line : content.split("\n")) {
                if (line.isBlank()) {
                    y -= BLANK_LINE_HEIGHT;
                    // Create a new page if a blank line would push us off
                    if (y < Y_MIN) {
                        cs = openNewPage(cs, document);
                        y = Y_START;
                    }
                    continue;
                }

                boolean isHeader = isHeaderLine(line);
                PDFont font       = isHeader ? boldFont   : normalFont;
                float  fontSize   = isHeader ? HEADER_FONT_SIZE : BODY_FONT_SIZE;
                float  lineHeight = isHeader ? HEADER_LINE_HEIGHT : BODY_LINE_HEIGHT;

                // Word-wrap the line to fit within the content width
                List<String> wrappedLines = wrapText(line, font, fontSize, CONTENT_WIDTH);

                for (String wrappedLine : wrappedLines) {
                    // Start a new page if this line would overflow
                    if (y - lineHeight < Y_MIN) {
                        cs = openNewPage(cs, document);
                        y = Y_START;
                    }

                    // Draw the line at the current y position
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText(sanitizeForPdf(wrappedLine));
                    cs.endText();

                    y -= lineHeight;
                }
            }

            cs.close();
            document.save(out);
            log.info("PDF export complete: {} bytes", out.size());
            return out.toByteArray();
        }
    }

    public byte[] exportToWord(String content, String documentType) throws IOException {
        log.debug("Exporting {} as DOCX ({} chars)", documentType, content.length());

        Resource template = "COVER_LETTER".equalsIgnoreCase(documentType)
                ? coverLetterDocxTemplate
                : resumeDocxTemplate;

        // Parse Claude's JSON response into a placeholder map
        Map<String, String> replacements = parsePlaceholderJson(content);

        try (XWPFDocument document = openDocxTemplate(template);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Replace placeholders in all paragraphs
            for (XWPFParagraph para : document.getParagraphs()) {
                replacePlaceholdersInParagraph(para, replacements);
            }

            // Replace placeholders in tables (if template uses them)
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph para : cell.getParagraphs()) {
                            replacePlaceholdersInParagraph(para, replacements);
                        }
                    }
                }
            }

            // Reduce font size if content is too long to fit one page
            compressToOnePage(document);

            // After all replacements, remove empty paragraphs that were
            // placeholder-only bullets with no content
            removeEmptyParagraphs(document);

            document.write(out);
            log.info("DOCX export complete: {} bytes", out.size());
            return out.toByteArray();

        }
    }

    private void removeEmptyParagraphs(XWPFDocument document) {
        List<XWPFParagraph> toRemove = new ArrayList<>();

        for (XWPFParagraph para : document.getParagraphs()) {
            String text = para.getText().trim();

            // ADD THIS DEBUG LOG
            String stripped = text.replaceAll("[|\\-\\u2013\\u2014\\s]", "");
            log.debug("Para: '{}' | stripped: '{}' | isBlank: {}", text, stripped, stripped.isBlank());

            // 1. Fully blank
            if (text.isBlank()) {
                toRemove.add(para);
                continue;
            }

            // 2. Unfilled [ ] placeholders still present
            if (text.contains("[") && text.contains("]")) {
                toRemove.add(para);
                continue;
            }

            // 3. Orphaned separator-only lines — covers ASCII hyphen, en dash, em dash
            if (stripped.isBlank()) {
                toRemove.add(para);
                continue;
            }

            // 4. Structural label lines with no actual values
            String noLabels = text
                    .replaceAll("(?i)technologies\\s*:", "")
                    .replaceAll("(?i)github\\s*:", "")
                    .replaceAll("(?i)live demo\\s*:", "")
                    .replaceAll("(?i)languages\\s*:", "")
                    .replaceAll("(?i)frameworks\\s*[&a-z]*\\s*:", "")
                    .replaceAll("(?i)databases\\s*:", "")
                    .replaceAll("(?i)tools\\s*[&a-z]*\\s*:", "")
                    .replaceAll("(?i)methodologies\\s*:", "")
                    .replaceAll("(?i)certifications\\s*:", "")
                    .replaceAll("[|\\-\\u2013\\u2014\\s]", "");
            if (noLabels.isBlank()) {
                toRemove.add(para);
            }

            // 5. Job heading lines where location or dates are empty
            // Catches patterns like "Job Title  |    |    |   –" or "Title  |  Company  |    |"
            // Split on | and check if any segment (after the first) is blank
            String[] segments = text.split("\\|");
            if (segments.length >= 3) {
                long emptySegments = Arrays.stream(segments)
                        .skip(1) // skip the job title itself
                        .map(String::trim)
                        .filter(s -> s.isBlank() || s.equals("–") || s.equals("-") || s.equals("—"))
                        .count();
                if (emptySegments >= 2) {
                    toRemove.add(para);
                    continue;
                }
            }
        }

        log.info("removeEmptyParagraphs removing {} paragraphs", toRemove.size());
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            XWPFParagraph para = toRemove.get(i);
            document.removeBodyElement(document.getPosOfParagraph(para));
        }
    }

    private void replacePlaceholdersInParagraph(XWPFParagraph para,
                                                Map<String, String> replacements) {
        List<XWPFRun> runs = para.getRuns();
        if (runs == null || runs.isEmpty()) return;

        // Reconstruct full paragraph text across all runs
        String fullText = runs.stream()
                .map(r -> r.getText(0) == null ? "" : r.getText(0))
                .collect(Collectors.joining());

        // Only process paragraphs that actually contain placeholders
        if (!fullText.contains("[") || !fullText.contains("]")) return;

        // Replace all placeholders with their values
        String replaced = fullText;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            replaced = replaced.replace(entry.getKey(), entry.getValue());
        }

        // Clean up REMOVE sentinels and their surrounding " | " separators.
        // Order matters — handle middle cases before edge cases.
        replaced = replaced
                .replaceAll("REMOVE\\s*\\|\\s*", "")   // REMOVE at start or middle
                .replaceAll("\\s*\\|\\s*REMOVE", "")   // REMOVE at end
                .replaceAll("^\\s*\\|\\s*", "")        // orphaned leading separator
                .replaceAll("\\s*\\|\\s*$", "")        // orphaned trailing separator
                .trim();

        // Write merged result into first run, blank out the rest.
        // This preserves the first run's font/size/bold/color styling.
        runs.get(0).setText(replaced, 0);
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("", 0);
        }
    }

    private void compressToOnePage(XWPFDocument document) {
        // Rough character count heuristic — a single page at 11pt Calibri
        // with 0.7" margins fits approximately 3500-4000 characters.
        String fullText = document.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .collect(Collectors.joining());

        int length = fullText.length();
        log.debug("Document length for page compression: {} chars", length);

        // Only compress if content is likely overflowing one page
        if (length < 3800) return;

        // Determine reduced font size based on how much content there is
        int bodySize;    // in half-points (POI uses half-points)
        int headerSize;

        if (length < 4400) {
            bodySize = 18;   // 9pt
            headerSize = 20; // 10pt
        } else {
            bodySize = 16;   // 8pt
            headerSize = 18; // 9pt
        }

        log.info("Compressing document to one page — body: {}pt, headers: {}pt",
                bodySize / 2, headerSize / 2);

        for (XWPFParagraph para : document.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                // Only shrink runs that have explicit font sizes set
                // (leave unstyled runs alone to avoid breaking template formatting)
                if (run.getFontSize() > 0) {
                    boolean isHeader = run.isBold() && run.getFontSize() >= 11;
                    run.setFontSize(isHeader ? headerSize / 2 : bodySize / 2);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parsePlaceholderJson(String content) {
        try {
            // Strip markdown fences if Claude wrapped the JSON
            String cleaned = content
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("```", "")
                    .trim();
            return new ObjectMapper().readValue(cleaned, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse Claude placeholder JSON: {}", e.getMessage());
            throw new RuntimeException("Claude did not return valid placeholder JSON for DOCX export", e);
        }
    }

    /**
     * Opens a DOCX template from the classpath. Falls back to a blank document
     * if the template is missing, so exports still work without a template file.
     */
    private XWPFDocument openDocxTemplate(Resource template) throws IOException {
        if (template != null && template.exists()) {
            log.debug("Loading DOCX template: {}", template.getFilename());
            return new XWPFDocument(template.getInputStream());
        }
        log.warn("DOCX template not found — falling back to blank document");
        return new XWPFDocument();
    }

    /**
     * Exports document content as a LaTeX (.tex) byte array.
     *
     * Wraps the content in a standard article template with common packages.
     * ALL-CAPS section headers become \section*{} commands with a horizontal rule.
     * Body text has LaTeX special characters escaped automatically.
     *
     * The output can be compiled with pdflatex or xelatex.
     *
     * @param content      plain text document content from ClaudeService
     * @param documentType "RESUME" or "COVER_LETTER"
     * @return LaTeX source as a UTF-8 byte array
     */
    public byte[] exportToLatex(String content, String documentType) {
        log.debug("Exporting {} as LaTeX ({} chars)", documentType, content.length());

        // If Claude already produced a complete LaTeX document (i.e. the resume was
        // generated with the user's own template), pass it through unchanged.
        // Otherwise wrap the plain-text content in the built-in article template.
        String latex = content.trim().startsWith("\\documentclass")
                ? content
                : buildLatexDocument(content, documentType);

        log.info("LaTeX export complete: {} bytes", latex.length());
        return latex.getBytes(StandardCharsets.UTF_8);
    }

    // ── Private: PDF Helpers ─────────────────────────────────────────────────

    /** Closes the current page's content stream and opens a new page */
    private PDPageContentStream openNewPage(PDPageContentStream oldStream, PDDocument document) throws IOException {
        oldStream.close();
        PDPage newPage = new PDPage(PDRectangle.A4);
        document.addPage(newPage);
        return new PDPageContentStream(document, newPage);
    }

    /**
     * Word-wraps a single line of text to fit within maxWidth points.
     * Falls back to a character-width estimate if font metrics fail.
     *
     * @return list of wrapped line strings (at least one element)
     */
    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        if (text.isBlank()) {
            return List.of("");
        }

        List<String> lines     = new ArrayList<>();
        String[]     words     = text.trim().split("\\s+");
        StringBuilder current  = new StringBuilder();

        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float  width;

            try {
                width = font.getStringWidth(sanitizeForPdf(candidate)) / 1000f * fontSize;
            } catch (Exception e) {
                // Rough fallback: ~0.5pt per character per font point
                width = candidate.length() * fontSize * 0.5f;
            }

            if (width > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                if (!current.isEmpty()) current.append(" ");
                current.append(word);
            }
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }

        return lines.isEmpty() ? List.of("") : lines;
    }

    /**
     * Replaces Unicode characters that PDFBox's standard fonts cannot render.
     * Standard Type 1 fonts (Helvetica, Times) only support the Windows-1252 charset.
     */
    private String sanitizeForPdf(String text) {
        // All arguments use String literals (double quotes) so Java resolves
        // replace(CharSequence, CharSequence) — mixing char and String is not allowed.
        return text
                .replace("\u2022", "-")     // bullet •
                .replace("\u2013", "-")     // en dash –
                .replace("\u2014", "--")    // em dash —
                .replace("\u201c", "\"")    // left double quote "
                .replace("\u201d", "\"")    // right double quote "
                .replace("\u2018", "'")     // left single quote '
                .replace("\u2019", "'")     // right single quote '
                .replaceAll("[^\\x00-\\x7E]", ""); // drop remaining non-ASCII
    }

    // ── Private: LaTeX Helpers ───────────────────────────────────────────────

    /** Wraps content in a complete LaTeX article document */
    private String buildLatexDocument(String content, String documentType) {
        StringBuilder sb = new StringBuilder();

        // Preamble
        sb.append("\\documentclass[11pt,a4paper]{article}\n");
        sb.append("\\usepackage[utf8]{inputenc}\n");
        sb.append("\\usepackage[T1]{fontenc}\n");
        sb.append("\\usepackage[margin=1in]{geometry}\n");
        sb.append("\\usepackage{hyperref}\n");
        sb.append("\\usepackage{enumitem}\n");
        sb.append("\\setlength{\\parindent}{0pt}\n");
        sb.append("\\setlength{\\parskip}{4pt}\n");
        sb.append("\\pagestyle{empty}\n");
        sb.append("% Generated by CraftyHire — ").append(documentType).append("\n");
        sb.append("\\begin{document}\n\n");

        // Body: convert ALL-CAPS headers to \section*{} with a rule
        for (String line : content.split("\n")) {
            if (line.isBlank()) {
                sb.append("\n");
            } else if (isHeaderLine(line)) {
                sb.append("\\medskip\n");
                sb.append("\\noindent\\textbf{\\large ").append(escapeLatex(line)).append("}\n\n");
                sb.append("\\hrule\\smallskip\n\n");
            } else {
                sb.append(escapeLatex(line)).append("\\\\\n");
            }
        }

        sb.append("\n\\end{document}\n");
        return sb.toString();
    }

    /** Escapes LaTeX special characters so they render as literal text */
    private String escapeLatex(String text) {
        return text
                .replace("\\", "\\textbackslash{}")
                .replace("&",  "\\&")
                .replace("%",  "\\%")
                .replace("$",  "\\$")
                .replace("#",  "\\#")
                .replace("_",  "\\_")
                .replace("{",  "\\{")
                .replace("}",  "\\}")
                .replace("~",  "\\textasciitilde{}")
                .replace("^",  "\\textasciicircum{}");
    }

    // ── Private: Shared Helpers ──────────────────────────────────────────────

    /**
     * Detects whether a line is a section header.
     *
     * Convention: Claude is prompted to output section headers in ALL CAPS
     * (e.g., "WORK EXPERIENCE", "SKILLS"). This method identifies those lines
     * so they can be styled differently in PDF and DOCX output.
     */
    private boolean isHeaderLine(String line) {
        String trimmed = line.trim();
        // Must be between 2 and 50 chars, and consist only of uppercase letters, spaces, and slashes
        return trimmed.length() >= 2
                && trimmed.length() <= 50
                && trimmed.equals(trimmed.toUpperCase())
                && trimmed.matches("[A-Z][A-Z\\s/&]+");
    }
}