package com.app.craftyhire.service;

import com.app.craftyhire.model.SkillGap;
import com.app.craftyhire.model.SkillScore;
// Jackson 3.x (shipped with Spring Boot 4) moved to the tools.jackson group ID
// and tools.jackson.* package names — replacing the old com.fasterxml.jackson.*
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analyzes job descriptions and identifies skill gaps between a job's
 * requirements and the candidate's resume.
 *
 * Uses the Claude API directly (via claudeRestClient) to perform
 * AI-powered skill extraction and semantic gap analysis.
 *
 * Responsibilities:
 *   1. extractSkills  — parse a job description into a scored list of skills
 *   2. rankSkills     — sort skills by relevance score (highest first)
 *   3. compareToResume — identify which required skills are missing from the resume
 *
 * Note: This service calls Claude independently of ClaudeService to avoid
 * circular dependencies (ClaudeService → JobAnalysisService for gap analysis).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobAnalysisService {

    private final RestClient claudeRestClient;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api.model}")
    private String model;

    @Value("${anthropic.api.max-tokens}")
    private int maxTokens;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Extracts and scores skills from a job description using Claude.
     *
     * Claude is asked to identify both explicit skills (e.g., "5 years of Java")
     * and implied skills (e.g., a "senior engineer" role implies system design).
     * Each skill is scored 0.0–1.0 based on how central it is to the role.
     *
     * @param jobDescription the raw job posting text
     * @return list of SkillScore objects (unranked)
     */
    public List<SkillScore> extractSkills(String jobDescription) {
        log.debug("Extracting skills from job description ({} chars)", jobDescription.length());

        String prompt = """
                Analyze this job description and extract all required skills.
                Return ONLY a valid JSON array — no markdown, no backticks, no explanation text.

                Each element must have exactly these fields:
                  "skillName":      the skill name (string, concise — e.g., "React", "Team Leadership")
                  "relevanceScore": importance for this role, from 0.0 (minor) to 1.0 (essential) (number)
                  "category":       one of: "Technical", "Soft Skills", "Tools", "Domain Knowledge" (string)

                Include both hard skills (languages, frameworks, tools) and soft skills (communication, leadership).
                Score skills that appear multiple times or are listed as requirements higher than those listed as nice-to-haves.

                Job Description:
                """ + jobDescription;

        String response = callClaude(prompt);
        List<SkillScore> skills = parseSkillScores(response);
        log.info("Extracted {} skills from job description", skills.size());
        return skills;
    }

    /**
     * Sorts a list of skills by relevance score in descending order.
     * The frontend displays these as a ranked list of skill bars (0–100%).
     *
     * @param skills unsorted list of SkillScore objects
     * @return new list sorted highest-relevance first
     */
    public List<SkillScore> rankSkills(List<SkillScore> skills) {
        return skills.stream()
                .sorted(Comparator.comparingDouble(SkillScore::getRelevanceScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Compares the candidate's resume against a list of required skills
     * and returns skills that are missing or underrepresented.
     *
     * Uses Claude for semantic matching — for example, a resume mentioning
     * "AWS Lambda" will be recognized as covering "Cloud Computing" even if
     * the exact phrase isn't present.
     *
     * Only skills with a relevanceScore above 0.4 are checked, to avoid
     * flagging minor nice-to-have skills as critical gaps.
     *
     * @param resume parsed text of the candidate's resume
     * @param skills required skills extracted from the job description
     * @return list of SkillGap objects for skills not shown in the resume
     */
    public List<SkillGap> compareToResume(String resume, List<SkillScore> skills) {
        log.debug("Comparing resume against {} skills", skills.size());

        // Only check skills above the minimum relevance threshold to reduce noise
        List<SkillScore> significantSkills = skills.stream()
                .filter(s -> s.getRelevanceScore() >= 0.4)
                .collect(Collectors.toList());

        if (significantSkills.isEmpty()) {
            return new ArrayList<>();
        }

        String skillsJson;
        try {
            skillsJson = objectMapper.writeValueAsString(significantSkills);
        } catch (Exception e) {
            log.error("Failed to serialize skills for gap analysis", e);
            return new ArrayList<>();
        }

        String prompt = """
                Compare this candidate's resume against the required skills list.
                Identify skills that are NOT clearly demonstrated in the resume.

                Use semantic matching — e.g., if the resume mentions "AWS Lambda" and "EC2",
                consider "Cloud Computing" as covered even if not stated explicitly.

                Return ONLY a valid JSON array of skill name strings — no markdown, no explanation.
                Example format: ["Kubernetes", "GraphQL", "Team Leadership"]
                If no gaps are found, return an empty array: []

                Candidate Resume:
                """ + resume + """

                Required Skills (JSON):
                """ + skillsJson;

        String response = callClaude(prompt);
        List<String> missingSkillNames = parseMissingSkillNames(response);

        // Map missing skill names back to their SkillScore objects to preserve scores
        Map<String, SkillScore> skillMap = significantSkills.stream()
                .collect(Collectors.toMap(
                        s -> s.getSkillName().toLowerCase(),
                        s -> s
                ));

        List<SkillGap> gaps = missingSkillNames.stream()
                .map(name -> skillMap.get(name.toLowerCase()))
                .filter(skill -> skill != null)
                .map(skill -> SkillGap.builder()
                        .skillName(skill.getSkillName())
                        .relevanceScore(skill.getRelevanceScore())
                        .hasExperience(false)
                        .transferableExperience("")
                        .resolved(false)
                        .build())
                .collect(Collectors.toList());

        log.info("Found {} skill gaps in resume", gaps.size());
        return gaps;
    }

    // ── Claude API ───────────────────────────────────────────────────────────

    /**
     * Sends a message to the Claude API and returns the response text.
     *
     * @param userMessage the prompt to send
     * @return Claude's text response
     * @throws RuntimeException if the API call fails or returns an unexpected format
     */
    @SuppressWarnings("unchecked")
    private String callClaude(String userMessage) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "user", "content", userMessage)
                )
        );

        Map<String, Object> response = claudeRestClient.post()
                .uri("/v1/messages")
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null) {
            throw new RuntimeException("Empty response from Claude API");
        }

        // Response format: { "content": [{ "type": "text", "text": "..." }] }
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("No content in Claude API response");
        }

        return (String) content.get(0).get("text");
    }

    // ── Response Parsing ─────────────────────────────────────────────────────

    /**
     * Parses Claude's response into a list of SkillScore objects.
     * Handles cases where Claude wraps the JSON in markdown code fences.
     */
    private List<SkillScore> parseSkillScores(String response) {
        try {
            String json = extractJsonArray(response);
            return objectMapper.readValue(json, new TypeReference<List<SkillScore>>() {});
        } catch (Exception e) {
            log.error("Failed to parse skill scores from response. Response was: {}", response, e);
            return new ArrayList<>();
        }
    }

    /**
     * Parses Claude's response into a list of missing skill name strings.
     */
    private List<String> parseMissingSkillNames(String response) {
        try {
            String json = extractJsonArray(response);
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse missing skill names from response. Response was: {}", response, e);
            return new ArrayList<>();
        }
    }

    /**
     * Extracts the first complete JSON array found in a string.
     * Used to handle responses where Claude adds extra explanation text
     * around the JSON despite being asked not to.
     *
     * @param text the raw response string
     * @return the JSON array substring
     * @throws IllegalArgumentException if no JSON array is found
     */
    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');

        if (start == -1 || end == -1 || start >= end) {
            throw new IllegalArgumentException("No JSON array found in response: " + text);
        }

        return text.substring(start, end + 1);
    }
}