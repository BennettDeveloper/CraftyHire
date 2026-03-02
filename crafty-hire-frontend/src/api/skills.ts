import type { SkillAnalysisResponse, SkillAnswerResponse } from '../types';
import { apiJson } from './client';

export async function analyzeSkills(
  resumeText: string,
  jobDescription: string,
): Promise<SkillAnalysisResponse> {
  return apiJson<SkillAnalysisResponse>('/api/skills/analyze', {
    method: 'POST',
    body: JSON.stringify({ resumeText, jobDescription }),
  });
}

export async function answerSkillQuestion(
  skillName: string,
  hasExperience: boolean,
  description: string,
): Promise<SkillAnswerResponse> {
  return apiJson<SkillAnswerResponse>('/api/skills/answer', {
    method: 'POST',
    body: JSON.stringify({ skillName, hasExperience, description }),
  });
}
