package com.app.craftyhire.controller;

import com.app.craftyhire.dto.request.CoverLetterRequest;
import com.app.craftyhire.dto.request.GenerateRequest;
import com.app.craftyhire.dto.request.JobRequest;
import com.app.craftyhire.dto.request.ResumeRequest;
import com.app.craftyhire.dto.response.GenerateDocumentResponse;
import com.app.craftyhire.dto.response.JobAnalysisResponse;
import com.app.craftyhire.dto.response.ParseResumeResponse;
import com.app.craftyhire.model.SkillScore;
import com.app.craftyhire.service.ClaudeService;
import com.app.craftyhire.service.JobAnalysisService;
import com.app.craftyhire.service.ResumeParserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * REST controller for the core resume workflow.
 *
 * Typical user flow (in order):
 *   1. POST /api/resume/parse          — upload resume file, get plain text back
 *   2. POST /api/resume/analyze-job    — paste job description, get skill analysis
 *   3. POST /api/skills/analyze        — identify gaps (see SkillController)
 *   4. POST /api/skills/answer         — answer follow-up questions (see SkillController)
 *   5. POST /api/resume/generate-resume       — generate tailored resume
 *   6. POST /api/resume/generate-cover-letter — generate cover letter
 *   7. POST /api/export/{format}       — download the file (see FileExportController)
 *
 * All endpoints except /parse require JSON bodies.
 * All endpoints require a valid JWT access token (configured in SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeParserService resumeParserService;
    private final ClaudeService claudeService;
    private final JobAnalysisService jobAnalysisService;

    /**
     * Parse an uploaded resume file and return its plain text content.
     *
     * Accepts: PDF, DOCX, or LaTeX (.tex) files via multipart form upload.
     * The returned resumeText should be stored by the frontend and included
     * in all subsequent generate/analyze requests.
     *
     * Returns 200 OK with { resumeText, fileType } on success.
     * Returns 400 Bad Request if the file type is not supported.
     *
     * @param file the uploaded resume file (form field name: "file")
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParseResumeResponse> parseResume(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Parsing resume: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        String fileType   = resumeParserService.detectFileType(file);
        String resumeText = resumeParserService.parseResume(file);

        return ResponseEntity.ok(ParseResumeResponse.builder()
                .resumeText(resumeText)
                .fileType(fileType)
                .build());
    }

    /**
     * Analyze a job description and return a structured overview plus ranked skills.
     *
     * Calls Claude to produce a human-readable analysis AND extracts a scored
     * list of required skills. Both are returned in the same response so the
     * frontend can display the skill bars immediately after the user pastes the job.
     *
     * Returns 200 OK with { analysis, skills[] }.
     *
     * @param request contains the raw job description text
     */
    @PostMapping("/analyze-job")
    public ResponseEntity<JobAnalysisResponse> analyzeJob(
            @Valid @RequestBody JobRequest request) {

        log.info("Analyzing job description ({} chars)", request.getJobDescription().length());

        // Run job analysis: Claude provides narrative, JobAnalysisService extracts + ranks skills
        String analysis          = claudeService.analyzeJobDescription(request.getJobDescription());
        List<SkillScore> skills  = jobAnalysisService.extractSkills(request.getJobDescription());
        List<SkillScore> ranked  = jobAnalysisService.rankSkills(skills);

        return ResponseEntity.ok(JobAnalysisResponse.builder()
                .analysis(analysis)
                .skills(ranked)
                .build());
    }

    /**
     * Generate an ATS-optimized resume tailored to the job description.
     *
     * Expects the user to have already answered any skill gap questions
     * (via SkillController) and included those answers in skillAnswers.
     * If skillAnswers is empty, the resume is generated from the base resume only.
     *
     * Returns 200 OK with { content, documentType: "RESUME" }.
     * The content can be sent to /api/export/{format} to download the file.
     *
     * @param request resume text, job description, output format, and optional skill answers
     */
    @PostMapping("/generate-resume")
    public ResponseEntity<GenerateDocumentResponse> generateResume(
            @Valid @RequestBody GenerateRequest request) {

        log.info("Generating resume (format: {}, skillAnswers: {})",
                request.getOutputFormat(),
                request.getSkillAnswers() == null ? 0 : request.getSkillAnswers().size());

        // Convert frontend GenerateRequest → internal ResumeRequest for ClaudeService
        ResumeRequest resumeRequest = ResumeRequest.builder()
                .rawResumeText(request.getResumeText())
                .jobDescription(request.getJobDescription())
                .outputFormat(request.getOutputFormat())
                .previousCoverLetter(request.getPreviousCoverLetter())
                .skillAnswers(request.getSkillAnswers())
                .build();

        String content = claudeService.generateResume(resumeRequest);

        return ResponseEntity.ok(GenerateDocumentResponse.builder()
                .content(content)
                .documentType("RESUME")
                .build());
    }

    /**
     * Generate a personalized cover letter for the job application.
     *
     * If a previousCoverLetter is provided in the request, Claude will adapt
     * the candidate's writing style and tone. Otherwise a professional default
     * style is used.
     *
     * Returns 200 OK with { content, documentType: "COVER_LETTER" }.
     *
     * @param request resume text, job description, optional previous letter, and skill answers
     */
    @PostMapping("/generate-cover-letter")
    public ResponseEntity<GenerateDocumentResponse> generateCoverLetter(
            @Valid @RequestBody GenerateRequest request) {

        log.info("Generating cover letter (format: {})", request.getOutputFormat());

        // Convert frontend GenerateRequest → internal CoverLetterRequest for ClaudeService
        CoverLetterRequest coverLetterRequest = CoverLetterRequest.builder()
                .resumeText(request.getResumeText())
                .jobDescription(request.getJobDescription())
                .previousCoverLetter(request.getPreviousCoverLetter())
                .skillAnswers(request.getSkillAnswers())
                .outputFormat(request.getOutputFormat())
                .build();

        String content = claudeService.generateCoverLetter(coverLetterRequest);

        return ResponseEntity.ok(GenerateDocumentResponse.builder()
                .content(content)
                .documentType("COVER_LETTER")
                .build());
    }
}