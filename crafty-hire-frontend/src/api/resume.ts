import type {
  ParseResumeResponse,
  JobAnalysisResponse,
  GenerateDocumentResponse,
  SkillAnswer,
  ExportFormat,
} from '../types';
import { apiJson } from './client';

export async function parseResume(file: File): Promise<ParseResumeResponse> {
  const form = new FormData();
  form.append('file', file);
  return apiJson<ParseResumeResponse>('/api/resume/parse', {
    method: 'POST',
    body: form,
  });
}

export async function analyzeJob(jobDescription: string): Promise<JobAnalysisResponse> {
  return apiJson<JobAnalysisResponse>('/api/resume/analyze-job', {
    method: 'POST',
    body: JSON.stringify({ jobDescription }),
  });
}

export async function generateResume(
  resumeText: string,
  jobDescription: string,
  outputFormat: ExportFormat,
  skillAnswers: SkillAnswer[],
  previousCoverLetter?: string,
): Promise<GenerateDocumentResponse> {
  return apiJson<GenerateDocumentResponse>('/api/resume/generate-resume', {
    method: 'POST',
    body: JSON.stringify({
      resumeText,
      jobDescription,
      outputFormat,
      skillAnswers,
      previousCoverLetter: previousCoverLetter ?? null,
    }),
  });
}

export async function generateCoverLetter(
  resumeText: string,
  jobDescription: string,
  outputFormat: ExportFormat,
  skillAnswers: SkillAnswer[],
  previousCoverLetter?: string,
): Promise<GenerateDocumentResponse> {
  return apiJson<GenerateDocumentResponse>('/api/resume/generate-cover-letter', {
    method: 'POST',
    body: JSON.stringify({
      resumeText,
      jobDescription,
      outputFormat,
      skillAnswers,
      previousCoverLetter: previousCoverLetter ?? null,
    }),
  });
}
